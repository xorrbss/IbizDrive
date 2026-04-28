package com.ibizdrive.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Method-level security wiring — {@code @PreAuthorize} 표현식의 {@code hasPermission(...)}이
 * {@link com.ibizdrive.permission.IbizDrivePermissionEvaluator}로 위임되도록 설정.
 *
 * <p>{@link EnableMethodSecurity}는 {@code @PreAuthorize}/{@code @PostAuthorize} 처리를 활성화한다.
 * Spring Security 6에서는 {@code prePostEnabled = true}가 기본값이지만 명시한다.
 *
 * <p>{@link DefaultMethodSecurityExpressionHandler}에 {@link PermissionEvaluator}를 주입하지 않으면
 * 기본 {@code DenyAllPermissionEvaluator}가 사용되어 모든 {@code hasPermission(...)} 호출이 deny.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(PermissionEvaluator permissionEvaluator) {
        var handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }
}
