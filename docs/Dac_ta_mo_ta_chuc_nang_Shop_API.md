# Đặc tả mô tả chức năng – Shop API

Phiên bản 1.0 · 14/10/2026 · Nguồn sự thật kỹ thuật: `src/main/resources/shop-api.yaml` (OpenAPI 3.0.3)

## 1. Quy ước chung

| Hạng mục | Quy ước |
|---|---|
| Base URL | `http://localhost:8080/api/v1` |
| Định dạng | `Content-Type: application/json`, `Accept: application/json` |
| Đặt tên URL | Danh từ số nhiều, kebab-case, không chứa động từ, lồng nhau tối đa 2 cấp |
| Thời gian | ISO-8601 UTC, ví dụ `2026-10-14T08:30:00Z` |
| Tiền tệ | Số thập phân (BigDecimal), tối đa 2 chữ số thập phân |
| Xác thực | Chưa áp dụng trong bài tập này |

### 1.1 Ngữ nghĩa HTTP Verbs

| Verb | Dùng cho | Thành công |
|---|---|---|
| GET | Truy vấn | 200 OK |
| POST | Tạo mới | 201 Created + header `Location` |
| PUT | Cập nhật toàn bộ | 200 OK |
| PATCH | Cập nhật một phần | 200 OK |
| DELETE | Xóa | 204 No Content |

### 1.2 Mã trạng thái HTTP

| Mã | Khi nào |
|---|---|
| 200 | Truy vấn/cập nhật thành công |
| 201 | Tạo tài nguyên thành công |
| 204 | Xóa thành công, không có thân phản hồi |
| 400 | Lỗi validation, JSON hỏng, tham số sai kiểu/thiếu, `sort` không hỗ trợ |
| 404 | Không tìm thấy tài nguyên được gọi trực tiếp theo `{id}` |
| 422 | Vi phạm quy tắc nghiệp vụ (dữ liệu hợp lệ cú pháp nhưng không xử lý được) |
| 500 | Lỗi hệ thống không dự kiến |

### 1.3 Cấu trúc JSON báo lỗi thống nhất

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Dữ liệu yêu cầu không hợp lệ",
  "details": [
    { "field": "email", "issue": "must be a well-formed email address" },
    { "field": "name", "issue": "size must be between 2 and 100" }
  ]
}
```

| `code` | HTTP | Ý nghĩa |
|---|---|---|
| VALIDATION_ERROR | 400 | Sai ràng buộc dữ liệu |
| MALFORMED_REQUEST | 400 | JSON hỏng, sai kiểu tham số, thiếu tham số bắt buộc |
| INVALID_SORT | 400 | Trường hoặc hướng sắp xếp không hợp lệ |
| RESOURCE_NOT_FOUND | 404 | Không tìm thấy tài nguyên |
| EMAIL_ALREADY_EXISTS | 422 | Email trùng |
| CUSTOMER_HAS_ORDERS | 422 | Xóa khách đã có đơn |
| CUSTOMER_NOT_FOUND / PRODUCT_NOT_FOUND | 422 | Đơn tham chiếu khách/sản phẩm không tồn tại |
| INSUFFICIENT_STOCK | 422 | Không đủ tồn kho |
| INVALID_STATUS_TRANSITION | 422 | Chuyển trạng thái đơn không hợp lệ |
| INTERNAL_ERROR | 500 | Lỗi hệ thống |

### 1.4 Phân trang và sắp xếp

| Tham số | Kiểu | Mặc định | Ràng buộc |
|---|---|---|---|
| `page` | integer | 0 | ≥ 0 (bắt đầu từ 0) |
| `size` | integer | 10 | 1–100 |
| `sort` | string | `id,asc` | `trường,asc` hoặc `trường,desc`, chỉ nhận trường trong danh sách cho phép |

Trường cho phép: khách hàng `id,name,email,createdAt`; sản phẩm `id,name,price,stock,createdAt`; đơn hàng `id,totalAmount,status,createdAt`.

Phản hồi danh sách có dạng:

```json
{ "content": [ ], "page": 0, "size": 10, "totalElements": 42, "totalPages": 5 }
```

## 2. Module Khách hàng (`/customers`)

### 2.1 Dữ liệu

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| id | int64 | Chỉ đọc | Server sinh |
| name | string | Có | 2–100 ký tự |
| email | string | Có | Đúng định dạng, duy nhất, tối đa 150 ký tự |
| phone | string | Không | Tối đa 20 ký tự |
| address | string | Không | Tối đa 255 ký tự |
| createdAt | date-time | Chỉ đọc | Server sinh |

### 2.2 Danh sách endpoint

| Endpoint | Mô tả | Thành công | Lỗi |
|---|---|---|---|
| GET /customers | Danh sách phân trang | 200 `CustomerPage` | 400 |
| POST /customers | Tạo mới | 201 `Customer` + `Location` | 400, 422 |
| GET /customers/{id} | Chi tiết | 200 `Customer` | 404 |
| PUT /customers/{id} | Cập nhật toàn bộ (body `NewCustomer`) | 200 `Customer` | 400, 404, 422 |
| DELETE /customers/{id} | Xóa | 204 | 404, 422 |
| GET /customers/{id}/orders | Lịch sử đơn của khách, phân trang | 200 `OrderPage` | 400, 404 |

### 2.3 Xử lý chi tiết

**POST /customers.** Chuẩn hóa email (cắt khoảng trắng, chuyển chữ thường) → kiểm tra trùng → lưu → trả 201.

```json
POST /api/v1/customers
{ "name": "An Dev", "email": "an@test.com", "phone": "0901234567" }

