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
