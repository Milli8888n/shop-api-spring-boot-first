# ĐẶC TẢ MÔ TẢ CHỨC NĂNG – PHIÊN ĐĂNG NHẬP AN TOÀN CHO CATALOG API
> **Học phần**: Phát triển Ứng dụng Doanh nghiệp với Spring Boot & Spring Security  
> **Căn cứ tài liệu**: *Tutorial: Bảo vệ REST API bằng JWT với Spring Security 7 (OAuth2 Resource Server)* — Mục 11 (Trang 27–29)  
> **Dự án**: `catalog` (Package: `com.fptaptech.catalog`)  
> **Phiên bản**: 1.0.0 · Ngày lập: 24/10/2026  

---

## 1. TỔNG QUAN HỆ THỐNG & ĐỐI TƯỢNG SỬ DỤNG

### 1.1. Bối cảnh Nghiệp vụ
Catalog API phục vụ ứng dụng mua sắm trực tuyến (Storefront Web & Mobile App). Để cân bằng giữa tính **bảo mật tối đa** và **trải nghiệm người dùng mượt mà**, hệ thống áp dụng cơ chế xác thực kép:
* **Access Token**: Định dạng JWT (JSON Web Token), ký số bằng thuật toán RSA (RS256), thời hạn sống ngắn (**15 phút**). Được gửi trong header `Authorization: Bearer <token>` ở mọi yêu cầu API cần xác thực.
* **Refresh Token**: Chuỗi định danh ngẫu nhiên mã hóa mạnh 256-bit (**không phải JWT**), thời hạn sống dài (**7 ngày**). Được dùng để đổi lấy cặp Access Token + Refresh Token mới mà không bắt người dùng phải nhập lại username/mật khẩu.

### 1.2. Các Vai trò & Nhóm người dùng (User Roles)
Hệ thống phân cấp người dùng thành 3 nhóm quyền:

| Vai trò / Đối tượng | Ký hiệu Quyền | Quyền hạn & Trách nhiệm |
| :--- | :--- | :--- |
| **Khách vãng lai** | `ANONYMOUS` | <ul><li>Xem tài liệu Swagger UI tại `/swagger-ui.html` và file đặc tả `/v3/api-docs`.</li><li>Truy vấn danh mục sản phẩm (`GET /api/v1/products/**`).</li><li>Lấy danh sách Public Keys tại `GET /.well-known/jwks.json`.</li><li>Gửi yêu cầu đăng nhập đổi mật khẩu lấy token (`POST /api/auth/token`).</li></ul> |
| **Người dùng thường** | `ROLE_USER`<br>Scope: `product:read` | <ul><li>Sở hữu toàn bộ quyền của Khách vãng lai.</li><li>Đổi Refresh Token lấy phiên mới (`POST /api/auth/refresh`).</li><li>Xem thông tin phiên đăng nhập của chính mình (`GET /api/auth/me`).</li><li>Đăng xuất phiên hiện tại (`POST /api/auth/logout`) hoặc đăng xuất mọi thiết bị (`POST /api/auth/logout-all`).</li><li>Thực hiện đổi mật khẩu cá nhân (`POST /api/auth/change-password`).</li></ul> |
| **Quản trị viên** | `ROLE_ADMIN`<br>Scope: `product:read`<br>Scope: `product:write` | <ul><li>Sở hữu toàn bộ quyền của `ROLE_USER`.</li><li>Thực hiện các thao tác thay đổi danh mục sản phẩm: Thêm mới (`POST`), Cập nhật (`PUT`), Xóa (`DELETE`) trên `/api/v1/products/**`.</li><li>Khóa hoặc mở khóa tài khoản người dùng (lập tức vô hiệu hóa phiên của user bị khóa).</li></ul> |

---

## 2. QUY ƯỚC CHUNG VỀ GIAO TIẾP RESTFUL API

