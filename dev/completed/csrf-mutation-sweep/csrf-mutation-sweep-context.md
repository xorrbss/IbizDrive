# csrf-mutation-sweep — Context

Last Updated: 2026-05-09

## SESSION PROGRESS

- 직전 세션에서 fix-create-folder-csrf hotfix(PR #115) 발견 → ADMIN 사용자도
  폴더 생성 시 403. 원인은 `api.createFolder`의 X-CSRF-TOKEN 헤더 누락.
- sweep 인벤토리 작성: api.ts mutation 23건 중 11건이 동일 누락
  (createFolder는 PR #115에서 처리 중이라 sweep 제외).
- 본 세션은 11건 일괄 fix + 회귀 가드 테스트 추가 — **완료**.
  - Phase 1 (코드 수정): 11/11 함수에 readCookie + X-CSRF-TOKEN 헤더 적용
  - Phase 2 (테스트): `api.csrfMutations.test.ts` 13 케이스(11 함수 + restoreFile newName 분기 + restoreFolder)
  - Phase 3 (게이트): typecheck/lint exit 0 + test 13 PASS

## Current Execution Contract

- **scope**: frontend/src/lib/api.ts 단독 수정 + 테스트 1개 신규.
- **base**: origin/master (워크트리 sweep base).
- **non-goals**: createFolder 미수정(PR #115), 면제 endpoint 미수정
  (signup/passwordForgot/passwordReset), backend 무변경.
- **gate**: `pnpm typecheck && pnpm lint && pnpm test --run api.csrfMutations`.

## 현재 active task

Phase 1 (mutation 11건에 X-CSRF-TOKEN 추가) → Phase 2 (회귀 가드) →
Phase 3 (게이트 + PR).

## 다음 세션 읽기 순서

1. `csrf-mutation-sweep-plan.md` (이 task의 범위 + 인벤토리 표)
2. `csrf-mutation-sweep-tasks.md` (체크박스 진행)
3. `frontend/src/lib/api.ts` 라인 285/306/318/344/395/966/985/1007/1046/1421/1561/1722
4. `frontend/src/lib/api.createFolder.test.ts` (회귀 가드 패턴 동형)
5. `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java`
   (CSRF 면제 매트릭스 §csrf)

## 핵심 파일과 역할

| 파일 | 역할 |
|---|---|
| `frontend/src/lib/api.ts` | mutation API 정의, 본 sweep의 유일한 코드 수정 대상 |
| `frontend/src/lib/api.csrfMutations.test.ts` (NEW) | 11 케이스 회귀 가드 |
| `frontend/src/lib/api.createFolder.test.ts` | (PR #115) 패턴 참고용 |
| `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java` | CSRF 정책 출처 |
| `docs/03-security-compliance.md` §1.3 | X-CSRF-Token 계약 |

## 중요한 의사결정

1. **createFolder 제외** — PR #115가 단독 처리 중. 본 sweep과 분리해
   conflict/혼선 회피.
2. **면제 endpoint 제외** — signup/passwordForgot/passwordReset은
   `ignoringRequestMatchers`로 backend가 면제. KISS: 헤더 추가하지
   않음.
3. **readCookie 동기 패턴 채택** — `ensureCsrfToken` 비동기 호출은
   인증 안 된 첫 호출(login)에 필요. 인증 후 mutation은 이미 cookie에
   토큰이 있어 readCookie로 충분. createFolder hotfix와 동형.
4. **단일 테스트 파일** — `api.csrfMutations.test.ts` 1개에 11 케이스.
   describe 도메인별 그룹화. KISS, 파일 폭증 방지.

## 빠른 재개

```
cd C:/project/IbizDrive/.claude/worktrees/csrf-mutation-sweep
git status
cat dev/active/csrf-mutation-sweep/csrf-mutation-sweep-tasks.md
# 미완료 phase 확인 후 plan.md 인벤토리 표대로 진행
```
