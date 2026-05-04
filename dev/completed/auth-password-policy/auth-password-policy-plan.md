---
Last Updated: 2026-05-04
---

# Plan — auth-password-policy

## 요약

ADR #19(비밀번호 정책 = min 12자, 영·숫 각 1자 이상, 공백 금지, max 128자) 본문 정합을 회복한다. ADR #41 auth-pages 트랙에서 MVP 가입 마찰 최소화를 이유로 `@Size(min=8, max=128)`로 임시 정정했던 3 endpoint(signup/reset/change)를 v1.x 정책 강화 트랙으로 일괄 닫는다.

## 현재 상태 분석

PR #54 머지 후 master(c1981f3) 기준:

**ADR #19 canonical (docs/03 §2.7)**
- min 12자, max 128자, 영문자 ≥1, 숫자 ≥1, 공백 금지
- 위반 시 `VALIDATION_ERROR { rule: 'min_length'|'missing_alpha'|'missing_digit'|'whitespace'|'max_length' }`
- BCrypt(strength=12)는 이미 `SecurityConfig.passwordEncoder()`에서 enforce 중 ✓

**현재 backend 구현 (정정 상태)**
- `auth/dto/SignupRequest.java:19` — `@NotBlank @Size(min = 8, max = 128) String password`
- `auth/password/dto/ResetPasswordRequest.java:16` — 동일 (`newPassword`)
- `auth/password/dto/ChangePasswordRequest.java:16` — 동일 (`newPassword`)
- 영·숫 1자 이상, 공백 금지 검증은 어디에도 없음

**현재 frontend 구현 (정정 상태)**
- `frontend/src/app/(auth)/signup/page.tsx:39` — `password.length < 8` 체크
- `frontend/src/app/(auth)/reset-password/page.tsx:81` — 동일
- `frontend/src/app/(explorer)/account/password/page.tsx:47` — 동일
- 검증 함수 공통화 없음 (각 페이지 ad-hoc)
- `frontend/src/lib/password.ts` 부재 — 신설 필요

**연관 자산**
- `docs/03-security-compliance.md §2.7` 표 — canonical 정책 (변경 불필요)
- `docs/03-security-compliance.md §2.8 L319` — ADR #19 정정 메모 (이번 트랙에서 closure)
- `docs/00-overview.md ADR #19 (L151)` — 본 ADR (closure note 추가 필요)
- `docs/00-overview.md ADR #41 (L176)` — auth-pages 트랙 정정 메모 (closure note 추가 필요)
- `backend/src/main/java/com/ibizdrive/admin/TempPasswordGenerator.java` — 16자 alphanumeric+소량 특수문자 → 새 정책 만족(영·숫·길이) 검증 필요. **임시 PW 자체가 정책 위반이면 admin invite 흐름 깨짐**.
- `backend/src/test/java/com/ibizdrive/auth/AuthControllerSignupTest.java` — VALIDATION_ERROR 테스트 갱신 대상
- `backend/src/test/java/com/ibizdrive/auth/SignupServiceTest.java`
- `backend/src/test/java/com/ibizdrive/auth/password/PasswordControllerChangeTest.java`
- `backend/src/test/java/com/ibizdrive/auth/password/PasswordControllerResetTest.java`
- `backend/src/test/java/com/ibizdrive/auth/password/PasswordResetServiceTest.java`

## 목표 상태

- backend: 3 DTO가 `@ValidPassword` 단일 어노테이션으로 ADR #19 5규칙(min/max/alpha/digit/whitespace) 일괄 검증. 위반 시 standard `VALIDATION_ERROR` envelope에 rule code 노출.
- backend: `TempPasswordGenerator`가 항상 새 정책을 만족함을 테스트로 보증 (회귀 가드).
- frontend: `frontend/src/lib/password.ts`에 backend와 동일 로직의 `validatePassword(input): { ok: true } | { ok: false, rule: PasswordRule }` export. 단위 테스트 5규칙 + 경계값.
- frontend: signup/reset-password/account/password 3 페이지가 공통 validator 사용. 위반 시 rule별 한국어 메시지 + `aria-describedby` 연결.
- docs: ADR #19 본문 closure 메모(2026-05-04 정정 회복), ADR #41 본문 password 항목 closure, §2.8 L319 정정 메모 → closure 표기 변경.
- progress.md entry 추가.

