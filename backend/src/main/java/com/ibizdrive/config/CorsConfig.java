package com.ibizdrive.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    private final CorsProperties props;

    public CorsConfig(CorsProperties props) {
        this.props = props;
    }

    @Bean
    public CorsFilter corsFilter() {
        var source = new UrlBasedCorsConfigurationSource();
        var config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(props.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "HEAD", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-CSRF-Token", "X-Request-Id",
                "Tus-Resumable", "Upload-Length", "Upload-Offset", "Upload-Metadata"));
        config.setExposedHeaders(List.of("X-Request-Id", "X-RateLimit-Remaining",
                "Tus-Resumable", "Upload-Offset", "Location"));
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
