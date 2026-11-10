package com.flowershop.common.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowershop.common.web.CorrelationId;
import org.aopalliance.intercept.MethodInterceptor;
import org.slf4j.MDC;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.function.Supplier;

/**
 * Tạo client gọi service khác, đã gắn sẵn mọi thứ bắt buộc của hệ thống:
 * <ul>
 *   <li>timeout (lấy từ {@link ServiceEndpoint}, không cho phép chờ vô hạn)</li>
 *   <li>header {@code X-Correlation-Id} lấy từ MDC: log của hai service nối được với nhau</li>
 *   <li>header {@code Authorization: Bearer} (zero trust: service đích không tin ai không có token)</li>
 *   <li>mọi lỗi đổi thành {@link RemoteServiceException}, kèm mã lỗi gốc của service đích</li>
 * </ul>
 * Khai báo client dạng interface ({@code @HttpExchange}, giống OpenFeign nhưng có sẵn trong Spring):
 * <pre>
 * interface CustomerApi {
 *     &#64;PostExchange("/api/v1/customers")
 *     ApiResponse&lt;CustomerDto&gt; create(&#64;RequestBody CreateCustomerBody body);
 * }
 * CustomerApi api = factory.create(CustomerApi.class, endpoint, serviceToken::get);
 * </pre>
 * Không phải {@code @Component}: service nào gọi service khác thì tự khai bean, truyền vào builder có
 * {@code @LoadBalanced} để URL dạng {@code http://customer-service} được tra trên Eureka.
 */
public final class ServiceClientFactory {

    private final RestClient.Builder builder;
    private final ObjectMapper objectMapper;

    public ServiceClientFactory(RestClient.Builder builder, ObjectMapper objectMapper) {
        this.builder = builder;
        this.objectMapper = objectMapper;
    }

    /**
     * @param token null nếu API đích là public (không gửi Authorization)
     */
    public <T> T create(Class<T> api, ServiceEndpoint endpoint, Supplier<String> token) {
        T client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient(endpoint, token)))
                .build()
                .createClient(api);
        return wrapUnexpectedErrors(api, client, endpoint.name());
    }

    /** Khi cần dùng thẳng RestClient thay vì interface. Lỗi HTTP và lỗi kết nối vẫn là {@link RemoteServiceException}. */
    public RestClient restClient(ServiceEndpoint endpoint, Supplier<String> token) {
        // java.net.http.HttpClient thay vì HttpURLConnection (SimpleClientHttpRequestFactory): HttpURLConnection
        // xử lý riêng response 401 và làm mất body lỗi, nên không đọc được mã lỗi (vd IDN-401-003).
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(endpoint.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(endpoint.readTimeout());

        return builder.clone()
                .baseUrl(endpoint.baseUrl())
                .requestFactory(requestFactory)
                // Đứng ĐẦU chuỗi (trước cả interceptor của load balancer) để bắt được lỗi "không có instance"
                .requestInterceptors(list -> list.add(0, unavailableTranslator(endpoint.name())))
                .requestInterceptor(correlationId())
                .requestInterceptor(bearer(token))
                .defaultStatusHandler(HttpStatusCode::isError, new RemoteErrorHandler(endpoint.name(), objectMapper))
                .build();
    }

    /**
     * IOException: từ chối kết nối, hết thời gian chờ.
     * IllegalStateException: load balancer không tìm thấy instance nào trên Eureka.
     */
    private static ClientHttpRequestInterceptor unavailableTranslator(String service) {
        return (request, body, execution) -> {
            try {
                return execution.execute(request, body);
            } catch (IOException | IllegalStateException e) {
                throw RemoteServiceException.unavailable(service, e);
            }
        };
    }

    private static ClientHttpRequestInterceptor correlationId() {
        return (request, body, execution) -> {
            String correlationId = MDC.get(CorrelationId.MDC_KEY);
            if (correlationId != null && !request.getHeaders().containsKey(CorrelationId.HEADER)) {
                request.getHeaders().set(CorrelationId.HEADER, correlationId);
            }
            return execution.execute(request, body);
        };
    }

    private static ClientHttpRequestInterceptor bearer(Supplier<String> token) {
        return (request, body, execution) -> {
            if (token != null) {
                request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token.get());
            }
            return execution.execute(request, body);
        };
    }

    /**
     * Lưới an toàn: lỗi còn sót của RestClient (vd đứt kết nối khi đang đọc body, body sai format)
     * cũng thành {@link RemoteServiceException}, để code gọi chỉ phải bắt một loại lỗi.
     */
    @SuppressWarnings("unchecked")
    private static <T> T wrapUnexpectedErrors(Class<T> api, T target, String service) {
        // ProxyFactory (Spring AOP) thay vì java.lang.reflect.Proxy: gọi được cả interface package-private
        // khai báo ở package của service (reflection thuần bị chặn IllegalAccessException).
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addInterface(api);
        proxy.addAdvice((MethodInterceptor) invocation -> {
            try {
                return invocation.proceed();
            } catch (RestClientException e) {
                throw RemoteServiceException.unavailable(service, e);
            }
        });
        return (T) proxy.getProxy(api.getClassLoader());
    }
}
