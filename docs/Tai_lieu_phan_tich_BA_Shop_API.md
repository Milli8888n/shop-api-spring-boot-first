# Tài liệu phân tích nghiệp vụ (BA) – Shop API

Phiên bản 1.0 · 14/10/2026 · Bài tập thực hành tổng hợp: RESTful API chuẩn doanh nghiệp theo quy trình API-First với Spring Boot 3.x

## 1. Tổng quan

Shop API là hệ thống backend phục vụ hoạt động kinh doanh trực tuyến quy mô nhỏ, cung cấp các REST API cho ứng dụng Web/Mobile. Hệ thống gồm 3 module tài nguyên: **Khách hàng (customers)**, **Sản phẩm (products)** và **Đơn hàng (orders)**.

Dự án áp dụng phương pháp **API-First**: hợp đồng API (OpenAPI 3.0.3, tệp `shop-api.yaml`) được thiết kế và thống nhất trước khi viết mã. Từ hợp đồng này, nhóm chạy Mock Server cho Frontend/UI-UX, sinh tự động Interfaces và DTOs, rồi hiện thực logic nghiệp vụ ở Backend.

## 2. Mục tiêu và phạm vi

### 2.1 Mục tiêu nghiệp vụ

- Cung cấp bộ API quản lý khách hàng, sản phẩm, đơn hàng nhất quán, dễ tích hợp.
- Đảm bảo tồn kho luôn chính xác khi có nhiều đơn đặt hàng đồng thời.
- Tự động tính tổng tiền đơn hàng, loại bỏ sai sót tính toán phía client.
- Cho phép Frontend phát triển song song với Backend nhờ Mock Server.
- Giảm chi phí sửa đổi nhờ rà soát thiết kế sớm (Fail-Fast).

### 2.2 Trong phạm vi

- CRUD khách hàng, CRUD sản phẩm (kèm cập nhật một phần giá/tồn kho).
- Tạo đơn hàng, xem chi tiết, liệt kê theo khách hàng, cập nhật trạng thái (thanh toán/hủy).
- Phân trang, sắp xếp cho mọi API danh sách.
- Chuẩn hóa mã lỗi và cấu trúc JSON báo lỗi.
- Tài liệu API qua Swagger UI.

### 2.3 Ngoài phạm vi

- Xác thực/phân quyền (Spring Security, JWT/OAuth2) – thuộc bước tiếp theo.
- Cổng thanh toán thật, vận chuyển, khuyến mãi, giỏ hàng.
- Giao diện người dùng hoàn chỉnh (chỉ dùng prototype kết nối Mock Server).

## 3. Các bên liên quan

| Bên liên quan | Vai trò | Mối quan tâm chính |
|---|---|---|
| Chủ cửa hàng / Product Owner | Đặt yêu cầu nghiệp vụ | Quản lý hàng hóa, theo dõi đơn hàng, tồn kho chính xác |
| Frontend / Mobile / UI-UX | Người dùng API | Hợp đồng ổn định, Mock Server sớm, cấu trúc phản hồi rõ ràng |
| Backend Developer | Hiện thực | Interfaces/DTOs sinh tự động, tập trung vào business logic |
| Tester / QA | Kiểm thử | Hợp đồng rõ ràng để viết test case, mã lỗi nhất quán |
| Giảng viên / Người chấm | Đánh giá | Tuân thủ RESTful, API-First, Codegen, JPA |

## 4. Quy trình nghiệp vụ chính

### 4.1 Đặt hàng (To-be)

1. Nhân viên/ứng dụng tạo hồ sơ khách hàng (nếu chưa có).
2. Quản trị viên tạo sản phẩm với giá bán và số lượng tồn.
3. Ứng dụng gửi yêu cầu tạo đơn: `customerId` và danh sách `items` (`productId`, `quantity`).
4. Hệ thống kiểm tra khách hàng, sản phẩm tồn tại và đủ tồn kho.
5. Hệ thống lấy **đơn giá hiện tại** của sản phẩm làm đơn giá chốt trên đơn, tính thành tiền từng dòng và tổng tiền, trừ tồn kho, đặt trạng thái `NEW`.
6. Đơn được thanh toán → trạng thái `PAID`; hoặc bị hủy → trạng thái `CANCELLED` và hoàn trả tồn kho.

### 4.2 Vòng đời trạng thái đơn hàng

| Từ | Đến | Điều kiện |
|---|---|---|
| (tạo mới) | NEW | Mặc định khi tạo |
| NEW | PAID | Đơn chưa hủy |
| NEW | CANCELLED | Hoàn trả tồn kho |
| PAID, CANCELLED | – | Trạng thái cuối, không đổi tiếp |

## 5. Quy tắc nghiệp vụ (BR)

