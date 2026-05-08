package com.ibizdrive.admin;

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
 * Wave 1 — T3 — `/admin/system` 페이지가 호출하는 read-only cron 설정 노출 endpoint.
 *
 * <p>운영자가 application.yml(또는 application-prod.yml)에 설정된 4 cron 잡(`purge.expired`,
 * `share.expire`, `permission.expire`, `storage.orphan.cleanup`)의 현재 활성화/스케줄/zone/배치
 * 파라미터를 admin UI에서 확인할 수 있도록 노출. 변경(toggle/edit)은 v1.x deferred — application.yml
 * 직접 수정 + 재기동이 유일한 설정 변경 경로 (config server 부재).
 *
 * <p>read-only이므로 audit emit 0 (SELECT-only). Wave 1.5(`auditor-cron-readonly`)에서
 * AUDITOR에게도 read 허용 — 감사자는 운영 cron 설정을 확인할 책임이 있다(docs/04 §7.x).
 * mutation은 정의되지 않았고(설정 변경 = application.yml + 재기동), 추후 mutation endpoint
 * 추가 시 AUDITOR는 그 endpoint에서는 차단.
 *
 * <p>admin-cron-policy-toggle (Wave 2 closure 후속): {@link #toggleCron} ADMIN-only mutation
 * 추가 — {@code cron_policy} 테이블에 enabled 값을 영구 저장, AFTER_COMMIT에 audit row.
 */
@RestController
@RequestMapping("/api/admin/system")
public class AdminSystemController {

    private final HardPurgeProperties purge;
    private final ShareExpirationProperties shareExpiration;
    private final PermissionExpirationProperties permissionExpiration;
    private final StorageOrphanCleanupProperties storageOrphanCleanup;
    private final AdminSystemService adminSystemService;

    public AdminSystemController(
        HardPurgeProperties purge,
        ShareExpirationProperties shareExpiration,
        PermissionExpirationProperties permissionExpiration,
        StorageOrphanCleanupProperties storageOrphanCleanup,
        AdminSystemService adminSystemService
    ) {
        this.purge = purge;
        this.shareExpiration = shareExpiration;
        this.permissionExpiration = permissionExpiration;
        this.storageOrphanCleanup = storageOrphanCleanup;
        this.adminSystemService = adminSystemService;
    }

    /**
     * 4 cron 잡 설정 스냅샷을 고정 순서(purge → share → permission → storage)로 반환.
     *
     * <p>응답 셰입은 {@code {"jobs": [CronJobStatusResponse, ...]}} — 향후 잡 추가 시
     * 배열 추가만으로 확장 가능.
     */
    @GetMapping("/cron")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")
    public Map<String, List<CronJobStatusResponse>> getCronStatus() {
        List<CronJobStatusResponse> jobs = List.of(
            new CronJobStatusResponse(
                "purge.expired", "휴지통 hard purge",
                purge.enabled(), purge.cron(), purge.zone(),
                /* batchSize */ null, purge.maxPerRun(), /* graceHours */ null
            ),
            new CronJobStatusResponse(
                "share.expire", "공유 만료 처리",
                shareExpiration.enabled(), shareExpiration.cron(), shareExpiration.zone(),
                shareExpiration.batchSize(), /* maxPerRun */ null, /* graceHours */ null
            ),
            new CronJobStatusResponse(
                "permission.expire", "권한 만료 처리",
                permissionExpiration.enabled(), permissionExpiration.cron(), permissionExpiration.zone(),
                permissionExpiration.batchSize(), /* maxPerRun */ null, /* graceHours */ null
            ),
            new CronJobStatusResponse(
                "storage.orphan.cleanup", "스토리지 고아 정리",
                storageOrphanCleanup.enabled(), storageOrphanCleanup.cron(), storageOrphanCleanup.zone(),
                /* batchSize */ null, storageOrphanCleanup.maxPerRun(), storageOrphanCleanup.graceHours()
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
