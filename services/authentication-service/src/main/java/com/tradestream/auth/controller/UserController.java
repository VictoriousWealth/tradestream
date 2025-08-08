// src/main/java/com/tradestream/auth/controller/UserController.java
package com.tradestream.auth.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradestream.auth.dto.RefreshRequest;
import com.tradestream.auth.dto.UserDTO;
import com.tradestream.auth.exceptions.InvalidCredentialsException;
import com.tradestream.auth.exceptions.UserNotFoundException;
import com.tradestream.auth.model.User;
import com.tradestream.auth.repository.UserRepository;
import com.tradestream.auth.service.TokenService;

@RestController
@RequestMapping("")
public class UserController {

    private final UserRepository userRepository;
    private final TokenService tokenService;

    public UserController(UserRepository userRepository, TokenService tokenService) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody UserDTO userDTO) {
        // we have the credentials, now we need to treat them to have no spaces
        String username = userDTO.getUsername().replace(" ", "");
        String password = userDTO.getPassword().replace(" ", "");

        // validate the credentials
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("Invalid credentials provided for user: " + username);
        }

        // if credentials are valid then we start making a jws tokens as said by the system diagram
        String accessToken = tokenService.generateAccessToken(username, user.getId(), "read:*", "write:transaction");
        String refreshToken = tokenService.generateRefreshToken(username, user.getId(), "read:*", "write:transaction");

        return ResponseEntity.ok(Map.of(
            "access_token", accessToken,
            "refresh_token", refreshToken,
            "token_type", "Bearer",
            "user_id", user.getId().toString()
        ));

    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(@RequestBody RefreshRequest req) {
        String refreshToken = req.getRefreshToken();

        String username = tokenService.extractUsernameFromRefreshToken(refreshToken);
        UUID userId = tokenService.extractUserIdFromRefreshToken(refreshToken);
        String[] scopes = tokenService.extractScopesFromRefreshToken(refreshToken);

        String accessToken = tokenService.generateAccessToken(username, userId, scopes);

        return ResponseEntity.ok(Map.of(
            "access_token", accessToken,
            "refresh_token", refreshToken,
            "token_type", "Bearer",
            "user_id", userId.toString()
        ));
    }
    
}
