package com.shop.repository;

import com.shop.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    /**
     * Kiểm tra khách hàng đã phát sinh đơn hàng hay chưa trước khi xóa (BR-03)
     */
    boolean existsByCustomerId(Long customerId);

    /**
     * Liệt kê đơn hàng theo khách hàng có phân trang
     */
    Page<OrderEntity> findByCustomerId(Long customerId, Pageable pageable);

    /**
     * Lấy chi tiết đơn hàng kèm danh sách items để tránh N+1
     */
    @EntityGraph(attributePaths = {"items"})
    Optional<OrderEntity> findWithItemsById(Long id);
}
