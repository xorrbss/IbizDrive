package com.ibizdrive.admin;

import java.util.UUID;

/**
 * Admin이 부서를 비활성화(soft-delete)할 때 publish — admin-department-crud (Wave 2 T4).
 *
 * <p>{@link AdminDepartmentAuditListener}가 구독해 {@code admin.department.deactivated} audit row INSERT.
 * deactivate는 share-picker / dept resolution에서 부서를 제외하는 제재성 분기 — reactivate({@link AdminDepartmentUpdatedEvent})
 * 와 의미 분리 (T1 user audit과 동형).
 *
 * @param departmentId 비활성화된 부서 id
 * @param actorId      비활성화를 수행한 관리자 user id
 */
public record AdminDepartmentDeactivatedEvent(UUID departmentId, UUID actorId) {
}
