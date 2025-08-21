// src/main/java/com/tradestream/user_registration_service/config/SecurityConfig.java
package com.tradestream.user_registration_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // APIs: disable CSRF
        http.csrf(AbstractHttpConfigurer::disable);

        // CORS (customize later if needed)
        http.cors(Customizer.withDefaults());

        http.authorizeHttpRequests(auth -> auth
            // public error
            .requestMatchers("/error").permitAll()
            // public root + actuator for healthchecks
            .requestMatchers(HttpMethod.GET, "/", "/actuator/health", "/actuator/info").permitAll()
            // OPTIONS for CORS preflights
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            // registration stays "open" – your interceptor enforces the internal header
            .requestMatchers(HttpMethod.POST, "/register").permitAll()
            // everything else denied
            .anyRequest().denyAll()
        );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
