# Dev Preview Stabilization Context

**Last Updated:** 2026-05-10

## SESSION PROGRESS

### 2026-05-10 (이번 세션, bootstrap)

- PR #148 (design fidelity quick wins: G1/G5/G6/G8) squash-merged → master `50ba149`
- 디자인 fidelity 시각 검증을 위해 master 기준 `master-preview` worktree 생성, frontend dev (3001) + backend (8080) + handoff prototype static (8000) 띄움
- backend가 사용자 외부 PG (`115.21.71.140:13401`) 의 default DB `postgres` 대상으로 띄울 때 V13 migration 실패 (`folders.scope_type` NOT NULL — 기존 row 때문)
- docker `postgres:15` image의 psql로 외부 PG에 신규 DB `ibizdrive_design_preview` 생성. backend는 그쪽 대상으로 V1~V15 정상 적용 후 기동.
- backend curl 직접 호출:
  - `POST /api/auth/signup` 정상 (HTTP 201, SESSION 쿠키 발급)
  - `POST /api/auth/login` 빈 body 401 — controller 도달 전, backend log에 어떤 라인도 안 찍힘 = SecurityFilterChain 단에서 차단
  - CSRF 정상 동봉 + SESSION 없음 + 새 가입 즉시 시도 모두 동일 결과
- 사용자 브라우저에서 frontend signup/login 폼 실패 보고. frontend dev proxy 통한 curl 호출은 정상 (signup 201). 사용자 측 정확한 에러 메시지 미수집.
- 디자인 fidelity 시각 확인은 핸드오프 HTML 프로토타입 + PR #148 코드 diff로 우회 처리. 사용자 컨펌 받음.
- 이 세션 종료 전 frontend(3001) / backend(8080) / static(8000) 모두 종료 (port kill), `master-preview` worktree 제거, `ibizdrive_design_preview` DB는 외부 PG에 유지.

## Current Execution Contract

이 트랙은 **읽기/조사 전용으로 시작해서 phase별로 별도 worktree·별도 PR**로 진행한다. 한 세션이 여러 phase를 동시에 잡지 않는다.

- 각 task 시작 시 `git worktree add C:/project/IbizDrive/.claude/worktrees/<task-slug> -b <branch> origin/master` 로 isolation.
- 시작 전 `dev/process/<session-id>.md` 에 working_files 기록 (충돌 가드).
- 각 task의 acceptance criteria 충족 + 검증 게이트 통과 후 squash merge.
- destructive 액션 (DB drop 등) 은 사용자 컨펌 받고 진행.
- 외부 PG `115.21.71.140:13401` 자격은 `postgres/postgres`. 기존 `postgres` DB는 **건들지 말 것** (V13 migration 미완료, 사용자 데이터 보존).

## 현재 active task

없음 (bootstrap 직후, 다음 세션이 T1부터 픽업).

## 다음 세션 읽기 순서

1. **이 파일 (`*-context.md`)** — 어디까지 진행됐고 무엇이 발견됐는지
2. **`*-plan.md`** — phase 구조와 acceptance criteria
3. **`*-tasks.md`** — 다음에 픽업할 task와 참조 블록
4. **`docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md`** §5.4 — workspace API 계약 (T5 시드 작성에 필요)
5. **코드 (task별 참조 블록 안내대로)**

## 핵심 파일과 역할

### Backend auth chain (T1·T2 필수)

- `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java` — SecurityFilterChain, `/api/auth/login` permitAll, CSRF 설정, `HttpStatusEntryPoint(UNAUTHORIZED)`
- `backend/src/main/java/com/ibizdrive/auth/AuthController.java` L58 — `POST /api/auth/login` 엔트리
- `backend/src/main/java/com/ibizdrive/auth/AuthService.java` L86 — login 로직 (BadCredentials, AccountLocked, timing-safe dummy hash)
- `backend/src/main/java/com/ibizdrive/auth/SessionValidityFilter.java` — A1.6 absolute 8h 만료 검사. SecurityContextHolderFilter 직후. 새 SESSION엔 영향 없음.
- `backend/src/main/java/com/ibizdrive/auth/dto/LoginRequest.java`, `LoginResponse.java`
- `backend/src/main/java/com/ibizdrive/security/dbUserDetailsService` (실제 클래스 이름은 grep 필요)

### Frontend auth UX (T3 필수)

