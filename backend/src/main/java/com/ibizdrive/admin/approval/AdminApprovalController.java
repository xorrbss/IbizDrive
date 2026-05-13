package com.ibizdrive.admin.approval;

import com.ibizdrive.approval.PendingAdminApproval;
import com.ibizdrive.approval.PendingApprovalService;
import com.ibizdrive.user.IbizDriveUserDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * `/api/admin/approvals` — dual-approval framework controller (ADR #47, docs/02 §2.11, Phase 2b).
 *
 * <p>5 endpoint:
 * <ul>
 *   <li>GET `/` — pending 목록 (admin 알림 페이지). {@code actionType} optional 필터, page/size paging.</li>
 *   <li>GET `/:id` — 단건 상세.</li>
 *   <li>POST `/:id/approve` — secondary 승인 → action 실행 + status=APPROVED.</li>
 *   <li>POST `/:id/reject` — secondary 거부 → status=REJECTED.</li>
 *   <li>DELETE `/:id` — requested_by 본인 취소 → status=CANCELLED.</li>
 * </ul>
 *
 * <p>모든 endpoint는 {@code @PreAuthorize("hasRole('ADMIN')")} — Phase 2b는 ROLE_ADMIN 단일 가드.
 * {@code APPROVE_ADMIN_ACTION} permission enum은 향후 세밀 제어용 reserve (Phase 3+에서 ROLE_ADMIN
 * 보유자 중 일부에게만 grant 가능하도록 분리 시 사용).
 *
 * <p>Phase 2 service의 self-approval 차단 / not-found / already-decided는 controller가 throw —
 * {@code GlobalExceptionHandler}가 envelope 매핑 (`APPROVAL_SELF` 403 / `APPROVAL_NOT_FOUND` 404 /
 * `APPROVAL_ALREADY_DECIDED` 409 — docs/02 §8).
 *
 * <p>cancel 경로는 본인 요청 한정 — service가 다른 사용자 시도를 404로 위장 (info leak 차단).
 */
@RestController
@RequestMapping("/api/admin/approvals")
public class AdminApprovalController {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final PendingApprovalService approvalService;

    public AdminApprovalController(PendingApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * pending 목록 — status='REQUESTED' 한정. {@code actionType=null}이면 전체 합산.
     * Page meta는 Spring Page 직렬화 그대로 (다른 admin endpoint와 동형).
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AdminApprovalDto>> listPending(
        @RequestParam(value = "actionType", required = false) String actionType,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "50") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
        Page<PendingAdminApproval> rows = approvalService.listPending(
            actionType == null || actionType.isBlank() ? null : actionType,
            pageable);
        return ResponseEntity.ok(rows.map(AdminApprovalDto::from));
    }

    @GetMapping("/{approvalId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminApprovalDto> get(@PathVariable UUID approvalId) {
        return ResponseEntity.ok(AdminApprovalDto.from(approvalService.getById(approvalId)));
    }

    /**
     * secondary 승인 — action handler 실행 + status=APPROVED + audit emit.
     *
     * <p>handler 미배포 → 500 {@code APPROVAL_HANDLER_MISSING}, self → 403 {@code APPROVAL_SELF},
     * 미존재 → 404, terminal → 409 {@code APPROVAL_ALREADY_DECIDED} (docs/02 §8).
     */
    @PostMapping("/{approvalId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminApprovalDto> approve(
        @PathVariable UUID approvalId,
        @RequestBody(required = false) @Valid AdminApprovalDecisionRequest body,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        String reason = body == null ? null : body.decisionReason();
        return ResponseEntity.ok(AdminApprovalDto.from(
            approvalService.approve(approvalId, principal.getUser().getId(), reason)));
    }

    @PostMapping("/{approvalId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminApprovalDto> reject(
        @PathVariable UUID approvalId,
        @RequestBody(required = false) @Valid AdminApprovalDecisionRequest body,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        String reason = body == null ? null : body.decisionReason();
        return ResponseEntity.ok(AdminApprovalDto.from(
            approvalService.reject(approvalId, principal.getUser().getId(), reason)));
    }

    /**
     * requested_by 본인 취소 — 다른 사용자 시도는 service가 {@code APPROVAL_NOT_FOUND}로 위장.
     */
    @DeleteMapping("/{approvalId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminApprovalDto> cancel(
        @PathVariable UUID approvalId,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        return ResponseEntity.ok(AdminApprovalDto.from(
            approvalService.cancel(approvalId, principal.getUser().getId())));
    }
}