| Hạng mục | Quy định chuẩn |
| :--- | :--- |
| **Base URL** | `http://localhost:8080` (API Auth: `/api/auth/**`, Catalog API: `/api/v1/products/**`) |
| **Định dạng dữ liệu** | `Content-Type: application/json; charset=UTF-8`, `Accept: application/json` |
| **Chuẩn Token** | HTTP Header `Authorization: Bearer <accessToken>` |
| **Chuẩn thời gian** | Chuẩn ISO-8601 UTC (ví dụ: `2026-10-24T00:00:00Z`) hoặc Unix Epoch Seconds |
| **Chuẩn tiền tệ** | `BigDecimal`, 2 chữ số thập phân, ví dụ `350000.00` |
| **Chuẩn báo lỗi** | RFC 9457 `ProblemDetail` (`Content-Type: application/problem+json`) |

---

## 3. DANH SÁCH & MÔ TẢ CHI TIẾT CÁC CHỨC NĂNG (R1 – R11)

### 3.1. [R1] Cấp Token lần đầu (User Login & Issue Initial Tokens)
* **Endpoint**: `POST /api/auth/token`
* **Xác thực**: Public (Ai cũng được gọi).
* **Mô tả**: Tiếp nhận username và password, kiểm tra đối soát với cơ sở dữ liệu. Nếu chính xác, cấp phát cặp Access Token + Refresh Token đầu tiên.
* **Request Body**:
  ```json
  {
    "username": "admin",
    "password": "Admin#Catalog2026"
  }
  ```
* **Phản hồi thành công (200 OK)**:
  ```json
  {
    "accessToken": "eyJraWQiOiJjYXRhbG9nLWtleS0xIiwiYWxnIjoiUlMyNTYiLCJ0eXAiOiJKV1QifQ...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "refreshToken": "x7bF9_q2Lk0mNp8Vr4Wz1AbC3dE5fG7hI9jK0lM2nO4"
  }
  ```
* **Cơ chế nghiệp vụ**:
  * Tạo chuỗi `refreshToken` ngẫu nhiên 256-bit (Base64URL không padding).
  * Khởi tạo `family_id` mới dạng UUID để theo dõi chuỗi xoay vòng token.
  * Chỉ lưu giá trị băm **SHA-256** của refresh token vào CSDL kèm `expires_at` (7 ngày), địa chỉ IP và User-Agent của client lúc cấp.

---

### 3.2. [R2] Xoay vòng Refresh Token (Refresh Token Rotation)
* **Endpoint**: `POST /api/auth/refresh`
* **Xác thực**: Public (Xác thực qua giá trị Refresh Token).
* **Mô tả**: Cho phép client gia hạn phiên khi Access Token hết hạn (15 phút).
* **Request Body**:
  ```json
  {
    "refreshToken": "x7bF9_q2Lk0mNp8Vr4Wz1AbC3dE5fG7hI9jK0lM2nO4"
  }
  ```
* **Quy tắc Xoay vòng (Rotation)**:
  1. Server băm SHA-256 token gửi lên và tìm kiếm trong bảng `refresh_tokens`.
  2. Nếu token hợp lệ (`revoked_at IS NULL` và `expires_at > NOW()`):
     * Đánh dấu token cũ là **đã dùng** (`revoked_at = NOW()`).
     * Cấp **Access Token mới** (15 phút).
     * Cấp **Refresh Token mới** (7 ngày) nhưng **giữ nguyên `family_id` cũ**.
     * Trả về cặp token mới cho client.

---

### 3.3. [R3] Phát hiện dùng lại Refresh Token (Token Reuse Detection)
* **Endpoint**: `POST /api/auth/refresh`
* **Mô tả**: Bảo vệ chống lại tấn công Replay Attack (khi kẻ gian đánh cắp được refresh token cũ và cố tình gửi lại sau khi người dùng hợp pháp đã xoay vòng).
* **Kịch bản xử lý**:
  * Nếu client gửi lên một refresh token mà trong CSDL đã có `revoked_at IS NOT NULL` (token này đã từng được sử dụng trước đó):
  * **Hành động trừng phạt tức thì**:
    1. Lập tức **thu hồi toàn bộ các Refresh Token** có cùng `family_id` trong hệ thống (`UPDATE refresh_tokens SET revoked_at = NOW() WHERE family_id = :familyId`).
    2. Ghi nhận nhật ký cảnh báo mức **`WARN`**: `"Phát hiện dùng lại Refresh Token trái phép! Thu hồi toàn bộ chuỗi family_id={}"`.
    3. Trả về mã lỗi HTTP **401 Unauthorized** kèm mã lỗi nghiệp vụ `REFRESH_TOKEN_REUSED`.
