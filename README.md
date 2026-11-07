# flower-shop-platform

Phần dùng chung cho mọi service của Flower Shop:

| Artifact | Vai trò |
|---|---|
| `com.flowershop:flower-shop-parent` (`pom.xml`) | Parent POM: version Spring Boot, Spring Cloud, springdoc, testcontainers, cấu hình Lombok |
| `com.flowershop:common-lib` | Response chuẩn, mã lỗi, xử lý lỗi, correlation id, che dữ liệu. **Không chứa logic nghiệp vụ.** |

## Dùng trong service

```xml
<parent>
    <groupId>com.flowershop</groupId>
    <artifactId>flower-shop-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../platform/pom.xml</relativePath>
</parent>
```

## Build & publish

- Local: `mvn install` (cài vào `~/.m2`), hoặc build cả workspace từ repo `flower-shop-workspace`.
- GitHub: push lên `dev` thì workflow `publish.yml` tự `mvn deploy` bản `-SNAPSHOT` lên GitHub Packages của org.

## Đổi version

Sửa **cả hai** chỗ trong `pom.xml`: `<version>` của project và property `flower-shop-common.version`.
Sau đó cập nhật version parent trong từng service muốn dùng bản mới.