- `frontend/src/app/(auth)/login/page.tsx` — login 폼 + Suspense
- `frontend/src/app/(auth)/signup/page.tsx` — signup 폼 + client-side password 검증
- `frontend/src/app/(auth)/layout.tsx` — TopBar 미포함 미니멀 chrome
- `frontend/src/hooks/useLogin.ts`, `useSignup.ts`, `useMe.ts`
- `frontend/src/lib/password.ts` — `validatePassword`, `getPasswordRuleMessage`

### Local dev infra (T4·T6 필수)

- `backend/src/main/resources/application.yml` — base config (datasource default, flyway, session jdbc)
- `backend/src/main/resources/application-prod.yml` — prod profile (참고용)
- 신규 작성 대상: `application-local.yml`
- `frontend/next.config.ts` — `/api/*` → `localhost:8080` proxy 설정
- 신규 작성 대상: `docs/local-dev.md`

### 디자인 시드 (T5 필수)

- spec `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md` §4.5 (3-section 트리), §5.4 (workspace 계약)
- backend `WorkspaceController` (grep 필요) — 시드 직후 `/api/workspaces/me` 응답 형식
- DB schema: `users`, `departments`, `teams`, `team_members`, `folders`, `files` (V14·V15 migration)

## 중요한 의사결정

- **외부 PG `postgres` DB는 절대 clean/drop 안 함** — 사용자 데이터 보존. preview 작업은 별도 DB.
- **Preview DB 이름 고정**: `ibizdrive_design_preview`. 다른 세션도 같은 이름 사용해서 시드 충돌 방지.
- **PR 단위 분리**: T1·T2·T3·T4·T6·T5 각각 별도 PR. T4·T6만 묶을 수 있음.
- **destructive 액션 (DB drop)**: 사용자 컨펌 후 진행.
- **Login 401 디버깅 timebox 4h** — 그 안에 미규명이면 T2에서 우회안 검토.
- **사용자 자율 실행 모드 켜져 있음** (memory: feedback_autonomous_mode) — 단 destructive·외부 영향 액션은 항상 컨펌.

## 빠른 재개 안내

다음 세션이 T1을 시작한다면:

```bash
# 1. master 최신화 + worktree 생성
cd C:/project/IbizDrive
git fetch origin master --quiet
git worktree add C:/project/IbizDrive/.claude/worktrees/login-401-diagnose \
  -b feat/login-401-diagnose origin/master

cd C:/project/IbizDrive/.claude/worktrees/login-401-diagnose
# 2. 의존성 설치
cd frontend && pnpm install --prefer-offline
cd ../backend  # gradle은 wrapper 자체로 충분

# 3. preview DB 그대로 재사용 (이미 V1~V15 적용)
# backend 띄우기:
SPRING_DATASOURCE_URL="jdbc:postgresql://115.21.71.140:13401/ibizdrive_design_preview" \
SPRING_DATASOURCE_USERNAME=postgres \
SPRING_DATASOURCE_PASSWORD=postgres \
./gradlew bootRun

# 4. login 401 재현 (별 터미널)
curl -s -c /tmp/c.txt http://localhost:8080/api/auth/csrf > /dev/null
T=$(grep XSRF-TOKEN /tmp/c.txt | awk '{print $7}')
curl -i -s -b /tmp/c.txt -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $T" \
  -d '{"email":"preview@local.test","password":"PreviewPass123"}'
# → HTTP 401, Content-Length: 0
```

기존 가입된 계정 (preview DB 안에 있는 사용자):

| 이메일 | 비밀번호 | 권한 |
|---|---|---|
| `admin@local.test` | `AdminPass123` | ADMIN |
| `test2@local.test` | `TestPassword123` | MEMBER |
| `design@test.local` | `DesignPass123` | MEMBER |
| `preview@local.test` | `PreviewPass123` | MEMBER |

(전부 login 시 401)

## Backlinks

- 상위 plan: `dev-preview-stabilization-plan.md`
- 작업 분해: `dev-preview-stabilization-tasks.md`
- 선행 트랙: `dev/active/design-handoff-gap-report-2026-05-10.md` (PR #148 closure 보고 — 이 트랙의 motivation)
- 핸드오프 README: `C:/Users/dream/AppData/Local/Temp/ibiz-design/ibizdrive/project/design_handoff_ibizdrive/README.md` (디자인 spec 원본 출처)
- spec: `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md` §4.5, §5.4
