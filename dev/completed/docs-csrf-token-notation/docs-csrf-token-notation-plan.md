# docs-csrf-token-notation — Plan

Last Updated: 2026-05-09

## 요약

PR #115(`fix-create-folder-csrf`) hotfix + PR #121(`csrf-mutation-sweep`) 11건
일괄 수정의 결과를 docs에 명시화. 핵심:
- **표기 규약**: backend 헤더 매핑은 `X-CSRF-Token`(mixed case)이지만 HTTP 헤더는
  case-insensitive라 frontend의 `X-CSRF-TOKEN`(uppercase) 송신과 호환. 두 표기
  모두 정상.
- **frontend 패턴 분기**: 인증 후 mutation은 `readCookie('XSRF-TOKEN')` 동기 송신,
  비인증 첫 호출(login 등)은 `ensureCsrfToken()` 비동기로 `/api/auth/csrf` 사전
  발급.
- **회귀 가드**: `frontend/src/lib/api.csrfMutations.test.ts`(13 케이스) +
  `api.createFolder.test.ts`(4 케이스).

## 현재 상태

`docs/03 §2.2` 세션 모델 표 L129에 CSRF 행 1줄만 존재. frontend 패턴/표기 case
명시 없음. `docs/02 §7.1` 인증 endpoint 영역에 `X-CSRF-Token` 헤더 표기 반복
사용되나 frontend 송신 패턴 분기 미문서화.

## 목표 상태

1. `docs/03 §2.2` 표 L129 CSRF 행에 case-insensitive 노트.
2. `docs/03 §2.2` 표 아래 callout — frontend 패턴 분기(readCookie vs
   ensureCsrfToken) + 회귀 가드 PR backlink (#115, #121).
3. `docs/02 §7.1` `/api/auth/csrf` GET endpoint 영역에 frontend 패턴 분기 노트.

## Phase별 실행 지도

### Phase 1 — docs 보강

- `docs/03 §2.2` 표 L129 + 그 아래 callout
- `docs/02 §7.1` `/api/auth/csrf` 영역

### Phase 2 — Dev Sync + PR

- `docs/progress.md` entry
- `dev/process` ownership 삭제
- 단일 commit + PR + 백그라운드 자동 머지/archive/cleanup

## Acceptance Criteria

- [ ] `docs/03 §2.2`에 case-insensitive 명시
- [ ] `docs/03 §2.2`에 frontend 패턴 분기 명시 + PR #115/#121 backlink
- [ ] `docs/02 §7.1`에 frontend 패턴 분기 노트
- [ ] 코드 0줄 변경
- [ ] `docs/progress.md` 최상단 entry

## 검증 게이트

코드 변경 0이라 테스트 불필요. 수동 검증:
- markdown 렌더링 깨짐 없음
- backlink 라인이 실제 PR 번호와 일치

## 리스크

- 다른 세션이 docs/02·03 동시 수정 가능성. 머지 전 master rebase로 확인.
