/**
 * Department 도메인 wire 타입 (A16, docs/02 §7.x, ADR #36).
 *
 * backend `DepartmentSummaryDto` record (`com.ibizdrive.department.DepartmentSummaryDto`)와 wire 1:1.
 * `GET /api/departments/search` 응답의 `items[*]` 형상.
 *
 * `path`(LTREE)/`createdAt` 등은 share subject picker UX에 불필요 → backend가 의도적으로 미노출
 * (ADR #36: minimal surface — A14 UserSummary 답습 + LTREE는 v1.x 트리 쿼리 도입 시 별도 wire).
 */
export interface DepartmentSummary {
  id: string
  name: string
}

/**
 * Admin 부서 목록/생성/수정 응답 항목 (admin-department-crud, Wave 2 T4, docs/02 §7.x).
 *
 * <p>backend {@code AdminDepartmentSummaryResponse} record와 wire 1:1. share-picker용
 * {@link DepartmentSummary}와 분리한 이유:
 * <ul>
 *   <li>share picker는 활성 부서만 노출 + minimal surface (ADR #36)</li>
 *   <li>admin은 비활성 포함 + {@code isActive}/{@code createdAt} 필요 (lifecycle UX)</li>
 * </ul>
 *
 * <p>{@code isActive}는 backend가 {@code deletedAt == null}로 도출한 boolean — UI는 본 필드만 사용.
 */
export interface AdminDepartmentSummary {
  id: string
  name: string
  isActive: boolean
  /** ISO-8601 (OffsetDateTime). */
  createdAt: string
}

/**
 * Spring {@code Page<AdminDepartmentSummaryResponse>} 직렬화 모양 — admin-department-crud.
 *
 * <p>{@link import('./user').AdminUserPage}와 동형. 전체 필드 중 UI에 필요한 부분집합만 type-narrow.
 */
export interface AdminDepartmentPage {
  content: AdminDepartmentSummary[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

/**
 * Admin 부서 생성 body — backend {@code AdminDepartmentCreateRequest} 1:1 mirror.
 *
 * <p>trim+검증은 backend에서 다시 수행 — UI는 입력값만 그대로 송신.
 */
export interface AdminDepartmentCreateBody {
  name: string
}

/**
 * Admin 부서 PATCH body — backend {@code AdminDepartmentPatchRequest} 1:1 mirror.
 *
 * <p>두 필드 모두 optional. 둘 다 비면 backend 400 ({@code AdminBadPatchException}).
 * {@code isActive=false} → deactivate, {@code true} → reactivate, {@code name} → rename.
 */
export interface AdminDepartmentPatchBody {
  name?: string
  isActive?: boolean
}
