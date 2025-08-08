// src/main/java/com/tradestream/auth/repository/UserRepository.java
package com.tradestream.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tradestream.auth.model.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    
}
