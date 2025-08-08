// src/main/java/com/tradestream/user_registration_service/repository/UserRepository.java
package com.tradestream.user_registration_service.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tradestream.user_registration_service.model.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    
}
