# MVP QA + 보안 점검 + 베타 (Week 11-12) — Context

Last Updated: 2026-05-02

## SESSION PROGRESS

### 2026-05-02 — Phase 4 완료 (G4 대기 — closure commit/PR sign-off)

- **P4.1 docs/03 §5-§10**: 빈 체크박스 23개에 운영(8)/v1.x deferred(13)/v2.x deferred(5)/구현됨(2) 마커. `§작성 우선순위` 갱신 — A2/A3 closure 표기 + Phase 3 §5 결정 인용
- **P4.2 docs/04 §3-§14**: 빈 체크박스 ~50개 마커. §6.3 orphan cleanup [x] / §8.2 cron [x] / §11.2 휴지통 복원 [x] / 나머지 v1.x. §13 cron 표에 상태 컬럼 추가 (4 MVP / 4 v1.x / 1 운영)
- **P4.3 BETA-RELEASE.md 신설**: 사내 베타 GO/NO-GO 단일 페이지. §1 코드 게이트 PASS (검증), §2-§3·§8 인프라 게이트 = 운영팀 sign-off 필요
- **P4.4 closure 준비**: `.gitignore`에 gradle 임시 5패턴, `docs/progress.md` 최상단 트랙 closure entry 추가 (회고 + 핵심 결정 7건)
- 다음 액션: G4 — 사용자 closure commit + PR/squash-merge sign-off → dev-docs `dev/completed/`로 이동

### 2026-05-02 — Phase 3 완료 (G3 통과)

