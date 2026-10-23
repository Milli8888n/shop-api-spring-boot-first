# Kế hoạch triển khai: Cổng tài khoản học viên FPT Aptech (Mục 11)

Dự án triển khai hoàn chỉnh ứng dụng **Cổng tài khoản học viên FPT Aptech** theo đúng yêu cầu Mục 11 trong tài liệu *"Tutorial: Đăng ký và đăng nhập với Spring Security 7 + Spring Boot 4.1 + Spring Data JPA"*. Ứng dụng đáp ứng các tiêu chuẩn an ninh cấp độ doanh nghiệp, bao gồm xác thực đa yếu tố (2FA TOTP), quản lý phiên tập trung (Spring Session JDBC), kiểm soát vòng đời tài khoản 5 trạng thái, phòng chống tấn công dò mật khẩu và tấn công phân tích thời gian (Timing Attack), cùng mô hình mối đe dọa (Threat Model) chi tiết.

---

## 1. Môi trường & Quyết định kỹ thuật cốt lõi

* **Ngôn ngữ & Runtime:** Java 21 LTS (`21.0.12.1`), Maven 3.9.9.
* **Framework:** Spring Boot 3.4.x (tương thích 100% với Spring Security 6.4 Lambda DSL, Java 21 LTS), Thymeleaf + Thymeleaf Extras Spring Security 6, Bootstrap 5 qua CDN.
* **Cơ sở dữ liệu:** MySQL 8.4 chạy qua Docker Compose, tích hợp Flyway Migration để quản lý schema (`V1__init_schema.sql`).
* **Session & Cache:** Spring Session JDBC (quản lý phiên trong cơ sở dữ liệu, cho phép hủy phiên từ xa và giới hạn phiên đồng thời).
* **SMTP Mailer:** Mailpit (`axllent/mailpit`) trong `compose.yaml` (cổng SMTP 1025, Web UI 8025 để xem email xác thực và đặt lại mật khẩu).
* **Xác thực 2 bước (2FA):** Chuẩn RFC 6238 qua `dev.samstevens.totp:totp`, hiển thị QR Code Data URI và cấp 10 mã khôi phục dùng một lần. Bí mật TOTP được mã hóa AES-GCM trước khi lưu DB.
* **Tài khoản khởi tạo (Seed Admin):** Tự động seed tài khoản Quản trị viên đầu tiên trong Flyway V1: `admin@fpt.edu.vn` / mật khẩu `Admin@Aptech2026!` (trạng thái `ACTIVE`).

---

## 2. Kiến trúc hệ thống & Sơ đồ luồng dữ liệu

```mermaid
graph TD
    subgraph Browser ["Người dùng (Trình duyệt)"]
        Client[HTTP Requests]
    end

    subgraph SecurityLayer ["Spring Security Filter Chain"]
        RateLimit[RateLimiterFilter (IP-based)]
        CSP[Security Headers (CSP, HSTS, CSRF)]
        FormLogin[Form Login & Timing Attack Shield]
        SessionCtrl[Spring Session Concurrent Control (Max 2)]
    end

    subgraph CoreServices ["Core Services"]
        UserService[UserService (Vòng đời 5 trạng thái)]
        TotpService[TotpService (RFC 6238 + AES-GCM)]
        PwdService[PasswordPolicy (10k blacklist, 5 history)]
        EmailService[EmailService (Mailpit SMTP)]
        AuditService[AuditService (Audit Logs)]
    end

    subgraph Storage ["Dữ liệu & Ngoại vi"]
        MySQL[(MySQL 8.4 / Flyway Migration)]
        SpringSessionDB[(Spring Session JDBC)]
        Mailpit[Mailpit SMTP Server (:1025/:8025)]
    end

    Client --> RateLimit
    RateLimit --> CSP
    CSP --> FormLogin
    FormLogin --> SessionCtrl
    SessionCtrl --> CoreServices
    CoreServices --> Storage
```

---

## 3. Ma trận 11 Tiêu chí Nghiệm thu (F1 – F11)

