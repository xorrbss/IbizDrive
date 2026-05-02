## auth-pages — Context

Last Updated: 2026-05-02

### SESSION PROGRESS

- [2026-05-02] 부트스트랩: dev-docs 3파일 생성, worktree `wip/auth-pages` (master fb07a56 기반).
- [2026-05-02] 진행 중: P1 (백엔드 signup TDD).

### Current Execution Contract

- 자율 실행 모드 활성 (`feedback_autonomous_mode.md`).
- TDD 사이클: RED → GREEN → REFACTOR. 각 sub-unit 1 commit.
- spec/계약 변경: `AuditEventType` enum 추가 → `docs/03 §4` 동기화.
- ADR #41 작성 + ADR #18 supersede 표기는 P5 closure에서 일괄.

### 현재 active task

P1 — 백엔드 signup endpoint (SignupService TDD).

### 다음 세션 읽기 순서

1. `auth-pages-plan.md` (acceptance criteria, phase 진행)
2. `auth-pages-tasks.md` (체크박스 + 참조 블록)
3. `backend/src/main/java/com/ibizdrive/auth/AuthService.java` (login 흐름 — 세션 발급 패턴 참고)
4. `backend/src/main/java/com/ibizdrive/user/User.java` (생성자 시그니처)
5. `backend/src/test/java/com/ibizdrive/auth/AuthServiceTest.java` (테스트 패턴)
6. `frontend/src/lib/api.ts` (api 함수 추가 위치)
7. `frontend/src/app/(explorer)/layout.tsx` (401 guard 추가 지점)

### 핵심 파일과 역할

| 파일 | 역할 | 변경 |
|---|---|---|
| `backend/.../auth/AuthController.java` | login/me/logout endpoint | + `signup()` 핸들러 |
| `backend/.../auth/AuthService.java` | 인증 흐름 + 세션 발급 | + `establishSession(user, req, res)` 추출 |
| `backend/.../auth/SignupService.java` | NEW — 가입 트랜잭션 | 신규 |
| `backend/.../auth/dto/SignupRequest.java` | NEW — DTO | 신규 |
| `backend/.../audit/AuditEventType.java` | 감사 이벤트 enum | + `USER_REGISTERED` |
| `backend/.../config/SecurityConfig.java` | 인증/인가 라우팅 | + `/api/auth/signup` permitAll + CSRF ignore |
| `frontend/src/lib/api.ts` | API client | + login/logout/me/signup |
| `frontend/src/app/(auth)/login/page.tsx` | NEW | 신규 |
| `frontend/src/app/(auth)/signup/page.tsx` | NEW | 신규 |
| `frontend/src/app/(explorer)/layout.tsx` | 탐색기 레이아웃 | + 401 guard + logout |
| `docs/00-overview.md` | ADR | + #41 (supersede #18) |
| `docs/03-security-compliance.md` | 인증/감사 | §2 회원가입 흐름 + §4 USER_REGISTERED |
| `BETA-RELEASE.md` | MVP 상태 | auth 섹션 |

### 중요한 의사결정

1. **자율 실행 모드 적용**: 사용자 명시("물어보지 말고 자동 실행"). 게이트(force-push, master 직접 push, gh pr merge)는 유지.
2. **Self-signup 채택, ADR #18 supersede**: 사내 베타 단계에서 admin invite 흐름은 운영 부담 + 사용자 진입장벽. 첫 user ADMIN으로 부트스트랩.
3. **이메일 인증 생략**: KISS. 사내 베타 가정. 향후 SSO 도입 시 별도 트랙.
4. **회원가입 직후 자동 로그인**: UX 일관성. `AuthService.establishSession` 재사용.

### 빠른 재개 안내

```bash
cd C:/project/IbizDrive/.claude/worktrees/auth-pages
# tasks.md의 체크되지 않은 첫 항목으로 진입
```
