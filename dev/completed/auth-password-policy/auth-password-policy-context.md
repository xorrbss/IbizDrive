---
Last Updated: 2026-05-04
---

# Context — auth-password-policy

## SESSION PROGRESS

**부트스트랩 세션 (2026-05-04)**
- Worktree: `C:/project/IbizDrive/.claude/worktrees/auth-password-policy` (branch `wip/auth-password-policy`, base `origin/master` @ `c1981f3`)
- PR #54 (m-admin-entry-rewrite, ADR #21 closure) MERGED 후 진입 — base는 master 직결, stacking 없음
- Dev Docs 3파일 생성 (plan/context/tasks)
- 코드 변경 없음 — 다음 세션부터 P1 진입

## Current Execution Contract

- **모드**: 자율 실행 + phase 단위 게이트. 각 phase 완료 시 사용자 보고 후 다음 phase로 진행 (수동 모드 전환 시 해제).
- **TDD 강제**: 각 phase는 빨강(테스트 추가, 실패 확인) → 초록(구현, 통과) → 리팩터 순. test-driven-development 스킬 준수.
- **머지 게이트**: P5 종료 후 사용자가 PR 검토하고 merge 승인.
- **컨텍스트 임계**: 60%/70%/75%/80% 임계 시 `dev-docs-update`로 본 3파일 갱신 후 핸드오프.

## 현재 active task

- Phase: **P1 — Backend 공통 validator (TDD)**
- Task: 다음 세션 시작 시 `auth-password-policy-tasks.md`의 P1.1부터 진행
- Status: 미진입 (worktree + dev docs만 준비)

## 다음 세션 읽기 순서

1. `auth-password-policy-context.md` (본 파일) — 현재 위치 확인
2. `auth-password-policy-tasks.md` — 다음 실행 task 식별
3. `auth-password-policy-plan.md` — 해당 phase의 acceptance criteria + 게이트 재확인
4. `docs/03-security-compliance.md §2.7` (L245~) — ADR #19 정책 표
5. `docs/00-overview.md` ADR #19 (L151) + ADR #41 (L176) — closure 메모 위치
6. `backend/src/main/java/com/ibizdrive/auth/dto/SignupRequest.java` 외 2개 DTO — 적용 대상
7. `backend/src/main/java/com/ibizdrive/common/error/AuthExceptionHandler.java:71-78` — `MethodArgumentNotValidException` 매핑 (`rule = fe.getCode()` — ValidPassword 적용 시 rule 매핑 검토 필요, plan 리스크 §3 참조)
8. `frontend/src/app/(auth)/signup/page.tsx:39`, `frontend/src/app/(auth)/reset-password/page.tsx:81`, `frontend/src/app/(explorer)/account/password/page.tsx:47` — 프론트 적용 대상

## 핵심 파일과 역할

| 파일 | 역할 | Phase |
|---|---|---|
| `backend/src/main/java/com/ibizdrive/auth/validation/ValidPassword.java` (신규) | 어노테이션 | P1 |
| `backend/src/main/java/com/ibizdrive/auth/validation/PasswordPolicyValidator.java` (신규) | 5규칙 검증 + ConstraintViolation 빌드 | P1 |
| `backend/src/test/java/com/ibizdrive/auth/validation/PasswordPolicyValidatorTest.java` (신규) | 단위 테스트 | P1 |
| `backend/src/main/java/com/ibizdrive/auth/dto/SignupRequest.java` | `@Size(min=8,max=128)` → `@ValidPassword` | P2 |
| `backend/src/main/java/com/ibizdrive/auth/password/dto/ResetPasswordRequest.java` | 동일 | P2 |
| `backend/src/main/java/com/ibizdrive/auth/password/dto/ChangePasswordRequest.java` | 동일 | P2 |
| `backend/src/main/java/com/ibizdrive/common/error/AuthExceptionHandler.java:71-78` | rule 매핑 (`@ValidPassword` 시 `getDefaultMessage()` 사용 분기 필요할 수 있음) | P1·P2 |
| `backend/src/main/java/com/ibizdrive/admin/TempPasswordGenerator.java` | 임시 PW 정책 만족 회귀 가드 대상 | P2 |
| `backend/src/test/java/com/ibizdrive/admin/TempPasswordGeneratorTest.java` (또는 신규) | 200회 sample 회귀 가드 | P2 |
| `frontend/src/lib/password.ts` (신규) | `validatePassword`, `getPasswordRuleMessage`, `PasswordRule` type | P3 |
| `frontend/src/lib/password.test.ts` (신규) | 5규칙 단위 테스트 | P3 |
| `frontend/src/app/(auth)/signup/page.tsx` | onSubmit에서 `validatePassword` 사용 | P4 |
| `frontend/src/app/(auth)/reset-password/page.tsx` | 동일 | P4 |
| `frontend/src/app/(explorer)/account/password/page.tsx` | 동일 | P4 |
| `docs/00-overview.md` | ADR #19 closure / ADR #41 closure | P5 |
| `docs/03-security-compliance.md §2.8 L319` | 정정 메모 → closure 정정 | P5 |
| `docs/progress.md` | 본 트랙 entry | P5 |

## 중요한 의사결정

- **단일 validator 어노테이션 선택 (vs 다중 어노테이션)**: `@ValidPassword` 1개로 5규칙 일괄 처리. 다중 어노테이션(@MinLength + @RequiresAlpha + ...)은 KISS 위반 + 메시지 fragmentation. 한 violation에서 첫 위반 rule만 노출 (사용자 입장 한 번에 하나씩 고치는 UX).
- **rule key는 enum string 계약**: `min_length` / `missing_alpha` / `missing_digit` / `whitespace` / `max_length`. backend·frontend 양쪽이 이 5개 string을 계약으로 공유. UI는 rule → 한글 메시지 매핑.
- **첫 위반만 노출 vs 모두 노출**: 첫 위반만 노출 (UX 단순). 다중 노출은 메시지 폭증 + 우선순위 모호.
- **위반 우선순위**: `whitespace` → `max_length` → `min_length` → `missing_alpha` → `missing_digit` (보안성 높은 순. 공백은 즉시 거부.)
- **TempPasswordGenerator 정책 만족 보증**: 회귀 가드 테스트 (200회). 실패 시 generator 자체를 "alpha ≥1 + digit ≥1 + length 16" 보장 알고리즘으로 강화 (현재 alphabet random이라 통계적으로 거의 만족하나 엄밀 보장 아님).
- **frontend i18n 부재**: 한국어 메시지 inline 매핑 (KISS). i18n 인프라 도입은 별도 트랙.

## 빠른 재개 안내

```
다음 세션 시작 시:
1. cd C:/project/IbizDrive/.claude/worktrees/auth-password-policy
2. git status → wip/auth-password-policy 클린 확인
3. dev/active/auth-password-policy/ 3파일 읽기
4. P1.1부터 진입 (PasswordPolicyValidatorTest 빨강 단계)
```

## 백링크

- ADR #19: `docs/00-overview.md` L151
- ADR #41: `docs/00-overview.md` L176
- 정책 표: `docs/03-security-compliance.md` §2.7 L245~263
- 정정 메모(닫을 대상): `docs/03-security-compliance.md` §2.8 L319
