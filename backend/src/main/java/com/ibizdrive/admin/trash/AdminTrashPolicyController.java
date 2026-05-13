package com.ibizdrive.admin.trash;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.approval.ApprovalRequiredException;
import com.ibizdrive.approval.PendingAdminApproval;
import com.ibizdrive.approval.PendingApprovalService;
import com.ibizdrive.approval.handlers.RetentionChangePayload;
import com.ibizdrive.approval.handlers.RetentionChangeApprovalHandler;
import com.ibizdrive.trash.TrashPolicyService;
import com.ibizdrive.user.IbizDriveUserDetails;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * `/admin/trash/policy` — 휴지통 보존 정책 viewer + mutation
 * (Wave 2 T9 follow-up wave2-trash-policy-viewer + trash-retention-mutation Phase B).
 *
 * <p>GET: 현재 보존 일수 조회. 본 트랙 Phase B에서 source가 yml({@code TrashRetentionProperties})에서
 * V17 DB 테이블({@code trash_policy})로 이관됨.
 *
 * <p>PUT: 보존 일수 변경 — 단일-approver MVP. 즉시 적용되며 신규 soft-delete만 새 일수가
 * 적용된다 (기존 trash row의 {@code purge_after}는 재계산 안 함). 변경 이력은
 * {@code admin.retention.changed} audit_log row가 보존
 * ({@link com.ibizdrive.audit.TrashPolicyAuditListener}).
 *
 * <p>cron 운영 상태(enabled/cron/zone)는 별도 endpoint({@code /api/admin/system/cron}, Wave 1 T3)
 * 가 진실의 출처라 본 controller는 정책 값에만 책임을 둔다.
 */
@RestController
@RequestMapping("/api/admin/trash/policy")
public class AdminTrashPolicyController {

    /**
     * dual-approval 게이트 TTL — config로 노출하지 않는다 (framework 공통 기본값). approval row의
     * {@code expires_at = NOW() + ttlDays}. ADR #47 default 7일 정합.
     */
    private static final int APPROVAL_TTL_DAYS = 7;

    private final TrashPolicyService policyService;
    private final PendingApprovalService approvalService;
    private final ObjectMapper objectMapper;
    private final boolean dualApprovalEnabled;

    public AdminTrashPolicyController(
        TrashPolicyService policyService,
        PendingApprovalService approvalService,
        ObjectMapper objectMapper,
        @Value("${app.dual-approval.retention-change.enabled:false}") boolean dualApprovalEnabled
    ) {
        this.policyService = policyService;
        this.approvalService = approvalService;
        this.objectMapper = objectMapper;
        this.dualApprovalEnabled = dualApprovalEnabled;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminTrashPolicyDto> get() {
        return ResponseEntity.ok(new AdminTrashPolicyDto(policyService.getRetentionDays()));
    }

    /**
     * 보존 일수 변경. 게이트 분기 — ADR #47 Phase 3.
     *
     * <p>게이트 {@code app.dual-approval.retention-change.enabled=false} (default):
     * 단일-approver MVP — service가 즉시 적용 + audit 발행. 200 응답.
     *
     * <p>게이트 {@code =true} (운영 환경에서 활성화 시):
     * {@link PendingApprovalService#submit}으로 framework에 등록 +
     * {@link ApprovalRequiredException} throw → {@code GlobalExceptionHandler}가 **202 ACCEPTED** +
     * envelope {@code APPROVAL_REQUIRED} (`{approvalId, expiresAt}`) 매핑. secondary admin이
     * {@code /api/admin/approvals/:id/approve} 호출하면
     * {@link RetentionChangeApprovalHandler}가 동일 service 메서드로 위임 — audit 동일 enum.
     *
     * <p>요청 검증: Bean Validation으로 {@code days} 7..90 + {@code @NotNull}. service 단도 동일 가드.
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminTrashPolicyDto> update(
        @RequestBody @Valid AdminTrashPolicyUpdateRequest body,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        if (dualApprovalEnabled) {
            int currentDays = policyService.getRetentionDays();
            String payloadJson = serializePayload(currentDays, body.days(), /*reason*/ null);
            PendingAdminApproval pending = approvalService.submit(
                RetentionChangeApprovalHandler.ACTION_TYPE,
                payloadJson,
                principal.getUser().getId(),
                APPROVAL_TTL_DAYS);
            throw new ApprovalRequiredException(pending.getId(), pending.getExpiresAt());
        }

        int applied = policyService.updateRetentionDays(body.days(), principal.getUser().getId());
        return ResponseEntity.ok(new AdminTrashPolicyDto(applied));
    }

    private String serializePayload(int fromDays, int toDays, String reason) {
        try {
            return objectMapper.writeValueAsString(new RetentionChangePayload(fromDays, toDays, reason));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("retention_change payload serialize failed", e);
        }
    }
}
