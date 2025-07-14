package com.tradestream.user_registration_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 🔹 Disable CSRF (only for APIs; keep it enabled for traditional web apps)
        http.csrf(AbstractHttpConfigurer::disable);

        // 🔹 CORS configuration (enable if frontend is on a different origin)
        http.cors(Customizer.withDefaults());

        // 🔹 Authorization
        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers(HttpMethod.GET, "/", "/error").permitAll();
            auth.requestMatchers("/register").permitAll();            
            auth.anyRequest().denyAll(); // Protect all other routes by denying access
        });

        return http.build();
    }


}