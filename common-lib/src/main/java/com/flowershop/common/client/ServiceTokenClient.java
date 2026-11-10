package com.flowershop.common.client;

import com.flowershop.common.api.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Xin service token từ identity-service (client_credentials) và dùng lại tới gần hết hạn.
 * Là một {@code Supplier<String>}: truyền thẳng vào {@link ServiceClientFactory#create}:
 * <pre>
 * ServiceTokenClient token = new ServiceTokenClient(factory, identityEndpoint, "order-service", secret, clock);
 * CustomerApi customers = factory.create(CustomerApi.class, customerEndpoint, token);
 * </pre>
 * Thread-safe: nhiều request cùng lúc thấy token sắp hết hạn thì chỉ MỘT request đi xin token mới.
 * Không xin được token thì ném {@link RemoteServiceException} (service="identity-service").
 */
public final class ServiceTokenClient implements Supplier<String> {

    static final String TOKEN_PATH = "/internal/v1/service-token";
    /** Làm mới trước khi hết hạn: tránh token hết hạn giữa đường (lệch giờ giữa các máy, request chậm). */
    private static final Duration RENEW_BEFORE = Duration.ofMinutes(1);

    private record CachedToken(String value, Instant renewAt) {
    }

    private record TokenBody(String tokenType, String accessToken, long expiresIn) {
    }

    private static final ParameterizedTypeReference<ApiResponse<TokenBody>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final String service;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;
    private volatile CachedToken cached;

    public ServiceTokenClient(ServiceClientFactory factory, ServiceEndpoint identity, String clientId,
                              String clientSecret, Clock clock) {
        this.restClient = factory.restClient(identity, null);   // chưa có token: đây chính là chỗ xin token
        this.service = identity.name();
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.clientSecret = Objects.requireNonNull(clientSecret, "clientSecret");
        this.clock = clock;
    }

    @Override
    public String get() {
        CachedToken token = cached;
        if (token == null || !clock.instant().isBefore(token.renewAt())) {
            synchronized (this) {
                token = cached;
                if (token == null || !clock.instant().isBefore(token.renewAt())) {
                    token = fetch();
                    cached = token;
                }
            }
        }
        return token.value();
    }

    private CachedToken fetch() {
        Instant requestedAt = clock.instant();
        ApiResponse<TokenBody> response;
        try {
            response = restClient.post()
                    .uri(TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("clientId", clientId, "clientSecret", clientSecret))
                    .retrieve()
                    .body(RESPONSE_TYPE);
        } catch (RestClientException e) {
            throw RemoteServiceException.unavailable(service, e);
        }
        TokenBody body = response == null ? null : response.data();
        if (body == null || body.accessToken() == null || body.expiresIn() <= 0) {
            throw RemoteServiceException.unavailable(service, new IllegalStateException("Response không có token"));
        }
        Duration ttl = Duration.ofSeconds(body.expiresIn());
        // TTL ngắn hơn 2 phút: làm mới khi còn nửa thời gian, nếu không token vừa nhận đã bị coi là hết hạn
        Duration renewBefore = ttl.compareTo(RENEW_BEFORE.multipliedBy(2)) < 0 ? ttl.dividedBy(2) : RENEW_BEFORE;
        // Tính từ lúc GỬI request (không phải lúc nhận): an toàn hơn khi mạng chậm
        return new CachedToken(body.accessToken(), requestedAt.plus(ttl).minus(renewBefore));
    }

    /** Không bao giờ in secret ra log. */
    @Override
    public String toString() {
        return "ServiceTokenClient[clientId=" + clientId + "]";
    }
}
