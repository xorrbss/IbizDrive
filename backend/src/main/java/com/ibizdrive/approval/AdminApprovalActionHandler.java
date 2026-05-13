package com.ibizdrive.approval;

import java.util.UUID;

/**
 * dual-approval framework — action 실행 strategy (ADR #47, docs/02 §2.11).
 *
 * <p>{@link PendingApprovalService#approve}가 secondary 승인 transition을 처리할 때 본 interface 구현체를
 * {@link #actionType()} 매칭으로 찾아 {@link #execute}를 호출. action 실행은 outer transaction 내에서
 * 수행되어 실패 시 status도 함께 rollback (REQUESTED 복귀).
 *
 * <p>Phase 2 본 트랙은 framework + interface만 도입 — 구체 handler(role_change/trash_purge/
 * retention_change)는 Phase 4+ 별도 PR. 본 PR 시점에는 {@link UnknownApprovalActionException}이
 * 동작 (handler 미배포 빌드에서 approve 시도 시 운영자에게 명시).
 *
 * <p>구현체 등록은 Spring component scan (@Service/@Component) — 호출자가 List&lt;...&gt; 주입으로
 * 자동 수집.
 */
public interface AdminApprovalActionHandler {

    /**
     * 본 handler가 처리하는 action_type — {@code PendingAdminApproval.actionType}과 정확히 일치해야 한다.
     * 예: {@code "role_change"}, {@code "trash_purge"}, {@code "retention_change"}.
     */
    String actionType();

    /**
     * action 실행. outer {@code @Transactional} 안에서 호출됨 — 본 메서드는 별도 트랜잭션 시작하지
     * 말 것 (rollback 일관성 보장).
     *
     * @param payloadJson approval row에 저장된 raw JSONB 문자열. 구현체가 ObjectMapper로
     *                    action-specific DTO에 deserialize.
     * @param actorId     secondary admin id (audit emit 시 사용 가능).
     * @throws RuntimeException 실행 실패 — outer transaction이 rollback → status=REQUESTED 복귀.
     */
    void execute(String payloadJson, UUID actorId);
}