| Mã | Chức năng | Tiêu chí kỹ thuật & Giải pháp cài đặt |
| :--- | :--- | :--- |
| **F1** | Đăng ký bằng email | Tạo user trạng thái `PENDING`. Sinh link xác minh gửi qua `EmailService` (Mailpit). |
| **F2** | Xác minh email | Token dùng 1 lần, hạn 24h, lưu băm SHA-256 trong bảng `tokens`. Giới hạn gửi lại link tối đa 3 lần/giờ. Xác minh xong tự động chuyển `ACTIVE` và tự động đăng nhập. |
| **F3** | Quên mật khẩu | Token 30 phút, dùng 1 lần. Phản hồi mờ (blind response) giống hệt nhau dù email có tồn tại hay không. Đặt lại mật khẩu xong lập tức hủy mọi phiên cũ. |
| **F4** | Chính sách mật khẩu | Tối thiểu 12 ký tự, không chứa username/email; không nằm trong danh sách 10.000 mật khẩu phổ biến (`common-passwords-10k.txt`); không trùng 5 mật khẩu gần nhất (`password_history`). |
| **F5** | Khóa tạm thời | Sai mật khẩu 5 lần trong 15 phút -> khóa 15 phút (`LOCKED`). Thông báo lỗi chung không lộ tài khoản tồn tại hay không. Giữ chênh lệch thời gian phản hồi $\le 50\text{ ms}$ (chống Timing Attack). |
| **F6** | Xác thực 2 bước (TOTP) | Chuẩn RFC 6238 qua `dev.samstevens.totp`, hiển thị QR code, 10 mã khôi phục dùng 1 lần. `LECTURER` và `ADMIN` chưa bật thì sau khi đăng nhập bị chuyển hướng bắt buộc vào trang thiết lập. Secret mã hóa AES-GCM. |
| **F7** | Ghi nhớ đăng nhập | Remember-me lưu `PersistentRememberMeToken` trong DB, hiệu lực 14 ngày; tự thu hồi khi đổi mật khẩu. |
| **F8** | Quản lý phiên | `Spring Session JDBC`, tối đa 2 phiên đồng thời; vượt quá sẽ đá phiên cũ nhất. Trang `/sessions` hiển thị thời điểm, IP, User-Agent và cho phép đăng xuất từ xa từng phiên. |
| **F9** | Đổi mật khẩu | Bắt buộc nhập mật khẩu hiện tại; đổi thành công thì hủy mọi phiên khác, phiên hiện tại giữ nguyên. |
| **F10** | Quản trị tài khoản | Phân trang, lọc theo vai trò (`STUDENT`, `LECTURER`, `ADMIN`), lọc trạng thái (`PENDING`, `INVITED`, `ACTIVE`, `LOCKED`, `DISABLED`), tìm kiếm email; khóa/mở khóa, gán vai trò; hệ thống luôn còn ít nhất 1 ADMIN đang hoạt động và ADMIN không thể tự gỡ quyền ADMIN của mình. |
| **F11** | Nhật ký kiểm toán | Ghi nhận mọi sự kiện: đăng nhập thành công/thất bại, đăng xuất, khóa/mở khóa, đổi mật khẩu, đổi vai trò, bật/tắt 2FA; mỗi bản ghi lưu timestamp, email, action, IP, user-agent; ADMIN có trang giao diện xem và lọc nhật ký. |

---

## 4. Các thành phần và File triển khai cụ thể

