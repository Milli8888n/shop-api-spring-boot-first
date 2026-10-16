package com.shop.controller;

import com.shop.api.OrdersApi;
import com.shop.api.model.NewOrder;
import com.shop.api.model.Order;
import com.shop.api.model.OrderPage;
import com.shop.api.model.UpdateOrderStatus;
import com.shop.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
public class OrdersController implements OrdersApi {

    private final OrderService orderService;

    public OrdersController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public ResponseEntity<Order> createOrder(NewOrder newOrder) {
        Order created = orderService.createOrder(newOrder);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Override
    public ResponseEntity<Order> getOrder(Long id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @Override
    public ResponseEntity<OrderPage> listOrders(Long customerId, Integer page, Integer size, String sort) {
        return ResponseEntity.ok(orderService.listOrders(customerId, page, size, sort));
    }

    @Override
    public ResponseEntity<Order> updateOrderStatus(Long id, UpdateOrderStatus updateOrderStatus) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, updateOrderStatus));
    }
}
