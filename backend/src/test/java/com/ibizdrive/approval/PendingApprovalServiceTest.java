package com.ibizdrive.approval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PendingApprovalService} 단위 — ADR #47 / dual-approval Phase 2. Mockito mock repo + handler
 * + publisher로 Postgres 의존성 없이 5 transition state machine 검증.
 */
class PendingApprovalServiceTest {

    private PendingAdminApprovalRepository repository;
    private ApplicationEventPublisher publisher;
    private AdminApprovalActionHandler roleHandler;
    private PendingApprovalService service;

    private UUID requester;
    private UUID secondary;
    private UUID approvalId;

    @BeforeEach
    void setUp() {
        repository = mock(PendingAdminApprovalRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        roleHandler = mock(AdminApprovalActionHandler.class);
        when(roleHandler.actionType()).thenReturn("role_change");
        service = new PendingApprovalService(repository, publisher, List.of(roleHandler));

        requester = UUID.randomUUID();
        secondary = UUID.randomUUID();
        approvalId = UUID.randomUUID();

        // 기본 save() echo
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ──────────────────────────────────────────────────────────────────
    // submit
    // ──────────────────────────────────────────────────────────────────

    @Test
    void submit_persistsRowAsRequestedAndPublishesEvent() {
        PendingAdminApproval saved = service.submit("role_change", "{\"userId\":\"u\"}", requester, 7);

        assertThat(saved.getStatus()).isEqualTo(PendingApprovalStatus.REQUESTED);
        assertThat(saved.getRequestedBy()).isEqualTo(requester);
        assertThat(saved.getExpiresAt()).isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusDays(6));

        ArgumentCaptor<AdminApprovalDecidedEvent> evt =
            ArgumentCaptor.forClass(AdminApprovalDecidedEvent.class);
        verify(publisher).publishEvent(evt.capture());
        assertThat(evt.getValue().status()).isEqualTo(PendingApprovalStatus.REQUESTED);
        assertThat(evt.getValue().actorId()).isEqualTo(requester);
    }

    @Test
    void submit_throws_whenTtlLessThan1() {
        assertThatThrownBy(() -> service.submit("role_change", "{}", requester, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ttlDays");
    }

    @Test
    void submit_throws_whenActionTypeBlank() {
        assertThatThrownBy(() -> service.submit("  ", "{}", requester, 7))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ──────────────────────────────────────────────────────────────────
    // approve
    // ──────────────────────────────────────────────────────────────────

    @Test
    void approve_happyPath_invokesHandlerAndTransitionsApproved() {
        PendingAdminApproval row = newRequestedRow("role_change", "{\"userId\":\"" + UUID.randomUUID() + "\"}");
        when(repository.lockById(approvalId)).thenReturn(Optional.of(row));

        PendingAdminApproval result = service.approve(approvalId, secondary, "ok");

        verify(roleHandler).execute(row.getPayloadJson(), secondary);
        assertThat(result.getStatus()).isEqualTo(PendingApprovalStatus.APPROVED);
        assertThat(result.getSecondaryApproverId()).isEqualTo(secondary);
        assertThat(result.getDecisionReason()).isEqualTo("ok");

        ArgumentCaptor<AdminApprovalDecidedEvent> evt =
            ArgumentCaptor.forClass(AdminApprovalDecidedEvent.class);
        verify(publisher).publishEvent(evt.capture());
        assertThat(evt.getValue().status()).isEqualTo(PendingApprovalStatus.APPROVED);
        assertThat(evt.getValue().actorId()).isEqualTo(secondary);
        assertThat(evt.getValue().primaryApproverId()).isEqualTo(requester);
    }

    @Test
    void approve_throwsNotFound_whenIdMissing() {
        when(repository.lockById(approvalId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.approve(approvalId, secondary, "ok"))
            .isInstanceOf(PendingApprovalNotFoundException.class);
        verify(roleHandler, never()).execute(any(), any());
    }

    @Test
    void approve_throwsAlreadyDecided_whenStatusTerminal() {
        PendingAdminApproval row = newRequestedRow("role_change", "{}");
        row.setStatus(PendingApprovalStatus.APPROVED);
        when(repository.lockById(approvalId)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.approve(approvalId, secondary, "ok"))
            .isInstanceOf(AlreadyDecidedException.class)
            .satisfies(ex -> assertThat(((AlreadyDecidedException) ex).getCurrentStatus())
                .isEqualTo(PendingApprovalStatus.APPROVED));
        verify(roleHandler, never()).execute(any(), any());
    }

    @Test
    void approve_throwsSelf_whenSecondaryEqualsRequester() {
        PendingAdminApproval row = newRequestedRow("role_change", "{}");
        when(repository.lockById(approvalId)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.approve(approvalId, requester, "ok"))
            .isInstanceOf(SelfApprovalException.class);
        verify(roleHandler, never()).execute(any(), any());
    }

    @Test
    void approve_throwsSelf_whenRoleChangePayloadContainsSecondaryId() {
        // secondary가 target user인 role_change 자기 승인 차단.
        String payload = "{\"userId\":\"" + secondary + "\",\"toRole\":\"ADMIN\"}";
        PendingAdminApproval row = newRequestedRow("role_change", payload);
        when(repository.lockById(approvalId)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.approve(approvalId, secondary, "ok"))
            .isInstanceOf(SelfApprovalException.class);
        verify(roleHandler, never()).execute(any(), any());
    }

    @Test
    void approve_throwsUnknownHandler_whenActionTypeMissing() {
        PendingAdminApproval row = newRequestedRow("trash_purge", "{}");
        when(repository.lockById(approvalId)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.approve(approvalId, secondary, "ok"))
            .isInstanceOf(UnknownApprovalActionException.class)
            .hasMessageContaining("trash_purge");
    }

    @Test
    void approve_rollsBack_whenHandlerThrows() {
        PendingAdminApproval row = newRequestedRow("role_change", "{\"userId\":\"u\"}");
        when(repository.lockById(approvalId)).thenReturn(Optional.of(row));
        org.mockito.Mockito.doThrow(new RuntimeException("downstream fail"))
            .when(roleHandler).execute(any(), any());

        assertThatThrownBy(() -> service.approve(approvalId, secondary, "ok"))
            .isInstanceOf(RuntimeException.class);

        // status는 service 안에서 set되지 않음 — outer transaction rollback 책임 (Spring).
        verify(repository, never()).save(row);
        verify(publisher, never()).publishEvent(any(AdminApprovalDecidedEvent.class));
    }

    // ──────────────────────────────────────────────────────────────────
    // reject
    // ──────────────────────────────────────────────────────────────────

    @Test
    void reject_transitionsRejectedWithoutHandlerCall() {
        PendingAdminApproval row = newRequestedRow("trash_purge", "{}");
        when(repository.lockById(approvalId)).thenReturn(Optional.of(row));

        PendingAdminApproval result = service.reject(approvalId, secondary, "거부 사유");

        assertThat(result.getStatus()).isEqualTo(PendingApprovalStatus.REJECTED);
        assertThat(result.getSecondaryApproverId()).isEqualTo(secondary);
        verify(roleHandler, never()).execute(any(), any());

        ArgumentCaptor<AdminApprovalDecidedEvent> evt =
            ArgumentCaptor.forClass(AdminApprovalDecidedEvent.class);
        verify(publisher).publishEvent(evt.capture());
        assertThat(evt.getValue().status()).isEqualTo(PendingApprovalStatus.REJECTED);
    }

    @Test
    void reject_throwsSelf_whenSecondaryEqualsRequester() {
        PendingAdminApproval row = newRequestedRow("role_change", "{}");
        when(repository.lockById(approvalId)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.reject(approvalId, requester, "거부"))
            .isInstanceOf(SelfApprovalException.class);
    }

    // ──────────────────────────────────────────────────────────────────
    // cancel
    // ──────────────────────────────────────────────────────────────────

    @Test
    void cancel_byRequester_transitionsCancelled_noAuditEvent() {
        PendingAdminApproval row = newRequestedRow("role_change", "{}");
        when(repository.lockById(approvalId)).thenReturn(Optional.of(row));

        PendingAdminApproval result = service.cancel(approvalId, requester);

        assertThat(result.getStatus()).isEqualTo(PendingApprovalStatus.CANCELLED);
        // CANCELLED는 audit emit 없음 (ADR #47 KISS).
        verify(publisher, never()).publishEvent(any(AdminApprovalDecidedEvent.class));
    }

    @Test
    void cancel_byOtherUser_throwsNotFound() {
        // 자기 row 아님 — 존재 자체 미노출 (404로 위장).
        PendingAdminApproval row = newRequestedRow("role_change", "{}");
        when(repository.lockById(approvalId)).thenReturn(Optional.of(row));

        UUID stranger = UUID.randomUUID();
        assertThatThrownBy(() -> service.cancel(approvalId, stranger))
            .isInstanceOf(PendingApprovalNotFoundException.class);
    }

    // ──────────────────────────────────────────────────────────────────
    // expire
    // ──────────────────────────────────────────────────────────────────

    @Test
    void expire_transitionsExpired_actorNullSystem() {
        PendingAdminApproval row = newRequestedRow("retention_change", "{}");
        when(repository.lockById(approvalId)).thenReturn(Optional.of(row));

        PendingAdminApproval result = service.expire(approvalId);

        assertThat(result.getStatus()).isEqualTo(PendingApprovalStatus.EXPIRED);
        assertThat(result.getSecondaryApproverId()).isNull();

        ArgumentCaptor<AdminApprovalDecidedEvent> evt =
            ArgumentCaptor.forClass(AdminApprovalDecidedEvent.class);
        verify(publisher).publishEvent(evt.capture());
        assertThat(evt.getValue().status()).isEqualTo(PendingApprovalStatus.EXPIRED);
        assertThat(evt.getValue().actorId()).isNull(); // system
    }

    @Test
    void expire_throwsAlreadyDecided_whenStatusTerminal() {
        // race: cron이 이미 결정된 row를 잡았을 때 silent skip 의미 — service는 throw, 호출자가 catch.
        PendingAdminApproval row = newRequestedRow("role_change", "{}");
        row.setStatus(PendingApprovalStatus.APPROVED);
        when(repository.lockById(approvalId)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.expire(approvalId))
            .isInstanceOf(AlreadyDecidedException.class);
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────

    private PendingAdminApproval newRequestedRow(String actionType, String payloadJson) {
        PendingAdminApproval row = new PendingAdminApproval();
        row.setId(approvalId);
        row.setActionType(actionType);
        row.setPayloadJson(payloadJson);
        row.setRequestedBy(requester);
        row.setRequestedAt(OffsetDateTime.now(ZoneOffset.UTC));
        row.setStatus(PendingApprovalStatus.REQUESTED);
        row.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
        return row;
    }
}
