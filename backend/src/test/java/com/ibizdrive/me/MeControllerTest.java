package com.ibizdrive.me;

import com.ibizdrive.auth.LoginAttemptTracker;
import com.ibizdrive.common.error.AdminExceptionHandler;
import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import com.ibizdrive.config.MethodSecurityConfig;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.me.dto.MySharedWithMeItem;
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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MeController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class,
    AuthExceptionHandler.class, AdminExceptionHandler.class, GlobalExceptionHandler.class})
class MeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private MeSharedQueryService meSharedQueryService;

    @MockBean
    private LoginAttemptTracker tracker;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private DbUserDetailsService dbUserDetailsService;

    @MockBean
    private PermissionEvaluator permissionEvaluator;

    private IbizDriveUserDetails memberPrincipal;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        User member = new User(userId, "m@example.com", "Member",
            "{bcrypt}$2a$12$dummy", Role.MEMBER, true, false, OffsetDateTime.now());
        memberPrincipal = new IbizDriveUserDetails(member);
    }

    @Test
    void sharedWithMe_authenticated_returns200WithEnvelope() throws Exception {
        UUID resourceId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        UUID granterId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID parentFolderId = UUID.randomUUID();
        MySharedWithMeItem item = new MySharedWithMeItem(
            permissionId, "file", resourceId, "계약서.pdf", "read",
            Instant.parse("2026-05-14T08:00:00Z"),
            new MySharedWithMeItem.Granter(granterId, "김매니저"),
            new MySharedWithMeItem.Workspace("department", workspaceId),
            parentFolderId
        );
        when(meSharedQueryService.list(eq(userId), eq(5)))
            .thenReturn(List.of(item));

        mvc.perform(get("/api/me/shared-with-me?limit=5").with(user(memberPrincipal)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.items").isArray())
           .andExpect(jsonPath("$.items.length()").value(1))
           .andExpect(jsonPath("$.items[0].name").value("계약서.pdf"))
           .andExpect(jsonPath("$.items[0].preset").value("read"))
           .andExpect(jsonPath("$.items[0].grantedBy.name").value("김매니저"))
           .andExpect(jsonPath("$.items[0].workspace.kind").value("department"))
           .andExpect(jsonPath("$.items[0].workspace.id").value(workspaceId.toString()))
           .andExpect(jsonPath("$.items[0].navigationFolderId").value(parentFolderId.toString()));
    }

    @Test
    void sharedWithMe_default_limit_20() throws Exception {
        when(meSharedQueryService.list(any(), eq(20))).thenReturn(List.of());

        mvc.perform(get("/api/me/shared-with-me").with(user(memberPrincipal)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.items").isArray())
           .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void sharedWithMe_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/me/shared-with-me"))
           .andExpect(status().isUnauthorized());
    }
}
