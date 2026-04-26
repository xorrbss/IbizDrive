package com.ibizdrive.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UserRepository} 통합 테스트.
 *
 * <p>실제 Postgres 컨테이너에 Flyway V1+V2 마이그레이션이 적용된 상태에서
 * {@code findActiveByEmail}이 (a) lowercase 정규화된 이메일에 매칭하고
 * (b) 미정규화 입력(원본 대소문자)은 매칭하지 않으며 (c) 미존재 이메일은 빈 결과를
 * 반환하는지 검증한다 (docs/03 §2.7 — 호출자가 미리 lowercase 적용해야 함).
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = NONE)} — 기본 H2 대체를 막고
 * Testcontainers가 제공하는 Postgres URL을 사용한다. {@code @DynamicPropertySource}로
 * 컨테이너의 JDBC URL을 주입한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class UserRepositoryTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Flyway가 V1+V2 적용 → JPA validate 모드로 schema 일치 검증
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void findActiveByEmail_matchesCaseInsensitive_whenCallerLowercases() {
        UUID id = UUID.randomUUID();
        User saved = new User(
            id,
            "Alice@Example.com",
            "Alice",
            "{bcrypt}$2a$12$dummyhashfortestonlydummyhashfortestonlydummyhashfortest",
            Role.MEMBER,
            true,
            false,
            OffsetDateTime.now()
        );
        userRepository.save(saved);

        Optional<User> found = userRepository.findActiveByEmail("alice@example.com");

        assertTrue(found.isPresent(), "lowercase 정규화된 이메일로 활성 사용자 조회 가능해야 함");
        assertEquals(id, found.get().getId());
        assertEquals(Role.MEMBER, found.get().getRole());
        assertTrue(found.get().isActive());
        assertFalse(found.get().isMustChangePassword());
    }

    @Test
    void findActiveByEmail_doesNotMatch_whenInputIsNotLowercased() {
        UUID id = UUID.randomUUID();
        User saved = new User(
            id,
            "bob@example.com",
            "Bob",
            null,            // SSO 사용자 가정 — password_hash NULL 허용 (ADR #19)
            Role.AUDITOR,
            true,
            false,
            OffsetDateTime.now()
        );
        userRepository.save(saved);

        // 호출자가 lowercase 적용을 빠뜨린 경우 — 매치 안 됨 (caller 책임 명시)
        Optional<User> found = userRepository.findActiveByEmail("Bob@Example.com");

        assertFalse(found.isPresent(),
            "@Query는 LOWER(u.email)만 적용 — 입력 파라미터의 lowercase는 호출자 책임");
    }

    @Test
    void findActiveByEmail_returnsEmpty_whenEmailNotFound() {
        Optional<User> found = userRepository.findActiveByEmail("ghost@example.com");

        assertFalse(found.isPresent());
    }
}