* **Phản hồi lỗi (401 Unauthorized)**:
  ```json
  {
    "type": "urn:problem-type:refresh-token-reused",
    "title": "Token Reuse Detected",
    "status": 401,
    "detail": "Refresh token đã được sử dụng trước đó. Toàn bộ phiên liên quan đã bị thu hồi vì lý do an toàn.",
    "code": "REFRESH_TOKEN_REUSED",
    "timestamp": "2026-10-24T00:30:00Z"
  }
  ```

---

### 3.4. [R4] Đăng xuất một phiên (Logout Current Session)
* **Endpoint**: `POST /api/auth/logout`
* **Xác thực**: Yêu cầu Bearer Access Token hợp lệ.
* **Mô tả**: Hủy phiên làm việc hiện tại của người dùng.
* **Request Body** (tùy chọn hoặc truyền refresh token):
  ```json
  {
    "refreshToken": "x7bF9_q2Lk0mNp8Vr4Wz1AbC3dE5fG7hI9jK0lM2nO4"
  }
  ```
* **Cơ chế xử lý**:
  1. Thu hồi `refreshToken` tương ứng trong CSDL (`revoked_at = NOW()`).
  2. Trích xuất định danh duy nhất `jti` (UUID) và thời điểm hết hạn `exp` từ Access Token đang gọi.
  3. Đưa `jti` vào **Danh sách thu hồi (Revocation List / Blacklist)** trong CSDL và Cache in-memory.
  4. Bất kỳ request nào tiếp theo sử dụng Access Token này đều bị từ chối với HTTP 401.
* **Phản hồi thành công**: `204 No Content` hoặc `200 OK`.

---

### 3.5. [R5] Đăng xuất mọi thiết bị (Logout From All Devices)
* **Endpoint**: `POST /api/auth/logout-all`
* **Xác thực**: Yêu cầu Bearer Access Token hợp lệ.
* **Mô tả**: Vô hiệu hóa toàn bộ các phiên làm việc của người dùng trên tất cả các trình duyệt và thiết bị di động (ví dụ khi nghi ngờ tài khoản bị lộ).
* **Cơ chế xử lý**:
  1. Đánh dấu thu hồi **toàn bộ Refresh Token** của người dùng hiện tại (`user_id`).
  2. Tăng giá trị `token_version` trong bảng `users` lên 1 đơn vị (`token_version = token_version + 1`).
  3. Cập nhật cache trạng thái người dùng.
  4. Mọi Access Token đã phát hành trước đó (mang claim `ver < token_version`) lập tức bị từ chối khi gọi API.
* **Phản hồi thành công**: `204 No Content` hoặc `200 OK`.

---

### 3.6. [R6] Thu hồi Token khi Đổi Mật Khẩu hoặc Khóa Tài Khoản
* **Endpoint Đổi mật khẩu**: `POST /api/auth/change-password`
* **Request Body**:
  ```json
  {
    "currentPassword": "Admin#Catalog2026",
    "newPassword": "Admin#NewPassword2026!"
  }
  ```
* **Cơ chế bảo mật**:
  * Kiểm tra tính đúng đắn của `currentPassword`.
  * Cập nhật mật khẩu mới băm bằng `PasswordEncoder`.
  * **Tự động tăng `token_version = token_version + 1`** và thu hồi toàn bộ Refresh Token.
  * Tương tự, khi Quản trị viên thực hiện khóa tài khoản (Status chuyển sang `LOCKED`), hệ thống cũng tăng `token_version` để chấm dứt quyền truy cập tức thì của tài khoản đó.

