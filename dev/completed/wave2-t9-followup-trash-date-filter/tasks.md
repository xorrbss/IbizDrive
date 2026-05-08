# Tasks

## P1 백엔드 (filter → repo → service → controller → tests)

- [x] T1.1 `AdminTrashFilters` record에 `Instant deletedFromMin`, `Instant deletedToMax` 추가
- [x] T1.2 `AdminTrashRepository` 두 native @Query에 deletedFrom/deletedTo 절 추가
- [x] T1.3 `AdminTrashService` 검증(`deletedFromMin >= deletedToMax` 거부) + repo 호출 시 두 값 전달
- [x] T1.4 `AdminTrashController` `@RequestParam(required=false) String deletedFrom, deletedTo` + `LocalDate.parse` → Instant 경계 변환
- [x] T1.5 `AdminTrashServiceTest` — pass-through + 거꾸로 범위 거부 + 동일값 거부 (3건 신규 + 시그니처 마이그레이션)
- [x] T1.6 `AdminTrashControllerTest` — 정상 파싱, 잘못된 형식 400 (3건 신규)

## P2 프론트엔드

- [x] T2.1 `types/trash.ts` `AdminTrashFilters`에 `deletedFrom`/`deletedTo: string | null`
- [x] T2.2 `queryKeys.ts` `adminTrashList`에 두 필드 추가
- [x] T2.3 `api.ts` `adminListTrash`가 두 파라미터 송신
- [x] T2.4 `app/admin/trash/all/page.tsx` 필터 UI 두 date input 추가, 초기 상태에 `deletedFrom: null, deletedTo: null` 추가
- [x] T2.5 `api.adminTrash.test.ts` — date 파라미터 송신 + 단독 적용 (2건 신규) + EMPTY_FILTERS 확장
- [x] T2.6 `useAdminTrash.test.tsx` — `AdminTrashFilters` 확장 필드 반영

## P3 문서

- [x] T3.1 `docs/02-backend-data-model.md` §7.11 — 쿼리 파라미터 표/예시 갱신, deferred 노트에서 "날짜 범위" 제거
- [x] T3.2 `docs/progress.md` 최상단에 트랙 종료 항목 추가

## P4 검증

- [x] V1 backend `./gradlew test --tests "com.ibizdrive.admin.trash.*"` BUILD SUCCESSFUL
- [x] V2 frontend `pnpm test --run` 121 files / 903 passed, `pnpm typecheck` 0, `pnpm lint` 0, `pnpm build` 0
- [x] V3 `git diff --stat` 14 files / +327 / -48 — 영향 범위 검토 완료

## 후속(아직 미실행)

- [ ] B1 브랜치 결정: 현재 `docs/wave2-closure-and-beta-ops-runbook`에 작업됨 — feature 코드를 docs 브랜치에 두면 안 되므로 별도 feature 브랜치(`feat/wave2-t9-trash-date-filter`)로 옮겨야 함. **사용자 게이트.**
- [ ] B2 commit + PR (사용자 승인 후)
- [ ] B3 dev-docs 아카이브 → `dev/completed/`
