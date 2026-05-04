---
Last Updated: 2026-05-04
---

# Tasks — auth-password-policy

## Phase 상태

| Phase | 상태 | 게이트 |
|---|---|---|
| P1 — Backend 공통 validator | ✅ 완료 | `./gradlew test --tests PasswordPolicyValidatorTest` GREEN |
| P2 — Backend 3 DTO 적용 + integration | ✅ 완료 | `./gradlew test` 전체 GREEN |
| P3 — Frontend 공통 validator | ✅ 완료 | `vitest run lib/password` GREEN (25 tests) |
| P4 — Frontend 3 페이지 적용 | ✅ 완료 | typecheck + lint + 738 tests GREEN |
| P5 — Docs sync + PR | ✅ 완료 | docs sync + dev-docs archive + PR |

## P1 — Backend 공통 validator (TDD)

- [ ] **P1.1** — 빨강: `PasswordPolicyValidatorTest` 신설, 5규칙 × 경계값 단위 테스트 추가 (실패 확인)
- [ ] **P1.2** — 초록: `auth/validation/ValidPassword` 어노테이션 + `PasswordPolicyValidator` 구현, 테스트 통과
- [ ] **P1.3** — `AuthExceptionHandler.validation()`이 `@ValidPassword` violation에서 `rule` 노출 가능한지 검증 (필요 시 `fe.getCode() == "ValidPassword"`일 때 `fe.getDefaultMessage()`를 rule로 매핑하는 분기 추가)
- [ ] **P1.4** — `./gradlew test --tests PasswordPolicyValidatorTest` 통과 확인 → 게이트 통과 보고

### 작업 전 필독
- `auth-password-policy-plan.md` § "P1 — Backend 공통 validator (TDD)"
- `docs/03-security-compliance.md §2.7` 정책 표 (5규칙 + rule code)
- `backend/src/main/java/com/ibizdrive/common/error/AuthExceptionHandler.java:71-78` (rule 매핑 현행)
- `backend/src/main/java/com/ibizdrive/common/error/ErrorResponse.java:14, 36` (envelope)

### 원본 코드 참조
- `auth/dto/SignupRequest.java:19` — `@NotBlank @Size(min = 8, max = 128) String password`
- `auth/password/dto/ResetPasswordRequest.java:16`
- `auth/password/dto/ChangePasswordRequest.java:16`
- `admin/TempPasswordGenerator.java` — 임시 PW 생성 (P2 회귀 가드 대상)

### 구현 대상
- 신규: `backend/src/main/java/com/ibizdrive/auth/validation/ValidPassword.java`
- 신규: `backend/src/main/java/com/ibizdrive/auth/validation/PasswordPolicyValidator.java`
- 신규: `backend/src/test/java/com/ibizdrive/auth/validation/PasswordPolicyValidatorTest.java`
- 가능 수정: `backend/src/main/java/com/ibizdrive/common/error/AuthExceptionHandler.java`

### 검증 참조
- `./gradlew test --tests PasswordPolicyValidatorTest`
- 케이스 매트릭스:
  - 길이: 0 / 11 / 12(경계 통과) / 128(경계 통과) / 129
  - 문자류: 알파만 12자 / 숫자만 12자 / 알파+숫자 12자(통과) / 영·숫·기호 12자(통과)
  - 공백: " " 포함 / "\t" / "\n" / 일반 ASCII만 (통과)
- 위반 우선순위: whitespace → max_length → min_length → missing_alpha → missing_digit

### 문서 반영
- 없음 (P1은 코드만, docs는 P5에서 일괄 반영)

---

## P2 — Backend 3 DTO 적용 + integration 테스트 (TDD)

