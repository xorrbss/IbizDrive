package com.ibizdrive.approval;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * dual-approval framework data layer — ADR #47 / docs/02 §2.11.
 *
 * <p>Phase 1 contract — Phase 2+ service가 사용:
 * <ul>
 *   <li>{@link #lockById} — transition 진입 시점 행 잠금 (REQUESTED → APPROVED/REJECTED/CANCELLED race 차단)</li>
 *   <li>{@link #findPendingByActionType} — admin 알림 페이지: action_type별 pending 목록</li>
 *   <li>{@link #findPendingByRequester} — 요청자 본인 이력 (취소 가능 항목 조회)</li>
 *   <li>{@link #findExpiredPending} — expiration cron 후보 스캔</li>
 * </ul>
 *
 * <p>모든 finder는 partial index({@code idx_pending_approvals_*})와 정합된 WHERE 조건을 갖는다 —
 * V20 migration 참조.
 */
public interface PendingAdminApprovalRepository extends JpaRepository<PendingAdminApproval, UUID> {

    /**
     * Pessimistic write lock — Phase 2 service의 approve/reject/cancel/expire transition 진입에서 사용
     * (CLAUDE.md §3 원칙 7). 결과 부재 시 service가 {@code APPROVAL_NOT_FOUND} 404로 변환.
     *
     * <p>본 lock은 status 제약 없이 모든 row를 잠근다 — terminal에 도달한 row에 대한 재결정 시도도
     * 트랜잭션 안에서 status 검사로 분기 ({@code APPROVAL_ALREADY_DECIDED} 409).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PendingAdminApproval a WHERE a.id = :id")
    Optional<PendingAdminApproval> lockById(@Param("id") UUID id);

    /**
     * pending 목록 (status='REQUESTED'). {@code action_type=null}이면 전체 action_type 합산.
     * 정렬: requested_at DESC (최근 요청부터). Page 컨트롤은 호출자({@link Pageable}).
     *
     * <p>V20 {@code idx_pending_approvals_requested(action_type, status) WHERE status='REQUESTED'}
     * partial index가 hit (action_type 필터 시) 또는 prefix scan (전체 시).
     */
    @Query("""
        SELECT a FROM PendingAdminApproval a
        WHERE a.status = com.ibizdrive.approval.PendingApprovalStatus.REQUESTED
          AND (:actionType IS NULL OR a.actionType = :actionType)
        ORDER BY a.requestedAt DESC, a.id ASC
        """)
    Page<PendingAdminApproval> findPendingByActionType(@Param("actionType") String actionType,
                                                      Pageable pageable);

    /**
     * 요청자 본인의 pending 이력 — 취소 가능 항목 조회용. status='REQUESTED'만.
     *
     * <p>V20 {@code idx_pending_approvals_by_requester(requested_by, requested_at DESC)} 매칭.
     */
    @Query("""
        SELECT a FROM PendingAdminApproval a
        WHERE a.requestedBy = :requesterId
          AND a.status = com.ibizdrive.approval.PendingApprovalStatus.REQUESTED
        ORDER BY a.requestedAt DESC, a.id ASC
        """)
    List<PendingAdminApproval> findPendingByRequester(@Param("requesterId") UUID requesterId);

    /**
     * expiration cron 후보 — {@code expires_at <= :now AND status='REQUESTED'}.
     * 호출자(cron service)가 결과를 batch로 받아 EXPIRED transition.
     *
     * <p>V20 {@code idx_pending_approvals_expires(expires_at) WHERE status='REQUESTED'} partial
     * index 매칭. 결과 cap은 cron이 {@link Pageable}로 강제 (default 1000건/run 가정).
     */
    @Query("""
        SELECT a FROM PendingAdminApproval a
        WHERE a.status = com.ibizdrive.approval.PendingApprovalStatus.REQUESTED
          AND a.expiresAt <= :now
        ORDER BY a.expiresAt ASC, a.id ASC
        """)
    List<PendingAdminApproval> findExpiredPending(@Param("now") OffsetDateTime now, Pageable pageable);
}