Thư mục dự án: `c:\Users\Million\ApifirstSpring\fpt-aptech-auth\`

### 4.1 Cấu hình Maven & Docker Compose
* `pom.xml`: Khai báo Spring Boot 3.4.x, Spring Data JPA, Spring Security, Spring Session JDBC, Mail, Flyway, MySQL Driver, `dev.samstevens.totp:totp`, GreenMail cho test.
* `compose.yaml`: Chạy MySQL 8.4 (port `3307:3306`) và Mailpit (SMTP `1025`, Web UI `8025`).
* `mvnw`, `mvnw.cmd`, `.mvn/wrapper/`: Bộ Maven Wrapper hỗ trợ chạy 1 lệnh `./mvnw spring-boot:run`.

### 4.2 Cơ sở dữ liệu & Flyway Migrations
* `src/main/resources/db/migration/V1__init_schema.sql`:
  * Bảng `roles` (`ROLE_STUDENT`, `ROLE_LECTURER`, `ROLE_ADMIN`).
  * Bảng `users` (id, email, username, password_hash, status, failed_attempts, locked_until, totp_secret, totp_enabled, created_at, updated_at).
  * Bảng `users_roles` (user_id, role_id).
  * Bảng `tokens` (id, token_hash, token_type, user_id, expires_at, used_at, created_at).
  * Bảng `password_history` (id, user_id, password_hash, created_at).
  * Bảng `recovery_codes` (id, user_id, code_hash, used, used_at).
  * Bảng `audit_logs` (id, event_time, email, action, ip_address, user_agent, status, details).
  * Bảng `persistent_logins` (series, username, token, last_used).
  * Bảng `SPRING_SESSION` & `SPRING_SESSION_ATTRIBUTES`.
  * Dữ liệu Seed: ADMIN ban đầu (`admin@fpt.edu.vn`).

### 4.3 Tầng Domain & Repository
* `com.fptaptech.auth.domain.*`: `AppUser`, `Role`, `UserStatus`, `TokenEntity`, `TokenType`, `PasswordHistoryEntity`, `RecoveryCodeEntity`, `AuditLogEntity`.
* `com.fptaptech.auth.repository.*`: `UserRepository`, `RoleRepository`, `TokenRepository`, `PasswordHistoryRepository`, `RecoveryCodeRepository`, `AuditLogRepository`.

### 4.4 Tầng Bảo mật & Tiện ích
* `com.fptaptech.auth.config.SecurityConfig.java`: SecurityFilterChain Lambda DSL, Form Login, CSRF, CSP, HSTS, Session Management.
* `com.fptaptech.auth.security.TimingDefenseService.java`: Băm giả lập để cân bằng thời gian phản hồi (chống Timing Attack).
* `com.fptaptech.auth.security.TotpService.java`: Quản lý secret mã hóa AES-GCM, sinh mã QR Data URI và xác minh OTP.
* `com.fptaptech.auth.security.RateLimiterService.java`: Giới hạn IP cho đăng ký và quên mật khẩu.

### 4.5 Tầng Nghiệp vụ (Service)
* `com.fptaptech.auth.service.UserService.java`: Luồng đăng ký, xác thực token, đổi mật khẩu, quên mật khẩu.
* `com.fptaptech.auth.service.PasswordPolicyValidator.java`: Đọc từ điển 10k mật khẩu và lịch sử mật khẩu.
* `com.fptaptech.auth.service.SessionManagementService.java`: Truy vấn danh sách phiên hoạt động và hủy phiên từ xa.
* `com.fptaptech.auth.service.AdminService.java`: Quản lý học viên/giảng viên, mời người dùng mới, khóa/mở khóa.
* `com.fptaptech.auth.service.AuditService.java`: Ghi vết mọi sự kiện hệ thống.

### 4.6 Giao diện Web (Thymeleaf + Bootstrap 5)
* 14 templates: `login.html`, `registration.html`, `verify-email.html`, `forgot-password.html`, `reset-password.html`, `2fa-setup.html`, `2fa-verify.html`, `welcome.html`, `profile.html`, `change-password.html`, `sessions.html`, `lecturer/students.html`, `admin/users.html`, `admin/audit-logs.html`.

---

## 5. Kế hoạch Kiểm thử (Verification Plan)

| Tầng kiểm thử | Test Class | Số lượng test | Mục tiêu kiểm chứng |
| :--- | :--- | :---: | :--- |
| **Unit Test** | `PasswordPolicyTest`<br>`TotpTest` | +12 | Kiểm thử độ dài, từ điển 10k, trùng 5 pass gần nhất; sinh mã TOTP, mã khôi phục. |
| **Integration Test** | `AccountLockoutTest`<br>`SessionManagementTest`<br>`AdminRulesTest`<br>`AuditLogTest` | +18 | Sai 5 lần khóa 15m; delta thời gian $\le 50\text{ ms}$; 2 phiên đồng thời, đá phiên cũ; cấm xóa Admin cuối cùng; ghi vết nhật ký. |
| **E2E Web Flow** | `AuthFlowComprehensiveTest` | +10 | Đăng ký -> Mailpit/GreenMail -> click link -> tự login -> 2FA -> đổi pass -> logout. |
| **Tổng cộng** | Chạy với H2 (MODE=MySQL) | **~40 tests** | Đảm bảo 100% test pass không phụ thuộc Docker ngoài. |

---

## 6. Lộ trình thực hiện (Execution Timeline)

1. **Bước 1 (Khung dự án & Docker):** Khởi tạo `pom.xml`, `compose.yaml`, Flyway migration V1, cấu hình cấu trúc thư mục.
2. **Bước 2 (Domain & Database):** Tạo các Entity, Repository và seed dữ liệu.
3. **Bước 3 (Security Core & Services):** Cài đặt `SecurityConfig`, `UserService`, `PasswordPolicyValidator`, `TotpService` (AES-GCM), `TimingDefenseService`, `SessionManagementService`.
4. **Bước 4 (Controllers & Giao diện Thymeleaf):** Xây dựng toàn bộ các trang giao diện người dùng và quản trị viên với Bootstrap 5.
5. **Bước 5 (Bộ kiểm thử tự động):** Viết và chạy bộ test suite toàn diện phủ F1–F11.
6. **Bước 6 (Tài liệu):** Soạn thảo `docs/THREAT-MODEL.md` (mô hình mối đe dọa STRIDE/OWASP) và `README.md` hoàn chỉnh.
