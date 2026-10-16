package com.shop.util;

import com.shop.exception.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.Set;

public final class PaginationUtil {

    private PaginationUtil() {
    }

    public static Pageable createPageable(Integer page, Integer size, String sortParam, Set<String> allowedSortFields) {
        int pageNumber = (page != null && page >= 0) ? page : 0;
        int pageSize = (size != null && size > 0) ? Math.min(size, 100) : 10;

        if (sortParam == null || sortParam.trim().isEmpty()) {
            return PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.ASC, "id"));
        }

        String[] parts = sortParam.trim().split(",");
        String property = parts[0].trim();
        Sort.Direction direction = Sort.Direction.ASC;

        if (parts.length > 1) {
            String dirStr = parts[1].trim().toLowerCase();
            if ("desc".equals(dirStr)) {
                direction = Sort.Direction.DESC;
            } else if (!"asc".equals(dirStr)) {
                throw new ApiException("INVALID_SORT", "Hướng sắp xếp không hợp lệ: " + parts[1], HttpStatus.BAD_REQUEST);
            }
        }

        if (allowedSortFields != null && !allowedSortFields.contains(property)) {
            throw new ApiException("INVALID_SORT", "Trường sắp xếp không hợp lệ: " + property, HttpStatus.BAD_REQUEST);
        }

        return PageRequest.of(pageNumber, pageSize, Sort.by(direction, property));
    }
}