| Mã | Quy tắc |
|---|---|
| BR-01 | Tên khách hàng bắt buộc, 2–100 ký tự. |
| BR-02 | Email khách hàng đúng định dạng và **duy nhất** (không phân biệt hoa thường). |
| BR-03 | Không được xóa khách hàng đã có đơn hàng. |
| BR-04 | Tên sản phẩm bắt buộc; giá bán ≥ 0; tồn kho ≥ 0. |
| BR-05 | Đơn hàng phải có ít nhất 1 dòng hàng; số lượng mỗi dòng ≥ 1. |
| BR-06 | Nếu một sản phẩm xuất hiện nhiều dòng trong cùng đơn, hệ thống gộp số lượng. |
| BR-07 | Đơn giá chốt trên đơn lấy từ giá sản phẩm tại thời điểm đặt; thay đổi giá sau đó không ảnh hưởng đơn cũ. |
| BR-08 | Tổng tiền = Σ (đơn giá × số lượng), tính tự động ở server, client không được gửi. |
| BR-09 | Số lượng đặt không được vượt tồn kho; đặt thành công thì trừ tồn. |
| BR-10 | Hủy đơn ở trạng thái `NEW` thì hoàn trả tồn kho. |
| BR-11 | Chỉ cho phép chuyển trạng thái theo bảng 4.2. |
| BR-12 | Liệt kê đơn hàng bắt buộc theo `customerId`. |

## 6. Yêu cầu chức năng (FR)

| Mã | Module | Chức năng | Endpoint | Ưu tiên |
|---|---|---|---|---|
| FR-C01 | Khách hàng | Danh sách (phân trang) | GET /customers | Cao |
| FR-C02 | Khách hàng | Tạo mới | POST /customers | Cao |
| FR-C03 | Khách hàng | Xem chi tiết | GET /customers/{id} | Cao |
| FR-C04 | Khách hàng | Cập nhật toàn bộ | PUT /customers/{id} | Trung bình |
| FR-C05 | Khách hàng | Xóa | DELETE /customers/{id} | Trung bình |
| FR-C06 | Khách hàng | Lịch sử đơn của khách | GET /customers/{id}/orders | Trung bình |
| FR-P01 | Sản phẩm | Danh sách (phân trang, sắp xếp theo giá) | GET /products | Cao |
| FR-P02 | Sản phẩm | Tạo mới | POST /products | Cao |
| FR-P03 | Sản phẩm | Xem chi tiết giá và tồn kho | GET /products/{id} | Cao |
| FR-P04 | Sản phẩm | Cập nhật toàn bộ | PUT /products/{id} | Trung bình |
| FR-P05 | Sản phẩm | Cập nhật một phần (giá, tồn kho) | PATCH /products/{id} | Cao |
| FR-P06 | Sản phẩm | Xóa | DELETE /products/{id} | Thấp |
| FR-O01 | Đơn hàng | Tạo đơn, tự tính tổng tiền, trừ tồn | POST /orders | Cao |
| FR-O02 | Đơn hàng | Xem chi tiết và trạng thái | GET /orders/{id} | Cao |
| FR-O03 | Đơn hàng | Liệt kê theo khách hàng | GET /orders?customerId= | Cao |
| FR-O04 | Đơn hàng | Cập nhật trạng thái | PATCH /orders/{id}/status | Cao |

## 7. User story và tiêu chí chấp nhận

**US-01 – Tạo khách hàng.** Là nhân viên bán hàng, tôi muốn tạo hồ sơ khách hàng để gắn đơn hàng.

- Given dữ liệu hợp lệ, When gửi POST /customers, Then nhận 201 kèm header `Location` và thân JSON khách hàng có `id`.
- Given email đã tồn tại, Then nhận 422 với `code = EMAIL_ALREADY_EXISTS`.
- Given name dưới 2 ký tự hoặc email sai định dạng, Then nhận 400 với `details` chỉ rõ từng trường.

**US-02 – Xem danh sách sản phẩm theo giá.** Là khách mua hàng, tôi muốn xem sản phẩm sắp xếp theo giá giảm dần.

- Given `?page=0&size=10&sort=price,desc`, Then nhận 200, nội dung được sắp theo giá giảm dần kèm `totalElements`, `totalPages`.
- Given `sort=abc,desc` (trường không hỗ trợ), Then nhận 400.

**US-03 – Đặt hàng.** Là khách hàng, tôi muốn đặt nhiều sản phẩm trong một đơn.

- Given khách hàng tồn tại và tồn kho đủ, When POST /orders, Then nhận 201, `status = NEW`, `totalAmount` đúng, tồn kho giảm tương ứng.
- Given một sản phẩm không đủ tồn, Then nhận 422 `INSUFFICIENT_STOCK` và **không** trừ tồn của bất kỳ sản phẩm nào (giao dịch được rollback).
- Given `items` rỗng hoặc `quantity` ≤ 0, Then nhận 400.

**US-04 – Hủy đơn.** Là nhân viên, tôi muốn hủy đơn chưa thanh toán để hoàn kho.

- Given đơn `NEW`, When PATCH status = CANCELLED, Then nhận 200 và tồn kho được hoàn trả.
- Given đơn `PAID`, When chuyển sang CANCELLED, Then nhận 422 `INVALID_STATUS_TRANSITION`.

**US-05 – Frontend dùng Mock Server.** Là Frontend developer, tôi muốn gọi API giả lập từ tệp YAML để làm giao diện trước khi Backend hoàn thành.