- [ ] **P2.1** — 빨강: `AuthControllerSignupTest`에 5규칙 거부 케이스 추가 (400 + fieldErrors[0].rule 검증)
- [ ] **P2.2** — 빨강: `PasswordControllerResetTest`에 동일 5케이스 추가
- [ ] **P2.3** — 빨강: `PasswordControllerChangeTest`에 동일 5케이스 추가
- [ ] **P2.4** — 빨강: `TempPasswordGeneratorTest`에 "200회 sample 모두 PasswordPolicyValidator 통과" 추가
- [ ] **P2.5** — 초록: 3 DTO에서 `@Size(min=8, max=128)` 제거, `@NotBlank @ValidPassword` 적용
- [ ] **P2.6** — TempPasswordGenerator 회귀 가드 통과 (실패 시 generator 알고리즘에서 alpha/digit ≥1 강제)
- [ ] **P2.7** — 기존 e2e/통합 테스트의 8자 PW fixture grep + 12자로 일괄 정정
- [ ] **P2.8** — `./gradlew test` 전체 통과 → 게이트 통과 보고

### 작업 전 필독
- `auth-password-policy-plan.md` § "P2 — Backend 3 DTO 적용 + integration 테스트 (TDD)"
- P1 결과물 (validator 동작 확인)

### 원본 코드 참조
- `auth/dto/SignupRequest.java:19`
- `auth/password/dto/ResetPasswordRequest.java:16`
- `auth/password/dto/ChangePasswordRequest.java:16`
- `admin/TempPasswordGenerator.java` (구체 알고리즘 확인)
- `auth/SignupServiceTest.java`
- `auth/password/PasswordResetServiceTest.java`
- `auth/AuthControllerSignupTest.java`
- `auth/password/PasswordControllerResetTest.java`
- `auth/password/PasswordControllerChangeTest.java`

### 구현 대상
- 수정: 위 3 DTO + 5 테스트
- 가능 수정: `admin/TempPasswordGenerator.java` (회귀 가드 실패 시)
- 가능 신규: `admin/TempPasswordGeneratorTest.java` (없으면 신설)

### 검증 참조
- `./gradlew test`
- `grep -rn "min = 8" backend/src/main/java/com/ibizdrive/auth` — 잔존 없음 확인
- fixture grep: `grep -rn "password.{0,40}\"[A-Za-z0-9]\\{8,11\\}\"" backend/src/test`

### 문서 반영
- 없음 (P5에서 일괄)

---

## P3 — Frontend 공통 validator + 단위 테스트 (TDD)

- [ ] **P3.1** — 빨강: `frontend/src/lib/password.test.ts` 신설 — 5규칙 × 경계값 (Vitest, backend `PasswordPolicyValidatorTest` 미러)
- [ ] **P3.2** — 초록: `frontend/src/lib/password.ts` 신설
  - `export type PasswordRule = 'min_length'|'missing_alpha'|'missing_digit'|'whitespace'|'max_length'`
  - `export function validatePassword(input: string): { ok: true } | { ok: false, rule: PasswordRule }`
  - `export function getPasswordRuleMessage(rule: PasswordRule): string` (한국어)
- [ ] **P3.3** — `pnpm test --filter password` 통과 → 게이트 통과 보고

### 작업 전 필독
- `auth-password-policy-plan.md` § "P3 — Frontend 공통 validator + 단위 테스트 (TDD)"
- 핵심 원칙 11 (`docs/00-overview.md` 또는 `CLAUDE.md` §3): **정규화·검증 함수는 프론트/백엔드 동일 로직**
- P1 백엔드 validator 결과 (계약 미러)

### 원본 코드 참조
- `frontend/src/lib/normalize.ts` — frontend lib 패턴 참조
- `backend/.../PasswordPolicyValidator.java` (P1 결과)

### 구현 대상
- 신규: `frontend/src/lib/password.ts`
- 신규: `frontend/src/lib/password.test.ts`

### 검증 참조
- `pnpm test --filter password`
- backend P1 테스트 케이스와 동일 input/output 매트릭스 보증

### 문서 반영
- 없음 (P5에서 일괄)

---

## P4 — Frontend 3 페이지 적용 (TDD per page)

