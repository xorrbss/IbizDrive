package com.ibizdrive.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 부팅 시 audit_log append-only 연결 검증 설정 (application.yml {@code app.audit.append-only-check.*}).
 *
 * <p>{@link AuditAppendOnlyStartupCheck}가 소비. V4의 REVOKE는 {@code app_user} role에만 적용되므로
 * 런타임 datasource가 다른 계정(table owner·superuser)으로 연결되면 append-only 보증(ADR #25)이
 * 조용히 무력화된다 — 본 설정이 그 검증의 실패 정책을 결정한다 (ADR #49).
 *
 * @param enforce true면 위반 시 부팅 실패(fail-fast). false(기본)면 WARN 로그만 — 로컬 dev의
 *                postgres superuser 흐름(docs/local-dev.md)을 막지 않기 위함. prod 프로파일은
 *                application-prod.yml이 true로 강제.
 */
@ConfigurationProperties(prefix = "app.audit.append-only-check")
public record AuditAppendOnlyProperties(boolean enforce) {
}
