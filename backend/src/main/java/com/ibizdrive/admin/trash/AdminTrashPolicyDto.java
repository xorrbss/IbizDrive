package com.ibizdrive.admin.trash;

/**
 * `/admin/trash/policy` 응답 DTO — 운영자가 현재 휴지통 보존 정책을 한눈에 확인할 read-only 뷰.
 *
 * <p>현재는 `app.trash.retention.days`(`TrashRetentionProperties`) 단일 노출. hard purge cron의
 * runtime 상태(enabled/cron/zone)는 별도 endpoint(`/api/admin/system/cron`, Wave 1 T3)가
 * 진실의 출처라 본 DTO에는 포함하지 않는다 — admin-cron-toggle 트랙(#102)이 cron policy를
 * DB-backed runtime mutation으로 진화시키므로 yml 정적 값과 혼동을 피하기 위함.
 *
 * <p>mutation은 v1.x deferred (`@ConfigurationProperties` 부팅 바인딩). 운영자는 application.yml
 * 수정 + 재기동으로 일수 변경.
 */
public record AdminTrashPolicyDto(int retentionDays) {}
