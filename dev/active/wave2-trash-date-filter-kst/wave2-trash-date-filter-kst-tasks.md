# Tasks — admin trash KST date filter

## P1 Backend

- [ ] `AdminTrashController.parseDateBoundary` ZoneOffset.UTC → ZoneId.of("Asia/Seoul")
- [ ] javadoc 갱신 (KST 경계 의미 명시)
- [ ] `AdminTrashControllerTest`에 KST 경계 검증 추가 (instant 9시간 시프트 확인)
- [ ] 기존 PR #83 테스트 중 expected instant 가정한 케이스 갱신

## P2 검증

- [ ] `cd backend && ./gradlew test --tests "com.ibizdrive.admin.trash.*"` GREEN
- [ ] frontend `pnpm test --run` / `pnpm typecheck` / `pnpm lint` 회귀 0 (시그니처 무변경 확인용)

## P3 문서

- [ ] `docs/02 §7.11` admin trash endpoint 표/주석에 KST 경계 명시
- [ ] `docs/04 §8.3` (필요 시) 휴지통 필터 KST 명시
- [ ] `BETA-RELEASE.md §7` v1.x deferred에서 KST 항목 closure 표시
- [ ] `docs/progress.md` 최상단에 트랙 회고

## P4 PR

- [ ] dev-docs-update로 active Dev Docs 동기화
- [ ] dev/process 세션 파일 정리
- [ ] PR title: `feat(wave2-trash-date-filter-kst): admin trash deletedFrom/deletedTo KST 경계 (Wave 2 T9 follow-up)`
- [ ] PR body: KST 9시간 시프트 사례 + before/after 동작 + 단일 ZoneId 변경
