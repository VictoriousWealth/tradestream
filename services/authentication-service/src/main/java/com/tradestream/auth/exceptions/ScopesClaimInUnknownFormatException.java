package com.tradestream.auth.exceptions;

public class ScopesClaimInUnknownFormatException extends RuntimeException {
    public ScopesClaimInUnknownFormatException(String message) {
        super("Scopes Claim Is In An Unknown Format Exception: "+message);
    }

    public ScopesClaimInUnknownFormatException(String message, Throwable cause) {
        super("Scopes Claim Is In An Unknown Format Exception: "+message, cause);
    }
}
