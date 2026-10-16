package com.shop.service;

import com.shop.api.model.*;
import com.shop.entity.CustomerEntity;
import com.shop.entity.OrderEntity;
import com.shop.entity.OrderItemEntity;
import com.shop.exception.ApiException;
import com.shop.repository.CustomerRepository;
import com.shop.repository.OrderRepository;
import com.shop.util.PaginationUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service quản lý khách hàng áp dụng @Transactional(readOnly = true) mức class
 * để tối ưu hóa hiệu năng Hibernate/JPA (tiết kiệm CPU, RAM, bỏ qua Dirty Checking).
 */
@Service
@Transactional(readOnly = true)
public class CustomerService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "name", "email", "createdAt");
    private static final Set<String> ALLOWED_ORDER_SORT_FIELDS = Set.of("id", "totalAmount", "status", "createdAt");

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    public CustomerService(CustomerRepository customerRepository, OrderRepository orderRepository) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
    }

    public CustomerPage listCustomers(Integer page, Integer size, String sort) {
        Pageable pageable = PaginationUtil.createPageable(page, size, sort, ALLOWED_SORT_FIELDS);
        Page<CustomerEntity> entityPage = customerRepository.findAll(pageable);

        List<Customer> customers = entityPage.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        CustomerPage response = new CustomerPage();
        response.setContent(customers);
        response.setPage(entityPage.getNumber());
        response.setSize(entityPage.getSize());
        response.setTotalElements(entityPage.getTotalElements());
        response.setTotalPages(entityPage.getTotalPages());
        return response;
    }

    public Customer getCustomer(Long id) {
        CustomerEntity entity = customerRepository.findById(id)
                .orElseThrow(() -> new ApiException("RESOURCE_NOT_FOUND", "Không tìm thấy khách hàng ID: " + id, HttpStatus.NOT_FOUND));
        return toDto(entity);
    }

    @Transactional
    public Customer createCustomer(NewCustomer req) {
        String normalizedEmail = req.getEmail().trim().toLowerCase();
        if (customerRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ApiException("EMAIL_ALREADY_EXISTS", "Email đã tồn tại trong hệ thống: " + req.getEmail(), HttpStatus.UNPROCESSABLE_ENTITY);
        }

        CustomerEntity entity = new CustomerEntity(
                req.getName().trim(),
                normalizedEmail,
                req.getPhone() != null ? req.getPhone().trim() : null,
                req.getAddress() != null ? req.getAddress().trim() : null
        );

        CustomerEntity saved = customerRepository.save(entity);
        return toDto(saved);
    }

    @Transactional
    public Customer updateCustomer(Long id, NewCustomer req) {
        CustomerEntity entity = customerRepository.findById(id)
                .orElseThrow(() -> new ApiException("RESOURCE_NOT_FOUND", "Không tìm thấy khách hàng ID: " + id, HttpStatus.NOT_FOUND));

        String normalizedEmail = req.getEmail().trim().toLowerCase();
        if (customerRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, id)) {
            throw new ApiException("EMAIL_ALREADY_EXISTS", "Email đã thuộc về khách hàng khác: " + req.getEmail(), HttpStatus.UNPROCESSABLE_ENTITY);
        }

        entity.setName(req.getName().trim());
        entity.setEmail(normalizedEmail);
        entity.setPhone(req.getPhone() != null ? req.getPhone().trim() : null);
        entity.setAddress(req.getAddress() != null ? req.getAddress().trim() : null);

        CustomerEntity updated = customerRepository.save(entity);
        return toDto(updated);
    }

    @Transactional
    public void deleteCustomer(Long id) {
        CustomerEntity entity = customerRepository.findById(id)
                .orElseThrow(() -> new ApiException("RESOURCE_NOT_FOUND", "Không tìm thấy khách hàng ID: " + id, HttpStatus.NOT_FOUND));

        // BR-03: Không được xóa khách hàng đã có đơn hàng
        if (orderRepository.existsByCustomerId(id)) {
            throw new ApiException("CUSTOMER_HAS_ORDERS", "Không thể xóa khách hàng đã có lịch sử đơn hàng", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        customerRepository.delete(entity);
    }

    public OrderPage listCustomerOrders(Long customerId, Integer page, Integer size, String sort) {
        if (!customerRepository.existsById(customerId)) {
            throw new ApiException("RESOURCE_NOT_FOUND", "Không tìm thấy khách hàng ID: " + customerId, HttpStatus.NOT_FOUND);
        }

        Pageable pageable = PaginationUtil.createPageable(page, size, sort, ALLOWED_ORDER_SORT_FIELDS);
        Page<OrderEntity> entityPage = orderRepository.findByCustomerId(customerId, pageable);

        List<Order> orders = entityPage.getContent().stream()
                .map(this::toOrderDto)
                .collect(Collectors.toList());

        OrderPage response = new OrderPage();
        response.setContent(orders);
        response.setPage(entityPage.getNumber());
        response.setSize(entityPage.getSize());
        response.setTotalElements(entityPage.getTotalElements());
        response.setTotalPages(entityPage.getTotalPages());
        return response;
    }

    public Customer toDto(CustomerEntity entity) {
        Customer dto = new Customer();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setEmail(entity.getEmail());
        dto.setPhone(entity.getPhone());
        dto.setAddress(entity.getAddress());
        if (entity.getCreatedAt() != null) {
            dto.setCreatedAt(entity.getCreatedAt().atOffset(ZoneOffset.UTC));
        }
        return dto;
    }

    private Order toOrderDto(OrderEntity entity) {
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