---

### 3.7. [R7 & R8] Xoay Khóa Ký RSA & Công bố JWKS
* **Chức năng Xoay Khóa (R7)**:
  * Hệ thống hỗ trợ nạp cấu hình đồng thời ít nhất **2 cặp khóa RSA** (ví dụ `catalog-key-1` và `catalog-key-2`).
  * Thuộc tính cấu hình `app.jwt.active-kid` xác định khóa dùng để ký Access Token mới.
  * Khi tiến hành xoay khóa định kỳ: Chỉ cần thay đổi `active-kid` trong file `application.yaml` sang khóa mới. Không cần sửa mã nguồn và không làm gián đoạn các token cũ đang lưu hành.
* **Endpoint Công bố Khóa (R8)**: `GET /.well-known/jwks.json`
  * **Xác thực**: Hoàn toàn công khai (Public).
  * **Mô tả**: Trả về tập hợp các Public Key đang hiệu lực theo chuẩn JWK Set (RFC 7517) để các dịch vụ hoặc API Gateway xác thực chữ ký.
  * **Phản hồi mẫu**:
    ```json
    {
      "keys": [
        {
          "kty": "RSA",
          "kid": "catalog-key-1",
          "use": "sig",
          "alg": "RS256",
          "n": "u1w7q8x...",
          "e": "AQAB"
        },
        {
          "kty": "RSA",
          "kid": "catalog-key-2",
          "use": "sig",
          "alg": "RS256",
          "n": "v9z4b2k...",
          "e": "AQAB"
        }
      ]
    }
    ```
  * **Quy tắc an ninh**: Tuyệt đối không bao giờ để lộ các thành phần khóa bí mật (`d`, `p`, `q`, `dp`, `dq`, `qi`).

---

### 3.8. [R9] Phân quyền theo Scope trên Catalog API
* **Mô tả**: Quyền hạn của Access Token được biểu diễn bằng claim `scope`, sinh ra từ vai trò người dùng:
  * `ROLE_USER` $\rightarrow$ gán scope `product:read`
  * `ROLE_ADMIN` $\rightarrow$ gán scope `product:read product:write`
* **Quy tắc truy cập tài nguyên Sản phẩm**:

| Endpoint | HTTP Method | Scope yêu cầu | Ghi chú nghiệp vụ |
| :--- | :---: | :--- | :--- |
| `/api/v1/products` | `GET` | Không yêu cầu (Public) | Xem danh mục sản phẩm |
| `/api/v1/products/{id}` | `GET` | Không yêu cầu (Public) | Xem chi tiết sản phẩm |
| `/api/v1/products` | `POST` | `SCOPE_product:write` | Thêm sản phẩm mới (Chỉ ADMIN) |
| `/api/v1/products/{id}` | `PUT` | `SCOPE_product:write` | Sửa sản phẩm (Chỉ ADMIN) |
| `/api/v1/products/{id}` | `DELETE`| `SCOPE_product:write` | Xóa sản phẩm (Chỉ ADMIN) |

---

### 3.9. [R10] Giới hạn tần suất gọi API (Rate Limiting)
* **Endpoint áp dụng**: `POST /api/auth/token` và `POST /api/auth/refresh`
* **Quy tắc**: Tối đa **10 requests / phút** cho mỗi địa chỉ IP nguồn.
* **Xử lý khi vi phạm**:
  * Trả về HTTP Status **429 Too Many Requests**.
  * Header bắt buộc: `Retry-After: <số giây còn lại đến khi được gọi tiếp>`.
  * Body theo chuẩn RFC 9457 `ProblemDetail`.
* **Phản hồi mẫu (HTTP 429)**:
  ```json
  {
    "type": "urn:problem-type:rate-limit-exceeded",
    "title": "Too Many Requests",
    "status": 429,
    "detail": "Bạn đã vượt quá giới hạn 10 yêu cầu/phút đối với dịch vụ xác thực. Vui lòng thử lại sau.",
    "instance": "/api/auth/token",
    "code": "RATE_LIMIT_EXCEEDED",
    "retryAfterSeconds": 48
  }
  ```

