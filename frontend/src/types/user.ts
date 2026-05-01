/**
 * User 도메인 wire 타입 (A14, docs/02 §7.14, ADR #35).
 *
 * backend `UserSummaryDto` record (`com.ibizdrive.user.UserSummaryDto`)와 wire 1:1.
 * `GET /api/users/search` 응답의 `items[*]` 형상.
 *
 * `role`/`createdAt` 등은 share subject picker UX에 불필요 → backend가 의도적으로 미노출
 * (ADR #35: minimal surface — privacy/payload 비용 최소화).
 */
export interface UserSummary {
  id: string
  displayName: string
  email: string
}
