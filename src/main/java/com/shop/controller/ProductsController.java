package com.shop.controller;

import com.shop.api.ProductsApi;
import com.shop.api.model.NewProduct;
import com.shop.api.model.Product;
import com.shop.api.model.ProductPage;
import com.shop.api.model.ProductPatch;
import com.shop.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
public class ProductsController implements ProductsApi {

    private final ProductService productService;

    public ProductsController(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public ResponseEntity<Product> createProduct(NewProduct newProduct) {
        Product created = productService.createProduct(newProduct);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Override
    public ResponseEntity<Void> deleteProduct(Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Product> getProduct(Long id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    @Override
    public ResponseEntity<ProductPage> listProducts(Integer page, Integer size, String sort) {
        return ResponseEntity.ok(productService.listProducts(page, size, sort));
    }

    @Override
    public ResponseEntity<Product> patchProduct(Long id, ProductPatch productPatch) {
        return ResponseEntity.ok(productService.patchProduct(id, productPatch));
    }

    @Override
    public ResponseEntity<Product> updateProduct(Long id, NewProduct newProduct) {
        return ResponseEntity.ok(productService.updateProduct(id, newProduct));
    }
}
