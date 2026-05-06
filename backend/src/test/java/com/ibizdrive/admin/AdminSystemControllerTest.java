package com.ibizdrive.admin;

import com.ibizdrive.auth.LoginAttemptTracker;
import com.ibizdrive.common.error.AdminExceptionHandler;
import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import com.ibizdrive.config.MethodSecurityConfig;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.permission.PermissionExpirationProperties;
import com.ibizdrive.purge.HardPurgeProperties;
import com.ibizdrive.share.ShareExpirationProperties;
import com.ibizdrive.storage.StorageOrphanCleanupProperties;
import com.ibizdrive.user.DbUserDetailsService;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AdminSystemController} sliced WebMvcTest — Wave 1 — T3.
 *
 * <p>{@code GET /api/admin/system/cron}이 4개 cron 잡 설정을 정해진 순서/페이로드로 노출하는지 검증.
 * read-only — audit emit 0, side effect 0. 매트릭스: 200 / 401 / 403.
 */
@WebMvcTest(controllers = AdminSystemController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class,
    AuthExceptionHandler.class, AdminExceptionHandler.class, GlobalExceptionHandler.class,
    AdminSystemControllerTest.PropertiesConfig.class})
class AdminSystemControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private LoginAttemptTracker tracker;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private DbUserDetailsService dbUserDetailsService;

    @MockBean
    private PermissionEvaluator permissionEvaluator;

    private IbizDriveUserDetails adminPrincipal;
    private IbizDriveUserDetails memberPrincipal;
    private IbizDriveUserDetails auditorPrincipal;

    @BeforeEach
    void setUp() {
        UUID adminId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        User admin = new User(adminId, "admin@example.com", "Admin",
            "{bcrypt}$2a$12$dummy", Role.ADMIN, true, false, OffsetDateTime.now());
        adminPrincipal = new IbizDriveUserDetails(admin);

        User member = new User(UUID.randomUUID(), "m@example.com", "Member",
            "{bcrypt}$2a$12$dummy", Role.MEMBER, true, false, OffsetDateTime.now());
        memberPrincipal = new IbizDriveUserDetails(member);

        User auditor = new User(UUID.randomUUID(), "a@example.com", "Auditor",
            "{bcrypt}$2a$12$dummy", Role.AUDITOR, true, false, OffsetDateTime.now());
        auditorPrincipal = new IbizDriveUserDetails(auditor);
    }

    @Test
    void getCronStatus_admin_returns200WithFourJobsInFixedOrder() throws Exception {
        mvc.perform(get("/api/admin/system/cron").with(user(adminPrincipal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobs.length()").value(4))
            // 순서 고정: purge → share → permission → storage
            .andExpect(jsonPath("$.jobs[0].key").value("purge.expired"))
            .andExpect(jsonPath("$.jobs[1].key").value("share.expire"))
            .andExpect(jsonPath("$.jobs[2].key").value("permission.expire"))
            .andExpect(jsonPath("$.jobs[3].key").value("storage.orphan.cleanup"));
    }

    @Test
    void getCronStatus_admin_purgeJobPayload() throws Exception {
        mvc.perform(get("/api/admin/system/cron").with(user(adminPrincipal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobs[0].key").value("purge.expired"))
            .andExpect(jsonPath("$.jobs[0].label").value("휴지통 hard purge"))
            .andExpect(jsonPath("$.jobs[0].enabled").value(false))
            .andExpect(jsonPath("$.jobs[0].cron").value("0 0 0 * * *"))
            .andExpect(jsonPath("$.jobs[0].zone").value("Asia/Seoul"))
            .andExpect(jsonPath("$.jobs[0].maxPerRun").value(10000))
            .andExpect(jsonPath("$.jobs[0].batchSize").doesNotExist())
            .andExpect(jsonPath("$.jobs[0].graceHours").doesNotExist());
    }

    @Test
    void getCronStatus_admin_shareExpireJobPayload() throws Exception {
        mvc.perform(get("/api/admin/system/cron").with(user(adminPrincipal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobs[1].key").value("share.expire"))
            .andExpect(jsonPath("$.jobs[1].label").value("공유 만료 처리"))
            .andExpect(jsonPath("$.jobs[1].enabled").value(true))
            .andExpect(jsonPath("$.jobs[1].cron").value("0 */5 * * * *"))
            .andExpect(jsonPath("$.jobs[1].zone").value("Asia/Seoul"))
            .andExpect(jsonPath("$.jobs[1].batchSize").value(200))
            .andExpect(jsonPath("$.jobs[1].maxPerRun").doesNotExist())
            .andExpect(jsonPath("$.jobs[1].graceHours").doesNotExist());
    }

    @Test
    void getCronStatus_admin_permissionExpireJobPayload() throws Exception {
        mvc.perform(get("/api/admin/system/cron").with(user(adminPrincipal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobs[2].key").value("permission.expire"))
            .andExpect(jsonPath("$.jobs[2].label").value("권한 만료 처리"))
            .andExpect(jsonPath("$.jobs[2].enabled").value(false))
            .andExpect(jsonPath("$.jobs[2].cron").value("0 */5 * * * *"))
            .andExpect(jsonPath("$.jobs[2].zone").value("Asia/Seoul"))
            .andExpect(jsonPath("$.jobs[2].batchSize").value(200));
    }

    @Test
    void getCronStatus_admin_storageOrphanJobPayload() throws Exception {
        mvc.perform(get("/api/admin/system/cron").with(user(adminPrincipal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobs[3].key").value("storage.orphan.cleanup"))
            .andExpect(jsonPath("$.jobs[3].label").value("스토리지 고아 정리"))
            .andExpect(jsonPath("$.jobs[3].enabled").value(false))
            .andExpect(jsonPath("$.jobs[3].cron").value("0 0 1 * * *"))
            .andExpect(jsonPath("$.jobs[3].zone").value("Asia/Seoul"))
            .andExpect(jsonPath("$.jobs[3].maxPerRun").value(10000))
            .andExpect(jsonPath("$.jobs[3].graceHours").value(24));
    }

    @Test
    void getCronStatus_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/admin/system/cron"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getCronStatus_member_returns403() throws Exception {
        mvc.perform(get("/api/admin/system/cron").with(user(memberPrincipal)))
            .andExpect(status().isForbidden());
    }

    @Test
    void getCronStatus_auditor_returns403() throws Exception {
        // 운영 cron 설정은 ADMIN 단독 책임 — AUDITOR 미허용 (T2 audit-export와 분리).
        mvc.perform(get("/api/admin/system/cron").with(user(auditorPrincipal)))
            .andExpect(status().isForbidden());
    }

    /**
     * sliced WebMvcTest는 {@code @ConfigurationProperties} 빈을 자동 생성하지 않으므로
     * 본 트랙 테스트가 의존하는 4 properties를 명시 instantiation으로 주입.
     * 값은 {@code application.yml} 기본값과 동형 (purge/perm/storage는 enabled=false, share만 true로
     * 설정 toggle 노출이 의미 있다는 신호로 차별화).
     */
    static class PropertiesConfig {
        @org.springframework.context.annotation.Bean
        HardPurgeProperties hardPurgeProperties() {
            return new HardPurgeProperties(false, 10000, "0 0 0 * * *", "Asia/Seoul");
        }

        @org.springframework.context.annotation.Bean
        ShareExpirationProperties shareExpirationProperties() {
            return new ShareExpirationProperties(true, 200, "0 */5 * * * *", "Asia/Seoul");
        }

        @org.springframework.context.annotation.Bean
        PermissionExpirationProperties permissionExpirationProperties() {
            return new PermissionExpirationProperties(false, 200, "0 */5 * * * *", "Asia/Seoul");
        }

        @org.springframework.context.annotation.Bean
        StorageOrphanCleanupProperties storageOrphanCleanupProperties() {
            return new StorageOrphanCleanupProperties(false, "0 0 1 * * *", "Asia/Seoul",
                10000, 24, 200);
        }
    }
}
