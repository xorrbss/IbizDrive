# Dev Preview Stabilization Plan

**Last Updated:** 2026-05-10

## 요약

로컬·외부 PG 환경에서 IbizDrive frontend·backend를 띄울 때 사용자 브라우저에서 **회원가입/로그인이 동작하지 않아 디자인 live preview를 확인할 수 없는 상태**다. 이 문제를 일으키는 4가지 별도 이슈를 분리하여, 다른 세션이 독립적으로 picking할 수 있는 task로 정리하고 차례로 해결한다.

## 왜 bootstrap이 필요한가

- 디자인 fidelity gap을 master에 머지(PR #148)했지만 **live preview로 검증 못 함** — 코드 diff와 CI 통과만 증거.
- backend `/api/auth/login` 호출 시 controller 도달 전에 빈 body 401이 반환됨. 원인 미규명. CSRF 정상 동봉 + SESSION 없음 + 새 가입 직후 모두 동일 결과.
- backend bootRun이 application.yml의 hardcoded dev 자격에 의존 → 다른 PG 환경에서 매번 환경변수 override 필요. profile 분리 부재.
- 사용자 브라우저의 frontend signup 폼이 실패한다고 보고됨 (정확한 에러 메시지 미수집). curl 직접 호출은 성공.
- `dev preview`를 다음에도 띄울 때 같은 핑퐁을 반복하지 않으려면 절차 문서화·자동화가 필요.

## 현재 상태 분석

### 인프라

| 컴포넌트 | 상태 | 비고 |
|---|---|---|
| `frontend/` Next.js dev | 가동 가능 | `pnpm dev` → port 3001 (기본 3000 점유 시 자동 시프트) |
| `backend/` Spring Boot | 가동 가능 (조건부) | `./gradlew bootRun` — 외부 PG 자격 환경변수 override 필요 |
| 외부 PG | `115.21.71.140:13401` (사용자 제공) | postgres/postgres |
| 기본 DB `postgres` | V13 migration 미완료 (`folders.scope_type` NOT NULL fail) | **건들지 말 것**. 사용자 데이터 보존. |
| 신규 DB `ibizdrive_design_preview` | V1~V15 migration 적용 완료 | docker `postgres:15` image의 psql로 `CREATE DATABASE`. 빈 schema. |

### 발견된 결함

- **F1. Login 401 (controller 도달 전)** — `/api/auth/login`은 SecurityConfig L137 `permitAll`인데 빈 body 401. backend log에 어떤 라인도 안 찍힘 = SecurityFilterChain 단에서 차단되는 것으로 추정. 새 가입 즉시 login 시도 / SESSION 없음 / CSRF 정상 동봉 모두 동일 결과. signup·csrf endpoint는 정상.
- **F2. Frontend 폼 측 미동작** — 사용자 브라우저에서 signup/login 폼 실패 보고. frontend dev proxy 통한 curl 호출은 signup 정상 (HTTP 201). 차이 미규명 (client-side validation, dev tool 캐시, browser cookie 처리 등 후보).
- **F3. Backend local profile 부재** — `application-local.yml`이 없음. dev 자격이 `application.yml` default에 박혀 있어 다른 PG 환경에서는 매번 환경변수로 override. 사용자 가이드도 부재.
- **F4. Preview 시드 데이터 부재** — 새 DB는 비어 있어 부서/팀/파일이 없음. workspace API가 빈 응답을 반환해도 frontend가 적절한 onboarding 또는 빈 상태 UI를 보여주지 않을 수 있음 (구체적 분기 미확인).

## 목표 상태

- T1·T2 후: 새 DB + 가입 한 명의 계정으로 `/login` 정상 진입.
- T3 후: 사용자 브라우저 시뮬레이션(Playwright)으로 signup→login→/files 진입 e2e 통과.
- T4 후: `./gradlew bootRun -Plocal` 또는 `bootRun` + `application-local.yml` 만으로 dev DB 자격이 자동 적용됨.
- T5 후: 시드 SQL 또는 admin UI flow 1개로 부서·팀·파일이 들어가 `/files`에서 G1/G5/G6/G8 디자인 fidelity 시각 확인 가능.
- T6 후: 다른 세션 작업자가 `docs/local-dev.md` 한 문서만 읽고 dev preview 띄우기 가능.

## Phase별 실행 지도

| Phase | 목표 | Tasks | 의존성 | 병렬성 |
|---|---|---|---|---|
| 1. Login 401 진단·수정 | F1 해결 | T1, T2 | (없음) | T1→T2 시퀀셜 |
| 2. Frontend auth UX 점검 | F2 해결 | T3 | Phase 1 완료 후 retest | 다른 phase와 독립 |
| 3. Local dev 인프라 | F3 해결 + 문서화 | T4, T6 | (없음) | Phase 1·2와 병렬 가능 |
| 4. Preview 시드 | F4 해결 (디자인 fidelity 시각 검증) | T5 | Phase 1 완료 필수 | 마지막 |

각 phase·task는 **자체 worktree + 자체 PR**로 분리 가능. T4와 T6은 같은 worktree에서 묶어도 무방 (둘 다 docs/config 영역).

## Acceptance Criteria

- 새 worktree에서 `pnpm install && ./gradlew bootRun` (with documented profile flag) 만으로 backend가 live PG 대상 정상 기동.
- 새 worktree의 frontend `pnpm dev` 가 backend proxy 정상 (signup 201, login 200, me 200).
- 사용자 브라우저에서 신규 가입 → `/files` 자동 redirect → TopBar 보임 (G1 시각 확인 가능).
- 4 variants (linear/notion/dropbox/terminal) TweaksPanel 토글 시 row 높이 자동 추종 (G5 확인).
- 시드 데이터 1세트로 FileTable에 row가 표시되어 G5/G6/G8 시각 확인 가능.
- `docs/local-dev.md` 의 절차만 따라 다른 세션이 30분 내에 preview 환경 재현 가능.

## 검증 게이트

각 task의 PR이 master에 머지되기 전:

- `pnpm typecheck && pnpm lint && pnpm test` (frontend)
- `./gradlew test` (backend) — 신규 변경분에 대한 unit test 포함
- T2 (login 401 fix) 는 필수로 backend integration test (실 PG 또는 Testcontainers PG) 추가
- T3 는 Playwright e2e 로 signup→login→/files 흐름 covering

## 리스크와 완화

| 리스크 | 영향 | 완화 |
|---|---|---|
| F1 원인이 master 코드의 잠재 regression | T1·T2 길어짐 | Spring `DEBUG` log enable + `git bisect` 후보 시퀀스 (Plan A2/D 머지 commit들) |
| 외부 PG가 다른 세션 작업 중 또 V13 같은 migration 충돌 | T5 막힘 | Preview 전용 DB 이름 고정 (`ibizdrive_design_preview`) + 시드 idempotent SQL |
| Playwright 미설치 (`playwright.config.ts`만 존재 가능) | T3 시작 시 setup 비용 | Phase 2 시작 시 첫 확인 항목, 미설치면 `pnpm exec playwright install` |
| 동시 세션이 같은 DB에 동시 시드 | 충돌 | T5 SQL을 `ON CONFLICT DO NOTHING` + 고정 UUID로 idempotent |
| login 401 debug에 시간 너무 길어짐 | 다른 phase 블록 | T1에 timebox 4h. 그 안에 미규명이면 T2 우회안 (e.g. signup 자동 로그인 강화) 검토 |
