---
Last Updated: 2026-05-02
---

# Context — auth-must-change-pw

## SESSION PROGRESS

- 2026-05-02 세션 0: dev-docs bootstrap, 컨텍스트 수집, 범위 고정. 새 브랜치 `wip/auth-must-change-pw` 생성 (origin/master fdb57c7).

## Current Execution Contract

- 자율 실행 모드 (memory: feedback_autonomous_mode). 머지/master push/force push만 게이트.
- TDD 우선: 빨강 → 초록 → 리팩토링.
- 컨텍스트 70/75/80% 임계값에 핸드오프 박스 출력.
- 백엔드 작업 후 `./gradlew test`, 프론트 작업 후 `pnpm test/typecheck/lint`.
- ADR #21이 본 트랙의 source of truth — `docs/03-security-compliance.md` §2.8.

## 현재 active task

P1 — Backend: PasswordResetService change()/reset()에서 mustChangePassword 클리어 (TDD)

## 다음 세션 읽기 순서

1. `auth-must-change-pw-plan.md` (Phase 표 + Acceptance Criteria)
2. `auth-must-change-pw-tasks.md` (현재 phase의 미완료 체크박스)
3. `auth-must-change-pw-context.md` 이 문서 (직전 결정/blocker)
4. 코드 진입점:
   - `backend/src/main/java/com/ibizdrive/auth/password/PasswordResetService.java` (change L203, reset L154)
   - `backend/src/test/java/com/ibizdrive/auth/password/PasswordResetServiceTest.java`
   - `frontend/src/app/(auth)/login/page.tsx`
   - `frontend/src/components/auth/AuthGuard.tsx`
   - `frontend/src/app/(explorer)/account/password/page.tsx`
   - `frontend/src/hooks/useMe.ts`, `usePasswordChange.ts`
5. 참고 문서: `docs/03-security-compliance.md` §2.8 (ADR #21), `docs/00-overview.md` ADR 색인

## 핵심 파일과 역할

| 파일 | 역할 | 본 트랙에서 변경? |
|---|---|---|
| `backend/.../user/User.java` | mustChangePassword field + mutator 추가 (`clearMustChangePassword`) | ✓ |
| `backend/.../auth/password/PasswordResetService.java` | change()/reset()에서 clear 호출 | ✓ |
| `backend/.../auth/password/PasswordResetServiceTest.java` | 새 테스트 2건 | ✓ |
| `backend/.../auth/password/PasswordControllerChangeTest.java` | integration: 200 후 me=false 검증 | ✓ |
| `frontend/src/app/(auth)/login/page.tsx` | mustChangePassword 분기 redirect | ✓ |
| `frontend/src/components/auth/AuthGuard.tsx` | mustChangePassword 분기 redirect (단, /account/password는 통과) | ✓ |
| `frontend/src/app/(explorer)/account/password/page.tsx` | force 배너 + 돌아가기 hide + 성공 후 자동 /files | ✓ |
| `frontend/src/hooks/useMe.ts` | (변경 없음) — 이미 user.mustChangePassword 노출 | - |
| `frontend/src/hooks/usePasswordChange.ts` | onSuccess에 qk.authMe() invalidate가 있는지 확인 → 없으면 추가 | 조건부 ✓ |
| `docs/03-security-compliance.md` | §2.8 ADR #21 잔여 4개 closure 표기 | ✓ |
| `docs/progress.md` | 트랙 entry 추가 | ✓ |

## 중요한 의사결정

- **change()와 reset() 둘 다 클리어**: reset()도 자발적 분실 흐름이 있지만, true→false는 안전. ADR #21 §2.8 "사용자가 reset link로 PW 설정 → mustChangePassword=false 처리" 명시와 일치.
- **force 표시는 query param `?force=1`이 아닌 `me.user.mustChangePassword`를 진실의 출처로**: query param은 share/refresh 시 stale 가능. flag는 항상 신선 (useMe staleTime 60s + invalidate). query param은 LoginPage가 force redirect를 쓸 때 명시적 표시 용도로만 사용.
- **AuthGuard에서 `pathname === '/account/password'` 예외**: window.location.pathname 사용 (기존 패턴 유지). `/account/password` 외 모든 explorer 경로는 force redirect.
- **변경 성공 후 자동 /files redirect**: 사용자가 force 모드로 진입한 케이스만. usePasswordChange가 이미 useMe invalidate를 한다면 AuthGuard가 자연스럽게 unblock. force 모드 페이지에서는 추가로 router.replace('/files') 호출.
- **백엔드 audit 추가 안 함**: 이미 USER_PASSWORD_CHANGED/RESET이 emit. 플래그 변화에 별도 이벤트 불필요.

## 빠른 재개 안내

```text
1) git status; git log --oneline -3   # 어디까지 커밋했는지
2) cat dev/active/auth-must-change-pw/auth-must-change-pw-tasks.md  # 미완료 체크박스
3) (P1 미완) ./gradlew test --tests PasswordResetServiceTest
   (P2 미완) cd frontend && pnpm test login
   ...
4) 단계 완료 시 tasks.md 체크박스 업데이트 + dev-docs-update
```

## blocker

(없음)
