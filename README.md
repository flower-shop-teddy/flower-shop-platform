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
