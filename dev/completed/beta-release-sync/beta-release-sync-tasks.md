---
Last Updated: 2026-05-05
---

# Tasks — beta-release-sync

## Phase별 상태

- [ ] P1 — `BETA-RELEASE.md` sync
- [ ] P2 — `dev/process/` 스테일 파일 삭제
- [ ] P3 — closure (progress.md + dev-docs archive + PR)

## P1 — BETA-RELEASE.md sync

- [ ] header `Last Updated: 2026-05-02` → `2026-05-05`
- [ ] header Source 라인에 5건 closure 인용 추가 (`auth-must-change-pw` / `auth-forgot-rate-limit` / `m-admin-entry-rewrite` / `auth-password-policy` / `email-async`)
- [ ] §1 frontend 카운트 `647/647` → `738/738` 정정
- [ ] §5에 4행 추가:
  - 비밀번호 정책 (ADR #19 본문 회복) — auth-password-policy
  - mustChangePassword UX enforcement — auth-must-change-pw, ADR #21 §2.7
  - 운영자 초대 endpoint `POST /api/admin/users` — m-admin-entry-rewrite, ADR #21 closure
  - 이메일 비동기 발송 `@Async EmailService` — ADR #45 (anti-enumeration timing leak 완화)
- [ ] §6 audit emit coverage `29 emit (69%)` → `32 emit (76%)` 정정 + 신규 emit cross-link (`USER_PASSWORD_FORGOT_REQUESTED`, `USER_PASSWORD_RESET`, `ADMIN_USER_CREATED`)
- [ ] §7 admin frontend 표현 정정 (`audit logs UI(M12)만 활성` → `audit logs UI(M12) + admin shell + 운영자 초대 폼 활성, 사용자 목록/role 변경 v1.x`)

### 작업 전 필독

- `BETA-RELEASE.md` 전체 (166 라인)
- `docs/progress.md` 최상단 5건 closure (auth-password-policy 2026-05-04 / email-async 2026-05-03 / m-admin-entry-rewrite 2026-05-03 / auth-forgot-rate-limit 2026-05-03 / auth-must-change-pw 2026-05-03)

### 원본 코드 참조

- `backend/src/main/java/com/ibizdrive/admin/AdminUserController.java` — `POST /api/admin/users` `@PreAuthorize("hasRole('ADMIN')")`
- `backend/src/main/java/com/ibizdrive/admin/AdminAuditListener.java` — `ADMIN_USER_CREATED` emit
- `backend/src/main/java/com/ibizdrive/auth/validation/PasswordPolicyValidator.java` — ADR #19 5규칙
- `backend/src/main/java/com/ibizdrive/email/EmailService.java` — `@Async("emailExecutor")`
- `backend/src/main/java/com/ibizdrive/auth/password/PasswordResetService.java` — `clearMustChangePassword()` 호출 (auth-must-change-pw)
- `frontend/src/components/auth/AuthGuard.tsx` — `mustChangePassword` redirect

### 구현 대상

- `BETA-RELEASE.md` — 위 6개 변경

### 검증 참조

- `grep -n "2026-05-02\|29 emit\|647/647\|audit logs UI(M12)만 활성" BETA-RELEASE.md` 결과 0
- §5 4행 추가 확인 (`grep -n "auth-password-policy\|auth-must-change-pw\|admin/users\|emailExecutor" BETA-RELEASE.md`)

### 문서 반영

- 본 phase는 `BETA-RELEASE.md` 자체가 산출물.

## P2 — dev/process/ 스테일 파일 삭제

- [ ] `dev/process/a1.5-email-infra.md` 삭제
- [ ] `dev/process/auth-forgot-rate-limit.md` 삭제
- [ ] `dev/process/email-async.md` 삭제

### 작업 전 필독

- 각 파일 frontmatter `status: closed` 확인
- `working_files: []` 확인 (다른 세션 ownership 0)

### 원본 코드 참조

- `dev/completed/a1.5-email-infra/` — 동일 트랙 archive 존재 확인
- `dev/completed/auth-forgot-rate-limit/`
- `dev/completed/email-async/`

### 구현 대상

- 3개 파일 삭제

### 검증 참조

- `ls dev/process/`가 본 세션 ownership 파일(`beta-release-sync.md`)만 남음

### 문서 반영

- 별도 docs 갱신 없음 (closure summary는 dev/completed/에 보존됨)

## P3 — closure

- [ ] `docs/progress.md` 최상단에 본 트랙 closure entry 추가
- [ ] `dev/active/beta-release-sync/` → `dev/completed/beta-release-sync/` 이동
- [ ] `dev/process/beta-release-sync.md` 삭제 (본 세션 ownership)
- [ ] `git add .` → commit → push → PR

### 작업 전 필독

- 직전 트랙들의 progress.md entry 양식 (auth-password-policy 2026-05-04)

### 구현 대상

- `docs/progress.md` 추가
- `dev/active → dev/completed` 이동
- `dev/process/beta-release-sync.md` 삭제

### 검증 참조

- `gh pr checks` GREEN
- `gh pr view` mergeable=MERGEABLE

### 문서 반영

- progress.md 본 entry 자체.
