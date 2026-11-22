package com.orderaudit.common;

/** A business rule was broken (insufficient stock, wrong status, overpayment...). Mapped to HTTP 409. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
