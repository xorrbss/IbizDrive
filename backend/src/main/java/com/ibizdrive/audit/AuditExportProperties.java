package com.ibizdrive.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code GET /api/admin/audit/export} 설정 (application.yml {@code app.audit.export.*}).
 *
 * <p>{@link AuditQueryService#exportAll}이 한 번에 직렬화하는 행 hard cap. 메모리·DoS 방어.
 * 진정한 SQL → JSON streaming(메모리 cap 제거)은 v1.x deferred — 그때까지는 본 cap이
 * 운영 안전망이다.
 *
 * <p>cap 조정은 무중단 적용 불가(Spring `@ConfigurationProperties`는 부팅 시 바인딩) — 운영자가
 * yml 수정 후 재기동. {@code PermissionExpirationProperties} 패턴 동형.
 *
 * @param rowCap export 응답에 포함되는 최대 행 수. 0 이하 입력은 default 10000으로 보정.
 */
@ConfigurationProperties(prefix = "app.audit.export")
public record AuditExportProperties(int rowCap) {
    public AuditExportProperties {
        if (rowCap <= 0) rowCap = 10_000;
    }
}
