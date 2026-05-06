package com.ibizdrive.admin;

import java.util.UUID;

/**
 * Admin이 부서의 비제재 일반 속성을 변경할 때 publish — admin-department-crud (Wave 2 T4).
 *
 * <p>{@link AdminDepartmentAuditListener}가 구독해 {@code admin.department.updated} audit row INSERT.
 * before/after는 변경된 필드만 담은 JSON 문자열 — service에서 수동 직렬화 (Jackson 의존 회피, T1
 * `AdminUserUpdatedEvent`와 동형).
 *
 * <p><b>커버 분기</b>:
 * <ul>
 *   <li>rename — {@code beforeJson="{\"name\":\"old\"}"}, {@code afterJson="{\"name\":\"new\"}"}</li>
 *   <li>reactivate — {@code beforeJson="{\"isActive\":false}"}, {@code afterJson="{\"isActive\":true}"}</li>
 * </ul>
 *
 * <p>deactivate는 별도 이벤트({@link AdminDepartmentDeactivatedEvent}, audit type
 * {@code admin.department.deactivated}) — 제재 분기로 의미 분리 (T1 user audit과 동형).
 *
 * @param departmentId 변경된 부서 id
 * @param actorId      변경을 수행한 관리자 user id
 * @param beforeJson   변경 전 부분 상태 JSON
 * @param afterJson    변경 후 부분 상태 JSON
 */
public record AdminDepartmentUpdatedEvent(UUID departmentId, UUID actorId, String beforeJson, String afterJson) {
}