## Phase별 실행 지도

### P1 — Backend 공통 validator (TDD)

- 빨강: `PasswordPolicyValidatorTest` 신설 — 5규칙 × 경계값(11/12/128/129자, 알파만/숫자만/혼합, 스페이스/탭/개행 포함) 단위 테스트.
- 초록: `auth/validation/ValidPassword` 어노테이션 + `PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String>` 구현. message resolution은 `{rule}` 키로 ConstraintValidatorContext에 disable + buildConstraintViolationWithTemplate 패턴 사용 (standard envelope의 fieldErrors에 rule 매핑).
- 위치: `backend/src/main/java/com/ibizdrive/auth/validation/`
- 검증: `./gradlew test --tests PasswordPolicyValidatorTest` 통과.

### P2 — Backend 3 DTO 적용 + integration 테스트 (TDD)

- 빨강: `AuthControllerSignupTest`, `PasswordControllerResetTest`, `PasswordControllerChangeTest`에 5규칙 거부 케이스 각 1개씩 추가 (Bean Validation → 400 VALIDATION_ERROR + fieldErrors[0].rule 검증).
- 초록: 3 DTO에서 `@Size(min=8, max=128)` 제거하고 `@NotBlank @ValidPassword` 적용 (max=128은 validator 내부에서 보장).
- TempPasswordGenerator 회귀 가드: `TempPasswordGeneratorTest`에 "생성된 임시 PW가 PasswordPolicyValidator 통과한다 (200회 sample)" 추가.
- 검증: `./gradlew test` 전체 통과.

### P3 — Frontend 공통 validator + 단위 테스트 (TDD)

- 빨강: `frontend/src/lib/password.test.ts` — 5규칙 × 경계값 (Vitest). backend `PasswordPolicyValidatorTest`와 동일 케이스 (계약 미러).
- 초록: `frontend/src/lib/password.ts` — `export type PasswordRule = 'min_length'|'missing_alpha'|'missing_digit'|'whitespace'|'max_length'`, `export function validatePassword(input: string): { ok: true } | { ok: false, rule: PasswordRule }`. 정규식/charAt 기반, dependency 없음.
- 검증: `pnpm test --filter password` 통과.

### P4 — Frontend 3 페이지 적용 (TDD per page)

- 각 페이지(`signup`, `reset-password`, `account/password`)에 대해:
  - 빨강: 페이지 단위 테스트에 "rule별 거부 케이스" 추가 (`@testing-library/react`, fireEvent submit + assert error 메시지).
  - 초록: 페이지 onSubmit 가드에서 `validatePassword` 호출 → rule → `setError(getPasswordRuleMessage(rule))`. submit 버튼 disabled는 `email/password 비어있음` 만 유지(현재와 동일 — 정책 위반은 submit 시 표시).
- 한국어 메시지 매핑은 `frontend/src/lib/password.ts`에 `getPasswordRuleMessage(rule: PasswordRule): string` 동거. (i18n 인프라 없음, KISS.)
- 검증: `pnpm typecheck && pnpm lint && pnpm test` 통과.

### P5 — Docs sync + PR

- `docs/00-overview.md`:
  - ADR #19 row: closure 메모 추가 — "**Closure (auth-password-policy, 2026-05-04)**: 본문 정책(min 12자, 영·숫·공백 금지)을 backend 3 endpoint + frontend 3 페이지에 일괄 강제. ADR #41 정정 회복."
  - ADR #41 row: password 항목 끝에 "**정정 closure**: auth-password-policy 트랙(2026-05-04)에서 ADR #19 본문 회복 — min 12 + 영·숫 ≥1 + 공백 금지."
