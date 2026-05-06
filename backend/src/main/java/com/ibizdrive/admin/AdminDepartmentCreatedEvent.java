package com.ibizdrive.admin;

import java.util.UUID;

/**
 * Admin이 부서를 새로 생성할 때 publish — admin-department-crud (Wave 2 T4).
 *
 * <p>{@link AdminDepartmentAuditListener}가 구독해 {@code admin.department.created} audit row INSERT.
 * {@link AdminUserCreatedEvent}와 동형 — admin이 만든 도메인 entity의 audit 트레일.
 *
 * @param departmentId 새로 생성된 부서의 id
 * @param actorId      생성을 수행한 관리자의 user id (audit actor)
 * @param name         생성된 부서의 정규화된 name (trim 완료)
 */
public record AdminDepartmentCreatedEvent(UUID departmentId, UUID actorId, String name) {
}
