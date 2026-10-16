package com.shop.controller;

import com.shop.api.CustomersApi;
import com.shop.api.model.Customer;
import com.shop.api.model.CustomerPage;
import com.shop.api.model.NewCustomer;
import com.shop.api.model.OrderPage;
import com.shop.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
public class CustomersController implements CustomersApi {

    private final CustomerService customerService;

    public CustomersController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Override
    public ResponseEntity<Customer> createCustomer(NewCustomer newCustomer) {
        Customer created = customerService.createCustomer(newCustomer);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Override
    public ResponseEntity<Void> deleteCustomer(Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Customer> getCustomer(Long id) {
        return ResponseEntity.ok(customerService.getCustomer(id));
    }

    @Override
    public ResponseEntity<CustomerPage> listCustomers(Integer page, Integer size, String sort) {
        return ResponseEntity.ok(customerService.listCustomers(page, size, sort));
    }

    @Override
    public ResponseEntity<OrderPage> listCustomerOrders(Long id, Integer page, Integer size, String sort) {
        return ResponseEntity.ok(customerService.listCustomerOrders(id, page, size, sort));
    }

    @Override
    public ResponseEntity<Customer> updateCustomer(Long id, NewCustomer newCustomer) {
        return ResponseEntity.ok(customerService.updateCustomer(id, newCustomer));
    }
}
