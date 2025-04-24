package com.cardealer.services.exceptions;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("Email is already in use: " + email);
    }
}