- `docs/03-security-compliance.md §2.8 L319`: "—§2.7 ADR #19(min 12) 정정 …" 라인을 "—§2.7 ADR #19 정책 그대로 적용 (auth-password-policy closure, 2026-05-04)"로 정정.
- `docs/progress.md`: 본 트랙 entry (완료/다음/블로커 형식).
- PR title: `feat(auth-password-policy): ADR #19 비밀번호 정책 본문 회복 (signup/reset/change 통합)`.

## Acceptance Criteria

- [ ] `PasswordPolicyValidatorTest` 신설 — 5규칙 × 경계값 통과
- [ ] 3 DTO에 `@ValidPassword` 적용, `@Size(min=8, max=128)` 제거
- [ ] `AuthControllerSignupTest` / `PasswordControllerResetTest` / `PasswordControllerChangeTest` 각 5규칙 거부 케이스
- [ ] `TempPasswordGeneratorTest` 정책 회귀 가드 (200회 sample)
- [ ] `frontend/src/lib/password.ts` + `password.test.ts` (단위 테스트)
- [ ] signup/reset-password/account/password 3 페이지가 `validatePassword` 사용
- [ ] 각 페이지 단위 테스트에 rule별 거부 케이스
- [ ] `./gradlew test` 통과
- [ ] `pnpm typecheck && pnpm lint && pnpm test` 통과
- [ ] docs ADR #19 / ADR #41 / §2.8 / progress.md 동기화
- [ ] 수동 시나리오: 신규 가입 시 11자/공백 포함/숫자 누락 PW 거부 메시지 → 정책 만족 PW로 가입 성공

## 검증 게이트

| Phase | 게이트 |
|---|---|
| P1 | `./gradlew test --tests PasswordPolicyValidatorTest` 통과 |
| P2 | `./gradlew test` 전체 통과, TempPasswordGenerator 회귀 가드 포함 |
| P3 | `pnpm test --filter password` 통과 |
| P4 | `pnpm typecheck && pnpm lint && pnpm test` 모두 통과 |
| P5 | docs diff 검토 + PR 생성 |
| 머지 | (gate) 사용자 승인 후 master push |

## 리스크와 완화 전략

| 리스크 | 완화 |
|---|---|
| TempPasswordGenerator 출력이 새 정책 위반 (예: 어떤 random seed에서 영문 0자 발생) | P2 회귀 가드 (200회 sample). 위반 시 generator 알고리즘에서 영문/숫자 ≥1 보장 로직(`shuffle([alpha1, digit1, ...rest])`) 추가. |
| 기존 사용자 PW가 새 정책 위반이지만 hash만 저장 | 검증은 hash 비교라서 영향 없음. PW 변경 시점에만 신정책 적용 — 의도된 동작 (docs §2.7 정책에 부합). |
| Bean Validation에 rule 메타 전달 | `ConstraintValidatorContext.disableDefaultConstraintViolation` + `buildConstraintViolationWithTemplate("{rule}")` + `addConstraintViolation`. ErrorResponse fieldError mapper가 message를 그대로 rule로 매핑 (현 envelope 구조 확인 필요 — P1 초록 단계에서 결정). |
| frontend rule 메시지와 backend 응답 메시지 불일치 | rule code(enum string) 자체를 계약으로 사용. UI는 rule → 한글 메시지 매핑. backend는 rule만 노출. |
| 정책 강화 후 기존 e2e/통합 테스트 fixture PW가 8자라 회귀 | grep으로 8자 PW fixture 전수 식별 → P2 빨강 단계에서 12자로 일괄 정정. |

## 영향 범위

- backend: 3 DTO + 신규 validator 1쌍 + 1 generator 회귀 가드 + 3 컨트롤러 통합 테스트
- frontend: 신규 lib 1개 + 3 페이지 onSubmit 분기 + 3 페이지 단위 테스트
- docs: ADR #19, ADR #41, §2.8, progress.md
- 마이그레이션: 없음 (검증 로직만)
- API contract: 응답 envelope 동일 (rule field는 standard fieldErrors에 이미 존재)
