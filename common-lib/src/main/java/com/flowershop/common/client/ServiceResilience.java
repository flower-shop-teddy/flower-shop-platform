package com.flowershop.common.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Circuit breaker (một cái cho mỗi service ĐÍCH, dùng chung cho mọi client gọi tới service đó) + retry.
 * <p>
 * Chỉ tính là LỖI khi chính service đích có vấn đề: không gọi được, hết thời gian chờ, trả 5xx.
 * Lỗi nghiệp vụ (409 hết hàng, 404, 422...) là câu trả lời BÌNH THƯỜNG, không làm ngắt mạch và không thử lại.
 * Lỗi của service KHÁC trong cùng lời gọi (vd xin token ở identity thất bại khi đang gọi inventory)
 * không bị tính cho service đích.
 */
final class ServiceResilience {

    private static final Logger log = LoggerFactory.getLogger(ServiceResilience.class);
    /** Bad Gateway / Service Unavailable / Gateway Timeout: thường là tạm thời, thử lại có ích. 500 thì không. */
    private static final Set<Integer> RETRYABLE_STATUS = Set.of(502, 503, 504);

    private final ResilienceSettings settings;
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, Retry> retries = new ConcurrentHashMap<>();

    ServiceResilience(ResilienceSettings settings) {
        this.settings = settings;
    }

    <T> T call(String service, boolean idempotent, Supplier<T> action) {
        Supplier<T> guarded = CircuitBreaker.decorateSupplier(circuitBreaker(service), action);
        if (idempotent && settings.maxAttempts() > 1) {
            // Retry BỌC NGOÀI circuit breaker: mỗi lần thử đều được tính, mạch ngắt thì dừng thử ngay
            guarded = Retry.decorateSupplier(retry(service), guarded);
        }
        try {
            return guarded.get();
        } catch (CallNotPermittedException e) {
            // Mạch đang ngắt: trả lỗi ngay, không đợi timeout. Code gọi xử lý như "không gọi được"
            throw RemoteServiceException.unavailable(service, e);
        }
    }

    CircuitBreaker.State state(String service) {
        return circuitBreaker(service).getState();
    }

    private CircuitBreaker circuitBreaker(String service) {
        return circuitBreakers.computeIfAbsent(service, name -> {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(settings.windowSize())
                    .minimumNumberOfCalls(settings.minimumCalls())
                    .failureRateThreshold(settings.failureRatePercent())
                    .waitDurationInOpenState(settings.openDuration())
                    .permittedNumberOfCallsInHalfOpenState(settings.halfOpenCalls())
                    .recordException(e -> isOutage(service, e))
                    .build();
            CircuitBreaker cb = CircuitBreaker.of(service, config);
            cb.getEventPublisher().onStateTransition(event -> log.warn("Circuit breaker [{}]: {}",
                    service, event.getStateTransition()));
            return cb;
        });
    }

    private Retry retry(String service) {
        return retries.computeIfAbsent(service, name -> {
            RetryConfig config = RetryConfig.custom()
                    .maxAttempts(settings.maxAttempts())
                    .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(settings.initialBackoff(), 2.0, 0.5))
                    .retryOnException(e -> isRetryable(service, e))
                    .build();
            Retry retry = Retry.of(service, config);
            retry.getEventPublisher().onRetry(event -> log.info("Retry [{}] lần {}: {}", service,
                    event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()));
            return retry;
        });
    }

    private static boolean isOutage(String service, Throwable e) {
        return e instanceof RemoteServiceException r && service.equals(r.service())
                && (r.isUnavailable() || r.httpStatus() >= 500);
    }

    private static boolean isRetryable(String service, Throwable e) {
        return e instanceof RemoteServiceException r && service.equals(r.service())
                && (r.isUnavailable() || RETRYABLE_STATUS.contains(r.httpStatus()));
    }
}
