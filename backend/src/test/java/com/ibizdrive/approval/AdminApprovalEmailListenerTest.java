package com.ibizdrive.approval;

import com.ibizdrive.email.EmailService;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AdminApprovalEmailListener} 단위 — ADR #47 Phase 4 (docs/04 §16.4.4).
 *
 * <p>Service mock. 4 transition 매트릭스 + disabled-skip + per-recipient 격리 + lookup miss
 * + soft-delete 가드 검증. Spring 컨텍스트 미사용.
 */
class AdminApprovalEmailListenerTest {

    private EmailService emailService;
    private UserRepository userRepository;
    private AdminApprovalEmailListener listener;

    private static final String BASE_URL = "http://app.example.com";
    private static final String FROM = "noreply@ibizdrive.local";

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        userRepository = mock(UserRepository.class);
        AdminApprovalEmailProperties props =
            new AdminApprovalEmailProperties(/*enabled*/ true, BASE_URL, FROM);
        listener = new AdminApprovalEmailListener(emailService, userRepository, props);
    }

    private User admin(UUID id, String email) {
        return new User(id, email, "name-" + id.toString().substring(0, 6),
            "{bcrypt}$2a$12$dummy", Role.ADMIN, true, false, OffsetDateTime.now());
    }

    /**
     * soft-delete 상태의 User — entity가 deletedAt setter를 노출하지 않아 reflection으로 필드 주입.
     * listener의 isDeleted 가드 검증 용도, 테스트 한정.
     */
    private User softDeletedAdmin(UUID id, String email) {
        User u = admin(id, email);
        try {
            java.lang.reflect.Field f = User.class.getDeclaredField("deletedAt");
            f.setAccessible(true);
            f.set(u, OffsetDateTime.now().minusDays(1));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return u;
    }

    private AdminApprovalDecidedEvent event(
        PendingApprovalStatus status, String actionType,
        UUID actorId, UUID primaryApproverId, UUID secondaryId, String decisionReason
    ) {
        return new AdminApprovalDecidedEvent(
            UUID.randomUUID(), actionType, status, actorId, primaryApproverId, secondaryId,
            /*payloadJson*/ "{}", decisionReason);
    }

    // ──────────────────────────────────────────────────────────────────
    // disabled-skip
    // ──────────────────────────────────────────────────────────────────

    @Test
    void disabled_doesNotInvokeEmailOrRepo() {
        AdminApprovalEmailProperties offProps =
            new AdminApprovalEmailProperties(false, BASE_URL, FROM);
        AdminApprovalEmailListener offListener =
            new AdminApprovalEmailListener(emailService, userRepository, offProps);

        offListener.onApprovalDecided(event(
            PendingApprovalStatus.REQUESTED, "role_change",
            UUID.randomUUID(), null, null, null));

        verifyNoInteractions(emailService);
        verifyNoInteractions(userRepository);
    }

    // ──────────────────────────────────────────────────────────────────
    // REQUESTED — 다인 발송
    // ──────────────────────────────────────────────────────────────────

    @Test
    void requested_sendsToAllActiveAdmins_exceptRequester() {
        UUID requesterId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID admin2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID admin3 = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(userRepository.findActiveAdmins()).thenReturn(List.of(
            admin(requesterId, "primary@example.com"),
            admin(admin2, "admin2@example.com"),
            admin(admin3, "admin3@example.com")));

        listener.onApprovalDecided(event(
            PendingApprovalStatus.REQUESTED, "role_change",
            /*actor=requested_by*/ requesterId, null, null, null));

        verify(emailService, times(2)).send(anyString(), anyString(), anyString());
        verify(emailService).send(eq("admin2@example.com"), anyString(), anyString());
        verify(emailService).send(eq("admin3@example.com"), anyString(), anyString());
        verify(emailService, never()).send(eq("primary@example.com"), anyString(), anyString());
    }

    @Test
    void requested_subject_hasRequestPrefix_andKoreanActionLabel() {
        UUID requesterId = UUID.randomUUID();
        when(userRepository.findActiveAdmins()).thenReturn(List.of(
            admin(requesterId, "primary@example.com"),
            admin(UUID.randomUUID(), "admin2@example.com")));

        listener.onApprovalDecided(event(
            PendingApprovalStatus.REQUESTED, "retention_change",
            requesterId, null, null, null));

        ArgumentCaptor<String> subjectCap = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(anyString(), subjectCap.capture(), anyString());
        assertThat(subjectCap.getValue()).startsWith("[승인 요청]");
        assertThat(subjectCap.getValue()).contains("휴지통 보존 정책 변경");
    }

    @Test
    void requested_body_containsApprovalDeepLink() {
        UUID requesterId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        when(userRepository.findActiveAdmins()).thenReturn(List.of(
            admin(requesterId, "primary@example.com"),
            admin(UUID.randomUUID(), "admin2@example.com")));

        AdminApprovalDecidedEvent ev = new AdminApprovalDecidedEvent(
            approvalId, "role_change", PendingApprovalStatus.REQUESTED,
            requesterId, null, null, "{}", null);
        listener.onApprovalDecided(ev);

        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(anyString(), anyString(), bodyCap.capture());
        assertThat(bodyCap.getValue()).contains(BASE_URL + "/admin/approvals/" + approvalId);
        assertThat(bodyCap.getValue()).contains("새 2인 승인 요청");
    }

    @Test
    void requested_perRecipientFailure_doesNotBlockNextRecipient() {
        UUID requesterId = UUID.randomUUID();
        when(userRepository.findActiveAdmins()).thenReturn(List.of(
            admin(requesterId, "primary@example.com"),
            admin(UUID.randomUUID(), "admin2@example.com"),
            admin(UUID.randomUUID(), "admin3@example.com")));
        doThrow(new RuntimeException("smtp blip"))
            .when(emailService).send(eq("admin2@example.com"), anyString(), anyString());

        assertThatCode(() -> listener.onApprovalDecided(event(
            PendingApprovalStatus.REQUESTED, "role_change",
            requesterId, null, null, null)))
            .doesNotThrowAnyException();

        verify(emailService).send(eq("admin2@example.com"), anyString(), anyString());
        verify(emailService).send(eq("admin3@example.com"), anyString(), anyString());
    }

    @Test
    void requested_singleAdmin_withRequesterOnly_sends0() {
        // ADMIN 1명(요청자 본인)뿐이면 secondary 후보가 없어 send 0.
        UUID solo = UUID.randomUUID();
        when(userRepository.findActiveAdmins()).thenReturn(List.of(admin(solo, "solo@example.com")));

        listener.onApprovalDecided(event(
            PendingApprovalStatus.REQUESTED, "trash_purge",
            solo, null, null, null));

        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    // ──────────────────────────────────────────────────────────────────
    // APPROVED / REJECTED / EXPIRED — requested_by 단일 발송
    // ──────────────────────────────────────────────────────────────────

    @Test
    void approved_sendsToRequester_withSecondaryAndReason() {
        UUID requester = UUID.randomUUID();
        UUID secondary = UUID.randomUUID();
        when(userRepository.findById(requester))
            .thenReturn(Optional.of(admin(requester, "requester@example.com")));

        listener.onApprovalDecided(event(
            PendingApprovalStatus.APPROVED, "role_change",
            /*actor=*/secondary, /*primary=requested_by*/ requester, secondary, "OK 검토 완료"));

        ArgumentCaptor<String> subjectCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("requester@example.com"), subjectCap.capture(), bodyCap.capture());
        assertThat(subjectCap.getValue()).startsWith("[승인됨]");
        assertThat(bodyCap.getValue()).contains("승인됨");
        assertThat(bodyCap.getValue()).contains(secondary.toString());
        assertThat(bodyCap.getValue()).contains("OK 검토 완료");
    }

    @Test
    void rejected_sendsToRequester_withRejectPrefix() {
        UUID requester = UUID.randomUUID();
        UUID secondary = UUID.randomUUID();
        when(userRepository.findById(requester))
            .thenReturn(Optional.of(admin(requester, "requester@example.com")));

        listener.onApprovalDecided(event(
            PendingApprovalStatus.REJECTED, "trash_purge",
            secondary, requester, secondary, "사유 부족"));

        ArgumentCaptor<String> subjectCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("requester@example.com"), subjectCap.capture(), bodyCap.capture());
        assertThat(subjectCap.getValue()).startsWith("[거부됨]");
        assertThat(bodyCap.getValue()).contains("거부됨");
        assertThat(bodyCap.getValue()).contains("사유 부족");
    }

    @Test
    void expired_sendsToRequester_withRetryHint() {
        UUID requester = UUID.randomUUID();
        when(userRepository.findById(requester))
            .thenReturn(Optional.of(admin(requester, "requester@example.com")));

        listener.onApprovalDecided(event(
            PendingApprovalStatus.EXPIRED, "retention_change",
            /*actor=*/null, requester, /*secondary*/ null, /*reason*/ null));

        ArgumentCaptor<String> subjectCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("requester@example.com"), subjectCap.capture(), bodyCap.capture());
        assertThat(subjectCap.getValue()).startsWith("[만료]");
        assertThat(bodyCap.getValue()).contains("만료된 요청은 자동 처리되지 않습니다");
    }

    @Test
    void approved_lookupMiss_doesNotSend() {
        UUID requester = UUID.randomUUID();
        when(userRepository.findById(requester)).thenReturn(Optional.empty());

        assertThatCode(() -> listener.onApprovalDecided(event(
            PendingApprovalStatus.APPROVED, "role_change",
            UUID.randomUUID(), requester, UUID.randomUUID(), null)))
            .doesNotThrowAnyException();

        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void approved_softDeletedRequester_doesNotSend() {
        UUID requester = UUID.randomUUID();
        when(userRepository.findById(requester))
            .thenReturn(Optional.of(softDeletedAdmin(requester, "deleted@example.com")));

        listener.onApprovalDecided(event(
            PendingApprovalStatus.APPROVED, "role_change",
            UUID.randomUUID(), requester, UUID.randomUUID(), null));

        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void approved_nullPrimary_doesNotSend() {
        // 이론상 service가 APPROVED publish 시 primary를 반드시 채우지만, 방어 가드 검증.
        listener.onApprovalDecided(event(
            PendingApprovalStatus.APPROVED, "role_change",
            UUID.randomUUID(), /*primary*/ null, UUID.randomUUID(), null));

        verifyNoInteractions(userRepository);
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    // ──────────────────────────────────────────────────────────────────
    // CANCELLED — emit 없음
    // ──────────────────────────────────────────────────────────────────

    @Test
    void cancelled_doesNotSendOrLookup() {
        listener.onApprovalDecided(event(
            PendingApprovalStatus.CANCELLED, "role_change",
            UUID.randomUUID(), UUID.randomUUID(), null, null));

        verifyNoInteractions(emailService);
        verifyNoInteractions(userRepository);
    }

    // ──────────────────────────────────────────────────────────────────
    // 다인 발송 순서 검증
    // ──────────────────────────────────────────────────────────────────

    @Test
    void requested_preservesFindActiveAdminsOrder() {
        UUID a1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID a2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID a3 = UUID.fromString("33333333-3333-3333-3333-333333333333");
        // requester는 a1. send는 a2 → a3 순.
        when(userRepository.findActiveAdmins()).thenReturn(List.of(
            admin(a1, "a1@example.com"),
            admin(a2, "a2@example.com"),
            admin(a3, "a3@example.com")));

        listener.onApprovalDecided(event(
            PendingApprovalStatus.REQUESTED, "role_change",
            a1, null, null, null));

        InOrder order = inOrder(emailService);
        order.verify(emailService).send(eq("a2@example.com"), anyString(), anyString());
        order.verify(emailService).send(eq("a3@example.com"), anyString(), anyString());
    }
}
