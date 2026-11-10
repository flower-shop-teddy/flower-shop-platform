# flower-shop-platform

Phần dùng chung cho mọi service của Flower Shop:

| Artifact | Vai trò |
|---|---|
| `com.flowershop:flower-shop-parent` (`pom.xml`) | Parent POM: version Spring Boot, Spring Cloud, springdoc, testcontainers, cấu hình Lombok |
| `com.flowershop:common-lib` | Response chuẩn, mã lỗi, xử lý lỗi, correlation id, che dữ liệu, mảnh security dùng chung, client gọi service khác (`client/ServiceClientFactory`: timeout, token, correlation id, circuit breaker + retry Resilience4j, `@Idempotent`). **Không chứa logic nghiệp vụ.** |

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
- Push `dev`: `publish-snapshot.yml` publish bản `-SNAPSHOT` lên GitHub Packages.
- Merge `dev → production`: `release.yml` publish bản chính thức `X.Y.Z` + tag `vX.Y.Z`.

## CI dùng chung cho mọi repo

| File | Vai trò |
|---|---|
| `.github/actions/setup-maven` | Cài JDK + settings.xml đọc/ghi GitHub Packages bằng `GITHUB_TOKEN` |
| `.github/workflows/maven-ci.yml` | Build + test (check `ci / build`) |
| `.github/workflows/service-release.yml` | Release service thành Docker image trên GHCR |
| `.github/workflows/commit-lint.yml` | Kiểm tra Conventional Commits |

Service gọi bằng `uses: flower-shop-teddy/flower-shop-platform/.github/workflows/<file>@dev`. Sửa ở `dev` của repo này là mọi service áp dụng ngay, nên hãy cẩn thận.

## Đổi version

Dùng `./set-platform-version.sh <version>` ở repo workspace: script sửa cả `<version>` lẫn property `flower-shop-common.version` (hai chỗ phải luôn bằng nhau, workflow release sẽ kiểm tra), và cập nhật parent của mọi service.

## Gọi service khác (`com.flowershop.common.client`)

```java
interface InventoryApi {
    @GetExchange("/api/v1/inventory/stock")      // GET: tự thử lại khi service đích chập chờn
    ApiResponse<Stock> stock(@RequestParam String sku);

    @Idempotent                                   // POST idempotent: được thử lại
    @PostExchange("/internal/v1/reservations")
    ApiResponse<Reservation> reserve(@RequestBody ReserveBody body);
}
InventoryApi api = factory.create(InventoryApi.class, endpoint, serviceTokenClient);
```

| Tình huống | Kết quả |
|---|---|
| Không kết nối được / timeout / 502-503-504, lời gọi idempotent | Thử lại tối đa 3 lần (chờ ~200ms, ~400ms) |
| Lời gọi POST không có `@Idempotent` | Không bao giờ thử lại (có thể tạo trùng) |
| ≥50% của 20 lời gọi gần nhất là sự cố (cần ≥10 lời gọi) | Ngắt mạch 15s: trả `RemoteServiceException` ngay, không gửi request |
| Lỗi nghiệp vụ 4xx (409 hết hàng, 404...) | Không thử lại, không tính là sự cố |
| Xin token ở identity lỗi khi đang gọi service X | Không tính là sự cố của X |

Đổi ngưỡng: `new ServiceClientFactory(builder, objectMapper, new ResilienceSettings(...))`.
