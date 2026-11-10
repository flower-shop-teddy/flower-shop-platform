package com.flowershop.common.clienttest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flowershop.common.client.RemoteServiceException;
import com.flowershop.common.client.ServiceClientFactory;
import com.flowershop.common.client.ServiceEndpoint;
import com.flowershop.common.client.ServiceTokenClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceTokenClientTest {

    /** Đồng hồ chỉnh tay được, để thử "token sắp hết hạn" mà không phải chờ thật. */
    static final class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-10T00:00:00Z"));

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }

        void advance(Duration d) { now.updateAndGet(i -> i.plus(d)); }
    }

    private final MutableClock clock = new MutableClock();
    private final ServiceClientFactory factory =
            new ServiceClientFactory(RestClient.builder(), new ObjectMapper().registerModule(new JavaTimeModule()));
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile long expiresIn = 300;
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/internal/v1/service-token", ex -> {
            int n = calls.incrementAndGet();
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String body = status == 200
                    ? "{\"code\":\"00\",\"data\":{\"tokenType\":\"Bearer\",\"accessToken\":\"tok-%d\",\"expiresIn\":%d}}"
                    .formatted(n, expiresIn)
                    : "{\"code\":\"IDN-401-003\",\"message\":\"sai\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            try (var out = ex.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void reusesTokenUntilOneMinuteBeforeExpiry() {
        ServiceTokenClient client = client();

        assertThat(client.get()).isEqualTo("tok-1");
        assertThat(lastBody.get()).contains("\"clientId\":\"order-service\"").contains("\"clientSecret\":\"s3cret\"");
        clock.advance(Duration.ofSeconds(239));
        assertThat(client.get()).isEqualTo("tok-1");   // còn 61 giây: dùng lại
        clock.advance(Duration.ofSeconds(1));
        assertThat(client.get()).isEqualTo("tok-2");   // còn 60 giây: xin token mới
        assertThat(calls).hasValue(2);
    }

    @Test
    void shortTtl_renewsAtHalfLife() {
        expiresIn = 60;
        ServiceTokenClient client = client();

        client.get();
        clock.advance(Duration.ofSeconds(29));
        assertThat(client.get()).isEqualTo("tok-1");
        clock.advance(Duration.ofSeconds(1));
        assertThat(client.get()).isEqualTo("tok-2");
    }

    @Test
    void concurrentCalls_fetchOnlyOnce() throws Exception {
        ServiceTokenClient client = client();
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    start.await();
                    return client.get();
                });
            }
            start.countDown();
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
        assertThat(calls).hasValue(1);
    }

    @Test
    void rejectedCredentials_throwRemoteServiceExceptionWithCode() {
        status = 401;

        assertThatThrownBy(() -> client().get())
                .isInstanceOfSatisfying(RemoteServiceException.class, e -> {
                    assertThat(e.service()).isEqualTo("identity-service");
                    assertThat(e.httpStatus()).isEqualTo(401);
                    assertThat(e.code()).isEqualTo("IDN-401-003");
                });
    }

    @Test
    void toString_doesNotLeakSecret() {
        assertThat(client().toString()).doesNotContain("s3cret");
    }

    private ServiceTokenClient client() {
        ServiceEndpoint identity = new ServiceEndpoint("identity-service",
                "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(1), Duration.ofSeconds(1));
        return new ServiceTokenClient(factory, identity, "order-service", "s3cret", clock);
    }
}
