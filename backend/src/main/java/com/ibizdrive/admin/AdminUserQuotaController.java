package com.ibizdrive.admin;

import com.ibizdrive.user.IbizDriveUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * `/api/admin/users/{userId}/quota` — 사용자 storage quota viewer + mutation
 * (quota mutation Phase 3, `docs/02 §7.4`, `docs/04 §6.1`).
 *
 * <p>GET: 현재 한도/사용량 조회 (admin 화면에서 사용량 ↔ 한도 비교 표시용).
 *
 * <p>PUT: 한도 변경 — 단일-approver MVP. dual-approval은 framework 활성화 시 hook
 * (`docs/04 §16` / ADR #47 `app.dual-approval.user-quota-change.enabled` v1.x 추가 예정).
 * 변경 이력은 {@code admin.quota.changed} audit_log row가 보존
 * ({@link com.ibizdrive.audit.UserQuotaAuditListener}).
 *
 * <p>HTTP status 매트릭스 ({@link com.ibizdrive.common.error.AdminExceptionHandler}):
 * <ul>
 *   <li>200 OK — 조회/변경 성공</li>
 *   <li>400 VALIDATION_ERROR — body 누락 또는 storageQuota 음수</li>
 *   <li>401 — 미인증</li>
 *   <li>403 — ROLE_ADMIN 부재</li>
 *   <li>404 USER_NOT_FOUND — 대상 사용자 부재/soft-deleted</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/users/{userId}/quota")
public class AdminUserQuotaController {

    private final AdminUserQuotaService quotaService;

    public AdminUserQuotaController(AdminUserQuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminUserQuotaDto> get(@PathVariable UUID userId) {
        return ResponseEntity.ok(quotaService.getQuota(userId));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminUserQuotaDto> update(
        @PathVariable UUID userId,
        @RequestBody @Valid AdminUserQuotaUpdateRequest body,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        return ResponseEntity.ok(
            quotaService.updateQuota(userId, body.storageQuota(), principal.getUser().getId()));
    }
}
