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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class TeamMembershipRepositoryTest {

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
    private TeamMembershipRepository repo;

    @Test
    void findByUserId_returnsAllMemberships_whenUserHasMultiple() {
        UUID user = persistUser("u@t", "u");
        UUID t1 = persistTeam("Alpha", "alpha", user);
        UUID t2 = persistTeam("Beta", "beta", user);
        em.persist(new TeamMembership(t1, user, TeamMembership.Role.OWNER, null, OffsetDateTime.now()));
        em.persist(new TeamMembership(t2, user, TeamMembership.Role.MEMBER, null, OffsetDateTime.now()));
        em.flush();

        List<TeamMembership> all = repo.findByUserId(user);

        assertThat(all).hasSize(2);
        assertThat(all).extracting(TeamMembership::getTeamId)
            .containsExactlyInAnyOrder(t1, t2);
    }

    @Test
    void countByTeamIdAndRole_returnsCount() {
        UUID user1 = persistUser("o1@t", "o1");
        UUID user2 = persistUser("o2@t", "o2");
        UUID team = persistTeam("T", "t", user1);
        em.persist(new TeamMembership(team, user1, TeamMembership.Role.OWNER, null, OffsetDateTime.now()));
        em.persist(new TeamMembership(team, user2, TeamMembership.Role.MEMBER, null, OffsetDateTime.now()));
        em.flush();

        assertThat(repo.countByTeamIdAndRole(team, TeamMembership.Role.OWNER)).isEqualTo(1);
    }

    @Test
    void findByTeamId_returnsAllMembersOfTeam() {
        UUID user1 = persistUser("m1@t", "m1");
        UUID user2 = persistUser("m2@t", "m2");
        UUID team = persistTeam("Gamma", "gamma", user1);
        em.persist(new TeamMembership(team, user1, TeamMembership.Role.OWNER, null, OffsetDateTime.now()));
        em.persist(new TeamMembership(team, user2, TeamMembership.Role.MEMBER, null, OffsetDateTime.now()));
        em.flush();

        assertThat(repo.findByTeamId(team)).hasSize(2);
    }

    private UUID persistUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        em.getEntityManager().createNativeQuery(
            "INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)")
            .setParameter(1, id).setParameter(2, email).setParameter(3, displayName)
            .executeUpdate();
        return id;
    }

    private UUID persistTeam(String name, String normalized, UUID createdBy) {
        Team t = new Team(UUID.randomUUID(), name, normalized, null,
            Team.Visibility.PRIVATE, createdBy, OffsetDateTime.now());
        em.persist(t);
        return t.getId();
    }
}
