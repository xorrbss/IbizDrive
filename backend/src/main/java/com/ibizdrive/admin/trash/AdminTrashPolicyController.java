package com.ibizdrive.admin.trash;

import com.ibizdrive.trash.TrashPolicyService;
import com.ibizdrive.user.IbizDriveUserDetails;
import jakarta.validation.Valid;
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

    private final TrashPolicyService policyService;

    public AdminTrashPolicyController(TrashPolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminTrashPolicyDto> get() {
        return ResponseEntity.ok(new AdminTrashPolicyDto(policyService.getRetentionDays()));
    }

    /**
     * 보존 일수 변경 (단일-approver MVP).
     *
     * <p>요청 검증: Bean Validation으로 {@code days} 7..90 + {@code @NotNull}. 추가로 service가
     * 동일 범위 재검증 + actor null 가드 — 위반 시 {@link IllegalArgumentException} →
     * {@code GlobalExceptionHandler}가 400 VALIDATION_ERROR로 매핑.
     *
     * <p>응답: {@code 200 { retentionDays: <적용값> }}. 동일값 입력은 service가 no-op 처리(audit
     * 미발생)이며 응답은 동일.
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminTrashPolicyDto> update(
        @RequestBody @Valid AdminTrashPolicyUpdateRequest body,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        int applied = policyService.updateRetentionDays(body.days(), principal.getUser().getId());
        return ResponseEntity.ok(new AdminTrashPolicyDto(applied));
    }
}
