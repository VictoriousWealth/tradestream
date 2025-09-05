// api-gateway/src/main/java/com/tradestream/gateway/security/JwtDecoderConfig.java
package com.tradestream.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class JwtDecoderConfig {

  @Bean
  ReactiveJwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.public-key-location}") Resource pubKeyLocation
  ) throws Exception {
    try (InputStream is = pubKeyLocation.getInputStream()) {
      RSAPublicKey pub = (RSAPublicKey) RsaKeyConverters.x509().convert(is);
      return NimbusReactiveJwtDecoder
          .withPublicKey(pub)
          .signatureAlgorithm(SignatureAlgorithm.PS256) // <— force PS256
          .build();
    }
  }
}

