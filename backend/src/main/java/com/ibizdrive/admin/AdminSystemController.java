package com.ibizdrive.admin;

import com.ibizdrive.approval.PendingAdminApprovalExpirationProperties;
import com.ibizdrive.audit.WebRequestContextHolder;
import com.ibizdrive.permission.PermissionExpirationProperties;
import com.ibizdrive.purge.HardPurgeProperties;
import com.ibizdrive.share.ShareExpirationProperties;
import com.ibizdrive.storage.StorageOrphanCleanupProperties;
import com.ibizdrive.user.IbizDriveUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Wave 1 — T3 — `/admin/system` 페이지가 호출하는 cron 설정 노출 endpoint.
 *
 * <p>4 cron 잡(`purge.expired`, `share.expire`, `permission.expire`, `storage.orphan.cleanup`)의
 * schedule/zone/batch 등 정의는 application.yml에서 read하고, enabled 토글 상태는
 * {@link CronPolicyRepository}(admin-cron-toggle 트랙, V11 {@code cron_policy} 테이블)에서 read.
 * 두 source가 합쳐져 viewer에 노출되며, 토글 직후 GET이 즉시 새 enabled를 반영한다.
 *
 * <p>Wave 1.5(`auditor-cron-readonly`)에서 read는 ADMIN+AUDITOR 모두 허용 — 감사자는 운영 cron 상태를
 * 확인할 책임이 있다(docs/04 §7.x). mutation({@link #toggleCron})은 ADMIN-only.
 *
 * <p>admin-cron-policy-toggle (Wave 2 closure 후속, 2026-05-08): {@link #toggleCron} ADMIN-only
 * mutation 추가 — {@code cron_policy} 테이블에 enabled 값을 영구 저장, AFTER_COMMIT에 audit row.
 *
 * <p>yml-enabled-cleanup (admin-cron-toggle 직접 후속, 2026-05-09): 4 {@code *Properties}의 dead
 * {@code enabled} 필드 제거 + viewer 응답이 yml 대신 DB({@code cron_policy})를 참조하도록 전환.
 */
@RestController
@RequestMapping("/api/admin/system")
public class AdminSystemController {

    private final HardPurgeProperties purge;
    private final ShareExpirationProperties shareExpiration;
    private final PermissionExpirationProperties permissionExpiration;
    private final StorageOrphanCleanupProperties storageOrphanCleanup;
    private final PendingAdminApprovalExpirationProperties adminApprovalExpiration;
    private final AdminSystemService adminSystemService;
    private final CronPolicyRepository cronPolicyRepository;

    public AdminSystemController(
        HardPurgeProperties purge,
        ShareExpirationProperties shareExpiration,
        PermissionExpirationProperties permissionExpiration,
        StorageOrphanCleanupProperties storageOrphanCleanup,
        PendingAdminApprovalExpirationProperties adminApprovalExpiration,
        AdminSystemService adminSystemService,
        CronPolicyRepository cronPolicyRepository
    ) {
        this.purge = purge;
        this.shareExpiration = shareExpiration;
        this.permissionExpiration = permissionExpiration;
        this.storageOrphanCleanup = storageOrphanCleanup;
        this.adminApprovalExpiration = adminApprovalExpiration;
        this.adminSystemService = adminSystemService;
        this.cronPolicyRepository = cronPolicyRepository;
    }

    /**
     * 4 cron 잡 상태 스냅샷을 고정 순서(purge → share → permission → storage)로 반환.
     *
     * <p>응답 셰입은 {@code {"jobs": [CronJobStatusResponse, ...]}} — 향후 잡 추가 시
     * 배열 추가만으로 확장 가능. enabled 값은 {@code cron_policy}에서 read하여 토글 직후
     * 즉시 반영된다.
     */
    @GetMapping("/cron")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")
    public Map<String, List<CronJobStatusResponse>> getCronStatus() {
        List<CronJobStatusResponse> jobs = List.of(
            new CronJobStatusResponse(
                "purge.expired", "휴지통 hard purge",
                cronPolicyRepository.isEnabled("purge.expired"), purge.cron(), purge.zone(),
                /* batchSize */ null, purge.maxPerRun(), /* graceHours */ null
            ),
            new CronJobStatusResponse(
                "share.expire", "공유 만료 처리",
                cronPolicyRepository.isEnabled("share.expire"), shareExpiration.cron(), shareExpiration.zone(),
                shareExpiration.batchSize(), /* maxPerRun */ null, /* graceHours */ null
            ),
            new CronJobStatusResponse(
                "permission.expire", "권한 만료 처리",
                cronPolicyRepository.isEnabled("permission.expire"), permissionExpiration.cron(), permissionExpiration.zone(),
                permissionExpiration.batchSize(), /* maxPerRun */ null, /* graceHours */ null
            ),
            new CronJobStatusResponse(
                "storage.orphan.cleanup", "스토리지 고아 정리",
                cronPolicyRepository.isEnabled("storage.orphan.cleanup"), storageOrphanCleanup.cron(), storageOrphanCleanup.zone(),
                /* batchSize */ null, storageOrphanCleanup.maxPerRun(), storageOrphanCleanup.graceHours()
            ),
            new CronJobStatusResponse(
                "admin.approval.expire", "2인 승인 만료 처리",
                cronPolicyRepository.isEnabled("admin.approval.expire"),
                adminApprovalExpiration.cron(), adminApprovalExpiration.zone(),
                adminApprovalExpiration.batchSize(), /* maxPerRun */ null, /* graceHours */ null
            )
        );
        return Map.of("jobs", jobs);
    }

    /**
     * Cron 정책 토글 — admin-cron-policy-toggle 트랙. {@code enabled}만 변경, schedule/zone은 yml 그대로.
     *
     * <p>토글 결과는 {@code cron_policy} 테이블에 영구 저장. {@link AdminCronToggledListener}가
     * AFTER_COMMIT에 audit_log {@code admin.cron.toggled} row를 기록한다.
     *
     * <p>경로 변수 {@code key}는 4종 식별자 중 하나 — 그 외 값은 service에서
     * {@link IllegalArgumentException} → 글로벌 핸들러 400 {@code BAD_REQUEST}.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/cron/{key}")
    public ResponseEntity<Void> toggleCron(
        @PathVariable("key") String key,
        @Valid @RequestBody AdminCronToggleRequest body,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        adminSystemService.toggleCron(
            key,
            body.enabled(),
            principal.getUser().getId(),
            WebRequestContextHolder.currentIp(),
            WebRequestContextHolder.currentUserAgent()
        );
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