201 Created
Location: http://localhost:8080/api/v1/customers/1
{ "id": 1, "name": "An Dev", "email": "an@test.com", "phone": "0901234567", "createdAt": "2026-10-14T08:30:00Z" }
```

**PUT /customers/{id}.** Ghi đè toàn bộ trường có thể sửa. Email mới không được trùng với **khách khác**.

**DELETE /customers/{id}.** Nếu khách đã có đơn hàng → 422 `CUSTOMER_HAS_ORDERS`.

## 3. Module Sản phẩm (`/products`)

### 3.1 Dữ liệu

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| id | int64 | Chỉ đọc | Server sinh |
| name | string | Có | 1–200 ký tự |
| description | string | Không | Tối đa 1000 ký tự |
| price | number | Có | ≥ 0 |
| stock | integer | Có | ≥ 0 |
| createdAt | date-time | Chỉ đọc | Server sinh |

### 3.2 Danh sách endpoint

| Endpoint | Mô tả | Thành công | Lỗi |
|---|---|---|---|
| GET /products | Danh sách, ví dụ `?page=0&size=10&sort=price,desc` | 200 `ProductPage` | 400 |
| POST /products | Thêm sản phẩm | 201 `Product` + `Location` | 400 |
| GET /products/{id} | Xem giá và tồn kho | 200 `Product` | 404 |
| PUT /products/{id} | Cập nhật toàn bộ (body `NewProduct`) | 200 `Product` | 400, 404 |
| PATCH /products/{id} | Cập nhật một phần (`ProductPatch`: tất cả trường tùy chọn) | 200 `Product` | 400, 404 |
| DELETE /products/{id} | Xóa | 204 | 404 |

### 3.3 Xử lý chi tiết

**PATCH /products/{id}.** Chỉ cập nhật trường có trong body; trường vắng mặt giữ nguyên. Các ràng buộc (giá ≥ 0, tồn ≥ 0) vẫn áp dụng. Ví dụ nhập thêm hàng: `{ "stock": 50 }`.

**DELETE /products/{id}.** Cho phép xóa; các đơn cũ vẫn giữ bản chụp tên và đơn giá.

## 4. Module Đơn hàng (`/orders`)

### 4.1 Dữ liệu

**NewOrder** (yêu cầu tạo đơn)

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| customerId | int64 | Có | Khách phải tồn tại |
| items | array | Có | Ít nhất 1 phần tử |
| items[].productId | int64 | Có | Sản phẩm phải tồn tại |
| items[].quantity | integer | Có | ≥ 1 và ≤ tồn kho |

**Order** (phản hồi): `id`, `customerId`, `status` (NEW | PAID | CANCELLED), `totalAmount`, `createdAt`, `items[]` gồm `productId`, `productName`, `quantity`, `unitPrice`, `lineTotal`.

### 4.2 Danh sách endpoint

| Endpoint | Mô tả | Thành công | Lỗi |
|---|---|---|---|
| POST /orders | Tạo đơn, tự tính tổng tiền, trừ tồn | 201 `Order` + `Location` | 400, 422 |
| GET /orders/{id} | Chi tiết đơn và trạng thái | 200 `Order` | 404 |
| GET /orders?customerId= | Liệt kê theo khách, `customerId` **bắt buộc** | 200 `OrderPage` | 400, 404 |
| PATCH /orders/{id}/status | Đổi trạng thái (body `{ "status": "PAID" }`) | 200 `Order` | 400, 404, 422 |

### 4.3 Thuật toán tạo đơn (POST /orders)

1. Validate cú pháp (400 nếu `items` rỗng, `quantity` < 1, thiếu trường).
2. Tìm khách hàng; không có → 422 `CUSTOMER_NOT_FOUND`.
3. Gộp các dòng cùng `productId`, sắp theo `productId` tăng dần.
4. Với từng sản phẩm: khóa ghi hàng sản phẩm; không có → 422 `PRODUCT_NOT_FOUND`; `stock < quantity` → 422 `INSUFFICIENT_STOCK`.
5. Trừ tồn; đơn giá = giá hiện tại; `lineTotal = unitPrice × quantity`.
6. `totalAmount = Σ lineTotal`; `status = NEW`; lưu đơn và trả 201.
7. Bất kỳ lỗi nào ở bước 2–5 → rollback toàn bộ giao dịch, tồn kho không đổi.

```json
POST /api/v1/orders
{ "customerId": 1, "items": [ { "productId": 1, "quantity": 2 } ] }

