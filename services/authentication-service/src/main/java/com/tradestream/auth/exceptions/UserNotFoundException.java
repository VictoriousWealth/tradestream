package com.tradestream.auth.exceptions;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super("User Not Found Exception"+message);
    }

    public UserNotFoundException(String message, Throwable cause) {
        super("User Not Found Exception"+message, cause);
    }
}
