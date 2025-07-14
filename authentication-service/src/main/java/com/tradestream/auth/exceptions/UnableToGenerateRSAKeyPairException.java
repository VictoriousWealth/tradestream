package com.tradestream.auth.exceptions;

public class UnableToGenerateRSAKeyPairException extends RuntimeException {
    public UnableToGenerateRSAKeyPairException(String message) {
        super("Unable To Generate RSA Key Pair Exception: "+message);
    }

    public UnableToGenerateRSAKeyPairException(String message, Throwable cause) {
        super("Unable To Generate RSA Key Pair Exception: "+message, cause);
    }
    
}
