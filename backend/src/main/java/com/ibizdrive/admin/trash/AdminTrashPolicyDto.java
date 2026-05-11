package com.ibizdrive.admin.trash;

/**
 * `/admin/trash/policy` 응답 DTO — 운영자가 현재 휴지통 보존 정책을 확인할 뷰 (GET) +
 * mutation 응답 (PUT).
 *
 * <p>{@code retentionDays}는 V17 single-row {@code trash_policy} 테이블의 현재 값
 * (trash-retention-mutation Phase B). 운영자는 {@code PUT}으로 무중단 변경 가능 (단일-approver
 * MVP, dual-approval framework는 v1.x++ deferred — docs/04 §15.4).
 *
 * <p>hard purge cron의 runtime 상태(enabled/cron/zone)는 별도 endpoint
 * (`/api/admin/system/cron`, Wave 1 T3)가 진실의 출처라 본 DTO에는 포함하지 않는다.
 */
public record AdminTrashPolicyDto(int retentionDays) {}
