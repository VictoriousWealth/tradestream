package com.tradestream.auth.exceptions;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super("Invalid Refresh Token Exception: "+message);
    }

    public InvalidRefreshTokenException(String message, Throwable cause) {
        super("Invalid Refresh Token Exception: "+message, cause);
    }
}
