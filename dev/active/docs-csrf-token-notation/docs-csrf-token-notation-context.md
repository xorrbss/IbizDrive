# docs-csrf-token-notation — Context

Last Updated: 2026-05-09

## SESSION PROGRESS

- 본 트랙은 PR #121(csrf-mutation-sweep) 머지 직후 시작.
- 회귀 가드 결과를 docs에 명시화하는 정리 트랙. 코드 0줄.

## Current Execution Contract

- **scope**: `docs/02-backend-data-model.md` §7.1 + `docs/03-security-compliance.md` §2.2 + `docs/progress.md` 단독.
- **base**: origin/master (b7f4de3).
- **non-goals**: 코드 변경, 다른 docs 섹션, ADR 신설.
- **gate**: markdown 렌더 시각 검증 + git diff stat code 변경 0.

## 핵심 의사결정

1. **header 표기 규약 명시**: backend(`X-CSRF-Token`) ↔ frontend(`X-CSRF-TOKEN`)
   양쪽 다 정답, HTTP 헤더 case-insensitive라 호환. case mismatch로 회귀
   진단하지 않도록 docs에 case-insensitive 명시.
2. **frontend 패턴 분기 명시**: 인증 후 mutation = `readCookie` 동기, 비인증
   첫 호출 = `ensureCsrfToken` 비동기 (`/api/auth/csrf` GET). 향후 새 mutation
   추가 시 어느 패턴 채택할지 결정 빠름.
3. **회귀 가드 backlink**: PR #115, PR #121을 docs/03 §2.2 callout에 명기 →
   미래 회귀 시 root cause 추적 단축.

## 빠른 재개

```
cd C:/project/IbizDrive/.claude/worktrees/docs-csrf-token-notation
git status
cat dev/active/docs-csrf-token-notation/docs-csrf-token-notation-tasks.md
```
