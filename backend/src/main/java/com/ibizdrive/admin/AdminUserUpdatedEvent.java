package com.ibizdrive.admin;

import java.util.UUID;

/**
 * Admin이 사용자의 비제재 일반 속성(displayName, isActive→true)을 변경 시 publish —
 * admin-user-search-update (Wave 1 — T1).
 *
 * <p>{@link AdminAuditListener}가 구독해 {@code admin.user.updated} audit row INSERT 시
 * before/after metadata({@code beforeJson}, {@code afterJson})를 같이 기록.
 *
 * <p>의도된 분리:
 * <ul>
 *   <li>{@link AdminRoleChangedEvent} — role 변경 (별도 audit type {@code admin.role.changed})</li>
 *   <li>{@link AdminUserDeactivatedEvent} — 명시적 제재 (deactivate, {@code admin.user.deactivated})</li>
 *   <li>본 이벤트 — displayName 편집, reactivate 등 "비제재 일반 속성 변경" 우산</li>
 * </ul>
 *
 * <p>{@code beforeJson}/{@code afterJson}은 호출자(서비스)가 변경 필드만 담아 전달
 * (예: {@code {"displayName":"old"}} / {@code {"displayName":"new"}}). 같은 값이면 호출자
 * 단계에서 멱등 분기(no event publish) — listener는 변경 사실만 신뢰한다.
 *
 * @param userId      변경된 user의 id (target)
 * @param actorId     변경을 수행한 관리자의 user id (audit actor)
 * @param beforeJson  변경 전 부분 스냅샷 JSON (수동 직렬화)
 * @param afterJson   변경 후 부분 스냅샷 JSON
 */
public record AdminUserUpdatedEvent(
    UUID userId,
    UUID actorId,
    String beforeJson,
    String afterJson
) {
}
