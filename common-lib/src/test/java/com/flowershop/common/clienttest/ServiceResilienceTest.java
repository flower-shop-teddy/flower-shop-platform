package com.flowershop.common.clienttest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flowershop.common.api.ApiResponse;
import com.flowershop.common.client.Idempotent;
import com.flowershop.common.client.RemoteServiceException;
import com.flowershop.common.client.ResilienceSettings;
import com.flowershop.common.client.ServiceClientFactory;
import com.flowershop.common.client.ServiceEndpoint;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Circuit breaker + retry của ServiceClientFactory, với HTTP server thật trả lỗi theo kịch bản. */
class ServiceResilienceTest {

    interface StockApi {
        @GetExchange("/stock")
        ApiResponse<Object> read();

        @PostExchange("/stock")
        ApiResponse<Object> createNotIdempotent();

        @Idempotent
        @PostExchange("/stock")
        ApiResponse<Object> reserveIdempotent();
    }

    /** Ngưỡng nhỏ, chờ ngắn để test chạy nhanh: ngắt mạch khi ≥50% của 4 lời gọi lỗi, ngắt 300ms. */
    private static final ResilienceSettings FAST =
            new ResilienceSettings(4, 4, 50f, Duration.ofMillis(300), 1, 3, Duration.ofMillis(5));

    private final AtomicInteger hits = new AtomicInteger();
    /** Server trả mã này cho N lần đầu, sau đó trả 200. */
    private volatile int failStatus;
    private volatile int failTimes;
    private HttpServer server;
    private ServiceClientFactory factory;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stock", ex -> {
            int n = hits.incrementAndGet();
            if (n <= failTimes) {
                reply(ex, failStatus, "{\"code\":\"X-%d\",\"message\":\"lỗi\"}".formatted(failStatus));
            } else {
                reply(ex, 200, "{\"code\":\"00\",\"data\":{}}");
            }
        });
        server.start();
        factory = new ServiceClientFactory(RestClient.builder(), new ObjectMapper().registerModule(new JavaTimeModule()),
                FAST);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void get_retriesTransient503_thenSucceeds() {
        failWith(503, 2);

        assertThat(api(null).read().code()).isEqualTo("00");
        assertThat(hits).hasValue(3);
    }

    @Test
    void post_withoutIdempotent_isNeverRetried() {
        failWith(503, 99);

        assertThatThrownBy(() -> api(null).createNotIdempotent()).isInstanceOf(RemoteServiceException.class);
        assertThat(hits).hasValue(1);   // timeout/503 không có nghĩa bên kia chưa xử lý: gọi lại có thể tạo trùng
    }

    @Test
    void post_withIdempotent_isRetriedUpToMaxAttempts() {
        failWith(503, 99);

        assertThatThrownBy(() -> api(null).reserveIdempotent())
                .isInstanceOfSatisfying(RemoteServiceException.class, e -> assertThat(e.httpStatus()).isEqualTo(503));
        assertThat(hits).hasValue(3);
    }

    @Test
    void businessErrors_areNotRetried_andNeverOpenTheCircuit() {
        failWith(409, 99);
        StockApi api = api(null);

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(api::read)
                    .isInstanceOfSatisfying(RemoteServiceException.class, e -> assertThat(e.httpStatus()).isEqualTo(409));
        }
        assertThat(hits).hasValue(10);
        assertThat(factory.circuitState("stock-service")).isEqualTo("CLOSED");
    }

    @Test
    void outages_openCircuit_failFastWithoutCalling_thenRecover() throws InterruptedException {
        failWith(500, 4);   // 500: không thử lại, nhưng tính là sự cố
        StockApi api = api(null);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(api::read).isInstanceOf(RemoteServiceException.class);
        }
        assertThat(factory.circuitState("stock-service")).isEqualTo("OPEN");

        // Mạch ngắt: lỗi NGAY, không gửi request (server không nhận thêm lời gọi nào)
        assertThatThrownBy(api::read)
                .isInstanceOfSatisfying(RemoteServiceException.class, e -> {
                    assertThat(e.isUnavailable()).isTrue();
                    assertThat(e.getCause()).isInstanceOf(CallNotPermittedException.class);
                });
        assertThat(hits).hasValue(4);

        // Hết thời gian ngắt: cho một lời gọi thử, service đã khỏe thì đóng mạch lại
        Thread.sleep(400);
        assertThat(api.read().code()).isEqualTo("00");
        assertThat(factory.circuitState("stock-service")).isEqualTo("CLOSED");
    }

    @Test
    void failureOfAnotherService_isNotBlamedOnTarget() {
        // Xin token ở identity thất bại ngay trong lúc gọi stock-service
        StockApi api = api(() -> {
            throw RemoteServiceException.unavailable("identity-service", new IOException("identity down"));
        });

        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(api::read)
                    .isInstanceOfSatisfying(RemoteServiceException.class,
                            e -> assertThat(e.service()).isEqualTo("identity-service"));
        }
        assertThat(factory.circuitState("stock-service")).isEqualTo("CLOSED");
        assertThat(hits).hasValue(0);
    }

    private StockApi api(java.util.function.Supplier<String> token) {
        return factory.create(StockApi.class, new ServiceEndpoint("stock-service",
                "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(1), Duration.ofSeconds(1)), token);
    }

    private void failWith(int status, int times) {
        failStatus = status;
        failTimes = times;
    }

    private static void reply(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (var out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
