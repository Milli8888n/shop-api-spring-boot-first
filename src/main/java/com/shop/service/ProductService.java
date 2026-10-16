package com.shop.service;

import com.shop.api.model.*;
import com.shop.entity.ProductEntity;
import com.shop.exception.ApiException;
import com.shop.repository.ProductRepository;
import com.shop.util.PaginationUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service quản lý sản phẩm áp dụng @Transactional(readOnly = true) mức class
 * để tối ưu hóa hiệu năng Hibernate/JPA.
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "name", "price", "stock", "createdAt");

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ProductPage listProducts(Integer page, Integer size, String sort) {
        Pageable pageable = PaginationUtil.createPageable(page, size, sort, ALLOWED_SORT_FIELDS);
        Page<ProductEntity> entityPage = productRepository.findAll(pageable);

        List<Product> products = entityPage.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        ProductPage response = new ProductPage();
        response.setContent(products);
        response.setPage(entityPage.getNumber());
        response.setSize(entityPage.getSize());
        response.setTotalElements(entityPage.getTotalElements());
        response.setTotalPages(entityPage.getTotalPages());
        return response;
    }

    public Product getProduct(Long id) {
        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() -> new ApiException("RESOURCE_NOT_FOUND", "Không tìm thấy sản phẩm ID: " + id, HttpStatus.NOT_FOUND));
        return toDto(entity);
    }

    @Transactional
    public Product createProduct(NewProduct req) {
        if (req.getPrice() != null && req.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException("VALIDATION_ERROR", "Giá sản phẩm phải lớn hơn hoặc bằng 0", HttpStatus.BAD_REQUEST);
        }
        if (req.getStock() != null && req.getStock() < 0) {
            throw new ApiException("VALIDATION_ERROR", "Số lượng tồn kho phải lớn hơn hoặc bằng 0", HttpStatus.BAD_REQUEST);
        }

        ProductEntity entity = new ProductEntity(
                req.getName().trim(),
                req.getDescription() != null ? req.getDescription().trim() : null,
                req.getPrice(),
                req.getStock()
        );

        ProductEntity saved = productRepository.save(entity);
        return toDto(saved);
    }

    @Transactional
    public Product updateProduct(Long id, NewProduct req) {
        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() -> new ApiException("RESOURCE_NOT_FOUND", "Không tìm thấy sản phẩm ID: " + id, HttpStatus.NOT_FOUND));

        if (req.getPrice() != null && req.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException("VALIDATION_ERROR", "Giá sản phẩm phải lớn hơn hoặc bằng 0", HttpStatus.BAD_REQUEST);
        }
        if (req.getStock() != null && req.getStock() < 0) {
            throw new ApiException("VALIDATION_ERROR", "Số lượng tồn kho phải lớn hơn hoặc bằng 0", HttpStatus.BAD_REQUEST);
        }

        entity.setName(req.getName().trim());
        entity.setDescription(req.getDescription() != null ? req.getDescription().trim() : null);
        entity.setPrice(req.getPrice());
        entity.setStock(req.getStock());

        ProductEntity updated = productRepository.save(entity);
        return toDto(updated);
    }

    @Transactional
    public Product patchProduct(Long id, ProductPatch patch) {
        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() -> new ApiException("RESOURCE_NOT_FOUND", "Không tìm thấy sản phẩm ID: " + id, HttpStatus.NOT_FOUND));

        if (patch.getName() != null) {
            entity.setName(patch.getName().trim());
        }
        if (patch.getDescription() != null) {
            entity.setDescription(patch.getDescription().trim());
        }
        if (patch.getPrice() != null) {
            if (patch.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new ApiException("VALIDATION_ERROR", "Giá sản phẩm phải lớn hơn hoặc bằng 0", HttpStatus.BAD_REQUEST);
            }
            entity.setPrice(patch.getPrice());
        }
        if (patch.getStock() != null) {
            if (patch.getStock() < 0) {
                throw new ApiException("VALIDATION_ERROR", "Số lượng tồn kho phải lớn hơn hoặc bằng 0", HttpStatus.BAD_REQUEST);
            }
            entity.setStock(patch.getStock());
        }

        ProductEntity updated = productRepository.save(entity);
        return toDto(updated);
    }

    @Transactional
    public void deleteProduct(Long id) {
        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() -> new ApiException("RESOURCE_NOT_FOUND", "Không tìm thấy sản phẩm ID: " + id, HttpStatus.NOT_FOUND));
        productRepository.delete(entity);
    }

    public Product toDto(ProductEntity entity) {
        Product dto = new Product();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setPrice(entity.getPrice());
        dto.setStock(entity.getStock());
        if (entity.getCreatedAt() != null) {
            dto.setCreatedAt(entity.getCreatedAt().atOffset(ZoneOffset.UTC));
        }
        return dto;
    }
}
