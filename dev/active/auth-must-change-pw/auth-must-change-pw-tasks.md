---
Last Updated: 2026-05-03
---

# Tasks — auth-must-change-pw

## phase별 상태

| Phase | 상태 |
|---|---|
| P1 backend change/reset clear flag | ✅ 완료 (7e8e4cc) |
| P2 frontend LoginPage redirect | ✅ 완료 (cab34c1) |
| P3 frontend AuthGuard guard | ✅ 완료 (cab34c1) |
| P4 frontend /account/password force UI | ✅ 완료 (cab34c1) |
| P5 docs sync + PR | ✅ 완료 (closure commit) |

---

## P1 — Backend: change()/reset() clears mustChangePassword

- [x] T1.1 `PasswordResetServiceTest.change_clearsMustChangePasswordFlag` 추가
- [x] T1.2 `PasswordResetServiceTest.reset_clearsMustChangePasswordFlag` 추가
- [x] T1.3 `User.clearMustChangePassword()` 메서드 추가
- [x] T1.4 `PasswordResetService.change()`에서 클리어 호출
- [x] T1.5 `PasswordResetService.reset()`에서 클리어 호출
- [x] T1.6 `./gradlew test --tests PasswordResetServiceTest` 통과
- [x] T1.7 (skip) integration은 단위테스트로 충분 — 별도 controller 테스트 추가 없이 통합 BUILD SUCCESSFUL로 확인
- [x] T1.8 `./gradlew test` 전체 BUILD SUCCESSFUL

---

## P2 — Frontend: LoginPage redirect on mustChangePassword

- [x] T2.1 `frontend/src/app/(auth)/login/page.test.tsx` 추가 (3 tests)
- [x] T2.2 LoginPage useEffect 분기 추가 (`postLoginTarget` helper)
- [x] T2.3 LoginPage onSubmit 분기 (login.mutateAsync 결과 직접 redirect)
- [x] T2.4 vitest 통과 (681/681)

---

## P3 — Frontend: AuthGuard mustChangePassword guard

- [x] T3.1 단위 테스트: force redirect (3 tests)
- [x] T3.2 단위 테스트: /account/password에서 통과
- [x] T3.3 AuthGuard useEffect에 분기 추가 (pathname 가드)
- [x] T3.4 vitest 통과

---

## P4 — Frontend: /account/password force UI

- [x] T4.1 배너 표시 테스트
- [x] T4.2 돌아가기 hide 테스트
- [x] T4.3 force 모드 → /files redirect 테스트 (5 tests)
- [x] T4.4 Suspense 경계 + force 모드 구현
- [x] T4.5 usePasswordChange onSuccess에 qk.authMe() invalidate 추가
- [x] T4.6 vitest 통과

---

## P5 — Docs sync + PR

- [x] T5.1 `docs/03-security-compliance.md` §2.7 강제 비밀번호 변경 UX 서브섹션 추가 + §2.8 운영자 초대 noteupdate
- [x] T5.2 `docs/progress.md` 본 트랙 closure entry 추가
- [x] T5.3 plan/context/tasks 최종화
- [x] T5.4 `dev/process/auth-must-change-pw.md` 삭제
- [ ] T5.5 `dev/active/auth-must-change-pw/` → `dev/completed/` 이동 (PR 머지 후 — 게이트)
- [ ] T5.6 PR 작성 (사용자 승인 후 — 게이트)
- [ ] T5.7 사용자 승인 후 master push (게이트)

### 검증 결과
- backend `./gradlew test` BUILD SUCCESSFUL
- frontend `pnpm vitest run` 681/681 passed
- typecheck + lint clean
