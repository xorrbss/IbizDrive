# Wave 1.5 — auditor-cron-readonly (tasks)

## P1 — Backend guard relaxation
- [ ] `AdminSystemController#getCronStatus` `@PreAuthorize` 표현식 변경 (`hasRole('ADMIN')` → `hasRole('ADMIN') or hasRole('AUDITOR')`)
- [ ] 클래스 javadoc L23~24 "AUDITOR 미허용" 문구 정정 (Wave 1.5에서 read-only 한정 허용)

## P2 — Backend test flip
- [ ] `AdminSystemControllerTest`의 AUDITOR 케이스 200 expectation으로 flip
- [ ] AUDITOR 케이스에도 jobs 배열 길이 4 검증 추가 (단순 status 검사가 아닌 contract 검증)

## P3 — Docs sync
- [ ] docs/04-admin-operations.md §7.x 권한 표 갱신 (`/admin/system/cron` ADMIN+AUDITOR)
- [ ] BETA-RELEASE.md 해당 라인 갱신 (있을 시)

## P4 — Verification
- [ ] `cd backend && mvn -pl . test -Dtest='AdminSystemControllerTest'` 통과
- [ ] `cd backend && mvn test` 회귀 통과

## P5 — Closure
- [ ] commit + push
- [ ] PR 본문 작성 (Summary / Test plan)
- [ ] docs/progress.md closure 엔트리 추가
