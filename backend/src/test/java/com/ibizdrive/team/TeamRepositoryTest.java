package com.ibizdrive.team;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class TeamRepositoryTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TeamRepository repo;

    @Test
    void findActiveByNormalizedName_returnsPresent_whenActive() {
        UUID userA = persistUser("a@t", "alpha-owner");
        Team active = new Team(UUID.randomUUID(), "Alpha", "alpha", null,
            Team.Visibility.PRIVATE, userA, OffsetDateTime.now());
        em.persist(active);
        em.flush();
        assertThat(repo.findActiveByNormalizedName("alpha"))
            .isPresent()
            .get()
            .extracting(Team::getId)
            .isEqualTo(active.getId());
    }

    @Test
    void findActiveByNormalizedName_returnsEmpty_whenArchived() {
        UUID u = persistUser("b@t", "beta-owner");
        Team t = new Team(UUID.randomUUID(), "Beta", "beta", null,
            Team.Visibility.PRIVATE, u, OffsetDateTime.now());
        em.persist(t);
        em.getEntityManager().createNativeQuery(
            "UPDATE teams SET archived_at = NOW() WHERE id = ?")
            .setParameter(1, t.getId()).executeUpdate();
        em.clear();
        assertThat(repo.findActiveByNormalizedName("beta")).isEmpty();
    }

    private UUID persistUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        em.getEntityManager().createNativeQuery(
            "INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)")
            .setParameter(1, id).setParameter(2, email).setParameter(3, displayName)
            .executeUpdate();
        return id;
    }
}
