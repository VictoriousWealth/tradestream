// src/main/java/com/tradestream/auth/service/TokenService.java
package com.tradestream.auth.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Date;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.tradestream.auth.exceptions.InvalidRefreshTokenException;
import com.tradestream.auth.exceptions.ScopesClaimInUnknownFormatException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@Service
public class TokenService {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public TokenService(
        @Value("${JWT_PRIVATE_KEY_PATH}") String privateKeyPath,
        @Value("${JWT_PUBLIC_KEY_PATH}") String publicKeyPath
    ) throws InvalidKeySpecException {
        String privateKeyPem;
        String publicKeyPem;
        try {
            privateKeyPem = Files.readString(Path.of(privateKeyPath));
            publicKeyPem = Files.readString(Path.of(publicKeyPath));
        } catch (IOException e) {
            throw new InvalidKeySpecException("Failed to read key from path", e);
        }
        this.privateKey = loadPrivateKeyFromPem(privateKeyPem);
        this.publicKey = loadPublicKeyFromPem(publicKeyPem);
    }

    private PrivateKey loadPrivateKeyFromPem(String pem) throws InvalidKeySpecException {
        String privateKeyPEM = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(privateKeyPEM);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            throw new InvalidKeySpecException("Failed to load private key from PEM", e);
        }
    }

    private PublicKey loadPublicKeyFromPem(String pem) throws InvalidKeySpecException {
        String publicKeyPEM = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            throw new InvalidKeySpecException("Failed to load public key from PEM", e);
        }
    }


    // Add method to generate JWT here using `privateKey`
    public String generateAccessToken(String username, UUID userId, String... scopes) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(15 * 60); // 15 minutes

        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(userId.toString())
                .setHeaderParam("typ", "JWT")
                .setHeaderParam("alg", "PS256")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .setIssuer("authentication-service")
                .setAudience("api-gateway")
                .claim("username", username)
                .claim("token_type", "access")
                .claim("scopes", scopes)
                .signWith(privateKey, SignatureAlgorithm.PS256)
                .compact();
    }

    public String generateRefreshToken(String username, UUID userId, String... scopes) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(30 * 24 * 60 * 60); // 30 days

        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(userId.toString())
                .setHeaderParam("typ", "JWT")
                .setHeaderParam("alg", "PS256")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .setIssuer("authentication-service")
                .setAudience("api-gateway")
                .claim("username", username)
                .claim("token_type", "refresh") 
                .claim("scopes", scopes)
                .signWith(privateKey, SignatureAlgorithm.PS256)
                .compact();
    }

    public String extractUsernameFromRefreshToken(String refreshToken) {        
        try {
            Claims claims = parseToken(refreshToken);
            if (isNotRefreshToken(refreshToken)) {
                throw new InvalidRefreshTokenException("Token is not a refresh token");
            }
            return claims.get("username", String.class);
        } catch (JwtException e) {
            throw new InvalidRefreshTokenException("Invalid refresh token: unexpected format or tampering", e);
        }
    }

    public UUID extractUserIdFromRefreshToken(String refreshToken) {
        try {
            Claims claims = parseToken(refreshToken);
            if (isNotRefreshToken(refreshToken)) {
                throw new InvalidRefreshTokenException("Token is not a refresh token");
            }
            return UUID.fromString(claims.getSubject());
        } catch (JwtException e) {
            throw new InvalidRefreshTokenException("Invalid refresh token: unexpected format or tampering", e);
        }
    }

    public String[] extractScopesFromRefreshToken(String refreshToken) {
        try {
            Claims claims = parseToken(refreshToken);
            if (isNotRefreshToken(refreshToken)) {
                throw new InvalidRefreshTokenException("Token is not a refresh token");
            }
            Object rawScopes = claims.get("scopes");

            if (rawScopes == null) {
                throw new InvalidRefreshTokenException("Invalid refresh token: unexpected format or tampering");   
            }

            if (rawScopes instanceof List<?>) {
                List<?> list = (List<?>) rawScopes;
                return list.stream().map(Object::toString).toArray(String[]::new);
            } else if (rawScopes instanceof String[]) {
                return (String[]) rawScopes;
            } else {
                throw new ScopesClaimInUnknownFormatException("Scopes claim is in an unknown format");
            }

        } catch (JwtException e) {
            throw new InvalidRefreshTokenException("Invalid refresh token", e);
        }
    }

    private Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(publicKey) // Required for parsing signed JWTs even though validation is done at the api-gateway
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isNotRefreshToken(String token) {
        return !"refresh".equals(parseToken(token).get("token_type", String.class));
    }


}