201 Created
Location: http://localhost:8080/api/v1/orders/1
{
  "id": 1, "customerId": 1, "status": "NEW", "totalAmount": 4000.00,
  "createdAt": "2026-10-14T08:35:00Z",
  "items": [ { "productId": 1, "productName": "Laptop Pro", "quantity": 2, "unitPrice": 2000.00, "lineTotal": 4000.00 } ]
}
```

### 4.4 Máy trạng thái (PATCH /orders/{id}/status)

| Trạng thái hiện tại | Yêu cầu | Kết quả |
|---|---|---|
| NEW | PAID | 200, đơn thành PAID |
| NEW | CANCELLED | 200, hoàn trả tồn kho |
| PAID / CANCELLED | bất kỳ | 422 `INVALID_STATUS_TRANSITION` |
| NEW | NEW | 422 `INVALID_STATUS_TRANSITION` |

## 5. Phân tầng và giao dịch

| Tầng | Thành phần | Trách nhiệm |
|---|---|---|
| API (sinh tự động) | `CustomersApi`, `ProductsApi`, `OrdersApi` + DTOs | Hợp đồng, validation annotation |
| Controller | `*Controller implements *Api` | Ánh xạ HTTP, status code, header `Location` |
| Service | `CustomerService`, `ProductService`, `OrderService` | Nghiệp vụ, giao dịch |
| Repository | Spring Data JPA | Truy cập MySQL |
| Xử lý lỗi | `GlobalExceptionHandler` | Chuyển ngoại lệ thành JSON lỗi thống nhất |

Quy định giao dịch: lớp Service đánh dấu `@Transactional(readOnly = true)` mức class cho thao tác đọc (Hibernate bỏ qua dirty checking); phương thức ghi ghi đè bằng `@Transactional`.

## 6. Lược đồ cơ sở dữ liệu (MySQL)

| Bảng | Cột |
|---|---|
| customers | id BIGINT PK AI, name VARCHAR(100), email VARCHAR(150) UNIQUE, phone VARCHAR(20), address VARCHAR(255), created_at DATETIME |
| products | id BIGINT PK AI, name VARCHAR(200), description VARCHAR(1000), price DECIMAL(15,2), stock INT, created_at DATETIME |
| orders | id BIGINT PK AI, customer_id BIGINT FK, status VARCHAR(20), total_amount DECIMAL(15,2), created_at DATETIME |
| order_items | id BIGINT PK AI, order_id BIGINT FK, product_id BIGINT, product_name VARCHAR(200), quantity INT, unit_price DECIMAL(15,2) |

## 7. Kịch bản kiểm thử chủ đạo

| Mã | Kịch bản | Kết quả mong đợi |
|---|---|---|
| TC-01 | Tạo khách hợp lệ | 201, có `Location` |
| TC-02 | Tạo khách email sai định dạng | 400, `details` chỉ trường `email` |
| TC-03 | Tạo khách email trùng | 422 `EMAIL_ALREADY_EXISTS` |
| TC-04 | GET /customers/999 | 404 `RESOURCE_NOT_FOUND` |
| TC-05 | Tạo sản phẩm giá âm | 400 |
| TC-06 | GET /products?sort=price,desc | 200, giá giảm dần |
| TC-07 | GET /products?sort=abc,asc | 400 `INVALID_SORT` |
| TC-08 | Tạo đơn 2 sản phẩm, tồn đủ | 201, tổng tiền đúng, tồn giảm |
| TC-09 | Tạo đơn vượt tồn | 422 `INSUFFICIENT_STOCK`, tồn không đổi |
| TC-10 | Tạo đơn `items` rỗng | 400 |
| TC-11 | GET /orders không có `customerId` | 400 `MALFORMED_REQUEST` |
| TC-12 | NEW → CANCELLED | 200, tồn được hoàn |
| TC-13 | PAID → CANCELLED | 422 `INVALID_STATUS_TRANSITION` |
| TC-14 | Xóa khách có đơn | 422 `CUSTOMER_HAS_ORDERS` |
