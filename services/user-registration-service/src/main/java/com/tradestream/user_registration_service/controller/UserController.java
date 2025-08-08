// src/main/java/com/tradestream/user_registration_service/controller/UserController.java
package com.tradestream.user_registration_service.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradestream.user_registration_service.dto.UserDTO;
import com.tradestream.user_registration_service.model.User;
import com.tradestream.user_registration_service.repository.UserRepository;

@RestController
@RequestMapping("")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody UserDTO userDTO) {
        
        String encodedPassword = new BCryptPasswordEncoder().encode(userDTO.getPassword());
        User user = new User();
        user.setUsername(userDTO.getUsername());
        user.setPassword(encodedPassword);
        userRepository.save(user);

        return ResponseEntity.ok("All good.");
    }


    
    
}
