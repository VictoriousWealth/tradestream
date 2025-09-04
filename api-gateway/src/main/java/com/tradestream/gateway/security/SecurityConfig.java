package com.tradestream.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  @Bean
  SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    http
      .csrf(ServerHttpSecurity.CsrfSpec::disable)
      .cors(Customizer.withDefaults())
      .authorizeExchange(ex -> ex
        // Public health
        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
        // Public auth endpoints (edge validates JWT elsewhere)
        .pathMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
        .pathMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
        // Public registration (still header-gated at downstream)
        .pathMatchers(HttpMethod.POST, "/api/users/register").permitAll()
        // Everything else needs a valid JWT
        .anyExchange().authenticated()
      )
      .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter())));
    return http.build();
  }

  private ReactiveJwtAuthenticationConverterAdapter converter() {
    var delegate = new JwtAuthenticationConverter();
    delegate.setJwtGrantedAuthoritiesConverter((Jwt jwt) -> {
      Object scopes = jwt.getClaims().getOrDefault("scopes", jwt.getClaims().get("scope"));
      Collection<String> list = switch (scopes) {
        case null -> List.of();
        case String s -> Arrays.stream(s.split("\\s+")).filter(x -> !x.isBlank()).toList();
        case Collection<?> c -> c.stream().map(Object::toString).toList();
        default -> List.of();
      };
      return list.stream().map(s -> (GrantedAuthority) () -> "SCOPE_" + s).collect(Collectors.toSet());
    });
    return new ReactiveJwtAuthenticationConverterAdapter(delegate);
  }
}

