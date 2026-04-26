package com.ibizdrive.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SecurityConfig#passwordEncoder()} 단위 테스트 — Spring 컨텍스트 없이
 * @Bean 메서드를 직접 호출 (메서드 자체는 의존성 없는 정적 동작).
 *
 * <p>검증 항목 (ADR #19):
 * <ul>
 *   <li>{@code {bcrypt}} 프리픽스 (DelegatingPasswordEncoder 호환)</li>
 *   <li>strength=12 ({@code $2a$12$} 또는 {@code $2b$12$} cost factor)</li>
 *   <li>encode/matches 라운드트립</li>
 * </ul>
 */
class SecurityConfigTest {

    private final PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

    @Test
    void encode_producesBcryptPrefixedHashWithStrength12() {
        String hash = encoder.encode("CorrectHorse123");

        assertTrue(hash.startsWith("{bcrypt}"),
            "DelegatingPasswordEncoder는 알고리즘 프리픽스를 강제해야 함");
        // BCrypt cost factor는 {bcrypt} 프리픽스 직후 $2a$12$ 또는 $2b$12$
        String bcryptPart = hash.substring("{bcrypt}".length());
        assertTrue(bcryptPart.startsWith("$2a$12$") || bcryptPart.startsWith("$2b$12$"),
            "ADR #19 strength=12 강제 — 실제 해시: " + bcryptPart);
    }

    @Test
    void matches_returnsTrue_forCorrectPassword() {
        String raw = "CorrectHorse123";
        String hash = encoder.encode(raw);

        assertTrue(encoder.matches(raw, hash));
    }

    @Test
    void matches_returnsFalse_forWrongPassword() {
        String hash = encoder.encode("CorrectHorse123");

        assertFalse(encoder.matches("WrongHorse123", hash));
    }

    @Test
    void encode_producesDifferentHashes_forSameInput() {
        // BCrypt salt가 매번 무작위 생성되어야 함 (rainbow table 저항)
        String h1 = encoder.encode("samePassword");
        String h2 = encoder.encode("samePassword");

        assertNotEquals(h1, h2);
        assertTrue(encoder.matches("samePassword", h1));
        assertTrue(encoder.matches("samePassword", h2));
    }
}
