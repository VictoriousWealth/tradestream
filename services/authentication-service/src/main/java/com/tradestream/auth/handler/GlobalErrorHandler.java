package com.tradestream.auth.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.tradestream.auth.exceptions.InvalidCredentialsException;
import com.tradestream.auth.exceptions.InvalidRefreshTokenException;
import com.tradestream.auth.exceptions.ScopesClaimInUnknownFormatException;
import com.tradestream.auth.exceptions.UnableToGenerateRSAKeyPairException;
import com.tradestream.auth.exceptions.UserNotFoundException;

@ControllerAdvice
public class GlobalErrorHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<String> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<String> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage()); // here it could have 
                        // been a bad request but i feel like its better as unathorized as refresh token have a life span
    }

    @ExceptionHandler(ScopesClaimInUnknownFormatException.class)
    public ResponseEntity<String> handleInvalidRefreshToken(ScopesClaimInUnknownFormatException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(UnableToGenerateRSAKeyPairException.class)
    public ResponseEntity<String> handleUnableToGenerateRSAKeyPair(UnableToGenerateRSAKeyPairException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
    }
    
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + ex.getMessage());
    }  

}
