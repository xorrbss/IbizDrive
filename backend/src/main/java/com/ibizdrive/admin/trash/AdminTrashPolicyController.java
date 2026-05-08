package com.ibizdrive.admin.trash;

import com.ibizdrive.trash.TrashRetentionProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * `/admin/trash/policy` — 휴지통 보존 정책 read-only viewer (Wave 2 T9 follow-up,
 * wave2-trash-retention-config (#108) closure 후속).
 *
 * <p>운영자가 현재 보존 일수를 yml 직접 열지 않고도 확인할 수 있게 해준다. mutation은
 * `@ConfigurationProperties` 부팅 바인딩이라 v1.x deferred — 변경은 yml 수정 + 재기동.
 *
 * <p>cron 상태(enabled/cron/zone)는 별도 endpoint(`/api/admin/system/cron`, Wave 1 T3)가
 * 진실의 출처. admin-cron-toggle 트랙(#102)이 그쪽을 DB-backed runtime mutation으로 발전시키므로,
 * 본 endpoint는 yml 정적 값(retention)에만 책임을 둔다.
 */
@RestController
@RequestMapping("/api/admin/trash/policy")
public class AdminTrashPolicyController {

    private final TrashRetentionProperties retention;

    public AdminTrashPolicyController(TrashRetentionProperties retention) {
        this.retention = retention;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminTrashPolicyDto> get() {
        return ResponseEntity.ok(new AdminTrashPolicyDto(retention.days()));
    }
}
