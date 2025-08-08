package com.tradestream.auth.exceptions;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super("Invalid Credentials Exception: "+message);
    }

    public InvalidCredentialsException(String message, Throwable cause) {
        super("Invalid Credentials Exception: "+message, cause);
    }
}