- [ ] **P4.1** — 빨강: signup 페이지 단위 테스트에 rule별 거부 케이스 추가 (`@testing-library/react`, fireEvent submit)
- [ ] **P4.2** — 초록: `frontend/src/app/(auth)/signup/page.tsx:39` `password.length < 8` 분기를 `validatePassword` + rule → message로 교체
- [ ] **P4.3** — 빨강·초록 동일 흐름: `frontend/src/app/(auth)/reset-password/page.tsx:81`
- [ ] **P4.4** — 빨강·초록 동일 흐름: `frontend/src/app/(explorer)/account/password/page.tsx:47`
- [ ] **P4.5** — 3 페이지 단위 테스트 + `pnpm typecheck && pnpm lint && pnpm test` 통과 → 게이트 통과 보고

### 작업 전 필독
- `auth-password-policy-plan.md` § "P4 — Frontend 3 페이지 적용 (TDD per page)"
- P3 lib 결과
- 기존 페이지 form 구조 (controlled state, error state 변수명, aria 속성)

### 원본 코드 참조
- `frontend/src/app/(auth)/signup/page.tsx:39, 58`
- `frontend/src/app/(auth)/reset-password/page.tsx:81`
- `frontend/src/app/(explorer)/account/password/page.tsx:47`
- `frontend/src/hooks/useSignup.ts:16` (jsdoc — `<8자` 표기 정정 필요)

### 구현 대상
- 수정: 위 3 페이지
- 수정: `useSignup.ts` jsdoc + 다른 jsdoc/주석에 `<8자` 잔존 확인 (`grep -rn "8자\|min.*8" frontend/src`)
- 수정 또는 신규: 페이지 단위 테스트 파일 (기존 spec 위치에 따름)

### 검증 참조
- `pnpm typecheck && pnpm lint && pnpm test`
- 수동: 각 페이지에서 11자 / 공백 포함 / 숫자 누락 / 영문 누락 / 129자 입력 → 각 한글 메시지 표시 확인

### 문서 반영
- 없음 (P5에서 일괄)

---

## P5 — Docs sync + PR

- [ ] **P5.1** — `docs/00-overview.md` ADR #19 row에 closure 메모 추가
- [ ] **P5.2** — `docs/00-overview.md` ADR #41 row password 항목에 closure 메모 추가
- [ ] **P5.3** — `docs/03-security-compliance.md §2.8 L319` 정정 메모 → closure 표기로 변경
- [ ] **P5.4** — `docs/progress.md`에 본 트랙 entry 추가 (완료/다음 컨텍스트/블로커 형식)
- [ ] **P5.5** — `dev/active/auth-password-policy/` → `dev/completed/auth-password-policy/` 이동 (Last Updated 갱신)
- [ ] **P5.6** — `git status` clean 확인, `./gradlew test` + frontend 게이트 final pass
- [ ] **P5.7** — PR 생성: `feat(auth-password-policy): ADR #19 비밀번호 정책 본문 회복 (signup/reset/change 통합)`
- [ ] **P5.8** — 사용자 머지 승인 게이트

### 작업 전 필독
- `auth-password-policy-plan.md` § "P5 — Docs sync + PR" + Acceptance Criteria
- `docs/00-overview.md` ADR #19 / ADR #41 표 row 형식 (다른 closure 사례 참조 — 예: ADR #21 closure note)
- `docs/progress.md` 최근 entry 형식

### 원본 코드 참조
- `docs/00-overview.md` L151 (ADR #19), L176 (ADR #41)
- `docs/03-security-compliance.md §2.8 L319`
- `docs/progress.md` 최근 트랙 entry (auth-must-change-pw, auth-forgot-rate-limit, m-admin-entry-rewrite 참조)

### 구현 대상
- 수정: 위 docs 파일 4개
- 이동: dev/active → dev/completed

### 검증 참조
- `git diff docs/` 검토
- PR description: 5규칙 × 3 endpoint × 3 페이지 매트릭스 + ADR #19↔#41 정정 회복 명시

### 문서 반영
- 본 phase 자체가 docs phase

---

## 검증 메모

- 각 phase는 빨강 → 초록 → 게이트 보고 순. 빨강 없이 초록 진행 금지 (TDD invariant).
- phase 간 컨텍스트 임계 60% 도달 시 `dev-docs-update` 호출하여 본 3파일 갱신.
- 에러 발견 시 phase 중단 + plan.md 리스크 섹션에 추가 + 사용자 보고.
