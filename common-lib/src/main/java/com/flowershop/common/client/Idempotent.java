package com.flowershop.common.client;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu một lời gọi KHÔNG GET nhưng gọi lại nhiều lần vẫn cho cùng kết quả (vd giữ hàng theo orderCode),
 * để {@link ServiceClientFactory} được phép tự thử lại khi service đích tạm thời không phản hồi.
 * <p>
 * GET luôn được thử lại, không cần đánh dấu. POST/PUT/DELETE KHÔNG đánh dấu thì không bao giờ thử lại:
 * timeout không có nghĩa là bên kia chưa xử lý, gọi lại có thể tạo trùng (vd tạo hai hồ sơ khách).
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}
