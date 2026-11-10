package com.flowershop.common.clienttest;

import com.flowershop.common.client.RemoteServiceException;
import com.flowershop.common.client.ServiceClientFactory;
import com.flowershop.common.client.ServiceEndpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flowershop.common.api.ApiResponse;
import com.flowershop.common.web.CorrelationId;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dùng HTTP server thật của JDK (không mock) để kiểm tra đúng cả timeout và lỗi kết nối.
 * Cố ý đặt ở package KHÁC factory, interface ItemApi package-private: giống cách service khai báo client.
 */
class ServiceClientFactoryTest {

    record Item(Long id, String name) {
    }

    interface ItemApi {
        @GetExchange("/items/{id}")
        ApiResponse<Item> get(@PathVariable Long id);

        @PostExchange("/items")
        ApiResponse<Item> create(@RequestBody Item item);
    }

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ServiceClientFactory factory = new ServiceClientFactory(RestClient.builder(), objectMapper);
    /** Header của request cuối cùng server nhận được. */
    private final Map<String, String> received = new ConcurrentHashMap<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/items/1", ex -> {
            capture(ex.getRequestHeaders().getFirst("Authorization"), ex.getRequestHeaders().getFirst(CorrelationId.HEADER));
            reply(ex, 200, "{\"code\":\"00\",\"message\":\"Thành công\",\"data\":{\"id\":1,\"name\":\"Hồng đỏ\"},"
                    + "\"timestamp\":\"2026-11-10T00:00:00Z\"}");
        });
        server.createContext("/items/2", ex -> reply(ex, 409, "{\"code\":\"CUS-409-001\",\"message\":\"Trùng\"}"));
        server.createContext("/items/3", ex -> reply(ex, 502, "<html>Bad Gateway</html>"));
        server.createContext("/items/4", ex -> {
            sleep(Duration.ofSeconds(2));
            reply(ex, 200, "{\"code\":\"00\"}");
        });
        server.createContext("/items/5", ex -> reply(ex, 200, "không phải json"));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        MDC.clear();
    }

    @Test
    void success_sendsBearerAndCorrelationId_andParsesApiResponse() {
        MDC.put(CorrelationId.MDC_KEY, "corr-123");
        ItemApi api = factory.create(ItemApi.class, endpoint(port()), () -> "token-abc");

        ApiResponse<Item> response = api.get(1L);

        assertThat(response.code()).isEqualTo("00");
        assertThat(response.data()).isEqualTo(new Item(1L, "Hồng đỏ"));
        assertThat(received).containsEntry("auth", "Bearer token-abc").containsEntry("corr", "corr-123");
    }

    @Test
    void nullTokenSupplier_sendsNoAuthorization() {
        factory.create(ItemApi.class, endpoint(port()), null).get(1L);

        assertThat(received).doesNotContainKey("auth");
    }

    @Test
    void errorResponse_keepsRemoteErrorCode() {
        ItemApi api = factory.create(ItemApi.class, endpoint(port()), null);

        assertThatThrownBy(() -> api.get(2L))
                .isInstanceOfSatisfying(RemoteServiceException.class, e -> {
                    assertThat(e.isUnavailable()).isFalse();
                    assertThat(e.httpStatus()).isEqualTo(409);
                    assertThat(e.code()).isEqualTo("CUS-409-001");
                    assertThat(e.hasCode("CUS-409-001")).isTrue();
                    assertThat(e.service()).isEqualTo("item-service");
                });
    }

    @Test
    void errorResponseWithoutJson_hasNullCode() {
        ItemApi api = factory.create(ItemApi.class, endpoint(port()), null);

        assertThatThrownBy(() -> api.get(3L))
                .isInstanceOfSatisfying(RemoteServiceException.class, e -> {
                    assertThat(e.httpStatus()).isEqualTo(502);
                    assertThat(e.code()).isNull();
                });
    }

    @Test
    void readTimeout_isUnavailable() {
        ServiceEndpoint fast = new ServiceEndpoint("item-service", "http://127.0.0.1:" + port(),
                Duration.ofSeconds(1), Duration.ofMillis(300));
        ItemApi api = factory.create(ItemApi.class, fast, null);

        assertThatThrownBy(() -> api.get(4L))
                .isInstanceOfSatisfying(RemoteServiceException.class, e -> assertThat(e.isUnavailable()).isTrue());
    }

    @Test
    void connectionRefused_isUnavailable() throws IOException {
        ItemApi api = factory.create(ItemApi.class, endpoint(unusedPort()), null);

        assertThatThrownBy(() -> api.get(1L))
                .isInstanceOfSatisfying(RemoteServiceException.class, e -> assertThat(e.isUnavailable()).isTrue());
    }

    @Test
    void malformedSuccessBody_isRemoteServiceException() {
        ItemApi api = factory.create(ItemApi.class, endpoint(port()), null);

        assertThatThrownBy(() -> api.get(5L)).isInstanceOf(RemoteServiceException.class);
    }

    @Test
    void endpoint_rejectsMissingOrZeroTimeout() {
        assertThatThrownBy(() -> new ServiceEndpoint("x", "http://x", null, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ServiceEndpoint("x", "http://x", Duration.ofSeconds(1), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void capture(String auth, String corr) {
        received.clear();
        if (auth != null) {
            received.put("auth", auth);
        }
        if (corr != null) {
            received.put("corr", corr);
        }
    }

    private int port() {
        return server.getAddress().getPort();
    }

    private static ServiceEndpoint endpoint(int port) {
        return new ServiceEndpoint("item-service", "http://127.0.0.1:" + port, Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    private static int unusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", body.startsWith("{") ? "application/json" : "text/html");
        ex.sendResponseHeaders(status, bytes.length);
        try (var out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
