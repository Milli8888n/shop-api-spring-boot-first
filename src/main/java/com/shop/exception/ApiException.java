package com.shop.exception;

import com.shop.api.model.ErrorDetail;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;

public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;
    private final List<ErrorDetail> details;

    public ApiException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = Collections.emptyList();
    }

    public ApiException(String code, String message, HttpStatus httpStatus, List<ErrorDetail> details) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = details != null ? details : Collections.emptyList();
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public List<ErrorDetail> getDetails() {
        return details;
    }
}
