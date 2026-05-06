---
Last Updated: 2026-05-06
Status: ✅ CLOSED
---

# Tasks — admin-user-search-update (Wave 1 — T1)

## P1 — Backend domain
- [x] `User.changeDisplayName(String)` + 검증 (blank, length<=100)
- [x] `UserTest.changeDisplayName` 케이스
- [x] `UserRepository.findForAdminPageable(String pattern, Pageable)` (q null/blank → 전체)
- [x] `UserRepositoryTest.findForAdminPageable_*` 케이스 (전체 / 검색 / 비활성 포함 / soft-delete 제외 / 정렬 / 페이지네이션)

## P2 — Backend events + service
- [x] `AdminUserUpdatedEvent` record
- [x] `AdminUserService.list(Pageable, String q)` 시그니처 변경 + LIKE escape
- [x] `AdminUserService.changeDisplayName(UUID, String, UUID)` (멱등 + self-허용 + event publish)
- [x] `AdminUserService.reactivate(UUID, UUID)` (멱등 + self-허용 + event publish)
- [x] `AdminUserServiceTest` 신규 매트릭스

## P3 — Backend controller + listener
- [x] `AdminUserPatchRequest.displayName?` + `isEmpty()` 갱신
- [x] `AdminUserController.list(?q=, page=, size=)`
- [x] `AdminUserController.patch` — reactivate 가드 제거 + 4분기
- [x] `AdminAuditListener.onAdminUserUpdated`
- [x] `AdminUserControllerTest` 신규 매트릭스
- [x] `AdminAuditListenerTest` 신규/보강

## P4 — Frontend api + hooks + queryKeys
- [x] `AdminUserPatchBody.displayName?`
- [x] `qk.adminUsersList(page, size, q='')`
- [x] `api.adminListUsers(page, size, q?='')` (URLSearchParams로 인코딩, blank omit)
- [x] `useAdminUsers(page, size, q='')`
- [x] hook test 갱신

## P5 — Frontend page + tests
- [x] `/admin/users` 검색 input (useDebounce 300ms + page=0 reset)
- [x] UserRow displayName inline edit (편집/저장/취소 + blank/length 가드)
- [x] 비활성 row 재활성화 버튼 분기
- [x] page.test.tsx 갱신/추가 (총 19 케이스)

## P6 — Docs + closure + PR
- [x] docs/02 §7.4 PATCH body + GET ?q=
- [x] docs/04 §2 / §4 활성 표시
- [x] BETA-RELEASE.md §6 (35→36 emit / 9→8 deferred) + §7 wording
- [x] docs/progress.md entry (이 트랙 종료 + audit-emit-gap-mapping 표 갱신)
- [ ] dev/active → dev/completed (closure 단계에서 이동)
- [ ] PR