---

### 3.10. [R11] Tài liệu hóa Đầy đủ trên Swagger UI
* Toàn bộ các endpoint xác thực và quản lý token (`/api/auth/**`, `/.well-known/jwks.json`, `/api/v1/products/**`) được mô tả tường minh trên Swagger UI (`/swagger-ui.html`).
* Hỗ trợ nút **Authorize** với cấu hình SecurityScheme kiểu `bearerAuth` (JWT Format).
* Mô tả đầy đủ các phản hồi: 200/201/204 thành công, 400 Bad Request, 401 Unauthorized, 403 Forbidden, 429 Too Many Requests.

---

## 4. BẢNG QUY TẮC NGHIỆP VỤ (BUSINESS RULES MATRIX)

| Mã Quy tắc | Nội dung Quy tắc Nghiệp vụ | Mã lỗi trả về | HTTP Status |
| :---: | :--- | :---: | :---: |
| **BR-01** | Sai username hoặc mật khẩu khi đăng nhập | `INVALID_CREDENTIALS` | 401 |
| **BR-02** | Refresh token không tồn tại hoặc đã hết hạn 7 ngày | `INVALID_REFRESH_TOKEN` | 401 |
| **BR-03** | Gửi lại Refresh Token đã từng bị thu hồi (Phát hiện Replay Attack) | `REFRESH_TOKEN_REUSED` | 401 |
| **BR-04** | Access Token thiếu header Authorization, sai chữ ký, hết hạn 15m | `INVALID_ACCESS_TOKEN` | 401 |
| **BR-05** | Access Token nằm trong danh sách thu hồi (`jti` trong blacklist) | `TOKEN_REVOKED` | 401 |
| **BR-06** | Access Token có `ver < token_version` hiện tại của tài khoản | `TOKEN_VERSION_OUTDATED` | 401 |
| **BR-07** | Đổi mật khẩu nhưng mật khẩu hiện tại (`currentPassword`) không khớp | `INVALID_CURRENT_PASSWORD` | 400 |
| **BR-08** | Tài khoản USER cố tình gọi thao tác ghi sản phẩm (thiếu scope write) | `INSUFFICIENT_SCOPE` | 403 |
| **BR-09** | Vượt quá 10 request/phút tới endpoint xác thực | `RATE_LIMIT_EXCEEDED` | 429 |
| **BR-10** | Dữ liệu đầu vào không thỏa mãn Bean Validation | `VALIDATION_ERROR` | 400 |

---

## 5. TIÊU CHÍ NGHIỆM THU NGHIỆP VỤ (ACCEPTANCE CRITERIA)
1. **Khởi chạy ứng dụng**: Khởi chạy thành công bằng lệnh `./mvnw spring-boot:run` và kết nối CSDL MySQL 8.4.
2. **Luồng Cấp & Xoay vòng Token**: Đăng nhập lấy được cặp token $\rightarrow$ Gọi refresh đổi được cặp token mới $\rightarrow$ Token cũ mất hiệu lực ngay lập tức.
3. **Luồng Chống Tấn công Dùng lại**: Gửi lại token cũ $\rightarrow$ Nhận lỗi 401 `REFRESH_TOKEN_REUSED` $\rightarrow$ Mọi token thuộc family_id đó đều bị thu hồi.
4. **Luồng Đăng xuất & Thu hồi**: Gọi logout $\rightarrow$ Token cũ bị chặn qua `jti` $\rightarrow$ Gọi logout-all $\rightarrow$ Mọi thiết bị bị đăng xuất do lệch `token_version`.
5. **Luồng Xoay Khóa Ký**: Token ký bằng khóa cũ vẫn truy cập được cho đến khi hết hạn $\rightarrow$ Token mang `kid` lạ không tồn tại bị từ chối 401.
6. **Kiểm soát Tần suất**: Gửi 10 request liên tiếp hợp lệ $\rightarrow$ Request thứ 11 trong 60 giây nhận 429 kèm `Retry-After`.
