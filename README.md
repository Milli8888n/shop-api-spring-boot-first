# XÂY DỰNG RESTFUL API CHUẨN DOANH NGHIỆP THEO QUY TRÌNH API-FIRST VÀ SPRING BOOT 3.X

> **Hệ thống**: Shop API (Quản lý Khách hàng, Sản phẩm, Đơn hàng)  
> **Công nghệ**: Java 21, Spring Boot 3.4.x, Spring Data JPA, OpenAPI 3.0.3, MySQL, Maven  
> **Quy trình phát triển**: API-First Development & Fail-Fast Testing

---

## 1. Tổng quan Kiến trúc & Quy trình API-First

Dự án áp dụng chặt chẽ quy trình **API-First**:
1. **Thiết kế hợp đồng API (API Contract)**: Định nghĩa tại `src/main/resources/shop-api.yaml` tuân theo chuẩn **OpenAPI 3.0.3**.
2. **Khởi chạy Mock Server & Fail-Fast UI Testing**: Chạy **Prism Mock Server** kết nối giao diện Prototype `mock-ui.html` để kiểm thử trải nghiệm và cấu trúc phản hồi trước khi viết mã Backend.
3. **Sinh mã tự động (Codegen)**: Cấu hình `openapi-generator-maven-plugin` để sinh tự động Interfaces (`CustomersApi`, `ProductsApi`, `OrdersApi`) và DTOs vào `target/generated-sources`.
4. **Hiện thực Backend & Tối ưu JPA**:
   - Các `@RestController` kế thừa Interfaces sinh ra.
   - Tối ưu hiệu năng: `@Transactional(readOnly = true)` tại mức class Service để Hibernate tắt Dirty Checking, tiết kiệm CPU và RAM.
   - Tính toàn vẹn và chống Race Condition: Sử dụng `@Lock(LockModeType.PESSIMISTIC_WRITE)` khi trừ tồn kho và sắp xếp `productId` tăng dần chống Deadlock.

---

## 2. Cấu trúc thư mục

```
ApifirstSpring/
├── src/
│   ├── main/
│   │   ├── java/com/shop/
│   │   │   ├── config/          # Cấu hình WebMvc (prefix /api/v1)
│   │   │   ├── controller/      # RestControllers kế thừa Interfaces sinh ra
│   │   │   ├── entity/          # JPA Entities (Customer, Product, Order, OrderItem)
│   │   │   ├── exception/       # GlobalExceptionHandler & ApiException
│   │   │   ├── repository/      # Spring Data JPA Repositories
│   │   │   ├── service/         # Business Logic & Transaction Management
│   │   │   ├── util/            # Pagination & Sort Validator
│   │   │   └── ShopApiApplication.java
│   │   └── resources/
│   │       ├── application.yml  # Cấu hình MySQL, JPA batch fetch, Swagger UI
│   │       └── shop-api.yaml    # Hợp đồng API Contract chuẩn OpenAPI 3.0.3
├── mock-ui.html                 # Giao diện UI/UX Prototype kết nối Mock Server
├── proof_mockup_customers.png   # Minh chứng kiểm thử Mockup Customers
├── proof_mockup_products.png    # Minh chứng kiểm thử Mockup Products
├── proof_mockup_orders.png      # Minh chứng kiểm thử Mockup Orders
├── proof_swagger_ui.png         # Minh chứng nghiệm thu Swagger UI
├── pom.xml                      # Cấu hình Maven dependencies & Codegen plugin
└── README.md
```

---

## 3. Hướng dẫn chạy dự án

### 3.1. Chạy Mock Server (Bước 2.2)
```powershell
npx -y @stoplight/prism-cli mock src/main/resources/shop-api.yaml -p 4010
```
Mở file `mock-ui.html` trên trình duyệt để kiểm thử luồng UI/UX Prototype với Mock Server.

### 3.2. Sinh mã Codegen & Chạy Backend Spring Boot 3.x (Bước 2.3)
1. Đảm bảo MySQL đang chạy tại cổng 3306 (database `shop_db` sẽ được tự động tạo).
2. Biên dịch và sinh mã tự động:
   ```powershell
   mvn clean compile
   ```
3. Khởi chạy ứng dụng:
   ```powershell
   mvn spring-boot:run
   ```
4. Truy cập **Swagger UI**:
   [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

## 4. Minh chứng Nghiệm thu (Proof of Work)

* **Swagger UI Endpoints**: `proof_swagger_ui.png`
* **Mockup UI Customers**: `proof_mockup_customers.png`
* **Mockup UI Products**: `proof_mockup_products.png`
* **Mockup UI Orders**: `proof_mockup_orders.png`
