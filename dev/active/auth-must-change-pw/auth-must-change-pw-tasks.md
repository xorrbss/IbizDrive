---
Last Updated: 2026-05-02
---

# Tasks — auth-must-change-pw

## phase별 상태

| Phase | 상태 |
|---|---|
| P1 backend change/reset clear flag | 진행 예정 |
| P2 frontend LoginPage redirect | 대기 |
| P3 frontend AuthGuard guard | 대기 |
| P4 frontend /account/password force UI | 대기 |
| P5 docs sync + PR | 대기 |

---

## P1 — Backend: change()/reset() clears mustChangePassword

- [ ] T1.1 `PasswordResetServiceTest.change_clearsMustChangePassword` 테스트 추가 (빨강)
- [ ] T1.2 `PasswordResetServiceTest.reset_clearsMustChangePassword` 테스트 추가 (빨강)
- [ ] T1.3 `User.clearMustChangePassword()` 추가 (또는 `changePasswordHash` 내부에서 클리어)
- [ ] T1.4 `PasswordResetService.change()`에서 클리어 호출
- [ ] T1.5 `PasswordResetService.reset()`에서 클리어 호출
- [ ] T1.6 `./gradlew test --tests PasswordResetServiceTest` 통과
- [ ] T1.7 `PasswordControllerChangeTest`에 integration 케이스 추가 (200 후 me 호출 → mustChangePassword=false)
- [ ] T1.8 `./gradlew test --tests *PasswordController*` 전체 통과

### 작업 전 필독
- `dev/active/auth-must-change-pw/auth-must-change-pw-plan.md` P1 섹션
- `docs/03-security-compliance.md` §2.8 (ADR #21)

### 원본 코드 참조
- `backend/src/main/java/com/ibizdrive/auth/password/PasswordResetService.java` L154-225 (reset, change)
- `backend/src/main/java/com/ibizdrive/user/User.java` L73, L197 (mustChangePassword field, changePasswordHash)
- `backend/src/test/java/com/ibizdrive/auth/password/PasswordResetServiceTest.java` (기존 테스트 패턴)

### 구현 대상
- `User.java`에 `clearMustChangePassword()` 메서드 (boolean 직접 setter 노출 금지 — 의미 있는 mutator)
- `PasswordResetService.change()` save 직전 clearMustChangePassword 호출
- `PasswordResetService.reset()` save 직전 동일 호출
- 테스트 2건 + integration 1건

### 검증 참조
- `./gradlew test --tests PasswordResetServiceTest`
- `./gradlew test --tests *PasswordController*Test`

### 문서 반영
- 본 phase에서는 docs 변경 없음 (P5에 일괄)

---

## P2 — Frontend: LoginPage redirect on mustChangePassword

- [ ] T2.1 `frontend/src/app/(auth)/login/__tests__/page.test.tsx` (또는 가까운 테스트 위치) 단위 테스트 추가: mustChangePassword=true 시 `/account/password?force=1`로 router.replace
- [ ] T2.2 LoginPage useEffect 분기 추가 (`me.data?.user.mustChangePassword`)
- [ ] T2.3 LoginPage onSubmit 분기 추가 (login.mutateAsync 결과 → useMe refetch → 동일 useEffect가 처리)
- [ ] T2.4 `pnpm test login` 통과

### 작업 전 필독
- plan P2
- `frontend/src/app/(auth)/login/page.tsx` 현재 구현
- `frontend/src/types/auth.ts` AuthSession.user.mustChangePassword

### 원본 코드 참조
- `frontend/src/app/(auth)/login/page.tsx`
- `frontend/src/hooks/useMe.ts`, `useLogin.ts`

### 구현 대상
- LoginPage useEffect와 onSubmit에서 `mustChangePassword` 분기

### 검증 참조
- `pnpm test login`
- `pnpm typecheck && pnpm lint`

### 문서 반영
- (P5에 일괄)

---

## P3 — Frontend: AuthGuard mustChangePassword guard

- [ ] T3.1 단위 테스트: me.data.user.mustChangePassword=true && pathname !== '/account/password' → redirect
- [ ] T3.2 단위 테스트: pathname === '/account/password' 면 통과
- [ ] T3.3 AuthGuard useEffect에 분기 추가
- [ ] T3.4 `pnpm test AuthGuard` 통과

### 원본 코드 참조
- `frontend/src/components/auth/AuthGuard.tsx`

### 구현 대상
- AuthGuard useEffect 내부에서 data!=null 케이스에 mustChangePassword 분기

### 검증 참조
- `pnpm test AuthGuard`

---

## P4 — Frontend: /account/password force UI

- [ ] T4.1 단위 테스트: `?force=1` 또는 me.user.mustChangePassword=true 시 배너 표시
- [ ] T4.2 단위 테스트: 같은 조건에서 "돌아가기" 버튼 hide
- [ ] T4.3 단위 테스트: 변경 성공 후 router.replace('/files')
- [ ] T4.4 페이지 구현 + Suspense 경계 (useSearchParams)
- [ ] T4.5 usePasswordChange onSuccess가 qk.authMe() invalidate 하는지 확인 → 없으면 추가
- [ ] T4.6 `pnpm test account/password` 통과

### 원본 코드 참조
- `frontend/src/app/(explorer)/account/password/page.tsx`
- `frontend/src/hooks/usePasswordChange.ts`

### 구현 대상
- ChangePasswordPage에 force 모드 분기 + 배너 + 돌아가기 hide + 성공 redirect
- (조건부) usePasswordChange onSuccess에 useMe invalidate

---

## P5 — Docs sync + PR

- [ ] T5.1 `docs/03-security-compliance.md` §2.8 ADR #21 잔여 4개(login redirect, useMe hook, frontend change form, mustChangePassword UX) closure 표기 + UX flow 다이어그램 보강
- [ ] T5.2 `docs/progress.md` 본 트랙 entry 추가 (audit emit coverage 31/42 유지, ADR #21 closure 표기)
- [ ] T5.3 `dev-docs-update`로 plan/context/tasks 최종화
- [ ] T5.4 `dev/process/auth-must-change-pw.md` 삭제
- [ ] T5.5 `dev/active/auth-must-change-pw/` → `dev/completed/`로 이동 (PR 머지 후)
- [ ] T5.6 PR 작성 — `feat(auth-must-change-pw): mustChangePassword UX enforcement (ADR #21 closure)`
- [ ] T5.7 사용자 승인 후 master push (게이트)

### 검증 참조
- `pnpm typecheck && pnpm lint && pnpm test` (frontend)
- `./gradlew test` (backend)
- `git diff origin/master --stat` 검토
