package com.ibizdrive.approval;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * dual-approval framework email notification 설정 (ADR #47 Phase 4, docs/04 §16.4.4).
 *
 * <p>4 transition별 이메일 발송을 토글하는 단일 yaml 게이트. share-expired/permission-expired cron의
 * `enabled` 토글이 DB로 이관된 것과 달리 본 트랙은 KISS — yaml-only. 운영 토글이 필요해지면 후속
 * 트랙에서 `cron_policy` 패턴 답습 가능.
 *
 * @param enabled 활성화 여부. false면 listener early-return으로 DB/SMTP 호출 0.
 * @param baseUrl 이메일 본문의 deep-link base — `{baseUrl}/admin/approvals/{id}` 조합. prod는
 *                application-prod.yml에서 overwrite.
 * @param from    발신자 주소. 기존 `app.email.from`(forgot/reset 흐름)과 별개로 유지 — 향후 발신
 *                도메인 분리가 자유롭다.
 */
@ConfigurationProperties(prefix = "app.admin-approval.email")
public record AdminApprovalEmailProperties(
    boolean enabled,
    String baseUrl,
    String from
) {
    public AdminApprovalEmailProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost:3000";
        if (from == null || from.isBlank()) from = "noreply@ibizdrive.local";
    }
}
