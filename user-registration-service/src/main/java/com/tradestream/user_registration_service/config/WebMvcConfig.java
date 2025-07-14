// src/main/java/com/tradestream/user_registration_service/config/WebMvcConfig.java
package com.tradestream.user_registration_service.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.tradestream.user_registration_service.security.InternalCallerInterceptor;


@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final InternalCallerInterceptor interceptor;

    @Autowired
    public WebMvcConfig(InternalCallerInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/register"); // 👈 apply to specific sensitive endpoints
    }
}
