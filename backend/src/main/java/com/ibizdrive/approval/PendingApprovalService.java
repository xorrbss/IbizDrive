package com.ibizdrive.approval;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * dual-approval framework — state machine + transition service (ADR #47, docs/02 §2.11).
 *
 * <p>Phase 2 본 트랙: 5 transition (submit / approve / reject / cancel / expire) + audit event
 * publish + action handler dispatch. controller / per-action hook / admin UI / cron은 별도 트랙.
 *
 * <p>모든 transition은 {@link PendingAdminApprovalRepository#lockById}로 pessimistic write lock
 * 획득 후 status 검사로 분기 (CLAUDE.md §3 원칙 7). 동시 결정 race를 차단하고 terminal row에 대한
 * 재결정 시도는 {@link AlreadyDecidedException} 409로 일관 처리.
 *
 * <p>{@link AdminApprovalDecidedEvent}는 AFTER_COMMIT 보장이 필요해
 * {@link com.ibizdrive.audit.AdminApprovalAuditListener}가 트랜잭션 phase 분기로 audit_log row 변환.
 */
@Service
@Transactional
public class PendingApprovalService {

    /** action_type='role_change' payload의 target user id 필드명 — self-approval 추가 가드 */
    private static final String PAYLOAD_USER_ID_FIELD_ROLE_CHANGE = "userId";

    private final PendingAdminApprovalRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, AdminApprovalActionHandler> handlers;

    public PendingApprovalService(PendingAdminApprovalRepository repository,
                                  ApplicationEventPublisher eventPublisher,
                                  List<AdminApprovalActionHandler> handlerList) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.handlers = handlerList.stream()
            .collect(Collectors.toMap(AdminApprovalActionHandler::actionType, h -> h));
    }

    /**
     * 신규 approval 요청 — controller가 게이트 활성 분기에서 호출.
     *
     * <p>invariant: {@code requestedBy} 인증된 ADMIN actor, {@code actionType} non-null,
     * {@code payloadJson} JSON 직렬화 본문(application 검증 책임), {@code ttlDays >= 1}.
     *
     * @return 저장된 approval row (status=REQUESTED, expires_at=requested_at + ttlDays).
     */
    public PendingAdminApproval submit(String actionType, String payloadJson, UUID requestedBy, int ttlDays) {
        if (actionType == null || actionType.isBlank())
            throw new IllegalArgumentException("actionType is required");
        if (payloadJson == null) throw new IllegalArgumentException("payloadJson is required");
        if (requestedBy == null) throw new IllegalArgumentException("requestedBy is required");
        if (ttlDays < 1) throw new IllegalArgumentException("ttlDays must be >= 1, got: " + ttlDays);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        PendingAdminApproval row = new PendingAdminApproval();
        row.setId(UUID.randomUUID());
        row.setActionType(actionType);
        row.setPayloadJson(payloadJson);
        row.setRequestedBy(requestedBy);
        row.setRequestedAt(now);
        row.setStatus(PendingApprovalStatus.REQUESTED);
        row.setExpiresAt(now.plusDays(ttlDays));
        PendingAdminApproval saved = repository.save(row);

        eventPublisher.publishEvent(new AdminApprovalDecidedEvent(
            saved.getId(), saved.getActionType(), PendingApprovalStatus.REQUESTED,
            requestedBy, /*primary*/ requestedBy, /*secondary*/ null,
            saved.getPayloadJson(), /*decisionReason*/ null));

        return saved;
    }

    /**
     * secondary 승인 — REQUESTED → APPROVED. 트랜잭션 내 action 실행. action 실패 시 rollback →
     * status=REQUESTED 복귀.
     *
     * <p>Self-approval 차단 (ADR #47):
     * <ul>
     *   <li>모든 action_type: secondary ≠ requested_by</li>
     *   <li>action_type='role_change': secondary ≠ payload.userId (target 사용자가 자기 승인 차단)</li>
     * </ul>
     *
     * @throws PendingApprovalNotFoundException approval id 미존재
     * @throws AlreadyDecidedException terminal status
     * @throws SelfApprovalException secondary가 self
     * @throws UnknownApprovalActionException action_type에 매칭되는 handler 부재
     */
    public PendingAdminApproval approve(UUID approvalId, UUID secondaryId, String decisionReason) {
        if (approvalId == null) throw new IllegalArgumentException("approvalId is required");
        if (secondaryId == null) throw new IllegalArgumentException("secondaryId is required");

        PendingAdminApproval row = repository.lockById(approvalId)
            .orElseThrow(() -> new PendingApprovalNotFoundException("approval not found: " + approvalId));

        if (row.getStatus() != PendingApprovalStatus.REQUESTED) {
            throw new AlreadyDecidedException(
                "approval already decided: " + approvalId + " status=" + row.getStatus(),
                row.getStatus());
        }
        if (secondaryId.equals(row.getRequestedBy())) {
            throw new SelfApprovalException("secondary cannot equal requested_by: " + secondaryId);
        }
        if ("role_change".equals(row.getActionType())) {
            // payload.userId가 secondary와 같으면 차단 — Phase 2는 raw JSON에서 가벼운 substring 검사로
            // 의존성을 ObjectMapper에 두지 않는다. handler가 정확한 deserialize 책임을 가지므로 본 가드는
            // defensive only. 정확한 검증은 handler가 결정자 메타에서 다시 확인 가능.
            String secondaryWire = secondaryId.toString();
            if (row.getPayloadJson() != null && row.getPayloadJson().contains(secondaryWire)) {
                throw new SelfApprovalException(
                    "secondary cannot equal payload.userId for role_change: " + secondaryWire);
            }
        }

        AdminApprovalActionHandler handler = handlers.get(row.getActionType());
        if (handler == null) {
            throw new UnknownApprovalActionException(row.getActionType());
        }

        // outer transaction 내 action 실행 — 실패 시 rollback로 status 복귀.
        handler.execute(row.getPayloadJson(), secondaryId);

        row.setStatus(PendingApprovalStatus.APPROVED);
        row.setSecondaryApproverId(secondaryId);
        row.setDecidedAt(OffsetDateTime.now(ZoneOffset.UTC));
        row.setDecisionReason(decisionReason);
        PendingAdminApproval saved = repository.save(row);

        eventPublisher.publishEvent(new AdminApprovalDecidedEvent(
            saved.getId(), saved.getActionType(), PendingApprovalStatus.APPROVED,
            secondaryId, saved.getRequestedBy(), secondaryId,
            saved.getPayloadJson(), decisionReason));

        return saved;
    }

    /**
     * secondary 거부 — REQUESTED → REJECTED. action 미실행.
     */
    public PendingAdminApproval reject(UUID approvalId, UUID secondaryId, String decisionReason) {
        if (approvalId == null) throw new IllegalArgumentException("approvalId is required");
        if (secondaryId == null) throw new IllegalArgumentException("secondaryId is required");

        PendingAdminApproval row = repository.lockById(approvalId)
            .orElseThrow(() -> new PendingApprovalNotFoundException("approval not found: " + approvalId));

        if (row.getStatus() != PendingApprovalStatus.REQUESTED) {
            throw new AlreadyDecidedException(
                "approval already decided: " + approvalId + " status=" + row.getStatus(),
                row.getStatus());
        }
        if (secondaryId.equals(row.getRequestedBy())) {
            throw new SelfApprovalException("secondary cannot equal requested_by: " + secondaryId);
        }

        row.setStatus(PendingApprovalStatus.REJECTED);
        row.setSecondaryApproverId(secondaryId);
        row.setDecidedAt(OffsetDateTime.now(ZoneOffset.UTC));
        row.setDecisionReason(decisionReason);
        PendingAdminApproval saved = repository.save(row);

        eventPublisher.publishEvent(new AdminApprovalDecidedEvent(
            saved.getId(), saved.getActionType(), PendingApprovalStatus.REJECTED,
            secondaryId, saved.getRequestedBy(), secondaryId,
            saved.getPayloadJson(), decisionReason));

        return saved;
    }

    /**
     * requested_by 본인 취소 — REQUESTED → CANCELLED. action 미실행. audit emit 없음 (ADR #47 KISS).
     */
    public PendingAdminApproval cancel(UUID approvalId, UUID requesterId) {
        if (approvalId == null) throw new IllegalArgumentException("approvalId is required");
        if (requesterId == null) throw new IllegalArgumentException("requesterId is required");

        PendingAdminApproval row = repository.lockById(approvalId)
            .orElseThrow(() -> new PendingApprovalNotFoundException("approval not found: " + approvalId));

        if (row.getStatus() != PendingApprovalStatus.REQUESTED) {
            throw new AlreadyDecidedException(
                "approval already decided: " + approvalId + " status=" + row.getStatus(),
                row.getStatus());
        }
        if (!requesterId.equals(row.getRequestedBy())) {
            // 다른 사용자의 cancel 시도는 404로 위장 — 자기 row가 아닌 row의 존재 자체를 노출하지 않음.
            throw new PendingApprovalNotFoundException("approval not found: " + approvalId);
        }

        row.setStatus(PendingApprovalStatus.CANCELLED);
        row.setDecidedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return repository.save(row);
    }

    /**
     * expiration cron — REQUESTED → EXPIRED. action 미실행. actor_id=NULL (system trigger).
     * 호출자 (cron)가 후보 row id를 받아 batch loop으로 호출.
     */
    public PendingAdminApproval expire(UUID approvalId) {
        if (approvalId == null) throw new IllegalArgumentException("approvalId is required");

        PendingAdminApproval row = repository.lockById(approvalId)
            .orElseThrow(() -> new PendingApprovalNotFoundException("approval not found: " + approvalId));

        if (row.getStatus() != PendingApprovalStatus.REQUESTED) {
            // 이미 결정된 row는 expire 대상 아님 — cron이 race로 동일 row를 잡을 수 있어 silent skip.
            throw new AlreadyDecidedException(
                "approval already decided: " + approvalId + " status=" + row.getStatus(),
                row.getStatus());
        }

        row.setStatus(PendingApprovalStatus.EXPIRED);
        row.setDecidedAt(OffsetDateTime.now(ZoneOffset.UTC));
        PendingAdminApproval saved = repository.save(row);

        eventPublisher.publishEvent(new AdminApprovalDecidedEvent(
            saved.getId(), saved.getActionType(), PendingApprovalStatus.EXPIRED,
            /*actor=NULL system*/ null, saved.getRequestedBy(), /*secondary*/ null,
            saved.getPayloadJson(), /*decisionReason*/ null));

        return saved;
    }

    // ──────────────────────────────────────────────────────────────────
    // read — service-level facade for controller / cron
    // ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PendingAdminApproval> listPending(String actionType, Pageable pageable) {
        return repository.findPendingByActionType(actionType, pageable);
    }

    /**
     * 단건 조회 — controller GET /:id 진입점. lock 없이 read-only.
     *
     * @throws PendingApprovalNotFoundException id 미존재
     */
    @Transactional(readOnly = true)
    public PendingAdminApproval getById(UUID approvalId) {
        if (approvalId == null) throw new IllegalArgumentException("approvalId is required");
        return repository.findById(approvalId)
            .orElseThrow(() -> new PendingApprovalNotFoundException("approval not found: " + approvalId));
    }

    @Transactional(readOnly = true)
    public List<PendingAdminApproval> listMyPending(UUID requesterId) {
        if (requesterId == null) throw new IllegalArgumentException("requesterId is required");
        return repository.findPendingByRequester(requesterId);
    }

    @Transactional(readOnly = true)
    public List<PendingAdminApproval> findExpired(int cap) {
        if (cap < 1) throw new IllegalArgumentException("cap must be >= 1, got: " + cap);
        return repository.findExpiredPending(
            OffsetDateTime.now(ZoneOffset.UTC),
            org.springframework.data.domain.PageRequest.of(0, cap));
    }
}
