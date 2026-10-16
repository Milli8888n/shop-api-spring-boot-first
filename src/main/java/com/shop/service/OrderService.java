package com.shop.service;

import com.shop.api.model.*;
import com.shop.entity.CustomerEntity;
import com.shop.entity.OrderEntity;
import com.shop.entity.OrderItemEntity;
import com.shop.entity.ProductEntity;
import com.shop.exception.ApiException;
import com.shop.repository.CustomerRepository;
import com.shop.repository.OrderRepository;
import com.shop.repository.ProductRepository;
import com.shop.util.PaginationUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service xử lý đơn hàng:
 * - Đảm bảo tính toàn vẹn giao dịch (ACID)
 * - Tối ưu truy vấn đọc với @Transactional(readOnly = true)
 * - Áp dụng khóa bi quan (Pessimistic Lock) chống Race Condition khi trừ tồn kho
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "totalAmount", "status", "createdAt");

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository,
                        CustomerRepository customerRepository,
                        ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    public Order getOrder(Long id) {
        OrderEntity entity = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ApiException("RESOURCE_NOT_FOUND", "Không tìm thấy đơn hàng ID: " + id, HttpStatus.NOT_FOUND));
        return toDto(entity);
    }

    public OrderPage listOrders(Long customerId, Integer page, Integer size, String sort) {
        if (customerId == null) {
            throw new ApiException("MALFORMED_REQUEST", "Tham số customerId là bắt buộc", HttpStatus.BAD_REQUEST);
        }
        if (!customerRepository.existsById(customerId)) {
            throw new ApiException("RESOURCE_NOT_FOUND", "Không tìm thấy khách hàng ID: " + customerId, HttpStatus.NOT_FOUND);
        }

        Pageable pageable = PaginationUtil.createPageable(page, size, sort, ALLOWED_SORT_FIELDS);
        Page<OrderEntity> entityPage = orderRepository.findByCustomerId(customerId, pageable);

        List<Order> orders = entityPage.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        OrderPage response = new OrderPage();
        response.setContent(orders);
        response.setPage(entityPage.getNumber());
        response.setSize(entityPage.getSize());
        response.setTotalElements(entityPage.getTotalElements());
        response.setTotalPages(entityPage.getTotalPages());
        return response;
    }

    /**
     * Tạo đơn hàng mới trong một giao dịch duy nhất (@Transactional).
     * Bất kỳ lỗi nào xảy ra sẽ rollback toàn bộ, không thất thoát tồn kho.
     */
    @Transactional
    public Order createOrder(NewOrder req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "Đơn hàng phải có ít nhất một sản phẩm", HttpStatus.BAD_REQUEST);
        }

        // 1. Kiểm tra khách hàng tồn tại (BR-05)
        if (!customerRepository.existsById(req.getCustomerId())) {
            throw new ApiException("CUSTOMER_NOT_FOUND", "Khách hàng không tồn tại: " + req.getCustomerId(), HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // 2. Gộp các dòng có cùng productId (BR-06)
        Map<Long, Integer> productQuantities = new HashMap<>();
        for (OrderItemRequest itemReq : req.getItems()) {
            if (itemReq.getQuantity() == null || itemReq.getQuantity() < 1) {
                throw new ApiException("VALIDATION_ERROR", "Số lượng sản phẩm phải lớn hơn hoặc bằng 1", HttpStatus.BAD_REQUEST);
            }
            productQuantities.merge(itemReq.getProductId(), itemReq.getQuantity(), Integer::sum);
        }

        // 3. Sắp xếp danh sách productId tăng dần để khóa hàng theo thứ tự, tránh deadlock (NFR-04)
        List<Long> sortedProductIds = new ArrayList<>(productQuantities.keySet());
        Collections.sort(sortedProductIds);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItemEntity> orderItems = new ArrayList<>();

        // 4. Khóa ghi, kiểm tra tồn kho, trừ tồn và chụp snapshot giá/tên
        for (Long productId : sortedProductIds) {
            ProductEntity product = productRepository.findByIdWithLock(productId)
                    .orElseThrow(() -> new ApiException("PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm ID: " + productId, HttpStatus.UNPROCESSABLE_ENTITY));

            int requestedQty = productQuantities.get(productId);
            if (product.getStock() < requestedQty) {
                throw new ApiException("INSUFFICIENT_STOCK",
                        String.format("Sản phẩm '%s' không đủ tồn kho (còn: %d, yêu cầu: %d)", product.getName(), product.getStock(), requestedQty),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            // Trừ tồn kho
            product.setStock(product.getStock() - requestedQty);
            productRepository.save(product);

            // Chụp snapshot đơn giá hiện tại (BR-07, BR-08)
            BigDecimal unitPrice = product.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(requestedQty));
            totalAmount = totalAmount.add(lineTotal);

            OrderItemEntity itemEntity = new OrderItemEntity(
                    productId,
                    product.getName(),
                    requestedQty,
                    unitPrice,
                    lineTotal
            );
            orderItems.add(itemEntity);
        }

        // 5. Tạo và lưu đơn hàng
        OrderEntity orderEntity = new OrderEntity(req.getCustomerId(), totalAmount);
        for (OrderItemEntity item : orderItems) {
            orderEntity.addItem(item);
        }

        OrderEntity saved = orderRepository.save(orderEntity);
        return toDto(saved);
    }

    /**
     * Cập nhật trạng thái đơn hàng theo máy trạng thái (BR-10, BR-11).
     */
    @Transactional
    public Order updateOrderStatus(Long orderId, UpdateOrderStatus req) {
        OrderEntity order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ApiException("RESOURCE_NOT_FOUND", "Không tìm thấy đơn hàng ID: " + orderId, HttpStatus.NOT_FOUND));

        OrderStatus currentStatus = order.getStatus();
        OrderStatus newStatus = req.getStatus();

        if (currentStatus == OrderStatus.NEW && newStatus == OrderStatus.PAID) {
            order.setStatus(OrderStatus.PAID);
        } else if (currentStatus == OrderStatus.NEW && newStatus == OrderStatus.CANCELLED) {
            order.setStatus(OrderStatus.CANCELLED);

            // BR-10: Hoàn trả tồn kho khi hủy đơn ở trạng thái NEW
            for (OrderItemEntity item : order.getItems()) {
                productRepository.findByIdWithLock(item.getProductId()).ifPresent(product -> {
                    product.setStock(product.getStock() + item.getQuantity());
                    productRepository.save(product);
                });
            }
        } else {
            throw new ApiException("INVALID_STATUS_TRANSITION",
                    String.format("Không thể chuyển trạng thái đơn hàng từ %s sang %s", currentStatus, newStatus),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        OrderEntity updated = orderRepository.save(order);
        return toDto(updated);
    }

    public Order toDto(OrderEntity entity) {
        Order dto = new Order();
        dto.setId(entity.getId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setStatus(entity.getStatus());
        dto.setTotalAmount(entity.getTotalAmount());
        if (entity.getCreatedAt() != null) {
            dto.setCreatedAt(entity.getCreatedAt().atOffset(ZoneOffset.UTC));
        }

        List<OrderItem> items = entity.getItems().stream()
                .map(i -> {
                    OrderItem item = new OrderItem();
                    item.setProductId(i.getProductId());
                    item.setProductName(i.getProductName());
                    item.setQuantity(i.getQuantity());
                    item.setUnitPrice(i.getUnitPrice());
                    item.setLineTotal(i.getLineTotal());
                    return item;
                }).collect(Collectors.toList());

        dto.setItems(items);
        return dto;
    }
}
