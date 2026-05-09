package com.ibizdrive.common.error;

import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.config.MethodSecurityConfig;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.Preset;
import com.ibizdrive.share.ShareController;
import com.ibizdrive.share.ShareExceedsMembershipException;
import com.ibizdrive.user.DbUserDetailsService;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link GlobalExceptionHandler} 예외 매핑 통합 테스트.
 *
 * <p>MockMvc를 통해 controller → handler → envelope 매핑을 검증.
 * 각 exception handler가 정확한 HTTP status + envelope code를 반환하는지 확인.
 */
@WebMvcTest(controllers = ShareController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class,
    AuthExceptionHandler.class, GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private com.ibizdrive.share.ShareCommandService shareCommandService;

    @MockBean
    private com.ibizdrive.share.ShareQueryService shareQueryService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private DbUserDetailsService userDetailsService;

    @MockBean
    private PermissionEvaluator permissionEvaluator;

    @Test
    void shareExceedsMembership_returns403WithCode() throws Exception {
        UUID fileId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();

        com.ibizdrive.share.ShareCreateRequest req = new com.ibizdrive.share.ShareCreateRequest(
            java.util.List.of(new com.ibizdrive.share.ShareCreateRequest.Subject("user", subjectId)),
            "admin", null, null
        );

        // sharer membership MEMBER (READ, UPLOAD, EDIT) 권한에서 ADMIN 요청 시도
        Set<Permission> sharerMembershipPerms = Set.of(
            Permission.READ,
            Permission.UPLOAD,
            Permission.EDIT
        );

        doThrow(new ShareExceedsMembershipException(Preset.ADMIN, sharerMembershipPerms))
            .when(shareCommandService).createShares(any(), any(), any());

        User principal = new User(
            ACTOR, "user@example.com", "User", "{bcrypt}$2a$12$dummy",
            Role.MEMBER, true, false, OffsetDateTime.now()
        );

        // Mock permissionEvaluator to return true (user has SHARE permission on file)
        when(permissionEvaluator.hasPermission(any(), eq(fileId), eq("file"), eq("SHARE")))
            .thenReturn(true);

        mvc.perform(post("/api/files/{fileId}/share", fileId)
                .with(user(new IbizDriveUserDetails(principal)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subjects\":[{\"type\":\"user\",\"id\":\"" + subjectId + "\"}],\"preset\":\"admin\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("SHARE_EXCEEDS_MEMBER"));
    }
}