- Given `shop-api.yaml` hợp lệ, When chạy Prism/Postman Mock, Then mọi endpoint trả dữ liệu mẫu đúng schema.

## 8. Mô hình dữ liệu mức khái niệm

### 8.1 Thực thể

| Thực thể | Thuộc tính chính |
|---|---|
| Customer | id (PK), name, email (unique), phone, address, createdAt |
| Product | id (PK), name, description, price (DECIMAL 15,2), stock, createdAt |
| Order | id (PK), customer_id (FK), status (NEW/PAID/CANCELLED), totalAmount, createdAt |
| OrderItem | id (PK), order_id (FK), productId, productName (chụp lại), quantity, unitPrice |

### 8.2 Quan hệ

- Một Customer có nhiều Order (1–N).
- Một Order có nhiều OrderItem (1–N, xóa theo đơn: cascade).
- OrderItem tham chiếu Product theo `productId` và lưu **bản chụp** tên, đơn giá để không bị ảnh hưởng khi sản phẩm đổi giá hoặc bị xóa.

## 9. Yêu cầu phi chức năng

| Mã | Nhóm | Yêu cầu |
|---|---|---|
| NFR-01 | Chuẩn mực | URL dùng danh từ số nhiều, kebab-case, lồng nhau tối đa 2 cấp; tiền tố `/api/v1`. |
| NFR-02 | Nhất quán | Mọi lỗi trả cùng cấu trúc `{code, message, details}`. |
| NFR-03 | Hiệu năng | Thao tác đọc dùng `@Transactional(readOnly = true)`; danh sách luôn phân trang, `size` tối đa 100; bật `default_batch_fetch_size` để tránh N+1. |
| NFR-04 | Toàn vẹn | Tạo/hủy đơn nằm trong một giao dịch; khóa ghi (pessimistic lock) khi trừ tồn, khóa theo thứ tự `productId` để tránh deadlock. |
| NFR-05 | Khả bảo trì | Hợp đồng YAML là nguồn sự thật duy nhất; Controller kế thừa Interface sinh tự động. |
| NFR-06 | Tài liệu | Swagger UI tại `/swagger-ui.html`. |
| NFR-07 | Môi trường | Java 21, Spring Boot 3.4.x, MySQL 8, Maven. |

## 10. Giả định, ràng buộc và rủi ro

**Giả định:** một đơn chỉ thuộc một khách hàng; tiền tệ đơn vị duy nhất; chưa có xác thực.

**Ràng buộc:** hoàn thành trong 120 phút; công cụ Swagger Editor, Postman, Maven.

| Rủi ro | Ảnh hưởng | Giảm thiểu |
|---|---|---|
| Hợp đồng thay đổi sau khi Frontend đã code | Phải sửa lại cả hai phía | Rà soát hợp đồng ở bước Mock, đóng băng phiên bản trước khi sinh mã |
| Sinh mã tạo tên lớp xung đột với Entity | Lỗi biên dịch | Entity đặt hậu tố `Entity`, DTO nằm package `api.model` |
| Bán vượt tồn khi đặt hàng đồng thời | Sai tồn kho | Khóa ghi sản phẩm trong giao dịch |
| Sai lệch giữa YAML và code | Tài liệu lỗi thời | Luôn sinh mã từ YAML, không sửa tay mã sinh ra |

## 11. Kế hoạch thực hiện theo quy trình API-First (120 phút)

| Thời lượng | Hoạt động | Sản phẩm |
|---|---|---|
| 0–20' | Phân tích yêu cầu, thiết kế endpoint, schema | Tài liệu BA, bản nháp YAML |
| 20–40' | Hoàn thiện `shop-api.yaml`, kiểm tra trên Swagger Editor | YAML hợp lệ OAS 3.0.3 |
| 40–55' | Chạy Mock Server (Prism/Postman), kết nối prototype UI | Video/báo cáo Mockup, danh sách chỉnh sửa hợp đồng |
| 55–70' | Cấu hình Maven plugin, sinh Interfaces và DTOs | `target/generated-sources` |
| 70–105' | Hiện thực Controller, Service, Repository, xử lý lỗi | Mã nguồn chạy được |
| 105–120' | Kiểm thử Swagger UI, chụp ảnh, đóng gói nộp | Ảnh kiểm thử, gói nộp |

## 12. Truy vết yêu cầu theo tiêu chí chấm điểm

| Tiêu chí (điểm) | Yêu cầu liên quan | Bằng chứng |
|---|---|---|
| Chuẩn RESTful API (3.0) | NFR-01, NFR-02, FR-* | Bảng endpoint, JSON lỗi thống nhất, status code đúng |
| Quy trình API-First và Mockup (2.5) | US-05, mục 11 | `shop-api.yaml`, video/báo cáo Mock Server |
| Sinh mã tự động (2.5) | NFR-05 | `pom.xml` cấu hình plugin, `generated-sources`, Controller `implements ...Api` |
| Backend và tối ưu JPA (2.0) | BR-*, NFR-03, NFR-04 | Kết nối MySQL, `@Transactional(readOnly = true)` ở Service |