- **G2 sign-off**: 사용자 A안 채택 (#1 defer, #2 MVP-fix, #3 MVP-fix)
- **Fix #1 (deferred)**: `docs/03 §5.3` 4개 체크박스 v1.x deferred 마커 + 인용 박스. `§5.4` 첫 두 항목 [x] (attachment + nosniff evidence)
- **Fix #2**: `application.yml` `server.error.{include-stacktrace,include-message,include-binding-errors}` 3줄 명시 — production stacktrace leak 차단
- **Fix #3**: `SecurityConfig.java` `.headers(h -> h.contentTypeOptions(...).frameOptions(deny).cacheControl(...))` chain 추가 — Spring Security 7+ forward-compat
- **검증**: backend `./gradlew test` 75 classes / 723 tests / 522 PASS / 201 skip / **0 fail / 0 error** — 회귀 0
- **산출**: `findings/triage-decisions.md`
- 다음 액션: Phase 4 (Beta Release Readiness — docs/03 §5-§10 + docs/04 §3-§10 본문/deferred 마커, BETA-RELEASE.md, closure PR)

### 2026-05-02 — Phase 2 완료 (G2 대기)

- **P2.1 STRIDE 매트릭스** → `findings/stride-gap-analysis.md`: 28행 매핑 — 구현됨 18 / 부분 3 / v1.x deferred 5 / 운영 책임 2
- **P2.2 audit enum**: 41 enum 중 26 emit (63%). 미사용 15건 모두 deferred 항목과 정합 (ADMIN_*/MFA/audit_level/FILE_VIEWED 등)
- **P2.3 @PreAuthorize 매핑**: 모든 mutation 엔드포인트 (File/Folder/Permission/Trash/Share) 보호 — 미보호 0
- **P2.4 핵심 원칙 11개** → `findings/principle-conformance.md`: 8 PASS / 3 PARTIAL (frontend 3/4/5 수동) / 0 FAIL
- **MVP-blocker 후보 3건 식별** (Phase 3 G2 sign-off 대상):
  1. 확장자 화이트리스트 — *추천*: v1.x deferred (사내 베타 + Content-Disposition: attachment 1차 방어)
  2. `server.error.include-stacktrace` 명시 — *추천*: MVP-fix `application.yml` 1줄 (`never` + `on-param`)
  3. Spring Security `.headers()` 명시화 — *추천*: MVP-fix 5-10 LOC (Spring Security 7+ 호환성 보호)
- 다음 액션: G2 사용자 sign-off → Phase 3 (Triage + Remediation)

### 2026-05-02 — Bootstrap + Phase 1 완료

- 트랙 디렉터리 생성: `dev/active/mvp-qa-security-week-11-12/`
- 3파일 (plan/context/tasks) 작성
- 세션 파일: `dev/process/mvp-qa-security-2026-05-02.md`
- baseline 코드 확인: master HEAD `90274c7`, ADR #38까지 closure 완료
- **Phase 1 완료 (G1 통과)**:
  - 백엔드 75 classes / 723 tests / 522 executed PASS / 201 skipped (Docker 미가용 IT) / 0 fail
  - 프론트 563/563 GREEN, typecheck/lint/build 모두 GREEN (build 43s)
  - ADR 인덱스 (`findings/adr-index.md`): 38 active + 2 superseded. Phase 2 finding 후보 3건
  - 빈 체크박스 인벤토리 (`findings/empty-checkbox-inventory.md`): ~12 MVP / ~20 운영 / ~50 v1.x. Phase 2 검증 후보 8건
  - baseline 보고 (`findings/baseline-report.md`)
- 다음 액션: Phase 2 (P2.1 STRIDE 매트릭스 매핑) 진입 가능

## Current Execution Contract

- 본 트랙은 **신규 기능 추가 금지**. (a) 인벤토리, (b) gap 분석, (c) 트리아지 후 MVP-fix만 코드 변경, (d) 나머지는 docs sync 또는 deferred 마커로 closure.
- destructive 액션은 게이트: master push, gh pr merge, `.gitignore` 외의 untracked 파일 삭제는 사용자 승인 후.
- Phase 2 종료 시점 (G2) finding 트리아지는 사용자 sign-off 필요.
- 본 트랙은 worktree 신설 없이 master 작업 디렉터리에서 dev-docs 작성 + Phase 1/2 분석 진행. Phase 3 MVP-fix 시점에 (필요하면) feature 브랜치 worktree 분기.
- 자율 모드: dev-docs 작성/갱신, baseline 명령 실행, gap 분석 grep, docs/03·04 inline 마커 추가는 자동. 새 ADR 작성 / 코드 수정 / DB 마이그레이션은 트리아지 후 sign-off.

## 현재 active

- Phase: **4 완료 — G4 대기 (closure commit/PR sign-off)**
- 다음 액션: 사용자 sign-off 후 closure commit (master 직접) + dev-docs `dev/completed/`로 이동

## 다음 세션 읽기 순서

1. `dev/active/mvp-qa-security-week-11-12/mvp-qa-security-week-11-12-plan.md` — 트랙 전체 phase 지도
2. 본 파일 (`-context.md`) — SESSION PROGRESS 최신 항목
3. `mvp-qa-security-week-11-12-tasks.md` — 다음 미완료 task의 참조 블록
4. (Phase 2 진입 후) `findings/baseline-report.md` — Phase 1 산출
5. `docs/03-security-compliance.md` §1.3 STRIDE 매트릭스 (Phase 2 활성화 시)
6. `CLAUDE.md §3` 핵심 원칙 11개 (Phase 2 활성화 시)

## 핵심 파일과 역할

### Plan/track 파일

- `dev/active/mvp-qa-security-week-11-12/mvp-qa-security-week-11-12-plan.md` — phase 지도, acceptance criteria, 검증 게이트
- `dev/active/mvp-qa-security-week-11-12/mvp-qa-security-week-11-12-tasks.md` — phase별 체크박스 + 참조 블록
- `dev/active/mvp-qa-security-week-11-12/findings/` — Phase 1/2 산출 (baseline, gap-analysis, principle-conformance)

### 분석 대상 docs (read-only Phase 1/2)

- `docs/00-overview.md` — ADR #1~#38, §4.1 phase 정의
- `docs/01-frontend-design.md` — §1·§19 핵심 원칙(프론트 5개), §2~§17 구현
- `docs/02-backend-data-model.md` — §3 정규화, §6 트랜잭션, §7 API 스펙, §8 에러 코드
- `docs/03-security-compliance.md` — §1 STRIDE, §2 인증, §3 권한, §4 감사, §5~§10 (스켈레톤)
- `docs/04-admin-operations.md` — §1 역할, §2 페이지 트리, §13 배치 작업, §3~§10 (스켈레톤)
- `CLAUDE.md` — §3 핵심 원칙 11개

### 구현 코드 (gap 분석 대상)

- `backend/src/main/java/com/ibizdrive/` — `audit/auth/common/department/file/folder/permission/purge/search/share/storage/trash/user`
- `backend/src/main/resources/db/migration/V1__*.sql ~ V7__*.sql`
- `frontend/src/components/` — `audit/dnd/files/folders/shares/statusbar/storage/topbar/trash/upload`
- `frontend/src/lib/` — `api.ts / queryKeys.ts / normalize.ts / errors.ts / folderPath.ts`
- `frontend/src/types/` — `permission.ts / audit.ts / share.ts / department.ts`

## 중요한 의사결정 (본 트랙 시작 시점 기준)

1. **베타 = 사내 베타 (외부 일반 출시 X)**. SSO/MFA/SAST/외부 모의해킹은 모두 v1.x 또는 운영 시점 deferred.
2. **신규 ADR 작성은 최소화**. deferred 결정은 docs inline 마커 + progress entry. ADR은 본문 변경이 동반될 때만 (#39부터 사용 가능).
3. **본 트랙 = master 작업 디렉터리 + dev-docs 우선**. Phase 3에서 MVP-fix 발견 시점에 별도 worktree 분기 (현재 master 위 직접 commit은 closure commit만).
4. **`.gradle-user-home*` 등 untracked**는 Phase 1에서 정리. 단순 .gitignore 추가가 1차, 물리 삭제는 사용자 확인.
5. **`dev/process/a10-shares-2026-05-01.md` deleted**는 staging만 하고 본 트랙 closure commit에 흡수.

## 연관 closure 트랙 (참조 가능)

- `dev/completed/a1-auth-impl/` — 인증 (§2)
- `dev/completed/a2-audit-log/` — 감사 (§4)
- `dev/completed/a3-permission-matrix/` — 권한 (§3)
- `dev/completed/a4-folder-file-domain/` 외 5개 — A4 분할 트랙
- `dev/completed/a5-file-versions/` — 버전 schema
- `dev/completed/a6-folder-mutation-delete/`, `a7-hard-purge/` — soft/hard delete
- `dev/completed/a10-shares/`, `a12-folder-shares-endpoint/`, `a13-shares-permissions-join/` — 공유
- `dev/completed/a11-effective-permissions-endpoint/` — effective permission
- `dev/completed/a14-user-search/`, `a16-department-subject-picker/` — subject picker
- `dev/completed/a15-file-upload-download/` — 업로드/다운로드 (§5.4 일부 구현)

## 빠른 재개

```bash
# 1. 작업 디렉터리 진입
cd C:/project/IbizDrive

# 2. 트랙 dev-docs 재로드
# (Claude 세션이 plan → tasks → context 순으로 읽음)

# 3. 활성 task 확인 후 진행
#    Phase 1 시작이면 → backend ./gradlew test 베이스라인 측정부터
#    Phase 2 시작이면 → findings/baseline-report.md 확인 후 STRIDE 매핑
```

## 변경 이력

- 2026-05-02 bootstrap (3파일 신설, 세션 파일 생성, master `90274c7` 시점)
