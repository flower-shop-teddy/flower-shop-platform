package com.flowershop.common.client;

/**
 * Lỗi DUY NHẤT mà client tạo bởi {@link ServiceClientFactory} ném ra khi gọi service khác thất bại.
 * Hai trường hợp:
 * <ul>
 *   <li>{@link #isUnavailable()}: không gọi được (không có instance trên Eureka, từ chối kết nối, hết thời gian chờ).
 *       Không biết service đích đã xử lý hay chưa, nên chỉ thử lại nếu thao tác là idempotent.</li>
 *   <li>Service đích trả lỗi HTTP: có {@link #httpStatus()} và {@link #code()} (mã lỗi gốc, vd CUS-409-001,
 *       null nếu body không phải {@code ApiResponse}).</li>
 * </ul>
 * Service gọi tự dịch sang mã lỗi của mình: client bên ngoài không cần biết bên trong có mấy service.
 * Message KHÔNG chứa body của response (có thể có dữ liệu cá nhân).
 */
public class RemoteServiceException extends RuntimeException {

    private static final int NO_RESPONSE = 0;

    private final String service;
    private final int httpStatus;
    private final String code;

    private RemoteServiceException(String service, int httpStatus, String code, String message, Throwable cause) {
        super(message, cause);
        this.service = service;
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public static RemoteServiceException unavailable(String service, Throwable cause) {
        return new RemoteServiceException(service, NO_RESPONSE, null,
                "Không gọi được " + service + ": " + cause.getClass().getSimpleName(), cause);
    }

    public static RemoteServiceException rejected(String service, int httpStatus, String code) {
        return new RemoteServiceException(service, httpStatus, code,
                service + " trả lỗi HTTP " + httpStatus + (code == null ? "" : " code=" + code), null);
    }

    public String service() {
        return service;
    }

    /** 0 nếu không nhận được response. */
    public int httpStatus() {
        return httpStatus;
    }

    /** Mã lỗi gốc của service đích, có thể null. */
    public String code() {
        return code;
    }

    public boolean isUnavailable() {
        return httpStatus == NO_RESPONSE;
    }

    public boolean hasCode(String expected) {
        return expected.equals(code);
    }
}
