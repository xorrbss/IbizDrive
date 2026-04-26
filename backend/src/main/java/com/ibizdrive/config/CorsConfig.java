package com.ibizdrive.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${ibizdrive.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        var source = new UrlBasedCorsConfigurationSource();
        var config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "HEAD", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-CSRF-Token", "X-Request-Id",
                "Tus-Resumable", "Upload-Length", "Upload-Offset", "Upload-Metadata"));
        config.setExposedHeaders(List.of("X-Request-Id", "X-RateLimit-Remaining",
                "Tus-Resumable", "Upload-Offset", "Location"));
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
