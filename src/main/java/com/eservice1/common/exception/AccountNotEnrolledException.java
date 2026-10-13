package com.eservice1.common.exception;

public class AccountNotEnrolledException extends RuntimeException {
    public AccountNotEnrolledException(String message) {
        super(message);
    }
}
