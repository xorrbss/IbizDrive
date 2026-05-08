# docs-csrf-token-notation — Tasks

Last Updated: 2026-05-09

## Phase 1 — docs 보강

- [x] T1.1 `docs/03 §2.2` 표 L129 CSRF 행에 case-insensitive 노트
- [x] T1.2 `docs/03 §2.2` 표 아래 callout — frontend 패턴 분기 + PR backlink
- [x] T1.3 `docs/02 §7.1` `/api/auth/csrf` 영역 frontend 패턴 노트

## Phase 2 — Dev Sync + PR

- [x] T2.1 `docs/progress.md` 최상단 entry
- [x] T2.2 `dev/process/docs-csrf-token-notation.md` 삭제
- [x] T2.3 단일 commit
- [x] T2.4 PR open + 백그라운드 자동 머지/archive/cleanup

## 검증 참조

- 시각 검증: `git diff docs/02-backend-data-model.md docs/03-security-compliance.md`
- code change 0 검증: `git diff --stat | grep -v '^\(docs\|dev\)/'` 비어야 함

## 문서 반영

본 트랙 자체가 docs. 백엔드/프론트 코드 동기화 불필요.
