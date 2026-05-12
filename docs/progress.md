# 진행 상황 (Progress Log)

> 각 세션이 완료될 때마다 **최상단에 추가**합니다. 기존 내용은 보존.
> 양식: `CLAUDE.md §7` 또는 각 BRIEF의 회고 섹션 참조.

---

## 2026-05-12 — 🧹 tier0-drift-sweep (v1x-backlog stale entry 2건 + §7.12 deprecation marker, PR #TBD)

### 범위

v1.0.0-beta 출시 직후 다음 트랙 결정 state-check 단계에서 `v1x-backlog.md`에 stale entry 2건 발견. 단일 PR로 closure mark + 잔여 spec drift(§7.12 ghost endpoint 3건) deprecation marker.

### 발견

1. **Admin Grant Phase C/D (Tier 1 #48)** — backlog `Last Updated: 2026-05-11`에도 불구하고 entry는 PR #163 머지(2026-05-11 grant-permission-dialog Phase C+D) 반영 누락. `feedback_state_check_first.md` 메모리가 가리킨 함정 — backlog만 보고 트랙 진입했으면 closed 트랙 재구현 위험.
2. **docs drift entry (Tier 0 #31)** — (a) `docs/02 §2.12 trash_policy`는 이미 line 495에 schema entry 완비 — drift check 자체가 stale. (b) `/api/admin/download-logs` `/api/admin/permission-logs` `/api/admin/storage-usage` 3건은 backend (controller / service) 0건 + frontend hook 0건 = never-implemented ghost. AdminAudit + AdminStorage + `/api/admin/dashboard/summary`로 정보 통합 제공.

### 변경 (docs only, 4 파일)

- `docs/v1x-backlog.md`
  - Tier 0 #31 `~~docs drift~~` closure mark (a 조건 stale + b 조건 본 PR + c 조건 정합 OK)
  - Tier 1 #48 `~~Admin Grant Phase C/D~~` closure mark (PR #163 + #193 ref + ROLE/TEAM v2.x 분리 표기)
  - Last Updated 2026-05-12
- `docs/02-backend-data-model.md` §7.12 — table 직후 deprecation note blockquote (3 ghost endpoint 명시 + AdminAudit/AdminStorage/dashboard summary 대체 매핑 + 부활 조건)
- `docs/01-frontend-design.md` §16.2 — admin 페이지 4 라우트 중 3건 inline marker(`[v1.x 미구현 — AdminAudit ... filter로 대체]`) + 동일 deprecation note blockquote (§7.12 동기 backlink)
- `docs/progress.md` — 본 closure entry

### 결정/편차

- **deprecation marker 채택 (wire spec 보강 X)** — backend 0건 상태에서 wire spec 작성은 YAGNI 위반. row 제거는 `docs/01 §16.2`까지 sync 확대 → KISS 위반. blockquote marker가 가장 가벼움.
- **row 자체는 §7.12 table에 보존** — 운영자가 endpoint path 추적 시 마지막 결정 출처 보존. row 제거하면 "왜 없지?" → 또 다른 drift 유발.
- **PR # TBD** — closure entry 안에 본 PR # 미확정. PR open 후 followup commit으로 정정 또는 다음 트랙 closure에서 함께 정정.

### 검증

- 코드 0줄 — `pnpm typecheck`/`pnpm lint`/`pnpm test` 무관.
- `git diff --stat` 결과: 4 파일 (`docs/01-frontend-design.md`, `docs/02-backend-data-model.md`, `docs/v1x-backlog.md`, `docs/progress.md`).
- 회귀 영향: 0 — docs only.

### 다음 세션 컨텍스트

- **v1x-backlog 정합성 회복** — 다음 트랙 부트스트랩 시 stale entry 함정 제거.
- **잔여 Tier 1 후보** (blocker 0): Quota Phase 4~5 (worktree `quota-phase4` locked → co-session 진행 가능성, 확인 필요), 2인 승인 framework (L), 잔여 admin 페이지 admin-grid 재구성 (M).
- **Housekeeping 잔여**: `dev/active/design-fidelity-sweep/` archive, `quota-phase3` worktree 제거 (PR #198 머지됨), `v1.0.0-beta` 태그 push (사용자 게이트).

---

## 2026-05-12 — 🎨 design-fidelity-sweep (디자인 zip 메인 탐색기 + Admin Console 전체 반영, PR #199 + #200)

### 범위

`IbizDrive_design.zip` (2026-05-10 export) 기준 frontend 전체 visual fidelity sweep. v1.0.0-beta 출시 직전 사용자 회귀에서 "디자인 미반영" 발견 → 3-phase 분할 sweep (Phase 1 토큰 / Phase 2 메인 탐색기 / Phase 3 Admin Console).

### 진행

- **Phase 1 — Tokens**: closure (globals.css `:root` 27개 + `[data-theme="dark"]` 16개 + variants(notion/dropbox/terminal) + density 모두 이미 정합. G1~G8 누적 효과)
- **Phase 2 — 메인 탐색기** (PR #199, 8 commits): Breadcrumb fidelity / FileRow star·lock·share·items 배지 + Sidebar NewButton + storage bar 재정렬 / `@theme inline` `--color-accent-fg` 매핑 fix(40+ 파일 영향) / VersionTab 좌측 dot+라인 타임라인 / FileTable/GridView hover·selected·opened(`box-shadow: inset 2px 0 0 var(--accent)`) / `--row-h` 일관성(TrashTable/SharesTable 정정) / `text-accent-text` → `text-accent-fg` 통일 / modalBg+modalPop+bulkSlide 키프레임 + `prefers-reduced-motion` 가드
- **Phase 3 — Admin Console** (PR #200, 10 commits): AdminSharing 전체 신규(policies/domains/flagged 3 섹션, mock) / SectionCard 공통 추출 / Overview UploadChart(28일 SVG)+FlagRow+DeptRow+audit-mini(useAuditLogs 재사용) / Storage CleanupList / Retention LegalHoldList(v2.x mock) / Audit SeverityTabs+AuditStream(실 backend + frontend severity 매핑) / AdminTopHeader header-left·right + sharing badge / DashboardKpiCard delta·tone·progress props + `.kpi-card` 클래스 wiring / `.btn-*` text-decoration:none + inline style 제거

### 결정/편차

- **variants**: base `linear`만 채택. notion/dropbox/terminal은 globals.css 정의 보존, 토글 UI 미마운트 (v1.x backlog)
- **mobile**: CLAUDE.md §3 원칙 13 폐기 유지
- **backend endpoint 호출 0건 신규** — AdminSharing/LegalHold mock, AdminAudit는 기존 `useAuditLogs` 재사용
- **AuditTable.tsx 보존** (페이지에서 AuditStream으로 swap, 컴포넌트 자체 제거 안 함 — KISS)
- **신규 CSS 최소화** — admin.css에 클래스 이미 포팅됨, Phase 3는 wiring만(boilerplate `.btn` text-decoration 1줄 외 0)

### 검증

- `pnpm typecheck && pnpm lint`: PASS (각 sub-phase 게이트)
- CI green (PR #199 / #200 모두 머지 통과)
- 회귀 세션이 master로 sync하면 디자인 반영 확인 가능

### 다음 세션 컨텍스트 (closure follow-ups)

- **v1.x backlog 잔여**: 잔여 admin 페이지 admin-grid 재구성 / audit severity backend 컬럼 / DashboardKpiCard delta backend 컬럼 / AdminSharing backend endpoint / LegalHold backend(v2.x)
- **v1.0.0-beta 출시 ceremony** — design sweep closure로 readiness 갱신 완료, 사용자가 golden path 회귀 통과 후 `git tag v1.0.0-beta && git push` 트리거

### 참조

- `IbizDrive_design.zip` (2026-05-10 export, `.claude/design-zip-extract/`)
- `docs/v1x-backlog.md` Tier 1 design gap 4건 closure 표시
- BETA-RELEASE.md 헤더 design-fidelity-sweep 트랙 추가

---

## 2026-05-12 — quota-phase3 (admin user storage quota mutation backend)

### 범위

quota mutation 5-phase track의 Phase 3 closure — admin이 사용자별 `storage_quota`를 조회/변경할 수 있는 backend endpoint + audit emit + AFTER_COMMIT listener. Phase 1 (#185 spec drift) + Phase 2 (#186 V18 컬럼) 위에서 V18 컬럼을 JPA entity에 매핑 + service/controller/event/listener 풀세트 + 단위 테스트 10건.

### 변경 (9 file, +408 코드)

backend
- `user/User.java` — `storage_quota` / `storage_used` 컬럼 매핑 + `getStorageQuota` / `getStorageUsed` / `changeStorageQuota` (invariant `>= 0` 가드).
- `user/UserStorageQuotaChangedEvent.java` — 도메인 이벤트 record (targetUserId, before/after, actorId).
- `admin/AdminUserQuotaService.java` — `@Transactional` getQuota/updateQuota. idempotent (same-value no-op, audit emit 생략). 음수 거부 + actor null 거부 + soft-deleted 404. AFTER_COMMIT event publish.
- `admin/AdminUserQuotaController.java` — `GET/PUT /api/admin/users/{userId}/quota` + `@PreAuthorize("hasRole('ADMIN')")`.
- `admin/AdminUserQuotaDto.java` + `AdminUserQuotaUpdateRequest.java` — DTO + Bean Validation (`@Min(0)`, `@NotNull`).
- `audit/UserQuotaAuditListener.java` — `@TransactionalEventListener(AFTER_COMMIT)`. `ADMIN_QUOTA_CHANGED` audit_log row 변환. `before/after.storageQuota` + `metadata.appliesTo="new-uploads-only"`. ADR #24 swallow.
- `test/.../AdminUserQuotaServiceTest.java` — Mockito 단위 8건 (getQuota happy/404/soft-deleted + updateQuota 음수/actor null/404/soft-deleted/happy + event/no-op/edge values/over-quota 허용).
- `test/.../UserQuotaAuditListenerTest.java` — Mockito 단위 2건 (happy emit + audit failure swallow).

docs
- `docs/04 §6.1` Phase 3 plan line — `USER_QUOTA_UPDATED` → `ADMIN_QUOTA_CHANGED` 정정 + closure 마크.
- `docs/v1x-backlog.md` Tier 1 — Quota Phase 3~5 → Phase 4~5만 잔존, ref 본 트랙으로 갱신.
- `BETA-RELEASE.md` — Source line + §6 audit coverage `51 → 52 emit (~90%)` + §7 deferred wording + Last Updated 2026-05-12.

### 결정/편차

- **over-quota 허용**: 한도 < 현재 사용량 변경은 service가 허용 (운영 grace). Phase 5 enforcement에서 신규 업로드만 차단하는 분기.
- **storage_used 미변경 책임**: 본 트랙은 한도만. `storage_used` mutation은 Phase 5 upload commit 트랜잭션에서 `UPDATE ... FOR UPDATE`.
- **idempotent same-value**: 같은 값 입력 시 row UPDATE + audit emit 모두 생략 — audit_log 폭증 방지.
- **single-approver MVP**: dual-approval은 framework 활성 시 hook (ADR #47 `app.dual-approval.user-quota-change.enabled` v1.x).

### 검증

- `./gradlew compileJava compileTestJava` BUILD SUCCESSFUL.
- `./gradlew test --tests "*AdminUserQuotaServiceTest" --tests "*UserQuotaAuditListenerTest"` BUILD SUCCESSFUL (10건 모두 통과).
- 마스터 통합: 브랜치를 stash + ff-only 머지로 origin/master HEAD에 동기화 후 작업물 복원.
- 사전 검증: `AuditEventType.ADMIN_QUOTA_CHANGED("admin.quota.changed")` enum 존재 (Phase 1), `AdminUserNotFoundException` 존재, `AdminExceptionHandler` 404 매핑 활용.

### 다음 세션 컨텍스트 — Phase 4/Phase 5

- **Phase 4 (frontend UI)**: `/admin/members` 페이지의 quota 컬럼 또는 `/admin/quota` 별도 페이지 — `RetentionPolicyEditor` 패턴(single-row inline editor) 답습. `useUpdateAdminUserQuota` hook + `api.getAdminUserQuota` 신규. 기존 `api.getStorageQuota` mock 제거 (도입돼 있다면).
- **Phase 5 (upload enforcement)**: `FileUploadController.create` / `FileVersionService.create` / tus init 진입에서 `users.storage_used + payload.size > storage_quota` 검증 → `413 QUOTA_EXCEEDED`. 성공 시 `UPDATE users SET storage_used = storage_used + size` 트랜잭션 (`FOR UPDATE`, CLAUDE.md §3 원칙 7). 에러 코드 `QUOTA_EXCEEDED`는 docs/02 §8 + frontend `src/lib/errors.ts` 동시 추가.
- Phase 4/5는 v1x-backlog Tier 1 잔여 (M effort 2 PR 분할).

### 블로커

- 없음.

### 설계 문서 동기화 완료

- `docs/04 §6.1` Phase 3 plan ✓
- `docs/v1x-backlog.md` Tier 1 ✓
- `BETA-RELEASE.md` (Source + §6 + §7) ✓

---

## 2026-05-11 — 🚦 Golden path 회귀 자율 수행 (BETA-RELEASE.md §9 GO 보조)

### 범위

BETA-RELEASE.md §9 출시 ceremony 1번 "Golden path 수동 회귀"를 자율 모드 + PowerShell/curl로 API-level 시퀀스 수행. 코드 변경 0 — 1회성 출시 게이트 evidence 확보(자동화 spec은 KISS+YAGNI로 미작성).

### 결과 (6 step 모두 PASS)

| Step | API | 결과 |
|---|---|---|
| 1. signup | `POST /api/auth/signup` | 201. first-user-ADMIN 분기 `SignupService.java:69` 코드 정합 (DB가 dev-preview seed 잔존이라 ADMIN role 직접 검증은 admin@local.test login으로 fallback) |
| 2. upload | `POST /api/files` multipart | 201 + fileId + size 정합 (34=34) + version=1 |
| 3. permission grant | `POST /api/files/{id}/permissions` | 201 (everyone/READ) + GET list verify (1건) |
| 4. download | `GET /api/files/{id}/download` | 200 + byte 일치 + Content-Disposition RFC 5987 (`attachment; filename=...; filename*=UTF-8''...`) |
| 5. soft delete | `DELETE /api/files/{id}` | 204 + GET `/api/trash?scopeType=department&scopeId=...` items 포함 |
| 6. restore | `POST /api/files/{id}/restore` | 200 + GET `/api/folders/{id}/items`에 복원 + trash에서 제거 |
| audit | `GET /api/admin/audit?targetId=...` | 6 events: `file.uploaded` / `permission.granted` / `file.downloaded`×2 / `file.deleted` / `file.restored` (actorId/resourceId 정합) |

### 결정/편차

- **DB 빈 상태 fallback**: backend가 dev-preview-stabilization 트랙 DB 재활용 상태(admin@local.test seed 잔존). 첫 signup 시도가 MEMBER 부여돼 first-user-ADMIN role-binding 직접 검증 실패 → admin@local.test login으로 pivot. ADR #41 분기 자체는 `SignupService.java:69` 코드(`userRepository.count() == 0L ? Role.ADMIN : Role.MEMBER`) + signup endpoint 201 동작으로 확인. 진짜 빈 DB role-binding 회귀는 출시 ceremony 이전 1회 빈 DB 가동 권장(코드 변경 0).
- **API-level only**: UI 회귀는 vitest 817 + e2e mock 4건 + 사용자 시각 1회로 cover. Playwright spec 자산화는 1회성 게이트라 YAGNI.
- **Frontend 미가동 영향 0**: 본 회귀는 backend(:8080) 직접 호출. frontend(:3000/:3001)는 별도.
- **local-dev.md drift 1건**: §5.1 signup 예시 body가 `fullName`이라 명시했으나 실제 DTO는 `displayName`. follow-up backlog(별도 PR).

### BETA-RELEASE.md §9 GO 결정 영향

§1/§3/§4/§5/§6/§10/§11 코드 게이트 ✅ 유지 + 본 회귀로 골든 패스 통합 정합 추가 확보. 남은 GO 조건은 **§2 인프라 게이트 + §8 모니터링 (운영자 sign-off)** + `v1.0.0-beta` 태그.

### 다음 단계

1. **`v1.0.0-beta` 태그 + push** (사용자 트리거)
2. **인프라팀 핸드오프** (BETA-RELEASE §2 / §8 + `docs/local-dev.md`)
3. **사내 베타 사용자 그룹 공지**
4. (선택) local-dev.md §5.1 `fullName` → `displayName` drift 정정

### 참조

- Evidence: `$env:TEMP\golden-path-*` (fixture / download bin+headers / session JSON)
- 코드 회귀 surface: `SignupService.java:69`, `FileUploadController.java:69`, `PermissionController.java:83`, `FileDownloadController.java`, `FileController.java:143/164`, `TrashController.java:64`, `AuditQueryController.java:89`
- 출시 체크리스트 SOT: `BETA-RELEASE.md` (Last Updated 2026-05-11)

---

## 2026-05-11 — retention-page-section-ref-fix (frontend §16 ref 정정 follow-up)

### 범위

직전 PR #189 (`dual-approval-spec-mini`)에서 분리한 frontend ref 정정 backlog 해소. 단순 string literal 변경 2건 (docs ref만, code 로직 무변경).

### 변경 (2 file, +2/-2)

- `frontend/src/app/admin/retention/page.tsx` line 101 — `(운영 런북 docs/04 §15.4)` → `(운영 런북 docs/04 §16 / ADR #47)`
- `frontend/src/components/admin/RetentionPolicyEditor.tsx` line 19 — Javadoc `(docs/04 §15.4)` → `(docs/04 §16 / ADR #47)`

### 검증

- `page.test.tsx`는 "2인 승인" 라벨 및 "dual-approval" 문자열만 검증 (line 113/115) — §15.4/§16 문구 회귀 zero.
- string literal만 변경, syntax/type 무영향 → typecheck/lint는 CI에 위임.

---

## 2026-05-11 — 🚀 v1.0.0-beta 출시 readiness 확정 (BETA-RELEASE.md sync, PR #188)

### 범위

`BETA-RELEASE.md` 5월 7일~11일 30+ 트랙 closure 동기화 (PR #188). 사내 베타 출시 ceremony의 사전 readiness 확정 — 태그 / golden path 수동 회귀는 별도 트리거.

### 코드 게이트 (BETA-RELEASE.md §9 단일 진실)

- §1 코드 베이스 게이트 ✅ (master CI green, principle FAIL 0, STRIDE 28/28)
- §3 cron 4종 prod profile 자동 활성 ✅
- §4 보안 헤더 / §5 인증 ✅
- §6 감사 / 권한 ✅ — audit emit **58 enum / 51 emit (~88%)**, 미emit 7개 §7 deferred 매핑 (누락 버그 0)
- §10 RightPanel 4탭 / §11 버전 관리 ✅
- §2 인프라 게이트 / §8 모니터링 — 운영자 책임, 코드 측 readiness 완료

### v1.x deferred (BETA-RELEASE.md §7 단일 진실)

- 확장자 화이트리스트 / MIME magic / AV 스캔
- presigned URL / S3 / KMS / cross-region replication
- MFA / refresh rotation / SCIM (ADR #18)
- audit_level / `FILE_VIEWED` / `FOLDER_AUDIT_LEVEL_CHANGED` (ADR #9)
- Legal Hold 전체 (docs/00 §4.3 v2.x)
- Team-centric pivot Plan A Phase 3+ (21 task), Plan B frontend foundation, Plan F
- grant-permission-dialog Phase C/D (USER/DEPT picker + ResourcePermissionsList 통합)
- quota mutation 5-phase Phase 3~5 (mutation UI / endpoint / `ADMIN_QUOTA_CHANGED` emit)
- 2-admin 승인 framework 실 구현 (spec만 PR #124, ADR #47 본문 정합 PR #189)
- progress streaming
- tus 재개 업로드 / SSE 실시간 동기화 (docs/01 §18 v1.x)
- mobile-view (CLAUDE.md §3 원칙 13 — 폐기 공식화 PR #179)

### 다음 단계

1. **Golden path 수동 회귀** (사용자 직접) — signup → 업로드 → 공유 → 다운로드 → 휴지통 사이클 (체크리스트는 본 트랙 세션 기록 참조)
2. **`v1.0.0-beta` 태그** (사용자 트리거 — `git tag -a v1.0.0-beta origin/master -m "v1.0.0-beta — 사내 베타 출시" && git push origin v1.0.0-beta`)
3. **인프라팀 핸드오프** — `BETA-RELEASE.md` §2 (HTTPS/CORS/DB secret) + §8 (모니터링) + `docs/local-dev.md` 전달, 추가 작성 0건
4. **사내 베타 사용자 그룹 공지**

### 참조

- 출시 체크리스트 단일 진실: `BETA-RELEASE.md` (Last Updated 2026-05-11)
- 직전 머지: PR #188 (BETA-RELEASE.md sync, 4-edit diff), PR #189 (dual-approval-spec drift 정정)

---

## 2026-05-11 — dual-approval-spec-mini (§16 ref drift 정정)

### 범위

Dual-approval framework (ADR #47) docs ref drift 정정. ADR #47 본문은 매우 정밀(데이터 모델·state machine·Tier 0·audit·API·config 모두 명세)하므로 본 PR은 추가 정밀화가 아닌 stale ref/표현 정정에 한정 (docs-only).

### 발견된 drift

- `docs/04 §6.5` (line 313) 및 §13 (line 378): "2인 승인 framework: v1.x deferred (**§15.4**)" — §15.4는 "운영 cron 4종 변경 절차"라 dual-approval과 무관. 정확한 ref는 §16 (Dual-Approval 운영 sub-section) / ADR #47.
- `docs/04 §16.1` Tier 0 표 line 871의 retention_change row 진입점이 "**deferred** — wave2-trash-policy-viewer mutation 후속"이라 stale. 실제 trash-retention-mutation #173 (Phase C frontend mutation editor)은 2026-05-11 머지 완료, 단일-approver MVP closure 상태. dual-approval은 framework 활성화 시 hook으로 표현 갱신.

### 변경 (1 file, +3/-3)

- §6.5 retention 정책 mutation UI 카드 ref: §15.4 → §16 / ADR #47
- §13 trash retention cron 절차 ref: §15.4 → §16 / ADR #47
- §16.1 Tier 0 표 retention_change row: deferred → "단일-approver MVP closure 2026-05-11 #173 — framework 활성화 시 hook"

### 결정/편차

- **frontend ref 정정은 별도 트랙으로 분리** — `frontend/src/app/admin/retention/page.tsx`의 "2인 승인" 카드 본문도 §15.4를 직간접 ref하나 vitest 회귀 가드(`page.test.tsx`) 영향 가능성. docs-only로 한정해 single-shot fit 유지. frontend ref 정정은 다음 트랙에서 묶음.
- **ADR #47 본문 무변경** — 데이터 모델/state machine/Tier 0/audit/API 모두 이미 정밀 명세. 추가 정밀화 over-engineering, drift 해소 목적과 무관.

### 다음 세션 컨텍스트

- v1.x backlog 잔여: quota Phase 3~5 (V18 backend service + UI + enforcement) / 2인 승인 framework 실 구현 (v1.x V_ 마이그레이션 + `pending_admin_approvals` table + service + admin UI) / progress streaming SSE / admin/permissions 전역 grant resource picker / frontend retention page §16 ref 정정.

---

## 2026-05-11 — quota-phase2-v18-migration (Phase 2: schema-only)

### 범위

quota mutation 5-phase track의 Phase 2 — `users` 테이블에 `storage_quota` / `storage_used` 컬럼을 V18로 활성. JPA entity 매핑 / endpoint / audit / enforcement는 Phase 3~5로 분리. schema-only 단일 SQL + 2 docs callout 갱신.

### 변경

- `backend/src/main/resources/db/migration/V18__user_storage_quota.sql` 신설:
  - `ALTER TABLE users ADD COLUMN storage_quota BIGINT NOT NULL DEFAULT 10737418240, ADD COLUMN storage_used BIGINT NOT NULL DEFAULT 0`
  - named CHECK constraint `users_storage_quota_nonneg` / `users_storage_used_nonneg` (>= 0 보호. soft limit `used <= quota`는 Phase 5 application 레벨에서 — quota 변경/grace 운영 유연성)
  - 컬럼 COMMENT 2건
- `docs/02 §2.1` 두 라인 callout 갱신: "V18 미도입 예정" → "V18 도입(2026-05-11), CHECK (>=0)".
- `docs/04 §6.1` 5-phase plan: Phase 1 → "(완료, #185 2026-05-11)", Phase 2 → "(본 PR)" + extra column 허용 사유 명기.

### 검증

- Hibernate `spring.jpa.hibernate.ddl-auto: validate` (`application.yml:13`) — schema의 extra column 허용. User entity는 storage_quota/used 미매핑이므로 V18 후에도 부팅 통과.
- UserRepository derived query는 quota 무관 (countBy/findBy email/role/dept 기반) — 영향 zero.
- 본 PR은 backend 코드 변경 zero → vitest/eslint/tsc 영향 zero. CI 검증은 backend (junit) Testcontainers slice가 V18 적용 후 schema validate 통과 여부 (메모리 *Local Docker-skip CI gap* — local에서 SKIPPED → CI 결과 의존).

### 결정/편차

- **JPA entity 매핑 미동반** — Phase 3 service 추가 시점에 함께. KISS + YAGNI. validate mode가 extra column 허용하므로 단계 분리 안전.
- **soft limit CHECK 미도입** — `used <= quota`는 application 레벨 (Phase 5). 한도 변경 / 운영 grace 시 일시적 over-quota 허용 필요.
- **Phase 3 진입 의무 없음** — 5-phase 분할은 각 단계 독립 closure. v1.x 다른 backlog로 자유 pivot.

### 다음 세션 컨텍스트

- **Phase 3** — `AdminUserQuotaService` (Spring service) + `PUT /api/admin/users/{id}/quota` endpoint + `AdminUserQuotaController` + `AuditEventType.USER_QUOTA_UPDATED` + `UserQuotaAuditListener`. User entity에 `storage_quota` / `storage_used` 필드 매핑 + getter/changeQuota domain method. slice + service 테스트 (admin-trash-policy 패턴).
- v1.x backlog 잔여: 2인 승인 framework (ADR #47) / progress streaming SSE / quota Phase 3~5 / admin/permissions 전역 grant resource picker.

---

## 2026-05-11 — quota-spec-drift-realign (Phase 1: docs-only)

### 범위

quota mutation UI 트랙 진입 사전 점검에서 발견된 spec drift 정정. backend 도메인 부재 + frontend mock + docs/04 §6.1 잘못된 컬럼명("quota_bytes")이 동시 노출됐고, 본 PR은 5-phase plan으로 분할한 Phase 1 (docs-only).

### 발견된 drift

- `docs/02 §2.1` schema에 `users.storage_quota` / `users.storage_used` 컬럼이 정의돼 있으나 V2~V17 어떤 마이그레이션에도 ALTER 미적용 (`V2__users_auth.sql` 주석 "후속 phase에서 추가"가 ground truth).
- `docs/04 §6.1` callout이 `quota_bytes` 컬럼명을 언급 — 실제 spec 이름은 `storage_quota` (drift 2건의 충돌).
- frontend `api.getStorageQuota`는 mock placeholder ("백엔드 미존재... 실제 quota API 신설 시 본 mock만 fetch로 교체"). impl 부재가 mock 주석에는 명시됐으나 docs callout에는 누락.
- upload path 에러 contract(`docs/02 §7.6 / §8`)의 `413 QUOTA_EXCEEDED`는 정의만 있고 enforcement 미구현 — docs/04 §6.1 callout에 별도 명시 누락.

### 변경 핵심

- `docs/02 §2.1` schema 두 라인에 V18 미도입 callout 추가 (행 주석에 `⚠️ V2~V17 ALTER 미적용 — V18 도입 예정`). schema 라인은 보존 (spec = 최종 의도 상태).
- `docs/04 §6.1` 정정 + 5-phase plan 통일:
  - 컬럼명 "quota_bytes" → 정확한 `storage_quota` / `storage_used` 명기 + `V2__users_auth.sql` 주석 ground truth 인용
  - Phase 1 (본 PR) → Phase 5 (enforcement) 의 trajectory를 한 곳에서 추적 가능하도록 명세화
  - frontend mock 상태와 `413` enforcement 미구현 상태도 명시
- `docs/02 §8` 에러 contract 표는 무수정 — 계약은 정의 그대로 유지, "Phase 5까지 미발생" 상태는 §6.1 plan에서 명시.

### 검증

docs-only. typecheck/lint/test 무영향. 본 PR이 후속 Phase 2~5의 trajectory를 단일 anchor (`docs/04 §6.1`)에 고정 → 후속 PR이 "현재 어느 Phase까지 머지됐는가"를 명확히 표기 가능.

### 결정/편차

- **§2.1 schema 라인 보존** — 두 라인 삭제(spec 후퇴) vs 인라인 callout(spec 유지 + 현황 표시) 중 후자. spec drift 정정의 목적은 spec/impl 일치 *명시*이지 spec 후퇴 아님.
- **5-phase 분할** — 각 phase가 독립 PR로 완결 가능. Phase 1 closure 후에도 후속 phase 진입 의무 없음. 다음 트랙(2인 승인 framework, audit streaming 등)으로 자유 pivot.

### 다음 세션 컨텍스트

- **quota Phase 2 (V18 migration)**: `backend/src/main/resources/db/migration/V18__user_storage_quota.sql` — `ALTER TABLE users ADD storage_quota BIGINT NOT NULL DEFAULT 10737418240, storage_used BIGINT NOT NULL DEFAULT 0`. 기존 row는 DEFAULT로 backfill. testcontainers slice는 V18 적용 후 schema 검증.
- v1.x backlog 잔여: 휴지통 보존(완료) / quota Phase 2~5 / 2인 승인 framework 실 구현(ADR #47, trash+role hook into) / progress streaming SSE / admin/permissions 전역 grant resource picker.

---

## 2026-05-11 — upload-xhr-csrf-helper (csrf-helper-sweep #165 follow-up)

### 범위

csrf-helper-sweep #165에서 의도적 예외로 분리됐던 `api.uploadFile` (XHR sync 시그니처)을 cold-start 안전하게 정리. backlog 한 줄(`upload XHR 예외 유지 — async 시그니처 도입은 useUpload 호출자 변경 필요 → sweep 범위 초과`) 해소.

### 변경 핵심

- `frontend/src/lib/api.ts` `uploadFile`: `(...): XMLHttpRequest` → `async (...): Promise<XMLHttpRequest>`. `readCookie('XSRF-TOKEN')` → `await ensureCsrfToken()` — cookie 부재 cold-start에서 `/api/auth/csrf` 부트스트랩 후 헤더 set. 기존 의도적 예외 주석 제거.
- `frontend/src/hooks/useUpload.ts` `startTask`: async 변환 + `await api.uploadFile(...)` + try-catch (ensureCsrfToken fetch network error → task failed/network 마킹) + race 가드 (await 중 cancel로 status가 'queued'를 벗어나면 xhr.abort + return).
- `frontend/src/lib/api.upload.test.ts`: 4 case async 변환 + cold-start 회귀 가드 1건 추가 (cookie 부재 → fetch `/api/auth/csrf` 호출 + 부트스트랩 토큰으로 `setRequestHeader('X-CSRF-Token', 'boot-csrf')` 호출 확인).
- `frontend/src/hooks/useUpload.test.ts`: 9 case async 변환 + `act(() => …)` → `await act(async () => …)` + `vi.advanceTimersByTime` → `await vi.advanceTimersByTimeAsync` (fake timer + promise micro task 동기화).

### 검증

- `pnpm --filter frontend typecheck` exit 0.
- `pnpm --filter frontend lint` exit 0.
- `pnpm --filter frontend test --run` **1314/1314 PASS** (180 test files). cold-start 가드 1건 추가, 기존 회귀 zero.

### 결정/편차

- **race 가드는 status === 'queued' 비교** — store.cancel은 status를 'failed'로 마킹하므로 await 후 'queued'가 아니면 cancel/retry 사이라는 신호. xhrMap 등록 전이라 store.cancel이 xhr를 abort하지 못한 상태 → 명시적 abort + return.
- **`if (csrf) xhr.setRequestHeader` 가드 제거** — #165 본문 정합. `ensureCsrfToken`이 빈 문자열이라도 반환 보장하므로 가드 불필요, 다른 16+ 사이트 패턴과 동일.
- **progress event listener race 미해결** — listener는 여전히 send() 호출 이후 등록 (기존 동작과 동일). 이 race는 #165 sweep과 무관한 별개 risk라 YAGNI로 분리 (필요 시 future track: send를 caller로 노출 + listener 등록 후 명시 send).

### 다음 세션 컨텍스트

- v1.x backlog 잔여: 휴지통 보존 정책 mutation UI(완료) / quota mutation UI (큰 트랙 — spec drift `docs/02 §2.1 storage_quota` impl 누락 + v1.x deferred + per-user 도메인) / 2인 승인 framework 실 구현 (ADR #47, trash + role hook into) / progress streaming SSE.
- **quota track 진입 전 사전 작업**: docs/02 §2.1 storage_quota 컬럼 spec drift 정정(impl 없음을 명시), docs/04 §6.2 deferred 라벨 유지. enforcement 도입 시점에 upload path `413 QUOTA_EXCEEDED` 분기 신설.

---

## 2026-05-11 — 🎨 design-handoff G2~G5 closure (track 종료)

### 범위

Claude Design 핸드오프 (`q_E8bGXpCKkbcXTFb4TiTg`) 와 frontend 시각 fidelity gap-report (`dev/active/design-handoff-gap-report-2026-05-10.md`) 의 잔여 항목 G2~G5 정렬 + 트랙 closure.

### 완료된 PR (이 세션 생성)

| # | 제목 | 상태 |
|---|---|---|
| #172 | `feat(searchbar-kbd): platform별 kbd 칩 텍스트 분기 (⌘K / Ctrl K)` — #168 follow-up | CI green, OPEN |
| #175 | `feat(design-handoff-g5): density 토글 (compact\|default\|comfortable)` | CI green, OPEN |
| #177 | `feat(design-handoff-g4): FileTable 6열 + 체크박스/action 컬럼 시각화` | CI 진행, OPEN |
| (본 PR) | `docs(design-handoff-g2-g5): track closure — gap-report 갱신 + progress.md` | - |

### Closed (중복)

- **PR #170** `feat(design-handoff-g3): SearchBar 중앙정렬 + ⌘K kbd 칩 + clear 버튼`
  - 다른 세션이 #168에서 G2+G3+사이드바 collapse 동시 머지하여 중복 발생
  - 차별점(kbd platform 분기)은 #172로 분리

### gap-report 결과

| Gap | 결과 | PR |
|---|---|---|
| G1 TopBar 48px | ✅ 머지 | #148 |
| G2 TopBar 3-col grid + 햄버거 | ✅ 머지 (다른 세션) | #168 |
| G3 SearchBar ⌘K + 폭 | ✅ 머지 / 진행 | #168 + #172 |
| G4 FileTable 6열 | ✅ 진행 | #177 |
| G5 row 34 + density 토글 | ✅ 머지 / 진행 | #148 + #175 |
| G6 grid 172px | ✅ 머지 | #148 |
| G7 mobile-view | ⏸ backlog | M-mobile (사내 데스크톱 메인 가정) |
| G8 DropOverlay | ✅ 머지 | #148 (`166432b`) |
| G9 statusbar | ✅ 변경 불필요 | — |

**총 8/9 해결**, G7만 backlog 명시.

### 핵심 결정

- **D1 — density 우선순위**: `[data-density]` (사용자 명시) > `[data-variant]` default (시각 정체성). CSS cascade로 variant rules 뒤에 배치하여 override.
- **D2 — ⌘K + `/` 공존**: muscle memory 보호 (현 `/` 단축키 유지) + 디자인 spec 따름 (⌘K/Ctrl+K 추가). editable target 가드 차이로 분리.
- **D3 — kbd 칩 platform 분기**: 사내 Windows 다수 → `Ctrl K` 표시. macOS만 `⌘K`. navigator.platform 으로 분기 (#172).
- **D4 — G7 backlog**: 사내 데스크톱 메인. mobile-view 우선순위 낮음 (사용자 확인).
- **D5 — G4 액션 버튼 placeholder**: 메뉴 wiring (rename/move/share/delete 컨텍스트 메뉴) 은 v1.x 후속 PR. 본 PR은 layout fidelity 만.

### 회고

**좋았던 점:**
- M4 selection store가 이미 wired 상태였음을 사전 점검으로 확인 → G4 scope이 visual layer 만으로 압축 (Plan A2 검증된 state-check-first pattern).
- variant pattern(`lib/variant.ts` + `useVariant.ts` + FOUC inline script + TweaksPanel radio) 을 G5 density 에서 1:1 mirror → KISS + 검증 비용 최소화.

**learning:**
- **Co-session 충돌**: 세션 진행 중 다른 세션이 #167/#168/#171/#174/#176/#178 등 다수 머지. PR #170 (G2+G3) 이 #168 (G2+G3+sidebar) 와 완전 중복 → close 처리. 시작 시 state check 했으나 세션 중 master 이동 빈도가 컸음.
- **PR 작성 후 즉시 mergeStateStatus 확인 필요** — 진행 중 다른 세션의 #168 이 같은 영역을 머지했음을 PR 생성 후에야 발견 (DIRTY 신호).

### 다음 세션 컨텍스트

- PR #172/#175/#177 머지 후 본 closure PR 머지 + gap-report 를 `dev/active/` → `dev/completed/` 이동.
- G4 액션 버튼 메뉴 wiring 후속 PR (v1.x).
- M-mobile 신설 시 G7 처리 — `[data-density="compact"]` cascade 와 통합 가능 여부 검토.
  - **2026-05-11 정정**: G7 폐기 처리됨 (CLAUDE.md §3 원칙 13). M-mobile 트랙 자체 폐기.

---

## 2026-05-11 — ⌨️ TopBar 키보드 단축키 도움말 버튼 (cheat sheet 클릭 진입점)

### 범위

PR #171 (ShortcutsCheatSheet `?` 모달) 후속 폴리시. `?` 단축키 미인지 사용자의 발견성(discoverability)을 위해 TopBar 우측에 Keyboard 아이콘 버튼 추가. 클릭 시 동일 `OPEN_SHORTCUTS_EVENT` dispatch → 모달 트리거.

### 변경 핵심

- `frontend/src/components/topbar/TopBar.tsx`: 우측 영역에 Keyboard 도움말 버튼 추가 (TweaksPanel 왼쪽). `aria-label="키보드 단축키 보기"` + `title="키보드 단축키 ( ? )"`. 클릭 시 `window.dispatchEvent(new CustomEvent(OPEN_SHORTCUTS_EVENT))`.
- `frontend/src/components/topbar/TopBar.test.tsx`: 회귀 가드 2건 추가 (버튼 노출 + aria/title / 클릭 시 OPEN_SHORTCUTS_EVENT dispatch). 누적 5건.
- `docs/01 §12.1` Shortcut Cheat Sheet callout — `?` 키 외 TopBar 버튼 클릭 진입점 명시.
- `docs/01 §17` TopBar 레이아웃 callout — 우측 영역에 Keyboard 도움말 버튼 추가.

### 검증

- `pnpm typecheck` exit 0.
- `pnpm lint` exit 0.
- `pnpm test --run TopBar` 5/5 PASS (3 기존 + 2 신규).
- 광범위 회귀 zero — TopBar 외 영향 없음.

### 결정/편차

- **CustomEvent 재사용** — 새 prop/store 없이 기존 `OPEN_SHORTCUTS_EVENT` 그대로 사용. KISS.
- **lucide `Keyboard` 아이콘** — 사용자가 즉시 단축키 도움말로 인지. `HelpCircle`(일반 도움말 의미)보다 명확.
- **title="키보드 단축키 ( ? )"** — 호버 시 단축키 hint. placeholder 아닌 tooltip이라 i18n 영향 최소.
- **dev-docs 부트스트랩 생략** — 단일 컴포넌트 1줄 추가, KISS.

### 트랙 외 후속

- 단축키 → action 매핑 통합 (KEYBOARD_SHORTCUTS에 action 필드) — v1.x backlog.
- ShareDialog 도움말 진입점 (필요 시) — 별도.

---

## 2026-05-11 — 🏁 trash-retention-mutation 트랙 종료 (Phase A → B → C 모두 머지, dev-docs archive)

### 범위

휴지통 보존 정책 mutation UI 트랙 closure. 코드 0줄 — dev-docs 3종을 `dev/active/` → `dev/completed/`로 이동 + plan.md frontmatter status 갱신.

### 트랙 closure 표

| Phase | PR | 머지 | 산출물 |
|---|---|---|---|
| A — spec | #167 | 2026-05-11 | docs/02 §2.12 V17 schema + §7.11.1 PUT spec, docs/03 §4.1 audit event(`admin.retention.changed`) + §6.4.3 dual-approval 표 갱신, docs/04 §8.3/§9.2/§15.6 운영 명세, dev-docs 3종 |
| B — backend | #169 | 2026-05-11 | V17 migration + TrashPolicy entity/repo/service + RetentionPolicyChangedEvent + TrashPolicyAuditListener + AdminTrashPolicyController PUT + FileMutationService/FolderMutationService 호출 전환 + 단위/slice 테스트 21건 |
| C — frontend | #173 | 2026-05-11 | api.updateAdminTrashPolicy + useUpdateAdminTrashPolicy + RetentionPolicyEditor (감소 경고 + ConfirmDialog) + page wire + 회귀 가드 17건 |

### 핵심 결정 (회고)

- **저장 전략 Option A** — single-row `trash_policy` (id=1 CHECK + retention_days BETWEEN 7 AND 90). KISS/YAGNI, 일반화 `app_settings`는 후속 트랙(quota 등) 패턴 확정 후 검토.
- **신규 row만 적용** — 기존 trash row의 `purge_after`는 재계산 안 함. 일수 감소 시 hard purge 폭증 회피. UI confirm dialog + audit `appliesTo:'new-deletes-only'` 명시.
- **단일-approver MVP** — 단일 ADMIN 즉시 적용. 2인 승인 framework는 v1.x++ deferred. 본 endpoint는 framework 도입 시 hook point (`app.dual-approval.retention-change.enabled`).
- **`@PostConstruct` ensure-row** — V17 migration이 INSERT 안 함, service 부팅 시 yml 값으로 idempotent INSERT (운영자 yml override 이력 보존 + 다중 instance race는 PK + CHECK가 흡수).
- **No-op same value** — `updateRetentionDays(currentDays, ...)`는 row touch + audit emit 없이 현재값 반환.

### 사용자 가시 결과

- `/admin/retention` 페이지에서 ADMIN이 보존 일수(7~90)를 무중단 변경
- 감소 시 인라인 경고 + ConfirmDialog 재확인 (hard purge 폭증 회피)
- audit_log `admin.retention.changed` 자동 기록
- 운영자 yml 직접 수정 + 재기동 의존 해소 (docs/04 §15.6 backlog 처리)

### 함께 처리된 부수 작업

- PR #169에서 co-session이 master에 push한 `TopBar.test.tsx` lint error 1줄 fix (unblock)
- 두 차례 master drift conflict 해결 (#168, #171, #169 머지 사이)

### 다음 트랙 후보

- **quota mutation UI** — 같은 패턴(single-row 테이블 또는 `app_settings` 일반화 검토 시점). docs/04 §6.1 spec.
- **2인 승인 framework** — trash + quota + role 모두 hook into. ADR #47 활성화 트랙. 가장 큰 트랙.
- **uploadFile XHR async cleanup** — csrf-helper-sweep(#165)의 의도적 예외 해소. useUpload 호출자 변경 필요.

---

## 2026-05-11 — 📜 docs/no-mobile-support-policy (CLAUDE.md §3 원칙 13 + G7 폐기 명시)

### 범위

사용자 결정(2026-05-11): IbizDrive는 사내 데스크탑 메인 가정, 모바일 미지원. 프로젝트 차원 invariant로 명시해 모든 세션·co-session이 같은 가드 적용. 코드 0줄, doc-only PR.

### 변경

- **CLAUDE.md §3 핵심 원칙 13 추가** — "데스크탑 메인, 모바일 미지원" 원칙. `lg:` breakpoint / `.mobile-view` / `useMediaQuery` / 사이드바 mobile overlay / RightPanel mobile auto-hide / FileTable 컬럼 축약 모두 backlog 제외. 좁은 데스크탑 폭은 기존 `useSidebarChromeStore` 사용자 토글로 충분.
- **`dev/active/design-handoff-gap-report-2026-05-10.md` 진행 상태 갱신**:
  - G1/G2/G3/G6/G8/G9 ✅ 머지 완료 마커 (PR #148, #168 등).
  - G4 활성 트랙 마커 (`feat/design-handoff-g4-filetable` co-session).
  - G5 활성 트랙 마커 (`feat/design-handoff-g5-density` co-session).
  - **G7 폐기** 마커 (CLAUDE.md §3 원칙 13 backlink).
  - 결론 섹션 2026-05-11 시점으로 갱신.
  - PR #171/#174 (`?` 모달 + 데이터 single-source) backlink — 직접 항목은 아니나 §12.1 spec 정합 강화.

### 검증

- 코드 0줄 — typecheck/lint/test 무관.
- spec 정합성 자체 리뷰: 메모리 `project_no_mobile_support` ↔ CLAUDE.md §3 원칙 13 ↔ design-handoff-gap-report G7 마커 3중 일관성.

### 결정/편차

- **CLAUDE.md §3 원칙 13으로 격상** — 메모리는 본 세션 한정이라 co-session에 전파 안 됨. 프로젝트 invariant로 격상해야 모든 세션이 G7류 작업 회피.
- **gap report는 active 유지** — G4/G5 활성 + co-session 진행 중. 두 트랙 머지 후 archive 후보.
- **dev-docs 부트스트랩 생략** — doc-only 정렬 작업, KISS.

### 다음 자율 후보 (모바일 제외)

- G4 / G5 (co-session 진행 중) — 충돌 회피.
- **admin/permissions 전역 grant — resource picker** (큰 가치, 분할 가능).
- audit JSON streaming / progress streaming (SSE/WS) — backend 영역.
- ROLE/TEAM grant 평가 (v2.x backend resolver).

---

## 2026-05-11 — 🧹 dev-active-archive sweep (design-topbar-sidebar-collapse + shortcut-cheatsheet)

### 범위

PR #168(design-fidelity G2/G3 + sidebar collapse) + PR #171(shortcut-cheatsheet `?` 모달) 머지 후 잔존하던 dev-docs 2건을 `dev/active/` → `dev/completed/`로 이동. plan front matter `status: in_progress` → `completed` + `merged: PR #...` 마커. 코드 0줄.

본 closure에 포함되지 않은 잔존:
- `dev/active/design-handoff-gap-report-2026-05-10.md` — 단일 doc gap report, active 유지 (PR #166 결정 동형). G7 항목은 사내 데스크탑 메인 가정으로 폐기됨(사용자 결정).
- `dev/active/trash-retention-mutation/` — Phase B/C 머지 후 closure는 별도 co-session 트랙에 위임.

### 변경

- `dev/active/design-topbar-sidebar-collapse/` → `dev/completed/` (plan status `completed` + 결과 마커).
- `dev/active/shortcut-cheatsheet/` → `dev/completed/` (plan status `completed` + `merged: PR #171, 후속 PR #174` 마커).

### 검증

- 코드 0줄 — typecheck/lint/test 무관.
- `git diff --stat` rename only.

### 트랙 외 (사용자 결정)

- **G7 mobile-view 폐기** — 사내 데스크탑 메인 가정. 자율 모드 후보 표에서 제외 (메모리 `project_no_mobile_support` 기록).

---

## 2026-05-11 — ♻️ shortcuts-data-tokenize (cheat sheet 데이터 single source 추출)

### 범위

PR #171 (shortcut-cheatsheet) 종료 보고의 v1.x backlog 항목 closure. `ShortcutsCheatSheet.tsx` 컴포넌트 내부에 inline됐던 단축키 데이터 array를 `frontend/src/lib/keyboardShortcuts.ts` single source로 추출. docs/01 §12.1 ↔ 코드 표현 정합 일관성 확보.

### 변경 핵심

- `frontend/src/lib/keyboardShortcuts.ts` (NEW): `KeyboardShortcut` interface + `ShortcutCategory` interface + `KEYBOARD_SHORTCUTS` readonly array 5종 카테고리. 데이터/로직 분리(useGlobalShortcuts는 이벤트 dispatch 책임 유지).
- `frontend/src/lib/keyboardShortcuts.test.ts` (NEW, 3건): 5 카테고리 순서 / 핵심 키 노출 회귀 / 항목별 비빈 값 가드.
- `frontend/src/components/topbar/ShortcutsCheatSheet.tsx`: 내부 `SHORTCUTS`/`Shortcut`/`ShortcutCategory` 정의 제거 → `KEYBOARD_SHORTCUTS` import. 본체 로직 무수정 — 회귀 zero.
- `docs/01 §12.1` callout 갱신 — "컴포넌트 내부 정적 array (YAGNI)" → "`frontend/src/lib/keyboardShortcuts.ts` single source" + 동기화 의무.
- `CLAUDE.md §4` 계약 파일 표에 `src/lib/keyboardShortcuts.ts` ↔ `docs/01 §12.1` 매핑 추가.

### 검증

- `pnpm typecheck` exit 0.
- `pnpm lint` exit 0.
- `pnpm test --run keyboardShortcuts` 3/3 PASS.
- `pnpm test --run ShortcutsCheatSheet` 6/6 PASS (회귀 zero, import 경로만 변경).
- 동작 변경 zero — 순수 data 추출 refactor.

### 결정/편차

- **데이터 ↔ 로직 분리 유지** — `KEYBOARD_SHORTCUTS` (data)와 `useGlobalShortcuts` (event dispatch)는 별도 책임. 통합 후보(키 → action 매핑 테이블)는 v2.x backlog (key/action enum 도입 + 다중 listener 패턴 검토 선결).
- **`as const` + `readonly`** — 데이터 mutation 방지 + 정확한 타입 추론.
- **테스트 — 데이터 회귀 가드 3건만** — 5 카테고리 순서 + 핵심 키 노출 + 비빈 값 가드. 항목별 라벨 검사는 docs와의 drift 비용 큼 → KISS.

### 트랙 외 후속

- **G7 mobile-view** — 큰 마일스톤. 사이드바/RP auto-hide + 모바일 FileTable 컬럼 축약.
- **단축키 ↔ action 매핑 통합** — v2.x.

### dev-docs

- 본 PR은 작은 리팩터링이라 dev-docs 부트스트랩 생략 (CLAUDE.md "private helper만 바꾸는 국소 수정은 spec 갱신을 생략할 수 있다" 동형 판단 — spec drift 방지 갱신만 포함).

---

## 2026-05-11 — trash-retention-mutation Phase C (frontend mutation editor)

### 범위

Phase B(PR #169 backend) 의존. `/admin/retention` 페이지에 mutation editor 추가 — input + 감소 경고 + ConfirmDialog flow + 단일-approver MVP 명시.

### 변경 핵심

- `frontend/src/lib/api.ts` — `updateAdminTrashPolicy(days)` 추가 (`PUT /api/admin/trash/policy`, `await ensureCsrfToken()`, body `{days}`, 응답 `{retentionDays}` unwrap).
- `frontend/src/lib/api.updateAdminTrashPolicy.test.ts` — 회귀 가드 5건 (PUT/CSRF/body/4xx envelope/no-op).
- `frontend/src/hooks/useUpdateAdminTrashPolicy.ts` — `useMutation` 래퍼, onSuccess `qk.adminTrashPolicy()` invalidate.
- `frontend/src/hooks/useUpdateAdminTrashPolicy.test.tsx` — 회귀 가드 2건 (invalidate + error pass-through).
- `frontend/src/components/admin/RetentionPolicyEditor.tsx`:
  - props `currentDays`. input(7~90) + diff 미리보기 + 감소 경고 + 범위 위반 차단.
  - "정책 변경" 버튼 (unchanged/range/pending 3중 disable).
  - ConfirmDialog (focus trap, Esc, 단일-approver 명시) → mutate.
  - 성공 시 `toast.success` + dialog close. 400 inline alert(dialog 유지) / 403 toast+close / 기타 fallback.
- `frontend/src/components/admin/RetentionPolicyEditor.test.tsx` — 회귀 가드 10건 (초기 렌더, 변경, 감소 경고, 증가 시 경고 미노출, 범위 위반, confirm 노출, confirm flow, cancel, 400 inline, 403 toast).
- `frontend/src/app/admin/retention/page.tsx` — yml 안내 섹션 제거, `<RetentionPolicyEditor>` 마운트 + 2인 승인 deferred 안내 갱신.
- `frontend/src/app/admin/retention/page.test.tsx` — RetentionPolicyEditor stub mock + Phase C 통합 테스트 1건 추가.

### 검증 (Phase C)

- `pnpm typecheck` exit 0.
- `pnpm lint` exit 0.
- `pnpm test --run` Phase C 4 파일 25/25 PASS.
- 회귀: 기존 page.test.tsx 일부 갱신 (yml 안내 제거 → editor stub 검증 + multi-text getByText → getAllByText)

### 결정/편차 (Phase C)

- **단일-approver MVP 명시** — page §approval-heading + ConfirmDialog 내부 텍스트 두 곳 모두 "단일 ADMIN 즉시 적용" 명시. v1.x++ dual-approval 도입 시 hook point.
- **감소 경고 UI 위치** — 입력 변경 시 인라인 + ConfirmDialog 재확인. 두 단계 가드.
- **input HTML5 min/max + JS 가드 이중화** — 브라우저별 형식 가드 일관성.
- **page.test.tsx에서 RetentionPolicyEditor stub** — 본체 행위는 RetentionPolicyEditor.test.tsx가 책임. page는 wire만 검증.

---

## 2026-05-11 — trash-retention-mutation Phase B (backend — V17 + service + endpoint + audit)

### 범위

Phase A spec(PR #167) 구현. backend mutation full path: V17 single-row 테이블 + entity/repo/service + audit listener + PUT endpoint + FileMutationService/FolderMutationService 호출 전환. yml `TrashRetentionProperties`는 V17 row 부재 시 첫 INSERT의 default value source로만 잔존.

### 변경 핵심

- **V17 migration** (`backend/src/main/resources/db/migration/V17__trash_policy.sql`):
  - `CREATE TABLE trash_policy (id SMALLINT CHECK id=1, retention_days INT CHECK 7..90, updated_at, updated_by FK ON DELETE SET NULL)`
  - 초기 row INSERT는 V17이 직접 안 함 — service `@PostConstruct`가 yml 값으로 idempotent INSERT (운영자 yml override 이력 보존).
- **TrashPolicy entity + repository** (`com.ibizdrive.trash.TrashPolicy`, `TrashPolicyRepository`).
- **TrashPolicyService** (`getRetentionDays`, `updateRetentionDays`, `ensureSingletonRow @PostConstruct`):
  - 7..90 범위 + actor null 검증, 동일값 입력 no-op (audit 미발행).
  - 변경 시 `RetentionPolicyChangedEvent` publish.
- **RetentionPolicyChangedEvent** (record).
- **TrashPolicyAuditListener** (`@TransactionalEventListener AFTER_COMMIT`, `TeamAuditListener` 패턴 답습) — `RETENTION_POLICY_CHANGED` audit_log 변환, metadata `appliesTo:'new-deletes-only'`.
- **AuditEventType.ADMIN_RETENTION_CHANGED** + **AuditTargetType.TRASH_POLICY** enum 추가.
- **AdminTrashPolicyController**:
  - GET source 변경: `TrashRetentionProperties.days()` → `trashPolicyService.getRetentionDays()`.
  - PUT 추가: `@PreAuthorize("hasRole('ADMIN')")`, body `{days:7..90}` (Bean Validation), 응답 `{retentionDays}`.
  - DTO `AdminTrashPolicyUpdateRequest` 신규.
- **FileMutationService** + **FolderMutationService**: `TrashRetentionProperties` 의존성 → `TrashPolicyService`로 전환. constructor 시그니처 + import 정리.
- **테스트**:
  - `TrashPolicyServiceTest` (10 단위 케이스) — get/update/ensureSingletonRow 흐름, race(DataIntegrityViolation) swallow, 7..90 boundary.
  - `TrashPolicyAuditListenerTest` (2 케이스) — 변환 정확성 + 실패 swallow.
  - `AdminTrashPolicyControllerTest` 확장 — PUT 200/400(범위×3, null)/401/403(member, auditor) 7건 추가.
  - 12 기존 slice 테스트(File/Folder MutationService 외) — `new TrashRetentionProperties(30)` → `TrashPolicyTestSupport.stubReturning(30)` sed sweep + 신규 helper 1.

### 검증

- `./gradlew compileJava compileTestJava` 통과.
- `./gradlew test --tests AdminTrashPolicyControllerTest` 9/9 PASS (Webmvc slice).
- `./gradlew test --tests TrashPolicyServiceTest --tests TrashPolicyAuditListenerTest` (단위) — 결과 commit 직전 확인.
- DB-touching slice (FileMutationServiceTest 등)는 Testcontainers 필요 → 로컬 SKIP, CI(Linux)에서 검증.

### 결정/편차

- **`@PostConstruct` ensure-row** — V17 migration에서 INSERT 생략 + service 부팅 시점에 yml 값으로 idempotent INSERT. 운영자 yml override 이력 보존 + 다중 instance race는 V17 PK + CHECK가 두 번째 INSERT를 차단(catch DataIntegrityViolation, swallow).
- **No-op same value** — `updateRetentionDays(currentDays, ...)`는 row touch + audit emit 없이 현재값 반환. 운영자 noop 클릭 audit 노이즈 방지.
- **target_id NULL** — single-row 정책이라 audit_log row의 `target_id`는 null. `target_type='trash_policy'`만으로 식별. 다른 single-row 시스템 audit 패턴 (SYSTEM_PURGE_EXECUTED 등)과 동형.
- **TrashRetentionProperties 잔존** — 완전 제거 대신 yml default value source로 retain. Phase B 이후 production 의존성은 service 단방향이지만 record는 부팅 default + 테스트 fixture 양쪽에서 활용.
- **Test sweep 헬퍼** — 12 slice 테스트에 직접 mock 인라인 추가 대신 `TrashPolicyTestSupport.stubReturning(int)` 단일 helper. DRY.

### 다음 세션 컨텍스트

**Phase C (frontend, 별도 PR)**:
- `api.updateTrashPolicy(days)` + 회귀 가드.
- `useUpdateTrashPolicy` hook + invalidate `qk.adminTrashPolicy()` + 회귀 가드.
- `RetentionPolicyEditor` 컴포넌트 — input + 감소 경고 (`{currentDays}일 → {newDays}일, 신규 삭제부터 적용`) + ConfirmDialog flow.
- `/admin/retention` page mutation 섹션 wire (admin 보유 + 데이터 로드 시 노출).
- 회귀 가드 vitest.

---

## 2026-05-11 — ⌨️ shortcut-cheatsheet 트랙 (`?` 도움말 모달)

### 범위

design-fidelity G2/G3(PR #168) 종료 보고에서 v1.x backlog로 분리됐던 단축키 cheat sheet. 사용자 가시 진입점 추가 — `?` 키 → 모달 → 카테고리별 단축키 표. 새 컴포넌트 + 훅 분기 + layout 마운트 + spec 갱신.

### 변경 핵심

**Hook:**
- `hooks/useGlobalShortcuts.ts`: `?` 분기 추가 (editable 가드 + Ctrl/Meta/Alt modifier 무시). `OPEN_SHORTCUTS_EVENT = 'app:open-shortcuts'` 신규 export. `Shift+/`로 입력되는 `?`는 별도 modifier 검사 불필요.

**Component (NEW):**
- `components/topbar/ShortcutsCheatSheet.tsx`: self-contained 모달 — props 없음, `useState` owns visibility, `OPEN_SHORTCUTS_EVENT` listen → open. ESC/X 버튼으로 close + 이전 focus 복귀. 카테고리 5종(검색·내비게이션·선택·액션·도움말), 단축키 데이터는 컴포넌트 내부 정적 array(docs/01 §12.1 동등 표현).

**Layout:**
- `(explorer)/layout.tsx`: 모달 1회 마운트 (DndProvider 자식).

**Docs:**
- `docs/01 §12.1` 키맵 표에 `⌘K / Ctrl+K`, `?` row 추가 + cheat sheet callout.

### 검증

- `pnpm typecheck` exit 0.
- `pnpm lint` exit 0 (PR #168 머지 후 잔존했던 `TopBar.test.tsx` `container` unused 정리 — 동일 머지에서 새로 들어온 lint rule이 잡음).
- `pnpm test --run` 175 file / **1273/1273 PASS** (신규 9건 — useGlobalShortcuts `?` 3 + ShortcutsCheatSheet 6).

### 결정/편차

- **self-contained 모달** — store 신설 불필요. 외부 trigger도 없음(키 입력만). YAGNI.
- **단축키 데이터 컴포넌트 내부** — 통합 source 토큰화는 별도 v1.x PR. 현재는 docs/01 §12.1이 truth, 컴포넌트는 동등 표현.
- **`?` editable 가드 적용 (vs `⌘K` 미적용)** — `/`와 동형. 다른 input에서 `?` 입력 보호.
- **`Shift` modifier 검사 제외** — `?`는 자연스럽게 `Shift+/`로 입력되므로 `e.key === '?'` 직접 매칭이 명확.
- **lint cleanup 부수 처리** — `TopBar.test.tsx` `container` unused 처리. 별도 PR로 분리할 가치 없음 (1줄).

### 트랙 외 후속

- 단축키 데이터 토큰화 (`lib/keyboardShortcuts.ts` single source) — v1.x.
- G7 mobile-view — 별도 마일스톤.
- 단축키 사용량 분석 (audit 또는 telemetry) — v2.x.

### dev-docs

- `dev/active/shortcut-cheatsheet/{plan,context,tasks}.md` — 머지 후 별도 archive PR.

---

## 2026-05-11 — trash-retention-mutation Phase A (spec 설계 + dev-docs 부트스트랩)

### 범위

휴지통 보존 정책 mutation UI 트랙 진입 — Phase A는 **spec 설계 + dev-docs 부트스트랩** docs-only PR. backend/frontend 코드 변경 없음. Phase B/C는 별도 PR로 분리.

### 변경 핵심

- `dev/active/trash-retention-mutation/` plan/tasks/context 3종 신규.
- `docs/02 §2.12` `trash_policy` 테이블 schema (V17 — single-row, CHECK days BETWEEN 7 AND 90).
- `docs/02 §7.11` PUT `/api/admin/trash/policy` endpoint 명세 추가 + 본문 §7.11.1 신규 (request body / response / 적용 정책 / 2인 승인 v1.x deferred).
- `docs/03 §4.1` `admin.retention.changed` audit event 추가 (target_type='trash_policy', metadata `{before, after, appliesTo:'new-deletes-only'}`).
- `docs/03 §6.4.3` dual-approval action_type 표 — `retention_change` 행 갱신 ("deferred" → "단일-approver MVP 활성화 + framework hook point").
- `docs/04 §8.3` 휴지통 정책 항목 — read-only viewer + mutation UI 활성 마커.
- `docs/04 §9.2` 보존 정책 — yml + V17 테이블 dual-source + 변경 영향(신규만 적용, 기존 row 재계산 안 함) 명시.
- `docs/04 §15.6` Wave 2 backlog → v1.x 전환 표 — `/admin/trash/policy` UI 항목 strikethrough + trash-retention-mutation 트랙 backlink.

### 핵심 결정 (Phase A)

- **저장 전략**: Option A — single-row `trash_policy` 테이블 (`id=1` CHECK, `retention_days BETWEEN 7 AND 90` CHECK). KISS/YAGNI — 일반화된 `app_settings` 패턴은 quota 등 후속 트랙에서 검토.
- **신규 row 적용 정책**: 변경은 신규 soft-delete만 적용. 기존 trash row의 `purge_after`는 재계산 안 함 — 일수 감소 시 hard purge 폭증 회피. UI confirm dialog + audit metadata `appliesTo:'new-deletes-only'`로 명시.
- **2인 승인**: 단일-approver MVP (단일 ADMIN 즉시 적용). Framework는 v1.x++ deferred(`app.dual-approval.retention-change.enabled`). 본 endpoint는 framework 도입 시 hook point로 사용.
- **CHECK 제약**: 7..90 (docs/04 §8.1 spec과 일치). 0/음수/100+ 입력은 backend에서 400 VALIDATION_ERROR.
- **TrashRetentionProperties yml** 잔존 — V17 migration이 row 부재 시 yml 값으로 idempotent INSERT (Phase B에서 `ApplicationReadyEvent` listener 또는 `@PostConstruct` 기반). 운영자 yml override 이력 보존.

### 검증

- 코드 0줄 — typecheck/lint/test 무관.
- spec 정합성 self-review: docs/02 schema ↔ docs/03 audit event ↔ docs/04 운영 명세 ↔ dev-docs plan 4중 일관성 확인.

### 다음 세션 컨텍스트

**Phase B (별도 PR — backend)**:
- V17 migration `trash_policy` (single-row, CHECK, FK updated_by ON DELETE SET NULL)
- `TrashPolicy` entity + `TrashPolicyRepository` + `TrashPolicyService` (`getRetentionDays`, `updateRetentionDays`)
- `RetentionPolicyChangedEvent` + `TrashPolicyAuditListener` (TeamAuditListener 패턴 답습)
- `AuditEventType.RETENTION_POLICY_CHANGED` + `AuditTargetType.TRASH_POLICY`
- `AdminTrashPolicyController.update(PUT)` + DTO + GlobalExceptionHandler 매핑
- `FileMutationService` + `FolderMutationService` `TrashRetentionProperties.days()` → `trashPolicyService.getRetentionDays()` 전환
- `TrashRetentionProperties` 처리 — V17 row 부재 시 app 부팅 시 yml 값으로 INSERT
- 회귀 가드 backend test 추가

**Phase C (별도 PR — frontend)**:
- `api.updateTrashPolicy(days)` + 회귀 가드
- `useUpdateTrashPolicy` hook + invalidate `qk.adminTrashPolicy()` + 회귀 가드
- `RetentionPolicyEditor` 컴포넌트 — input + 감소 경고 + ConfirmDialog flow
- `/admin/retention` page mutation 섹션 wire (admin 보유 + 데이터 로드 시 노출)
- 회귀 가드 vitest 추가

---

## 2026-05-11 — 🎨 design-fidelity G2/G3 + 사이드바 collapse 트랙

### 범위

`dev/active/design-handoff-gap-report-2026-05-10.md`의 G2(TopBar 3-column grid + 햄버거)·G3(SearchBar ⌘K + 폭) 갭을 사이드바 collapse 구현과 함께 단일 PR로 처리. Plan B(PR #139) 후 사이드바 3-section 구조까지 머지됐으나 collapse 토글이 미구현 상태였음 — 디자인 핸드오프 의도와 함께 closure.

### 변경 핵심

**Store + Layout:**
- `frontend/src/stores/sidebarChrome.ts` (NEW): `collapsed: boolean` + `setCollapsed/toggle`, `sidebar-chrome:v1` localStorage persist. `sidebarTree`(폴더 expand) 와 책임 분리. 단위 테스트 3건.
- `frontend/src/app/(explorer)/layout.tsx`: aside 폭 transition (`w-[248px]` ↔ `w-0`) + `transition-[width] duration-200` + `overflow-hidden` + `aria-hidden={collapsed}`. 내부 컨텐츠는 고정 폭 248px wrapper로 잘림 방지. `'use client'` 전환 (store 소비).

**TopBar 3-column grid (G2):**
- `frontend/src/components/topbar/TopBar.tsx`: `flex justify-between` → `grid grid-cols-[auto_1fr_auto]`. 좌측 햄버거 (`lucide Menu`, `aria-pressed={collapsed}`, `aria-label="사이드바 접기/펴기"`), 중앙 SearchBar 컨테이너 (`mx-auto w-full max-w-[560px]`), 우측 TweaksPanel + Avatar. 햄버거는 collapsed 상태에서도 노출(re-open 진입점).
- 테스트 3건 (3-col 구조 / 클릭 toggle / aria-pressed sync).

**SearchBar polish (G3):**
- `frontend/src/components/topbar/SearchBar.tsx`: `h-[30px]` (디자인 styles.css L629), 우측 영역에 query 비어있고 unfocused일 때 `⌘K` kbd 칩 / query 있을 때 clear 버튼(`lucide X`). 폭은 부모 grid가 max-w-[560px]로 결정. placeholder는 "파일 검색"만 — 시각 hint는 kbd 칩이 담당.
- 테스트 +3건 (h-30 + placeholder / kbd 칩 / focus 시 hint 숨김 / clear 토글) — 누적 7건.

**Global shortcut (G3):**
- `frontend/src/hooks/useGlobalShortcuts.ts`: `⌘+K` / `Ctrl+K` 분기 추가 (modifier + `key.toLowerCase() === 'k'`, `Shift`/`Alt` 조합 무시). **editable 가드 미적용** — 다른 input에서도 검색 호출 가능 (VS Code 패턴). 기존 `/` 호환 유지(editable 가드 그대로).
- 테스트 +5건 (Ctrl+K / ⌘K / input 안에서도 dispatch / Ctrl+Shift+K 무시 / 대문자 'K') — 누적 12건.

**Docs:**
- `docs/01 §2` Sidebar Chrome callout (collapse store, persist 키, 책임 분리).
- `docs/01 §10` 글로벌 단축키 callout (`⌘K`/`Ctrl+K` + editable 가드 차이).
- `docs/01 §17` TopBar 3-column grid + aside transition callout.

### 검증

- `pnpm typecheck` exit 0.
- `pnpm lint` exit 0.
- `pnpm test --run` 174 file / **1264/1264 PASS** (신규 sidebarChrome 3 + TopBar 3 + SearchBar +3 + useGlobalShortcuts +5 = 누적 신규/보강 14건).

### 결정/편차

- **store 분리** — `sidebarTree`(폴더 expand + 30일 TTL + section collapsed) 와 책임 분리. 단일 boolean 분리가 추후 mobile-view(G7) 통합에도 명확.
- **w-0 transition 채택 (display:none 미사용)** — UX 부드러움 + 자식 컴포넌트 unmount 회피(렌더 비용 + state 보존). `overflow-hidden + aria-hidden` 로 SR/시각 모두 처리.
- **⌘K editable 가드 미적용** — 다른 input에서도 검색 호출 가능해야 운영자 UX 자연. `/` 는 의도된 차이(editable 안에서 슬래시 입력 보호).
- **placeholder 단순화** — "파일 검색 ( / )" → "파일 검색". 단축키 시각 hint는 우측 kbd 칩이 owns. 단축키 변경 시 placeholder 텍스트 분기 회피.
- **layout 'use client' 전환** — 기존 AuthGuard/DndProvider/TopBar 가 모두 client였으므로 server-only 가치 없음. KISS.

### 트랙 외 후속

- **G4 FileTable 6열** — M7 권한 마일스톤 종속.
- **G5 density 토글** — viewStore 신규 또는 sidebarChrome 확장.
- **G6 GridView 172px** — PR #148에서 처리됨.
- **G7 mobile-view** — `lg:` breakpoint + sidebar/RP auto-hide. 본 트랙 store와 통합.
- **단축키 cheat sheet (`?`)** — v1.x backlog.

### dev-docs

- `dev/active/design-topbar-sidebar-collapse/{plan,context,tasks}.md` — 머지 후 별도 archive PR.

---

## 2026-05-11 — chore/dev-active-archive (orphan dev-docs 2건 → `dev/completed/`)

### 범위

`dev/active/`에 잔존하던 종료 트랙 dev-docs 2건을 `dev/completed/`로 이동. 코드 0줄, 회귀 0.

### 변경

- `dev/active/dev-preview-stabilization/` → `dev/completed/` (T1·T2 PR #152, T3 PR #153, T4·T6 PR #151, T5 PR #150 — 모두 머지 완료, 트랙 closure 마커 자체 tasks.md에 명시됨)
- `dev/active/team-centric-pivot-plan-c/` → `dev/completed/` (Plan C 본체 PR #140 머지 완료, 2026-05-10)
- `dev/active/design-handoff-gap-report-2026-05-10.md`는 잔존 — gap report는 트랙 단위 dev-docs가 아닌 단일 doc, gap 자체는 design-refresh-admin 후속 트랙(T7-P2 등)에서 점진적으로 처리되므로 active 유지가 적절.

### 검증

- 코드 0줄, typecheck/lint/test 무관.
- `git diff --stat` 결과: rename only (파일 내용 무변경).

### 다음 세션 컨텍스트

- `dev/active/`가 깔끔해져서 다음 세션이 active 트랙 식별 비용 감소.
- v1.x backlog 잔여(spec 필요): 휴지통 보존 정책 mutation UI / quota mutation UI / 2인 승인 framework / progress streaming / GrantPermissionDialog v2.x ROLE/TEAM grant 평가.

---

## 2026-05-11 — csrf-helper-sweep (`readCookie('XSRF-TOKEN')` → `await ensureCsrfToken()` 일관화)

### 범위

`api.ts`의 mutation 콜사이트에서 CSRF 토큰 조회가 두 패턴으로 나뉘어 있던 sleeping inconsistency 정리.
- `readCookie('XSRF-TOKEN') ?? ''` (sync, 16+ 사이트) — cookie 없으면 빈 문자열 → backend 403.
- `await ensureCsrfToken()` (async, bootstrap fallback 12 사이트) — cookie 없으면 `/api/auth/csrf` GET으로 부트스트랩.

후자가 cold-start 안전하므로 mutation 사이트 전부 후자로 통일.

### 변경 핵심

- `frontend/src/lib/api.ts` 18 콜사이트 변경:
  - `const csrf = readCookie('XSRF-TOKEN') ?? ''` × 16 → `const csrf = await ensureCsrfToken()` (replace_all 일괄)
  - `createTeam`/`createFolder` (2): `readCookie + ...(csrf ? {} : {})` 조건부 spread → `ensureCsrfToken + 항상 헤더 포함` (ensureCsrfToken은 SSR 시 '' 반환 → harmless)
- `uploadFile` XHR (1 사이트, line 623) **의도적 예외**: XHR sync 시그니처라 `await` 불가. 인라인 주석으로 sweep 예외 사유 명기. 업로드는 인증된 세션 중에만 발생하므로 cold-start 경로 사실상 부재.
- `ensureCsrfToken` 본체(line 1996/1999)의 readCookie는 helper 내부 — 그대로 유지.

backend 무변경, 컴포넌트 무변경. 18 콜사이트 모두 이미 `async` 함수 내부라 시그니처 변경 없음.

### 검증

- `pnpm --filter frontend typecheck` exit 0.
- `pnpm --filter frontend lint` exit 0.
- `pnpm --filter frontend test --run` 회귀 zero (예상) — 기존 테스트는 `document.cookie = 'XSRF-TOKEN=...'`로 cookie 직접 set, `ensureCsrfToken`이 readCookie로 즉시 반환하므로 fetch bootstrap 미발생, 기존 spy 기대치 무영향.

### 결정/편차

- **upload XHR 예외 유지** — async 시그니처 도입은 useUpload 호출자 변경 필요 → 본 sweep 범위 초과. 인라인 주석으로 의도 명기.
- **conditional spread 제거** — `ensureCsrfToken`이 빈 문자열이라도 반환 보장하므로 `...(csrf ? {} : {})` 패턴 불필요. 단순화 + 일관성.
- **테스트 setup.ts에 전역 XSRF-TOKEN cookie 기본값 추가** — `ensureCsrfToken`이 cookie 부재 시 `/api/auth/csrf` GET fetch를 trigger하면, 기존 fetch mock의 응답 큐를 의도치 않게 소비. 결과적으로 mutation 본 fetch가 undefined → `Cannot read properties of undefined (reading 'ok')`. 7 test 파일(shares/trash/versions/moveFiles/adminTrashBulk/renameFile/adminToggleCron) 회귀 발생. 해결: `frontend/src/test/setup.ts`의 beforeEach에 `document.cookie = 'XSRF-TOKEN=test-csrf-default; path=/'` 추가 — 모든 테스트가 ensureCsrfToken을 통과해도 fetch bootstrap 미발생. cookie 부재 동작 검증이 필요한 테스트는 개별 unset 가능.

### 다음 세션 컨텍스트

- 본 sweep으로 cold-session에서 mutation 시 403 회귀 가능성 제거.
- v1.x backlog 잔여: 휴지통 보존 정책 mutation UI / quota mutation UI / 2인 승인 framework 실 구현 / progress streaming / GrantPermissionDialog v2.x ROLE/TEAM 도입(backend resolver 확장 선결).
- 다음 트랙 후보: 휴지통 보존 정책 mutation UI (큰 트랙, spec부터) 또는 stale worktree 정리 (housekeeping).

---

## 2026-05-11 — grant-permission-dialog Phase C+D (subject 분기 + ResourcePermissionsList 통합)

### 범위

PR #157 Phase B + PR #162 spec realign 후속. Phase C(subject 라디오 + Combobox 재사용)와 Phase D(`ResourcePermissionsList` 통합)를 단일 PR로 병행 — co-session 협업.

### 변경 핵심

**Phase C (`GrantPermissionDialog.tsx`)**:
- `subjectType` state 추가 (`'everyone' | 'user' | 'department'`).
- 라디오 3종 + 조건부 `UserSearchCombobox`(A14)/`DepartmentSearchCombobox`(A16) 재사용.
- 라디오 전환 시 다른 subject 선택 reset.
- 미선택 + submit → inline alert 차단.
- ROLE/TEAM은 PR #162 spec realign 결정대로 v2.x backlog (§14.5.4 callout).

**Phase D (`ResourcePermissionsList.tsx`)**:
- 헤더 우측 "권한 부여" 버튼 (`aria-haspopup="dialog"`, `aria-expanded` sync).
- `useState open` + `GrantPermissionDialog` 마운트 — 다이얼로그 첫 사용자 가시화.
- 가드: `usePermission().PERMISSION_ADMIN` 보유 + `!isLoading && !isError` (loading/error 동안 trigger 차단, invalidate 후 깜빡임 방지).

**회귀 가드 vitest** (누적):
- `GrantPermissionDialog.test.tsx`: Phase B 7 + Phase C 9 = 16 (라디오 노출 2 / 미선택 inline 2 / 분기 body shape 2 / radio 전환 reset 1 / 400 1 / generic fallback 1).
- `ResourcePermissionsList.test.tsx`: Phase D 3 (버튼 클릭 → dialog open / admin 미보유 시 트리거 미노출 / error 시 트리거 미노출). dialog 본체는 stub으로 격리, trigger wire만 검증.

### 검증

- `pnpm --filter frontend typecheck` exit 0.
- `pnpm --filter frontend lint` exit 0.
- `pnpm --filter frontend test --run src/components/files/` 118/118 PASS.

### 결정/편차

- **Phase C와 D를 단일 PR로 병행** — Phase D는 Phase C 골격(subject 라디오) 완성 후에야 사용자 가시 가치가 있어 분리 인센티브 약함.
- **co-session 협업 모델** — 같은 worktree(`grant-perm-c`)에서 다른 세션이 Phase D 동시 작업. dev/process로 working files 선언, GrantPermissionDialog.test.tsx만 양쪽 추가 형태로 머지. `ResourcePermissionsList.tsx`는 co-session 단독, `GrantPermissionDialog.tsx`는 본 세션 단독. 양쪽 모두 typecheck/lint/test 통과 후 합본 commit (PR #163).
- **ResourcePermissionsList.test.tsx에서 dialog stub** — GrantPermissionDialog 본체 행위는 별도 파일이 책임, Phase D 통합 테스트는 trigger wire에 집중.

### 다음 세션 컨텍스트

- 다이얼로그가 첫 사용자 가시화 — 운영자 SQL/ShareDialog 우회 unblock.
- 후속 트랙(별도 PR로): csrf-helper-sweep (`readCookie` vs `ensureCsrfToken` 분기 통일).
- v2.x backlog: ROLE/TEAM grant 평가 (backend `PermissionRepository.findEffective` 확장 선결).

---

## 2026-05-11 — 🏁 grant-permission-dialog 트랙 종료 (Phase B → C → D 전 phase 완료, dev-docs archive)

### 범위

권한 부여 다이얼로그 트랙의 모든 phase가 master에 도달. 본 closure PR은 **dev-docs archive** + **progress.md 정리 + Phase B/C/D 완료 마커** 단일 책임. 코드 0줄.

### 트랙 closure 표

| Phase | PR | 머지 | 산출물 |
|---|---|---|---|
| A — spec 설계 | (no PR, docs only) | 2026-05-09 | `docs/01 §14.5` 신규 (architecture, wire body, error envelope, phase 분할) |
| B — api/hook/Dialog 골격 | #157 | 2026-05-10 | `api.grantPermission` + `useGrantPermission` + `GrantPermissionDialog` (subject=`everyone`) + 회귀 가드 18건 |
| (spec realign) | #162 | 2026-05-10 | ROLE/TEAM subject v2.x backlog 분리 (spec/impl 정합) |
| C — subject 분기 | #163 (codepath 81c55e4) | 2026-05-11 | `everyone`/`user`/`department` 라디오 + `UserSearchCombobox`(A14) + `DepartmentSearchCombobox`(A16) 재사용 + 회귀 가드 9건 (누적 16건) |
| D — `ResourcePermissionsList` 통합 | #163 | 2026-05-11 | 헤더 우측 "권한 부여" 버튼(`aria-haspopup`/`aria-expanded`) + `useState` open + dialog 마운트, 가드: PERMISSION_ADMIN + `!isLoading && !isError` + 회귀 가드 3건 |

본 closure PR은 archive만 — dev-docs 2건을 `dev/active/` → `dev/completed/`로 이동:

- `dev/active/grant-permission-dialog-phase-b/` → `dev/completed/grant-permission-dialog-phase-b/`
- `dev/active/grant-permission-dialog-phase-cd/` → `dev/completed/grant-permission-dialog-phase-cd/`

`dev/completed/grant-permission-dialog-phase-cd/grant-permission-dialog-phase-cd-plan.md` front matter `status: in_progress` → `completed` + `merged: PR #163` 마커.

### 검증

- 코드 0줄 — typecheck/lint/test 무관.
- `git diff --stat` 결과: `docs/progress.md` + dev-docs 6 rename만.
- 회귀 영향: 0 (이전 PR #163 CI green: frontend vitest + backend junit 둘 다 SUCCESS).

### 결정/편차

- **Phase B archive를 같이 처리** — Phase B(PR #157) 머지 후 별도 archive PR 미생성 상태로 dev/active에 잔존. closure 단위 일관성을 위해 본 PR에서 함께 이동.
- **새 ADR 미신설** — Phase A spec(2026-05-09)이 이미 §14.5에 결정 박힘. closure는 운영 정리.
- **Co-session 협업 기록** — Phase C/D 본체(81c55e4)는 co-session이 푸시 (memory `feedback_co_session_collab` 패턴). 본 트랙은 closure만 본 세션이 마무리.

### v1.x backlog 잔여 (트랙 외)

- **ROLE/TEAM grant 평가 도입** — backend `PermissionRepository.findEffective` 쿼리 확장 + `PermissionResolver` 분기 + `subject_id` UUID-encoded role enum 또는 컬럼 분리. v2.x backlog (§14.5.4 callout).
- **admin/permissions 페이지 전역 grant** — resource picker(folder tree + file search) 컴포넌트 신규 작성 필요. v2.x.
- **Dialog modal stacking** — ShareDialog와 동시 동작 시 z-index/focus trap 회귀 spot-check (현재 양 다이얼로그 동시 열림 경로 부재).

---

## 2026-05-11 — grant-permission-spec-phase-c-realign (ROLE/TEAM subject 제외 정정)

### 범위

Phase B 종료 직후 Phase C 진입 사전 검증에서 spec/impl 불일치 발견. 원래 다음 트랙 후보였던 "backend SubjectRef.id String 완화"가 실제로는 무효한 처방임이 드러나, docs-only 정정으로 pivot.

### 발견된 불일치

- `docs/01 §14.5.4`: `subject.type`을 `'user' | 'department' | 'role' | 'everyone'` 4종으로 명시.
- backend reality:
  1. `permissions.subject_id`는 `UUID NOT NULL` (V5 마이그레이션). 'role' subject id로 제안됐던 enum 문자열('MEMBER'/'AUDITOR'/'ADMIN')은 UUID 컬럼에 저장 불가.
  2. `PermissionRepository.findEffective` 쿼리는 `subject_type IN ('user', 'everyone', 'department')`만 매칭(`PermissionRepository.java:84-94`). 'role' grant row는 INSERT돼도 `PermissionResolver`가 무시.
  3. `AdminPermissionRowResponse.java:14` 주석에도 명시: `'role' → subject_id의 UUID text (V5 schema artifact, MVP 평가 미사용)`.
  4. 'team' subject도 동일 — Plan C에서 share endpoint에 'team' 추가됐으나 grant 평가는 여전히 user/dept/everyone만. team folder 권한은 `WorkspaceMembershipResolver`가 membership 기반으로 자동 부여(별도 경로).

### 변경 핵심 (docs + 1 type 좁히기)

- `docs/01 §14.5.4` wire body 타입: `'user' | 'department' | 'everyone'`로 축소 + ROLE/TEAM 제외 사유 callout (`docs/03 §3.4.3` 참조).
- `docs/01 §14.5.5` Subject 분기 표: 'role' 행 제거. v2.x backlog 항목으로 분리 (ROLE/TEAM 도입 시 backend resolver 확장이 선결).
- `docs/01 §14.5.9` Phase 분할: Phase C 범위 = USER + DEPARTMENT만.
- `docs/01 §14.5.10` 결정/편차: "Subject 4종" → "Subject 3종" 정정 + 2026-05-11 정정 사유 명기.
- `frontend/src/types/permission.ts` `GrantPermissionRequest` interface: type union 축소 (`'user' | 'department' | 'everyone'`) + Javadoc 갱신.

backend 무변경, Phase B 회귀 가드 vitest 18건 무영향 (Phase B는 'everyone'만 송신).

### 검증

- `pnpm --filter frontend typecheck` exit 0.
- `pnpm --filter frontend lint` exit 0.
- `pnpm --filter frontend test --run` 회귀 zero (Phase B 18건 + 기타 모두 PASS).

### 결정/편차

- **원래 제안한 "backend SubjectRef.id String 완화" 폐기** — 진단은 맞았으나 처방이 틀림. DB 컬럼이 UUID이고 evaluator가 user/dept/everyone만 보므로 String 완화로도 ROLE/TEAM 평가 불가능. 정확한 fix는 spec 정정 + backend resolver 확장(별도 트랙).
- **Type 좁히기 동반** — spec에서 빼면서 wire 타입에서도 빼야 contract drift 회피. Phase B 골격은 'everyone'만 송신하므로 타입 축소가 회귀 zero.
- **`docs/03 §3.4.3` 무수정** — 원래 truth-of-source. spec §14.5에서 backlink만 추가.

### 다음 세션 컨텍스트

- **Phase C** 진입 가능 — USER + DEPARTMENT subject picker만 구현. UserSearchCombobox/DepartmentSearchCombobox 재사용 (ShareDialog와 동형). 회귀 가드 vitest 추가.
- **ROLE/TEAM grant 평가 도입 (v2.x backlog)** — backend resolver 확장 + UUID-encoded role enum 매핑 또는 `subject_id` 타입 분리 + `PermissionRepository.findEffective` 쿼리 확장. 별도 트랙으로 분리.
- v1.x backlog 잔여: 휴지통 보존 정책 mutation UI / quota mutation UI / 2인 승인 framework 실 구현 / progress streaming / CSRF helper 일관화 sweep.

---

## 2026-05-10/11 — design-refresh-admin 트랙 완료 (T7 + T8)

### 완료
- **PR #154** T7-P1 — Admin chrome 8탭 골격
  - admin.css 921L 이식 (admin/kpi/section-card/admin-table/role-status-chips/
    dept/storage/cleanup/flags/audit/retention + teams + modal + permissions)
  - 좌측 AdminSideNav 제거 → 가로 8탭 AdminTabBar (overview/멤버/팀/폴더권한/
    저장공간/공유정책/감사로그/보관)
  - 상단 AdminTopHeader + tenant chip
  - AUDITOR 가시성 유지 — audit 탭만 (wave1.5-auditor-admin-ui-access 답습)
  - `/admin/teams`, `/admin/sharing` placeholder 페이지
- **PR #156** T8-P3 — Backend admin team endpoints
  - V16 schema: `teams.color` (#RRGGBB CHECK) + `teams.lead_id` (FK users,
    NOT NULL after backfill)
  - Team 도메인 확장: 9-arg full constructor + 7-arg backward-compat.
    assignLead/changeColor/updateDescription/touchUpdatedAt
  - 5 admin endpoints (모두 `hasRole('ADMIN')`):
    - `GET /api/admin/teams` (list with memberCount)
    - `GET /api/admin/teams/{id}` (detail)
    - `PATCH /api/admin/teams/{id}` (name/description/color/leadId)
    - `DELETE /api/admin/teams/{id}` (soft archive — TeamService.archive 위임)
    - `POST /api/admin/teams/{id}/restore` (un-archive)
  - 새 `AuditEventType.TEAM_UPDATED` + `TeamUpdatedEvent` +
    `TeamAuditListener.onTeamUpdated`
- **PR #158** T8-P4 — Frontend `/admin/teams` 풀
  - hooks 8종 (`useAdminTeams*`) + `qk.adminTeams.{all,list,detail}` +
    `invalidations.afterAdminTeamChanged`
  - Components: TeamsListPanel, TeamDetail, CreateTeamModal, EditTeamModal,
    MemberPickerModal, Avatars
  - multi-step create (POST /api/teams → invite members → PATCH metadata)
  - AUDITOR read-only 게이팅
  - 40 tests 신규 (5 파일)
- **현재 PR** T8 closure
  - `AdminTeamControllerTest` slice 19 tests — 200/204/400/401/403/404/409 매트릭스
  - `GlobalExceptionHandler` — `TeamNameConflictException` → 409 TEAM_CONFLICT
    매핑 추가 (gap fix; 기존엔 500 fall-through)
  - dev-doc `dev/active/design-refresh-admin-2026-05-10` → `dev/completed/`

### 다음 세션 컨텍스트
- **T7-P2** (라우트 rename) — `/admin/users → /admin/members` 등 URL 정리.
  UX 영향 0 (탭바 isActive prefix가 두 URL 모두 활성). 별도 정리 트랙으로
  미룸.
- **T8 follow-on** (필요 시):
  - Folder grid 풀 구현 — 백엔드 team→folder linkage 추가 후 (현재 placeholder)
  - Member dept column — `AdminUserSummary` 확장 또는 새 endpoint
  - Playwright e2e — 등록/멤버 picker/리더 지정/삭제 시나리오
- **디자인 핸드오프 다른 갭** (gap report `2026-05-10`):
  - G2 TopBar 3-col grid + 햄버거 — Plan B 영역
  - G3 SearchBar ⌘K + 폭
  - G4 FileTable 6열
  - G5 density 토글
  - G7 Mobile view (낮은 우선순위)

### 블로커
- 없음 — design-refresh-admin 트랙 closure.

---

## 2026-05-11 — grant-permission-dialog Phase B (api wrapper + hook + dialog 골격, 18 회귀 가드)

### 범위

`docs/01 §14.5` Phase A spec(2026-05-09) 후속 — Phase B 골격 진입. backend `POST /api/{folders|files}/{id}/permissions` (PermissionController#grant, 2026-04~)이 완비된 상태에서 frontend api wrapper · 훅 · 다이얼로그 골격 · 회귀 가드만 추가. ResourcePermissionsList 통합과 USER/DEPT/ROLE 분기는 Phase D/C로 분리.

### 변경 핵심 (commit 후보)

- `frontend/src/types/permission.ts` — `GrantPermissionRequest` interface 추가 (subject 4종 모두 지원, backend SubjectRef 1:1 미러).
- `frontend/src/lib/api.ts` — `api.grantPermission(resource, resourceId, body)` 메서드 (createFolder의 `readCookie('XSRF-TOKEN')` 패턴 답습, `Map<String, PermissionDto>` unwrap).
- `frontend/src/lib/queryKeys.ts` — `invalidations.afterPermissionGrant(qc, resource, resourceId)` 헬퍼 (3종 prefix 무효화: resourcePermissions / adminPermissions / permissions).
- `frontend/src/hooks/useGrantPermission.ts` — useMutation 래퍼, onSuccess afterPermissionGrant, onError pass-through.
- `frontend/src/components/files/GrantPermissionDialog.tsx` — Phase B 골격: subject = `everyone` 고정(라디오 미노출), preset select 5값, expiresAt datetime-local, 409 inline alert, 403/404 toast+close.
- 회귀 가드 vitest 18건:
  - `api.grantPermission.test.ts` (8) — POST 메서드, URL(folder/file), CSRF, body shape, expiresAt ISO, 409/403/404/400 envelope.
  - `useGrantPermission.test.tsx` (3) — invalidate 3종 호출, file resource 분기, error pass-through.
  - `GrantPermissionDialog.test.tsx` (7) — open=false 미렌더, preset 5옵션, submit body, expiresAt 변환, 409 inline alert, 403/404 toast+close.
- `docs/01 §14.5` Status 갱신 (Phase B 완료) + §14.5.9 Phase 분할 진척 마크.

### 검증

- `pnpm --filter frontend typecheck` exit 0.
- `pnpm --filter frontend lint` exit 0.
- `pnpm --filter frontend test --run` 167 file / 1196/1196 PASS — 신규 18 + 기존 회귀 zero.

### 결정/편차

- **CSRF helper 선택** — adjacent `adminRevokePermission`은 `ensureCsrfToken` (async + bootstrap fallback) 사용, 본 트랙은 createFolder/createTeam 패턴인 `readCookie('XSRF-TOKEN') ?? ''` 답습. resource grant는 ResourcePermissionsList 진입 후 호출되므로 cookie bootstrap이 이미 완료된 상태 — async helper 불필요. KISS.
- **NaN expiresAt 가드 제거** — 초기 구현은 `Number.isNaN(d.getTime())` 후 inline alert. datetime-local input은 브라우저가 형식을 강제하므로 dead code 판단 → 단일 표현식 `expiresAtLocal ? new Date(expiresAtLocal).toISOString() : undefined`로 축약. 형식 오류는 backend 400 → submitError fallback 처리. YAGNI 적용.
- **invalidations 헬퍼 추가** — afterShareCreate 패턴 답습. Phase B 단일 caller지만 spec §6.1 "queryKeys.ts가 invalidation의 single source" 정합 위해 helper 도입.
- **Phase B 골격 dead code** — Phase D 통합 전까지 GrantPermissionDialog 호출자 없음. spec §14.5.9가 phase 분할을 명시하므로 의도된 결과. lint/typecheck 통과 확인.
- **Korean wording fix (self-review)** — "권한 부여 권한이 없습니다" → "권한을 부여할 권한이 없습니다" (이중 명사 회피).

### 다음 세션 컨텍스트 (Phase C/D 후속 트랙)

- **Phase C** — subject 분기. `SubjectPicker` 컴포넌트 (USER/DEPARTMENT/ROLE/EVERYONE 라디오), UserSearchCombobox/DepartmentSearchCombobox 재사용, ROLE select(MEMBER/AUDITOR/ADMIN). 다만 backend `SubjectRef.id`가 UUID 타입이라 'role' subject id는 enum 문자열 → backend deserialize 실패 위험. backend 측 SubjectRef를 String으로 완화 또는 별도 path 분리 필요(spec §14.5.5 검토 항목).
- **Phase D** — `ResourcePermissionsList`에 "권한 부여" 버튼 (`usePermission().admin === true` 가드) + GrantPermissionDialog trigger. Phase D 진입 시 Phase B 골격이 처음으로 호출자와 연결됨.
- **Phase B 호출자 미연결 dead code 상태** — code review에서 "호출자가 없으면 왜 머지?"라는 지적 가능. Phase 분할 문서(§14.5.9)와 progress.md로 의도 명시.
- v1.x backlog 잔여: 휴지통 보존 정책 mutation UI / quota mutation UI / 2인 승인 framework 실 구현 / progress streaming.

---

## 2026-05-10 — team-centric-pivot Plan C 종료 (share endpoint subject_type='team' + §4.2 멤버십 cap)

### 범위

Plan C — share grant matrix에 'team' 추가 + §4.2 cap (sharer 멤버권 ≥ 부여 preset) 백엔드 강제. Plan A의 V15 + WorkspaceMembershipResolver 위에 얹어 11+1 task로 구현. ADMIN cross-workspace 절대 금지는 Plan D로 이월.

### 변경 핵심

**Backend**:
- `PermissionService.ALLOWED_SUBJECT_TYPES` ← 'team' 1줄 확장 + 에러 메시지 동기 (Task 1).
- `ShareCommandService.resolveSubjectName` ← TeamRepository 주입 + 'team' 분기 (Task 2).
- `ShareController` wire 회귀 가드 (file/folder 양쪽, Task 3).
- `ShareExceedsMembershipException` 신규 도메인 예외 (Task 4).
- `ShareGrantCapValidator` 신규 — `WorkspaceMembershipResolver` 위임. cap = "preset.permissions() ⊆ sharer 멤버권" 단순 부분집합 비교 (Task 5).
- **Task 5b 발견**: Plan A Task 22의 `WorkspaceMembershipResolver` 멤버권 정의가 spec §3.2(preset 단위)와 어긋남(권한 enum 단위로 좁게 펼침). `Preset.X.permissions()` 호출로 정합화 — 단일 진실의 출처 보존.
- `ShareCommandService.createShares/createFolderShares` cap helper 1회 호출 (자원 workspace 기준, Task 7).
- `GlobalExceptionHandler` `SHARE_EXCEEDS_MEMBER` → 403 매핑 (Task 6).

**Docs**:
- `docs/02 §7.9` share endpoint subject 매트릭스 'team' 명시.
- `docs/02 §8` `SHARE_EXCEEDS_MEMBER` 403 신규.
- `frontend/src/lib/errors.ts` 상수 `SHARE_EXCEEDS_MEMBER` 신규 (Plan C 범위 안 1건만; KISS).

### 검증

- `cd backend && ./gradlew test`: BUILD SUCCESSFUL.
- `cd frontend && pnpm typecheck`: exit 0.
- 신규 backend test: PermissionServiceGrantRevokeTest +1, ShareCommandServiceTest +3 (team 분기 + cap 위반 file/folder), ShareControllerTest +2 (team wire file/folder), ShareGrantCapValidatorTest 신규 5, GlobalExceptionHandlerTest 신규 1, WorkspaceMembershipResolverTest 3 expected 정합화.

### 결정·편차

- envelope code 컨벤션은 bare SCREAMING_SNAKE_CASE (`SHARE_EXCEEDS_MEMBER`) — spec §5.4의 `ERR_SHARE_EXCEEDS_MEMBER` 표기는 spec 작성 표기일 뿐, 실제 wire/docs/frontend는 `ERR_` prefix 없음. 기존 `RENAME_CONFLICT`/`PERMISSION_DENIED`/`DEPARTMENT_CONFLICT` 동형.
- cap 알고리즘 = "preset 펼침 ⊆ sharer 멤버권" 단순형. spec §4.2의 `min(member_default, SHARE_PRESET_MAX)` 의미를 코드로 옮길 때 단일 진실의 출처(`Preset.permissions()`)를 모든 곳에서 호출하는 형태로 정합화.
- ADMIN preset cross-workspace 절대 금지(§4.2 강한 형태)는 Plan C 범위 외 — Plan D(cross-workspace move)에서 cross-scope 분기 추가 시 함께 도입.
- Task 5b는 plan 작성 후 발견된 spec/impl 정합성 결함의 fix — Plan A Task 22가 멤버권을 좁게 정의했음을 Plan C cap 검증 도입 시 표면화. 본 plan에 sub-task로 추가, 다른 세션 PermissionResolver 통합과 충돌 회피(`PermissionResolverMembershipStepTest`는 mock 사용으로 무영향).

### 다음 세션 컨텍스트

- Plan D(cross-workspace move): 본 plan이 도입한 `ShareGrantCapValidator`에 ADMIN preset cross-workspace 절대 금지 분기 추가. preset=ADMIN + sharer.scope ≠ subject.scope 차단.
- Plan B(frontend): share dialog에 team picker + `SHARE_EXCEEDS_MEMBER` 사용자 메시지. errors.ts에 다른 상수 필요해질 때 해당 트랙 PR에서 추가.
- Plan E(휴지통 workspace 분리): scope 컬럼은 이미 도입됨, trash query 확장만 남음.

### 블로커

- 없음.

---

## 2026-05-10 세션 — 🎯 team-centric-pivot Plan F (Team Member Management)

### 범위

사내 시스템 일상 운영 gap 마무리: 팀 OWNER가 UI로 멤버 목록 조회 / 초대 / 역할 변경 / 제거.
backend는 service에 이미 있는 메서드 1:1 wire (신규 도메인 zero). 18 task 자율 실행 (subagent-driven).

### 변경 핵심 (commit 순서)

**Phase 1 backend (T1~T6)**:
- `76f44d8` / `c870503` T1 `TeamMemberResponse` + `TeamMemberRoleUpdateRequest` DTO + `@BeforeAll/@AfterAll` ValidatorFactory 정합화
- `dbed262` T2 `TeamMembershipRepository.findMembersWithUser` (JPQL constructor projection — entity 매핑 추가 없이 read-only JOIN)
- `438a812` / `addeae2` T3 `TeamService.listMembers` + 클린 imports
- `d266dbd` T4 `TeamAuthz.isMember`
- `b451c24` / `d65d5d9` T5 `TeamController GET /members` + 테스트 imports 정합화
- `349765f` / `aa38025` T6 `TeamController PATCH /members/{userId}` + static-import doThrow

**Phase 2 frontend foundation (T7~T9)**:
- `64f394d` T7 `qk.teams.detail/members` + `invalidations.afterTeamMembersChanged` + `TeamMember`/`TeamMemberRole` types
- `1b87f74` T8 `api.{getTeamMembers, inviteTeamMember, removeTeamMember, changeTeamMemberRole}` + 5 fetch-mock tests
- `d3164d5` T9 `useTeamMembers` (read)

**Phase 3 frontend mutations (T10~T12)**:
- `b5baece` T10 `useInviteTeamMember`
- `27ed064` T11 `useRemoveTeamMember` (+ 400 TEAM_OWNER_REQUIRED skip-invalidate test)
- `76a8479` T12 `useChangeTeamMemberRole`

**Phase 4 frontend UI (T13~T17)**:
- `ab61c32` / `07dcfcc` T13 `TeamMemberTable` (3 col + actions, scope=col a11y)
- `0b47e2d` T14 `InviteMemberDialog` (UserSearchCombobox 재사용)
- `f347f90` T15 `ChangeRoleDialog` + `RemoveMemberDialog` + `errors.ts` 신규 (TEAM_OWNER_REQUIRED 등 28 상수)
- `0f4e0c8` T16 `/t/{teamId}/settings/members` 라우트 + `ClientMembersPage`
- `c1e61f7` T17 `WorkspaceSection` 설정 link (hover-revealed, team-only)

**Phase 5 closure (T18)**: 본 commit (docs/02 §7.16 + progress.md + PR)

### 검증

- backend: T1~T6 신규 테스트 13건 + 기존 회귀 zero (`./gradlew test --tests "com.ibizdrive.team.*"`)
- frontend: 1079 테스트 ALL PASS, typecheck exit 0
- 수동 확인 deferred — co-session 충돌 우려로 dev server 미기동, 머지 후 검증

### YAGNI 명시 제외 (사용자 요청: "꼭 필요한 기능만, 사내라 디테일 과잉 금지")

- 팀 archive/restore UI — 사내 빈도 매우 낮음
- 일괄 invite (CSV) — 한 번 클릭으로 충분
- 멤버 검색/필터링 — 30명 미만 팀 가정
- 멤버 활동 로그 페이지 — admin audit 페이지로 충분
- archive 가드 — 데이터 플레인은 team-archive-write-enforcement 트랙

### 다른 트랙과의 관계

- Plan C (PR #140 share team subject) / Plan D (PR #138 cross-workspace move) / Plan E (trash split, 미시작) / team-archive-write-enforcement: **파일 영역 disjoint** — 머지 순서 무관.
- `errors.ts` was untracked in main repo; Plan F included it because dialogs need `TEAM_OWNER_REQUIRED`. Plan C will likely have minor merge with this file (different lines, no overlap).

### 다음 세션 컨텍스트

- 머지 후 dev server 수동 검증 (settings/members 진입 → invite → role 변경 → remove → last-OWNER 강등 차단)
- Plan C 충돌 해결 진행

---

## 2026-05-10 — 🎯 team-centric-pivot Plan E 완료 (휴지통 workspace 분리)

### 범위
6 phase / 15 task. 휴지통을 workspace 단위(부서/팀)로 분리. backend listing endpoint scope 필수화 + restore 진입에 archive guard + cross-scope mismatch. frontend는 `/trash` redirect → `/trash/d/:deptSlug` `/trash/t/:teamSlug` 라우트 + `TrashWorkspaceTabs` 가로 탭.

### 변경 핵심 (commit 별 1줄)

**Phase 1 — Backend listing scope filter:**
- `c08556c` T1 findTrashedPageByScope native query (Folder/File repo, scope_type/scope_id 필터)
- `779b16e` T2 GET /api/trash scopeType/scopeId 필수 + WorkspaceMembershipResolver 가드 (ADMIN bypass) + dbValue() lowercase wire 회귀 가드

**Phase 2 — Backend restore guards:**
- `26080d8` T3 RestoreConflictException Reason enum (NAME_CONFLICT/SCOPE_MISMATCH) + GlobalExceptionHandler body.reason 노출
- `637c3dd` (cherry-pick from team-archive-write-enforcement T1+T2) TeamArchivedException + TeamArchiveGuard helper
- `e7fb4d9` T4 FolderMutationService.restore archive guard + cross-scope mismatch + AdminTrashService Folder catch 분기
- `d989eba` T5 FileMutationService.restore archive guard + cross-scope mismatch + AdminTrashService File catch 분기 + TeamArchivedException 통합 catch

**Phase 3 — Frontend queryKey + 훅:**
- `82de0af` T6 qk.trashList(scopeType, scopeId) 시그니처 (qk.trash() prefix 그대로 → invalidations 영향 0)
- `a8fac02` T7 useTrashList + api.getTrash scope 시그니처 + ClientTrashPage 임시 placeholder (TODO BLOCKED)

**Phase 4 — Frontend 라우트:**
- `d2908fa` T8 ClientWorkspaceTrashPage shared component
- `4cd6942` T9 /trash/d/[deptSlug] route
- `5a7cfab` T10 /trash/t/[teamSlug] route
- `2a9ddfd` T11 /trash redirect handler + EmptyWorkspacesState (ClientTrashPage 삭제, TODO BLOCKED 해소)

**Phase 5 — Frontend UX:**
- `7ddf20a` T12 TrashWorkspaceTabs 가로 탭 (archived dim+🔒)
- `9560d34` T13 TrashRowActions disabled prop + RestoreConflictDialog reason 분기 + buildApiError details 노출

**Phase 6 — Docs sync:**
- `0713936` T14 docs/01 §13/§6.1, docs/02 §6.5/§7.5/§7.6/§7.11/§8, docs/03 §3.5, CLAUDE.md §2, plan-e design §5.1 정밀화

### 검증
- backend: ./gradlew :backend:test BUILD SUCCESSFUL (Testcontainers 통합 테스트는 Docker 미가용 시 SKIP, Plan A 패턴)
- frontend: pnpm typecheck && pnpm lint GREEN, 1111 tests PASS / 149 files
- AdminTrashService bulk restore catch chain 통합: Folder/File RESTORE_CONFLICT (NAME_CONFLICT/SCOPE_MISMATCH switch) + TeamArchivedException → TEAM_ARCHIVED 별개 wire code

### 핵심 결정
- queryKey: `qk.trash()` prefix 유지 (invalidations 영향 0), `qk.trashList(scopeType, scopeId)`만 변경
- 권한: Plan A `WorkspaceMembershipResolver` membership step 자동 평가 (TEAM MEMBER+ DELETE 묵시) + listing 진입 시 workspace 멤버십 fast-fail 가드 (ADMIN bypass) + ADR #32 row-level DELETE 후처리 그대로
- 신규 에러 코드 0 — RESTORE_CONFLICT body.reason ('name_conflict' / 'scope_mismatch') 분기 추가만, errors.ts 무변경
- TeamArchiveGuard cherry-pick 18935d2 활용 (다른 worktree team-archive-write-enforcement T1+T2)
- AdminTrashController 이미 분리 — admin 글로벌 trash 처리 작업 0
- MVP slug = workspace UUID (Plan B 정합)

### 미통합 / 후속
- T7 reviewer I1 (TrashTable scope props → usePermission 전달 X) — 별도 트랙 권장 (usePermission 변경은 spec architecture 영역)
- team-archive-write-enforcement T1+T2 cherry-pick 활용 시 머지 시점에 정합화 필요 (해당 트랙이 본 worktree 이전에 머지되면 자동 통합, 후이면 conflict 또는 follow-up commit으로 cleanup)
- 휴지통 30일 retention 정책 변경 UI는 별도 트랙 (#108 + #114 페어)

### 다음 세션 컨텍스트
- Plan C #140 / Plan D #138 머지 후 본 plan과의 통합 검증 (cross-workspace mismatch 시나리오 실제 발생 가능)
- spec §4.2 useTrash 훅 처리 open question — T7에서 useTrashList로 명명 결정 (별도 closure 노트 불필요, 본 entry로 명시)

### 블로커
- 없음

---

## 2026-05-10 — Plan D PR #138 CI 안정화 (rounds 3~7) + master 머지 (PR #138 머지)

### 범위
Plan D PR #138의 CI 실패 14건을 5 라운드에 걸쳐 해소. Plan D 고유 실패 0건 달성. 마지막에 origin/master(PR #142 + PR #141 머지 포함)를 본 브랜치에 머지.

### 해결한 카테고리
1. **테스트 fixture 스키마 정합** (round 3): files INSERT의 `storage_key` 컬럼 (file_versions에만 존재) 제거 5곳, `idx_permissions_unique` 회피용 subject 분리, `shares.folder_id` FK용 `seedFolder` 헬퍼 도입, FileMutationServiceTest move tests의 same-scope guard 호환 (sibling fixture).
2. **Production 버그 — entity scope revert** (round 3): `CrossWorkspaceMoveService.moveFile/moveFolder`의 step 3 native @Modifying `updateScopeBatch` 후 stale entity로 `saveAndFlush` → JPA dirty-check가 stale scope를 DB에 되돌림. `assignScope(dest)` 동기화로 해결.
3. **Production 버그 — moveFolder root constraint** (round 5): step 3 batch update 시 source가 아직 root(parent_id NULL)인 채 dest scope로 변경 → V14 `idx_folders_root_per_scope` 위반. parent_id 변경을 step 3 전으로 이동.
4. **E2E loginAs CSRF** (round 4): `loginAs`가 `X-CSRF-Token` 헤더를 빠뜨려 POST 요청이 403. `csrf.getFirst("X-CSRF-Token")` 추가.
5. **E2E ScopeRef wire key** (round 5b): 테스트가 `scope.scopeId`로 query했으나 DTO record는 `{type, id}` (spec §5.3 wire format) → `scope.id`로 변경.
6. **Mockito @Primary 빈 + @BeforeEach reset** (rounds 6~7): `@DataJpaTest` 컨텍스트는 `ApplicationContext`도 `ApplicationEventPublisher` 구현체로 등록 → `@Bean` mock과 모호성. `@Primary` 부여 + 테스트 간 invocation 누적 회피용 reset.
7. **master 머지 (round 8)**: PR #142 (Instant→OffsetDateTime JDBC binding fix + reject move-to-root) + PR #141 (TeamArchiveGuard) 가 master에 머지된 후 본 브랜치에 흡수. FileMutationService/FolderMutationService 생성자에 TeamArchiveGuard 파라미터 추가 (compose both Plan D + master). 9 파일 conflict resolve.

### PR 상태
- mergeable: MERGEABLE
- master 머지 후 모든 잔존 회귀(FolderMutationServiceTest.move_toRoot_setsParentNull, TeamPivotEndToEndTest) 자연 해소 예상.

---

## 2026-05-10 세션 — Plan A 라인 follow-on: ERR_TEAM_ARCHIVED Write Enforcement

### 완료
- [team-archive-enforcement] **T1+T2** TeamArchivedException + TeamArchiveGuard + GlobalExceptionHandler 423 → `TEAM_ARCHIVED` 매핑 (commit 18935d2)
- [team-archive-enforcement] **T3** FolderMutationService 5 진입점 가드 (create/rename/move/delete/restore-3arg) + FolderArchivedTeamGuardTest (10 cases) (commit de99427)
- [team-archive-enforcement] **T4** FileMutationService 4 진입점 가드 (rename/move/delete/restore-3arg) + FileArchivedTeamGuardTest (8 cases) (commit 510caa7)
- [team-archive-enforcement] **T5** FileUploadService.upload + FileVersionMutationService.restoreVersion 가드 + FileUploadArchivedTeamGuardTest (4 cases) (commit 0ee3fe8)
- [team-archive-enforcement] **T6** docs/02 §8 `TEAM_ARCHIVED` row "예약" 마커 제거 (정상 운영 항목으로 전환)
- [team-archive-enforcement] dev-docs bootstrap + closure (dev/active → dev/completed)

### 범위
spec §2.2 archive lifecycle ("archived 팀 콘텐츠 read-only") + §5.4 (`ERR_TEAM_ARCHIVED 423`)에서 docs/02 §8 "예약" 항목으로만 선언되어 있던 enforcement 계약을 실제 동작하는 백엔드 가드로 닫음. archived 팀 소속 폴더/파일에 대한 모든 write API → HTTP 423 + envelope code `TEAM_ARCHIVED` + `details.teamId`. read 경로(GET, download)는 그대로 허용.

11개 진입점 모두 `TeamArchiveGuard.assertNotArchived(scopeType, scopeId)` 단일 helper로 차단. DEPARTMENT scope는 helper에서 short-circuit (no-op) — 부서 deactivate는 별도 정책.

### 다음 세션 컨텍스트
- **CrossWorkspaceMoveService TEAM_ARCHIVED 가드** — Plan D 머지 후 (PR #138). 본 세션 미커버. cross-workspace move의 source/destination 양쪽 archive 검증 필요.
- **프론트 423 토스트 wiring** — Plan B 머지(PR #139) 후 `errors.ts` `TEAM_ARCHIVED` 상수와 mutation hook의 onError에서 토스트 표시. UX 측면.
- **archived 팀 read-only UI 시각** — spec §4.5(9) "archived 팀 dim + 🔒". 사이드바/탐색기에서 archived 팀 콘텐츠는 read-only 모드로 진입. 별도 frontend task.

---

## 2026-05-09 — 🎯 team-centric-pivot Plan D 완료 (cross-workspace move backend)

### 범위
spec §5.6 cross-workspace folder/file 이동을 backend(트랜잭션 + invariant) + frontend(컨텍스트 메뉴 + dialog)로 활성화.
backend(Phase 1~6 + Finalize)는 이번 PR에서 완료. frontend Phase 7은 Plan B의 `WorkspaceFolderTree` 머지 후 별도 트랙으로 진행.

### 변경 핵심
- `CrossWorkspaceMoveService` (subtree scope update + permissions cleanup + shares revoke + invariant assert + Spring ApplicationEvent publish)
- `MovePreviewService` (멱등 영향 계산)
- `/move/preview` endpoint (folder + file)
- `/move`에 `allowCrossScope` body 추가 (default false → 기존 same-scope 가드 유지)
- 신규 envelope: `ERR_DEST_WORKSPACE_DENIED` (403), `ERR_INVALID_DESTINATION` (400). 기존 `ERR_CROSS_SCOPE_MOVE` 매핑 wire-up 정상화 (Plan A 잔여)
- 신규 audit event: `folder.moved.cross_workspace`, `file.moved.cross_workspace`
- `CrossWorkspaceMoveCompletedEvent` (SSE는 v1.x 이월)
- frontend `types/audit.ts` 두 wire 멤버 추가

### 결정/편차
- subtree permissions 보존 옵션 → v1.x 이월 (spec §5.6 KISS 명시)
- SSE 실 전송 → v1.x 이월 (이벤트 publish hook만 도입)
- destination=root 직접 이동 차단 (`ERR_INVALID_DESTINATION`) — spec §1.3 정합
- ADMIN preset cross-workspace 절대 금지(§4.2 강한 형태) → 본 plan에서 추가 분기 불필요. permissions 전체 삭제 후 destination 멤버십 기본권만 적용되어 ADMIN cross 부여 경로 차단.
- V6 `shares.permission_id ON DELETE CASCADE` 발견 — Task 18 implementer가 `revokedShareCount`를 step 4(perm delete) 전에 capture하도록 조정. 결과적으로 active shares 0 invariant은 cascade로 자연 만족, audit metadata는 정확.

### 블로커
- 없음.

---

## 2026-05-09 세션 — Plan B (Frontend Foundation)

### 완료
- [team-pivot-fe] workspaces 타입 + api + queryKeys + useWorkspaces (Tasks 1~4)
- [team-pivot-fe] workspacePath builder/parser + useCurrentWorkspace + useCurrentFolder refactor (Tasks 5~7)
- [team-pivot-fe] /d/[dept], /t/[team], /shared/* 라우트 + ClientFilesPage variants + root redirect (Tasks 8~12)
- [team-pivot-fe] Breadcrumb workspace head crumb (Task 13)
- [team-pivot-fe] SidebarSections + WorkspaceSection + WorkspaceFolderTree + lazy children (Tasks 14~22)
- [team-pivot-fe] SharedWithMeSection (flat MVP) + empty states + archived hook (Tasks 23~24)
- [team-pivot-fe] TeamCreateButton + Dialog + useCreateTeam (Task 25)
- [team-pivot-fe] DnD same-workspace constraint + visual feedback (Tasks 27~29)
- [team-pivot-fe] /files/* + folderPath.ts + FolderTree.tsx + view.ts + getFolderTree() 폐기 (Tasks 12, 16, 22)
- [team-pivot-fe] MoveFolderDialog lazy-tree 회복 (Phase 6 follow-up — `MoveFolderTree` 신규)
- [team-pivot-fe] docs/01 + CLAUDE.md 동기화 (Tasks 30~31)
- [team-pivot-fe] code review 1차 fix — `/files` redirect stub 복구, SidebarSectionKind dedupe, Breadcrumb canonical builder, TeamCreateDialog try/catch + Escape, FolderTreeNode/MoveFolderTree 에러 상태, getFolder dead virtual-root 제거

### 다음 세션 컨텍스트
- 공유받음 출처 workspace 그룹핑은 backend가 shares-with-me에 source workspace 메타 노출 후 — Plan C와 함께
- archived 팀 dim/[보관됨]은 backend Team archive endpoint(Plan A2)와 함께 활성
- /trash workspace별 분리 페이지(탭 UI)는 Plan E
- TrashTable 원위치 path display는 backend가 trash item DTO에 originalPath 노출하면 정합 (현재 fallback "원위치 폴더 삭제됨")
- folderTreeUtils.ts는 MoveFolderDialog 재작성 후 orphan — Plan C/D 따라 정리 가능

### PR
- PR #139 — `feat/team-centric-pivot-plan-b-frontend`
- 34 commits ahead of origin/master, 1057+ tests pass

---

## 2026-05-09 — 🎯 team-centric-pivot Plan A 완료 (30/30 task, 100%)

### 범위 (이전 세션 핸드오프 후 추가분)

직전 세션이 24/30(80%)에서 핸드오프된 상태에서 Phase 9 통합 + Task 29 E2E + Task 30 finalize 진행. 두 worktree(`feat/team-centric-pivot-plan-a` + `feat/team-centric-pivot-plan-a-phase9`)가 병렬 진행되어 분기 → cherry-pick으로 통합.

### 변경 핵심

**Phase 9 통합 (cherry-pick from `feat/team-centric-pivot-plan-a-phase9`):**
- `f6e1809` Task 24 folder.create scope 상속 + root via API 차단 (parentId NOT NULL invariant 강제, spec §1.3)
- `3b2b0b5` Task 25 folder.move same-scope 검증 (`CrossScopeMoveException`, ERR_CROSS_SCOPE_MOVE)
- `5011be3` Task 26 file upload parent folder scope 상속
- `cee17b8` Task 27 Folder/File response DTO에 `ScopeRef` 블록 추가

**Phase 11 finalize:**
- `9ece76d` Task 29 `TeamPivotEndToEndTest` — `@SpringBootTest + Testcontainers Postgres` E2E. team create → invite → workspace listing → child folder scope 상속 → membership 기반 permission 검증.

### Cherry-pick conflict 해결 노트

- `ba490c0`(Task 24)이 이전 회차(merge base = c6bbfce) 이전 커밋이라 내 `13f6c0e` Task 16 refactor의 `FolderMutationService.createRootForScope`를 보지 못함. 단순 `--theirs`는 createRootForScope를 삭제 → 수동 hunk-level 수정으로 두 변경(create() 본문 + createRootForScope) 모두 보존.
- Tasks 25-27은 대부분 자동 merge. `37e5778`(Task 26)에서 V13 fixture 충돌 5건 발생 — 그들 버전(parent scope 사용)이 우리 버전('department' literal)보다 의미적으로 정확 → `--theirs` 채택.

### 미통합 (의도적)

- Phase9 worktree의 Tasks 20/21 별개 commit(`01edf48`/`be6f57a`) — `Folder.createWorkspaceRoot` 정적 factory 패턴(Option A). 내 `067f624`/`be6cae3`는 `FolderMutationService.createRootForScope` 위임(Option B). 두 워크트리에 같은 기능을 다른 패턴으로 보유. 통합 시점에 단일 패턴으로 정합화 필요(권장: Option B = 내 버전, Folder 캡슐화 보존).

### 검증

- 본 브랜치 단위 + 통합 테스트 합계 약 47건 PASS (Testcontainers 가용 시).
- E2E `TeamPivotEndToEndTest`는 환경적으로 Testcontainers Docker socket strategy fail 시 SKIP 처리 (peer V12MigrationIT와 동일 조건). 코드 정합성은 컴파일 + Spring context 로드 + 실제 API 호출 시그니처 검증으로 보장.
- 코-세션 WIP `PermissionServiceGrantRevokeTest.grantPermission_teamSubjectTypeAccepted` 1건은 production code 미반영 상태로 working tree에 잔존 — 본 브랜치 commit에 미포함.

### 다음 세션 컨텍스트 (Plan A2 backlog)

- 두 워크트리 통합 — Tasks 20/21 패턴 통일 + phase9 worktree archive.
- archive/un-archive (Team + Department), last-OWNER guard, role 변경, frontend types/audit.ts sync.
- cross-workspace move 가드 (spec §5.6), Department workspace listing UI (spec §4.5).
- PermissionService grant에 `subjectType="team"` 허용 (코-세션 WIP 후속).

---

## 2026-05-09 — 🚀 team-centric-pivot Plan A Phase 3~8+10 (24/30 task, 80%)

### 범위

team-centric-pivot 설계 변경 backend foundation 구현. 직전 세션이 Phase 1~2 (Tasks 1-9: V12-V15 마이그레이션 + 도메인 엔티티 5종)를 완료한 상태에서 이어 받아 다음을 처리.

### 변경 핵심 (commit 순서)

**Phase 3 — Repositories** (`feat/team-centric-pivot-plan-a`):
- `2c2b2cc` Task 10 `TeamRepository` (active-name lookup, Javadoc + AssertJ ID assertion + underscore tests)
- `553ae4b` Task 11 `TeamMembershipRepository` (3 query methods: `findByUserId`, `countByTeamIdAndRole`, `findByTeamId`)
- `7fcefb9` Task 11 follow-up: `findByTeamId` identity assertion 복구 (코-세션 reset로 손실)

**Phase 4 — Audit constants**:
- `b129271` Task 12 `AuditTargetType.TEAM` + `TEAM_CREATED`/`TEAM_MEMBER_ADDED`/`TEAM_MEMBER_REMOVED` (YAGNI: archive/role-change은 Plan A2 이월; frontend `types/audit.ts` sync도 Plan A2)

**Phase 5 — Workspace 추상화**:
- `4737bec` Task 13 `Workspace` interface + `WorkspaceKind` enum + `DepartmentWorkspace` / `TeamWorkspace` adapters (record + null-guard compact constructor)
- `1c9d67a` Task 14 `WorkspaceService.findForUser` + `UserDepartmentLookup` indirection + `DefaultUserDepartmentLookup` + `WorkspaceListing` (3 Mockito tests)
- `87108ec` Task 15 `WorkspaceController GET /api/workspaces/me` (3 WebMvcTest, `@AuthenticationPrincipal IbizDriveUserDetails`)
- `7b82fe2` Task 15 follow-up: `@JsonInclude(NON_NULL)` (peer DTO 일관성)

**Phase 6 — Team CRUD**:
- `41d1933` → `13f6c0e` Task 16 `TeamService.create` (initial) + refactor: audit 위임을 Task 28 listener로 + `FolderMutationService.createRootForScope` 위임 (Folder 캡슐화 보존, 생성자 protected 유지)
- `bc29a9d` Task 17 `TeamService.invite` (idempotent — `findById().orElseGet(...)`, `TeamMemberAddedEvent` publish)
- `311f3c9` Task 18 `TeamService.remove` (basic — last-OWNER guard 없음, Plan A2 이월; `TeamMemberRemovedEvent` publish)
- `f788214` Task 19 `TeamController` + `TeamAuthz` (Spring Security SpEL `@PreAuthorize`) + 3 DTOs (TeamCreateRequest/TeamResponse/TeamMemberInviteRequest) + 7 WebMvcTest

**Phase 7 — Department root folder hook**:
- `067f624` Task 20 `AdminDepartmentService.create` 확장 — root Folder 생성 + `attachRootFolder` (FolderMutationService.createRootForScope 위임)
- `be6cae3` Task 21 `DepartmentRootFolderBackfillRunner` (idempotent dev tool, production 자동 실행 안 함)

**Phase 8 — Permission evaluation rewrite**:
- `c6bbfce` Task 22 `WorkspaceMembershipResolver` — workspace 멤버십 → 묵시적 권한 (DEPT member: READ+UPLOAD / TEAM MEMBER: +EDIT / TEAM OWNER: +DELETE+SHARE)
- `8b4e267` Task 23 `PermissionResolver`에 membership 단계 wiring — `isGranted` 흐름이 explicit/share 우선, membership fallback. `FolderRepository`/`FileRepository` 주입으로 scope lookup. `IbizDrivePermissionEvaluator` 무수정 (자동 혜택)

**Phase 10 — Audit listener**:
- `7739d51` Task 28 `TeamAuditListener` — `@TransactionalEventListener(AFTER_COMMIT)`로 TeamCreatedEvent/MemberAddedEvent/MemberRemovedEvent를 audit_log row로 변환. ADR #24에 따라 audit 실패는 ERROR 로그로 swallow.

**코-세션 commits (origin push됨):**
- `dd9d135` raw SQL fixtures에 `scope_type/scope_id` 추가 (V13 NOT NULL fallout)
- `7f6608e` JPA Folder/FileItem fixtures에 `assignScope` 추가
- `1afd61b` production 서비스 scope 상속 (FolderMutationService.create / FileUploadService.insertNewFile)

### 미완료 (다음 세션)

**phase9 worktree (별도 브랜치 `feat/team-centric-pivot-plan-a-phase9`)에 이미 진행됨**:
- Task 24 `ba490c0` folder.create scope 상속
- Task 25 `d3a0f98` folder.move same-scope validation (`ERR_CROSS_SCOPE_MOVE`)
- Task 26 `37e5778` file upload scope 상속
- Task 27 `c4412fc` Folder/File response DTOs scope block

**양쪽 브랜치 모두 미수행**:
- Task 29 E2E (create team → list members → child folder → scope cascade + permission). Phase 9 의존 — phase9 통합 후 진행.

### 검증

- 본 브랜치 `feat/team-centric-pivot-plan-a` 모든 commit별 `./gradlew test --tests <target>` PASS.
- 누적 새 unit test 약 35건 (10 → 11 → 12 → 13 → 14 → 15 → 16 → 17 → 18 → 19 → 20 → 21 → 22 → 23 → 28).
- 회귀 zero — Task 23에서 PermissionResolver constructor arity 변경으로 `AuditQueryServiceTest.StubPermissionResolver` collateral fix 1건만 발생.

### 결정/편차

- **plan 리터럴 API 가정 다수 부정확** — 실제 코드 기반 adaptation: `NameNormalizer` → `NormalizeUtil`, `Folder.assignNew` → setters + `FolderMutationService.createRootForScope`, `auth.getName()` → `IbizDriveUserDetails.getUser().getId()`, `AuditTargetType.dbValue()` → `wire()`, `@SpringBootTest` 무거운 통합 → Mockito unit + WebMvcTest slice.
- **audit 위임 패턴** — Task 16 초기 구현이 직접 audit emit → review에서 Task 28 listener와 중복 row 위험 발견 → refactor로 도메인 event publish만 남김 (TeamCreatedEvent → Task 28 AFTER_COMMIT). 안전성 +1.
- **Folder 생성자 캡슐화 보존** — Task 16 초기 구현이 cross-package 접근 위해 `protected → public` 변경 → review에서 peer 엔티티(Department/User/Team) 일관성 위반 지적 → `FolderMutationService.createRootForScope(...)` 위임으로 refactor (Option B), 생성자 protected 유지.
- **YAGNI 엄수** — Task 12 audit constants 6개 → 4개로 축소 (archive/role-change/role 변경 audit은 Plan A2). Task 18 last-OWNER guard 미구현. Task 17 권한 검증은 controller layer에 위임.
- **코-세션 origin push 활성** — `feat/team-centric-pivot-plan-a` 브랜치를 코-세션이 origin에 push. 한 차례 reset --hard origin이 내 로컬 rebase 결과(Task 11 identity fix 포함)를 덮음 → 별도 follow-up commit `7fcefb9`로 복구. 이후 양 세션 모두 push 자제.

### 다음 세션 컨텍스트

- **두 브랜치 통합 필요** — `feat/team-centric-pivot-plan-a` (mine, Phase 3-8+10 완료) + `feat/team-centric-pivot-plan-a-phase9` (Phase 9 + 일부 중복). 중복 영역(Tasks 20-21)은 양쪽 functionally 동일하지만 commit SHA 다름. merge 전략 결정 필요.
- **Task 29 E2E**는 phase9 통합 후 진행. create team → invite member → child folder 생성 시 scope 상속 + permission resolver가 membership으로 grant되는지 검증.
- **Plan A2 backlog**: archive/un-archive, last-OWNER guard, role 변경, frontend `types/audit.ts` sync, cross-workspace move (가드레일 포함), Department workspace listing UI.

---

## 2026-05-09 — 📋 spec-permission-grant-dialog 트랙 종료 (단일 자원 권한 grant 다이얼로그 spec, Phase A)

### 범위

v1.x backlog "권한 grant 다이얼로그" 진입 — Phase A 설계 문서만. backend `POST /api/{folders|files}/{id}/permissions`는 완비(`PermissionController#grant`, A1.4), frontend가 누락이라 운영자가 SQL 직접 INSERT 또는 ShareDialog 우회 사용 중. 본 트랙으로 spec 명시 → phase B/C/D 후속 트랙에서 구현.

### 변경 핵심

- `docs/01 §14.5 GrantPermissionDialog` (NEW, 11 sub-sections):
  - Scope (단일 자원 grant, admin 전역 grant는 v2.x)
  - Architecture (ResourcePermissionsList → "권한 부여" 버튼 → GrantPermissionDialog → SubjectPicker / PresetSelector / ExpiresAtInput)
  - api wrapper spec (`api.grantPermission` + X-CSRF-TOKEN 헤더)
  - wire body (`GrantPermissionRequest`)
  - Subject 분기 (USER/DEPARTMENT/ROLE/EVERYONE — 4종 모두)
  - Preset 라벨 한국어 (READ/UPLOAD/EDIT/SHARE/ADMIN 5종)
  - Error envelope mapping (409/400/403/404)
  - 캐시 무효화 (resourcePermissions + adminPermissions + 자기 effective)
  - Phase 분할 (B 골격, C 분기, D 통합)
  - 결정/편차 (ShareDialog 패턴 답습 + ROLE 추가 + Preset 5값 / 단일 다이얼로그)
  - 회귀 가드 spec (Phase B 적용 시 가드 목록)
- `docs/04 §15.3` 운영 런북 backlink 갱신 — "grant 다이얼로그 v1.x deferred" → "docs/01 §14.5 spec 작성, 실 구현 v1.x phase B/C/D".

backend/frontend 무변경, 코드 0줄. ShareDialog UserSearchCombobox/DepartmentSearchCombobox 재사용 명시 (KISS / ULTIMATE INVARIANT 5).

### 검증

- markdown 렌더 깨짐 없음.
- backend `Preset.from(wire)` 실재 enum 5값 일치 (`Preset.java` 검토 후 spec 작성).
- backend `PermissionController#grant` `@PreAuthorize("hasPermission(#id, #resource, 'PERMISSION_ADMIN')")` 가드 명시.

### 결정/편차

- **Phase 분할** — 단일 PR로 BcD 다 묶지 않음. spec 단계만 본 세션, 실 구현은 Phase B 시 backend wire 검증 / Phase C 시 subject 분기 / Phase D 시 통합으로 분할.
- **단일 자원 단위만** — admin/permissions 페이지 전역 grant는 v2.x. resource picker (folder tree + file search) 컴포넌트가 phase 5+ 추가.
- **ShareDialog 패턴 답습** — UserSearchCombobox / DepartmentSearchCombobox 재사용. 추상화 정당화 3+ 규칙(ADR #28 동형) 미충족이라 새 generic picker 신설 거부.
- **ROLE 분기 추가** — ShareDialog는 schema impedance(ADR #37 결정 #5)로 ROLE 미노출이지만 permissions 테이블은 `subject_type='role'` persistable. backend 수정 없이 frontend만 ROLE select 노출.
- **CSRF 헤더 명시** — Phase B 시 csrf-mutation-sweep(#121) 패턴 적용, spec에 `'X-CSRF-TOKEN': csrf` 명시.

### 다음 세션 컨텍스트

- v1.x backlog 잔여: 권한 grant Phase B/C/D / quota / 휴지통 보존 정책 mutation UI / 2인 승인 framework 실 구현 / progress streaming.
- Phase B 진입 시 `api.grantPermission` + 회귀 가드 vitest 부터. backend 검증은 이미 완료, 단순 frontend wire wrapper 추가.
- ResourcePermissionsList 통합(Phase D)은 `usePermission().admin` 가드 필요 — 자원에 PERMISSION_ADMIN 보유한 운영자에게만 "권한 부여" 버튼 노출.

---

## 2026-05-09 — 📐 format-bytes-tb-nan-guard 트랙 종료 (formatBytes TB 단위 + NaN/Infinity 폴백)

### 범위

`frontend/src/lib/formatBytes.ts` 두 가지 작은 회귀 보강:
1. **TB 단위 추가** — 1024 GB 이상이 "1500.0 GB"가 아닌 "1.5 TB"로 표기. v1.x storage.usedBytes 증가 대비.
2. **NaN/Infinity 폴백** — 비정상 입력 시 `'-'` 반환 (`Number.isFinite` 가드). 일시적 nullable API 반환 시 "NaN GB" 표기 회귀 차단.

### 변경 핵심

- `formatBytes.ts` — `Number.isFinite(bytes)` 가드 + TB 분기 (1024 GB 이상은 `(bytes / 1024^4).toFixed(1) + ' TB'`).
- `formatBytes.test.ts` — 회귀 가드 2 그룹 추가 (TB 3 케이스 + NaN/Infinity 3 케이스), 기존 GB 그룹에 1024 GB - 1 byte 경계 1 케이스 보강.

backend 무변경, docs 무변경. utility 단독 (DashboardSummary/StorageOverviewCards/StorageBar/AdminTrashAllPage 모두 자동 혜택).

### 검증

- `pnpm typecheck` exit 0.
- `pnpm lint` exit 0.
- `pnpm test --run formatBytes` 6/6 PASS (기존 4 그룹 + 신규 2 그룹).

### 결정/편차

- **음수 무처리** — backend 자동 계산이라 음수 발생 가능성 ↓. `Number.isFinite`만 가드, 음수는 그대로 통과 (`'-N B'` 표기 가능). 발생 시 별도 트랙.
- **TB 이상 단위 미도입** — PB 등은 v1.x 운영 환경에서 도달 가능성 ↓. KISS, 도달 시 별도 트랙.
- **`'-'` 폴백 텍스트** — `'?'`, `'N/A'` 후보 중 `-`가 사내 베타 운영 화면에 가장 자연스러움 (admin/trash size 컬럼이 이미 `null`을 `-`로 표기).

### 다음 세션 컨텍스트

- v1.x backlog 잔여: 권한 grant 다이얼로그 / quota / 휴지통 보존 정책 mutation UI / 2인 승인 framework 실 구현 / progress streaming.
- formatBytes utility는 사내 베타 운영 KPI 표시의 core라 회귀 가드 보강이 운영 신뢰도에 직접 기여.

---

## 2026-05-09 — 📖 docs-multi-session-runbook 트랙 종료 (사내 베타 운영 런북에 multi-session 자율 작업 트러블슈팅 §15.7 추가)

### 범위

본 세션 trajectory에서 검증된 multi-session 자율 작업 패턴 (mergeStateStatus 5단계 분기 + rebase/force-push-with-lease 복구 + 다른 세션 영역 회피 + master 직접 commit 회피 + Windows lock cleanup)을 docs/04 §15 사내 베타 운영 런북에 §15.7 sub-section으로 추가. 차후 자율 세션 시작 시 재개 가이드.

### 변경 핵심

- `docs/04 §15.7` 신설 (5 sub-sections):
  - §15.7.1 `mergeStateStatus` 5단계 분기 표 (UNSTABLE/UNKNOWN/DIRTY/CLEAN/MERGED)
  - §15.7.2 충돌 복구 — rebase + `--force-with-lease` + reflog 백업 가드
  - §15.7.3 다른 세션 영역 회피 — worktree list / branch -a / open PR 확인
  - §15.7.4 다른 세션이 master에 직접 commit 시 회피 — `origin/master` 직접 base worktree로 격리
  - §15.7.5 Windows lock cleanup 잔여 처리

backend/frontend 무변경, 코드 0줄. 운영 매뉴얼 추가만.

### 검증

- 시각 검증: §15.7 markdown 렌더 깨짐 없음.
- `git diff --stat`: docs/04 + docs/progress.md + dev/active만.
- 백링크: `feedback_co_session_collab` 메모리와 일치.

### 결정/편차

- **§15.7 위치 선정** — §15.6 (Wave 2 backlog → v1.x 전환) 다음, §16 (Dual-Approval 운영) 직전. 본 세션이 발견한 운영 패턴이 §15 런북 영역에 자연스럽게 들어감.
- **메모리와 docs 양쪽 명시** — `feedback_co_session_collab.md`는 자율 세션 즉시 가드, docs/04 §15.7은 운영 런북. 두 경로 다 유지가 KISS — 메모리는 자동 로드, docs는 사람 검수용.
- **5단계 분기 표 형식** — 운영자가 한 번에 인지 가능하도록 mergeStateStatus 5상태를 표로. 본문 풀어쓰지 않음.

### 다음 세션 컨텍스트

- v1.x backlog 잔여: 권한 grant 다이얼로그 / quota / 휴지통 보존 정책 mutation UI (callout #130 페어) / 2인 승인 framework 실 구현 (#124 design 머지됨) / progress streaming.
- 다음 자율 세션은 §15.7을 재개 가이드로 활용 가능 — 백그라운드 패턴, force-push 가드, master 직접 commit 회피 모두 명시.

---

## 2026-05-09 — 🛎️ trash-policy-dual-approval-callout 트랙 종료 (/admin/trash/policy 변경 안내에 dual-approval 의존성 명시)

### 범위

PR #114 (wave2-trash-policy-viewer) read-only viewer 후속. `retention_change`가 docs/04 §15.4 dual-approval workflow 대상으로 등록됐으나 페이지 안내가 yml + 재기동만 명시. 운영자가 v1.x 무중단 변경 도입 후에도 우회 경로 없음을 사전 인지하도록 callout 1단락 추가.

### 변경 핵심

- `frontend/src/app/admin/trash/policy/page.tsx` — "보존 일수 변경 방법" 섹션 끝에 border-top callout 추가: v1.x `PUT /api/admin/trash/policy` 도입 시 dual-approval workflow 적용 예정 + docs/04 §15.4 backlink + hard purge 폭증 방지 이유.
- `frontend/src/app/admin/trash/policy/page.test.tsx` — 회귀 가드 1 케이스 추가 (`2인 승인` + `dual-approval` 텍스트 노출 검증).

backend 무변경, docs 무변경 — UI text 단독.

### 검증

- `pnpm typecheck` exit 0.
- `pnpm lint` exit 0.
- `pnpm test --run admin/trash/policy` 7/7 PASS (기존 6 + 신규 1).

### 결정/편차

- **page.tsx 안에만 노출** — docs/04 §8.3 viewer 섹션은 이미 closure 마커 + mutation deferred 명시. 페이지 안내가 부족했던 부분만 보강. docs는 그대로.
- **별도 ConfirmDialog/Modal 미도입** — KISS, 단순 텍스트 callout. mutation 실 구현 시점에 dual-approval flow UI 별도 트랙.
- **dual-approval 한국어/영문 둘 다 노출** — 운영자가 docs와 워크플로 양쪽 매칭 쉽도록 `2인 승인(dual-approval)` 병기.

### 다음 세션 컨텍스트

- v1.x backlog 잔여: 권한 grant 다이얼로그 / quota / 휴지통 보존 정책 mutation UI(본 callout과 페어로 진행) / 2인 승인 framework 실 구현(v1x-confirm-2admin-design #124 머지됨, 실 구현 별도) / progress streaming(SSE/WS).
- `retention_change`는 dual-approval framework 의존이라 framework 실 구현 후 mutation UI 진입.

---

## 2026-05-09 — 🏁 yml-enabled-cleanup 트랙 종료 (admin-cron-toggle 직접 후속)

### 범위

admin-cron-toggle (PR #102, 2026-05-08) 후 dead config가 된 yml `app.*.enabled` + 4 `*Properties.enabled` 필드 제거. `AdminSystemController.getCronStatus()` viewer를 DB source(`cron_policy` 테이블)로 전환 — 토글 직후 viewer 즉시 갱신.

### 변경 핵심

**Backend:**
- `application.yml` + `application-prod.yml` — 4 cron의 `enabled` 키 제거 + 관련 stale 주석 정리.
- 4 `*Properties` record (`HardPurgeProperties` / `ShareExpirationProperties` / `PermissionExpirationProperties` / `StorageOrphanCleanupProperties`) — `boolean enabled` param 제거, Javadoc 정정.
- `AdminSystemController` — `CronPolicyRepository` 의존성 주입. `getCronStatus()` 4 응답이 `cronPolicyRepository.isEnabled(KEY)`로 DB source 노출 (viewer 토글 즉시 갱신).
- 테스트: `AdminSystemControllerTest`에 `@MockBean CronPolicyRepository` + `@BeforeEach` share=true stub. DB source 회귀 보호 케이스 1 추가. 2 cron job test(`ShareExpirationJobTest` / `PermissionExpirationJobTest`)의 `new XxxProperties(...)` 호출 갱신.

**Docs:**
- `docs/04 §15.4` — yml `app.*.enabled` 표/주석에서 dead `enabled` 언급 제거. 본 트랙 closure 명시.

### 검증

- `cd backend && ./gradlew test` BUILD SUCCESSFUL.
- 신규 audit enum 0, 새 에러 코드 0, schema 변경 0.
- frontend wire format 동일(`CronJobStatusResponse.enabled` 필드 그대로) → frontend 변경 0.

### 다음 세션 컨텍스트

- 4 cron의 schedule/zone/batchSize 등 정의는 yml 그대로 유지. UI 편집은 v1.x 후속.
- 2인 승인 워크플로는 별도 트랙 (Wave 2 closure backlog).

---

## 2026-05-09 — 📚 docs-csrf-token-notation 트랙 종료 (X-CSRF-Token 표기 + frontend 패턴 분기 명시)

### 범위

PR #115 (createFolder hotfix) + PR #121 (sweep 11건) + PR #123 (admin-permission-revoke `adminRevokePermission`) 결과를 docs에 명시화. 코드 0줄. 미래 회귀 진단·새 mutation 추가 시 패턴 결정 단축이 목적.

### 변경 핵심

- `docs/03 §2.2` 세션 모델 표 CSRF 행 — case-insensitive 명시 (backend `X-CSRF-Token` ↔ frontend `X-CSRF-TOKEN` 양쪽 정답).
- `docs/03 §2.2` 표 아래 callout 신규 — frontend 송신 패턴 분기(인증 후 mutation = `readCookie` 동기, 비인증 첫 호출 = `ensureCsrfToken` 비동기, 면제 endpoint = 헤더 미송신) + 회귀 가드 PR backlink (#115, #121, #123).
- `docs/02 §7.1` `/api/auth/csrf` GET Note 확장 — case-insensitive 호환 + frontend 패턴 분기 + 회귀 가드 backlink.

### 검증

- 코드 0줄 — typecheck/lint/test 무관.
- `git diff --stat` 결과: docs/02·docs/03·docs/progress.md + dev/active만.
- backlink PR 번호(#115, #121, #123) 모두 master에 머지된 실제 PR 확인.

### 결정/편차

- **§2.2 표 + callout 한 곳에 집중** — 별도 sub-section 신설 대신 기존 §2.2 영역 내 확장. 큰 문서 라우팅(CLAUDE.md §2)에 기존 매핑 그대로 유지, KISS.
- **§7.1 Note 확장만** — endpoint별 사용 패턴은 §7.1이 권위. §7.x 다른 mutation endpoint 모두에 같은 노트 반복하지 않음 (집약 백링크).
- **ADR 신설 미수행** — ADR #41(self-signup CSRF 면제)이 이미 있으므로 신규 ADR 불필요. 본 보강은 기존 ADR 운영 가이드 정도.

### 다음 세션 컨텍스트

- v1.x backlog 잔여: 권한 grant 다이얼로그 / quota / audit SQL→JSON streaming / 휴지통 보존 정책 mutation UI / 2인 승인 (v1x-confirm-2admin-design #124 머지됨, 실 구현 별도 트랙) / progress streaming(SSE/WS) / admin dashboard KPI 추가.
- 본 트랙으로 CSRF 회귀 진단·새 mutation 추가 시 패턴 결정이 docs로 단축됨. 차후 신규 endpoint 추가 시 `docs/03 §2.2` callout만 보면 됨.

---

## 2026-05-09 — 🛡️ csrf-mutation-sweep 트랙 종료 (X-CSRF-TOKEN 누락 11건 일괄 회귀 차단)

### 범위

PR #115 (`fix-create-folder-csrf`) hotfix에서 발견된 회귀 — `api.createFolder`의 `X-CSRF-TOKEN` 헤더 누락 → ADMIN 운영자도 403. 동일 누락이 frontend `api.ts`의 mutation 11건에 더 존재함을 sweep으로 확인하고 일괄 차단.

### 변경 핵심

`frontend/src/lib/api.ts` — 11건 mutation에 `'X-CSRF-TOKEN': csrf` 헤더 추가 (`csrf = readCookie('XSRF-TOKEN') ?? ''` 동기 패턴, createFolder hotfix와 동형):

| 함수 | method | 비고 |
|---|---|---|
| `restoreVersion` | POST | 파일 버전 복원 |
| `softDeleteFile` | DELETE | 휴지통 이동 |
| `softDeleteFolder` | DELETE | 휴지통 이동 |
| `moveItem` | POST | file/folder 분기 |
| `renameFile` | PATCH | file/folder 분기 |
| `restoreFile` | POST | newName conditional spread 보존 |
| `restoreFolder` | POST | newName conditional spread 보존 |
| `purgeTrashItem` | DELETE | ADMIN-only |
| `revokeShare` | DELETE | F4 |
| `adminToggleCron` | PUT | ADMIN-only, admin-cron-toggle 후속 |
| `postShareCreate` (helper) | POST | createFileShares/createFolderShares 공통 |
| `adminBulkTrash` (export) | POST | wave2-t9-bulk endpoint |

`frontend/src/lib/api.csrfMutations.test.ts` (NEW, 13 케이스): 도메인별 describe(files-folders / versions / trash / share / admin)로 헤더 송신 회귀 가드. restoreFile은 newName 분기 두 케이스 별도 검증.

기존 wire 테스트 호환: `api.adminTrashBulk.test.ts`의 strict 헤더 매칭(`toEqual({'content-type': ...})`)을 `toMatchObject`(subset)로 변경 — 추가 헤더(X-CSRF-TOKEN) 허용. 검증 의도는 'content-type' 송신 자체로 유지.

### 검증

- `pnpm typecheck` exit 0.
- `pnpm lint` exit 0.
- `pnpm test --run api.csrfMutations` 13/13 PASS.
- backend 무변경 — wire 호환 0, 권한 정책 변경 0.

### 결정/편차

- **createFolder 제외** — PR #115에서 단독 처리. sweep base가 origin/master(머지 시점) 였고 sweep PR과 충돌 0 (다른 함수 영역).
- **익명 endpoint 제외** — `signup`/`passwordForgot`/`passwordReset`은 backend `SecurityConfig.ignoringRequestMatchers`로 CSRF 면제(ADR #41 self-signup, A1.5 비밀번호 분실). 헤더 추가 시 부작용은 없으나 KISS 위해 미수정.
- **readCookie 동기 패턴** — `ensureCsrfToken`은 cookie 부재 시 `/api/auth/csrf` 호출 보장용으로 login/logout/passwordChange/admin*에 사용. 인증 후 mutation은 이미 cookie에 토큰이 있어 readCookie로 충분 (createFolder hotfix와 일치).
- **단일 테스트 파일** — 11 함수 + 2 분기 = 13 케이스 한 파일에 묶음. 도메인별 describe 그룹화로 가독성 유지, 파일 폭증 방지.
- **adminTrashBulk 기존 테스트 완화** — 단순 strict header 비교가 새 헤더 추가 차단 → subset 매칭으로 변경. 헤더 추가 자체는 미래 회귀 가드 영역(`api.csrfMutations.test.ts`)이 책임.

### 다음 세션 컨텍스트

- v1.x backlog 잔여: 권한 grant 다이얼로그 / quota / audit SQL→JSON streaming / 휴지통 보존 정책 mutation UI (retention-config #108 + viewer #114 머지 후) / 2인 승인 / full path resolve / progress streaming(SSE/WS).
- 다음 자율 트랙 후보: 작은 KISS — `audit SQL→JSON streaming` 또는 admin dashboard KPI 추가. 큰 트랙(권한 grant CRUD, quota)은 phase 분할 필요.

---

## 2026-05-09 — 🏁 admin-permission-revoke 트랙 종료 (Wave 2 T5 follow-up — `/admin/permissions` 단일 row 철회 UI)

### 범위

PR #120 (wave2-trash-original-path) 후속. `/admin/permissions` viewer가 read-only로만 동작하던 상태에서 **단일 row 철회**를 추가해, 잘못 부여된 권한 또는 만료 임박 이전 즉시 회수가 필요한 grant를 운영자가 클릭 한 번으로 정리할 수 있게 한다. grant 다이얼로그(subject/resource picker)는 v1.x deferred.

### 변경 핵심

**Backend**: 변경 0건. 기존 `PermissionController#revoke` (`DELETE /api/permissions/{id}`)가 `@permissionService.canRevokePermission`로 가드되어 ROLE.ADMIN 통과. audit emit(`PERMISSION_REVOKED`)도 기존 동작.

**Frontend**:
- `lib/api.ts` — `adminRevokePermission(id)` 추가 (DELETE + `X-CSRF-TOKEN` 헤더, 다른 mutation 동형). `adminListPermissions` 직후 위치.
- `hooks/useAdminRevokePermission.ts` (NEW) — `useMutation`, onSuccess `qk.adminPermissions()` 전체 invalidate. 낙관적 업데이트 미적용(§3 원칙 3 정합).
- `app/admin/permissions/page.tsx` — table에 "작업" column 추가 + 행 우측 "철회" 버튼 + ConfirmDialog 인라인. 만료된 grant도 동일 버튼으로 정리 가능. mutation pending 동안 모든 버튼 disabled. ARIA label은 `${subject} ${resource} ${preset} 철회` 패턴으로 spinner-free 식별성 확보.
- `lib/api.adminRevokePermission.test.ts` (NEW, 4 tests) — DELETE 메서드 + URL + CSRF 헤더 회귀 가드 + permissionId encodeURIComponent + 404/403 envelope.
- `app/admin/permissions/page.test.tsx` — `vi.hoisted`로 revokeMutate spy 모킹. 신규 4 tests: 행별 철회 버튼 노출 / 확인 후 mutate(rowId) + dialog 닫힘 / 취소 시 mutate 호출 없음 / 만료 grant도 동일 버튼 정리.

**Docs**:
- `docs/04 §2.2` admin 페이지 표 — `/admin/permissions` 설명에 "단일 row 철회" 추가, grant deferred 명시.
- `docs/04 §2.3` 사이드바 트리 — 동일 closure 마커.
- `docs/04 §15.3` 권한 만료 모니터링 — viewer 정의에서 read-only 제거 + 철회 흐름 문장 추가, grant 다이얼로그 deferred 표기.

### 검증

- `cd frontend && pnpm typecheck` exit 0, `pnpm lint` exit 0.
- `pnpm test --run page.test.tsx api.adminRevokePermission.test.ts` — 18 tests passed (page 14 + api 4).
- backend 무변경 — wire/policy 변경 0.

### 결정/편차

- **revoke만 closure** — grant는 subject/resource picker UI(user-search + folder/file picker + preset + expiresAt)가 작은 PR을 넘는 규모. 1 PR 단위로 자르기 위해 revoke만 우선. 만료 임박 권한 즉시 정리라는 운영 가치는 revoke만으로도 95% 달성.
- **별도 admin endpoint 미신설** — backend `DELETE /api/permissions/{id}` + ROLE.ADMIN 통과로 기존 endpoint 재사용. KISS + 단건 endpoint 무변경 원칙 (admin-trash-bulk와 동일).
- **낙관적 업데이트 미적용** — §3 원칙 3 정합. mutation pending 동안 row 버튼 disabled + invalidate 후 row 사라짐. 사용자 인지 충분.
- **ConfirmDialog 인라인 유지** — AdminTrashAllPage 동일 패턴. 프로젝트에 공통 ConfirmDialog 컴포넌트가 아직 표준화되지 않음. 향후 공통화 시 함께 추출.
- **테스트 모킹 = `vi.hoisted`** — `vi.mock` factory가 hoist되어 module-scope `let` 변수 reference 시 ReferenceError 가능. `vi.hoisted`로 spy 변수 명시 호이스팅.

### 다음 세션 컨텍스트

- v1.x backlog 잔여(권한 grant 다이얼로그 / 보존 정책 mutation UI / 2인 승인 / quota / progress streaming SSE/WS)는 별도 트랙. 권한 grant는 user-search/folder picker 컴포넌트 재사용 가능성 검토 필요.
- 본 트랙으로 docs/04 권한 매트릭스 노드의 "read-only viewer" 표기는 closure로 전환됨. grant 다이얼로그 트랙에서 docs/04 §15.3의 "갱신은 v1.x deferred" 항목도 일부 closure 가능.

### 블로커

- 없음.

---

## 2026-05-09 — 📝 v1x-confirm-2admin-design 트랙 (v1.x deferred Generic dual-approval framework 설계 명세 정합화)

### 범위

여러 종료 entry "다음 세션 컨텍스트"에서 반복 언급된 "2인 승인" 항목을 spec으로 정합화. Legal Hold dual-approval(ADR #46 §6.3.9)을 generic framework로 추출하여 N개 admin destructive action에 일반 적용. 코드 0줄 — 활성화는 v1.x 진입 시.

### 변경 핵심

- **ADR #47 신규** (docs/00 §5) — Generic dual-approval framework: `pending_admin_approvals` 메타 테이블 + state machine(`REQUESTED→APPROVED/REJECTED/CANCELLED/EXPIRED`) + per-action config 게이트. 1차 적용(Tier 0) = role 변경 / trash purge / retention 변경 3종.
- **ADR #46 보강** — Legal Hold release dual-approval를 ADR #47 framework로 이관 명시 (v2.x 진입 시 함께 활성화). `legal_holds.dual_approval_*` 컬럼 deprecated, payload_json='legal_hold_release' 사용.
- **docs/02 §2.11 신설** — `pending_admin_approvals` 테이블 + 인덱스 4종(action_type+status / requested_by+date / expires_at / decided_at) + state machine 다이어그램 + payload_json 스키마 매트릭스. ER 요약(§2.12)에 edge 추가.
- **docs/02 §8** — 에러 코드 4종 신규: `APPROVAL_REQUIRED` 202 / `APPROVAL_SELF` 403 / `APPROVAL_NOT_FOUND` 404 / `APPROVAL_ALREADY_DECIDED` 409.
- **docs/03 §3.1** — `APPROVE_ADMIN_ACTION` 권한 enum 추가, ROLE=ADMIN 매핑.
- **docs/03 §3.2.5** — ADMIN ROLE 매트릭스에 `APPROVE_ADMIN_ACTION` 명시.
- **docs/03 §4.1** — audit enum 4종 신규: `admin.approval.requested/granted/rejected/expired`. cancellation은 audit row 미발행 (KISS).
- **docs/03 §6.4 본문화** — Dual-Approval Framework 9 sub-section: 정책 개요 / 데이터 모델 / Tier 0 매트릭스 / self-approval 차단 / API 계약 / audit 매핑 / per-action 게이트 / expiration cron / v1.x 작업 분해.
- **docs/04 §16 본문화** — 운영 명세 6 sub-section: Tier 0 / 활성화 정책(env별) / 운영 흐름(요청→승인→실행) / `/admin/approvals` UI / 운영 런북(긴급 우회 + 만료 모니터링) / v1.x 분해.
- **dev/active/v1x-confirm-2admin-design/** — `plan.md`/`context.md`/`tasks.md` 3파일. tasks는 §A(현재 — 설계, 12항목 완료) + §B(v1.x 활성화, 9 sub-track 미실행) + §C(Tier 1 후속) + §D(산출물 위치) 구성.

### 검증

- 코드 변경 0건 (백엔드/프론트 0줄). docs + dev-docs only.
- `git diff --stat` 4 파일 (docs/00, 02, 03, 04, progress) + dev/active/v1x-confirm-2admin-design 3파일.
- Cross-reference 정합 검사: ADR #47 ↔ ADR #46 보강, docs/02 §2.11 ↔ §8 ↔ docs/03 §6.4 ↔ docs/04 §16. 모든 forward-reference 해소.
- 다른 worktree 작업 충돌 0: yml-enabled-cleanup #118 / csrf-mutation-sweep #121 / audit-export-filename-timestamp #107 / m12-audit-log-ui (codex). 영역 분리.

### 결정/편차

- **데이터 모델 = Generic framework** (2안 중 선택) — ad-hoc(각 액션 자기 컬럼)는 단발성 mutation에 부적합. Generic이 N개 액션을 단일 service + state machine으로 흡수, 일관 audit/UI/알림.
- **Tier 0 = 3종** (role 변경 / trash purge / retention 변경) — 보안 critical + 회복 불가 + 데이터 손실 위험 명확. cron toggle / user 비활성화는 Tier 1로 명세에 backlog 명시.
- **Per-action config 게이트** (default false) — Legal Hold / share-expired-cron / permission-expired-cron 동형. 환경별 점진 활성화 (dev OFF → staging role-change ON → prod 전체 ON).
- **Self-approval 차단**: 모든 action 공통 `secondary != requested_by` + `role_change` 추가 `secondary != payload.userId` (자기 ADMIN 부여 차단).
- **TTL 7일 + expiration cron** — share-expired-cron 동형, default `enabled=false`.
- **Cancellation audit 미발행** (KISS) — 자가 취소는 보안 이벤트 아니라 운영 이벤트, audit_log 폭증 회피. CANCELLED status 필터로 history 조회.
- **Action 자체 audit 별도 emit** — `role_change` APPROVED 시 기존 `ADMIN_ROLE_CHANGED` listener가 emit. framework는 governance trail(`admin.approval.*`)만 담당.
- **거부**: Multi-secondary 합의("2 of 3 admins") / approval cancellation audit / dual-approval 통계 대시보드 — 모두 v1.x 후반 또는 별도 ADR로 분리.

### 다음 세션 컨텍스트

- 본 트랙은 design-only. 활성화 트리거 = v1.x 진입 시점에 `dev/active/v1x-confirm-2admin-design/v1x-confirm-2admin-design-tasks.md §B` (V_ 마이그레이션 → permission/audit enum → entity/repository/service → controller → 진입점 변형 → ADR #46 보강 → frontend → 검증 → 운영 런북) 그대로 실행.
- Wave 2 closure / MVP 라인과 무관.

### 블로커

- 없음 (v1.x 활성화 트리거는 v1.x 진입 의사결정 — 본 트랙 범위 외).

---

## 2026-05-09 — 🏁 wave2-trash-original-path 트랙 종료 (Wave 2 T9 follow-up — admin trash 항목의 원위치 절대 경로 노출)

### 범위

PR #114 (wave2-trash-policy-viewer) 후속. 운영자가 휴지통 항목의 "원위치"를 단일 segment name이 아닌 절대 경로(예: `/회사/팀A/문서`)로 즉시 식별할 수 있게 한다. 같은 이름 폴더가 여러 위치에 존재하는 환경에서 식별 공수 ↓.

### 변경 핵심

**Backend**:
- `AdminTrashItemDto` — `originalParentPath: String` 필드 추가. leading `/`, trailing slash 없음. 부모 row 미존재 또는 chain 종착 실패 시 null. `originalParentName`은 fallback 용도로 유지.
- `AdminTrashRepository.findFolderAncestorPaths(Collection<UUID>)` — 단일 재귀 CTE로 leaf id별 절대 경로 일괄 조회. anchor에서 `parent_id`를 따라 root까지 누적, 종착 row(`current_id IS NULL`)만 SELECT. depth 100 cap (cycle 방지). subtree-size 동일 패턴.
- `AdminTrashService.list(...)` — 기존 owner email + parent name batch lookup에 `parentPathsFor(parentIds)` 추가 (빈 set short-circuit). file/folder 양쪽 DTO 생성 시 path 채움.
- `AdminTrashServiceTest` — `list_attachesOwnerEmailAndParentName`에 path stub + assertion 추가, 신규 `list_populatesOriginalParentPath_deepHierarchy` (다중 segment 검증) + `list_skipsAncestorPathQuery_whenNoParents` (빈 부모 short-circuit) 2종.
- `AdminTrashControllerTest` — 기존 NULL 직렬화 테스트에 `$.items[0].originalParentPath` null assertion 추가 + DTO 호출부 새 필드 정합.

**Frontend**:
- `types/trash.ts` — `AdminTrashItem.originalParentPath: string | null` 추가.
- `app/admin/trash/all/page.tsx` "원위치" cell — path 우선 + name fallback + `(루트)` 마커. `max-w-[320px] truncate` + `title` tooltip으로 긴 경로 처리.
- `app/admin/trash/all/page.test.tsx` — 기존 sample에 path 추가 + 기존 테스트 assertion을 path로 update + 신규 path-null fallback / root marker 2종 추가.
- `lib/api.adminTrash.test.ts` — fixture에 path 보강.

**Docs**:
- `docs/02 §7.11` `AdminTrashItemDto` 와이어 스키마에 `originalParentPath` row + Note에서 "full path resolve" deferred 표기를 closure로 전환.
- `docs/04 §8.3` 새 closure 항목 + JSON 예시에 path 필드.

### 검증

- `cd backend && ./gradlew test --tests "...AdminTrashServiceTest" --tests "...AdminTrashControllerTest"` BUILD SUCCESSFUL.
- `cd frontend && pnpm typecheck` exit 0, `pnpm lint` exit 0.
- `pnpm test --run page.test.tsx api.adminTrash.test.ts` — 24 tests passed (page 16 + api 8).

### 결정/편차

- **단일 재귀 CTE 1회 호출** — frontend가 path 매번 계산하는 대신 backend에서 batch query. subtree-size와 동일 패턴이라 운영자가 동작 모델 1개로 이해. depth cap 100도 동일.
- **fallback 유지** — `originalParentName`을 wire에서 제거하지 않음. 부모 chain 종착 실패(데이터 corruption / depth 초과) 시 운영자가 단일 segment라도 볼 수 있어야 함. wire dead weight는 small, robustness 우선.
- **부모 row 보존 정책 활용** — 휴지통의 부모 폴더도 hard purge 전까지 row 유지되므로 path는 살아있는/삭제된 부모 모두 동일 join으로 추적. 별도 history 테이블 미도입.
- **path 표기 = leading `/`, trailing 없음** — 일관성. UI는 `title` tooltip으로 truncate된 긴 경로 hover 노출.
- **`List.of(Object[]...)` 함정 회피** — element 1개 stub 시 varargs가 `List<Object>`로 추론되어 cast 깨짐. `Collections.singletonList`로 명시 (test 안에 한 줄 코멘트 추가).

### 다음 세션 컨텍스트

- v1.x backlog 잔여(보존 정책 mutation UI / 2인 승인 / 권한 grant/revoke direct CRUD / quota / progress streaming SSE/WS)는 별도 트랙.
- 본 트랙으로 docs/02 §7.11 Note의 "full path resolve" deferred 표기는 closure로 전환됨. 추후 admin DTO 추가 필드 검토 시 동일 batch lookup 패턴 재사용.

### 블로커

- 없음.

---

## 2026-05-09 — 🔥 fix-create-folder-csrf hotfix (ADMIN role도 폴더 생성 403 회귀)

### 범위

운영자가 ADMIN role임에도 `/files` 하위 폴더 생성 시 "폴더를 만들 권한이 없습니다"로 거부되던 회귀 수정. 원인: `api.createFolder`의 fetch 호출에 `X-CSRF-TOKEN` 헤더가 누락되어 Spring CSRF filter가 PermissionEvaluator 도달 전 403 차단. 다른 mutation(rename/move/share 등) 7개 이상은 이미 CSRF 헤더 정상 송신.

### 변경 핵심

- `frontend/src/lib/api.ts` `createFolder`: `readCookie('XSRF-TOKEN')` + `'X-CSRF-TOKEN': csrf` 헤더 추가 (다른 mutation 패턴 정합).
- `frontend/src/lib/api.createFolder.test.ts` (NEW, 4 tests): CSRF 헤더 회귀 가드 + parentId 'root'→null + name trim + 403 envelope.

### 검증

- `pnpm typecheck` exit 0.
- `pnpm test --run api.createFolder` 4/4 PASS.
- backend 무변경 — wire 호환 0, 권한 정책 변경 0.

### 결정/편차

- 헤더 이름 `X-CSRF-TOKEN` (uppercase) — 다른 mutation들과 동형. HTTP 헤더는 case-insensitive라 backend `X-CSRF-Token` 매핑과 호환.
- 회귀 가드 단위 테스트 1건만 — `api.createFolder.test.ts`. CreateFolderDialog 통합은 기존 `CreateFolderDialog.test.tsx`가 mock된 `api.createFolder`를 호출하므로 본 fix 직접 영향 없음.

### 다음 세션 컨텍스트

- 다른 mutation에도 동일 회귀 가능성 — `frontend/src/lib/api.ts`의 POST/PATCH/DELETE 호출 12+건에 X-CSRF-TOKEN 누락 sweep 필요. 다음 트랙 csrf-mutation-sweep로 진행.

---

## 2026-05-09 — 🏁 wave2-trash-policy-viewer 트랙 종료 (Wave 2 T9 follow-up — `/admin/trash/policy` read-only viewer)

### 범위

PR #108 (wave2-trash-retention-config — `app.trash.retention.days` 외부화) 후속. 운영자가 yml 직접 열지 않고도 현재 보존 일수를 admin UI에서 확인할 수 있게 한다. `/admin/trash/policy` 페이지 + 신규 endpoint + AdminSideNav 링크. mutation은 v1.x deferred.

### 변경 핵심

**Backend (신규 파일 위주)**:
- `AdminTrashPolicyDto` (NEW, record(int retentionDays)) — 단일 필드 read-only DTO.
- `AdminTrashPolicyController` (NEW) — `GET /api/admin/trash/policy` `@PreAuthorize("hasRole('ADMIN')")`. `TrashRetentionProperties.days()` 그대로 노출.
- `AdminTrashPolicyControllerTest` (NEW, 4 tests) — 200/401/403 매트릭스 + non-default 값(14) 회귀 가드. `@TestConfiguration`으로 properties 고정 주입 (audit-export-cap-config 패턴 동형).

**Frontend (신규 파일 위주 + 작은 추가 수정)**:
- `types/admin-trash-policy.ts` (NEW) — `AdminTrashPolicy { retentionDays: number }`.
- `hooks/useAdminTrashPolicy.ts` (NEW) — `useQuery`, staleTime 60s, retry false. `useAdminStorageOverview` 패턴 동형.
- `lib/api.ts` — `getAdminTrashPolicy()` 추가 (`getAdminStorageOverview` 직후, 패턴 동형).
- `lib/queryKeys.ts` — `qk.adminTrashPolicy()` 추가 (`adminTrashList` 직후).
- `app/admin/trash/policy/page.tsx` (NEW) — `<AdminGuard>` + 카드 3개: 보존 기간(숫자 강조), cron cross-link(`/admin/system`), 변경 절차 안내(yml + 재기동 + 0/음수 보정 안내).
- `app/admin/trash/policy/page.test.tsx` (NEW, 6 tests) — loading/error/success/non-default/cross-link href/h1.
- `components/admin/AdminSideNav.tsx` — `'휴지통 정책'` 링크 추가 (`scope: 'ADMIN'`, `match: 'exact'`). DEFERRED_ITEMS에서 `'정책'` 제거 (closure 마커).
- `components/admin/AdminSideNav.test.tsx` — ADMIN test에 `'휴지통'`/`'휴지통 정책'` 검증 + AUDITOR 화면 hide 회귀 가드.

**Docs**:
- `docs/02 §7.11` 표 — `GET /api/admin/trash/policy` 신규 row.
- `docs/04 §8.3` — viewer closure 마커 추가, mutation v1.x deferred 명시 유지.

### 검증

- `cd backend && ./gradlew test --tests "com.ibizdrive.admin.trash.AdminTrashPolicyControllerTest"` BUILD SUCCESSFUL.
- `cd frontend && pnpm typecheck` exit 0, `pnpm lint` exit 0.
- `pnpm test --run page.test.tsx + AdminSideNav.test.tsx` — 15 tests passed (page 6 + sidenav 9).

### 결정/편차

- **read-only 우선** — mutation API/UI는 v1.x. `@ConfigurationProperties` 부팅 바인딩이라 runtime 변경 자체가 어렵고, 즉시 변경 시 hard purge가 갑자기 일어나는 사고 위험을 운영자가 의도적으로 yml + 재기동 절차로 격리.
- **cron 분리** — `enabled/cron/zone`은 `/admin/system` 책임 (Wave 1 T3 + admin-cron-toggle #102 closure). 본 페이지는 cross-link만 제공해 책임 경계 명확화.
- **신규 controller 분리** — listing/bulk이 있는 `AdminTrashController`와 책임 분리 (1 endpoint·1 책임, KISS). 향후 mutation 도입 시 같은 controller에서 `PUT` 추가가 자연스러움.

### 다음 세션 컨텍스트

- v1.x backlog 잔여: 휴지통 보존 정책 mutation UI / 2인 승인 / full path resolve / 권한 grant/revoke direct CRUD / quota / progress streaming(SSE/WS).
- mutation 도입 시 `@RefreshScope` 또는 별도 `TrashRetentionPolicyEntity` (DB-backed) 검토 — admin-cron-toggle (#102)이 cron policy를 동일 패턴으로 진화시킨 결과 참고 가능.

### 블로커

- 없음.

---

## 2026-05-08 — 📝 legal-hold-design 트랙 (v2.x deferred Legal Hold 설계 명세 정합화)

### 범위

`docs/03 §6.3` Legal Hold가 v2.x deferred 스텁만 보유하던 상태를 **설계 명세**로 본문화. 코드 0줄 — 활성화는 v2.x 진입 시.

### 변경 핵심

- **ADR #46 신규** (docs/00 §5) — Legal Hold 데이터 모델 = 하이브리드(메타 테이블 `legal_holds` + cache flag `files.legal_hold`/`folders.legal_hold`). 활성화 시점 v2.x deferred 명시.
- **ADR #31 보강** — `WHERE legal_hold IS NOT TRUE` forward-reference가 ADR #46 cache flag와 정합함을 명시.
- **docs/02 §2.10 신설** — `legal_holds` 테이블 + cache flag 2개 + 인덱스 4개 + 동기화 invariant. ER 요약(§2.11)에 edge 추가.
- **docs/02 §8** — `LEGAL_HOLD_VIOLATION` 423 + `LEGAL_HOLD_RECENTLY_RELEASED` 409 신규.
- **docs/02 §7.13.1 stub 갱신** — `purge.expired` Skip 항목이 ADR #46 참조하도록 정정.
- **docs/03 §3.1** — `MANAGE_LEGAL_HOLD` 권한 enum 추가, ROLE=ADMIN 매핑.
- **docs/03 §3.2.5** — ADMIN ROLE 매트릭스에 `MANAGE_LEGAL_HOLD` 명시.
- **docs/03 §4.1** — audit enum 4종 정렬 (placeholder 2종 활성화 noted + 신규 `expired`/`violation_blocked` 2종).
- **docs/03 §6.3 본문화** — 정책 개요/데이터 모델/차단 액션 매트릭스 9종/API wire format/audit 매핑/권한/30일 락/만료 cron/dual-approval 게이트/v2.x 진입 시 task 분해 10개 sub-section.
- **docs/04 §10 본문화** — 대상 지정/Hold 동작/해제 워크플로/만료/관리자 페이지/운영 런북 진입 7개 sub-section.
- **dev/active/legal-hold-design/** — `plan.md`/`context.md`/`tasks.md` 3파일. tasks는 §A(현재 — 설계, 12항목 완료) + §B(v2.x 활성화, 7 sub-track 미실행) + §C(후반 backlog) + §D(산출물 위치) 구성.

### 검증

- 코드 변경 0건 (백엔드/프론트 0줄). docs + dev-docs only.
- `git diff --stat` 4 파일 (docs/00, 02, 03, 04) + dev/active/legal-hold-design 3파일 + dev/process/legal-hold-design.md.
- Cross-reference 정합 검사: ADR #46 ↔ ADR #31, docs/02 §2.10 ↔ §8 ↔ docs/03 §6.3 ↔ docs/04 §10. 모든 forward-reference 해소.
- 충돌 검증: 다른 세션 `dev/process/*.md` 0건 → 작업 파일 겹침 0.

### 결정/편차

- **데이터 모델 = 하이브리드** (3안 중 선택) — flag-only는 메타 부재로 컴플라이언스 증거 부족, 메타 테이블만은 ADR #31 forward-ref 정합 깨짐. 하이브리드는 (a) hot path 1줄 검사 + (b) 풍부한 메타 둘 다 확보.
- **dual-approval = config 게이트** (default false) — A7 hard purge / share-expired-cron / permission-expired-cron 패턴 답습. v2.x MVP는 단일 admin, 외부 출시 시 환경별로 활성화.
- **30일 재지정 락** — 실수 방지. `app.legal-hold.replace-cooldown-days` properties로 환경별 조정.
- **차단 매트릭스 = 9 mutation entry** (휴지통/restore/purge/버전/이름/이동/공유/권한/신규업로드). 다운로드/조회는 허용 (보존 목적상 access trail 정상).
- **거부**: 태그 기반 hold (v2.x 1차 컷 미포함, reason LIKE 검색으로 임시 대체) / hold export endpoint (audit log export로 대체) — context.md backlog 명시.

### 다음 세션 컨텍스트

- 본 트랙은 design-only. 활성화 트리거 = 외부 출시 + 컴플라이언스 도메인 진입 시점에 `dev/active/legal-hold-design/legal-hold-design-tasks.md §B` (V_ 마이그레이션 → permission/audit enum → guard → controller → frontend → 검증 → 운영 런북) 그대로 실행.
- v1.x backlog 잔여(휴지통 UI / full path resolve / 권한 CRUD / quota / audit streaming)와 무관.

### 블로커

- 없음 (v2.x 활성화 트리거는 외부 출시 + 컴플라이언스 도메인 진입 의사결정 — 본 트랙 범위 외).

---

## 2026-05-08 — 🏁 admin-cron-toggle 트랙 종료 (Wave 2 closure 후속 — cron 4종 runtime 토글)

### 범위

`/admin/system` viewer 페이지(Wave 1 T3, PR #73)에 ADMIN-only mutation 추가. cron 4종(`purge.expired`/`share.expire`/`permission.expire`/`storage.orphan.cleanup`)의 enabled를 재기동 없이 UI로 토글. 토글은 신규 `cron_policy` 테이블에 영구 저장, audit_log `admin.cron.toggled`로 추적.

### 변경 핵심

**Backend:**
- V11 마이그레이션 — `cron_policy` 테이블 + 4 row 시드(전부 false). yml의 `app.*.enabled`는 dead config(cleanup v1.x).
- `CronPolicy` entity + `CronPolicyRepository`(`isEnabled` 헬퍼).
- `AdminCronToggledEvent` + `AdminCronToggledListener`(AFTER_COMMIT) — `AdminDepartmentAuditListener` 패턴 미러.
- `AdminSystemService.toggleCron(key, enabled, actor, ip, ua)` — 트랜잭션 + event publish. unknown key는 `IllegalArgumentException` → 글로벌 핸들러 400.
- `AdminSystemController` — `PUT /api/admin/system/cron/{key}` (ADMIN-only `@PreAuthorize`). 응답 204.
- 4 cron job 클래스 — `@ConditionalOnProperty` 제거 + `run()` 첫 줄에 `cronPolicyRepository.isEnabled(KEY)` 가드.
- `AuditEventType.ADMIN_CRON_TOGGLED("admin.cron.toggled")` 추가 (47 → 48).
- 테스트: Repository slice 4 케이스, Listener 단위 2 케이스, Service 단위 3 케이스, Controller slice 4 케이스, E2E (login + toggle + audit_log + cron skip).

**Frontend:**
- `api.adminToggleCron(key, enabled)` PUT 메서드 + 단위 테스트 4 케이스.
- `useAdminToggleCron()` mutation hook — `qk.adminSystemCron()` 무효화.
- `/admin/system` page — ADMIN 세션에서만 토글 switch + ConfirmDialog. AUDITOR는 viewer 그대로.
- `types/audit.ts` — `'admin.cron.toggled'` union 추가.

**Docs:**
- `docs/03 §4.1` — admin.cron.toggled 행 + enum 47 → 48.
- `docs/04 §15.4` — yml 편집 + 재기동 → UI 토글 + audit 추적 절차.
- `BETA-RELEASE.md §7` — v1.x deferred "cron 4종 런타임 토글" 라인 제거 + Source 체인에 admin-cron-toggle 추가.

### 검증

- `cd backend && ./gradlew test` BUILD SUCCESSFUL (Repository 4 / Listener 2 / Service 3 / Controller slice 4 / E2E 모두 GREEN).
- `cd frontend && npm run typecheck && npm run lint && npm test -- --run && npm run build` 모두 exit 0.
- AUDIT_EXPORTED enum 47 → 48 (1 추가). 새 에러 코드 0 (기존 `BAD_REQUEST` / `FORBIDDEN` 재사용).

### 다음 세션 컨텍스트

- yml의 `app.*.enabled` 필드 제거(dead config) — v1.x 후속 트랙.
- schedule(cron expression) UI 편집 — v1.x.
- 2인 승인 워크플로(파괴적 토글 보호) — Wave 2 closure backlog의 일반 항목.
- `prod 첫 적용 시` V11 시드가 모든 cron을 OFF로 두므로 운영자가 적용 직후 UI에서 4종 토글로 의도된 상태로 설정 필요(release note 준비).

---

## 2026-05-08 — 🏁 wave2-admin-trash-format-size 트랙 종료 (admin trash 페이지 size 표시 formatBytes 적용)

### 범위

`/admin/trash/all` 페이지가 `${item.sizeBytes} B`로 raw 바이트를 렌더 — 5GB 폴더가 "5234567890 B"로 보여 운영자 가독성 ↓. 기존 `formatBytes` 유틸 재사용으로 KB/MB/GB로 정규화 표시. PR #103 (folder subtree size) closure 후 자연 따라오는 UX 마무리.

### 변경 핵심

- `app/admin/trash/all/page.tsx`: `formatBytes` import + 렌더를 `formatBytes(item.sizeBytes)`로 교체. 셀에 `tabular-nums` 클래스 추가 (숫자 정렬 시각적 안정).
- `app/admin/trash/all/page.test.tsx`: 신규 케이스 1개 — `12345 B` → `12 KB` 렌더 검증 + raw "12345 B" 미노출 회귀 가드.

### 검증

- `pnpm typecheck` exit 0, `pnpm lint` exit 0.
- `pnpm test --run src/app/admin/trash/all/page.test.tsx` 14 tests passed.
- backend 영향 0, wire 무변경.

### 결정/편차

- 기존 `formatBytes` 그대로 재사용 (DRY) — admin-storage-overview / admin-dashboard와 동일 헬퍼.
- `tabular-nums` 추가 — 행 별 size 자릿수 정렬 안정 (소소한 polish).

### 다음 세션 컨텍스트

- v1.x backlog 잔여: 휴지통 보존 정책 UI / 2인 승인 / full path resolve / 권한 grant/revoke direct CRUD / quota / progress streaming(SSE/WS) / chunked client.

### 블로커

- 없음.

---

## 2026-05-08 — 🏁 wave2-trash-retention-config 트랙 종료 (휴지통 보존 기간 외부화)

### 범위

`FileMutationService.PURGE_DAYS = 30` / `FolderMutationService.PURGE_DAYS = 30` 두 곳에 중복 하드코딩되었던 보존 기간을 `@ConfigurationProperties`로 외부화. `audit-export-cap-config` (PR #97) 패턴 동형 — 운영자가 application.yml 수정 + 재기동만으로 보존 기간 조정 가능. wire/DTO/default 동작 모두 무변경 (default 30일 유지).

### 변경 핵심

**Backend (backend-only, frontend 0 변경)**:
- `com.ibizdrive.trash.TrashRetentionProperties` (NEW) — `record(int days)` + 0/음수 입력 → default 30 보정. 운영자 실수 시 즉시 hard purge 후보가 되는 사고 방지(보안 가드).
- `com.ibizdrive.trash.TrashConfig` (NEW) — `@EnableConfigurationProperties(TrashRetentionProperties.class)` 등록 (`AuditConfig`/`StorageConfig` 패턴 동형).
- `application.yml`: `app.trash.retention.days: 30` 추가 (default).
- `FileMutationService`: `PURGE_DAYS=30` 상수 제거 → 생성자에 `TrashRetentionProperties retention` 주입, `retention.days()` 사용.
- `FolderMutationService`: 동일 패턴.

**Tests**:
- `TrashRetentionPropertiesTest` (NEW, 3 tests) — 양수/0/음수 보정.
- `FileMutationServiceTest.TestConfig` / `FolderMutationServiceTest.TestConfig` — manual constructor 호출 5번째 인자(`new TrashRetentionProperties(30)`) 추가.

**Docs**:
- `docs/02 §6.5` — 보존 기간이 `app.trash.retention.days`에서 외부화됨 + 0/음수 보정 + 무중단 변경 deferred 명시.
- `docs/04 §9.2` — 운영자 가이드 갱신 (코드 변경 없이 yml + 재기동만으로 일수 조정).

### 검증

- `cd backend && ./gradlew compileJava compileTestJava` BUILD SUCCESSFUL — cross-module DI 시그니처 영향 0 (manual constructor 호출 2곳만 존재, 테스트 TestConfig 둘 다 갱신).
- `cd backend && ./gradlew test --tests "com.ibizdrive.trash.TrashRetentionPropertiesTest"` BUILD SUCCESSFUL.
- FileMutationServiceTest/FolderMutationServiceTest는 Testcontainers + Docker 필요 — CI에서 회귀 검증.

### 결정/편차

- `audit-export-cap-config` 패턴 그대로 미러링 — 동일한 anchor/네이밍/검증 룰 유지로 일관성.
- 두 서비스 모두 외부화 — duplicated source of truth 제거 (KISS).
- 무중단 변경 deferred (`@ConfigurationProperties`는 부팅 바인딩) — 사내 베타 운영 단계라 재기동 1회로 충분, 무중단 도입은 v1.x++.
- 운영자 UI(`/admin/trash/policy`) — 별도 트랙으로 v1.x deferred 유지.

### 다음 세션 컨텍스트

- v1.x backlog 잔여: 휴지통 보존 정책 UI / 2인 승인 / full path resolve / 권한 grant/revoke direct CRUD / quota / progress streaming(SSE/WS) / chunked client.
- 보존 기간을 폴더/파일별로 다르게 하는 fine-grained 정책은 v1.x++.

### 블로커

- 없음.

---

## 2026-05-08 — 🏁 v1x-restore-conflict-dialog 트랙 종료 (M9 v1.x — 휴지통 복원 시 다른 이름으로 복원 다이얼로그)

### 범위

M9 휴지통 entry 의 v1.x backlog 중 첫 항목 closure. 사용자가 휴지통 항목을 복원할 때 원위치에 동일 이름의 활성 항목이 있으면 backend 가 `RESTORE_CONFLICT` 를 던지고 frontend 가 toast.error 한 줄로 끝나던 것을, **다른 이름으로 복원 다이얼로그** 흐름으로 교체.

### 변경 핵심

**Backend** (`POST /api/files/{id}/restore`, `POST /api/folders/{id}/restore` 양쪽):
- `common/dto/RestoreRequest` record 신규 (file/folder 공유, optional `name`).
- `FileMutationService.restore(id, actorId, newName?)` overload 추가 — 기존 2-arg 시그니처 보존(AdminTrashService 등 호환).
  - newName == null + 원본 이름 충돌 → `FileRestoreConflictException` (신규) → `RESTORE_CONFLICT` envelope.
  - newName != null + 새 이름 충돌 → `FileNameConflictException` (기존) → `RENAME_CONFLICT` envelope.
  - newName != null 시 `NormalizeUtil.normalizeFileName/normalizedNameForDedup` 재사용 (NFC + dedup).
- `FolderMutationService.restore` 동일 패턴 — `FolderNameConflictException` (rename 에서 사용 중) 재사용.
- `FileController.restore` / `FolderController.restore` body `@RequestBody(required = false) RestoreRequest` 바인딩.
- `GlobalExceptionHandler` 에 `FileRestoreConflictException` → `RESTORE_CONFLICT` 핸들러 신규 (folder 와 envelope 통일).
- audit_log metadata 에 `name` / `normalizedName` (renaming 시) before/after 추가, 기존 RESTORE 이벤트 enum 그대로.

**Frontend**:
- `lib/api.ts` `restoreFile/restoreFolder(id, opts?: { newName? })` 시그니처 확장 — body `{ name }` POST.
- `hooks/useRestoreItem` Vars 에 `newName?: string` 추가.
- `stores/restoreConflictUi.ts` 신규 zustand (`renameUi.ts` 미러).
- `lib/restoreNameSuggest.ts` 신규 — `suggestRestoreName(name, type)`. file 은 마지막 `.` 분리해 base + ` (1)` + ext, folder 는 단순 ` (1)`. MVP 1회만(시퀀스 자동 증분 v1.x).
- `components/trash/RestoreConflictDialog.tsx` 신규 — `RenameDialog` 패턴 미러: role=dialog/aria-modal, 자동 제안 입력 + inline alert + Esc/previousFocus.
- `app/(explorer)/trash/ClientTrashPage.tsx` 에 다이얼로그 마운트.
- `components/trash/TrashRowActions.tsx` `onError(RESTORE_CONFLICT)` → `restoreConflictUiStore.open(...)` (toast.error 폐기).
- `components/files/BulkActionBar.tsx` undoDelete 의 RESTORE_CONFLICT 메시지를 "휴지통에서 행 단위로 다른 이름으로 복원" 가이드로 강화 (DeletedItem 에 name 부재 + 다건 다이얼로그 v1.x 후속).

**Tests**:
- `FileMutationServiceTest`: 기존 `restore_conflictAtOriginalFolder` expect 를 `FileRestoreConflictException` 으로 정정 + `restore_withNewName_renames`/`_conflict` 2건 신규.
- `FolderMutationServiceTest`: `restore_withNewName_renames`/`_conflict` 2건 신규.
- `FileControllerTest` / `FolderControllerTest`: 기존 `restore_returnsOk_andDelegates` 시그니처 갱신(3-arg mock + null body) + `restore_withNewName_delegatesNewName` 신규.
- `lib/restoreNameSuggest.test.ts` 신규 (5 cases — file 확장자/multi-dot/dotfile/no-ext + folder).
- `components/trash/RestoreConflictDialog.test.tsx` 신규 (6 cases — 닫힘/자동 제안/submit success/RENAME_CONFLICT inline/unknown error toast/Esc).

**Docs**:
- `docs/02 §7.5/§7.6` restore 행 — body `{ name? }` + 두 envelope 코드 명시.
- `docs/01 §13.2` 휴지통 UX — RestoreConflictDialog 동작 + suggestRestoreName 로직 + BulkActionBar 안내.

### 검증

- Backend: `./gradlew test --tests "com.ibizdrive.file.*" --tests "com.ibizdrive.folder.*"` BUILD SUCCESSFUL.
- Frontend: `pnpm typecheck && pnpm lint` PASS, 신규 11/11 PASS (suggestRestoreName 5 + Dialog 6).

### 결정/편차

- **에러 envelope 통일**: 사용자 컨펌 §1 추천대로 file restore 의 원본 이름 충돌을 `RENAME_CONFLICT`(이전) → `RESTORE_CONFLICT`(통일) 로 변경. frontend 가 이미 RESTORE_CONFLICT 만 분기하던 상태라 정합 향상. backend behavior change 이지만 외부 클라이언트 영향 미미(internal use).
- **자동 제안**: ` (1)` 1회 MVP. 시퀀스(`(2)`,`(3)`...) 자동 증분은 v1.x.
- **다건 Undo 다이얼로그 미적용**: DeletedItem 에 name 부재 + 다건 다이얼로그 UX 자체가 v1.x 영역. BulkActionBar 메시지만 사용자가 휴지통 페이지로 유도하도록 강화.
- **focus trap 미적용**: SortChip/RenameDialog 패턴 따라 outside click(없음) + Esc 만. trap 은 v1.x.
- **2-arg overload 보존**: `restore(id, actorId)` 호출자(AdminTrashService.bulk) 모두 호환 유지 — 별도 변경 없이 새 시그니처가 위임.

### 다음 세션 컨텍스트

- v1.x 후속: 자동 증분 시퀀스 / 다건 Undo 다이얼로그 / focus trap / 다른 destination 으로 복원(현재는 원위치 한정).
- audit_log RESTORE 이벤트 enum 그대로 — `RESTORE_AS` 같은 별도 이벤트 도입은 v1.x 검토.
- docs/02 §8 에러 코드 표 본문 갱신은 follow-up (현재 §7.5/§7.6 표 + §13.2 만 갱신).

---

## 2026-05-08 — 🏁 audit-export-filename-timestamp 트랙 종료 (export filename UTC timestamp 추가)

### 범위

`/api/admin/audit/export` 응답 `Content-Disposition` filename에 UTC timestamp 추가. 같은 날 여러 다운로드 시 OS 자동 `(1)` suffix 회피 + 정렬 친화. backend 단독, wire 호환 0.

### 변경 핵심

- `AuditQueryController`: `FILENAME_DATE` (`yyyy-MM-dd`) → `FILENAME_TIMESTAMP` (`yyyy-MM-dd_HHmmss'Z'` UTC). filename 빌드는 `LocalDate.now()` → `Instant.now()` (UTC 결정적).
- 변경 결과: `audit_logs_2026-05-08.csv` → `audit_logs_2026-05-08_123045Z.csv`.
- 콜론(`:`)은 Windows 파일명 금지 문자라 시각부 delimiter 없이 작성 (ISO 8601 basic).
- `AuditExportE2ETest`: 두 곳 filename 검증을 `startsWith/endsWith` → `matches("audit_logs_\\d{4}-\\d{2}-\\d{2}_\\d{6}Z\\.csv|json")` regex로 보강.

### 검증

- `cd backend && ./gradlew test --tests "com.ibizdrive.audit.*"` BUILD SUCCESSFUL (3m57s).
- wire 호환 0 — 응답 형태/Content-Type/audit metadata 변경 0, frontend 영향 0 (filename은 OS 저장 시 사용, code lookup 안 됨).

### 결정/편차

- UTC 채택 — 운영자 timezone과 무관하게 결정적. KST 등 wall-clock은 audit_log row의 `occurredAt`이 별도로 보존하므로 filename은 결정적 정렬에 더 가치.
- delimiter `_HHmmss'Z'` (콜론 없음) — Windows 호환. ISO 8601 basic 변형.

### 다음 세션 컨텍스트

- v1.x backlog 잔여: 휴지통 보존 정책 UI / 2인 승인 / full path resolve / 권한 grant/revoke direct CRUD / quota / audit SQL→JSON streaming.

### 블로커

- 없음.

---

## 2026-05-08 — 🏁 audit-export-cap-metadata 트랙 종료 (cap 값 audit metadata 노출)

### 범위

직전 `audit-export-cap-config` 트랙(PR #97)으로 cap이 동적이 되면서, 운영 디버깅 시 어떤 cap에서 `truncated=true` 발생했는지 audit_log row만으로 추적 가능하도록 metadata에 `rowCap` 필드 추가.

### 변경 핵심

- `AuditExportEvent.rowCap` 필드 추가 (record 7→8 필드).
- `AuditQueryController.export`: `AuditExportProperties` 의존성 추가, event publish 시 `exportProperties.rowCap()` 전달.
- `AuditExportListener.onExport`: metadata JSON에 `"rowCap":N` 키 추가 (가장 마지막 위치).
- `AuditExportListenerTest`: 기존 3 케이스에 `CAP=10000` arg 추가 + `"rowCap"` assert. 1 신규 — 운영자가 cap을 5000으로 줄인 시나리오.
- `AuditQueryControllerTest`: `@MockBean AuditExportProperties` 추가.

### 검증

- `cd backend && ./gradlew test --tests "com.ibizdrive.audit.*"` BUILD SUCCESSFUL (4m8s).
- wire 호환 0 — 응답 형태/필터 처리 변경 0, frontend 영향 0.
- audit_log metadata는 추가만(기존 키 위치/타입 변경 0).

### 결정/편차

- metadata 키 위치는 마지막에 추가 — 기존 키 순서 유지로 audit row diff·테스트 안정.
- `AuditExportProperties`를 controller에 직접 주입 — service 경유 우회는 의미 없음(service는 cap을 적용하지만 publish 책임은 controller).

### 다음 세션 컨텍스트

- v1.x backlog 잔여: 휴지통 보존 정책 UI / 2인 승인 / full path resolve / 권한 grant/revoke direct CRUD / quota / audit SQL→JSON streaming.

### 블로커

- 없음.

---

## 2026-05-08 — 🏁 wave2-folder-subtree-size 트랙 종료 (Wave 2 T9 follow-up — admin trash folder DTO subtree size)

### 범위

`/admin/trash` 의 folder DTO `sizeBytes`는 기존 항상 null (spec §4.4 v1.x deferred 명시). 운영자가 휴지통에서 큰 폴더를 식별하지 못해 우선 처리 결정 불가. **DTO 시그니처 무변경** + 단일 재귀 CTE batch lookup으로 채워준다.

### 변경 핵심

**Backend** (backend-only, frontend 0 변경):
- `AdminTrashRepository.findFolderSubtreeSizes(Collection<UUID>)` (NEW) — 단일 재귀 CTE: 자기 자신 + 모든 하위 폴더의 `files.size_bytes` 합. depth cap 100 (cycle 방지). 빈 폴더는 `COALESCE(SUM, 0)`로 0 보장.
- `AdminTrashService` — page의 trashed folder ids 배치 lookup `subtreeSizesFor(folders)` private 메서드 신설. folder DTO 생성 시 `null` 자리에 `Map.get(folderId)` 사용. 빈 입력은 short-circuit (Postgres `IN ()` 문법 오류 방지).
- `AdminTrashItemDto` javadoc 갱신 — `sizeBytes` 항상 not-null 명시 (file=자기 size / folder=subtree size).

**Tests**:
- `AdminTrashServiceTest.list_populatesFolderSubtreeSize` (NEW) — 2개 folder의 subtree size가 DTO에 정확히 매핑되는지 검증 (빈 폴더 0 포함).
- `AdminTrashServiceTest.list_skipsSubtreeQuery_whenNoFolders` (NEW) — 빈 입력 short-circuit (Postgres IN () 회피).

**Docs**:
- `docs/02 §7.11` — `AdminTrashItemDto.sizeBytes` 표기를 `number` always not-null + folder 의미 명시. Note에서 folder subtree size를 closure로 표시.
- `docs/04 §8.3` — "폴더 subtree size" `[x]` 항목 신설 (단일 CTE + 운영 가치).

**Frontend (변경 0)**: `types/trash.ts`는 `sizeBytes: number | null` 그대로 유지(wire 안전), `page.tsx`는 이미 `item.sizeBytes != null ? ... : '-'` 처리 → folder도 자동으로 size 노출.

### 검증

- `cd backend && ./gradlew test --tests "com.ibizdrive.admin.trash.*"` BUILD SUCCESSFUL.
- `cd backend && ./gradlew compileJava compileTestJava` BUILD SUCCESSFUL — repo signature 변경의 cross-module 영향 0.
- 기존 admin.trash service test 회귀 0 (Mockito default empty list가 unstubbed `findFolderSubtreeSizes` 안전 처리).

### 결정/편차

- **단일 재귀 CTE per page** — 페이지 단위(max 100 root) 처리, admin 사용 빈도 ↓이라 size cache 미도입(YAGNI). 진정한 대규모 트리 최적화는 v1.x++.
- **Live size, not snapshot** — folder가 trash에 들어온 후 일부 file이 hard-purge되면 줄어듦. "지금 폴더가 가진 size"가 admin 의도에 더 자연스러움.
- **빈 폴더 = 0 (not null)** — 명시적 측정 결과. wire는 `number | null` 타입 유지(backward-compat) + 모든 row에서 number를 송신.
- **depth cap 100** — 정상 폴더 트리는 그보다 훨씬 얕음. 비정상 cycle 데이터에도 무한 루프 차단.
- **Repository 메서드 분리** — listing 2종 query와 별개 메서드. cursor/필터와 무관한 batch lookup이라 시그니처 합치면 가독성 ↓.

### 다음 세션 컨텍스트

- v1.x backlog 잔여: 휴지통 보존 정책 UI(`/admin/trash/policy`) / 2인 승인 / full path resolve / 권한 grant/revoke direct CRUD / quota / progress streaming(SSE/WS) / chunked client.
- size cache table / live folder의 subtree size 기능은 v1.x++.

### 블로커

- 없음.

---

## 2026-05-08 — 🏁 wave2-trash-date-filter-kst 트랙 종료 (Wave 2 T9 follow-up — admin trash 날짜 필터 KST 경계화)

### 범위

PR #83 (admin global trash `deletedFrom`/`deletedTo` 도입) 후속. 기존 변환은 `ZoneOffset.UTC` 기반이라 한국 운영자가 `2026-05-08`을 입력하면 백엔드는 UTC 5/8 0시 ~ UTC 5/9 0시 (= KST 5/8 09:00 ~ KST 5/9 09:00)를 검색해 의도한 KST 0~24시와 9시간 시프트되는 운영 신뢰도 버그.

### 변경 핵심

**Backend** (단일 메서드 ZoneId 변경):
- `AdminTrashController.parseDateBoundary` — `ZoneOffset.UTC` → `ZoneId.of("Asia/Seoul")` (+ `KST` 상수 분리). javadoc에 KST 경계 + PR #83 follow-up 명시.
- 사내 단일 지역 운영 + 기존 cron(`app.purge.zone: Asia/Seoul`) 패턴과 정합 → 하드코딩 (YAGNI; application.yml 주입 분기는 다중 리전 진입 시).

**Tests**:
- `AdminTrashControllerTest.list_passesDeletedDateRangeBoundaries` — expected instant을 KST 경계로 갱신 (5/1~5/7 → UTC `2026-04-30T15:00Z`/`2026-05-07T15:00Z`).
- `AdminTrashControllerTest.list_singleDayBoundaryUsesKstNotUtc` (NEW) — 단일 날짜 입력 시 9시간 KST 시프트 회귀 방지.

**Docs**:
- `docs/02 §7.11` — `deletedFrom`/`deletedTo` 변환 설명을 KST(`Asia/Seoul`) 경계로 명시 + 운영자 wall-clock 일치 부연.
- `docs/04 §8.3` — "삭제일 범위 필터" `[x]` 항목 신설 (KST 경계, exclusive 상한 의미 포함).
- `frontend/src/types/trash.ts` `AdminTrashFilters` — stale "UTC 00:00:00Z" 주석을 KST로 갱신 (wire 시그니처 무변경).

### 검증

- `cd backend && ./gradlew test --tests "com.ibizdrive.admin.trash.*"` BUILD SUCCESSFUL.
- `cd frontend && pnpm test --run` 125 files / **945 passed** (skipped 0).
- `pnpm typecheck` exit 0 / `pnpm lint` exit 0.

### 결정/편차

- ZoneId 하드코딩 — 사내 단일 지역 + cron 정합 (KISS, YAGNI). `app.timezone` 환경변수화는 다중 리전 진입 시 cron까지 묶어 처리.
- Repository SQL 무변경 — `timestamptz` 비교는 zone-agnostic, controller 측 변환만 진실의 출처.
- Frontend wire 무변경 — `<input type="date">`는 운영자 wall-clock(KST)을 그대로 송신, backend가 KST로 해석.
- 기존 KST 시프트 입력 의도(UTC 가정)로 사용 중이던 결과는 9시간 시프트됨 — PR #83이 5/8 직전 머지된 신규 기능이라 영향 운영자 ↓ + 운영자 의도 정합이 더 큼.

### 다음 세션 컨텍스트

- v1.x backlog 잔여 (KST 항목 closure 후): 휴지통 보존 정책 UI(`/admin/trash/policy`) / 2인 승인 / full path resolve / folder subtree size / 권한 grant/revoke direct CRUD / quota / progress streaming(SSE/WS) / chunked client.

### 블로커

- 없음.

---

## 2026-05-08 — 🏁 audit-export-cap-config 트랙 종료 (audit export cap 외부화)

### 범위

`AuditQueryService.EXPORT_ROW_CAP` 하드코딩 상수(10_000) → `@ConfigurationProperties(prefix="app.audit.export") AuditExportProperties` 외부화. 운영자가 application.yml 수정 + 재기동만으로 cap 조정. 진정한 SQL → JSON streaming(메모리 cap 제거)은 v1.x++ 그대로.

### 변경 핵심

- `com.ibizdrive.audit.AuditExportProperties` (NEW) — `record(int rowCap)` + 0/음수 입력 → default 10000 보정.
- `com.ibizdrive.audit.AuditConfig` (NEW) — `@EnableConfigurationProperties` 등록 (StorageConfig 패턴 동형, audit 후속 properties 추가용 anchor).
- `AuditQueryService`: `static final EXPORT_ROW_CAP` 제거 → 생성자에 `AuditExportProperties` 주입, instance 필드로 사용.
- `application.yml`: `app.audit.export.row-cap: 10000` 추가.
- 테스트:
  - `AuditExportPropertiesTest` (NEW, 3 tests) — 양수/0/음수 보정.
  - `AuditQueryServiceTest.TestConfig`: 생성자 호출 4-arg로 마이그.

### 검증

- `cd backend && ./gradlew test --tests "com.ibizdrive.audit.*"` BUILD SUCCESSFUL (3m23s).
- wire 호환 0: cap default 10000 동일, 응답 형태 변경 0, audit metadata 변경 0.
- frontend 영향 0.

### 결정/편차

- `AuditConfig` 별도 신설 — `SchedulingConfig`에 합치는 대신 audit 패키지 내부에 위치. audit 후속 properties(예: SQL streaming 임계값)도 같은 anchor에서 등록 가능하도록.
- `EXPORT_ROW_CAP` 상수 완전 제거 — 외부 참조 0건 확인 후 안전하게 삭제 (테스트만 4-arg로 마이그).

### 다음 세션 컨텍스트

- v1.x backlog 잔여: 휴지통 보존 정책 UI / 2인 승인 / full path resolve / folder subtree size / 권한 grant/revoke direct CRUD / quota / audit SQL→JSON streaming.
- audit SQL→JSON streaming 도입 시 본 트랙의 `AuditExportProperties`에 `streaming-fetch-size` 등을 추가하는 패턴.

### 블로커

- 없음.

---

## 2026-05-08 — 🏁 design-variants-tweaks 트랙 종료 (M13.1 — Variant 시스템 + TweaksPanel)

### 범위

`design-reference/styles.css` 핸드오프 번들의 미적용 갭 closure: 누락 토큰 2종(`--accent-text`, `--success-soft`) + Variant 시스템 4종(default/notion/dropbox/terminal) + 사용자 런타임 토글 UI(TweaksPanel). M13(2026-04-25 디자인 토큰 적용) 후속.

### 변경 핵심

**Frontend**:
- `frontend/src/app/globals.css`: `--accent-text` / `--success-soft` 추가 (light/dark + `@theme inline` 매핑) + `[data-variant="notion|dropbox|terminal"]` 블록 + `[data-variant="terminal"] body { letter-spacing: -0.01em }`.
- `frontend/src/lib/variant.ts` (NEW): `Variant` 타입 + `getStored/getInitial/apply/persist` 5함수 (`theme.ts` 미러). localStorage 키 `'variant'`.
- `frontend/src/hooks/useVariant.ts` (NEW): `{ variant, setVariant }` (`useTheme` 패턴).
- `frontend/src/app/layout.tsx`: `variantInitScript` 추가 (FOUC 방지 — `themeInitScript` 패턴 미러).
- `frontend/src/components/topbar/TweaksPanel.tsx` (NEW): `<SlidersHorizontal>` trigger + popover (`role=dialog`) 안에 ThemeToggle 임베드 + variant 4종 라디오 그룹 (`role=radiogroup`). outside click + Esc 닫기.
- `frontend/src/components/topbar/TopBar.tsx`: ThemeToggle 직접 마운트 → TweaksPanel 로 교체.

**Tests**:
- `frontend/src/lib/variant.test.ts` (NEW, 10 tests) — 5함수 단위 + invalid stored value + localStorage swallow.
- `frontend/src/components/topbar/TweaksPanel.test.tsx` (NEW, 8 tests) — trigger ARIA + 4 라디오 + data-variant 적용/제거 + ThemeToggle 임베드 + Esc/outside click.
- 기존 `ThemeToggle.test.tsx` 무변경 — 컴포넌트 자체 변경 0건.

**Docs**:
- `docs/design-system.md` §10 "Variant 지원 범위" → "M13.1 에서 해소" stub + 신규 §11 "Variant 시스템" (4종 비교 표 + 영속 + 옵션 B 설명 + TweaksPanel 사용법 + 관련 파일 표).
- `docs/01-frontend-design.md` §18 로드맵에 M13.1 행 신설.

### 검증

- `pnpm typecheck` exit 0.
- `pnpm lint` exit 0.
- `pnpm test --run` 125 files / **944 passed** (직전 baseline 대비 +18 신규).

### 결정/편차

- **옵션 B (KISS) 채택**: terminal variant 17 selector 폰트 override 대신 `:root --font-sans` 토글 + body letter-spacing 두 줄로 흡수. 이유: master frontend 가 Tailwind 유틸리티 기반이라 className 별 override 가 어색. 미세 차이 발생 시 v1.x 에서 옵션 A 로 재이식 가능.
- **dark theme `--accent-text: #0F0F0E`** (terminal `--bg` 와 동일). design-reference 미정의 항목 — accent 명도 위 가독성 검증 통과 가정.
- **density slider 미포함**: variant 가 `--row-h` 를 결정하므로 중복. v1.x.
- **focus trap 미적용**: SortChip/RenameDialog 패턴 따라 outside click + Esc 만. trap 은 v1.x.
- **TweaksPanel 내부에 ThemeToggle 임베드**: TopBar 에 토글 2개 노출 회피. ThemeToggle 컴포넌트는 보존, 부모만 변경.

### 다음 세션 컨텍스트

- variant 4종 다른 화면(FileTable / 휴지통 / admin)에서 시각 검증 필요 — 본 트랙은 토큰/store/UI 까지만, 시각 회귀(Playwright 또는 Storybook visual diff)는 v1.x.
- `lib/variant.ts` 가 system default 함수 미보유 — theme 의 `prefers-color-scheme` 같은 OS 시그널이 variant 에는 없음. localStorage 미지정 → `'default'` 폴백으로 충분.
- TweaksPanel popover 가 focus trap 부재 — 키보드 접근성 강화 필요 시 v1.x.

### 블로커

- 없음.

---

## 2026-05-08 — 🏁 audit-ndjson 트랙 종료 (Wave 1 T2 follow-up — audit export NDJSON 형식)

### 범위

`audit-export-json` 트랙(PR #85) "다음 세션 컨텍스트"의 v1.x 항목. `format=ndjson` 추가 — line-oriented 도구(`jq`/`grep`/`split`)·SIEM ingest 친화. 직전 `audit-format-enum` 트랙(PR #91)의 enum 패턴 재사용.

### 변경 핵심

**Backend:**
- `AuditExportFormat`에 `NDJSON` 멤버 + `from(...)` 분기.
- `AuditNdjsonWriter` (NEW) — row-per-line 직렬화. `objectMapper.writeValueAsBytes` + `out.write` 패턴 (Jackson `writeValue(OutputStream, ...)`은 AUTO_CLOSE_TARGET이 기본 enabled라 stream을 닫는 이슈 회피). 마지막 row 뒤에도 `\n` (POSIX text file 관행).
- `AuditQueryController.export` switch 정리 (CSV / JSON / NDJSON). `contentTypeFor` 헬퍼 추가 — NDJSON은 `application/x-ndjson; charset=UTF-8`.

**Frontend:**
- `getAuditLogsExportUrl` `format` 타입 확장 (`'csv' \| 'json' \| 'ndjson'`). 기존 호출자 영향 0.

**Tests:**
- `AuditNdjsonWriterTest` (NEW, 4 tests): 빈 결과 0 byte, 단일 row trailing LF, 다중 row LF 구분, BOM 미부착.
- `AuditExportListenerTest` +1: NDJSON metadata 케이스.
- `AuditQueryControllerTest`: ndjsonWriter mock 추가 (호출 안 함).
- `api.getAuditLogsExportUrl.test.ts` +1: ndjson URL 송신.

### 검증

- backend `./gradlew test --tests "com.ibizdrive.audit.*"` BUILD SUCCESSFUL (1m54s).
- frontend `pnpm typecheck` exit 0 + `pnpm test api.getAuditLogsExportUrl` 6/6 PASS.

### 결정

- **Content-Type `application/x-ndjson`** — 사실상 표준. RFC 7464 `application/json-seq`와는 다른 포맷.
- **Trailing newline 발행** — POSIX text file 관행, 빈 결과 vs 단일 row 구분 결정적.
- **frontend UI 미추가** — wire만 노출. 직접 URL 호출 시나리오 한정. `/admin/audit/logs`에 "NDJSON 내보내기" 버튼은 v1.x++.

### 다음 세션 컨텍스트

- v1.x backlog 잔여: 휴지통 보존 정책 UI / 2인 승인 / full path resolve / folder subtree size / KST 경계 날짜 필터 / 권한 grant/revoke direct CRUD / quota / audit SQL→JSON streaming.

### 블로커

- 없음.

---

## 2026-05-08 — 🏁 audit-format-enum 트랙 종료 (Wave 1 T2 follow-up — audit export `format` 강타입화)

### 범위

`audit-export-json` 트랙(PR #85)의 다음 세션 컨텍스트에 명시된 v1.x 항목. `format` String 비교(3 군데) → `AuditExportFormat` enum 강타입화. wire 호환·audit_log row byte-by-byte 동일.

### 변경 핵심

- 신규 `com.ibizdrive.audit.AuditExportFormat` enum (`CSV`, `JSON` + `wire()` + `from(String)`).
- `AuditExportEvent.format`: `String` → `AuditExportFormat`.
- `AuditQueryController.export`: `if/else String 비교` → `AuditExportFormat.from(format)` 단일 검증 + enum 변수로 stream/header/extension 분기.
- `AuditExportListener.onExport`: 5줄 fallback 로직 삭제 — enum이 컴파일러로 검증되어 fallback 도달 가능성 0. metadata는 `event.format().wire()`.
- `AuditExportListenerTest`: String 호출 → enum 마이그. `unknownFormatFallsBackToCsv` 테스트 삭제 (enum 도입으로 컴파일 단계 차단).

### 검증

- `cd backend && ./gradlew test --tests "com.ibizdrive.audit.*"` BUILD SUCCESSFUL (2m56s).
- frontend 영향 0 (wire 호환).
- audit_log metadata JSON byte-by-byte 동일 (CSV → `"csv"`, JSON → `"json"`).

### 결정/편차

- listener의 fallback 로직 **삭제** — enum이 valid 값만 허용하므로 defense-in-depth 의미 없음 (spec §5.1).
- enum 명: `AuditExportFormat` (도메인 명시). 짧은 `ExportFormat`은 다른 export 트랙과 충돌 위험.

### 다음 세션 컨텍스트

- v1.x backlog 잔여: 휴지통 보존 정책 UI / 2인 승인 / full path resolve / folder subtree size / KST 경계 날짜 필터 / 권한 grant/revoke direct CRUD / quota / audit SQL→JSON streaming / NDJSON.

### 블로커

- 없음.

---

## 2026-05-08 — 🏁 admin-trash-bulk 트랙 종료 (Wave 2 T9 follow-up — `/admin/trash/all` 일괄 복원·영구삭제)

### 범위

`/admin/trash/all` bulk restore/purge. 30일 만료 직전 일괄 정리 / 사용자 대량 오삭제 후 일괄 복원 시나리오 흡수. BETA-RELEASE §7 v1.x deferred "bulk restore·purge" 항목 closure.

### 변경 핵심

**Backend** (`POST /api/admin/trash/bulk`):
- `AdminTrashBulkRequestDto` / `AdminTrashBulkResponseDto` record 신설.
- `AdminTrashService.bulk(action, items, actorId)` — items 1..200 fan-out. `FileMutationService.restore` / `FolderMutationService.restore` / `TrashPurgeService.purgeFile|purgeFolder` 단건 service 그대로 호출. 도메인 예외(`FileNotFoundException`/`FolderNotFoundException` → "NOT_FOUND", `FileNameConflictException`/`FolderRestoreConflictException` → "NAME_CONFLICT")만 catch, 기타 RuntimeException은 글로벌 핸들러 전파.
- `AdminTrashController.bulk` — `@PreAuthorize("hasRole('ADMIN')")` + `@AuthenticationPrincipal IbizDriveUserDetails`.
- 응답 status는 항상 200 (부분 실패 허용); cap/action 검증 실패만 400. 단건 endpoint·V10 schema·audit enum 무변경.

**Frontend**:
- `types/trash.ts`에 `AdminTrashBulkAction`, `AdminTrashBulkItem`, `AdminTrashBulkRequest`, `AdminTrashBulkResponse` 추가.
- `lib/api.ts` `adminBulkTrash(action, items)` 신규.
- `hooks/useAdminTrash.ts` `useAdminBulkTrash()` mutation hook 신규 — onSuccess에서 `qk.adminTrash()` prefix invalidate.
- `components/admin/AdminTrashBulkActionBar.tsx` (NEW, admin 전용) — "선택 N개 / 전체 해제 / 일괄 복원 / 일괄 영구삭제".
- `app/admin/trash/all/page.tsx`: 행 좌측 체크박스 + 헤더 select-all (페이지 한정) + BulkActionBar + 결과 banner + ConfirmDialog (단건/일괄 message prop 분기). 필터 변경 / cursor 페이지 이동 시 선택 초기화.

**Tests**:
- `AdminTrashServiceBulkTest` (NEW, 12 tests) — action/cap 검증, fan-out, 부분 실패 매핑(NOT_FOUND/NAME_CONFLICT/INVALID_TYPE/INVALID_ITEM), idempotency, cap 200 boundary
- `AdminTrashControllerBulkTest` (NEW, 6 tests) — 200/401/403/400 매트릭스
- `api.adminTrashBulk.test.ts` (NEW, 6 tests) — wire 송신/응답/4xx
- `useAdminTrash.test.tsx` +2 tests — bulk mutation invalidate 검증
- `page.test.tsx` +6 tests — 다중 선택, select-all 토글, 일괄 복원/영구삭제 mutate, 부분 실패 banner, 필터 변경 시 선택 초기화

**Docs**:
- `docs/02 §7.11`에 `POST /api/admin/trash/bulk` 표 row + 상세 endpoint 블록 추가.
- `docs/04 §8.3`에 일괄 복원·영구삭제 항목 [x] 전환 + UI/audit 정책 명시.
- `BETA-RELEASE.md §7` admin-trash-bulk 트랙 backlink + "휴지통 보존 정책 UI / 2인 승인은 v1.x" 명시 (bulk는 closure).

### 검증

- `cd backend && ./gradlew test --tests "com.ibizdrive.admin.trash.*"` BUILD SUCCESSFUL (2m17s) + 영향 범위 (file/folder/trash) GREEN.
- `cd frontend && pnpm test --run` 123 files / **926 passed** (skipped 0).
- `pnpm typecheck` exit 0 / `pnpm lint` exit 0 / `pnpm build` Next.js 컴파일 성공.

### 결정/편차

- 단일 endpoint + action discriminator (RESTful 분리 대신 KISS).
- 부분 실패 모델 (status 항상 200, body의 `failed[]`로 표현) — all-or-nothing은 NAME_CONFLICT 흔한 시나리오에서 운영 가치 ↓.
- 새 audit enum 0 — per-item 기존 emit + actor + timestamp로 묶음 식별 가능.
- Cap 200 (q 길이 cap과 정합) — 클라이언트는 페이지당 max 100을 따라가게 두면 자연스럽게 안전.
- select-all은 현재 페이지(cursor 결과)만 — cursor 다음 페이지 자동 누적은 의도치 않은 영구삭제 위험.
- bulk service는 트랜잭션 미보유 — 단건 service의 짧은 트랜잭션 200개 직렬 처리 (한 항목 실패가 다른 항목 막지 않음).

### 다음 세션 컨텍스트

- v1.x backlog 잔여: 휴지통 보존 정책 UI(`/admin/trash/policy`) / 2인 승인 워크플로 / full path resolve / folder subtree size / 시간대 인지(KST 경계) 날짜 필터 / 권한 grant/revoke direct CRUD / quota.
- progress streaming(SSE/WS) / chunked client(200 초과 자동 분할)도 v1.x++.

### 블로커

- 없음.

---

## 2026-05-08 — 🏁 audit-export-json 트랙 종료 (Wave 1 T2 후속 — `GET /api/admin/audit/export?format=csv|json`)

### 범위

Wave 1 T2(`audit-export-endpoint`)의 CSV-only 다운로드를 `format=csv|json` 분기로 확장. 기존 호출자 호환성 유지(default csv) + AUDIT_EXPORTED audit metadata `format` 동적화. 신규 `AuditJsonWriter`(plain JSON 배열, BOM 미부착, nested metadata) + `AuditExportEvent.format` 필드 + Listener defensive guard.

### 변경 핵심

**Backend:**
- `AuditJsonWriter` 신규 — `ObjectMapper.writeValue(out, entries)`. SequenceWriter 미사용(KISS).
- `AuditExportEvent`에 `String format` 필드 추가(7-arg record).
- `AuditExportListener` — metadata `"format":"csv"` 하드코딩 → `event.format()` 동적, "csv"/"json" 외 값은 "csv" fallback + WARN 로그(defense-in-depth).
- `AuditQueryController.exportCsv` → `export`로 rename + `format` 파라미터(default csv) 분기. `format` 검증 실패 시 `IllegalArgumentException` → `GlobalExceptionHandler.handleBadRequest`가 400 `BAD_REQUEST`.
- 테스트: `AuditExportListenerTest`(신규, 3 케이스), `AuditJsonWriterTest`(신규, 4 케이스), `AuditQueryControllerTest` slice에 jsonWriter mock 추가, `AuditExportE2ETest` 2 케이스 추가(format=json + format=invalid 400).

**Frontend:**
- `api.getAuditLogsExportUrl(filters, format='csv')` 시그니처 확장 + `format` query param 합성. 단위 테스트 5 케이스(`api.getAuditLogsExportUrl.test.ts`).
- `/admin/audit/logs` 페이지 — "CSV 내보내기" + "JSON 내보내기" 두 anchor. testid는 `audit-export-link-csv` / `audit-export-link-json`로 분리. page.test.tsx 갱신(4 케이스).

**Docs:**
- `docs/02 §7.12` — table row에 format 분기 명시 + audit/export 상세 code block 추가.
- `docs/04 §7.2` line 263~265 — `format=csv|json` 분기 + nested metadata + JSON 다운로드 항목 [x] 전환.
- `BETA-RELEASE.md §7` v1.x deferred "audit log JSON export endpoint" 라인 제거 + Source 체인에 audit-export-json 트랙 추가 + audit emit coverage 행에 metadata.format 동적화 명시.

### 검증

- `cd backend && ./gradlew test --tests AuditExportListenerTest --tests AuditJsonWriterTest --tests AuditQueryControllerTest` BUILD SUCCESSFUL (Listener 3 / Writer 4 / Slice 9 모두 GREEN)
- `cd frontend && npm test -- --run api.getAuditLogsExportUrl` 5 PASS, `npm test -- --run page.test.tsx`(audit/logs) 4 PASS
- AUDIT_EXPORTED enum 변경 0 (47 enum 유지). 새 에러 코드 0 (기존 `BAD_REQUEST` 재사용).

### 다음 세션 컨텍스트

- 진정한 SQL → JSON streaming(메모리 cap 제거)은 v1.x.
- `format` enum 강타입화는 v1.x — 현재는 string check + 400으로 충분.
- NDJSON 형식 / `application/x-ndjson`은 별도 트랙 후보.
- 이 트랙은 worktree에서 `frontend/node_modules`를 main repo로 junction(`mklink /J`) — 후속 작업 시 동일 패턴 활용 가능(`npm install`이 stuck하는 환경).

---

## 2026-05-08 — Wave 2 T9 follow-up: `deleted_by` 컬럼 (V10) closure

### 완료

- [Wave 2 T9 follow-up] `files`/`folders.deleted_by UUID` 추가 (V10 마이그레이션) — nullable + 단방향 CHECK(`deleted_at IS NOT NULL OR deleted_by IS NULL`) + FK ON DELETE SET NULL
- soft-delete write path 4곳에 actor 전파: `FileMutationService.softDelete`, `FolderMutationService.softDelete`(root + cascade JPQL `softDeleteByIds(actorId)`), `FileRepository.softDeleteByFolderIds`, `FolderRepository.softDeleteByIds`
- restore 2곳에서 `deleted_by = NULL` 클리어
- `AdminTrashItemDto` 13필드 (`deletedById` + `deletedByEmail` 추가), `AdminTrashService.list` userIds 모음에 deleter 합류 → 단일 batch lookup 유지 (N+1 회피)
- `frontend/src/types/trash.ts` `AdminTrashItem` 확장 (`deletedById/Email: string | null`)
- `/admin/trash/all` 테이블 9컬럼 (크기 ↔ 삭제일 사이에 "삭제자" 추가, NULL은 em dash "—")
- 문서 4: docs/02 §6.5.1 / docs/04 §8.3 / BETA-RELEASE §7 / progress.md
- 게이트: backend `./gradlew test --tests "com.ibizdrive.{admin.trash,folder,file,purge}.*"` BUILD SUCCESSFUL + frontend `pnpm test --run` 121 files / 904 tests / **skipped 0** + `pnpm typecheck/lint/build` exit 0

### 핵심 결정

- **Backfill 미실시**: V10 적용(2026-05-08) 이전 trash row는 `deleted_by IS NULL` 영구 유지. audit_log derivation은 fragile/expensive — UI는 컷오프 이전을 "—"로 렌더 (docs/04 §8.3).
- **단방향 CHECK**: 활성 row에 deleter set 차단만, trash row의 NULL은 허용 (backfill + ON DELETE SET NULL fallback 수용).
- **owner-trash UI 미변경**: owner=deleter 범위라 정보 가치 0, v1.x++로 미룸.
- **새 audit emit 0**: 기존 `FILE_DELETED`/`FOLDER_DELETED` actor_id로 정합성 이미 보장.
- **인덱스 미추가**: `deleted_by` 필터링은 빈도 낮음, v1.x++.

### 다음 세션 컨텍스트

- 본 트랙 PR open 후 merge → `dev/active/wave2-t9-deleted-by/` archive (별도 PR, T9 본체 archive 패턴 재사용 — PR #81 참조).
- BETA-RELEASE §7 `deletedBy 컬럼은 v1.x` 항목은 본 트랙에서 closure 마킹 (해당 라인은 `wave2-t9-deleted-by` backlink로 갱신됨).
- V10 운영 적용은 nullable 컬럼 추가라 PG O(1) — 단일 트랜잭션 적용 KISS.

### 블로커

- 없음.

---

## 2026-05-07 — 🏁 wave2-t9-followup-trash-date-filter 트랙 종료 (Wave 2 T9 후속 — admin global trash 날짜 범위 필터)

### 범위

PR #79 (Wave 2 T9 admin global trash) closure에서 v1.x backlog로 명시되었던 "날짜 범위 필터" 항목을 admin 전용으로 구현. `/admin/trash/all`에서 deletedAt 범위로 필터링 가능. 사용자 트랙(`/api/trash`)은 본 트랙 외.

### 변경 핵심

**Backend:**
- `AdminTrashFilters` (record) — `Instant deletedFromMin`, `Instant deletedToMax` 추가 (3 → 5 필드).
- `AdminTrashController` — `@RequestParam(required=false) String deletedFrom, deletedTo` 수신. `LocalDate.parse` → UTC 경계 `Instant`. 하한은 inclusive(`00:00:00Z`), 상한은 exclusive(`+1d 00:00:00Z`, 즉 입력일 종일 포함). `DateTimeParseException`은 `IllegalArgumentException`으로 wrap → 글로벌 핸들러 400.
- `AdminTrashRepository` — 양쪽 native @Query에 `(:deletedFromMin IS NULL OR deleted_at >= ...)` + `(:deletedToMax IS NULL OR deleted_at < ...)` 추가. 시그니처 7-arg.
- `AdminTrashService` — repo 호출에 두 값 pass-through. 정합 검증: `deletedFromMin >= deletedToMax`(역전/동일) → IllegalArgumentException → 400.

**Frontend:**
- `types/trash.ts` `AdminTrashFilters` — `deletedFrom`/`deletedTo: string | null` (YYYY-MM-DD date-only) 추가.
- `lib/queryKeys.ts` `adminTrashList` — 두 필드를 키 컴포넌트로 추가 (mutation 후 `qk.adminTrash()` prefix 무효화는 그대로).
- `lib/api.ts` `adminListTrash` — `URLSearchParams`에 빈 값 skip 후 추가.
- `app/admin/trash/all/page.tsx` — 필터 영역에 `<input type="date">` 2개 + `~` 구분자 + 한글 aria-label. 초기 상태에 `deletedFrom: null, deletedTo: null` 추가. `updateFilter`가 cursor 리셋 보장.

**Tests:**
- backend `AdminTrashServiceTest` — pass-through, 역전 거부, 동일값 거부 3건 추가. 기존 케이스는 ctor/시그니처 마이그레이션.
- backend `AdminTrashControllerTest` — date 경계 변환(하한 = 당일 00Z, 상한 = +1일 00Z), 400 (deletedFrom/deletedTo 형식) 3건 추가.
- frontend `api.adminTrash.test.ts` — 두 필드 송신 + 단독 적용 2건 추가. EMPTY_FILTERS 확장.
- frontend `useAdminTrash.test.tsx` — `AdminTrashFilters` 확장 필드 반영.

### 검증

- `./gradlew test --tests "com.ibizdrive.admin.trash.*"` BUILD SUCCESSFUL.
- `cd frontend && pnpm test --run` 121 files / **903 passed**.
- `pnpm typecheck` exit 0 / `pnpm lint` exit 0 / `pnpm build` Next.js 컴파일 성공.

### 결정/편차

- 와이어는 `YYYY-MM-DD` date-only 채택 — `<input type="date">` UI와 audit-log `fromDate/toDate` 패턴 정합. ISO-8601 instant 직접 수신 미지원(KISS).
- 시간대: UTC 경계로 단순화. 사용자가 KST 종일을 의도해도 UTC 자정 기준으로 동작 — 일별 휴지통 분포가 9시간(KST→UTC) 시프트되는 효과는 admin tool 한정 사용성 차원에서 v1.x deferred 항목으로 남김.
- `deletedFromMin == deletedToMax`도 거부(빈 결과 보장 모호 — 상한 exclusive 정책 충돌). 실수 방지.

### 다음 세션 컨텍스트

- v1.x backlog 잔여: `deletedBy` 컬럼(V10) / 휴지통 정책 UI(/admin/trash/policy) / bulk restore·purge / 2인 승인 워크플로 / full path resolve / folder subtree size / 시간대 인지(KST 경계) 날짜 필터.
- (2026-05-08 update) `deletedBy` 컬럼(V10)은 본 페이지 최상단 entry로 closure.

### 블로커

- 없음

---

## 2026-05-07 — 🏁 Wave 2 종료 (admin frontend 6 트랙 + 사내 베타 운영 런북)

### 범위

Wave 2는 admin frontend 활성화 + 탐색기 real-fetch 마무리를 묶은 wave다. T4~T9 6 메인 트랙 모두 closure 완료, T6 후속 갭 2건도 정리, T9 archive 완료. `dev/active/` 비우고 `docs/04-admin-operations.md §15`에 사내 베타 운영 런북 신규 작성하여 본 wave 의 운영 매뉴얼을 봉인.

### Wave 2 트랙 인덱스 (closure 완료, merge 시간순)

| 트랙 | PR | merge | 활성 라우트 / 산출물 |
|---|---|---|---|
| Wave 2 T4 — admin-department-crud | #61 (`1378d82`) | 2026-05-06 | `/admin/departments` + audit 3 emit |
| Wave 2 T5 — admin-permission-matrix | #64 (`528ae4b`) | 2026-05-07 | `/admin/permissions` (read-only viewer + 만료 배지) |
| Wave 2 T6 — folder-items-wire | #67 (`ee7781b`) | 2026-05-07 | `/api/folders/{id}/items` + `/api/files/{id}` + 탐색기 mock 일괄 제거 |
| Wave 2 T6 followup — fetch-mock-test-restoration | #76 (`e8dc8b8`) | 2026-05-07 | `api.renameFile`/`api.moveFiles` fetch-mock 패턴 재작성 (10 active 복구) |
| Wave 2 T6 followup — fetch-mock-followup-cleanup | #80 (`1dd30f6`) | 2026-05-07 | fanout 검증 추가 + Phase B skip 삭제 (frontend skipped 0) |
| Wave 2 T7 — admin-dashboard | #70 (`ac63127`) | 2026-05-07 | `/admin` KPI 그리드 8종 + `GET /api/admin/dashboard/summary` |
| Wave 2 T8 — admin-storage-overview | #69 (`4a8ae0f`) | 2026-05-07 | `/admin/storage` 시스템 합계 + 정리 기록 overview |
| Wave 2 T9 — admin-global-trash | #79 (`fdd84e0`) | 2026-05-07 | `/admin/trash/all` + `GET /api/admin/trash` (cross-owner viewer) |
| Wave 2 T9 archive | #81 (`0eafa65`) | 2026-05-07 | dev-docs `dev/active/` → `dev/completed/` 이관 |

### 통계 (Wave 2 누적)

- 코드 PR: 6 메인 + 2 followup = **8 feat/test PR**
- closure docs: archive PR 1 (#81) + 본 entry + `docs/04 §15` 신규
- 신규 backend endpoint: 5 (`/api/admin/dashboard/summary`, `/api/admin/storage`, `/api/admin/trash`, `/api/folders/{id}/items`, `/api/files/{id}`)
- audit emit 변경: T4 +3 enum (`admin.dept.*`), T5/T6/T7/T8/T9 +0
- DB schema 변경: 0 (모든 트랙 read 기반 + 기존 컬럼 재사용)
- 권한 enum 변경: 0
- frontend test: 트랙 시작 시 skipped 11 → closure 시 **0 skipped / 887 passed**

### 핵심 결정 / 편차

- **`deletedBy` 컬럼은 v1.x deferred** (T9 design rationale, docs/02 §7.11). cross-owner 복원 추적은 `audit_log.actor_id` 차선 — `docs/04 §15.1` 운영 SQL 로 봉인.
- **T7 admin-dashboard 가 `/admin` landing 을 KPI 그리드로 재작성** → T9 plan §P6.3 "landing 카드 추가"는 skip. 가시성은 AdminSideNav + 휴지통 KPI 로 확보.
- **휴지통 bulk restore/purge / 날짜 범위 필터 / 2인 승인 / full path resolve** 모두 v1.x deferred (T9 backlog, `docs/04 §15.6` 인덱스 보존).
- **운영 cron 4종 런타임 토글** 미지원 — `application-prod.yml` 편집 + 재기동 절차로 봉인 (`docs/04 §15.4`).

### 사내 베타 운영 런북 (`docs/04 §15`)

코드 변경 없이 admin frontend + audit_log + cron 구성으로 처리 가능한 5 시나리오 + v1.x 분기 인덱스를 단일 섹션으로 정리:

1. §15.1 ADMIN cross-owner 복원/영구삭제 추적 (audit_log SQL)
2. §15.2 휴지통 일일 운영 (단건 처리 + cron 분담표)
3. §15.3 권한 만료 모니터링 (T5 viewer + permissions-expired-cron 검증)
4. §15.4 운영 cron 4종 변경 절차 (T3 read-only + application.yml 재기동)
5. §15.5 스토리지 KPI 해석 (T7 8종 + T8 overview)
6. §15.6 v1.x 전환 backlog 인덱스

### 다음 세션 컨텍스트 (Wave 2 → v1.x)

- `dev/active/` 비어있음. 다음 트랙 진입 시 `dev-docs` 또는 `docs/superpowers/` 신규 spec/plan 으로 시작.
- v1.x 우선 후보 (영향도 ↓ → ↑ 정렬, 본 런북 §15.6 인용):
  1. 휴지통 날짜 범위 필터 (admin/trash 가벼운 확장, 200~300 LoC 추정)
  2. `/admin/trash/policy` UI (cron 런타임 토글)
  3. 휴지통 bulk restore/purge
  4. T7 KPI 확장 (오늘 업로드/다운로드, 쿼터 알림)
  5. `deletedBy` 컬럼 V10 마이그레이션 (DB schema 변경)
- 사내 베타 출시 게이트: `application-prod.yml` cron 4종 활성화 + ADMIN/AUDITOR 계정 프로비저닝 + 본 런북 §15 운영자 인계.

### 블로커

- 없음. Wave 2 6 트랙 + 2 followup + 1 archive 모두 closure. dev/active/ 비움.

---

## 2026-05-07 — 🏁 t6-fetch-mock-followup-cleanup 트랙 종료 (T6 closure 갭 2건 정리: fanout 검증 추가 + Phase B skip 삭제)

### 범위

직전 PR #76(`t6-fetch-mock-test-restoration`, e8dc8b8) closure에서 명시한 두 갭을 단일 PR로 정리. 새 기능 0·docs 변경 0·테스트 위생만.

### 변경 핵심

- `frontend/src/lib/api.moveFiles.test.ts` (+1 case) — "멀티 아이템은 Promise.all로 N개 fetch fanout". `Promise.all` 호출 순서 비결정성을 고려해 URL 집합 단언(`.sort()`). `useMoveBulk.test.tsx`는 `api.moveFiles`를 통째 mock하므로 fanout은 api 레이어에서만 검증 가능 — 책임 경계 정확.
- `frontend/src/lib/api.renameFile.test.ts` (-1 it.skip + 코멘트 2줄) — 폴더 rename + folderTree invalidate은 `useRenameFile.test.tsx:91-117`에서 hook 레이어로 이미 커버. api 함수는 캐시 무효화 책임 없음 → 보류 테스트는 misplaced였음. Phase B 재작성이 아니라 삭제가 정답.

### 검증

- `cd frontend && pnpm test --run` — 118 files / **887 passed | 0 skipped | 0 failed**. 직전 1 skipped → 0.
- `pnpm typecheck` exit 0 / `pnpm lint` exit 0 / `pnpm build` exit 0.

### 다음 세션 컨텍스트

- T6 fetch-mock 후속 갭 모두 정리. 프론트 테스트 skipped=0 상태 유지.

---

## 2026-05-07 세션 — Wave 2 T9: admin-global-trash

### 완료
- [Wave 2 T9] `GET /api/admin/trash` admin listing endpoint (DTO: owner/originalParent/size 노출)
- [Wave 2 T9] `/admin/trash/all` 페이지 (filter q/type/ownerId + cursor pagination + ConfirmDialog purge + AdminSideNav 토글)
- [Wave 2 T9] mutation 0 신규 — 기존 `api.restoreFile/restoreFolder/purgeTrashItem` 재사용 (ADMIN ROLE이 SpEL 가드 통과)
- [Wave 2 T9] audit emit 변경 0 (47 enum 유지)
- [Wave 2 T9] DB schema 변경 0 (deletedBy 컬럼 미도입, audit_log lookup이 차선)
- [Wave 2 T9] docs/02 §7.11, docs/04 §2 + §8.3, BETA-RELEASE Source/§7 갱신

### 결정/편차
- T7 admin-dashboard(`ac63127`)가 `app/admin/page.tsx`를 KPI 그리드로 재작성하여 plan §P6.3 "landing 카드 추가"는 skip. 가시성은 AdminSideNav 활성 + 기존 '휴지통 파일' KPI로 확보.
- spec §4.4 `deletedBy` 미surface는 의도적 제한. cross-owner 복원 추적은 audit_log의 actor_id로 차선 경로.

### 다음 세션 컨텍스트
- v1.x backlog: `deletedBy` 컬럼(V10) / 휴지통 정책 UI(/admin/trash/policy) / bulk restore·purge / 2인 승인 워크플로 / 날짜 범위 필터 / full path resolve / folder subtree size.
- 사내 베타 운영 매뉴얼: ADMIN cross-owner 복원 시 audit_log 의 actor_id 추적 절차 문서화 필요(별도 트랙).

### 블로커
- 없음

---

## 2026-05-07 — 🏁 t6-fetch-mock-test-restoration 트랙 종료 (Wave 2 T6 후속 — `api.renameFile`/`api.moveFiles` fetch-mock 패턴 복구)

### 범위

Wave 2 T6 closure(2026-05-07)에서 `MOCK_TREE`/`MOCK_FILES` 일괄 제거와 함께 `describe.skip` 처리되었던 두 테스트 파일을 프로젝트 표준 fetch-mock 패턴(`vi.stubGlobal('fetch', ...)`)으로 재작성. real-wired된 `renameFile`/`moveFiles`의 회귀 가드 복원. 새 기능 도입 없음·docs 변경 없음·feature scope 0·테스트 위생만.

### 변경 핵심

**Frontend tests (T2~T3):**
- `frontend/src/lib/api.renameFile.test.ts` 재작성 — 5 active (빈 이름 client-side 가드 / 200 OK rename / 409 RENAME_CONFLICT / 404 NOT_FOUND / 200 no-op 동명 rename) + 1 it.skip(폴더 rename tree 반영 — Phase B 의존).
- `frontend/src/lib/api.moveFiles.test.ts` 재작성 — 5 active (400 MOVE_INTO_SELF / 400 MOVE_INTO_DESCENDANT / 404 TARGET_NOT_FOUND / 204 file move + body / 204 movedIds 단건). `moveItem` void 반환에 대응해 mock은 204 No Content.
- 두 파일 동일 패턴(`api.adminStorage.test.ts` mirror): `jsonResponse(body, status=200)` helper + `beforeEach`/`afterEach` stub teardown + URL/method/body assertion.

### 검증

- `cd frontend && pnpm test --run` — 115 files / 862 passed | 1 skipped (Phase B 잔존 only). 트랙 시작 11 skipped → 1 skipped (net −10 active).
- `pnpm typecheck` exit 0 / `pnpm lint` exit 0 / `pnpm build` Next.js 컴파일 26.0s 성공.
- `grep -r 'MOCK_TREE\|MOCK_FILES' frontend/src/` empty(헤더 코멘트 토큰까지 "내장 모의 데이터"로 중립화).
- `api.ts`/`errors.ts` 변경 없음(테스트 위생 한정).

### 다음 세션 컨텍스트

- `api.renameFile.test.ts`의 1 it.skip(`폴더 이름 변경 시 tree에도 반영`)은 Phase B(real-fetch tree refetch + cache invalidation 통합) 의존으로 본 트랙 외 보류. Phase B 트랙에서 활성화 예정.
- 두 happy-path move test(case 4·5)가 단일 fetch fanout만 검증 — 멀티 아이템 `Promise.all` 병렬 호출 경로는 별도 unit가 부재. `useMoveBulk` mutation hook 테스트에서 커버하는 것이 자연 (별도 트랙 후보).
- Code review에서 제안된 "case 4와 case 5 중복" 지적은 spec 준수 차원에서 단건 반환 검증으로 보존(향후 fanout 멀티 케이스 추가 시 재검토 가능).

### 블로커

- 없음. 본 트랙 closure 완료.

---

## 2026-05-07 — 🏁 folder-create-ui 트랙 종료 (탐색기 "새 폴더" 진입점)

### 범위

backend `POST /api/folders` + frontend `api.createFolder()`는 이미 존재했으나 진입 UI가 없어 사용자가 폴더를 만들 수 없는 상태였다. `FolderToolbar`에 "새 폴더" 버튼 + `CreateFolderDialog` (제어 컴포넌트, props 기반) + `useCreateFolder` mutation + `invalidations.afterFolderCreated` 헬퍼 4종을 frontend 단독으로 추가.

### 변경 핵심

- `lib/queryKeys.ts`: `invalidations.afterFolderCreated(qc, { parentId })` — `qk.filesListPrefix(parentId)` + `qk.folderTree()` + `qk.folder(parentId)` 3개 무효화 (afterRename 폴더 케이스 답습).
- `hooks/useCreateFolder.ts`: `useMutation` — onSuccess에서 위 헬퍼 호출. 409/403은 envelope 그대로 onError surface.
- `components/explorer/CreateFolderDialog.tsx`: 제어 컴포넌트(`parentId`/`open`/`onClose` props). `normalizeFileName` 클라이언트 사전 validation으로 빈/길이/금지문자/예약어/끝점 5종 인라인 메시지. 409 RENAME_CONFLICT → "같은 이름의 폴더가 이미 있습니다", 403 → "폴더를 만들 권한이 없습니다", 그 외 → generic — 다이얼로그 유지하여 사용자가 이름 수정 후 재시도.
- `components/upload/FolderToolbar.tsx`: UploadButton 옆 "새 폴더" 버튼 + 다이얼로그 mount (`useState`로 보유, 전역 store 도입 안 함 — KISS).
- `docs/01 §6.2` 무효화 매트릭스 row 갱신 (helper 명시).

### 게이트 통과

- `pnpm typecheck` ✅, `pnpm lint` ✅, `pnpm test` ✅ 116 files / 865 tests pass / 11 skipped / **0 fail**
- §3 핵심 원칙 11개 위반 없음. 특히:
  - 원칙 1 (URL이 어디 소유): parentId는 `useCurrentFolder().folderId`로 URL 단일 소스
  - 원칙 3 (낙관적 업데이트는 비파괴적만): 본 트랙은 신규 추가이지만 단순화를 위해 mutation pending 상태 사용, 낙관 캐시 prepend 없음
  - 원칙 11 (정규화 함수 동일): `normalizeFileName` 그대로 호출, 신규 정책 추가 안 함

### 의도적으로 v1.x deferred

- FolderTree 인라인 "+ 새 폴더" / 컨텍스트 메뉴
- FileTable 빈 영역 우클릭
- 키보드 단축키 (Ctrl+Shift+N)
- 생성 직후 자동 네비게이션 / 자동 선택 / rename 인라인 모드

### 다음 세션 컨텍스트

- 본 트랙 PR 머지 후 `dev/active/folder-create-ui/` → `dev/completed/folder-create-ui/` 이관.
- 향후 deferred 아이템 활성화 시 helper(`afterFolderCreated`) + dialog 재사용.

---

## 2026-05-07 — 🏁 admin-dashboard 트랙 종료 (운영 KPI 대시보드 — `/admin` 활성화)

### 범위

`/admin` 진입점을 v1.x deferred landing(가용 카드 2 + 안내 박스)에서 8개 KPI 그리드로 교체. backend `GET /api/admin/dashboard/summary` 단일 endpoint가 `@PreAuthorize hasRole('ADMIN')`로 envelope `{ summary: {...} }` 반환 — 등록·활성 사용자, 부서(전체/활성), 활성 폴더, 활성·휴지통 파일, 24h 감사 이벤트, 스토리지 사용량(SUM(file_versions.size_bytes)).

### 변경 핵심

**Backend (P1):**
- `AdminDashboardController` GET `/api/admin/dashboard/summary` + `@PreAuthorize("hasRole('ADMIN')")`.
- `AdminDashboardService` 6 derived count + 1 JPQL SUM + 1 JdbcTemplate native (audit_log COUNT 24h, `Clock` 주입으로 결정성 확보).
- `AdminDashboardSummaryResponse` nested record envelope.
- repository 확장(append-only): `User.countByDeletedAtIsNull/+IsActiveTrue`, `Department.countByDeletedAtIsNull`, `Folder.countByDeletedAtIsNull`, `File.countByDeletedAtIsNull/+IsNotNull`, `FileVersion.sumAllSizeBytes()`.
- audit emit 없음 (read-only). DB 스키마 변경 없음.

**Frontend (P2~P3):**
- `types/admin.ts` (`AdminDashboardSummary` + Response envelope, backend record 1:1 mirror).
- `lib/api.ts`: `api.adminGetDashboardSummary()` (read-only, no CSRF).
- `lib/queryKeys.ts`: `qk.adminDashboard()`.
- `lib/formatBytes.ts`: 1024 진법 B/KB/MB/GB/TB.
- `hooks/useAdminDashboardSummary.ts` (envelope unwrap, retry false, staleTime 0).
- `components/admin/DashboardKpiCard.tsx` (label + value + sub) + `DashboardSummary.tsx` (8 KPI 그리드 + 단일 로딩/에러).
- `app/admin/page.tsx` 전면 재작성 (deferred landing → KPI 그리드).
- `components/admin/AdminSideNav.tsx`: '대시보드'를 ACTIVE_ITEMS로 이동(`/admin` exact), DEFERRED_ITEMS에서 제거.

### 검증

- `cd backend && ./gradlew test` — BUILD SUCCESSFUL (4m 46s, 신규 ServiceTest 5 + ControllerTest 3 + sumAllSizeBytes 2 = 10건).
- `cd frontend && pnpm vitest run` — 105 files / 820 passed (2 skipped) — 신규 api 3 + hook 2 + formatBytes 7 + page 5 + sidenav 3 = 20건.
- `pnpm typecheck` exit 0.

### 다음 세션 컨텍스트

- KPI 8개 모두 단일 응답 — 카드별 fetch 분산 안 함(KISS). 추가 KPI는 동일 envelope 확장.
- audit_log 24h count는 JdbcTemplate 직접 — JPA 엔티티 미도입(기존 `AuditQueryService` 패턴 답습).
- Department의 total/active 동치는 `is_active` 컬럼 부재 결과(envelope 일관성을 위해 두 필드 동일 값).
- v1.x 후속: 오늘 업로드/다운로드 수, 쿼터 알림, 비정상 패턴 감지(docs/04 §3.2).

### 블로커

- 없음. 본 트랙 P4 closure 완료.

---

## 2026-05-07 — 🏁 wave2-t6-folder-items-wire 트랙 종료 (Wave 2 T6 — explorer mock 제거 + listing/detail/mutation real wire)

### 범위

탐색기 화면이 mock 데이터를 끊고 backend로 완전 연결되었다. P1~P2에서 backend 2개 read endpoint(`GET /api/folders/{id}/items`, `GET /api/files/{id}`) 추가, P3에서 frontend의 `MOCK_TREE`/`MOCK_FILES`와 in-memory tree mutation helper(`detachFromTree`, `relocateInTree` 등)를 일괄 제거, P4에서 hooks/invalidations와 upload 파이프라인을 real-fetch로 정렬. 파일 업로드 후 listing 미반영 회귀(서버 201 응답 처리 누락)도 함께 수정.

### 변경 핵심

**Backend (P1~P2):**
- `FolderController.items(UUID id, SortKey sort, SortDir dir)` + `@PreAuthorize("hasPermission(#id, 'folder', 'READ')")` — `GET /api/folders/{id}/items` (folder/file 묶음 응답).
- `FolderQueryService.loadItems(...)` — parent active 검증 → subfolders/files fetch → 그룹별 정렬 후 concat. SIZE 정렬 시 폴더 그룹은 `name asc` fallback (folder size 무의미).
- `SortKey`/`SortDir` enum + `@RequestParam` 자동 변환. DTO `FolderItemDto`(8 fields) + `FolderItemsResponse`.
- `FileController.detail(UUID id)` + `@PreAuthorize("hasPermission(#id, 'file', 'READ')")` — `GET /api/files/{id}`.
- `FileQueryService.loadDetail(id)` — soft-delete 제외 필터.
- TDD: `FolderQueryServiceItemsTest` 6 case + `FolderControllerItemsTest` 3 case + `FileQueryServiceTest` 3 case + `FileControllerDetailTest` 3 case.

**Frontend (P3~P4):**
- `lib/api.ts` — `MOCK_TREE`/`MOCK_FILES`/`detachFromTree`/`relocateInTree`/`findNode`/`containsNode`/`normalizedNameForDedup` 일괄 제거(약 130 LOC). `getFilesInFolder`, `getFileDetail`, `moveItem`, `moveFiles`(Promise.all fanout), `renameFile`, `createFolder`를 real-fetch로 전환.
- `renameFile(id, newName, isFolder=false)` 시그니처에 `isFolder` 추가 — backend dispatch(`/api/files/{id}` PATCH vs `/api/folders/{id}` PATCH)에 사용.
- `moveFiles(items: Array<{id, type}>, targetFolderId)` — id-only에서 `{id, type}` discriminated payload로 변경. 호출부(MoveFolderDialog, DnD)에서 캐시 룩업 또는 drag data로 type 결정.
- `useMoveBulk` Vars: `{ items, sourceFolderId, targetFolderId }` — 동일 패턴 (id-only → items).
- `useUpload`: backend `FileUploadController`가 신규=201 / 신버전=200 — 기존 `xhr.status === 200`만 done 처리하던 분기 → `200 || 201` 동시 처리. 성공 시 `qk.filesListPrefix(targetFolderId)` invalidate로 listing 즉시 갱신.

**Docs:**
- docs/02 §7.5 — `GET /api/folders/{id}/items` 행 + 응답/정렬/SoftDel 명세 블록 추가.
- docs/01 §6.1 — `qk.filesListPrefix(folderId)` 추가 (sort/dir 변종 일괄 invalidate용 prefix).

### 검증

- backend: `./gradlew test --no-daemon` BUILD SUCCESSFUL in 5m 16s ✓.
- frontend: `pnpm test --run` 817 passed / 11 skipped(105 files), `pnpm lint` exit 0, `pnpm build` exit 0 (`/files/[...parts]` 25.4 kB / First Load 171 kB).
- skipped 11: `api.renameFile.test.ts` / `api.moveFiles.test.ts`는 P3에서 MOCK 의존이 끊겨 fetch 모킹 패턴 재작성을 post-T6로 보류(`describe.skip`).

### 핵심 결정 (KISS)

- `qk.folderItems(folderId, sort, dir)` 신설 대신 기존 `qk.filesInFolder` 재사용 — `/items` 응답이 file/folder 합쳐진 형태이지만 캐시 keyspace 분리 효용보다 invalidations 엔트리 일관성이 큼. 호출부는 type discriminator로 분기.
- backend 단일 endpoint(`/items`) → frontend bulk 작업은 `Promise.all` fanout 패턴 — 트랜잭션 경계는 단일 항목 단위 유지(부분 실패 visibility).
- 'root' 가상 노드는 frontend-only (UUID 변환 안 됨) — 업로드/listing은 root 시 special-case 처리.

### 다음 세션 컨텍스트

- P5 게이트에서 `api.renameFile.test.ts` / `api.moveFiles.test.ts`를 fetch-mock 패턴(`vi.stubGlobal('fetch', ...)`)으로 전환 필요 — 별도 후속 트랙.
- Manual smoke 7 시나리오는 PR 머지 전 사용자 점검 필요(자동화 환경 외).
- `qk.fileVersions`/`fileActivity` 등 RightPanel 키와 docs/01 §6.1의 wording 차이가 일부 남음 — 다른 트랙과의 통합 sync는 별도.

---

## 2026-05-07 — 🏁 admin-permission-matrix 트랙 종료 (Wave 2 T5 — 권한 매트릭스 read-only viewer)

### 범위

`/admin/permissions` 페이지 활성화 — 관리자가 grant 전체 목록을 subjectType / subjectId / resourceType / preset / q 5축으로 검색·조회할 수 있다. 만료된 grant 도 포함되어 빨간 "만료됨" 배지로 가시화 (cron 정리 전 상태 인지). mutation(grant/revoke direct CRUD)은 **v1.x deferred** — Option A read-only ship 결정.

### 변경 핵심

**Backend (P1~P3):**
- `PermissionRepository.findAllForAdminPageable(filters, Pageable)` — native @Query, LEFT JOIN users/departments/folders/files + INNER JOIN granter, 동적 WHERE `(:param IS NULL OR p.col = :param)` 패턴, `LOWER(name) LIKE :q ESCAPE '\\'` 5필드 OR.
- `AdminPermissionService.list(filters, Pageable)` — q `trim+lowercase+LIKE escape '\' '%' '_' + '%' wrap`, subjectId-only(without subjectType) → 400, subjectType=everyone → subjectId 강제 null화, isExpired = `expiresAt < now`.
- `AdminPermissionController` `GET /api/admin/permissions` + `@PreAuthorize("hasRole('ADMIN')")`, page default 0 / size default 20 cap 100.
- `AdminPermissionRowResponse` record (id, subject*, resource*, preset, grantedBy*, grantedAt, expiresAt, isExpired).

**Frontend (P4~P5):**
- `types/permission.ts` — `AdminPermissionRow`, `AdminPermissionFilters`, `AdminPermissionPage`, `ADMIN_SUBJECT_TYPES`/`ADMIN_RESOURCE_TYPES`/`ADMIN_PRESETS` 상수.
- `lib/api.ts` — `adminListPermissions(filters)` GET, no CSRF, 빈 값 query 제외, 401/403/400 envelope `buildApiError` 매핑.
- `lib/queryKeys.ts` — `adminPermissionsList(filters)` 단일 키 (invalidations entry 미추가 — read-only).
- `hooks/useAdminPermissions.ts` — q `trim+lowercase`, subjectId trim, `placeholderData: keepPreviousData`로 page 전환 깜빡임 완화.
- `app/admin/permissions/page.tsx` — FilterBar(5 필터, q 300ms debounce) + 6 컬럼 테이블 + "만료됨" 빨간 배지 + Pagination(prev/next + page X/Y).
- `components/admin/AdminSideNav.tsx` — '권한' DEFERRED → ACTIVE_ITEMS.

**Docs:**
- docs/02 §7.12 admin matrix table에 `GET /api/admin/permissions` 행 + 풀 스펙 블록 추가 (manual filter matrix + 응답 shape + 만료 grant 포함 정책 명시).
- docs/04 §2 활성 라우트 목록 + 트리에서 `/permissions` 활성 swap (서브트리 `/bulk` `/templates`는 v1.x 유지).
- BETA-RELEASE.md header Source 트랙 closure 추가 + §7 admin frontend wording 갱신 (`/admin/permissions` 활성, "권한 mutation" 문구만 v1.x).

### 검증

- frontend: `pnpm test --run` 820/820 GREEN(102 files), `pnpm typecheck`/`pnpm lint`/`pnpm build` 모두 exit 0 — `/admin/permissions` 3.38 kB / First Load 118 kB.
- backend: `./gradlew test --no-daemon` GREEN (Windows에서 Testcontainers `disabledWithoutDocker=true`로 스킵; CI Linux runner에서 E2E 13 실행).

### read-only 정책 정당성

- mutation(grant/revoke) endpoint를 함께 ship하면 P1~P6 6 phase 풀 스펙(권한 evaluator 회귀 + audit emit 2종 + UI confirm dialog) 부담. Option A read-only로 분리 시 본 트랙은 viewer 1 endpoint + 1 page로 작아짐 — 만료 가시화/grant 조사라는 운영 1차 효용은 즉시 확보.
- `placeholderData: keepPreviousData`로 페이지 전환 ghost 행 방지 (T4 동형). q 정규화는 호출자(hook) 책임 — 서비스에서 한 번 더 정규화하지 않음(중복 방지).

### 다음 세션 컨텍스트

- v1.x grant/revoke direct CRUD 트랙 시 본 트랙의 `useAdminPermissions` 캐시 invalidation entry 추가 필요 (`afterAdminPermissionChanged`). 현재는 read-only이므로 entry 미추가.
- 만료 cron(만료 grant 정리)은 `audit-emit-gap-mapping` deferred 매핑과 별도 트랙 — 본 viewer가 만료 grant 노출하므로 cron이 도입되어도 데이터 표시 정책 변경 불필요.
- T5 PR 머지 후 master rebase 시 BETA §7 admin frontend wording / docs/04 §2 트리 줄에서 master의 다른 admin 트랙과 사소 conflict 가능 — wording 머지(둘 다 활성).

---

## 2026-05-07 — 🏁 wave1-t3-system-cron-readonly 트랙 종료 (Wave 1 T3 — 시스템 정책 페이지 / 운영 cron 4종 read-only 노출)

### 범위

`/admin/system` 페이지 활성화 — 관리자가 운영 cron 4종(`purge.expired`, `share.expire`, `permission.expire`, `storage.orphan.cleanup`)의 현재 설정(`enabled/cron/zone/batchSize/maxPerRun/graceHours`)을 한 화면에서 확인. 변경 UI는 v1.x deferred — application.yml 수정 + 재기동이 운영 통제 경로(SCM diff 자동 audit). audit emit 0(SELECT-only).

### 변경 핵심

**Backend:**
- `CronJobStatusResponse` (record + `@JsonInclude(NON_NULL)`) — 단일 DTO + optional batchSize/maxPerRun/graceHours로 4 잡 통합 (KISS: 프론트 union discriminator 회피).
- `AdminSystemController` `GET /api/admin/system/cron` — `@PreAuthorize("hasRole('ADMIN')")` (T2 audit-export와 달리 AUDITOR 제외 — 운영 설정은 admin 책임). `List.of(...)` 고정 순서 — purge → share → permission → storage. 4 `@ConfigurationProperties` bean(`HardPurgeProperties` / `ShareExpirationProperties` / `PermissionExpirationProperties` / `StorageOrphanCleanupProperties`)를 생성자 주입.
- `AdminSystemControllerTest` (`@WebMvcTest` + 내부 `PropertiesConfig` `@Configuration`) — 8 테스트: 200 fixed-order, payload per job, 401, MEMBER 403, AUDITOR 403.

**Frontend:**
- `types/system.ts` — `CronJobKey` literal union + `CronJobStatus` (optional 3 필드) + `CronJobsResponse`.
- `lib/api.ts` `adminGetCronStatus()` + `lib/queryKeys.ts` `adminSystem/adminSystemCron` factory.
- `hooks/useAdminSystem.ts` `useAdminSystemCron` (staleTime=30s — 정적 설정, retry=false — 401/403 즉시 표면화).
- `app/admin/system/page.tsx` — 4 카드 grid + ON/OFF 배지(색+텍스트 양쪽, docs/01 §12 a11y) + cron/zone/optional 필드. Header 헬퍼: "변경은 application.yml + 재기동".
- `components/admin/AdminSideNav.tsx` — '시스템' DEFERRED → ACTIVE_ITEMS.
- `app/admin/page.tsx` landing — '시스템 정책' 카드 추가, deferred 리스트에서 제거.

**Docs:**
- docs/02 §7.12 Admin 표 행 추가 + `GET /api/admin/system/cron` 풀 스펙(고정 순서 + audit emit 0 명기).
- docs/04 §2 활성 라우트 4 → 5(`/admin/system` 추가) + 트리에서 `/system` 활성 표기.
- docs/04 §13 — `/admin/system`에서 4 cron 설정 노출 안내(application.yml 변경 경로 강조).
- BETA-RELEASE.md §7 — admin frontend wording 갱신("권한/스토리지/정책 페이지" 잔여 — `/admin/system` Wave 1 T3 활성).

### 검증

- backend `./gradlew test` ✅ BUILD SUCCESSFUL (전체).
- frontend `pnpm test --run` ✅ 101 files / 806 tests pass (신규 6 tests: api.adminSystem 3 + page 3).
- frontend `pnpm typecheck` ✅, `pnpm lint` ✅, `pnpm build` ✅ (`/admin/system` 2.02 kB / 118 kB First Load).

### 다음 세션 컨텍스트

- 본 endpoint는 read-only — mutation은 v1.x. 운영자 cron 변경 절차는 docs/04 §13 + application.yml 직접 편집 + 재기동.
- AUDITOR가 cron 설정 확인을 요청할 가능성 — 현재는 403. 필요 시 별도 deferred 트랙(read-only 권한 확장)으로 처리.
- Wave 1 잔여 트랙은 docs/progress.md 상위 wave 인덱스 참조.

---

## 2026-05-06 — 🏁 audit-export-endpoint 트랙 종료 (Wave 1 — T2: server-side CSV 스트리밍 export + `AUDIT_EXPORTED` emit)

> closure entry backfill (2026-05-07) — PR #60 머지(2026-05-06 15:00) 시점에 progress.md 등재가 누락되어 사후 보충. dev/completed/audit-export-endpoint/(plan|context|tasks).md는 머지 시 정상 archive됨.

### 범위

`/admin/audit/logs`가 client-side current-page CSV에서 server-side full-result CSV 스트리밍으로 전환. `GET /api/admin/audit/export` 신설 — 기존 query endpoint와 동일 필터(`eventType`/`fromDate`/`toDate`/`actorQuery`) 재사용, 하드 캡 10,000행 + 초과 시 `X-Audit-Export-Truncated: true` 헤더 + AUDIT_EXPORTED metadata `truncated=true`. 가드는 ADMIN/AUDITOR (T3 admin-only와 분리 — 감사권은 운영권과 별개).

### 변경 핵심

**Backend (P1~P3):**
- `AuditCsvWriter.write(OutputStream, List<AuditLogEntryDto>)` (NEW) — `\uFEFF` BOM + RFC 4180 quoting + `\r\n`. metadata는 Jackson serialize(`LinkedHashMap` 결정적 순서, null → 빈 셀). 헤더 10 컬럼은 frontend `AUDIT_CSV_HEADERS`와 1:1.
- `AuditQueryService.exportAll(filters, viewerId, viewerRole)` — `ADMIN`/`AUDITOR` 외 IllegalStateException 가드, `LIMIT cap+1` (cap=10,000)으로 truncation 감지, return record `AuditExportResult(entries, truncated)`. WHERE 빌더 `appendFilterClauses(...)` private helper 추출 → `search()`와 공유 (필터 시맨틱 drift 차단).
- `AuditQueryController.exportCsv(...)` — `@PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")`, `@GetMapping("/export")`, `ResponseEntity<StreamingResponseBody>`. 응답 헤더: `Content-Type: text/csv;charset=UTF-8`, `Content-Disposition: attachment; filename="audit_logs_<yyyy-MM-dd>.csv"`. 응답 stream 작성 후 같은 메서드에서 `applicationEventPublisher.publishEvent(new AuditExportEvent(...))`.
- `AuditExportEvent` (NEW record: `actorId`/`actorIp`/`userAgent`/`filtersJson`/`rowCount`/`truncated`) — IP/UA를 이벤트에 carry. `StreamingResponseBody`가 다른 스레드에서 실행될 때 `WebRequestContextHolder` ThreadLocal 손실 방지.
- `AuditExportListener.onExport(...)` (NEW) — `AuditService.record(...)` (REQUIRES_NEW, ADR #24). metadataJson = `{"filters":{...},"rowCount":N,"truncated":bool,"format":"csv"}`. RuntimeException → ERROR 로그만 (PermissionAuditListener와 동형 — caller 흐름 보호).

**Frontend (P5):**
- `lib/api.ts` `getAuditLogsExportUrl(filters)` — `URLSearchParams`로 `eventType/fromDate/toDate/actorQuery` 빌드.
- `app/admin/audit/logs/page.tsx` — 기존 `handleExport`(client-side `toAuditCsvBlob`)를 `<a href={url} download>` anchor 패턴으로 교체. fetch 미사용.
- 죽은 코드 제거: `lib/auditCsv.ts`, `lib/auditCsv.test.ts` (KISS / YAGNI).
- `app/admin/audit/logs/page.test.tsx` (NEW) — 기본 URL, eventType 필터 반영, click 시 fetch 미발생 검증.

**Docs:**
- BETA-RELEASE.md §6 emit coverage `36/44 → 37/44`, line 115 deferred 라인 정정 (CSV는 ship, JSON v1.x).
- docs/02 §7.12 — `GET /api/admin/audit/export` 행 + 풀 스펙(가드 ADMIN/AUDITOR + truncation 헤더 + 캡 10k).
- docs/04 §7.2 — 두 deferred 체크박스 `[x]` 처리 (server-side streaming + `AUDIT_EXPORTED` runtime emission).
- docs/audit-emit-gap-mapping.md — `AUDIT_EXPORTED` 항목 deferred → ✓ emit으로 갱신.

### 검증

- backend `./gradlew test --tests "com.ibizdrive.audit.*"` ✅ — 신규 `AuditExportE2ETest`(MEMBER 403 / AUDITOR·ADMIN 200 / CSV 본문 / `AUDIT_EXPORTED` row 검증) + `AuditCsvWriterTest`(quoting / escape / BOM / metadata 직렬화) + 회귀(`AuditQueryE2ETest`/`AuthAuditE2ETest`/`AuthScenarioIntegrationTest`) 전체 그린.
- frontend `pnpm exec vitest run` 769/769 ✅, `pnpm exec tsc --noEmit` 0 error, `pnpm exec next lint` clean.

### 핵심 결정 (KISS)

- **publish는 controller 메서드 내부 (요청 스레드)** — `StreamingResponseBody`가 다른 스레드에서 실행되면 `WebRequestContextHolder` IP/UA가 손실. 응답 stream 작성 직후 같은 메서드에서 publish하여 listener가 정상 캡처.
- **WHERE 빌더 헬퍼 공유** — `search()`와 `exportAll()`이 `appendFilterClauses(...)` 동일 사용. 두 endpoint의 필터 의미가 자동 일치 (drift 차단).
- **CSV injection 정책** — v1은 RFC 4180 quoting만. `=`/`+`/`-`/`@` prefix 셀 sanitize는 v1.x로 미룸 (외부 첨부 아닌 내부 audit 데이터 → 노출 표면 작음).
- **MEMBER 가드 분리** — query endpoint는 self 조회 허용이지만 export는 정책상 ADMIN/AUDITOR로 더 엄격. 두 endpoint 가드 차이를 docs/02 §7.12에 명시.

### 다음 세션 컨텍스트

- audit log JSON export는 v1.x (docs/04 §7.2). 본 트랙은 CSV만 ship.
- Wave 1 — T3 (시스템 정책 페이지 — cron 토글 read-only 노출) 트랙 진입 가능. 별도 worktree 권장 (T2 PR과 독립).

---

## 2026-05-06 — 🏁 admin-department-crud 트랙 종료 (Wave 2 T4 — 관리자 부서 CRUD + audit emit 3종)

### 범위

`/admin/departments` 페이지 활성화 — 관리자가 부서를 생성/검색/이름변경/비활성/재활성할 수 있다. backend `Department` 도메인 lifecycle 메서드 + V9 partial unique + audit_log target_type 'department' 허용 + service/controller/listener 풀 스택 구현.

### 변경 핵심

**Backend (P1~P3, V9):**
- V9 마이그레이션: `idx_departments_name_active` partial unique (`WHERE deleted_at IS NULL`) — 활성 부서 이름 충돌 DB 차단(CLAUDE.md §3 원칙 6). audit_log target_type CHECK 'department' 추가.
- `Department.rename/deactivate/reactivate/isActive` — soft-delete 의미 등가, 멱등.
- `DepartmentRepository.findAllForAdminPageable(q, Pageable)` — 비활성 포함, LIKE escape.
- `AdminDepartmentService` 5종 동작 + 3 events. update는 rename + reactivate 흡수(T1 ADMIN_USER_UPDATED 정책 동형).
- `AdminDepartmentController` GET/POST/PATCH `/api/admin/departments` + `@PreAuthorize("hasRole('ADMIN')")`.
- `AdminDepartmentAuditListener` AFTER_COMMIT 3 emit.
- `AuditTargetType.DEPARTMENT` + `AuditEventType.ADMIN_DEPARTMENT_CREATED/_UPDATED/_DEACTIVATED` (44 → 47 enum).
- `DepartmentConflictException` → `GlobalExceptionHandler` 409 매핑(envelope `{error:{code:"DEPARTMENT_CONFLICT"}}`).

**Frontend (P4~P5):**
- `types/department.ts` Admin* 타입 4종 + `types/audit.ts` 'department' resource + 3 event mirror.
- `lib/api.ts` adminListDepartments/Create/Update + `lib/queryKeys.ts` `adminDepartmentsList` + `invalidations.afterAdminDepartmentChanged`.
- `hooks/useAdminDepartments.ts` 단일 파일 통합 (KISS — admin-user-mgmt의 4파일 분리와 다른 정책: 본 트랙 4 동작이 의미적 한 묶음).
- `app/admin/departments/page.tsx` — 생성 폼 + 검색(300ms debounce) + 목록 + rename inline + (de)activate toggle + pagination.
- `components/admin/AdminSideNav.tsx` — '부서' DEFERRED → ACTIVE_ITEMS.

**Docs:**
- docs/02 §2.2 V7 schema 정정 + V9 partial unique 명시 / §2.8 audit_log target_type CHECK 'department' / §7.12 Departments 3 endpoint 풀 스펙 / §8 DEPARTMENT_CONFLICT 행.
- docs/03 §4.1 `admin.department.created/updated/deactivated` 3종 추가.
- docs/04 §2 활성 라우트 + 트리 / §5 `/admin/departments` 활성 명세.
- BETA-RELEASE.md §6 (35→39 emit / 44→47 enum, ~83%) + §7 admin frontend wording 갱신.

### 검증

- backend: `./gradlew test` GREEN.
- frontend: `pnpm test --run` 789/789 GREEN(99 files), `pnpm typecheck` clean, `pnpm lint` clean, `pnpm build` clean — `/admin/departments` 5.09 kB / First Load 119 kB.

### Audit emit 의미 분리 유지

- `_DEACTIVATED` (제재 분기) ↔ `_UPDATED` (rename + reactivate 등 일반 속성). admin-user-mgmt `ADMIN_USER_DEACTIVATED` ↔ `ADMIN_USER_UPDATED`와 동형.

### 다음 세션 컨텍스트

- 본 트랙 PR 제출 → master rebase 시 BETA §6 / docs/04 §2 / docs/04 §5 줄에서 Wave 1 T1과 사소 conflict 가능 — 양 트랙 wording 머지(둘 다 활성).
- v1.x deferred: 조직도 트리 편집(LTREE), 부서 합병/분리, 부서 해산 시 구성원 이관 flow. 본 트랙은 flat list CRUD까지.

---

## 2026-05-06 — 🏁 admin-user-search-update 트랙 종료 (Wave 1 — T1: 검색 + 재활성 + displayName 편집 + `ADMIN_USER_UPDATED` emit)

### 범위

Wave 1 — Quick Wins T1. `/api/admin/users` GET에 `?q=` 부분매칭(LIKE escape) 추가, PATCH에 `isActive=true`(reactivate) + `displayName` 변경 분기 추가. Frontend `/admin/users`에 검색 입력(300ms debounce) + 재활성화 버튼(비활성 row) + displayName inline 편집 UI 추가. audit emit +1 (`ADMIN_USER_UPDATED` — reactivate + displayName 공용).

### 변경

- **backend**:
  - `User.changeDisplayName(String)` — trim 후 1-100자 도메인 검증.
  - `UserRepository.findForAdminPageable(String pattern, Pageable)` — JPQL `email/displayName LOWER LIKE ESCAPE '\\'`. soft-delete 제외, 비활성 포함.
  - `AdminUserService.list(Pageable, String q)` — q lowercase + `%`/`_`/`\` 이스케이프 후 wrap. `changeDisplayName(...)` / `reactivate(...)` — 멱등(no-op + audit 미발행), self 허용(제재 아님).
  - 신규: `AdminUserUpdatedEvent(userId, actorId, beforeJson, afterJson)`.
  - `AdminAuditListener.onAdminUserUpdated(...)` — AFTER_COMMIT, swallow.
  - `AdminUserPatchRequest.displayName` 추가.
  - `AdminUserController.list(...q?)` + `patch(...)` reactivate/displayName 분기 추가, reactivate 가드 제거.
- **frontend**:
  - `api.adminListUsers(page, size, q?)` — URLSearchParams로 q 인코딩, blank/whitespace 시 omit.
  - `AdminUserPatchBody.displayName?` 필드 추가.
  - `qk.adminUsersList(page, size, q='')` — q normalize(trim).
  - `useAdminUsers(page, size, q='')` — q를 hook signature에 노출.
  - `/admin/users` page — 검색 input(useDebounce 300ms) + 재활성화 버튼 분기 + displayName 편집/저장/취소 UI.
- **docs**:
  - `docs/02 §7.4` — GET q query 파라미터 명세, PATCH displayName/reactivate 분기 명세, audit emit 표 갱신(`admin.user.updated` 추가).
  - `docs/04 §2 / §4` — admin frontend 활성 라우트 표 + 사용자 관리 4.1/4.3 검색·편집·재활성 명시.
  - `BETA-RELEASE.md §6 / §7` — emit coverage 35→36 / 미emit 9→8, deferred 항목에서 사용자 검색·재활성·displayName 항목 제거.
  - `docs/progress.md` audit-emit-gap-mapping 미emit 표 — `ADMIN_USER_UPDATED` 행을 ✓ emit 으로 갱신.

### 검증

- 백엔드 gradle test (`UserTest`/`UserRepositoryTest`/`AdminUserServiceTest`/`AdminUserControllerTest`/`AdminAuditListenerTest`) — GREEN. 신규 케이스: changeDisplayName×5, findForAdminPageable×6, list q 분기 + escape, changeDisplayName 서비스/컨트롤러 분기, reactivate 멱등/self 허용, AdminUserUpdatedEvent emit 가드.
- 프론트 vitest — GREEN. 신규 케이스: api q 인코딩×4, PATCH displayName/reactivate body, useAdminUsers q 전달, page debounce/재활성/displayName 편집 매트릭스.
- TypeScript `tsc --noEmit` + ESLint — clean.

### 회고

- audit 의미 분리(`ADMIN_USER_UPDATED` = 일반 속성 변경, `ADMIN_USER_DEACTIVATED` = 제재)는 reactivate를 어디로 보낼지가 핵심 결정. 제재 해제는 의미상 "복원"에 가까워 일반 변경(`UPDATED`)으로 흡수, deactivate enum 의미를 "제재 행위"로 좁게 유지. 대시보드/리포팅에서 제재 행위만 필터링이 가능해진다.
- 검색 q escape는 service 단계 단일 진입점에서 처리해 SQL 주입/wildcard 누설 회피. 사용자가 `%`/`_` 입력해도 의도한 부분매칭 검색만 수행.

### 다음 세션 컨텍스트

- Wave 1 — T2 (audit export endpoint, `AUDIT_EXPORTED` emit) 트랙 진입 가능. 별도 worktree 권장 (T1 PR과 독립).
- Wave 1 — T3 (시스템 정책 페이지 — cron 토글 read-only 노출). T2 머지 후 진입.

---

## 2026-05-05 — 🏁 audit-emit-gap-mapping 트랙 종료 (BETA §6 메트릭 정정 + 미emit 9 deferred 매핑)

### 범위

`AuditEventType` enum 실제 카운트와 BETA-RELEASE.md §6 메트릭 사이의 drift 식별 + 정정 + 미emit 항목 deferred 분류. docs-only.

### 발견

- enum 헤더 javadoc "총 42개"와 BETA §6 "42 enum 중 35 emit (83%)" 모두 stale. 실제 enum 카운트 = **44개** (인증 카테고리 주석 `(6)`이 password 3종 추가 후 미갱신).
- emit grep = 35건 (정확).
- 미emit = **44 − 35 = 9개** (BETA가 적시한 7이 아님).

### 미emit 9개 분류 (모두 deferred — 누락 0건)

| Enum | 분류 | 근거 |
|---|---|---|
| `FILE_VIEWED` | v1.x | ADR #9 + `FileVersionController:60` javadoc |
| `FOLDER_AUDIT_LEVEL_CHANGED` | v1.x | docs/04 §6 line 269 |
| `USER_MFA_ENABLED` | v1.x | ADR #18 |
| `ADMIN_USER_UPDATED` | ✓ emit | admin-user-search-update closure (Wave 1 — T1, 2026-05-06) |
| `ADMIN_QUOTA_CHANGED` | v1.x | BETA §7 (quota) |
| `ADMIN_LEGAL_HOLD_PLACED` | v2.x | BETA §7 (Legal Hold) |
| `ADMIN_LEGAL_HOLD_RELEASED` | v2.x | BETA §7 |
| `SYSTEM_BACKUP_COMPLETED` | v1.x | docs/04 §13 (managed Postgres 자동 백업) |
| `AUDIT_EXPORTED` | v1.x | docs/04 §7.2 line 203 |

### 변경

- `BETA-RELEASE.md §6` audit emit coverage 행: `42 enum 중 35 emit (83%)` → `44 enum 중 35 emit (~80%) — 미emit 9개는 §7 deferred 매핑`.
- `BETA-RELEASE.md §7` 6개 항목에 미emit enum 이름 cross-link 추가 + `SYSTEM_BACKUP_COMPLETED` / `AUDIT_EXPORTED` 신규 deferred 항목 2개 추가.
- `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` 헤더 javadoc `총 42개 값` → `총 44개 값`, 인증 카테고리 주석 `// 인증 (6)` → `// 인증 (8)`.

### 검증

- 코드 동작 변경 0 (주석/문서만). 컴파일 영향 없음 — 주석 변경만이므로 별도 빌드 게이트 면제.
- `BETA-RELEASE.md` 다른 위치의 "42" / "35/42" 인용 잔존 0건 (grep 확인).
- `frontend/src/types/audit.ts` mirror 카운트 = 44 (정합 확인).
- `progress.md` 과거 세션의 "32/42 / 29/42" 등 historical 메트릭은 시점 진실로 보존.

### 회고

- BETA-RELEASE.md §6 audit emit 행은 매 트랙 종료마다 분자만 갱신되며 분모(enum 카운트)는 카테고리 추가 시점에 동기 갱신되지 않아 drift 누적. 향후 enum 추가 시 헤더 주석 + 카테고리 주석 + BETA §6 동시 갱신을 PR 체크리스트에 명시 권장.
- 미emit 분류는 모두 v1.x/v2.x deferred로 깔끔히 매핑됨 — 누락(버그) 0건. 코드 emit 추가 트랙 발생 가능성은 admin frontend v1.x 도입 시점.

### 다음 세션 컨텍스트

- 잔여 후속 트랙 후보(자동 발생 시): admin frontend v1.x → `ADMIN_USER_UPDATED` / `ADMIN_QUOTA_CHANGED` emit 활성, audit export endpoint v1.x → `AUDIT_EXPORTED` emit 활성, audit_level v1.x(ADR #9) → `FILE_VIEWED` / `FOLDER_AUDIT_LEVEL_CHANGED` emit 활성.

---

## 2026-05-05 — 🏁 admin-user-mgmt 트랙 종료 (목록/role 변경/비활성 + audit emit +2)

### 범위

`/api/admin/users` GET (paginated list) + PATCH (role/active 변경) endpoint 신설. self-protection 강제 (actor==target self-demote/self-deactivate 차단). Frontend `/admin/users` 페이지 확장 — 초대 폼 보존 + 목록 테이블 + role select + 비활성 버튼 + 페이지네이션. audit emit +2 (`ADMIN_USER_DEACTIVATED`, `ADMIN_ROLE_CHANGED`).

### 변경

- **backend**:
  - `User.deactivate()` / `.reactivate()` 추가.
  - `UserRepository.findAllActivePageable(Pageable)` — `deletedAt IS NULL` + `createdAt DESC, id ASC` 정렬.
  - `AdminUserService.list/changeRole/deactivate(...)` — self-protection은 service 단에 강제, 멱등 보장.
  - `AdminUserController.@GetMapping/@PatchMapping("/{id}")` + `@PreAuthorize("hasRole('ADMIN')")`.
  - 신규: `AdminUserSummaryResponse`, `AdminUserPatchRequest`, `AdminUserDeactivatedEvent`, `AdminRoleChangedEvent`, `AdminUserNotFoundException`, `AdminSelfProtectionException`, `AdminBadPatchException`.
  - `AdminAuditListener.onAdminUserDeactivated/.onAdminRoleChanged` 추가 (AFTER_COMMIT, swallow on failure).
  - `AdminExceptionHandler` 신설 — 404/403/400 envelope.
- **frontend**:
  - `api.adminListUsers/adminUpdateUser` + `AdminUserSummary/AdminUserPage/AdminUserPatchBody` 타입.
  - `qk.adminUsers/adminUsersList` + `invalidations.afterAdminUserChanged`.
  - `useAdminUsers/useAdminUpdateUser` 훅.
  - `/admin/users` page — 초대 폼 + 목록 테이블 + 페이지네이션 분리(InviteSection/ListSection).
- **docs**:
  - `docs/02 §7.12` — GET/PATCH 표 행 정정 + concrete 스펙 블록 2개 추가 (요청/응답/Side-effects/Self-protection/Errors).
  - `BETA-RELEASE.md §6` audit emit `32/42` → `35/42`(83%), `USER_PASSWORD_CHANGED` 누락 cross-link 보정 + 신규 2종 추가.
  - `BETA-RELEASE.md §7` admin frontend 표현 — `/admin/users` 목록/role 변경/비활성 활성, 검색/재활성/displayName 편집/quota만 v1.x로 좁힘.

### 검증

- backend: `gradlew test --tests "com.ibizdrive.admin.*"` BUILD SUCCESSFUL (controller 17 + service 12 + listener 3 + supporting).
- frontend: `pnpm exec vitest run src/lib/api.adminUsers.test.ts src/hooks/useAdminUsers.test.tsx src/hooks/useAdminUpdateUser.test.tsx` 12/12 GREEN, page 12/12 GREEN.
- frontend 전체: `pnpm test` 96 files / 758 tests GREEN. typecheck, lint clean.

### 회고

- 가장 비싼 결정은 `ADMIN_ROLE_CHANGED` vs 기존 `PERMISSION_CHANGED`(file/folder grant 변경) 분리. 둘 다 `Role` 단어를 쓰지만 의미가 달라 enum도 doc도 분리 유지. 신규 service 경로(`AdminUserService.changeRole`)와 기존 dead-path(`PermissionService.changeRole` — controller 미노출)는 무관.
- self-protection은 service 단에 두고 controller는 actorId 추출/전달만. 본인 마지막 ADMIN인지 여부와 무관하게 일관 차단(검증 단순화).
- `isActive=true`(재활성)는 본 트랙 미지원이라 명시적 400으로 거부 — v1.x 반복 사용 endpoint와 의미를 분리.
- 신규 ADR 발번 0 (ADR #21 admin shell + ADR #41 auth-pages 자연 확장).

---

## 2026-05-05 — 🏁 beta-release-sync 트랙 종료 (BETA-RELEASE.md drift 정렬, docs-only)

### 범위

`master` 5f143c6 기준으로 `BETA-RELEASE.md` last-updated(`2026-05-02` → `2026-05-05`) + Source 인용(5건 신규 closure) + §1 frontend 카운트(647 → 738) + §5 4행 추가(password policy / mustChangePassword / admin invite / email async) + §6 audit emit coverage(29/42 → 32/42) + §7 admin frontend 표현 정렬. 누락된 dev/process 스테일 파일 3건 housekeeping 삭제. 코드 0 변경.

### 회고

- **commits**: 2개(bootstrap + sync) + closure.
  - `wip` dev-docs bootstrap (plan/context/tasks)
  - `docs` BETA-RELEASE.md sync + dev/process housekeeping
  - + closure commit (progress entry + dev-docs archive)
- **production 신설/수정**: 0 (docs-only).
- **docs sync**:
  - `BETA-RELEASE.md`:
    - header `Last Updated: 2026-05-05` + Source에 `auth-must-change-pw`/`auth-forgot-rate-limit`/`m-admin-entry-rewrite`/`auth-password-policy`/`email-async` 5건 인용.
    - §1 frontend `647/647` → `738/738` (auth-password-policy closure 풀세트, 93 files).
    - §5 신규 4행: 비밀번호 정책(ADR #19 본문 회복) / mustChangePassword UX(ADR #21 §2.7) / 운영자 초대 endpoint(ADR #21 closure) / 이메일 비동기(`@Async`, ADR #45).
    - §6 audit emit `29 emit (69%)` → `32 emit (76%)` 정정 + 신규 emit 3종(`USER_PASSWORD_FORGOT_REQUESTED` / `USER_PASSWORD_RESET` / `ADMIN_USER_CREATED`) cross-link.
    - §7 admin frontend 표현 정정 — admin shell + `/admin/users` 초대 폼 활성화 반영, 사용자 목록/role 변경만 v1.x.
  - `docs/progress.md`: 본 entry.
- **housekeeping**:
  - `dev/process/{a1.5-email-infra,auth-forgot-rate-limit,email-async}.md` 3개 삭제 — 모두 `status: closed` + `working_files: []`로 dev 스킬 ⓪ 규칙대로 closure 시 삭제됐어야 하나 누락.
  - `.claude/worktrees/{auth-password-policy,email-async,m-admin-entry}` 워크트리 정리 (PR 머지 후).
- **dev-docs**: `dev/active/beta-release-sync/` (3파일) → closure 후 `dev/completed/beta-release-sync/`.
- **test**: 코드 0 변경 → 로컬 회귀 검증 불필요. CI(frontend vitest + backend junit) GREEN을 PR 게이트로.

### 핵심 결정 (beta-release-sync 트랙)

1. **신규 ADR 발번 거부**: 본 트랙은 결정 0의 docs alignment. ADR #45(email-async)는 PR #53로 이미 master 진입.
2. **last-updated = 본 트랙 closure 일자(2026-05-05)** — sync 행위 자체의 날짜로 기록. 인용된 closure 일자(2026-05-03/05-04)는 Source 라인에 보존.
3. **stale dev/process housekeeping을 본 트랙에서 일괄 처리** — 별도 housekeeping 트랙 신설은 YAGNI. 향후 트랙은 closure 시 `dev/process/[task].md` 삭제 의무 재확인.

### 다음 세션 컨텍스트

- BETA-RELEASE.md §2 인프라 게이트(HTTPS / 시크릿 / managed Postgres / 백업 정책) + §8 모니터링 — 운영자 책임. 코드 측 변경 없이 staging/prod 인프라 셋업 시점에 채워짐.
- 잔여 코드 트랙 후보: `audit-emit-coverage-closure` (32/42 → 더 높은 비율, 미사용 enum emit 활성). v1.x scope.
- BETA GO/NO-GO 코드 게이트는 PASS 유지 — 인프라 sign-off 대기 상태.

---

## 2026-05-03 — 🏁 email-async 트랙 종료 (`@Async` EmailService — anti-enumeration timing leak 완화, ADR #45)

### 범위

`dev/active/email-async/` bootstrap (plan/context/tasks 3파일) → P1 `EmailAsyncConfig` 신설 (`@EnableAsync` + `emailExecutor` `ThreadPoolTaskExecutor` corePool=2/maxPool=4/queue=100/prefix `email-async-`) → P2 `EmailService.send()`에 `@Async("emailExecutor")` 부착 + `SmtpEmailService` `MailException` → ERROR 로그 흡수(throw 제거) → P3 `PasswordResetService.requestReset()` try/catch 제거 + 미사용 import/Logger 정리 + dead 테스트 1건 삭제 → P4 `EmailAsyncIntegrationTest` 신설 (caller < 50ms vs stub 200ms sleep + thread name `email-async-` 검증) → P5 docs sync (ADR #45 + 03 §2.7 갱신) + closure.

### 회고

- **commits**: 5개 + closure.
  - `80ffd18` dev-docs bootstrap
  - `64cc370` feat — P1 EmailAsyncConfig
  - `d68c2c5` feat — P2 @Async + SmtpEmailService 예외 내부화
  - `3e3f590` feat — P3 PasswordResetService try/catch 제거 + dead test 정리
  - `af71e1e` feat — P4 EmailAsyncIntegrationTest (2건 GREEN)
  - + closure commit (docs sync + progress + dev-docs archive)
- **production 신설/수정**:
  - backend 신설: `EmailAsyncConfig` (configuration, 41 lines), `EmailAsyncIntegrationTest` (Spring proxy 활성, 105 lines, 2 케이스).
  - backend 수정: `EmailService` (`@Async("emailExecutor")` 부착 + javadoc 갱신), `SmtpEmailService` (`MailException` ERROR 로그 흡수, throw 제거), `PasswordResetService` (try/catch + EmailDeliveryException import + Logger 제거, javadoc 갱신).
  - backend 테스트: `PasswordResetServiceTest` dead 케이스 1건 삭제(`requestReset_emailFailure_swallowedAndStillProceeds`).
- **docs sync**:
  - `docs/00 §5`: ADR #45 (`@Async` on interface method, executor 풀 크기, 예외 정책, 거부 옵션 2종, 한계).
  - `docs/03 §2.7`: 이메일 인프라 절을 ADR #42 + #45 공동 참조로 갱신, anti-enumeration timing leak 한계가 ADR #45로 완화됨을 명시.
- **테스트**: backend 전체 GREEN (BUILD SUCCESSFUL 2m 7s). 신규 2건 + 회귀 0.
- **보안 효과**: 가입자/미가입자 forgot caller latency 동일 — SMTP RTT 변동(±수백ms)이 더 이상 timing side channel 노출하지 않음. 통합 테스트가 caller < 50ms 임계로 회귀 차단.
- **잔여**: `EmailDeliveryException` 클래스 자체는 본 트랙에서 보존(사용처 0). 별도 cleanup 트랙에서 삭제 검토.

### 다음 세션 컨텍스트

- queue=100 포화 시 default `CallerRunsPolicy`로 caller block(latency 회귀) — BETA 도달 불가 가정. v1.x 트래픽 증가 시 `RejectedExecutionHandler` 재검토.
- 다중 인스턴스 시 thread pool은 노드별 독립(stateless send) — 영향 0.
- ADR #42 `EmailDeliveryException` deprecated 표시 + cleanup 트랙은 backlog.

---

## 2026-05-04 — 🏁 auth-password-policy 트랙 종료 (ADR #19 본문 회복, signup/reset/change 통합)

### 범위

ADR #41(auth-pages)이 self-signup MVP 진입 마찰 회피 명목으로 임시 완화한 password min=8을 ADR #19 본문(min 12 + 영문+숫자 + 공백 금지)으로 회복. 백엔드 3 endpoint(signup/reset/change) DTO + 프론트 3 페이지 일괄 정렬, 공통 validator 추출, FE/BE identical logic(핵심 원칙 11) 보장.

P1 backend 공통 validator (TDD: `PasswordPolicyValidator` + `@ValidPassword` + 22 unit tests + `AuthExceptionHandler.ruleOf` 분기로 ValidPassword violation을 rule code 노출) → P2 backend 3 DTO 적용 + integration param tests (5규칙 × 3 endpoint) + `TempPasswordGenerator` 알고리즘 강화(영문/숫자 강제 주입 + Fisher-Yates shuffle) + 200 sample 회귀 가드 → P3 frontend `lib/password.ts` mirror + 25 unit tests → P4 3 페이지(signup/reset-password/account/password) 사전검증 교체 + rule별 한국어 메시지 + 페이지 레벨 테스트 + useSignup jsdoc 갱신 → P5 docs sync(ADR #19/#41 closure 메모 + §2.7 closure 헤더 + progress entry) + dev-docs archive.

### 회고

- **production 신설/수정**:
  - backend 신설: `auth/validation/ValidPassword` (Bean Validation annotation, validatedBy = PasswordPolicyValidator), `auth/validation/PasswordPolicyValidator` (5규칙 우선순위, ASCII letter/digit + Unicode whitespace+spaceChar로 frontend `\s` NBSP 정렬).
  - backend 수정: `auth/dto/SignupRequest` / `auth/password/dto/ResetPasswordRequest` / `auth/password/dto/ChangePasswordRequest` — `@Size(min=8, max=128)` → `@ValidPassword`. `common/error/AuthExceptionHandler.ruleOf(FieldError)` — ValidPassword violation일 때 `defaultMessage`(rule code 주입)를 우선, 그 외 annotation은 기존 `code` 사용. `admin/TempPasswordGenerator` — 영문 1자 + 숫자 1자 강제 주입 + Fisher-Yates shuffle로 ADR #19 항상 통과 보장.
  - backend 신규 테스트: `auth/validation/PasswordPolicyValidatorTest` (22 케이스 — 5규칙 × 경계값 + 우선순위), `common/error/AuthExceptionHandlerTest` (4 케이스 — ruleOf 분기), `admin/TempPasswordGeneratorTest` (200 sample × 4 회귀 가드 + 길이 RepeatedTest). `AuthControllerSignupTest` / `PasswordControllerResetTest` / `PasswordControllerChangeTest` — `@ParameterizedTest @CsvSource`로 5규칙 거부 매트릭스 + `details.rule` jsonPath 검증.
  - frontend 신설: `lib/password.ts` (`PasswordRule` type + `validatePassword` + `getPasswordRuleMessage` 한국어), `lib/password.test.ts` (25 케이스 — backend 매트릭스 미러).
  - frontend 수정: `app/(auth)/signup/page.tsx` / `app/(auth)/reset-password/page.tsx` / `app/(explorer)/account/password/page.tsx` — `password.length < 8` 분기를 `validatePassword` + rule 메시지로 교체, label/minLength `8` → `12자 이상, 영문·숫자 포함`. `hooks/useSignup.ts` jsdoc — `<8자` → ADR #19 5규칙. `app/(auth)/signup/page.test.tsx` 신설 (6 케이스), `app/(auth)/reset-password/page.test.tsx` 신설 (6 케이스), `app/(explorer)/account/password/page.test.tsx` 추가 4 케이스 + 라벨 정규식 정정.
- **docs sync**:
  - `docs/00 §5` ADR #19: closure 메모 추가 (backend validator + frontend mirror + 3 endpoint/페이지 적용 + TempPasswordGenerator 회귀 가드 + 핵심 원칙 11 정렬).
  - `docs/00 §5` ADR #41: password Validation을 `@NotBlank @ValidPassword`로 정정 + 인라인 closure 메모.
  - `docs/03 §2.7`: closure 블록 헤더 추가 (단일 진실의 출처 명시 + 구현 매핑).
  - `docs/03 §2.8 self-signup`: password Validation을 `@NotBlank @ValidPassword`로 정정 + 5규칙 cross-link.
- **dev-docs**: `dev/active/auth-password-policy/` (3파일) → closure 후 `dev/completed/auth-password-policy/`로 이동.
- **test**:
  - backend `./gradlew test` BUILD SUCCESSFUL — 전체 sweep GREEN, 회귀 0.
  - frontend `npx tsc --noEmit && npx next lint && npx vitest run` — 738 tests pass, 93 files, lint/typecheck clean.

### 핵심 결정 (auth-password-policy 트랙)

1. **별도 ADR 신설 거부 — ADR #19 본문 closure + ADR #41 정정**: 본 트랙은 ADR #19로의 회귀이므로 새로운 결정이 아님. ADR #41이 임시 완화한 정책을 본문으로 되돌리는 reconciliation. KISS 원칙으로 신규 ADR 발번 회피, 양 ADR row에 closure 메모만 추가.
2. **단일 violation 우선순위 보고**: 5규칙을 우선순위(whitespace > max_length > min_length > missing_alpha > missing_digit)로 정렬해 첫 위반만 노출. 다중 violation 동시 표시는 UX 노이즈로 판단 — backend `PasswordPolicyValidator`는 일찍 return + frontend `validatePassword`도 동일 short-circuit. backend는 `disableDefaultConstraintViolation()` + `buildConstraintViolationWithTemplate(rule).addConstraintViolation()`로 rule code를 `defaultMessage`에 주입, `AuthExceptionHandler.ruleOf`가 `ValidPassword` annotation 분기에서 이 message를 rule로 surfacing(다른 annotation 회귀 보호 위해 `code.equals("ValidPassword")` 가드).
3. **FE/BE 동일 ASCII letter/digit 채택**: backend `Character.isLetter`(Unicode 한글/한자 포함)와 frontend `[A-Za-z]`(ASCII) drift 발견 → ADR #19 "영문자" 정의를 ASCII로 통일하고 backend를 frontend에 정렬(핵심 원칙 11). Whitespace는 반대로 frontend `\s`가 NBSP 등 Unicode를 포함하므로 backend에 `Character.isSpaceChar` 추가하여 정렬.
4. **`TempPasswordGenerator` 알고리즘 강화**: 기존 random alphabet 기반 16자는 통계적으로 ~10% 확률로 missing_alpha/missing_digit 위반 가능. 영문 1자 + 숫자 1자 강제 주입 후 Fisher-Yates shuffle로 위치 노출 회피 + 항상 통과 보장. 200 sample 단위 테스트로 회귀 가드.

### 다음 세션 컨텍스트

- ADR #19/#41 reconciliation 종료. 잔여 password 정책 항목(zxcvbn/HIBP 사전 공격 방지)은 ADR #19 본문대로 v1.x reserve.
- 다음 트랙 후보: A1.5 잔여(EmailService prod SMTP 통합) 또는 마일스톤 1 frontend 핵심 (folderId 라우팅 + FolderTree).

---

## 2026-05-03 — 🏁 m-admin-entry-rewrite 트랙 종료 (admin shell + invite endpoint, ADR #21 closure)

### 범위

폐기 PR #45(`m-admin-entry`, admin frontend skeleton + AdminGuard) + PR #51(`admin-invite-email`, `POST /api/admin/users`)을 현 master 기준으로 통합 재작성. ADR #21 잔여 closure(self-signup + first-user-ADMIN + 강제 변경 UX는 ADR #41/auth-must-change-pw로 이미 활성, 운영자 초대 endpoint + admin shell만 잔여).

P0 부트스트랩 → P1 AdminGuard FE TDD (3 케이스) → P2 admin layout + AdminSideNav (deferred 8 항목 disabled 배지) → P3 `/admin` landing (가용 카드 2 + deferred 섹션) → P4 (explorer) UserMenu admin 링크 (ADMIN/MEMBER 분기) → P5 AdminUserService BE TDD (TempPasswordGenerator 16자 + AdminUserCreatedEvent + AdminAuditListener AFTER_COMMIT/REQUIRES_NEW + EmailService.send 호출) → P6 AdminUserController BE TDD (200/400/401/403/409 매트릭스 + 임시 PW 응답 부재 회귀 가드 jsonPath) → P7 frontend api/hook (`api.adminInviteUser` + `useAdminInviteUser`) → P8 `/admin/users` invite form (email/displayName/role select + 성공 안내 + 폼 리셋 + 409 인라인 에러 + PW 단어 부재 회귀 가드) → P9 closure (docs sync 7 파일 + progress + dev-docs archive + housekeeping + PR).

### 회고

- **commits**: 9개 + closure.
  - `7535499 wip(m-admin-entry-rewrite): dev-docs bootstrap (plan/context/tasks)`
  - `a747564 feat(m-admin-entry-rewrite): P1 AdminGuard (TDD)`
  - `075d8fc feat(m-admin-entry-rewrite): P2 admin layout + AdminSideNav`
  - `2e411af feat(m-admin-entry-rewrite): P3 /admin landing`
  - `a1f65c0 feat(m-admin-entry-rewrite): P4 UserMenu admin 링크 (TDD)`
  - `0e7b170 feat(m-admin-entry-rewrite): P5 AdminUserService + temp PW (TDD)`
  - `540e98f feat(m-admin-entry-rewrite): P6 AdminUserController + 200/400/401/403/409 (TDD)`
  - `ad0a37b feat(m-admin-entry-rewrite): P7 api/hook adminInviteUser (TDD)`
  - `72eb1dc feat(m-admin-entry-rewrite): P8 /admin/users invite form (TDD)`
  - + closure commit (docs sync 7 + progress + dev-docs archive)
- **production 신설/수정**:
  - backend 신설: `admin/AdminInviteUserRequest` (record + Bean Validation), `admin/AdminInviteUserResponse` (record — **tempPassword 필드 부재**, 회귀 가드 javadoc), `admin/AdminUserController` (`@PreAuthorize("hasRole('ADMIN')")` + `@AuthenticationPrincipal IbizDriveUserDetails`), `admin/AdminUserService` (`@Transactional` invite — email lower/trim + duplicate → `DuplicateEmailException` + BCrypt hash + User save + event publish + EmailService.send), `admin/TempPasswordGenerator` (16자 SecureRandom alnum+소량특수), `admin/AdminUserCreatedEvent` (record), `admin/AdminAuditListener` (`@TransactionalEventListener AFTER_COMMIT` + AuditService.record).
  - backend 신규 테스트: `AdminUserServiceTest` (6+ 케이스), `AdminUserControllerTest` (7 케이스 — 200 with 회귀 jsonPath tempPassword.doesNotExist + 400 invalid email + 400 blank displayName + 400 null role + 401 unauth + 403 MEMBER + 409 duplicate).
  - frontend 신설: `components/auth/AdminGuard` + 테스트 3 / `components/admin/AdminSideNav` / `app/admin/layout.tsx` (UPDATE — AuthGuard+AdminGuard 중첩 + 사이드 nav) / `app/admin/page.tsx` (landing) / `components/auth/UserMenu.tsx` (UPDATE — admin 링크 + 테스트) / `lib/api.ts` (UPDATE — adminInviteUser 메서드 + AdminInviteUserParams/AdminInvitedUser 타입) / `lib/api.adminInviteUser.test.ts` (4 케이스) / `hooks/useAdminInviteUser.ts` + `.test.tsx` (2 케이스) / `app/admin/users/page.tsx` + `.test.tsx` (4 케이스).
- **docs sync**:
  - `docs/00 §5`: ADR #21 본문 closure 메모 추가 (별도 ADR 신설 X — admin invite 활성화 + admin shell + audit emit + 임시 PW 비노출 4채널 + Prod SMTP는 v1.x).
  - `docs/02 §7.12`: `POST /api/admin/users` 행 + request/response 블록(검증 + side-effects + 에러 envelope + audit emission cross-link).
  - `docs/03 §2.7`: 운영자 초대 cross-link (활성화 완료 + 첫 로그인 force UX 진입 명시).
  - `docs/03 §2.8`: 상태 헤더 flip ("v1.x reserve" → "활성화 완료") + 본문 endpoint 추가 + 임시 PW 비노출 4채널(응답/로그/audit/예외) 정책 명시 + 첫 로그인 흐름 + 에러 envelope + 프론트 진입점 명시.
  - `docs/03 §2.10`: audit 표 +1 (`admin.user.created`).
  - `docs/04 §1`: §1.1 "가드 분리 — UX 게이트 vs 보안 게이트" 절 신설 (AdminGuard 책임 + `@PreAuthorize` 진실 + AuthGuard 중첩 순서 + SecurityConfig permitAll 회귀 가드).
  - `docs/04 §2`: 라우트 트리에 활성/v1.x deferred 명시 (활성: `/admin`, `/admin/audit/logs`, `/admin/users`).
- **dev-docs**: `dev/active/m-admin-entry-rewrite/` (3파일) → closure 후 `dev/completed/`로 이동. `dev/active/auth-must-change-pw/` 잔존도 같은 시점에 `dev/completed/`로 housekeeping 이동(PR #48 closure 시 누락분).
- **test**:
  - backend `./gradlew test` BUILD SUCCESSFUL — 신규 14+ 케이스(Service 6 + Controller 7) GREEN, 회귀 0.
  - frontend `pnpm typecheck && pnpm lint && pnpm vitest run` 풀세트 GREEN — 신규 케이스(P1 AdminGuard 3 + P4 UserMenu 분기 + P7 6 + P8 4) 합산.

### 핵심 결정 (m-admin-entry-rewrite 트랙)

1. **별도 ADR 신설 거부 — ADR #21 본문에 closure 기록**: 본 트랙은 ADR #21의 잔여 closure이므로 새로운 결정이 아님. ADR #41이 self-signup으로 supersede하면서 운영자 초대를 v1.x reserve로 보류했던 것을 m-admin-entry-rewrite로 활성화. 결정의 의미적 출처는 ADR #21 그대로 유지하고 본문에 closure 메모만 추가(별도 #46+ 발번 회피, KISS).
2. **임시 PW 비노출 4채널 회귀 가드**: 응답 DTO record에 `tempPassword` 필드 자체 부재 (컴파일 강제) + 컨트롤러 테스트 `jsonPath("$.tempPassword").doesNotExist()` + audit_log payload null + 서비스 어디에도 PW 평문 INFO/DEBUG 로그 없음. AdminInviteUserResponse javadoc에 정책을 명시해 향후 변경 시 가드 활성화.
3. **AdminAuditListener `AFTER_COMMIT` + `REQUIRES_NEW`**: user save가 rollback되었는데 audit만 남는 상황 회피. AuthAuditListener와 동일 패턴(ADR #24 §2 cross-cutting layer 분리). ApplicationEventPublisher.publish는 트랜잭션 동기화로 commit 후에만 listener 호출.
4. **AdminGuard = UX 가드만, 보안은 백엔드 `@PreAuthorize`**: 프론트 가드 강도와 보안 강도를 혼동하지 않도록 docs/04 §1.1에 명시 + 회귀 가드(SecurityConfig permitAll 목록에 `/api/admin/**` 포함 금지). 두 가드 중첩 순서는 `<AuthGuard><AdminGuard>` (인증 → 역할).
5. **사용자 목록 미구현, 초대 폼만**: ADR #21 closure 범위는 "초대 endpoint + 진입 shell"까지. 사용자 목록/검색/role 변경은 v1.x admin 트랙. mutation 후 cache invalidate 없음(invalidate 대상 query 자체 부재).
6. **Prod SMTP 도입은 v1.x**: 본 트랙은 ConsoleEmailService(`@Profile("!prod")`) stdout으로 dev/test 검증만 활성. SmtpEmailService(`@Profile("prod")`)는 a1.5 트랙에서 인터페이스만 도입된 상태 — 이메일 템플릿/암호화 채널/비동기 큐는 v1.x 인프라 트랙에서 별도 결정.
7. **audit emit coverage 31 → 32**: `ADMIN_USER_CREATED("admin.user.created")` enum이 a1.5 closure 시점에 정의되어 있었으나 사용처 0이었음. 본 트랙으로 emit 활성. 42 enum 중 32 emit (76%).

### 다음 세션 컨텍스트

- **사용자 목록/검색/role 변경** — `/admin/users` 페이지에 list view 추가 (v1.x admin 트랙).
- **이메일 비동기 큐** — `EmailService.send`를 `@Async` + `TaskExecutor`로 fire-and-forget화 (ADR #45 적용 인프라 도입 시점).
- **Prod SMTP 활성화** — SmtpEmailService 본문 구현 + 이메일 템플릿(HTML/i18n) + 비동기 큐(v1.x 인프라 트랙).
- **role 변경 시 useMe 즉시 invalidate** — 백엔드에서 role이 바뀐 직후 frontend stale 회피. v1.x admin user mgmt에서 invalidate 추가.

---

## 2026-05-03 — 🏁 auth-forgot-rate-limit 트랙 종료 (forgot 분당 1회 rate-limit, ADR #44)

### 범위

`dev/active/auth-forgot-rate-limit/` bootstrap (plan/context/tasks 3파일) → P1 RED (limiter 단위 테스트 8건 + controller MockMvc 6건 컴파일 RED) → P2 GREEN (`ForgotPasswordRateLimiter` Component, `RateLimitExceededException`, `ErrorResponse.rateLimitExceeded` 팩토리, `AuthExceptionHandler` 429 + `Retry-After` 매핑, `PasswordController.forgot` IP 추출 + lower email + tryAcquire 게이트 + WARN 로그 with email mask) → P3 sibling 테스트 회귀 봉합 (`PasswordControllerForgotTest` 기본 통과 stub 추가, Reset/Change 테스트는 `@MockBean ForgotPasswordRateLimiter`만 추가) → P4 docs sync (ADR #44 + 02 §7.4 + 03 §2.7 + BETA §5) → P5 closure (progress + dev/active→completed 이동 + PR).

### 회고

- **commits**: 3개 + closure.
  - `2a06246` dev-docs bootstrap
  - `a17f951` feat — limiter + 429 매핑 + controller wire (10 files / 492 insertions / TDD GREEN)
  - `bb4e3b1` docs sync — ADR #44 + endpoint contract
  - + closure commit (progress + dev-docs archive)
- **production 신설/수정**:
  - backend 신설: `ForgotPasswordRateLimiter` (Clock 주입 가능, 두 키 OR-block, lazy 만료) + `RateLimitExceededException` + 단위 테스트 8건 + 컨트롤러 MockMvc 테스트 6건.
  - backend 수정: `ErrorResponse` (rateLimitExceeded 팩토리 — `RATE_LIMIT_EXCEEDED` 코드 + `retryAfterSec` 필드, 신규 에러 코드 0), `AuthExceptionHandler` (429 + `Retry-After` 헤더 매핑), `PasswordController` (limiter 의존 주입 + `HttpServletRequest` 인자 + IP 추출 + email lower + 마스킹 WARN), 기존 `PasswordControllerForgotTest`/`ResetTest`/`ChangeTest` (`@MockBean` 추가).
- **docs sync**:
  - `docs/00 §5`: ADR #44 (in-memory single-instance, OR-block, X-Forwarded-For 첫값, anti-enumeration 정합, audit 미발행, login surface 비범위, 한계 3종).
  - `docs/02 §7.4`: forgot endpoint rate-limit 절 + 429 + `Retry-After` (기존 `RATE_LIMIT_EXCEEDED` §8 재사용 — 신규 에러 코드 0).
  - `docs/03 §2.7`: forgot row 정정(rate-limit 명시) + rate-limit 정책 1줄 (reset/change 미적용 사유).
  - `BETA-RELEASE.md §5`: forgot rate-limit 행 추가 (✓ ADR #44).
- **테스트**: 815 / 0 fail / 0 err / 214 skip. 신규 14건 GREEN.
- **a1.5 closure 잔여 항목**: `PasswordResetService.java:45` deferred 코멘트가 가리키던 forgot rate-limit 트랙 close.

### 다음 세션 컨텍스트

- single-instance 한계는 ADR #44 명시. 다중 인스턴스 도입 시점에 인터페이스 추출 + Redis 백엔드 트랙 (v1.x).
- `X-Forwarded-For` spoof — trusted proxy whitelist 트랙(별도, v1.x 인프라 셋업 시점).
- reset/change rate-limit는 토큰/세션 가드로 보호되므로 v1.x 별도 결정 — 현 트랙 범위 외.

---

## 2026-05-03 — 🏁 auth-must-change-pw 트랙 종료 (ADR #21 mustChangePassword UX 강제 closure)

### 범위

`dev/active/auth-must-change-pw/` bootstrap → P1 backend TDD (PasswordResetService.change()/reset()이 PW hash 갱신 직후 `User.clearMustChangePassword()` 호출 — 단일 트랜잭션 내 영속화. 이 클리어 없으면 프론트 enforce가 무한 redirect 루프) → P2 frontend LoginPage TDD (`postLoginTarget` 헬퍼로 redirect 단일화 — `me.user.mustChangePassword=true` 시 `next` 무시하고 `/account/password?force=1`) → P3 AuthGuard TDD (`usePathname` 도입 + 로그인 사용자라도 `mustChangePassword=true && pathname!=='/account/password'`이면 bounce, `/account/password` 자체에서는 통과로 무한 루프 회피) → P4 `/account/password` force UI TDD (`?force=1` 시 amber 배너 `role=alert` + "돌아가기" hide + 성공 시 `router.replace('/files')`, `usePasswordChange.onSuccess`가 `qk.authMe()` invalidate) → P5 closure (docs/03 §2.7 "강제 비밀번호 변경 UX" 절 신설 + §2.8 운영자 초대 reservation note 보강 + 본 entry + dev-docs archive + stacked PR).

### 회고

- **commits**: 3개 + closure.
  - `1b9d360 wip(auth-must-change-pw): dev-docs bootstrap (plan/context/tasks)`
  - `7e8e4cc feat(auth-must-change-pw): P1 backend change/reset clear mustChangePassword (TDD)` — PasswordResetServiceTest +2 (change_clearsMustChangePasswordFlag, reset_clearsMustChangePasswordFlag) + sampleUserWithMustChange 헬퍼
  - `cab34c1 feat(auth-must-change-pw): P2+P3+P4 frontend mustChangePassword UX (TDD)` — LoginPage/AuthGuard/account/password page + 3 신규 테스트 파일(11 케이스)
  - + closure commit (docs/03/progress/archive)
- **production 신설/수정**:
  - backend: `User.clearMustChangePassword()` 신설 + `PasswordResetService.change()/reset()` 양쪽 호출 추가.
  - frontend: `LoginPage` (`postLoginTarget` 헬퍼 + 두 redirect 분기 분기), `AuthGuard` (`usePathname` + mustChangePassword guard + `data` undefined 가드), `/account/password/page` (Suspense 경계 + `useSearchParams('force')` + 배너 + 돌아가기 조건부 렌더 + force 모드 redirect), `usePasswordChange` (onSuccess invalidate `qk.authMe`).
  - tests 신설: `PasswordResetServiceTest +2` / `LoginPage page.test.tsx (3)` / `AuthGuard.test.tsx (3)` / `account/password page.test.tsx (5)` = 합 13.
- **docs sync**:
  - `docs/03 §2.7` 끝에 "강제 비밀번호 변경 UX (auth-must-change-pw 트랙, 2026-05-03)" 절 신설 — 백엔드 클리어 + 프론트 LoginPage·AuthGuard·force UI·invalidation flow 명시.
  - `docs/03 §2.8` 운영자 초대 reservation note 보강 — 강제 변경 UX는 §2.7로 분리되어 활성화 완료, admin invite endpoint만 도입하면 즉시 동작.
- **dev-docs**: `dev/active/auth-must-change-pw/` (3파일) → closure 후 `dev/completed/`로 이동 + `dev/process/auth-must-change-pw.md` 정리.
- **test**:
  - backend `./gradlew test` BUILD SUCCESSFUL — PasswordResetServiceTest 신규 2 + 기존 통과, 회귀 0.
  - frontend `pnpm typecheck && pnpm lint && pnpm vitest run` — 685/685 통과 (681 → +13 케이스 일부 기존 테스트 통합 후 net +4 테스트 파일, 신규 11 → total 685).

### 핵심 결정 (auth-must-change-pw 트랙)

1. **백엔드 클리어가 선결조건**: 프론트 enforce(redirect)만 추가하면 `change()`/`reset()` 후에도 플래그가 true로 남아 무한 redirect. `clearMustChangePassword()`를 mutator로 도입(boolean setter 노출 회피, idempotent — 자발적 변경 false→false 무해).
2. **`reset()`도 클리어**: ADR #21 §2.8 "사용자가 reset link로 PW 설정 → mustChangePassword=false 처리"와 일치. 자발적 분실 reset은 원래 false였을 가능성 높지만 idempotent라 무해.
3. **`postLoginTarget` 헬퍼**: useEffect(이미 로그인) + onSubmit(신규 로그인) 두 경로의 redirect 결정 로직을 단일 함수로 통합 — 분기 누락 위험 제거.
4. **`usePathname` 도입 vs 기존 `window.location`**: 새 가드 분기에서만 `usePathname()`을 쓰고, 기존 401 분기의 `next` 구성은 `window.location.search`까지 포함하므로 그대로 유지. 변경 최소화 원칙(KISS).
5. **`/account/password`가 force redirect의 종착점**: AuthGuard에서 `pathname === '/account/password'` 예외 처리로 무한 루프 회피. force=1 query는 정보 표시(배너)와 변경 성공 후 `/files` redirect 트리거 용도. 진실의 출처는 `me.user.mustChangePassword`(useMe staleTime 60s + invalidate).
6. **`usePasswordChange.onSuccess`에서 useMe invalidate**: force 모드 → `/files` 전환 시 AuthGuard가 stale 플래그(true)로 bounce하지 않도록. `await qc.invalidateQueries({ queryKey: qk.authMe() })`로 refetch 완료 후 `router.replace('/files')` 실행.
7. **audit 변동 없음**: `USER_PASSWORD_CHANGED`/`USER_PASSWORD_RESET`이 이미 emit. 플래그 변화에 별도 이벤트 추가 안 함 (audit emit coverage 31/42 유지).

### 다음 세션 컨텍스트

- **password 정책 강화** — min=8 → min=12 + zxcvbn/HIBP, /signup·/reset·/change 양쪽 적용 (ADR #19 본문 정합 회복).
- **이메일 송신 비동기화** — `@Async` + `TaskExecutor`로 forgot 응답 latency 균일화.
- **이메일 초대 endpoint** (`POST /api/admin/users` invite-by-email) — ADR #21 잔여 admin 트랙. 강제 UX는 본 트랙으로 닫혔으므로 endpoint만 추가하면 end-to-end flow 완성.
- **rate limit on /forgot** — 동일 email/IP 분당 1회 등 (브루트포스 enumeration 추가 방어).

---

## 2026-05-03 — 🏁 m8.1-permission-list-frontend 트랙 closure 마무리 (PR #46 머지 후 docs/archive 정합)

### 범위

PR #46 (`fdb57c7` `feat(m8.1-permission-list-frontend): wire BE permission list into PermissionsTab`)는 2026-05-02에 이미 master 머지되었으나, dev-docs 측 closure(progress entry + active→completed archive + tasks G2 표시)가 누락된 채 `dev/active/m8-permission-list-frontend/`에 남아 있었음. 본 세션은 production 코드 변경 0의 closure-only 정합 작업.

### 회고

- **production 신설/수정**: 0 (이미 PR #46로 머지됨).
- **docs sync**: 본 entry 1건.
- **dev-docs**: `dev/active/m8-permission-list-frontend/` → `dev/completed/`. `tasks.md` G2 (PR 생성) 체크 표시.
- **test**: 변경 없음. PR #46 시점 기준 frontend 670 tests / 82 files (M8.1에서 +23) 그린 유지.

### 핵심 결정 (m8.1-permission-list-frontend closure)

1. **별도 회고 작성 X**: PR #46 머지 시점 closure가 적시 수행되지 못한 행정 누락. M8.1 본 작업의 회고/결정사항은 `dev/completed/m8-permission-list-frontend/` 의 `plan.md`/`context.md`/`tasks.md` 본문이 진실의 출처(verbatim 보존).
2. **PR/머지 사실만 기록**: 본 entry는 "어디로 갔는지" 정합용 minimum entry. 본문 회고는 dev-docs 참조.

### 다음 세션 컨텍스트

- 권한 목록 후속(검색/필터/페이지네이션, grant 행 액션 — revoke/edit/expiry 변경)은 별도 트랙. 현재는 read-only 목록만.
- folder 권한 read-only list — 본 트랙은 PermissionsTab(file) 우선이고 folder 영역은 보류였음. 후속 트랙으로 이관 가능.

---

## 2026-05-02 — 🏁 a1.5-email-infra 트랙 종료 (Spring Mail + password reset/change + 3 endpoints + 3 pages, ADR #42·#43)

### 범위

`dev/active/a1.5-email-infra/` bootstrap → P1 EmailService 추상화 (`EmailService` interface + `ConsoleEmailService @Profile("!prod")` + `SmtpEmailService @Profile("prod")` + spring-boot-starter-mail) → P2 V8 `password_reset_tokens` migration (token_hash CHAR(64) UNIQUE + user_id FK + expires_at + used_at + created_at, idx_password_reset_tokens_user_id) + JPA entity/repo → P3 POST `/api/auth/password/forgot` TDD (anti-enumeration 200 always + audit user.password.forgot_requested) → P4 POST `/api/auth/password/reset` TDD (token verify + bcrypt update + 모든 세션 invalidate + audit user.password.reset) → P5 POST `/api/auth/password/change` TDD (current pw verify + 다른 세션만 invalidate + audit user.password.changed) → P6 frontend (api 3 메서드 + 3 hooks + `(auth)/forgot-password` + `(auth)/reset-password?token=` + `(explorer)/account/password` + login 링크 + UserMenu 링크) → P7 closure (ADR #42·#43 + docs/03 §2.7 password reset 절 + docs/02 §7.4 endpoint 표·request/response 블록 + audit 이벤트 동기화 + 본 entry + archive + stacked PR).

### 회고

- **commits**: 6개 + closure.
  - `f...` P1 EmailService abstraction (Console/Smtp + Profile dispatch)
  - `673fbdc` P2 V8 password_reset_tokens + entity/repo
  - `6dd4488` P3 POST /api/auth/password/forgot (TDD)
  - `95e8a3c` P4 POST /api/auth/password/reset (TDD)
  - `8ade712` P5 POST /api/auth/password/change (TDD)
  - `d82b271` P6 frontend pages /forgot|reset|account/password
  - + closure commit (ADR/docs/03/docs/02/progress/archive)
- **production 신설/수정**:
  - backend 신설: `EmailService` interface + `ConsoleEmailService` + `SmtpEmailService` + V8 migration + `PasswordResetToken` entity/repo + `PasswordResetService` (forgot/reset/change) + `PasswordController` + 3 DTO + 신규 `InvalidTokenException` + tests 4 (controller 3 + service).
  - backend 수정: `SecurityConfig` (`/api/auth/password/forgot|reset` permitAll + CSRF ignore), `AuditEventType` (USER_PASSWORD_FORGOT_REQUESTED + USER_PASSWORD_RESET 추가, USER_PASSWORD_CHANGED 활성), `AuthExceptionHandler` (INVALID_TOKEN 매핑).
  - frontend 신설: 3 hooks (`usePasswordForgot`/`usePasswordReset`/`usePasswordChange`) + 3 pages (`(auth)/forgot-password`/`(auth)/reset-password`/`(explorer)/account/password`).
  - frontend 수정: `api.ts` (3 메서드 — forgot/reset CSRF skip, change CSRF 사용), `(auth)/login/page` (잊으셨나요 링크), `UserMenu` (비밀번호 변경 링크).
- **docs sync**:
  - `docs/00 §5`: ADR #42 (EmailService Profile 분기), ADR #43 (token SHA-256 hash + 30분 TTL + 1회 사용 + anti-enumeration).
  - `docs/03 §2.7` 직후 password reset/change 절 신설(엔드포인트·토큰·세션 invalidation 정책·이메일 인프라).
  - `docs/03 §2.10` audit 표 +2 (`user.password.forgot_requested`, `user.password.reset`).
  - `docs/03 §4.1` AuditEventType TS union +2 (ADR #43 활성).
  - `docs/02 §7.4` endpoint 표 +3 + request/response 블록 +3.
- **dev-docs**: `dev/active/a1.5-email-infra/` → closure 후 `dev/completed/`.
- **test**:
  - backend `./gradlew test` BUILD SUCCESSFUL — 신규 PasswordControllerForgotTest(3) + PasswordControllerResetTest(4) + PasswordControllerChangeTest(5) + PasswordResetServiceTest(8) = 20개, 회귀 0.
  - frontend `pnpm typecheck && pnpm lint && pnpm build` clean — 3 신규 페이지 정적 prerender.

### 핵심 결정 (a1.5)

1. **ADR #42 — EmailService Profile 분기**: dev/test=`ConsoleEmailService`(stdout 로그), prod=`SmtpEmailService`(JavaMailSender). 인터페이스 단일 진입점 `sendPasswordReset(email, rawToken)`. SMTP 설정은 `application-prod.yml` (host/port/username/password 환경변수).
2. **ADR #43 — Password reset token 정책**: 평문 토큰은 응답·DB 저장 X. SHA-256 hex hash만 저장. TTL 30분. 1회 사용(used_at 기록). 만료/사용/미존재 모두 동일 INVALID_TOKEN 응답(side-channel 차단).
3. **Anti-enumeration**: `/forgot` 응답·메시지 동일, audit에는 `found` 플래그로 분리 기록. latency는 best-effort(이메일 송신 비동기화는 v1.x).
4. **세션 invalidation 비대칭**: `/reset` = 모든 세션 종료(분실·탈취 가정), `/change` = 현재 세션만 보존(다른 디바이스만 종료). `FindByIndexNameSessionRepository.findByPrincipalName` 사용.
5. **CSRF asymmetry**: `/forgot`·`/reset`은 SecurityConfig `ignoringRequestMatchers` (anonymous), `/change`는 인증 + double-submit 유지.
6. **mustChangePassword 활성 시점**: ADR #21 잔여 — 첫 로그인 강제 변경 UX는 v1.x. 현 트랙은 자발적 `/account/password` + 잊었을 때 `/forgot-password` 두 경로만 활성.
7. **audit emit coverage 29 → 31**: USER_PASSWORD_FORGOT_REQUESTED + USER_PASSWORD_RESET 활성 (USER_PASSWORD_CHANGED는 ADR #41 트랙에서 enum만 추가, 본 트랙에서 emit).

### 다음 세션 컨텍스트

- **mustChangePassword UX 강제** — 로그인 직후 `/account/password` redirect (ADR #21 잔여, v1.x).
- **password 정책 강화** — min=8 → min=12 + zxcvbn/HIBP, /reset·/change 양쪽 적용.
- **이메일 송신 비동기화** — `@Async` + `TaskExecutor`로 forgot 응답 latency 균일화(현재는 동기 송신 — dev console은 무비용, prod SMTP는 best-effort).
- **이메일 초대 endpoint** (`POST /api/admin/users` invite-by-email) — ADR #21 잔여, EmailService 재사용.
- **rate limit on /forgot** — 동일 email/IP 분당 1회 등(브루트포스 enumeration 추가 방어).

---

## 2026-05-02 — 🏁 auth-pages 트랙 종료 (셀프 가입 + first-user-ADMIN + /login·/signup + 401 가드, ADR #41)

### 범위

`dev/active/auth-pages/` bootstrap (plan/context/tasks 3파일) → P1 backend signup TDD (SignupService 6 RED→GREEN + AuthControllerSignupTest 6 + AuthService.establishSession extract + AuditEventType.USER_REGISTERED + UserRegisteredEvent + AuthAuditListener.onRegistered + DuplicateEmailException + AuthExceptionHandler) → P2 SecurityConfig (`/api/auth/signup` permitAll + CSRF ignore) → P3 frontend api/hooks (api.signup/login/logout/me + ensureCsrfToken + buildApiError flat envelope + qk.authMe + useMe/useLogin/useSignup/useLogout + types/auth.ts) → P4 pages (`/login` Suspense wrap + `/signup` + `(auth)` 미니멀 layout + `(explorer)` AuthGuard + UserMenu) → P5 closure(ADR #21 supersede + ADR #41 + docs/03 §2.8/§4.1 + BETA-RELEASE §1·§5·§6 + 본 entry + archive + PR).

### 회고

- **commits**: 3개 + closure.
  - `70662bb feat(auth-pages): P1+P2 backend signup + SecurityConfig` — TDD GREEN, 6+6 신규 + 기존 13개 @MockBean SignupService 추가
  - `8ca3540 feat(auth-pages): P3 frontend api/hooks (signup/login/logout/me)` — typecheck/lint/test 통과
  - `334cf8d feat(auth-pages): P4 /login·/signup pages + (explorer) 401 guard` — typecheck/lint/test/build 통과
  - + closure commit (ADR/docs/BETA-RELEASE/progress/archive)
- **production 파일 수정/신설**: backend 신설 5 (SignupRequest/SignupService/UserRegisteredEvent/DuplicateEmailException + tests 2) + 수정 6 (AuthController/AuthService/AuditEventType/AuthAuditListener/AuthExceptionHandler/ErrorResponse/SecurityConfig + 기존 2 tests `@MockBean`), frontend 신설 7 (types/auth + 4 hooks + 2 components) + 신설 3 pages (login/signup/auth-layout) + 수정 3 (api/queryKeys/(explorer)layout/audit-types).
- **docs sync**: `docs/00 §5` ADR #41 추가 + ADR #21 Status: Superseded, `docs/03 §2.8` 셀프 가입 정책 재작성 + `§4.1` USER_REGISTERED enum 추가 + `§2.10` user.registered 이벤트 추가, `BETA-RELEASE` Source/§1/§5/§6 갱신.
- **dev-docs**: `dev/active/auth-pages/` (3파일) — closure 후 `dev/completed/`로 이동.
- **test**:
  - backend `./gradlew test` BUILD SUCCESSFUL (signup TDD +12 신규, 회귀 0).
  - frontend `pnpm test --run` **647/647** (M-RP 트랙 baseline 유지 — auth pages/hooks는 thin wrappers + Suspense 의존성으로 typecheck/lint/build 검증으로 대체).
  - frontend `pnpm typecheck && pnpm lint && pnpm build` clean (login/signup 정적 prerender 3.3kB 각).

### 핵심 결정 (auth-pages 트랙)

1. **ADR #41 — 셀프 가입 + first-user-ADMIN supersede ADR #21**: BETA 진입 차단(첫 가입자 부재) 해소. `userRepository.count() == 0`이면 ADMIN 부여, 그 외 MEMBER. race는 MVP single-instance + tx 직렬화로 차단(엄밀 보장은 advisory lock — v1.x).
2. **CSRF asymmetry**: `/api/auth/signup`은 `permitAll()` + `csrf().ignoringRequestMatchers` (첫 호출 token preflight 마찰 회피). login/logout은 ADR #12 그대로 double-submit 유지.
3. **AuthService.establishSession extract**: login 기존 세션 발급 로직을 public 헬퍼로 추출 → signup이 동일 helper 호출. `changeSessionId()` 호출 동일, AuthenticationSuccessEvent는 caller 책임(login만 발행, signup은 별도 USER_REGISTERED).
4. **password min=8 정정**: ADR #19(min 12)을 가입 진입 마찰 최소화로 8로 정정. v1.x 정책 강화 트랙으로 분리.
5. **useMe 401→null 매핑**: AuthGuard와 비로그인 페이지가 동일 hook으로 분기. retry false + staleTime 60s. AuthGuard는 `useSearchParams` 의존 제거(window.location in useEffect)로 prerender Suspense 마찰 회피.
6. **useLogout intent-driven**: onSettled에서 `qc.clear()` — 401/네트워크 실패도 사용자 의도가 로그아웃이므로 캐시는 비운다.
7. **audit emit coverage 28 → 29**: USER_REGISTERED 활성화. 42 enum 중 29 emit (69%).

### 다음 세션 컨텍스트

- **운영자 user 초대 endpoint** (`POST /api/admin/users`, ADR #21 잔여 부분) — v1.x reserve. 사내 도메인 배포 시 admin이 user를 사전 생성하는 수요는 BETA 운영 진입 후 결정.
- **email 인증 + 이메일 초대 (A1.5)** — 이메일 인프라 도입 시점에 self-signup에 verification 추가.
- **password 정책 강화 트랙** — min=8 → min=12, zxcvbn/HIBP 사전 공격 방지 (ADR #19 본문은 그대로).
- **first-user-ADMIN advisory lock 보강** — 다중 인스턴스 도입 시점에 PostgreSQL `pg_advisory_xact_lock(<key>)`로 race 엄밀 보장.

---

## 2026-05-02 — 🏁 m-rp-rightpanel-completion 트랙 종료 (RightPanel 4탭 완성 + 버전 다운로드/복원)

### 범위

`dev/active/m-rp-rightpanel-completion/` bootstrap (plan/context/tasks 3파일) → M-RP.1 versions 탭 read-only(G1) → M-RP.2 버전별 download/restore endpoint + UI(G2 사용자 sign-off 옵션 A + denormalized 메타 동기화 자체 리뷰 보강) → M-RP.3 permissions 탭 wiring(G3) → M-RP.4 activity 탭 wiring + AuditQueryFilters 확장(G4 closure).

### 회고

- **commits**: 4개 + closure.
  - `fa24169 wip(m-rp): rightpanel versions tab snapshot` — M-RP.1 작업 스냅샷
  - `71ee56b feat(m-rp.2): version download/restore + audit emit + denorm sync` — M-RP.2 (ADR #39)
  - `b91e28d feat(m-rp.3): RightPanel permissions 탭 wiring` — M-RP.3
  - `cc9c886 feat(m-rp.4): file activity timeline + RP-2 audit scope` — M-RP.4 (ADR #40)
  - + closure commit (BETA-RELEASE/progress/ADR/archive)
- **production 파일 수정/신설**: backend 6 (FileVersionController/Service + AuditQueryFilters/Service/Controller + tests), frontend 신설 7 + 수정 다수 (VersionsTab/PermissionsTab/ActivityTab + 훅 + api/queryKeys + RightPanel 통합).
- **docs sync**: `docs/01 §17.5` (RightPanel 4탭 활성화), `docs/02 §7.6/§7.12` (version + audit endpoint), `docs/03 §4` (VERSION_* emit + RP-2 정책), `docs/00 §5` (ADR #39, #40 추가).
- **신설**: `BETA-RELEASE.md §10·§11` (RightPanel 4탭 + 버전 관리 — 본 closure로 추가).
- **dev-docs**: `dev/active/m-rp-rightpanel-completion/` (3파일) — closure 후 `dev/completed/`로 이동.
- **test**:
  - backend `./gradlew test` BUILD SUCCESSFUL (M-RP.4 audit filter + RP-2 정책 신규 검증 포함, 회귀 0).
  - frontend `pnpm test --run` **647/647** (M-RP 트랙 누계 +84 tests vs mvp-qa-security baseline 563/563).
  - typecheck/lint clean.

### 핵심 결정 (M-RP 트랙)

1. **ADR #39 — 버전 복원 의미론 = 옵션 A (current_version_id 재지정)**: 새 version row 생성하지 않고 `files.current_version_id`만 재지정. 멱등(같은 versionId 재호출 시 audit 0). denormalized 메타(`files.size_bytes`/`files.mime_type`)도 동기화 — `FileUploadService:214-217` invariant 보존(자체 리뷰에서 발견 + 보강).
2. **ADR #40 — RP-2 정책 (activity 탭 권한 범위)**: `targetType="file"` + `targetId` 지정 + 호출자가 해당 파일 `READ` 보유 시 actor 제한 우회(다른 사용자 활동 노출 허용). 그 외 기존 정책(자기 actor만) 유지. RP-2 = "파일 단위 활동 timeline은 파일 권한 보유자에게 모두 보여야 한다"는 UX 요구를 충족하면서 audit 노출 최소화.
3. **모든 탭 fetch gate**: 비활성 탭은 `enabled: tab === 'X'`로 fetch 차단 — 불필요 네트워크 0, RightPanel 마운트 비용 최소화.
4. **M12 audit logs 페이지 회귀 0**: AuditQueryFilters 신규 필드는 nullable additive — 기존 호출부 무영향. 테스트로 명시 검증.
5. **VERSION_DOWNLOADED / VERSION_RESTORED audit emit 활성화**: audit emit coverage 26 → 28 enum (63% → 68%).

### 다음 세션 컨텍스트

- M-RP 트랙 closure로 RightPanel 4탭 완전 wiring. 다음 작업 후보:
  - admin frontend (사용자/부서/권한/스토리지/정책/시스템 페이지) — v1.x 후순위
  - audit emit 추가 13 enum (대부분 `ADMIN_*` + ADR #9 audit_level + ADR #18 MFA 의존) — v1.x
  - RightPanel "더보기" 페이지네이션 (현재 activity/versions 첫 페이지만) — UX backlog
- BETA-RELEASE.md §1 코드 게이트 ✓ 유지 — 베타 readiness 변동 없음 (인프라 게이트 운영팀 sign-off 대기).

### 블로커

없음.

---

## 2026-05-02 — 🏁 mvp-prod-profile 트랙 종료 (application-prod.yml + cron 4종 활성화 + cookie.secure)

### 범위

Phase 0 (WIP `wip/m-rp-rightpanel-completion` 분기 + master를 origin/df7cc97에 rebase — `docs/progress.md` additive 충돌 1건 해소: mvp-qa-security/M-Download top entry 양쪽 보존, mvp-qa-security를 새 HEAD로 위, M-Download 아래) → Phase 1 (`backend/src/main/resources/application-prod.yml` 신설: cron 4종 `enabled=true` + `server.servlet.session.cookie.secure=true` override + `ProdProfileConfigTest` 회귀 차단 2 케이스) → Phase 2 closure (`BETA-RELEASE.md` §1·§2.2·§3·§8·§9 PASS 갱신, Slack appender 항목은 외부 log shipper 운영자 책임/v1.x로 재분류).

### 회고

- **commits**: 본 closure commit 1개 (worktree branch `feature/mvp-prod-profile`) → squash-merge 예정. PR single, 회귀 0.
- **production 파일**: 신설 1.
  - `backend/src/main/resources/application-prod.yml` (32줄) — `app.{purge,share.expiration,permission.expiration,storage.orphan-cleanup}.enabled=true` + `server.servlet.session.cookie.secure=true`. dev/test/CI는 default profile 유지로 무영향.
- **test**: 신설 1 / 수정 0. `backend/src/test/java/com/ibizdrive/config/ProdProfileConfigTest.java` 2 케이스 (prod profile 활성/default profile 유지 회귀 양방향). backend `./gradlew test` GREEN — 회귀 0 (mvp-qa-security baseline 75 classes / 723 tests / 522 PASS / 201 skip + 2 신규 = 525 PASS / 201 skip).
- **docs sync**: `BETA-RELEASE.md` §2.2 cookie.secure ✓ / §3 cron 표 prod profile 컬럼 ✓ / §8 Slack 항목 운영자 책임 마커 / §9 GO 결정 §3 ✓ + §8 외부 shipper로 갱신.

### 핵심 결정 (mvp-prod-profile 트랙)

1. **`application-prod.yml` 분리** — `application.yml`은 dev safe default 유지(cron `false` + cookie `secure=false`). prod 활성화는 `SPRING_PROFILES_ACTIVE=prod` 환경 변수 1회 셋업으로 4 cron + cookie secure 자동 ON. 단일 파일에 prod-only 키만 두어 default 환경 회귀 표면적 0.
2. **TDD via `ApplicationContextRunner`** — 4 cron + cookie property 5개 키를 prod profile context에서 평가, default profile에서 false 유지를 양방향 검증. `@SpringBootTest` 미사용 — DB/Flyway/Web 무의존, `CorsConfigContextBootstrapTest` 패턴 답습. yaml 키 오타 회귀 차단.
3. **Slack appender 드롭 (KISS / YAGNI)** — `BETA-RELEASE.md §8`에서 "권장 (사내 베타 최소)"였던 in-process logback appender는 ① 권장 항목, ② ERROR-당-POST 모델은 incident rate-limit 위험, ③ 운영 표준은 fluent-bit/Promtail/Datadog 외부 shipper. 본 트랙은 application-prod.yml 단일 책임만 닫고 Slack은 v1.x 관측성 트랙 + 운영자 책임으로 재분류.
4. **rebase 충돌 해소 정책** — `docs/progress.md`는 reverse-chronological. 같은 날짜 양쪽 entry는 양쪽 보존, 새 HEAD가 위, 기존 origin이 아래. 추가 entry는 그 위에 stack.

### 파급 영향

- **`BETA-RELEASE.md §1·§2.2·§3·§8·§9` 갱신**. 코드 측 추가 게이트 항목 0건 (이번 트랙으로 §3·§2.2 닫힘, 잔여 §2/§8은 운영자 셋업).
- **dev/process/beta-release-prod-profile.md** session marker 사용 — m-rp-2 working_files와 0건 겹침 확인.
- **m-rp 트랙 영향**: `wip/m-rp-rightpanel-completion` 브랜치에 보존 (커밋 1개 — 13 files, +1124). master 머지 시점은 사용자 결정. *(2026-05-02 후속: M-RP.2~4 commit 추가 후 본 트랙 closure로 master 머지 — 위 entry 참조)*

---

## 2026-05-02 — 🏁 mvp-qa-security 트랙 종료 (Week 11-12 MVP QA + 보안 점검 + 베타 readiness)

### 범위

`dev/active/mvp-qa-security-week-11-12/` bootstrap (plan/context/tasks 3파일 + `findings/` 6개 보고서) → Phase 1 베이스라인 + Inventory(G1) → Phase 2 STRIDE Gap Analysis + 핵심 원칙 11개 conformance(G2 사용자 sign-off A안) → Phase 3 Triage + Remediation(MVP-fix 2건 + v1.x deferred 마커 1건, G3) → Phase 4 docs/03 §5-§10 + docs/04 §3-§14 본문화 또는 deferred 마커 + ROOT `BETA-RELEASE.md` 신설 + closure(G4).

### 회고

- **commits**: 본 closure commit 1개 (master 직접, worktree 미사용 — dev-docs + 작은 코드 fix만).
- **production 파일 수정**: 2개.
  - `backend/src/main/resources/application.yml` — `server.error.{include-stacktrace=never, include-message=on-param, include-binding-errors=on-param}` 3줄 (production stacktrace leak 차단)
  - `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java` — `.headers(h -> h.contentTypeOptions().frameOptions(deny).cacheControl())` chain 추가 (Spring Security 7+ 호환성 + 보안 정책 가시성)
- **docs sync**: 2개.
  - `docs/03-security-compliance.md` — §5.3/§5.4 Phase 3 결정 반영 + §5.1·§5.2·§6·§7·§8·§9·§10 빈 체크박스에 운영/v1.x/v2.x 마커
  - `docs/04-admin-operations.md` — §3·§4·§5·§6·§8·§9·§10·§11·§12·§13·§14 빈 체크박스에 v1.x/운영/구현됨 마커 + §13 cron 표에 상태 컬럼 추가
- **신설**: 1개. `BETA-RELEASE.md` (사내 베타 GO/NO-GO 단일 페이지 체크리스트 — 코드/인프라/cron/보안 헤더/인증/감사/모니터링).
- **dev-docs**: `dev/active/mvp-qa-security-week-11-12/` (3파일 + findings 6개) — closure 후 `dev/completed/`로 이동.
- **test**: 회귀 0. backend `./gradlew test` 75 classes / 723 tests / 522 PASS / 201 skip(no Docker IT) / **0 fail / 0 error** — Phase 1 baseline과 동일. frontend 변경 없음.
- **`.gitignore`**: gradle 임시 디렉터리 5개 패턴 추가(`.gradle-user-home*/`, `.g3/`, `.g4/`, `.g5/`, `.tmp-gradle-root-get/`).

### 핵심 결정 (mvp-qa-security 트랙)

1. **베타 = 사내 베타** — 외부 일반 출시는 본 트랙 범위 밖. SSO/MFA/SAST/외부 모의해킹/Legal Hold/quota는 v1.x 또는 v2.x.
2. **신규 ADR 0건** — deferred 결정은 docs inline 마커 + `findings/triage-decisions.md`로 흡수. ADR은 본문 변경 동반 시에만 사용 (#39부터 다음 트랙).
3. **MVP-blocker 트리아지 (G2 A안 sign-off)**:
   - 확장자 화이트리스트 = **v1.x deferred** (Content-Disposition: attachment + nosniff 1차 방어 충분)
   - production stacktrace = **MVP-fix** (`application.yml` 1줄)
   - Spring Security 헤더 = **MVP-fix** (Spring Security 6 default 명시화 — 동작 변화 0, 7+ 호환성 보험)
4. **`docs/03 §1.3` STRIDE 28행** — 18 구현됨 / 3 부분 / 5 v1.x deferred / 2 운영 책임 / **0 FAIL**. 핵심 원칙 11개 — 8 PASS / 3 PARTIAL(frontend grep 한계) / 0 FAIL.
5. **audit emit coverage** — 41 enum 중 26 emit (63%). 미사용 15는 모두 `ADMIN_*`(admin frontend v1.x) + ADR #9(audit_level) + ADR #18(MFA) 등 deferred 항목에 정합.
6. **운영 cron 4종 (`enabled=false` default)** — `purge.expired`, `share.expire`, `permission.expire`, `storage.orphan.cleanup`. 베타 출시 시 staging/prod에서 명시 enable. 단일 인스턴스 가정.
7. **`BETA-RELEASE.md`로 인프라 게이트 분리** — 코드 게이트(§1)는 master 시점 PASS, 인프라 게이트(§2/§3/§8)는 운영팀 sign-off 필요. 현재 = "코드 readiness 완료, 인프라 미정 = NO-GO".

### 다음 트랙 후보

- 운영팀 인프라 셋업 (HTTPS/HSTS/managed Postgres/시크릿/cron enable) — `BETA-RELEASE.md` 게이트 채움
- admin frontend (사용자/부서/권한/스토리지/정책 페이지) — `ADMIN_*` audit emit 활성화
- ADR #9 audit_level + 파티션 — audit_log 폭증 측정 후 결정
- ADR #18 MFA / refresh rotation / SCIM — 외부 출시 트리거링 시점

---

## 2026-05-02 — 🏁 M-Download 트랙 종료 (BulkActionBar 다운로드 와이어링 — A15 frontend gap closure)

### 범위

DL.0 (worktree `feature/m-download-wire` from `4e3da46` master + dev-docs bootstrap `dev/active/m-download-wire/` 3파일) → DL.1 RED→GREEN (`api.downloadFile(id)` programmatic anchor click helper — `<a href="/api/files/{id}/download">` + body append + click + remove. `BulkActionBar.handleDownload` file-only 가드 — `count===1 && singleItem.type==='file'`. 폴더는 "파일만 다운로드 가능" tooltip, 다중은 "단일 파일 선택 시 사용 가능", 캐시 미스 disabled 폴백 — rename/share 패턴 일관. `console.warn` 스텁 제거. 신규 테스트: `frontend/src/lib/api.downloadFile.test.ts` 3 케이스 + `BulkActionBar.test.tsx` 다운로드 describe 4 케이스) → DL.2 (closure: `docs/01 §9.5` 다운로드 단락 신설, `docs/progress.md` top entry, dev-docs archive, PR + master squash-merge).

### 회고

- **commits**: 2 on top of `4e3da46` master (worktree branch `feature/m-download-wire`) → squash-merge 예정. PR single, 회귀 0.
- **production 파일**: 신설 0 / 수정 2.
  - 수정: `frontend/src/lib/api.ts` (`api.downloadFile` 메서드 추가, +18줄)
  - 수정: `frontend/src/components/files/BulkActionBar.tsx` (`downloadEnabled` + `downloadTitle` + `handleDownload` 와이어링 + 버튼 disabled/title/aria-disabled props, 스텁 제거)
- **test**: 신설 1 / 수정 1. `api.downloadFile.test.ts` 3 GREEN; `BulkActionBar.test.tsx` 4 케이스 추가(12→16). 최종 frontend `pnpm test --run` GREEN 601/601 (baseline 594 → +7 다운로드 케이스). `pnpm typecheck` + `pnpm lint` + `pnpm build` clean.
- **docs sync**: `docs/01-frontend-design.md §9.5` 다운로드 단락 신설 (anchor click 채택 근거 + file-only 가드 정책 + ADR #36 권한 backlink).

### 핵심 결정 (M-Download 트랙)

1. **anchor `<a>` click 채택** (XHR/fetch + Blob 대신) — cookie 인증 same-origin 자동 동봉, RFC 5987 Content-Disposition을 backend가 처리(docs/02 §7.6.1)하므로 파일명 자동 적용, 100MB까지의 파일을 메모리에 적재하지 않고 스트림 → 디스크. 진행률은 브라우저 다운로드 매니저 책임 → UI 추가 0. 함수 5줄. KISS / YAGNI 충족.
2. **file-only 가드** — backend `GET /api/files/{id}/download`은 파일 단건만 지원(폴더 zip은 별도 트랙). BulkActionBar에서 폴더 선택 시 비활성 + tooltip "파일만 다운로드 가능"으로 사용자 혼란 차단.
3. **fire-and-forget** — 다운로드 후 토스트 / 진행률 / 결과 처리 없음. 브라우저 매니저가 사용자 피드백 책임. 추가하면 YAGNI 위배. 실패는 브라우저 표시(404/403도 attachment 응답이 아니라 JSON envelope이라 다운로드 매니저가 파일로 저장하긴 하지만 — 권한 게이트는 `usePermission().DOWNLOAD`가 1차 차단).
4. **DOWNLOAD enum 미사용** — backend `hasPermission('file', 'READ')` 가드(ADR #36). frontend `usePermission().DOWNLOAD`는 UX 게이트, 진실의 출처는 backend READ.
5. **rename/share 패턴 답습** — `count === 1 && !!singleItem` 가드 + `xEnabled` boolean + `disabled/title/aria-disabled` props 동일 형태. 새 추상화 0.

### 파급 영향

- **`docs/01 §9.5`**: 다운로드 단락 신설.
- **frontend backlog 정리**: A15 closure entry의 "BulkActionBar download 스텁" 항목 closed. 잔여(다중 zip 다운로드, preview/inline 분기, 진행률 UI)는 별도 트랙.
- **DB/스키마/backend**: 변경 0 — frontend-only 트랙. backend A15.5는 이미 완전 구현.

---

## 2026-05-02 — 🏁 M16VK 트랙 종료 (Grid View 2D 키보드 wrap — M16V backlog closure)

### 범위

M16VK.0 (worktree `feature/m16v-grid-keyboard-wrap` from `90274c7` master + dev-docs bootstrap `dev/active/m16v-grid-keyboard-wrap/` 3파일) → M16VK.1 (`frontend/src/lib/gridNav.ts` pure helper `computeNextIndex(prev, key, view, columns, length, isPending)` + `gridNav.test.ts` 25 vitest 케이스 — list 8 + grid 14 + initial focus(prev=-1) 3) → M16VK.2 (FileTable.tsx handleKeyDown switch에서 ArrowDown/Up/Left/Right 4 case를 helper 단일 분기로 통합. list ←/→는 helper no-op + preventDefault 스킵으로 상위 핸들러 보존. FileTable.test.tsx에 useGridColumns mock + 6 items 기반 Grid 2D 통합 케이스 6개 추가) → M16VK.3 (closure: `docs/01 §12.1` 키맵 표 List/Grid 분리 + Grid 2D 보강 주, `docs/progress.md` top entry, dev-docs archive, PR + master squash-merge).

### 회고

- **commits**: 3 on top of `90274c7` master (worktree branch `feature/m16v-grid-keyboard-wrap`) → squash-merge 예정. PR single, 회귀 0.
- **production 파일**: 신설 1 / 수정 1.
  - 신설: `frontend/src/lib/gridNav.ts` (`computeNextIndex` pure helper 67줄)
  - 수정: `frontend/src/components/files/FileTable.tsx` (handleKeyDown ArrowKey 4 case → helper 1 분기 + 주석 갱신)
- **test**: 신설 1 / 수정 1. `gridNav.test.ts` 25 GREEN; `FileTable.test.tsx` 6 케이스 추가(3→9). 최종 frontend `pnpm test --run` GREEN 594/594(baseline 588 → +6 wrap 케이스). `pnpm typecheck` + `pnpm lint` + `pnpm build` clean.
- **docs sync**: `docs/01-frontend-design.md §12.1` 키맵 표 List/Grid 컬럼 분리 + Grid 2D 정책 주(↓ overshoot clamp, ↑ 첫행 stay, pending stride skip).

### 핵심 결정 (M16VK 트랙)

1. **Pure helper 분리** — handleKeyDown은 이미 130+라인. `lib/gridNav.ts`로 핵심 인덱스 계산을 추출해 ResizeObserver mock 없이 unit test 가능. CLAUDE.md ULTIMATE INVARIANT 5(확장 전 검토) 충족 — 추상화 정당화는 "ResizeObserver 의존 회피 + 25 케이스 vitest 단순화".
2. **↑/↓ 정책 = column stride** — `prev ± gridSafeColumns`. ↑ overshoot(prev<columns)는 stay(첫 행 wrap 없음), ↓ overshoot은 last partial row에 항목 있으면 `length-1`로 clamp, 없으면 stay. Windows Explorer / macOS Finder 동작 답습.
3. **←/→ 정책 = ±1 + row 경계 자연 wrap** — list 모드 ↑/↓ 1D 패턴을 그대로 차용. 사용자 멘탈 모델 일관.
4. **List 모드 변경 0** — ←/→는 List에서 helper 안에서 no-op으로 처리 + `preventDefault` 스킵으로 상위 핸들러(textbox 캐럿 이동 등)에 영향 없음. 회귀 테스트 GREEN.
5. **prev=-1 초기 focus** — ↓/→는 첫 non-pending로 진입(기존 List 동작 보존), ↑/←는 stay. helper 진입부에서 단일 분기로 처리해 view 무관 통일.
6. **pending skip = stride 방향** — ↑/↓는 column stride(±columns), ←/→는 1-step. helper 내 `walk(prev, step, length, isPending)` 단일 함수로 통합 — step 부호/크기만 호출자가 결정.
7. **shift 범위 확장은 모든 방향 일관** — selectRange anchor 유지. 사용자 입장에서 ↑↓←→ 모두 동일 모델.
8. **`useGridColumns` 변경 0** — ResizeObserver 기반 columns 계산은 그대로 사용. 입력 1개(columns) 추가만으로 helper 동작.

### 파급 영향

- **`docs/01 §12.1`**: 키맵 표 List/Grid 컬럼 분리 + Grid 2D 정책 주 추가.
- **frontend backlog 정리**: M16V closure entry(`docs/progress.md`)의 "Grid 2D 키보드 wrap" 항목 closed. 잔여(Grid DnD, 가변 카드 높이, 썸네일 이미지)는 유지.
- **DB/스키마/backend**: 변경 0 — frontend-only 트랙.

---

## 2026-05-02 — 🏁 storage-orphan-cleanup 트랙 종료 (Storage Orphan Cleanup daily cron — A15/A7 backlog closure)

### 범위

OC.0 (worktree `feature/storage-orphan-cleanup` from `65e5cd3` A15 closure + dev-docs bootstrap `dev/active/storage-orphan-cleanup/`, 3파일 commit `941b6d5`) → OC.1 (`AuditEventType.STORAGE_ORPHAN_CLEANED("storage.orphan.cleaned")` enum + wire 추가, `StorageOrphanCleanupProperties` `@ConfigurationProperties("app.storage.orphan-cleanup")` record `{enabled, cron, zone, maxPerRun, graceHours, batchSize}` + `SchedulingConfig` `@EnableConfigurationProperties` 등록 + `application.yml` 블록 추가, `frontend/src/types/audit.ts` union에 `'storage.orphan.cleaned'` 추가) → OC.2 (`StorageObject` record `(String key, Instant lastModified)` 신설, `StorageClient.listOlderThan(Duration grace)` interface 확장, `LocalFsStorageClient.listOlderThan` impl — `Files.walk(root)` 기반 lazy stream + UUID regex match + mtime grace 컷오프 + 비-UUID name skip + WARN log, 6 RED→GREEN 테스트) → OC.3 (`FileVersionRepository.streamActiveStorageKeys()` `@Query("SELECT v.storageKey FROM FileVersion v")` + `@QueryHints(fetchSize=200, readOnly=true)`. **Plan 정정**: 원안 `JOIN files WHERE deleted_at IS NULL`은 trash file의 storage 보호 invariant 위반(soft-delete 30일 grace 내 storage 객체가 orphan으로 분류되어 삭제 → 복원 시 데이터 손실)이라 단순 stream으로 변경 — A7 hard purge cascade가 file_versions row를 cascade 삭제하므로 다음 cron에서 자연 orphan 분류. 3 신규 `@Transactional` 테스트) → OC.4 (`StorageOrphanCleanupResult` record `(runId, scanned, candidates, deleted, failed, truncated, durationMs)` + `StorageOrphanCleanupService.runDailyCleanup(maxPerRun, graceHours)` 5-stage 파이프라인 — liveSet 적재 → walk → diff → per-row delete(IOException isolation) → audit emit `STORAGE_ORPHAN_CLEANED`. `@Transactional(readOnly=true)` outer + `AuditService.record` REQUIRES_NEW. 8 Mockito 유닛 테스트 — happy/empty/cap/per-row 실패 isolation/non-uuid skip/audit JSON 7-field/invalid args/walk IOException) → OC.5 (`StorageOrphanCleanupJob @Component @ConditionalOnProperty + @Scheduled` — props 기반 cron/zone, RuntimeException catch-all + truncated WARN log, `StorageOrphanCleanupJobDisabledIntegrationTest` `@TestPropertySource(enabled=false)` bean 부재 검증, `StorageOrphanCleanupIntegrationTest` E2E with real Postgres + LocalFs `@TempDir` — live key + orphan(grace 통과) + orphan(in-flight) 3-객체 시나리오로 deletes orphans only + preserves live and in-flight 검증) → OC.6 (closure: ADR #38 + docs/02 §5.6 + docs/04 §13 row + docs/03 §4.1 audit type + progress entry + dev-docs archive + PR + master squash-merge. **참고**: master에 A16(ADR #37)이 먼저 머지되어 본 트랙 ADR은 #38로 재번호).

### 회고

- **commits**: 6 on top of `65e5cd3` A15 closure (worktree branch `feature/storage-orphan-cleanup`) → squash-merge 예정. PR single, 회귀 0.
- **production 파일**: 신설 6 / 수정 5.
  - 신설(backend): `StorageOrphanCleanupProperties.java`, `StorageObject.java`, `StorageOrphanCleanupResult.java`, `StorageOrphanCleanupService.java`, `StorageOrphanCleanupJob.java`
  - 수정(backend): `StorageClient.java` (listOlderThan 추가), `LocalFsStorageClient.java` (listOlderThan impl + UUID_PATTERN), `FileVersionRepository.java` (streamActiveStorageKeys), `AuditEventType.java` (enum 추가), `SchedulingConfig.java` (Properties 등록), `application.yml` (`app.storage.orphan-cleanup.*` 블록)
  - 수정(frontend): `types/audit.ts` (union 추가)
- **test 파일**: 신설 4 + 수정 1. `LocalFsStorageClientTest`(+6 listOlderThan 케이스), `FileVersionRepositoryTest`(+3 streamActiveStorageKeys), `StorageOrphanCleanupServiceTest`(8 Mockito), `StorageOrphanCleanupJobDisabledIntegrationTest`(1 disabled bean 검증), `StorageOrphanCleanupIntegrationTest`(2 E2E — bean registered + deletes orphans only). Testcontainers `disabledWithoutDocker=true` Docker 미가용 환경 자동 skip. backend `./gradlew test` GREEN, frontend 527/527 + typecheck/lint clean.
- **docs sync**: `docs/00-overview.md` ADR #38 신규 row, `docs/02-backend-data-model.md` §5.6 신규 섹션 (storage orphan cleanup 알고리즘 + properties + S3 확장 hook), `docs/04-admin-operations.md` §13 표 행 1개(`storage.orphan.cleanup` daily 01:00) + 각주 [§], `docs/03-security-compliance.md` §4.1 union에 `'storage.orphan.cleaned'` 추가. DB/스키마 변경 0.

### 핵심 결정 (storage-orphan-cleanup 트랙, 확정 → ADR #38)

1. **트리거 = daily cron(`0 0 1 * * *` Asia/Seoul) + 운영자 enable** — A7 hard purge / share-expired-cron / permissions-expired-cron 일관 (`enabled=false` default). A7 purge가 자정에 돌므로 1시간 격차로 trash purge → orphan 발생 → orphan 잡 처리 순.
2. **DB live set = `file_versions.storage_key` 전체 (NO `JOIN files WHERE deleted_at IS NULL`)** — Plan 원안의 JOIN은 trash 30일 grace 내 file의 storage 객체를 orphan으로 잘못 분류 → 복원 데이터 손실 위험. 단순 stream으로 정정 (CLAUDE.md §3 원칙 9 — 문제 은폐 금지). A7 hard purge cascade가 file_versions row 삭제 시점에 자연 orphan으로 분류되어 다음 cron에서 회수.
3. **Walk 대상 = LocalFs `{root}/{YYYY}/{MM}/*` 트리, `{UUID}` leaf만 candidate** — 비-UUID name / 디렉토리 leaf / symlink는 skip + WARN log. UUID regex 검증 후 mtime 비교.
4. **grace = mtime > NOW-24h skip** — in-flight 업로드(트랜잭션 timeout < 5분 가정) race 회피. `app.storage.orphan-cleanup.grace-hours=24` (default).
5. **삭제 cap = `max-per-run:10000`** — A7 `MAX_PURGE_PER_RUN` 패턴. 도달 시 `truncated=true` + 다음 cron 재시도. `truncated` 플래그 시 WARN log.
6. **per-row 실패 isolation** — 객체 1개 delete IOException → ERROR log + 다음 candidate 진행. counters에 `failed` 증가. 전체 잡 실패로 번지지 않음.
7. **Audit = summary 1건/run** = `STORAGE_ORPHAN_CLEANED` (target_type=`system`, target_id=`NULL`, actor_id=`NULL`, metadata=`{runId, scanned, candidates, deleted, failed, truncated, durationMs}`). A7 `SYSTEM_PURGE_EXECUTED` 일관 — per-row spam 회피. `REQUIRES_NEW` 트랜잭션으로 read-only outer trx와 분리.
8. **Lock = `@SchedulerLock` 미도입** — MVP single-instance 가정 (A7 패턴 일관). 멀티-인스턴스화 시 별도 ADR.
9. **Properties 네임스페이스 = `app.storage.orphan-cleanup.*`** — `app.*`(job-related) 일관, `ibizdrive.storage.*`(client config)와 분리. cron job은 `app:`, storage I/O 설정은 `ibizdrive:` — 기존 분리 답습.
10. **신규 enum 추가만으로 호환** — V3 `audit_log.event_type` CHECK 미존재 (VARCHAR(50) 자유). 마이그레이션 0.
11. **`StorageClient.listOlderThan` 시그니처 = `Stream<StorageObject> listOlderThan(Duration grace)`** — Stream lazy 보장으로 큰 트리에서 메모리 폭증 회피. caller(`try-with-resources`)로 close 책임. S3 impl(v1.x)은 ListObjectsV2 paginator로 자연 매핑(LastModified 비교).
12. **권한 트리거 = 시스템 잡 only** — HTTP endpoint 미도입(운영 트리거는 backlog). ROI 검증 후 admin endpoint 별도 ADR.

### 파급 영향

- **`docs/00 §5 ADR`**: #38 신규 row.
- **`docs/02 §5.6`**: 신규 섹션 (cleanup 알고리즘 + properties + S3 확장 hook).
- **`docs/04 §13`**: 배치 작업 표 행 1개(`storage.orphan.cleanup`) + 각주 [§].
- **`docs/03 §4.1`**: audit event union에 `'storage.orphan.cleaned'` 추가.
- **DB/스키마**: 변경 0.
- **Backend backlog**: S3StorageClient `listOlderThan` impl(ListObjectsV2 paginator), `@SchedulerLock`(멀티 인스턴스화 시), 운영자 트리거 admin endpoint(ROI 검증 후).
- **Frontend**: 인터페이스 변경 0 — `'storage.orphan.cleaned'` audit union 추가만, 사용처 0건(unknown 이벤트는 default 분기).

---

## 2026-05-02 — 🏁 A16 트랙 종료 (Department Subject Picker — Domain 도입 + 3-way picker)

### 범위

A16.0 (worktree `feature/a16-department-subject-picker` from `ab45e7d` BulkActionBar fix + dev-docs bootstrap) → A16.1 (V7 마이그레이션 — `departments` 테이블 + `users.department_id` FK + ltree extension + Department 도메인 6파일 entity/Repository/Service/Controller/2 DTOs + V7MigrationIT 9 + DepartmentRepositoryTest 5 + DepartmentSearchServiceTest 11 + DepartmentSearchControllerTest 4) → A16.2 (`PermissionRepository.findEffective` SQL에 dept 매칭 subquery 추가; PermissionRepositoryTest +6) → A16.3 (`ShareDto.subjectName` 필드 추가 + factory 갱신 + ShareCommandService.resolveSubjectName 단건 helper + ShareQueryService.fetchSubjectNames batch helper + everyone/lookup-miss null fallback; ShareCommand/QueryServiceTest +8 + ShareControllerTest fixture 14필드 갱신) → A16.4 (`frontend/src/types/department.ts` 신설 + `lib/api.ts:searchDepartments` + `lib/queryKeys.ts:qk.departments()`/`qk.departmentsSearch` + `types/share.ts ShareDto.subjectName` + 8 fixture `subjectName: null` 보강; `api.departments.test.ts` 7 GREEN) → A16.5 (`useDepartmentSearch` hook — useUserSearch 1:1 답습; 5 tests GREEN) → A16.6 (`DepartmentSearchCombobox.tsx` — UserSearchCombobox 1:1 답습, 표시 필드만 name; 12 tests GREEN) → A16.7 (ShareDialog — subjectType 3-way 라디오 + DepartmentSearchCombobox 마운트 + dept submit 분기 + `subjectLabel(type, id, subjectName)` subjectName-first fallback; +7 tests GREEN) → A16.8 (docs sync `docs/00 ADR #37` + `docs/02 §2.1/§2.2/§7.9/§7.15` + `docs/03 §3.3/§3.4/§3.5` + `docs/01 §14.4` + 본 progress entry + PR + master squash-merge + closure archive). **참고**: master에 A15가 먼저 머지되며 ADR #36을 점유 — A16 ADR은 #37로 재번호.

### 회고

- **commits**: 8 on top of `ab45e7d` (worktree branch `feature/a16-department-subject-picker`) → squash-merge 예정. PR single, 회귀 0.
- **production 파일**: 신설 12 / 수정 8.
  - backend 신설: `Department.java`, `DepartmentRepository.java`, `DepartmentSearchService.java`, `DepartmentSearchController.java`, `DepartmentSummaryDto.java`, `DepartmentSearchResponse.java`, `V7__departments_users_dept.sql`
  - backend 수정: `User.java`, `PermissionRepository.java`, `ShareDto.java`, `ShareCommandService.java`, `ShareQueryService.java`
  - frontend 신설: `types/department.ts`, `hooks/useDepartmentSearch.ts`, `components/shares/DepartmentSearchCombobox.tsx`
  - frontend 수정: `lib/api.ts`, `lib/queryKeys.ts`, `types/share.ts`, `components/shares/ShareDialog.tsx`
- **test**: backend +35 (V7MigrationIT 9 + DepartmentRepositoryTest 5 + DepartmentSearchServiceTest 11 + DepartmentSearchControllerTest 4 + PermissionRepositoryTest +6); frontend `api.departments.test.ts` 7 + `useDepartmentSearch.test.tsx` 5 + `DepartmentSearchCombobox.test.tsx` 12 + `ShareDialog.test.tsx` +7. 최종 backend `./gradlew test` GREEN 666 tests, frontend `pnpm test --run` GREEN 565/565 (baseline 533 → +32). `pnpm typecheck` + `pnpm lint` + `pnpm build` clean.
- **docs sync**: ADR #37 신설 / `docs/02 §2.2 V7 + §7.9 subjectName + §7.15 dept search` / `docs/03 §3.3 dept subject + §3.4 findEffective dept 분기 + §3.5 dept search guard` / `docs/01 §14.4 ShareDialog 3-way picker + DepartmentSearchCombobox + subjectName fallback`.

### 핵심 결정 (A16 트랙, 확정 → ADR #37)

1. **Department 도메인 도입 (V7)** — A14 결정 시점("V_ 마이그레이션 0")의 제약 해소. `departments` 테이블 + `users.department_id` FK 활성화. LTREE 컬럼은 schema 도입만, 애플리케이션은 flat 사용 (KISS, 트리 v1.x deferred).
2. **권한 평가 SQL 변경 필수** — A14 결정 #4 검증 정정. `PermissionRepository.findEffective`에 `subject_type='department' AND subject_id = (SELECT department_id FROM users WHERE id=:userId AND active)` OR 분기 추가. NULL/비활성 사용자는 unmatched(false). dept 후손 자동 포함은 v1.x deferred.
3. **ShareDto.subjectName = backend join (A13 패턴 답습)** — nullable 필드 1개 추가. user→users.display_name, department→departments.name, everyone/lookup miss → null. POST는 트랜잭션 내 단건 helper, by-me/with-me는 페이지 결정 후 batch helper(type별 1회 IN 절). 단건 lookup endpoint 미추가.
4. **Frontend = F6 답습 1:1** — `useDepartmentSearch`/`DepartmentSearchCombobox`는 user 변형의 동형. 일반화 거부 — 추상화 정당화 3+ 규칙 미충족 (KISS, ULTIMATE INVARIANT 5).
5. **Role share UI 보류** — schema impedance(role enum vs role-grant lookup). picker 라디오 3종(everyone/user/department)으로 한정. backend `subject_type='role'` persistable 유지 — v1.x role-share 트랙으로 분리.
6. **`subjectLabel` subjectName-first fallback** — `subjectName != null`이면 그대로 표시. null fallback 시 type+UUID 머릿8자(기존 동작 보존). everyone은 항상 "모든 사용자".

### 파급 영향

- **DB**: V7 마이그레이션 추가. 기존 V1~V6 무변경. `departments` 테이블 신설 + `users.department_id` FK 활성화.
- **wire**: ShareDto 13 → 14 필드 (`subjectName: string|null` 추가). Frontend fixture 8 위치 보강.
- **권한 평가**: `findEffective` OR 분기 추가. user/everyone/role grant는 기존 동작 보존, dept grant 신규 매칭.
- **frontend backlog**: F6 closure의 "department 옵션 backlog" 항목 closed. role 옵션은 v1.x backlog로 잔존.

---

## 2026-05-02 — 🏁 A15 트랙 종료 (Storage 모듈 + 파일 업로드/다운로드 endpoint)

### 범위

A15.0 (worktree `feature/a15-file-upload-download` from `09d4b52` A14 closure + dev-docs bootstrap `dev/active/a15-file-upload-download/`, 3파일 commit `b98b044` — ADR #13 재정정/ADR #36 신규 초안은 closure로 이월) → A15.1 (`backend/src/main/java/com/ibizdrive/storage/{StorageClient,LocalFsStorageClient,StorageProperties}.java` 신설 — interface `write(key,in,size)`/`read(key)`/`delete(key)` + LocalFs impl `{root}/{YYYY}/{MM}/{UUID}` 객체 키, `@ConfigurationProperties("app.storage")` `{type, root}`, `application.yml` 추가; `LocalFsStorageClientTest` 9 RED→GREEN — write/read roundtrip + 객체 키 포맷 + delete idempotent + 부재 read NoSuchFileException) → A15.2 (`backend/src/main/java/com/ibizdrive/file/{UploadResolution,UploadResult}.java` enum/record 신설 + `FileUploadService` skeleton + 7 Testcontainers RED — Docker 미가용 시 skip; audit emission은 file/ 패키지 기존 `emitAudit` direct convention 답습 — listener 미도입(ADR #36 채택)) → A15.3 (`FileUploadService.upload(...)` GREEN — folder lock + UNIQUE 이중 가드 + storage write + INSERT files+versions + UPDATE current_version_id + emitAudit FILE_UPLOADED, `FileVersionRepository.findMaxVersionNumberByFileId` + `FileRepository.lockActiveByFolderAndNormalizedName` 추가, RENAME 자동 suffix `(N)`) → A15.4 (`FileUploadController` POST `/api/files` + `UploadResponse` DTO + multipart 활성화 100MB cap; `FileUploadControllerTest` 8/8 GREEN — wire `resolution: new_version|rename|null`) → A15.5 (`FileDownloadService` + `FileDownloadController` + `DownloadHandle` 신설 — RFC 5987 Content-Disposition (`filename=` ASCII fallback + `filename*=UTF-8''` percent-encode) + `ETag("<versionId>")` + Content-Type fallback `application/octet-stream`, audit FILE_DOWNLOADED, 권한 = file `READ` (별도 `DOWNLOAD` enum 미도입); 7 service + 6 controller GREEN) → A15.6 (`frontend/src/lib/api.ts` `uploadFile` 분기를 실 `XMLHttpRequest`로 교체 — `POST /api/files` multipart + `withCredentials=true`, `frontend/src/lib/{fakeXhr.ts,fakeXhr.test.ts}` 삭제, `useUpload.ts` 타입 `XhrLike = XMLHttpRequest` 전환 + 409 envelope `{error:{code,message,details:{fileId,fileName}}}` 파싱(폴백 `conflictWith=undefined` → `UploadConflictDialog`가 `task.file.name`로 폴백 — 검증 `UploadConflictDialog.tsx:27`), MOCK_FILES side-effect 제거(backend authoritative), 신규 `api.upload.test.ts` 4 + `useUpload.test.ts` 9 (`vi.stubGlobal('XMLHttpRequest', MockXHR)` 패턴 + 파일명→응답 정적 테이블), 최종 527/527 GREEN + typecheck/lint clean) → A15.7 (ADR #13 supersede 마커 + 신규 ADR #36 (storage abstraction + multipart MVP), `docs/02 §7.6` 표에 POST `/api/files` 행 추가 + `download` guard `'DOWNLOAD'` → `'READ'` 정정 + 신규 §7.6.1 multipart spec(요청 form parts/응답 status/409 envelope/Audit)/download 헤더(RFC 5987/ETag/Content-Type fallback), §7.7 tus는 supersede 마커로 보존(MVP 미구현), `docs/progress.md` 본 entry + dev-docs archive + PR + master squash-merge).

### 회고

- **commits**: 7 on top of `09d4b52` A14 closure (worktree branch `feature/a15-file-upload-download`) → squash-merge 예정. PR single, 회귀 0.
- **production 파일**: 신설 8 / 수정 5.
  - 신설(backend): `StorageClient.java`, `LocalFsStorageClient.java`, `StorageProperties.java`, `FileUploadService.java`, `FileUploadController.java`, `FileDownloadService.java`, `FileDownloadController.java`, `DownloadHandle.java`, `UploadResolution.java`, `UploadResult.java`, `UploadResponse.java`
  - 수정(backend): `FileRepository.java` (lock/exists 헬퍼), `FileVersionRepository.java` (findMaxVersionNumberByFileId), `application.yml` (`app.storage.*` + multipart 한도)
  - 수정(frontend): `lib/api.ts` (실 XHR), `hooks/useUpload.ts` (XHR 타입 + 409 envelope details 파싱)
  - 삭제(frontend): `lib/fakeXhr.ts`, `lib/fakeXhr.test.ts`
- **test 파일**: 신설 6. `LocalFsStorageClientTest`(9), `FileUploadServiceTest`(7 Testcontainers), `FileUploadControllerTest`(8), `FileDownloadServiceTest`(7), `FileDownloadControllerTest`(6), `api.upload.test.ts`(4), `useUpload.test.ts`(9, 재작성). 최종 backend `./gradlew test` GREEN (Testcontainers Docker 가용 환경 한정), frontend `pnpm test --run` **527/527 GREEN** + `pnpm typecheck` + `pnpm lint` clean.
- **docs sync**: `docs/00-overview.md` ADR #13 supersede 마커 + 신규 ADR #36 (A15 정책 묶음), `docs/02-backend-data-model.md` §7.6 표 + 신규 §7.6.1 multipart/download spec + §7.7 supersede 마커. DB/스키마 변경 0 (storage 추상화는 코드 레벨, files/file_versions 테이블 재사용).

### 핵심 결정 (A15 트랙, 확정)

1. **MVP = 단일-POST multipart** — tus 프로토콜 v1.x 재이월. ADR #13 supersede(`docs/00 §5 ADR #36`) 명시. KISS+YAGNI: tus-java-server + 재개 토큰 lifecycle + S3 multipart 라이브러리 위임 비용이 100MB cap·평균 분포에서 ROI 역전. tus는 §7.7로 spec 보존(v1.x 재개 시 백업).
2. **Storage 추상화 = `StorageClient` interface + `LocalFsStorageClient` MVP impl** — S3 impl은 v1.x. AWS SDK v2 의존성 미추가. 객체 키 `{YYYY}/{MM}/{UUID}` (ADR #5 storage_key UUID 정합 — 원본 파일명은 DB에만).
3. **권한 = upload `UPLOAD` / download `READ`** — 별도 `DOWNLOAD` enum 미도입. `READ`가 view+download 모두 grant — KISS, docs/03 §3 권한 매트릭스 단순화. 업로드는 부모 folder의 `UPLOAD` 위임.
4. **Audit emission = file/ 패키지 직접 호출 convention** — `FileMutationService:298-315` `emitAudit` 헬퍼 답습. share/permission/auth는 listener 패턴이지만 file/ 패키지 내부 일관성을 위해 직접 호출 채택 — 동일 패키지에 두 패턴 혼재 회피 (KISS+§3). FILE_UPLOADED/FILE_DOWNLOADED 활성화.
5. **Conflict resolution wire format = `new_version` | `rename` | unset(409)** — M5 frontend 인터페이스 1:1. 자동 RENAME suffix `(N)` 부여(예: `report.pdf` → `report (2).pdf`).
6. **응답 status 분기**: 신규 파일 INSERT → **201 Created**, NEW_VERSION 분기 → **200 OK**. body = `UploadResponse{file: FileDto, versionId, versionNumber, newFile, resolution}`.
7. **409 envelope = `{error:{code:'RENAME_CONFLICT', message, details:{fileId, fileName}}}`** — `UploadConflictDialog`는 `details` 부재 시 `task.file.name` 폴백(검증 `UploadConflictDialog.tsx:27`).
8. **Download 헤더 = RFC 5987 + ETag(versionId) + Content-Type fallback** — `Content-Disposition: attachment; filename="<ascii-fallback>"; filename*=UTF-8''<percent-encoded>` (UTF-8 + 비ASCII 안전), `Content-Type` null/invalid → `application/octet-stream`, `ETag: "<versionId>"`, `Content-Length: version.sizeBytes`.
9. **Storage orphan = MVP 한정 알려진 한계** — 트랜잭션 실패 시 storage 객체 잔존 가능. cleanup job은 별도 트랙(편법 아닌 명시적 deferred — CLAUDE.md §3 원칙 9). storage 모듈 v1.x에서 `S3StorageClient` 추상화 + orphan detect 잡 cross-check 도입.
10. **Frontend FakeXHR 모듈 삭제** — `api.uploadFile`이 유일한 production importer였으므로 dev-only stub 격리 대신 완전 삭제. 테스트는 `vi.stubGlobal('XMLHttpRequest', MockXHR)` + 파일명→응답 정적 테이블로 FakeXHR 시절 magic-filename 계약 보존.

### 파급 영향

- **`docs/00 §5 ADR`**: #13 row에 Superseded 마커, #36 신규 row 추가.
- **`docs/02 §7.6`**: POST `/api/files` 행 신규 + `download` guard `'DOWNLOAD'` → `'READ'` 정정 + 신규 §7.6.1 multipart/download spec.
- **`docs/02 §7.7`**: tus spec은 supersede 마커와 함께 보존 (MVP 미구현, v1.x 백업).
- **DB/스키마**: 변경 0 — `files`/`file_versions` 테이블 재사용. `current_version_id` NULL 허용은 그대로(MVP 단일-버전 가정 유지).
- **Backend backlog**: storage orphan cleanup, S3 impl, Testcontainers Docker 부재 환경에서 service-level 단위 테스트(현재 7 케이스는 통합), tus 재개 업로드(v1.x ADR #13 재오픈).
- **Frontend**: 인터페이스 변경 0 — `UploadDock`/`upload store`/`ConflictDialog` 무수정. 시각적/기능적 회귀 0.

---

## 2026-05-01 — fix: BulkActionBar — 폴더 단일 선택 시 공유 버튼 활성화

### 변경

`frontend/src/components/files/BulkActionBar.tsx` — 공유 버튼의 `singleItem.type === 'file'` 가드 제거 + `handleShare` `kind: singleItem.type` 분기 + tooltip "단일 파일 선택 시 사용 가능" → "단일 항목 선택 시 사용 가능". 폴더 공유 endpoint(A12) + ShareDialog folder 분기(F5.2) + useCreateShare folder 변형이 모두 closed 상태이므로 BulkActionBar의 file-only 가드만 남아 있던 drift를 정정.

### 회고

- **production 파일**: 수정 1 (`BulkActionBar.tsx` — 3 line edit).
- **test 파일**: 수정 1 (`BulkActionBar.test.tsx` — 신규 describe 블록 + 4 케이스: file-kind 진입, folder-kind 진입, 다중 비활성, cache-miss 비활성).
- **검증**: `pnpm test --run` **533/533 GREEN** (baseline 529 → +4). `pnpm typecheck` + `pnpm lint` clean.
- **트랙 단위**: dev-docs 미생성 — 단일 atomic 변경(CLAUDE.md OPERATIONAL RULES "원자적 변경"). worktree 미생성, master 위 단일 commit.

### 핵심 결정 (확정)

1. **다중(2+) 공유는 비활성 유지** — wire는 `POST /api/{files|folders}/:id/share` 단건. 다중 공유는 (a) 클라이언트 반복 호출 (b) batch endpoint 신설 정책 미정 → 별도 트랙. 본 변경 scope는 단일 선택만.
2. **F5 closure backlog 글 정정** — "폴더 다중 선택 자체 부재"는 부정확. selection store(`Set<string>`) + FileTable 통합 row 모델로 폴더 다중 선택은 F4 시점부터 동작 중이었음. 진짜 missing은 BulkActionBar 공유 버튼의 file-only 가드 1줄.
3. **dev-docs 트랙 미생성** — 변경 단위가 1 컴포넌트 / 3 line / 4 테스트 케이스. 트랙 prelude(plan/context/tasks 3파일 + worktree + 6 phase)는 over-engineering.

### 파급 영향

- **frontend backlog 정리**: F5 closure의 "폴더 다중 선택 BulkActionBar 공유 액션" 항목 → folder-kind 단일 진입 부분 closed. 다중 공유는 backlog 잔존.
- **DB/스키마/wire**: 변경 없음.

---

## 2026-05-01 — 🏁 F6 트랙 종료 (Frontend Share Subject Picker — User)

### 범위

F6.0 (worktree `feature/f6-user-search-picker` from `09d4b52` A14 closure + dev-docs bootstrap `dev/active/f6-user-search-picker/`) → F6.1 (`UserSummary` 타입 신설 + `qk.users()`/`qk.usersSearch(normalized, limit)` 키 팩토리 + `api.searchUsers({q, limit?}, {signal?})` — q < 2 자체 short-circuit, default limit=20, `searchFiles` wire 패턴 그대로 답습; `api.users.test.ts` 7 케이스 GREEN) → F6.2 (`useUserSearch(rawQuery, {limit?})` — `useDebounce(300ms)` + `q.trim().toLowerCase()` (A14 ADR #35 — `normalizeForSearch` NFC collapse 미적용) + `useQuery` enabled `normalized.length >= 2` + `keepPreviousData` + `staleTime 30_000` + signal 전파; `useUserSearch.test.tsx` 5 케이스 GREEN) → F6.3 (`UserSearchCombobox.tsx` 신설 — WAI-ARIA 1.2 Combobox + Listbox self-contained, controlled `value: UserSummary|null` + `onChange`, internal `rawInput`/`isOpen`/`activeIndex` state, ArrowDown/Up wrap-around + Enter commit + Esc close + Click(`onMouseDown.preventDefault` + `onClick.commit`), 선택 후 재입력 시 `onChange(null)` (RenameDialog input-as-state 패턴), `aria-controls`/`aria-expanded`/`aria-activedescendant` 정합; 12 케이스 GREEN) → F6.4 (`ShareDialog.tsx` 통합 — `subjectType: 'everyone'|'user'` state default `'everyone'` + `selectedUser: UserSummary|null` state, 라디오 그룹 `모든 사용자 | 특정 사용자`, `subjectType==='user'` 분기로 Combobox 마운트, submit 분기: everyone → `{type:'everyone'}` / user+selected null → `toast.error('공유할 사용자를 선택해 주세요')` + return / user+selected → `{type:'user', id: selectedUser.id}`, dialog 재오픈 시 reset; `ShareDialog.test.tsx` +5 케이스 GREEN) → F6.5 (`docs/01 §14.4`에 subjectType 라디오 흐름 + `UserSearchCombobox`/`useUserSearch` 등재 + `docs/progress.md` 본 entry + dev-docs archive + PR + master squash-merge).

### 회고

- **commits**: 6 on top of `09d4b52` A14 closure (worktree branch `feature/f6-user-search-picker`) → squash-merge 예정. PR single, 회귀 0.
- **production 파일**: 신설 4 / 수정 3.
  - 신설: `frontend/src/types/user.ts`, `frontend/src/hooks/useUserSearch.ts`, `frontend/src/components/shares/UserSearchCombobox.tsx`
  - 신설(api 메서드): `api.searchUsers` (`frontend/src/lib/api.ts` 증분)
  - 수정: `frontend/src/lib/api.ts`, `frontend/src/lib/queryKeys.ts`, `frontend/src/components/shares/ShareDialog.tsx`
- **test 파일**: 신설 4. `api.users.test.ts`(7), `useUserSearch.test.tsx`(5), `UserSearchCombobox.test.tsx`(12), `ShareDialog.test.tsx`(+5). 최종 `pnpm test --run` **529/529 GREEN** (baseline 500 → +29). `pnpm typecheck` + `pnpm lint` + `pnpm build` clean.
- **docs sync**: `docs/01-frontend-design.md §14.4` — subject picker user 옵션 + Combobox 등재 + subjectType 라디오 흐름. DB/스키마 변경 0 (frontend 전용 트랙).

### 핵심 결정 (F6 트랙, 확정)

1. **`useUserSearch` normalize = `trim().toLowerCase()` only** — A14 ADR #35 정책. `normalizeForSearch`(NFC collapse + 다이아크리틱 제거)는 file/folder name 검색 전용. 사용자 displayName/email은 1:1 매칭 의도 — Unicode collapse는 false-positive 위험.
2. **Combobox는 self-contained (외부 a11y 라이브러리 거부)** — KISS + 의존성 최소. WAI-ARIA 1.2 Combobox + Listbox 패턴 직접 구현. 키보드 1D + wrap-around로 충분, 스크롤-into-view 미구현(option 10개 cap 가정).
3. **선택 = `value` controlled, input 텍스트 = internal state** — RenameDialog input-as-state 패턴 차용. 재입력 시 `onChange(null)`로 이전 선택 무효화 → submit 시 race 방지.
4. **단일 선택만 (multi-chip 없음)** — A14 wire가 `subjects[]`(다중)을 지원하지만 본 트랙 scope 외. 다중은 v1.x.
5. **subjectType radio default `'everyone'`** — 기존 ShareDialog UX 보존. 사용자가 명시적으로 `특정 사용자` 선택해야 picker 마운트.
6. **submit guard = inline toast** — `selectedUser=null + subjectType=user` 시 toast.error로 재시도 유도. dialog 닫지 않음.
7. **dialog 재오픈 시 reset** — open useEffect 안에서 `setSubjectType('everyone')` + `setSelectedUser(null)` — 이전 선택 누수 방지.
8. **`Esc` propagation 차단** — Combobox 자체 Esc는 `e.stopPropagation()` 후 listbox close만 수행. ShareDialog의 Esc 닫기와 충돌 방지(listbox 열림 상태에서 Esc → dialog까지 닫히면 UX 손실).

### 파급 영향

- **A14 wire 활용**: A14가 등재한 `GET /api/users/search` (q minLen 2, limit 1~50 default 20 cap 50) 1:1 사용. backend 변경 0.
- **frontend backlog**: department/role subject picker(별도 endpoint 필요), 다중 선택 chip, 사용자 lookup 캐시 공유(`qk.usersSearch` staleTime 30s 활용), Combobox 외부-클릭 close(현재 옵션 click + Esc만 close — modal 안에서 충분).
- **DB/스키마**: 변경 없음.

---

## 2026-05-01 — 🏁 M16 follow-up Grid 가상화 종료 (`useGridColumns` + row 단위 `useVirtualizer`)

### 범위

M16V.0 (worktree `feature/m16-grid-virtual` from `9cba282` A13 closure + dev-docs bootstrap `dev/active/m16-grid-virtual/`) → M16V.1 (`frontend/src/hooks/useGridColumns.ts` 신설 — container width를 ResizeObserver로 구독해 `Math.max(1, floor((width+gap)/(min+gap)))` columns 산출, SSR 안전 초기 1) → M16V.2 (`FileTable.tsx` grid 분기 row 단위 가상화 — 별도 `gridContainerRef` + `gridVirtualizer` 인스턴스, `count = ceil(items / columns)`, `estimateSize = 168`(고정), virtualRow 안에서 inline `gridTemplateColumns: repeat(N, minmax(0,1fr))`로 슬라이스 렌더, `data-grid-virtual="true"` 마커, `aria-rowcount`를 `gridRowCount`로 정정) → M16V.3 (`handleKeyDown` ArrowUp/Down 시 `view==='grid'` 분기로 `gridVirtualizer.scrollToIndex(Math.floor(idx/columns))` 호출, list 분기는 무수정) → M16V.4 (vitest `useGridColumns.test.ts` 4 케이스 + `FileTable.test.tsx`에 `@tanstack/react-virtual`/`ResizeObserver` mock + grid 마커 검증 1 추가, 기존 list/grid 시나리오 무수정) → M16V.5 (`docs/01 §18 row 16` footnote에 가상화 closed 마커 + `docs/progress.md` 본 entry + dev-docs archive).

### 회고

- **commits**: 1 on top of `9cba282` A13 closure (worktree branch `feature/m16-grid-virtual`) → squash-merge 예정. 단일 PR.
- **production 파일**: 신설 1 / 수정 1.
  - 신설: `frontend/src/hooks/useGridColumns.ts`
  - 수정: `frontend/src/components/files/FileTable.tsx`
- **test 파일**: 신설 1 / 수정 1. `frontend/src/hooks/useGridColumns.test.ts`(4) + `FileTable.test.tsx` (+1 신규 `data-grid-virtual` 마커 검증, mock 인프라 추가).
- **검증**: `pnpm test` **66 files / 500 tests GREEN** (baseline 65/495 → +1/+5). `pnpm typecheck` + `pnpm lint` clean.
- **docs sync**: `docs/01 §18 row 16` footnote 가상화 closed 마커 + v1.x 잔여 명시.

### 핵심 결정 (M16V 트랙, 확정)

1. **두 개의 virtualizer 인스턴스(list/grid) — view 분기 안에서만 active** — 단일 인스턴스의 view-aware count 분기는 dependency 폭발/coupling 야기. 분기는 서로 다른 컴포넌트 트리이므로 자연 격리(unmount/mount).
2. **`CARD_ROW_HEIGHT = 168` 고정 estimate** — 가변 높이(`measureElement`)는 v1.x. KISS — 카드 내부 layout 안정.
3. **inline style `gridTemplateColumns`** — Tailwind dynamic class(`grid-cols-${n}`)는 JIT 미스. `gap`도 inline. KISS.
4. **키보드 1D 유지** — `focusedIndex ±1`. grid 모드는 `Math.floor(idx/columns)`로 row index scroll만 추가. 좌/우 wrap (2D 네비게이션)은 v1.x.
5. **list 분기 코드 zero-touch** — 회귀 0 강제. 추가 변경은 (a) grid 전용 ref/virtualizer 추가, (b) focus selector view-aware fallback 1 line, (c) `handleKeyDown` 내 scroll 분기 helper 1개로 격리.
6. **`aria-rowcount` 정정** — 기존 grid는 `items.length`로 잘못 표기. `gridRowCount`(=ceil(items/columns))가 ARIA 의미상 정확.
7. **테스트 mock 전략** — `@tanstack/react-virtual` 테스트 파일 단위 mock(전 항목 visible 반환)으로 jsdom 0-viewport 한계 우회. ResizeObserver는 인라인 클래스 stub. **전역 setup 변경 없음** — 영향 격리.
8. **lockfile 미포함** — repo가 `pnpm-lock.yaml`을 트랙 안 함(.gitignore 미설정이지만 master 부재). 본 PR도 미포함 정책 유지.

### 파급 영향

- **M16 본체(2026-04-29) 비범위 절 진행**: M16 closure 시 `가상화 / 2D 키보드 / DnD / 썸네일`을 v1.x로 분리한 결정 중 **가상화만** closed. 나머지 3개는 그대로 v1.x 잔여.
- **M16V scope 외 backlog**: Grid 2D 키보드 wrap (좌/우 + columns 기반 ↑/↓), Grid DnD (list useDraggable 재사용 + drop target 시각화), 썸네일 이미지 (backend thumbnail API 후), 가변 카드 높이 (`measureElement`).
- **DB/스키마**: 변경 없음.
- **다른 계약 파일(`queryKeys`, `audit.ts` 등)**: 변경 없음.

---

## 2026-05-01 — 🏁 A13 트랙 종료 (`ShareDto` ↔ `permissions` join 복원, subject/preset surface)

### 범위

A13.0 (worktree `feature/a13-shares-permissions-join` from `544afc9` F5 closure 후 `fe9e963` permissions-expired-cron 위로 rebase + dev-docs bootstrap `dev/active/a13-shares-permissions-join/`) → A13.1 (`ShareDto` record에 `subjectType`/`subjectId`/`preset` 3 필드 복구, Jackson record 직렬화 wire 정합) → A13.2 (`ShareCommandService.createFileShares` / `createFolderShares` POST 응답 트랜잭션 내 `PermissionRow` 그대로 매핑 — 추가 SELECT 없음) → A13.3 (`ShareQueryService.listSharesByMe` / `listSharesWithMe` 페이지 결정 후 `permissionRepository.findAllById(ids)` **1회 IN-batch** N+1 회피, MAX_LIMIT=100 → 페이지당 최대 100 IN) → A13.4 (`ShareControllerTest` wire JSON 3 필드 surface 검증 강화 + `ShareQueryServiceTest` Mockito `grantRow` 로컬 변수 추출로 nested-stubbing exception 회피 + `ShareCommandServiceTest` POST 응답 매핑 단위 테스트 보강) → A13.5 (frontend `ShareDto` interface 3 필드 + `SharesTable` 4열 복원 `항목 | 공유한 사람 | 권한 | 만료` + `ShareDialog` 기존-share 행 `subjectLabel · presetLabel · 만료/무기한 + 해제` + `presetLabel` / `subjectLabel` helper) → A13.6 (`docs/00 §5 ADR #34` A13 closure 마커 + `docs/01 §14.4` 4열·subjectLabel·presetLabel 반영 + `docs/02 §7.9` POST/by-me/with-me 응답 schema 3 필드 + N+1 회피 IN-batch 명시) → A13.7 (PR #32 squash-merge `393e38f` + dev-docs archive).

### 회고

- **commits**: 1 on top of `fe9e963` permissions-expired-cron closure (worktree branch `feature/a13-shares-permissions-join`) → squash-merge `393e38f` on `master`. PR #32 single, frontend vitest + backend junit 모두 1회 GREEN. rebase 시 `docs/00-overview.md` ADR #34 cell 충돌 발생(master의 permissions-expired-cron closure marker vs A13 closure marker 동일 cell 확장) → 둘 다 보존하는 형태로 수동 머지.
- **production 파일**: 신설 0 / 수정 8.
  - backend: `ShareDto.java`, `ShareCommandService.java`, `ShareController.java`, `ShareQueryService.java`
  - frontend: `frontend/src/types/share.ts`, `frontend/src/components/shares/SharesTable.tsx`, `frontend/src/components/shares/ShareDialog.tsx`
- **test 파일**: 신설 0 / 수정 9. `ShareCommandServiceTest`, `ShareControllerTest`, `ShareQueryServiceTest` + frontend `useSharesByMe.test.tsx`, `useSharesWithMe.test.tsx`, `useCreateShare.test.tsx`, `api.shares.test.ts`, `SharesTable.test.tsx`, `ShareDialog.test.tsx`. backend share + audit GREEN, frontend 65 files / 495 tests GREEN.
- **docs sync**: `docs/00 §5 ADR #34` (A13 closure + permissions-expired-cron closure 양립), `docs/01 §14.4`, `docs/02 §7.9`. DB/V_ 마이그레이션 변경 0.

### 핵심 결정 (A13 트랙, 확정)

1. **POST 응답 = 트랜잭션 내 `PermissionRow` 그대로 매핑 (추가 SELECT 0)** — INSERT 시점에 grant row 객체가 이미 메모리에 존재 → `ShareDto` 생성 시 그 자리에서 reuse. by-me/with-me는 페이지 결정 후 `findAllById` IN-batch 1회. 두 경로의 패턴 차이는 의식적 — POST는 단일 row, query는 페이지(N rows)이므로 배치 대상이 다름.
2. **N+1 회피 = `findAllById` (Spring Data 표준 IN 절)** — 별도 JPQL/native query 추가 거부. MAX_LIMIT=100이 IN 절 길이 상한 보장 → 별도 chunking 불요.
3. **V6 FK CASCADE invariant** — active share row의 `permission_id`가 가리키는 permission row는 항상 존재. 누락 시 `IllegalStateException` (operationally unreachable, defense-in-depth만). N+1 회피 batch에서 `Map<UUID, PermissionRow>` lookup 시 missing key는 invariant 위반.
4. **frontend SharesTable 4열 복원** — F5.1에서 3열로 단순화한 결정을 정정. F5.1 당시는 backend wire에 preset이 없어 frontend 표기 불가 → 본 트랙에서 wire 복원과 동시에 컬럼 복원. presetLabel 한글화(read→읽기 / upload→업로드 / edit→편집 / admin→관리).
5. **subjectLabel = `everyone` → "모든 사용자", 나머지 → `{type} {id.slice(0,8)}`** — UUID 머릿8자 노출은 가독성 vs 식별 가능성 trade-off. department/role 이름 lookup은 별도 트랙(권한 관리 UI) 영역.
6. **ShareControllerTest wire JSON 검증 강화** — 3 필드 surface는 record 필드 추가만으로 자동 직렬화되지만, drift 방지 위해 controller test에서 `subjectType`/`subjectId`/`preset` JSON path 명시 단언.
7. **Mockito `grantRow` 로컬 변수 추출** — `ShareQueryServiceTest`에서 `when(permissionRepository.findAllById(...)).thenReturn(List.of(grantRow(...)))` 패턴이 nested-stubbing(`grantRow` 내부 stubbing 호출이 외부 `when` 진행 중에 발생) → `UnfinishedStubbingException`. 해결: `PermissionRow grant = grantRow(...)`로 outer stubbing 진입 전 evaluate.
8. **rebase 충돌 정책 = 둘 다 보존** — `permissions-expired-cron closure`(master) + `A13 closure`(branch) 모두 ADR #34 cell의 시간순 closure marker 누적이 의도. 한쪽 drop 거부.

### 파급 영향

- **F5 wire drift 정정**: F5.1에서 의식적으로 제거(`backend wire에 surface 못함`)했던 3 필드가 본 트랙으로 복구. 남은 `permissions` 직접 grant UX(만료 시각, message)는 별도 트랙.
- **ADR #34 누적**: A10 → A12 → SHARE_EXPIRED → permissions-expired-cron → A13 5개 closure marker가 동일 cell에 누적. 다음 트랙도 cell 분할이 아닌 marker append 형태 유지.
- **frontend audit.ts / queryKeys 등 다른 계약 파일**: 변경 없음.
- **DB/스키마**: 변경 없음 (V_ 마이그레이션 0개).
- **잔여 backlog (A13 scope 외)**: department/role subject name lookup, ShareDialog에서 subject picker(현재는 `everyone`만 default UI), `permissions.message` 컬럼 도입 시 ShareDto에 `message` 필드 별도 노출 검토.

---

## 2026-05-01 — 🏁 permissions-expired-cron 트랙 종료 (`permissions.expires_at` 만료 cron + `permission.expired` audit)

### 범위

PE.0 (worktree `feature/permissions-expired-cron` from `544afc9` F5 closure + dev-docs bootstrap `dev/active/permissions-expired-cron/`) → PE.1 (`PermissionExpiredEvent` 신규 record + `PermissionRepository.lockById(UUID)` PESSIMISTIC_WRITE + `findExpiredActiveIds(Instant, Pageable)` JPQL + `PermissionService.expirePermission(UUID)` lock→snapshot→DELETE→publish) → PE.2 (`AuditEventType.PERMISSION_EXPIRED("permission.expired")` + `PermissionAuditListener.onPermissionExpired` system metadata helper + `frontend/src/types/audit.ts` union 추가) → PE.3 (`PermissionExpirationProperties` record + `PermissionExpirationJob @ConditionalOnProperty + @Scheduled` + `SchedulingConfig` 등록 + `application.yml` `app.permission.expiration.*` 블록 default disabled) → PE.4 (Mockito-only `PermissionExpirationJobTest` 6개 + `PermissionServiceExpireTest` 5개 + `PermissionAuditListenerTest.onPermissionExpired_*` 3개 + Testcontainers `PermissionRepositoryTest.findExpiredActiveIds_*` 4개) → PE.5 (`docs/00 §5 ADR #34` closure 마커 추가 + `docs/02 §2.6` 본문 1줄 + 신규 `§7.10.1` 9-row 정책 표 + `docs/03 §4.1` enum mirror + `docs/04 §13` 배치 표 row + `[‡‡]` footnote) → PE.6 (PR #31 fix commit 1 후 squash-merge `00d05d6` + dev-docs archive).

### 회고

- **commits**: 2 on top of `544afc9` F5 closure (worktree branch `feature/permissions-expired-cron`) → squash-merge `00d05d6` on `master`. PR #31 single, 초기 backend CI 1 fail (`PermissionRepositoryTest.findExpiredActiveIds_returnsOnlyExpiredOldestFirst` — `idx_permissions_unique` 위반: 동일 `(folder, user, "user", user)` tuple에 read/edit/admin 3 row 시도) → fix commit `4e7b3a5` (subject 3명 분리). 재실행 frontend vitest + backend junit 모두 GREEN.
- **production 파일**: 신설 3 / 수정 7.
  - 신설: `PermissionExpiredEvent.java`, `PermissionExpirationProperties.java`, `PermissionExpirationJob.java`
  - 수정: `PermissionRepository.java`, `PermissionService.java`, `AuditEventType.java`, `PermissionAuditListener.java`, `SchedulingConfig.java`, `application.yml`, `frontend/src/types/audit.ts`
- **test 파일**: 신설 2 / 수정 2. `PermissionExpirationJobTest`(6) + `PermissionServiceExpireTest`(5) + `PermissionAuditListenerTest`(+3) + `PermissionRepositoryTest`(+4 + helper). 597/597 GREEN.
- **docs sync**: `docs/00 §5 ADR #34`, `docs/02 §2.6` + 신규 `§7.10.1`, `docs/03 §4.1`, `docs/04 §13`. grep `permission.expired|permissions-expired-cron|PERMISSION_EXPIRED|permission.expire` — 4개 docs + `audit.ts` + `AuditEventType.java` 일관.

### 핵심 결정 (permissions-expired-cron 트랙, 확정)

1. **DELETE only (soft-delete 불가)** — `permissions` 테이블에 `revoked_at` 컬럼 부재. SHARE_EXPIRED는 `shares.revoked_at=NOW()` + `permissions` row delete의 2단계지만, 본 트랙은 `permissions` row 단일 DELETE. 향후 soft-delete 필요 시 별도 마이그레이션.
2. **lockById 단일 조건 lock** — SHARE의 `lockByIdAndRevokedAtIsNull`(조건부) 대비 단순. race 시(다른 cron 인스턴스 / 사용자 직접 revoke) lock-then-query miss → `ResourceNotFoundException` → job swallow. 분산락 별도 도입 불요.
3. **revoke와 expire helper 추출 거부** — KISS. lock 메서드 다름(`findById` vs `lockById`) + event 타입 다름(`PermissionRevokedEvent` vs `PermissionExpiredEvent`) → helper 추출이 가독성 ↓. SHARE 트랙에서도 동일 판단.
4. **`expirationMetadataJson` 별도 helper** — grant/revoke의 `resourceMetadataJson` 형식 보존하면서 `"trigger":"system.expiration"` 키만 추가. listener 책임 안에 응집.
5. **cron의 가치 = (a) DB cleanup, (b) audit row** — `findEffective`가 이미 `expires_at > NOW()` 필터링하므로 cron이 없어도 만료 grant는 보안 평가에서 제외됨. 따라서 cron의 보안적 효과는 0, 운영적 효과(테이블 비대 방지 + 만료 추적성)만 의미.
6. **default disabled** — SHARE 트랙과 동일 패턴. staging/prod에서 명시적으로 `app.permission.expiration.enabled=true`로 활성화.
7. **테스트 unique 위반 수정 = subject 분리** — `idx_permissions_unique=(resource_type, resource_id, subject_type, subject_id)`는 `preset`을 키에 포함하지 않음. 동일 (resource, subject) 위에 다른 preset row를 만들 수 없음. 테스트는 owner/subject1/subject2/subject3 4명 user 분리로 해결.

### 파급 영향

- **ADR #34 backlog**: SHARE_EXPIRED(2026-05-01) closure 시 잔여로 표기됐던 `permissions.expires_at` 직접 grant 만료 cron이 본 트랙으로 closed. 두 트랙 모두 audit row `metadata.trigger='system.expiration'`로 system 트리거 분별. SSE emission(`permission.expired`)은 ADR #14 인프라 milestone까지 그대로 deferred.
- **frontend**: `audit.ts` union member 1줄 추가만 — 향후 audit log UI(M12)에서 자동 인식. 별도 UI 작업 불필요.
- **운영 가이드 (`docs/04 §13`)**: 배치 작업 표에 `permission.expire` row 추가 — `app.permission.expiration.{enabled, batch-size, cron, zone}` 4 properties 명시.
- **DB/스키마**: 변경 없음 (V_ 마이그레이션 0개).

---

## 2026-05-01 — 🏁 F5 마일스톤 종료 (Frontend Folder Share UI 확장 + ShareDto wire 정합)

### 범위

F5.0 (worktree `feature/f5-frontend-folder-share-ui` from `7c179d1` F4 closure + dev-docs bootstrap `dev/active/f5-frontend-folder-share-ui/`) → F5.1 (ShareDto wire 10필드 정합 — file_id/folder_id XOR + revokedAt/revokedBy 노출 + subjectType/subjectId/preset 제거 = backend `com.ibizdrive.share.ShareDto` record와 1:1, `api.createShares` → `createFileShares`/`createFolderShares` 분리 + `postShareCreate` 헬퍼, `useShareUiStore` `target: ShareTarget` discriminator 도입, `BulkActionBar`는 `{kind:'file'}` 명시, ShareDialog는 target 기반 + 기존공유 행 표시 단순화(만료+해제만), SharesTable 컬럼 4→3 + folder/file 아이콘 분기) → F5.2 (`useCreateShare` Vars `{target, req}` discriminated 전환 + target.kind 분기 mutationFn, ShareDialog `components/files/` → `components/shares/` 이동 + folder kind 분기 활성 + kind-aware 부제/NOT_FOUND toast, `ClientFilesPage.tsx` import 경로 갱신) → F5.3 (`Breadcrumb` 우측 폴더 공유 진입점 — 비루트 + `can.SHARE` 게이트, `Breadcrumb.test.tsx` 신규 +3 케이스) → F5.4 (`docs/01 §14.4` F4→F5 확장 sync — ShareDto 10필드/매칭식/wire 부재 항목 명시) → F5.5 (PR #30 squash-merge `abb8506` + dev-docs archive).

### 회고

- **commits**: 1 on top of `6f4377f` M12 closure (worktree branch `feature/f5-frontend-folder-share-ui`) → squash-merge `abb8506` on `master`. PR #30 single, CI green (frontend vitest + backend junit 모두 SUCCESS, fix commit 0).
- **production 파일**: 수정 9 / 이동 1(2파일) / 신설 1.
  - 수정: `frontend/src/types/share.ts`, `frontend/src/stores/shareUi.ts`, `frontend/src/lib/api.ts`, `frontend/src/hooks/useCreateShare.ts`, `frontend/src/components/shares/SharesTable.tsx`, `frontend/src/components/folders/Breadcrumb.tsx`, `frontend/src/components/files/BulkActionBar.tsx`, `frontend/src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx`, `docs/01-frontend-design.md` §14.4
  - 이동: `frontend/src/components/files/ShareDialog.{tsx,test.tsx}` → `frontend/src/components/shares/ShareDialog.{tsx,test.tsx}` (소유 경계 정합 — file 전용 컴포넌트 아님)
  - 신설: `frontend/src/components/folders/Breadcrumb.test.tsx`
- **test 파일**: 수정 6 / 신설 1. wire-aligned 10필드 fixture 일괄 갱신 + folder kind 케이스 추가. 최종 494/494 GREEN.
- **docs sync**: `docs/01 §14.4` F4→F5 확장 (트리거 분기 file/folder, target discriminated, mutation 분기 라우트, ShareDto 10필드 명시, SharesTable 3컬럼 정정, A13 backlog 등록).

### 핵심 결정 (f5-frontend-folder-share-ui 트랙, 확정)

1. **ShareDto wire 진실 = backend record** — F4 시점 frontend types가 `subjectType/subjectId/preset` 가정 + `folderId/revokedAt/revokedBy` 누락 = drift. A안 채택(frontend types를 wire에 정렬, ShareDialog 기존공유 행 표시 단순화 + SharesTable preset 컬럼 제거). 복원은 A13(backend join) backlog.
2. **`createShares` → `createFileShares`/`createFolderShares` 분리** — backend 라우트 분리(POST /api/files|folders/{id}/share)와 1:1 KISS. 단일 메서드 통합 시 endpoint 분기 로직이 클라이언트로 새는 안티패턴.
3. **useCreateShare 단일 hook 유지, Vars만 discriminated** — 호출자 관점 동일 액션, `qk.shares()` 무효화도 동일. hook 두 개로 쪼개면 무효화 중복.
4. **ShareDialog 위치 이동 (`files/` → `shares/`)** — 더 이상 file 전용이 아님. 소유 경계 정합.
5. **폴더 진입점은 Breadcrumb 우측 작은 액션** — 현재 폴더 = URL `folderId`이므로 §19 원칙 1과 정합. 비루트 + `can.SHARE` 게이트. FolderTree row 우클릭 컨텍스트 메뉴는 별도 트랙(범용 폴더 액션 시스템 신설 필요).
6. **`revokedAt`/`revokedBy` 미노출** — backend가 active 행에서 항상 null. UI 가치 0. 향후 admin 화면 재사용을 위해 wire 노출은 유지하되 UI는 표시 안 함 (YAGNI).
7. **with-me revoke 미노출 유지** — F4 보수 정책 그대로 (수신자 자진 반납 사양 미정).
8. **루트 폴더 공유 진입점 차단** — `breadcrumb.length > 1`로 게이트 (정책: 시스템 루트 = 공유 대상 아님).

### 파급 영향

- **frontend backlog**: 폴더 다중 선택 BulkActionBar 공유 액션(folder 다중 선택 자체 부재 → 함께 트랙 필요), FolderTree row 우클릭 컨텍스트 메뉴(범용 액션 시스템), subject picker UI(user/department/role 목록 endpoint 부재).
- **backend backlog**: **A13 (가칭) — `ShareDto` ↔ `permissions` join** (`subject_type`/`subject_id`/`preset`을 ShareDto에 join → ShareDialog 기존공유 행 풍부화 + SharesTable preset 컬럼 복원). `ShareControllerTest` wire JSON 필드 검증 보강(현 갭).
- **DB/스키마**: 변경 없음.

---

## 2026-05-01 — 🏁 M12 closure (Audit Log UI — A2.6 wired status 표기)

### 범위

M12 트랙(2026-04-25 mock 도입, `/admin/audit/logs` Filters/Table/Pagination/CSV)은 A2.6(2026-04-26)에서 backend `GET /api/admin/audit` 실연결로 교체되며 사실상 closed. 단 (1) `page.tsx:14-20` docblock은 "M12 mock + 백엔드 연결 없음" stale 문구를 그대로 유지 (2) `docs/04 §7`은 모든 항목이 미체크 상태로 mock-time 잔존 — 이 두 표기 정합만 누락. 본 closure는 (1) docblock 정정 + (2) docs §7 status 표기 갱신으로 트랙 종료.

### 회고

- **commits**: 1 on top of `17eac0e` SHARE_EXPIRED closure (worktree branch `feature/m12-audit-ui-closure`).
- **production 파일**: 수정 1 — `frontend/src/app/admin/audit/logs/page.tsx` docblock(M12 mock → M12 A2.6 wired + CSV export 동작/v1.x deferred 명시).
- **test 파일**: 미터치(JSDoc 변경만, 회귀 0).
- **docs sync**:
  - `docs/04 §7` Status quote 추가(M12 wired 2026-05-01 closure marker)
  - 7.1: `dateFrom`/`dateTo`/`actorId`/`eventType` 4 필터 활성 표기 + `대상 리소스`/`IP 주소`는 v1.x deferred(frontend filter + backend query param 미수용)
  - 7.2: CSV export 활성 + server-side full-result 스트리밍 / `audit.exported` runtime emission / JSON download 모두 v1.x deferred 명시
  - 7.3: before/after diff + 관련 이벤트 연결 v1.x deferred 명시
- **A2.6 wiring 사실**: `api.getAuditLogs`가 `fetch('/api/admin/audit?...', { credentials: 'include' })` 직접 호출(`frontend/src/lib/api.ts:493-553`). M12 mock 분기 + 60-row generator는 A2.6에서 완전 제거됨.

### 핵심 결정 (m12-audit-ui-closure 트랙, 확정)

1. **closure-only 트랙** — backend/frontend 코드 변경 0. 표기 정합만 처리. 새 기능 추가 거부(YAGNI).
2. **server export + `audit.exported` runtime emission은 v1.x deferred 유지** — current-page CSV는 운영 충분(필터 좁힘 + 페이지네이션). 전체 export는 별도 backend endpoint(streaming + audit emission) 도입 시점.
3. **`대상 리소스`/`IP 주소` 필터는 v1.x deferred** — `AuditLogFilters` 타입 + backend query param 양쪽 추가 필요. 현재 운영 필터(시간/행위자/이벤트)로 충분.
4. **`page.tsx` JSDoc 외 코드 미변경** — 실제 구현은 이미 GREEN(484+ frontend tests). closure는 의미 표기만.

### 파급 영향

- **frontend backlog**: 대상 리소스/IP 필터 + JSON download + 상세 뷰 diff(v1.x).
- **backend backlog**: server-side audit export endpoint + `audit.exported` emission(v1.x). docs/03 §4.1 enum에 `audit.exported`는 정의되어 있으므로 emission만 활성화하면 됨.
- **DB/스키마**: 변경 없음.

---

## 2026-05-01 — 🏁 SHARE_EXPIRED cron 트랙 종료 (ADR #34 backlog closure)

### 범위

SE.0 (worktree `feature/share-expired-cron` from `7c179d1` F4 closure + dev-docs bootstrap `dev/active/share-expired-cron/`) → SE.1 (`ShareCommandService.expireShare` 신규 + `lockAndCascadeRevoke` + `Snapshot` record helper 추출 + `revokeShare` helper 사용형 재작성 + `ShareExpiredEvent` record + `ShareAuditListener.onShareExpired`) → SE.2 (`ShareRepository.findExpiredActiveIds(Instant, Pageable)` JPQL + `ShareExpirationProperties` + `ShareExpirationJob @Scheduled(cron, zone) @ConditionalOnProperty` + `application.yml` `app.share.expiration.*` block + `SchedulingConfig` 다중 잡 진입점화 — `@ConditionalOnProperty(app.purge.enabled)` 제거) → SE.3 (`ShareCommandServiceTest` +4 / `ShareAuditListenerTest` +3 / `ShareExpirationJobTest` 신규) → SE.4 (00 §5 ADR #34 closure marker + 02 §7.9.1 만료 cron 정책 표 신규 + 03 §4.1 `share.expired` 활성화 마커 + 04 §13 `share.expire` 행 정정/footnote) → SE.5 (PR #28 squash-merge `bda5158` + dev-docs archive).

### 회고

- **commits**: 2 on top of `7c179d1` F4 close (worktree branch `feature/share-expired-cron`) → squash-merge `bda5158` on `master`. PR #28 single, CI green (backend junit 3m17s + frontend vitest 1m32s 모두 SUCCESS, fix commit 1회 — `HardPurgeJobDisabledIntegrationTest` SchedulingConfig 단언 정합).
- **production 파일**: 수정 5 / 신설 3.
  - 수정: `ShareCommandService.java`(expireShare + lockAndCascadeRevoke + Snapshot + revokeShare 재작성), `ShareAuditListener.java`(onShareExpired 추가), `ShareRepository.java`(findExpiredActiveIds), `SchedulingConfig.java`(다중 잡 진입점화), `application.yml`(app.share.expiration block).
  - 신설: `ShareExpiredEvent.java`(record, actorId 부재), `ShareExpirationProperties.java`(`@ConfigurationProperties("app.share.expiration")` record + 기본값 sanitization), `ShareExpirationJob.java`(`@Scheduled` + per-row 트랜잭션 + 실패 격리).
- **test 파일**: 수정 3 / 신설 1 — `ShareCommandServiceTest.java`(+4 expireShare 정상/race/folder/null guard), `ShareAuditListenerTest.java`(+3 onShareExpired 시스템 메타/folder variant/audit failure swallow), `HardPurgeJobDisabledIntegrationTest.java`(SchedulingConfig 빈 단언 → 존재 단언 정정), `ShareExpirationJobTest.java`(빈/N건/per-row 실패 격리/race ResourceNotFoundException/scan 실패 swallow/batchSize 전달). 회귀 0.
- **docs sync**: 00 §5 ADR #34 본문 closure 표기, 02 §7.9 인용문 수정 + §7.9.1 신규 정책 표(빈 등록/스케줄/한 회 한도/처리 단위/만료 동작/이벤트/audit row/다중 인스턴스/실패 격리/로그 9행), 03 §4.1 enum block에 `share.expired` 활성화 marker(actor_id=NULL/trigger 메모), 04 §13 `share.expire` 행을 default 5분/`share-expired-cron` 트랙으로 정정 + `[‡]` footnote(properties + 단위 처리 + audit + 다중 인스턴스 안전).
- **frontend 미터치**: `share.expired` audit row는 M12(Audit Log UI)가 자연 노출 — 별도 UI 변경 불요.

### 핵심 결정 (share-expired-cron 트랙, 확정)

1. **별도 `ShareExpiredEvent` record** — `ShareRevokedEvent`에 `bySystem` flag 추가 대안 거부. listener 분기 단순화 + payload 시그니처 의미 명료(시스템 트리거는 `actorId` 부재 = compile-time 보증). file/folder XOR invariant compact constructor 동형.
2. **`SchedulingConfig` 다중 잡 진입점화** — 기존 `@ConditionalOnProperty(app.purge.enabled)` gate 제거. `@EnableScheduling`은 무조건 활성, 잡-개별 `@ConditionalOnProperty`가 활성화 담당. 잡 빈 0개 시 single-thread scheduler는 idle(비용 무시).
3. **공통 helper 추출** — `lockAndCascadeRevoke(shareId, revokedBy)` + `Snapshot` private record. `revokeShare`/`expireShare` 두 메서드는 이 helper 호출 + 각자 다른 event publish만 담당 — DRY + 향후 변형(예: 트래시 만료 등) 추가 시 helper 재사용.
4. **`metadata.trigger='system.expiration'`** — audit consumer가 사용자 revoke(`actor_id` 존재)와 자동 만료(`actor_id=NULL` + `trigger='system.expiration'`)를 구분 가능하도록 보존. 추후 다른 시스템 트리거(예: `legal_hold.released_share_revoke`) 도입 시 trigger value만 분기.
5. **다중 인스턴스 안전성 = V6 row-level pessimistic lock** — 분산락(SchedulerLock 등) 도입 거부. 두 인스턴스가 동일 `shareId`로 동시 호출 시 한쪽만 lock 통과, 다른 쪽은 `revoked_at IS NOT NULL`로 lock query miss → `ResourceNotFoundException` swallow. 운영 단순화 + 인프라 추가 0.
6. **per-row 실패 격리** — 단일 row 예외는 ERROR 로그 + 다음 row 진행. 배치 전체 차단 없음. `ResourceNotFoundException`(사용자 동시 revoke race)도 같은 경로로 swallow.
7. **운영 기본 비활성** — `app.share.expiration.enabled=false`(`HardPurgeJob` 동형). staging/prod에서 명시적 활성화 후 투입. 실수로 dev 환경에서 share 자동 회수 방지.
8. **`permissions.expires_at`(직접 grant) 만료는 별도 트랙** — A10 scope에 직접 만료 케이스 부재. ShareCommand과 PermissionCommand는 책임 분리(SRP) — share 만료가 permission 만료를 강제하지 않음. 직접 grant 만료 트랙 도입 시 `PermissionCommandService.expirePermission` + 별도 cron 추가.

### 파급 영향

- **frontend**: 미터치. M12 Audit Log UI 트랙이 `share.expired` row 노출 책임 — 단순 enum mirror만 확인하면 됨(이미 03 §4.1 enum에 정의 존재).
- **backend backlog**: `permissions.expires_at` 만료 cron 트랙 분리(필요 시점에 별도 ADR). SSE `ShareExpiredEvent` emission은 ADR #14 인프라 milestone까지 그대로 deferred(audit-only).
- **DB/스키마**: 변경 없음. V6 schema 그대로 사용(`shares.expires_at`, `shares.revoked_at`, `shares.revoked_by`).
- **운영**: staging/prod에서 `app.share.expiration.enabled=true` + 필요 시 cron/zone/batch-size override. 단일 인스턴스 가정 해제(분산락 불요).

### 다음 세션 컨텍스트

- M12 Audit Log UI 트랙은 closure-only로 별도 dev-docs(`dev/active/m12-audit-ui-closure/`) 보존 중 — backend `GET /api/admin/audit` + frontend `api.getAuditLogs` 이미 GREEN, stale docblock 정정 + docs sync + 스모크 테스트만 남음.

---

## 2026-05-01 — 🏁 F4 마일스톤 종료 (Frontend Shares UI 실연결)

### 범위

F4.0 (worktree `feature/f4-frontend-shares-ui` from `e0957e5` M9 closure + dev-docs bootstrap `dev/active/f4-frontend-shares-ui/`) → F4.1 (`types/share.ts` + `qk.shares()/sharesByMe()/sharesWithMe()` + `invalidations.afterShareCreate/afterShareRevoke` 단일 prefix + 6 tests) → F4.2 (`api.{createShares,revokeShare,listSharesByMe,listSharesWithMe}` 4 메서드 + 19 wire-level 테스트) → F4.3 (`useCreateShare`/`useRevokeShare`/`useSharesByMe`/`useSharesWithMe` 4 hook + 9 테스트) → F4.4 (`ShareDialog` 전면 재작성 + `/shares` 페이지 + `SharesTable` + `SharesLink` + `(explorer)/layout.tsx` mount + docs/01 §6.1/§6.2/§14.4/§17 sync + 15 테스트) → F4.5 (PR #27 squash-merge `d6ab9aa` + dev-docs archive).

### 회고

- **commits**: 5 on top of `e0957e5` M9 close (worktree branch `feature/f4-frontend-shares-ui`) → squash-merge `d6ab9aa` on `master`. PR #27 single, CI green (backend junit 33s + frontend vitest 1m35s 모두 SUCCESS).
- **production 파일**: 수정 4 / 신설 8.
  - 수정: `lib/queryKeys.ts`(qk.shares + invalidations), `lib/api.ts`(4 메서드 + fetchSharePage helper), `app/(explorer)/layout.tsx`(SharesLink mount, TrashLink 위), `components/files/ShareDialog.tsx`(mock placeholder → 실연결 전면 재작성).
  - 신설: `types/share.ts`, `hooks/useCreateShare.ts`, `hooks/useRevokeShare.ts`, `hooks/useSharesByMe.ts`, `hooks/useSharesWithMe.ts`, `components/shares/SharesLink.tsx`, `components/shares/SharesTable.tsx`, `app/(explorer)/shares/page.tsx`+`ClientSharesPage.tsx`.
- **test 파일**: 수정 1 / 신설 7 — `ShareDialog.test.tsx`(전면 재작성 8건), `api.shares.test.ts`(19), 4 hook 테스트(2/2/3/2), `SharesLink.test.tsx`(2), `SharesTable.test.tsx`(5). 합계 +49 GREEN. 회귀 484/484.
- **build /shares ○ Static** — useSearchParams 호출 부재 + StatusBar Suspense 기존 wrap(M11)으로 SSG 회귀 0. typecheck/lint clean.
- **docs/01 sync**: §6.1 `qk.shares()/sharesByMe()/sharesWithMe()` 등재, §6.2 invalidation 매트릭스 +2행(공유 생성/해제), §14.4 ShareDialog backlink('everyone' MVP + preset 4값 + datetime-local + canRevoke 위임), §17 `/shares` 라우팅 등재.
- **backend 미터치**: A10 share endpoint 4종이 이미 GREEN — F4는 frontend 단독 트랙.

### 핵심 결정 (F4 트랙, 확정)

1. **subject = 'everyone' MVP only** — frontend user/department/role 목록 endpoint 부재(A-future 백로그). ShareDialog는 'everyone' 라벨 고정. subject picker는 후속 트랙에서 typeahead+chip로 도입.
2. **`/shares` 별도 페이지** — `/trash` 미러 패턴. `/files/[...parts]` 뷰에 share filter 통합 대안은 거부 — `/files`는 storage view, `/shares`는 access view로 분리. Sidebar `SharesLink`는 **TrashLink 위**(positive nav 위, destructive nav 아래).
3. **revoke = backend 위임** — `canRevoke`(sharedBy==me ‖ ADMIN)는 backend 진실의 출처. ShareDialog는 by-me share만 노출(자기 share=revoke 가능 자동 보장). `/shares`(with-me)는 revoke 버튼 미노출(보수 정책 — 수신자가 자기 권한 자진 반납은 사양 결정 후).
4. **preset 4값** — `read | upload | edit | admin` (ADR #34, V5 CHECK는 SHARE 미지원이라 wire에서 제외). 추후 SHARE 도입 시 backend 먼저 V_ migration + ADR 갱신.
5. **invalidations 단일 prefix** — `afterShareCreate/Revoke` 모두 `qk.shares()` 1회 → by-me/with-me 동시 갱신. KISS(co-located in queryKeys.ts, 별도 파일 미생성).
6. **expiresAt 변환** — HTML5 `datetime-local` 입력 → `new Date(v).toISOString()`(NaN check + 한국어 toast). timezone은 브라우저 로컬 → ISO 8601 UTC.
7. **에러 envelope code 분기** — `PERMISSION_CONFLICT`(이미 같은 대상…), `PERMISSION_DENIED`(권한 없음), `NOT_FOUND`(파일 없음), 그 외 폴백. backend `docs/02 §8` 에러 코드 계약 1:1 매핑.

### 파급 영향

- **frontend backlog**: subject picker UI(F-future, A-future 의존). with-me revoke(수신자 자진 반납) UI는 backend 사양 결정 후. folder share UI는 별도 트랙(A12 closure progress entry 참조).
- **backend**: 미터치. A10/A11/A12 endpoint 4종이 그대로 이번 frontend의 backbone.
- **DB/스키마**: 변경 없음.

---

## 2026-05-01 — 🏁 A12 마일스톤 종료 (Backend folder share endpoint)

### 범위

A12.0 (worktree `feature/a12-folder-shares` from `e0957e5` M9 closure + dev-docs bootstrap `dev/active/a12-folder-shares-endpoint/`) → A12.1 (`POST /api/folders/{folderId}/share` endpoint + `ShareCommandService.createFolderShares`(FolderRepository 주입) + `ShareCreatedEvent`/`ShareRevokedEvent` `folderId` XOR invariant + `ShareAuditListener` `nodeKey` 분기 + 테스트 갱신/추가) → A12.2 (`ShareQueryServiceTest` folder share 자연 노출 회귀 — by-me/with-me/mixed XOR per-row) → A12.3 (docs/00 ADR #34 활성화 + docs/02 §2.7/§7.9 folder POST + docs/03 §3 backlink) → A12.4 (PR #26 squash-merge `e076a1b` + dev-docs archive).

### 회고

- **commits**: 3 on top of `e0957e5` M9 close (worktree branch `feature/a12-folder-shares`) → squash-merge `e076a1b` on `master`. PR #26 single, CI green (backend junit + frontend vitest 모두 SUCCESS — 약 4분).
- **production 파일**: 수정 5 / 신설 0.
  - 수정: `share/ShareController.java`(folder POST 라우트), `share/ShareCommandService.java`(`createFolderShares` 추가, `revokeShare` snapshot folderId 캡처), `share/ShareCreatedEvent.java`/`ShareRevokedEvent.java`(folderId 필드 + XOR 컴팩트 컨스트럭터), `audit/ShareAuditListener.java`(nodeKey 분기로 file_id/folder_id JSON 키 출현).
- **test 파일**: 수정 3 / 신설 0 — `ShareAuditListenerTest`(기존 6개 이벤트 호출 folderId=null 인자 + folder variant 2건), `ShareCommandServiceTest`(@Mock FolderRepository + createFolderShares 7건 + revokeShare folder 변형 1건), `ShareControllerTest`(createFolderShare 4건), `ShareQueryServiceTest`(folder 자연 노출 3건). 합계 +20 GREEN.
- **schema 변경 0**: V6 `shares` 테이블 `file_id`/`folder_id` XOR CHECK가 이미 양립 — 컬럼/CHECK 추가 없음. ADR #34 backlog 항목(folder share endpoint 미도입) 명시적 closure.
- **backend 회귀 0**: 전체 `./gradlew test` GREEN. A10 file path 테스트 모두 통과.
- **frontend 미터치**: A12 backend stack only — frontend folder share UI는 docs/01 §6/§14 미명시 → 별도 backlog.

### 핵심 결정 (A12 트랙, 확정)

1. **`createFolderShares` 별도 메서드** — `createShares`(file)와 통합 abstraction(`createForNode(nodeType, nodeId, ...)`) 대안은 거부. KISS — A10 시그니처 보존 + 호출부 명시성 우선. 공통 검증(parsePreset / validateMessage / expiresAt 미래)은 private static helper로 재사용.
2. **`Share*Event`에 `folderId` 필드 추가 + XOR 컴팩트 컨스트럭터** — `nodeId` + `nodeType` 통합 대안은 거부. V6 `shares` 테이블이 이미 file_id/folder_id 양립이므로 event payload도 1:1 정합. 컴팩트 컨스트럭터 `(fileId == null) == (folderId == null)` invariant이 잘못된 발행을 차단.
3. **`ShareAuditListener` `nodeKey` 분기로 `file_id`/`folder_id` JSON 키 분리** — 통합 키(`node_id` + `node_type`) 대안은 거부. 기존 audit row 호환 유지 — A10 시점 audit_log row가 `file_id` 키를 사용하므로 query/조회 backward compat.
4. **`ShareQueryService` SQL 분기 없음** — by-me/with-me/DELETE는 file/folder share 모두 자연 노출. repository 쿼리는 `shared_by` 또는 `subject_id` 매칭만 — `file_id IS NOT NULL` 같은 분기 필터 부재. A12.2가 이 가정을 회귀 테스트로 박제.
5. **`@PreAuthorize("hasPermission(#folderId, 'folder', 'SHARE')")`** — file 변형(`'file'`)과 동형. PermissionEvaluator가 `nodeType` 분기 자동 처리(A4 evaluator).

### 파급 영향

- **frontend**: 미터치. folder share UI는 별도 트랙(`m_-folder-share-ui` 가칭, docs/01 §6/§14 추가 후 진행).
- **backend**: A10 `permission.granted` audit + `share.created` audit 이중 발행 패턴 그대로. `permission_id`로 grant 추적 가능.
- **DB**: V6 schema 변경 없음. 향후 `SHARE_EXPIRED` cron + SSE emission도 trigger 추가 없이 application 레벨에서 가능.

---

## 2026-05-01 — 🏁 M9 마일스톤 종료 (Frontend 휴지통 통합)

### 범위

M9.0 (`qk.trash()` + prep 키 + `invalidations.afterDelete/afterRestore/afterPurge` — filesListPrefix + trash + folderTree + search 4건 일괄) → M9.1 (`types/trash.ts` + `api.{getTrash,restoreFile,restoreFolder,purgeTrashItem,softDeleteFile,softDeleteFolder}` 실 backend fetch + `useDeleteBulk` Mock 제거 → 시그니처 `ids: string[]` → `items: {id,type}[]` 마이그) → M9.2 (`useTrashList`/`useRestoreItem`/`usePurgeTrashItem` hooks + 14 GREEN) → M9.3 (`/trash` 페이지 + `TrashTable`/`TrashRowActions`/`TrashLink` + `(explorer)/layout.tsx` Sidebar mount + `findFolderPath` 유틸 + 9 GREEN) → M9.4 (BulkActionBar `useDeleteBulk` onSuccess에 sonner `action: { label: '되돌리기', duration: 5000 }` + `undoDelete` 헬퍼 — type 분기 `restoreFile/restoreFolder` Promise.all + RESTORE_CONFLICT 분기 + 4 GREEN) → 중간 게이트(rebase onto master + PR #16 mock trash hooks orphan 정리 + M14 권한 정합 `admin → PURGE`) → M9.5 (PR #22 squash-merge `1927c56` + dev-docs archive).

### 회고

- **commits**: 10 on top of A10 close `24a78b2` (worktree branch `feature/m9-frontend-trash`, 중간에 origin/master 9 commits 흡수 — PR #16/F1.1/A9/A10) → 최종 rebase onto F2 close `69ab2e6` → squash-merge `1927c56` on `master`. PR #22 single, CI green (backend junit 36s + frontend vitest 1m25s 모두 SUCCESS).
- **production 파일**: 추가 8 / 수정 3.
  - 추가: `app/(explorer)/trash/{page,ClientTrashPage}.tsx`, `components/trash/{TrashTable,TrashRowActions,TrashLink}.tsx`, `hooks/{useTrashList,useRestoreItem,usePurgeTrashItem}.ts`, `types/trash.ts`, `lib/folderTreeUtils.ts`(findFolderPath).
  - 수정: `lib/queryKeys.ts`(qk.trash + 무효화 3건), `lib/api.ts`(trash + soft-delete 6 메서드 + `buildApiError` helper), `components/files/BulkActionBar.tsx`(Undo toast wiring), `app/(explorer)/layout.tsx`(TrashLink mount).
- **test 파일**: 신설 5 / 수정 2 — 42건 GREEN 신설(api.trash 15 / hooks 14 / TrashTable 7 / TrashLink 2 / BulkActionBar Undo 4). `useDeleteBulk.test.ts` softDelete*로 mock 교체.
- **frontend 회귀 0**: 전체 `pnpm test` 47 files / 439 tests GREEN. `pnpm typecheck` + `pnpm lint` clean. `pnpm build` GREEN(8/8 SSG) — pre-existing `/trash` Suspense fail은 PR #24(`ed89353`)에서 별도 fix 후 본 트랙 머지.

### 핵심 결정 (M9 트랙, 확정)

1. **`useDeleteBulk` 시그니처 마이그 (`ids[]` → `items[{id,type}]`)** — backend가 file/folder 분기 endpoint(`DELETE /api/{files,folders}/:id`)이라 호출부에서 type 동봉 필요. cache miss 시 `'file'` 폴백 — 404 response 시 onError에서 selection 복원.
2. **무효화 매트릭스 4건 일괄** — soft-delete/restore 모두 `filesListPrefix + trash + folderTree + search` 한 번에. folderTree 포함 이유: folder cascade restore/soft-delete 시 사이드바 stale 방지.
3. **`originalParentId` → folderTree path 해석 (N+1 회피)** — `useFolderTree()` 캐시 + `findFolderPath` 유틸로 해석. 부모도 trashed면 "원위치 폴더 삭제됨" 폴백. backend가 originalPath를 응답에 포함하도록 변경 시 endpoint patch 필요.
4. **PURGE는 M14 권한 hook의 ADMIN-only flag** — `usePermission().PURGE` (docs/03 §3.2). 초기엔 stub `usePermission().admin` 불리언이었으나 PR #16(M14) 머지 직후 `admin → PURGE` 일괄 정합. 의미적으로도 정확(영구 삭제 권한).
5. **Undo toast 5초 단일 sonner action** — `toast.success(msg, { duration:5000, action:{ label:'되돌리기', onClick } })`. 5초 시한 + 다중 action UX는 별도 디자인.
6. **RESTORE_CONFLICT 409 — toast.error 폴백** — MVP는 사용자가 폴더에서 충돌 항목 정리 후 재시도. ConflictDialog는 v1.x 보류.
7. **Bulk purge 미구현 (ADR #32)** — UI에서 "전체 비우기" 버튼 노출 안 함. backend `DELETE /api/trash` 트랙 별도화.
8. **`pnpm-lock.yaml` 미커밋** — F2와 동일 정책. 프로젝트는 `package-lock.json` 추적, pnpm 워크트리 lockfile은 로컬 artifact.
9. **PR #16 mock trash hooks orphan 정리** — PR #16이 mock 기반 `useRestoreBulk`/`usePurgeBulk`/`useTrashHooks.test.tsx`를 추가했으나 본 트랙이 real-backend `useRestoreItem`/`usePurgeTrashItem`/`useTrashList`로 대체했으므로 `488ca5a`에서 orphan 삭제. 트랙 충돌 시 mock 잔존 hooks는 squash 후 즉시 제거가 표준.

### 다음 트랙 후보

- **F4 — Frontend Shares UI** (A10 share endpoint 노출). docs/01 §6/§14 신설 후 진입.
- **F3 — `useStorageQuota` 실연결** — backend quota API 미신설 → 백엔드 트랙 선행 필요.
- **A12 — folder 공유** (A10 ADR #34 backlog).
- **A11 후속 — `SHARE_EXPIRED` 자동 전환 배치**.
- **M10 — SSE/WebSocket 실시간 동기화** (휴지통 다른 탭 변경 push 무효화 — docs/01 §15).

---

## 2026-05-01 — 🏁 F2 마일스톤 종료 (Frontend usePermission 실연결)

### 범위

F2.0 (dev-docs bootstrap — `dev/active/f2-frontend-permissions-realconnect/` plan/context/tasks 3 파일, A9→F1 직렬화 패턴 미러) → F2.1 (`api.getEffectivePermissions(nodeId?)` 본체 mock(80ms + admin preset 8 권한 하드코딩) → `fetch('/api/me/effective-permissions')` + inline `{permissions}.permissions` 매핑 + 비-OK 시 `status` 필드 가진 Error throw + `api.permissions.test.ts` fetch wire 9 케이스 RED→GREEN) → F2.2 (PR #25 squash-merge `76dda90` + dev-docs archive).

### 회고

- **commits**: 2 on top of A11 close `097e904` (worktree branch `feature/f2-frontend-permissions-realconnect`) → squash-merge `76dda90` on `master`. PR #25 single, CI green (backend junit 36s + frontend vitest 1m27s 모두 SUCCESS).
- **production 파일**: 1 수정 — `frontend/src/lib/api.ts` `getEffectivePermissions` 본체 교체 (14줄 → 18줄). 시그니처 `(nodeId?: string) => Promise<Permission[]>` 무수정 — 호출부(`usePermission.ts`, `BulkActionBar.tsx` consumer 등) drift 0.
- **test 파일**: 1 갱신 — `api.permissions.test.ts` 전면 재작성(fetch wire 계약 + 응답 매핑 + 401/404/5xx + qk 키 9건). 이전 mock 한정 2건 → fetch wire 9건.
- **frontend 회귀 0**: 전체 `pnpm test` 427/427 GREEN (55 suites). `pnpm typecheck` + `pnpm lint` clean.

### 핵심 결정 (F2 트랙, 확정)

1. **호출부 시그니처 무수정** (drift 0) — `usePermission` / `PermissionFlags` Record 변환 / consumer 전부 무수정. F1 패턴 그대로.
2. **매핑 inline (KISS)** — 별도 `permissionsMapper.ts` 파일 신설 회피. `api.ts` 내부 단일 라인(`(await res.json()).permissions`) 매핑.
3. **에러 envelope 글로벌 위임** — fetch 함수는 `!res.ok` 시 `status` 필드 가진 Error throw만. 401/403 화면 분기는 글로벌 `QueryCache.onError`에 위임 (F1 패턴 동일). 본 트랙에서 envelope handler 추가 변경 없음.
4. **AbortSignal 미전파** — 현 호출부(`queryFn: () => api.getEffectivePermissions(nodeId)`)가 signal 미전달이라 api 시그니처에 `options.signal` 추가 안 함. 미래 hook 갱신 시 backward-compat으로 추가 가능.
5. **PURGE 정책 동일** — backend `IbizDrivePermissionEvaluator.resolveAll`이 PURGE를 Preset 미포함 사유로 skip. 이전 mock도 PURGE 제외였으므로 frontend 동작 변화 없음.
6. **`pnpm-lock.yaml` 미커밋** — 프로젝트는 `package-lock.json` 추적(npm). 워크트리 셋업 시 `pnpm install`이 생성한 lockfile은 로컬 artifact로 untracked 유지.
7. **A9→F1 직렬화 패턴 재확인** — backend endpoint(A11) → frontend swap(F2) 직렬화. F1.1처럼 단일 mock body 교체 + 호출부 drift 0이 다음 mock→fetch swap의 표준.

### 다음 트랙 후보

- **F4 — Frontend Shares UI** (A10 share endpoint 노출). docs/01 §6/§14 신설 필요 → 설계 추가 후 진입.
- **A12 — folder 공유** (A10 ADR #34 backlog).
- **A11 후속 — `SHARE_EXPIRED` 자동 전환 배치** (cron + `expires_at` 도과 row → `revoked_at` set + audit emit).
- **F3 — useStorageQuota 실연결** (`api.getStorageQuota` mock → 실제 endpoint, 단 backend quota API 미신설). 백엔드 트랙 선행 필요.

---

## 2026-05-01 — 🏁 A11 마일스톤 종료 (Effective Permissions Endpoint Backend)

### 범위

A11.0 (dev-docs bootstrap — `dev/active/a11-effective-permissions-endpoint/` plan/context/tasks 3파일, F2 트랙 분리 결정 — 백엔드 endpoint 부재 발견 후 A11→F2 직렬화) → A11.1 (`IbizDrivePermissionEvaluator.resolveAll(user, resourceType, resourceId): Set<Permission>` 추가, role∪resource grant 합산 + ADMIN early return + role-already-granted skip + PURGE skip — TDD 9 케이스 RED→GREEN) → A11.2 (`PermissionController.myEffectivePermissions(@RequestParam UUID nodeId)` 신설, `GET /api/me/effective-permissions` `@PreAuthorize("isAuthenticated()")` + folder/file 양 테이블 lookup + 404/401 envelope — TDD 5 케이스 RED→GREEN) → A11.3 (docs/02 §7.10 응답 schema 본문 보강 + 에러 코드 `404` → `400, 401, 404` 정정) → A11.4 (PR #23 squash-merge `13b8c45` + dev-docs archive).

### 회고

- **commits**: 4 on top of A10 close `3b6906a` (worktree branch `feature/a11-effective-permissions-endpoint`) → squash-merge `13b8c45` on `master`. PR #23 single, CI green (backend junit 3m16s + frontend vitest 1m40s 모두 SUCCESS).
- **production 파일**: 2 수정 — `permission/IbizDrivePermissionEvaluator.java` `resolveAll` 메서드 추가(EnumSet 도입), `permission/PermissionController.java` `myEffectivePermissions` endpoint + 4-arg 생성자(evaluator 주입). 기존 grant/revoke endpoint 무수정.
- **test 파일**: 2 갱신 — `IbizDrivePermissionEvaluatorTest` 9 케이스(`resolveAll` null/ADMIN/AUDITOR/MEMBER × role-only/resource-level), `PermissionControllerTest` 5 케이스(noNodeId MEMBER/ADMIN, folder/file lookup, both-missing 404).
- **A1~A10 회귀 0**: 전체 백엔드 GREEN. F2 frontend 트랙은 별도 PR로 분리(다음 트랙).
- **F2 분리 결정**: 원래 사용자 요청은 F2 (frontend mock→fetch swap)였으나, 사전조사에서 `GET /api/me/effective-permissions` 백엔드 endpoint 부재 발견 → A11 백엔드 트랙으로 분리 후 F2 frontend 트랙 후속화. A9→F1 패턴과 동일한 직렬화.

### 핵심 결정 (A11 트랙, 확정)

1. **`resolveAll` 위치는 evaluator** — `PermissionService`에 두면 `PermissionResolver` 의존 추가로 순환 위험. evaluator는 이미 양쪽 의존이라 자연스러움. service는 ROLE 단계 진실의 출처로 유지.
2. **9× CTE 호출 최적화** — role이 이미 grant한 권한 + `PURGE`(Preset 미포함, docs/03 line 331~334) 는 resolver 미호출. ADMIN=0 / AUDITOR=7 / MEMBER=8 호출.
3. **node 존재 검증 — folder 우선 short-circuit** — `folderRepository.findByIdAndDeletedAtIsNull` 먼저, 부재 시 `fileRepository`. 양 테이블 모두 부재 → 404. V5 별도 시퀀스로 UUID 충돌은 운용상 불가능.
4. **401/400은 Spring 기본에 위임** — `@PreAuthorize("isAuthenticated()")` 미인증 401, `@RequestParam UUID` 변환 실패 400. KISS — `GlobalExceptionHandler` 추가 분기 회피.
5. **응답 정렬 = Permission enum natural order** — `set.stream().sorted().toList()` 단일 라인. 프론트는 set 비교만 해도 결정적.
6. **`PermissionDenyContext` 미기록** — read-only 정보 조회는 deny envelope 미생성. evaluator `hasPermission` 경로와 분리.
7. **frontend 시그니처 contract 확정** — `api.getEffectivePermissions(nodeId?: string): Promise<Permission[]>`. F2에서 mock body를 fetch+inline mapping으로 교체만 하면 호출부(`usePermission.ts`) drift 0.

### 다음 트랙 후보

- **F2 — useEffectivePermissions 실연결** (즉시 진입 가능). `frontend/src/lib/api.ts` `getEffectivePermissions` mock body → `fetch('/api/me/effective-permissions?nodeId=...')` 교체. `usePermission.ts` 무수정. F1 패턴(PR #20) 미러.
- **F4 — Frontend Shares UI** (A10 share endpoint 노출). 별도 트랙.
- **A12 — folder 공유** (A10 ADR #34 backlog).

---

## 2026-05-01 — 🏁 A10 마일스톤 종료 (Shares Endpoint Backend)

### 범위

A10.0 (ADR #34 신설 + docs/02 §2.7 `shares` 테이블 SQL에 `expires_at` 추가 + §7.9 spec 4 endpoints 정합 + docs/03 §3 backlink + Preset 4값 V5 check 일관) → A10.1 (V6 마이그레이션 `shares` 테이블 + `Share` entity + `ShareRepository` + Testcontainers V6MigrationIT 1건) → A10.2 (`ShareDto`/`ShareCreateRequest` + `ShareCommandService.createShares` 1 request → N subjects) → A10.3 (`ShareCommandService.revokeShare` soft-revoke + `canRevoke` SpEL + `ShareAuditListener` `share.created`/`share.revoked` emit — **audit 첫 활성화**) → A10.4 (`ShareQueryService` by-me/with-me + `ShareCursor` `{createdAt}|{id}` base64) → A10.5 (`ShareController` 4 endpoints + `@PreAuthorize` + 400/403/404 envelope + Mockito 통합 테스트) → A10.6 (PR #21 squash-merge `24a78b2` + dev-docs archive).

### 회고

- **commits**: 7 on top of F1 close `9875fe9` (worktree branch `feature/a10-shares`) → squash-merge `24a78b2` on `master`. PR #21 single, CI green (backend junit 3m7s + frontend vitest 1m33s 모두 SUCCESS).
- **production 파일**: 12 신설 — `share/Share.java`, `share/ShareCommandService.java`, `share/ShareController.java`, `share/ShareCreateRequest.java`, `share/ShareCreatedEvent.java`, `share/ShareCursor.java`, `share/ShareDto.java`, `share/SharePage.java`, `share/ShareQueryService.java`, `share/ShareRepository.java`, `share/ShareRevokedEvent.java`, `audit/ShareAuditListener.java` + V6 마이그레이션(`db/migration/V6__shares.sql`).
- **test 파일**: 6 신설 — `ShareCommandServiceTest`, `ShareControllerTest`, `ShareCursorTest`, `ShareQueryServiceTest`, `ShareAuditListenerTest`, `V6MigrationIT` (Testcontainers).
- **ADR 신설**: #34 — Shares endpoint 채택. file 한정(folder 공유 backlog), subject 4종(user/department/role/everyone, MVP 후처리 user 1차) + ADR #28 preset 4값 정합(V5 `permissions_preset_check`).
- **Audit 첫 활성화**: `AuditEventType.SHARE_CREATED`/`SHARE_REVOKED` enum 정의(이전까지 사용처 0) → `ShareAuditListener` `@TransactionalEventListener` REQUIRES_NEW로 첫 emit. `SHARE_EXPIRED` 배치 트랙은 deferred.
- **A1~A9 회귀 0**: 전체 백엔드 535 tests GREEN.

### 핵심 결정 (A10 트랙, 확정)

1. **shares 테이블 = share 메타, permission row = 권한 자체** (ADR #34) — share row가 `permission_id` 참조. revoke 시 share `revoked_at` set + permission row delete + `share.revoked` audit 단일 발행 (이중 audit 회피, KISS).
2. **subject 4종 채택, 후처리는 MVP user 한정** — wire format `user`/`department`/`role`/`everyone` 모두 수신. with-me 매칭은 `subject_type='user' AND subject_id=actorId` 1차. department/role/everyone 후처리는 별도 ADR로 박제.
3. **`expires_at` 컬럼 추가** (V6) — frontend permission expiresAt UX 패리티 + 향후 `SHARE_EXPIRED` 배치 자동 전환 hook. 컨트롤러 진입 시 미래 시각 검증.
4. **`canRevoke` SpEL** — `@PreAuthorize("@shareCommandService.canRevoke(#shareId, principal)")` (owner OR sharer OR ADMIN). audit 회피용 단일 진입점.
5. **Cursor `{createdAt}|{id}` base64 url-safe** — A8 TrashCursor / A9 SearchCursor 패턴 변형. by-me/with-me 둘 다 `created_at DESC, id DESC` 정렬.
6. **Preset 4값 정합** (`VIEW`/`COMMENT`/`EDIT`/`MANAGE`) — V5 `permissions_preset_check` 단일 진실. ADR #28 본문 5-preset drift 정정.
7. **bulk revoke / SSE / folder 공유 / 외부 토큰 / SHARE_EXPIRED 배치 / Frontend UI는 별도 트랙** — backend stack only, ADR #34 backlog 박제.

### 다음 트랙 후보

- **F2 — useEffectivePermissions 실연결** (M8 권한 UI mock → A4 `/api/permissions` real). A10 share row를 permission UI에 노출하려면 `/api/permissions/effective` 응답에 share-derived 권한 포함 여부 결정 선행.
- **F4 — Frontend Shares UI** (Right Panel `Shares` tab + 공유 다이얼로그). docs/01 §6/§14 신설 필요 → 설계 추가 후 트랙 진입.
- **A11 — `SHARE_EXPIRED` 자동 전환 배치** (A7 `purge.expired` 미러). cron + `expires_at` 도과 row → `revoked_at` set + audit emit.
- **A12 — folder 공유** (`POST /api/folders/:id/share`). 현재 shares 테이블은 `file_id`/`folder_id` 양립 — endpoint만 추가.

---

## 2026-05-01 — 🏁 F1 마일스톤 종료 (Frontend Search 실연결)

### 범위

F1.0 (dev-docs bootstrap — `dev/active/frontend-search-realconnect/` plan/context/tasks 3 파일) → F1.1 (`api.searchFiles` 본체 MOCK_FILES filter mock → `fetch('/api/search?q=...')` 직접 호출 교체 + `SearchPage{items,nextCursor,totalEstimate}` → `{items: FileItem[]}` inline 매핑 + `api.search.test.ts` fetch mock 패턴 14 케이스 GREEN + `useSearch.test.tsx` integration fetch stub 갱신 + `api.trash.test.ts` 휴지통 제외 시나리오 제거) → F1.2 (PR #20 squash-merge `f9200dc` + dev-docs archive).

### 회고

- **commits**: 1 on top of PR #16 close `f77f886` (worktree branch `feature/f1-frontend-search-realconnect`) → squash-merge `f9200dc` on `master`. PR #20 single, CI green (backend junit 28s + frontend vitest 1m32s 모두 SUCCESS).
- **production 파일**: 1 수정 — `frontend/src/lib/api.ts` `searchFiles` 본체 교체 + `normalizeForSearch` import 정리. 시그니처 무수정(`{q,filters},{signal}→{items: FileItem[]}`) — 호출부 drift 0.
- **test 파일**: 3 갱신 — `api.search.test.ts` 전면 재작성(fetch wire 계약 + 매핑 file/folder/mixed/null edge + 401/403/5xx + abort 14건), `useSearch.test.tsx` integration `vi.stubGlobal('fetch', ...)`로 전환, `api.trash.test.ts` 휴지통 제외 시나리오 제거(이제 backend `deleted_at IS NULL` 책임).
- **frontend 회귀 0**: 전체 `pnpm test` 422/422 GREEN (55 suites). `pnpm typecheck` + `pnpm lint` clean.

### 핵심 결정 (F1 트랙, 확정)

1. **호출부 시그니처 무수정** (drift 0) — `useSearch` / `SearchBar` / `SearchResults` / `FileItem` 타입 전부 무수정. `api.searchFiles` 본체만 교체. 후속 endpoint mock→real 전환 시 동일 패턴 적용.
2. **filters 인자는 보존하되 무시** — backend가 `type` 외 필터(mime/owner/date) 미지원 (ADR #33). 향후 추가 시 `searchFiles` 내부 `URLSearchParams`만 확장.
3. **매핑 inline (KISS)** — 별도 `searchMapper.ts` 파일 신설 회피. `api.ts` 내부 단일 화살표 함수로 처리(50줄 이하).
4. **`updatedBy: ''` 빈 문자열** — backend `SearchResultDto` actor 필드 미반환. SearchResults UI는 secondary info(아이콘 옆 작은 텍스트)이므로 빈 표시 허용. 후속 backend 확장(`updatedByName`) 시 매핑만 보강.
5. **에러 envelope 일관** — audit 패턴 그대로(`Error & {status}` throw). `QueryCache` 글로벌 onError가 401/403 분기 — endpoint별 분기 코드 추가 0.
6. **AbortSignal 전파 = fetch native** — 기존 mock의 setTimeout+manual abort hookup 폐기. `fetch(..., { signal })` 사용으로 코드량 감소 + DOMException AbortError 자연 전파.
7. **휴지통 제외 = backend 책임** — `SearchQueryService` repo 쿼리에 `deleted_at IS NULL` WHERE 절 박제. frontend api.ts 경계에서 더 이상 검증 안 함.

### 다음 트랙 후보

- **F2 — useEffectivePermissions 실연결** (`api.getEffectivePermissions` mock → backend endpoint). 백엔드 미존재 — A10 또는 별도 트랙으로 endpoint 신설 선행 필요.
- **F3 — useStorageQuota 실연결** (M15 StorageBar). backend quota API 미존재 — endpoint 신설 선행.
- **A10 — Shares §7.9** (backend share/permission API 신설). 권한 매트릭스 docs/03 §3 + share endpoint family.
- **B1 — full-text/trigram 검색** (ADR #33 후속 트랙, postponed). `pg_trgm` extension + GIN index 마이그레이션 + `tsvector` 또는 trigram 전환. 항목 수 임계 도달 시 활성화.

---

## 2026-04-30 — 🏁 A9 마일스톤 종료 (Search Endpoint Backend)

### 범위

A9.0 (docs/00 ADR #33 신설 + docs/02 §7.8 spec 보강 (q/type/cursor/limit + 6단계 처리 + SearchResultDto schema) + docs/01 §10 backlink) → A9.1 (`SearchResultDto`/`SearchPage`/`SearchCursor` + base64 url-safe codec `{updatedAtEpochMs}|{type}|{id}` + 11건 GREEN) → A9.2 (`FileRepository`/`FolderRepository.searchByNormalizedName` LIKE+ESCAPE + `SearchQueryService` (q normalize→minLen 2→escapeLike→merge sort→READ 후처리→cursor) + 12건 GREEN) → A9.3 (`SearchController` GET /api/search + `@PreAuthorize("isAuthenticated()")` + IAE→400 + 5건 GREEN) → A9.4 (PR #19 squash-merge `73a8f01` + dev-docs archive).

### 회고

- **commits**: 5 on top of A8 close `a952f78` (worktree branch `a9-search-endpoint`) → squash-merge `73a8f01` on `master`. PR #19 single, CI green (backend junit + frontend vitest 모두 SUCCESS).
- **production 파일**: 5 신설 + 2 수정 — `search/SearchController.java` NEW, `search/SearchQueryService.java` NEW, `search/SearchResultDto.java` NEW (record), `search/SearchPage.java` NEW (record), `search/SearchCursor.java` NEW (codec), `FileRepository`/`FolderRepository`에 `searchByNormalizedName` + `countByNormalizedName` 추가.
- **test 파일**: 3 신설 — `SearchCursorTest` (11건, encode/decode round-trip + edge timestamps + invalid base64/format/type/uuid), `SearchQueryServiceTest` (12건, minLen/type/empty/file/folder/merge/READ filter/cursor round-trip/cursor page no-count/invalid cursor/escape), `SearchControllerTest` (5건, 정상/type blank/type=file/cursor+limit echo/IAE propagate).
- **ADR 신설**: #33 — 검색 알고리즘 = LIKE on normalized_name (MVP), tsvector full-text + pg_trgm fuzzy + owner/modifiedFrom/To 필터 + SEARCH_QUERIED audit emission 보류.
- **A1~A8 회귀 0**: 전체 `./gradlew test` 476/476 GREEN (51 suites).

### 핵심 결정 (A9 트랙, 확정)

1. **LIKE on normalized_name** (ADR #33) — full-text(tsvector + GIN) / trigram(`pg_trgm`)는 extension/index 마이그레이션 + 별도 ADR 필요. MVP 항목 수 가정 < 10k. 후속 트랙으로 박제.
2. **type 필터 = file/folder/all** — frontend 현재 미사용이지만 spec 보존. owner/modifiedFrom/To는 controller param + service overload만으로 확장 가능하게 hook (KISS, YAGNI).
3. **Cursor `{updatedAtEpochMs}|{type}|{id}` base64 url-safe** — A8 `TrashCursor` 패턴 변형. `updated_at DESC, id DESC` 정렬 키 + type tiebreaker(merge sort). round-trip 테스트로 계약 고정.
4. **READ 후처리 필터** — A8 TrashQueryService와 동일 패턴. ROLE 단계 short-circuit (`PermissionService.effectivePermissions(role).contains(READ)`) → `PermissionResolver.isGranted(actorId, type, id, READ)` fallback.
5. **DTO discriminated union** — `SearchResultDto` record + `type: "file"|"folder"` discriminator + `@JsonInclude(NON_NULL)` per-type field. 정적 팩토리 `fromFile(FileItem)` / `fromFolder(Folder)`.
6. **min length 2 = normalize 후 기준** — `NormalizeUtil.normalizeForSearch(q).length() < 2` → `IllegalArgumentException("INVALID_SEARCH_QUERY")` → 400 envelope (`GlobalExceptionHandler` IAE→`BAD_REQUEST` + message).
7. **LIKE pattern escape** — `\`, `%`, `_` backslash escape + repo native query에 `ESCAPE '\'` 박제. `SearchQueryService.escapeLike` package-private (테스트용 노출).
8. **totalEstimate 첫 페이지 only** — cursor 페이지에서는 -1 (재집계 비용 회피). count 쿼리는 cursor==null일 때만 발사.
9. **Pure Mockito (no Testcontainers)** — A8 KISS 패턴 일관. service boundary + repository contract + 권한 후처리 verify는 Mockito로 충분.

### accepted-deviation (후속 backlog)

- **Frontend `useSearch` 백엔드 연결** — 현재 `api.searchFiles` mock. PR #16 머지 후 본체 fetch로 교체.
- **Full-text / trigram** — tsvector + GIN index 또는 `pg_trgm` 마이그레이션 (ADR #33 deferred).
- **Filter 확장** — owner / modifiedFrom / modifiedTo. controller param + service overload hook 이미 마련.
- **SEARCH_QUERIED audit** — 검색 패턴 분석/개인정보 우려 별도 보안 트랙 (ADR #33 deferred).

### DoD 7/7

1. ✅ ADR #33 신설 + docs/02 §7.8 spec 보강 (q/type/cursor/limit + 6단계 처리 + SearchResultDto schema) + docs/01 §10 backlink.
2. ✅ `GET /api/search` — `@PreAuthorize("isAuthenticated()")` + q minLen 2 + type ∈ {file,folder,all} + cursor base64 + limit default 50/cap 100.
3. ✅ `SearchQueryService` — q normalize → escapeLike → repo LIKE(limit+1) → merge sort → READ 후처리 → nextCursor + totalEstimate.
4. ✅ Repository — `FileRepository`/`FolderRepository.searchByNormalizedName` (LIKE :pattern ESCAPE '\\' + cursor tuple predicate + WHERE deleted_at IS NULL) + `countByNormalizedName`.
5. ✅ Cursor codec — base64 url-safe `{updatedAtEpochMs}|{type}|{id}` round-trip + invalid → IAE → 400.
6. ✅ 테스트 28건 GREEN (cursor 11 + service 12 + controller 5) + A1~A8 회귀 0 (총 476 tests).
7. ✅ PR #19 CI green (backend junit + frontend vitest) + master squash-merge `73a8f01` + dev-docs `dev/active/a9-search-endpoint/` → `dev/completed/a9-search-endpoint/` archive.

### 다음 단계

- **Frontend `useSearch` 실연결** — backend `/api/search` fetch + cursor pagination + minLength 2 일관 검증. PR #16 (M11) 머지 후 진입 권장.
- **A10 (TBD)** — 후속 백엔드 마일스톤 미정. 후보: shares 본체(7.9), permissions 본체(7.10), 또는 Frontend M14 SSE/실시간.

---

## 2026-04-30 — 🏁 A8 마일스톤 종료 (Trash Listing + Manual Purge)

### 범위

A8.0 (docs/00 ADR #32 신설 + docs/02 §7.11 패치 + docs/02 §7.13.1 audit 정합 + docs/01 §13 backlink) → A8.1 (`GET /api/trash` cursor + type filter + 권한 후처리 + `TrashItemDto`/`TrashItemType`/`TrashCursor` + 12건 GREEN) → A8.2 (`DELETE /api/trash/:type/:id` ADMIN-only + `TrashPurgeService` (file: lock→version cascade→hard delete→`FILE_PURGED` audit / folder: BFS+leaf-first topo+single root `FOLDER_PURGED` audit) + 8건 GREEN) → A8.3 (PR #18 squash-merge `0c806c1` + dev-docs archive).

### 회고

- **commits**: 4 on top of A7 close `d539640` (worktree branch `feature/a8-trash-manage`) → squash-merge `0c806c1` on `master`. PR #18 single, CI green (backend junit + frontend vitest 모두 SUCCESS).
- **production 파일**: 9 신설 + 5 수정 — `trash/TrashController.java` NEW, `trash/TrashQueryService.java` NEW, `trash/TrashPurgeService.java` NEW, `trash/TrashItemDto.java` NEW (record), `trash/TrashItemType.java` NEW (enum), `trash/TrashPage.java` NEW (record), `trash/TrashCursor.java` NEW, `audit/AuditEventType.java` `FOLDER_PURGED("folder.purged")` 추가 (38→39), `FileRepository`/`FolderRepository`/`FileVersionRepository` 보조 query 확장, `frontend/src/types/audit.ts` mirror, `docs/03 §4.1` mirror.
- **test 파일**: 5 신설 — `TrashControllerTest` (9건, list 6 + purge 3), `TrashQueryServiceTest` (6건, cursor/type/권한), `TrashPurgeServiceTest` (5건, file 3 + folder 2 cascade leaf-first 검증), `FileTestFixtures` / `FolderTestFixtures` (package-protected entity constructor 우회).
- **ADR 신설**: #32 — manual purge URL `:type/:id`, per-row audit (`FILE_PURGED`/`FOLDER_PURGED`), bulk endpoint deferred, SSE emission infra milestone deferred.
- **A1~A7 회귀 0**: 전체 `./gradlew test` 448/448 GREEN.

### 핵심 결정 (A8 트랙, 확정)

1. **URL 패턴 `:type/:id`** — REST 자원 명시 (`/api/trash/file/:id`, `/api/trash/folder/:id`). 단일 endpoint `:id`보다 명시적이며, type 분기 dispatch가 service layer에서 단순.
2. **per-row audit** (ADR #32) — A7 summary-only(`SYSTEM_PURGE_EXECUTED`)와 대비. manual purge는 actor가 명시적 ADMIN 의도이므로 `FILE_PURGED`/`FOLDER_PURGED` 1건씩 발행. before_state에 name/folderId/storageKeys 보존.
3. **folder cascade audit는 root-only** — A6/A7 패턴 일관. 후손 folder/file은 개별 audit 미발행. root audit before_state에 `descendantFolders`/`descendantFiles` 카운트 + `storageKeys` 리스트(cap=1000+`storageKeysTruncated` flag).
4. **Leaf-first topo-sort 인라인** — A7 `HardPurgeService` Kahn's algorithm을 service 내부에 인라인 (`leafFirstOrder` + `findIdAndParentIdByIds`). 별도 helper class 미신설 (KISS).
5. **bulk delete deferred** — `DELETE /api/trash` (전체 비우기) 미구현. 트랜잭션 길이 + 부분 실패 정책 + audit 폭주가 단일 PR 범위 초과. ADR #32에 backlog 박제.
6. **Cursor opaque base64** — `{deletedAt}|{id}` url-safe base64. `TrashCursor.encode/decode` round-trip 테스트로 계약 고정. invalid → 400 GlobalExceptionHandler.
7. **Pure Mockito (no Testcontainers)** — A6/A7가 DB-level FK 위반 시나리오 이미 커버. service boundary + repository contract + audit emit verify는 Mockito로 충분. KISS — 이중 가드 비용 회피.
8. **SSE emission deferred** (ADR #32) — `// TODO: SSE emit` 주석만 박제. 실시간 동기화는 별도 인프라 milestone에서 일괄 회수 (A6/A7/A8 누적 부채).

### accepted-deviation (후속 backlog)

- **Frontend M9 (휴지통 UI 통합)** — 본 closure 직후 진입. backend endpoint(`GET /api/trash`, `DELETE /api/trash/:type/:id`) + restore endpoint(A6) + Undo(5초) 통합.
- **Bulk purge** — `DELETE /api/trash` 전체 비우기 (ADR #32 deferred).
- **SSE emission** — `file.purged` / `folder.purged` 실시간 push (별도 인프라 milestone).
- **Storage key 실삭제** — orphan storage_keys는 audit `before_state`에만 기록, 실제 S3 객체 cleanup은 `orphan.detect` 잡 (ADR #31).

### DoD 7/7

1. ✅ ADR #32 신설 + docs/02 §7.11 patch (per-resource restore + DELETE `:type/:id` + bulk strikethrough) + docs/02 §7.13.1 audit 정합 + docs/01 §13 backlink.
2. ✅ `GET /api/trash` — `@PreAuthorize("isAuthenticated()")` + cursor base64 + type filter + 권한 후처리 + 응답 스키마 docs/02 정합.
3. ✅ `DELETE /api/trash/:type/:id` — `@PreAuthorize("hasRole('ADMIN')")` + file/folder dispatch + 204.
4. ✅ `TrashPurgeService` — file (lock→version→hard delete→audit) + folder (BFS→version→file→leaf-first→folder→single root audit) + 404 매핑.
5. ✅ Audit `FILE_PURGED`/`FOLDER_PURGED` 발행 + before_state JSON 보존(name, folderId, storageKeys, descendantFolders/Files).
6. ✅ 테스트 20건 GREEN (controller 9 + query 6 + purge 5) + A1~A7 회귀 0 (총 448 tests).
7. ✅ PR #18 CI green (backend junit + frontend vitest) + master squash-merge `0c806c1` + dev-docs `dev/active/a8-trash-manage/` → `dev/completed/a8-trash-manage/` archive.

### 다음 단계

- **Frontend M9 bootstrap** — `feature/m9-frontend-trash` worktree + plan/context/tasks 3파일 + 사용자 plan 리뷰 게이트. 본 세션에서 이어서 진입.
- **SSE 실시간 동기화 (별도 인프라 milestone)** — A6/A7/A8 누적 SSE TODO 일괄 회수.
- **Search endpoint backend** — M11 frontend search 미연결.
- **Audit query export** — `/admin/audit-logs` 필터 + CSV.

---

## 2026-04-30 — 🏁 A7 마일스톤 종료 (Hard Purge Job — purge.expired)

### 범위

A7.0 (docs/00 ADR #31 + docs/02 line 37 + §7.11.1 + docs/04 §13 patch) → A7.1 (Repository 8메서드 + Testcontainers 7건 GREEN) → A7.2 (`HardPurgeService` 트랜잭션 본체 + audit `SYSTEM_PURGE_EXECUTED` summary emit + Kahn's algorithm leaf-first 위상정렬 + 7건 테스트) → A7.3 (`HardPurgeProperties` + `SchedulingConfig` + `HardPurgeJob` + 통합 4건 + `application.yml` `app.purge` 섹션) → A7.4 (PR #17 squash-merge `5c22e23` + dev-docs archive).

### 회고

- **commits**: 4 on top of A6 close `fdeb610` (worktree branch `feature/a7-hard-purge`) → squash-merge `5c22e23` on `master`. PR #17 single, CI green (backend junit 3m6s + frontend vitest 1m6s 모두 SUCCESS).
- **production 파일**: 6 신설 + 4 수정 — `purge/HardPurgeService.java` NEW, `purge/PurgeResult.java` NEW (record), `purge/HardPurgeJob.java` NEW, `purge/HardPurgeProperties.java` NEW, `config/SchedulingConfig.java` NEW, `application.yml` `app.purge` 섹션 추가, `FileRepository`/`FolderRepository`/`FileVersionRepository` 각각 hard purge 보조 메서드 확장.
- **test 파일**: 4 신설 — `HardPurgeRepositoryTest` (7건, V5 schema + cascade 정합), `HardPurgeServiceTest` (7건, 트랜잭션 본체 + audit JSON), `HardPurgeJobIntegrationTest` (3건, enabled 시나리오), `HardPurgeJobDisabledIntegrationTest` (1건, disabled 빈 미등록).
- **ADR 신설**: #31 — A7 = DB-only, S3 객체 삭제는 storage 모듈 milestone 으로 deferred.
- **A6 회귀 0**: 전체 `./gradlew test` BUILD SUCCESSFUL.

### 핵심 결정 (A7 트랙, 확정)

1. **DB-only (ADR #31)** — backend storage 모듈 0개 시점. `purge_after` 경과 row의 DB hard delete만 A7 범위. orphan storage_keys는 audit `after_state.orphanStorageKeys`(cap=1000)에 기록만 — storage 모듈 도입 시 `orphan.detect` 잡(docs/04 §13)이 storage_key cross-check로 정리.
2. **Audit summary-only** — A6 root-only 패턴 일관. 1 run = 1 `SYSTEM_PURGE_EXECUTED` audit. per-row `FILE_PURGED`/`FOLDER_PURGED` enum은 정의되어 있으나 발행 없이 A8 manual purge `/api/trash/:id` 트랙으로 reserve.
3. **file_versions cascade hard delete** — docs/02 line 37 정책 갱신: 일반은 영구 보존이지만 file row hard purge 시점에는 cascade 삭제 (FK `ON DELETE RESTRICT` 만족). storage_key는 audit orphan 기록 후 삭제.
4. **Kahn's algorithm in-memory** — schema에 depth 컬럼 부재. parent_id 그래프로 batch 내 leaf-first 위상정렬. cycle 발생 시 ordered list 길이 < 입력으로 자연스러운 skip.
5. **MAX_PURGE_PER_RUN 합산 한도** — files+folders 합산 (기본 10000). 초과 시 `truncated=true`로 다음 run 이월 (잡 자체는 정상 완료, 가장 오래된 row 우선).
6. **단일 트랜잭션** — `@Transactional` 본체. partial purge 미허용. 예외 시 전체 rollback → audit 미발행 → 다음 cron 재시도. audit emit만 REQUIRES_NEW.
7. **운영 기본 비활성** — `app.purge.enabled=false`. staging/prod에서 명시적으로 `true` 설정 후 투입. dev/test는 `@TestPropertySource` override 패턴.
8. **No ShedLock** — 단일 인스턴스 운영 가정. 다중 인스턴스 도입 시 별도 ADR.

### accepted-deviation (후속 backlog)

- **S3 객체 삭제** — storage 모듈 도입 + `orphan.detect` 잡 (ADR #31).
- **A8 manual purge** — `/api/trash/:id` 단건 hard delete endpoint + per-row `FILE_PURGED`/`FOLDER_PURGED` audit emit.
- **Legal Hold 통합** — A7 cron 트랜잭션이 legal_holds 테이블 조회 후 hold된 row 제외 (docs/03 §6.3 후속).
- **Monitoring metric** — `purge.expired` 잡 실행 횟수 / 처리 row / 실패 카운터 (별도 backlog).

### DoD 10/10

1. ✅ Repository 확장 — `findExpiredFileIds/FolderIds`, `hardDeleteByIds`, `findStorageKeysByFileIds`, `deleteByFileIds`, `findIdAndParentIdByIds` (8 메서드).
2. ✅ `HardPurgeService.runDailyPurge` 단일 트랜잭션 본체 + 위상정렬 + audit summary emit (`PurgeResult` record).
3. ✅ Kahn's algorithm leaf-first 위상정렬 (parent_id 그래프, cycle 안전).
4. ✅ `HardPurgeProperties` + `SchedulingConfig` + `HardPurgeJob` (`@ConditionalOnProperty` 이중 가드) + cron 트리거 → service 위임.
5. ✅ `application.yml` `app.purge` 섹션 (운영 기본 enabled=false).
6. ✅ Repository 7건 + Service 7건 + Job 통합 4건 GREEN (총 18건 신규).
7. ✅ 회귀 0 — `./gradlew test` BUILD SUCCESSFUL.
8. ✅ ADR #31 본문 게재 + status: accepted, docs/02 line 37 cascade 정책 갱신, §7.11.1 신설, docs/04 §13 footnote.
9. ✅ PR #17 CI green (backend junit + frontend vitest) + master squash-merge `5c22e23`.
10. ✅ dev-docs `dev/active/a7-hard-purge/` → `dev/completed/a7-hard-purge/` archive.

### 다음 단계

- **A8 후보**: manual purge `/api/trash/:id` endpoint + frontend 휴지통 UI (docs/01 §13).
- **docs/03 §5~§8**: 저장소 보안 / Legal Hold / 데이터 보호 / 보안 회귀 가드.
- **docs/04 본문**: 관리자 페이지 / 쿼터 / 백업.

---

## 2026-04-29 — 🏁 M16 Grid View (FileCard + FileTable view 분기)

### 범위
docs/01 §18 row 16 — `FileTable에 grid 모드 추가 (썸네일 카드형). M14의 ViewSwitch에서 토글`. M15.2의 `?view=grid`를 FileTable이 소비.

### 변경
- **lib/fileIcon.ts (M16.1 사전)**: M14에서 FileRow에 인라인이던 `fileIconFor(item)` 추출. FileRow는 import으로 교체. KISS — FileCard와의 중복 방지.
- **FileCard (M16.1)**: 신규. `Lucide 아이콘(36px) + 이름(line-clamp-2) + 메타(폴더|크기)`. selection ring(`ring-2 ring-accent` + `bg-accent-soft`), `onClick`/`onDoubleClick`, `aria-selected`/`aria-disabled`(pending). 가상화/DnD 없음 (KISS, MVP).
- **FileTable (M16.2)**: `useViewParam` 통합. `view==='grid'`일 때 새 분기 — `role=grid` `aria-label="파일 그리드"` 컨테이너 + `grid-cols-[repeat(auto-fill,minmax(140px,1fr))]` + `items.map(FileCard)`. 키보드 핸들러는 list와 동일 (`handleKeyDown` 재사용 — 1D index ArrowUp/Down 동작). list 분기 무수정.

### 검증
- `npx vitest run`: **55 files / 415 tests passed** (M16 신규 7 — FileCard 5 + FileTable view 분기 2, 회귀 0).
- `npx tsc --noEmit`: clean.
- `npx eslint`: clean.

### 핵심 결정
- **`fileIconFor` lib 추출**: M14에선 FileRow 내 헬퍼였음. M16에서 FileCard와 공유 필요 → DRY. `lib/fileIcon.ts`로 분리, FileRow 재import.
- **FileCard는 별도 컴포넌트 (FileRow 재사용 X)**: gridCols 5-col table layout이 정사각 카드와 호환 안됨. FileRow 분기 추가는 가독성 ↓ → KISS, 분리.
- **Grid 모드 가상화 없음 (MVP)**: 폴더 당 100+ 항목 시 성능 이슈 가능 → v1.x 트랙. 현재 mock 데이터/실사용 패턴은 50 미만이므로 충분.
- **Grid 모드 키보드는 1D**: 좌/우 wrap navigation은 v1.x. M16 시점엔 list와 동일하게 ArrowUp/Down만 동작 (인덱스 ±1).
- **Grid 모드 DnD 없음**: list 모드에서만 이동 가능 — Grid는 마우스 클릭 + 더블클릭만. M15.2 ViewSwitch는 단순 토글이므로 DnD 필요시 list로 전환 권장.

### 비범위 (후속)
- Grid 모드 가상화 (TanStack Virtual grid) — v1.x
- Grid 모드 2D 키보드 wrap (좌우 + 상하) — v1.x
- Grid 모드 DnD — v1.x
- 썸네일 미리보기 (실제 이미지) — backend thumbnail API 후
- Grid `aria-rowcount`/`aria-rowindex` — 1D 인덱스라 부적합. v1.x grid 2D 네비 도입 시 재검토

### 다음 세션 컨텍스트
- 시퀀스 M11→M9→M8→M14→M15→M16 **전부 완료**. PR #16 6-마일스톤 번들.
- 다음 사용자 지시 대기. (자율 실행 모드 시퀀스 끝)

---

## 2026-04-29 — 🏁 M15 Layout Extras (SortChip + ViewSwitch + StorageBar + RightPanel 탭)

### 범위
docs/01 §18 row 15 — `SortChip + ViewSwitch + StorageBar + RightPanel 탭`. 모두 docs/01 §1.1 진실 출처 규칙 (URL 우선) 준수.

### 변경
- **useSortParams (M15.1)**: 기존 read-only → `setSort(key, dir?)` 추가. 같은 key 재선택 시 asc/desc 토글, 다른 key 선택 시 asc reset. `router.replace` + `URLSearchParams` 보존.
- **SortChip (M15.1)**: 신규. FolderToolbar 우측 정렬 드롭다운. `name/updatedAt/size`, `aria-haspopup="menu"` + `menuitemradio`. outside click 시 닫힘. label "정렬: {sort} {dir}".
- **useViewParam + ViewSwitch (M15.2)**: 신규. URL `?view=list|grid` (default list 시 param 제거). `aria-pressed` + `aria-label`. Grid 본체는 M16.
- **api.getStorageQuota + useStorageQuota + StorageBar (M15.3)**: mock — 50 GB total / 75% used 고정 placeholder. Sidebar 하단 (TrashLink 아래) 마운트. `role=progressbar` + `aria-valuenow`. 80%+ warn / 95%+ danger 색.
- **RightPanel 4-tab (M15.4)**: 헤더 아래 `role=tablist` 추가. detail/versions/activity/permissions. detail은 기존 `dl` 그대로(회귀 보호). 나머지는 `<ComingSoon>` placeholder. fileId 변경 시 detail 탭으로 자동 리셋.
- **qk.storageQuota 추가**, **api.ts getStorageQuota 추가**, **(explorer)/layout.tsx StorageBar 마운트**.
- **회귀 정합**: BulkActionBar.test.tsx 3곳 / StatusBar.test.tsx 1곳 — `useSortParams` mock에 `setSort: vi.fn()` 보강 (typecheck 호환).

### 검증
- `npx vitest run`: **53 files / 408 tests passed** (M15 신규 11 — SortChip 3 + ViewSwitch 3 + StorageBar 3 + RightPanel 탭 2, 회귀 0).
- `npx tsc --noEmit`: clean.
- `npx eslint`: clean (변경 파일 17개 0 issue).

### 핵심 결정
- **URL 진실 출처**: SortChip/ViewSwitch 모두 `router.replace` + searchParams. Zustand 복제 X (docs/01 §1.1).
- **정렬 토글 규칙**: 같은 key 재선택 → dir 토글, 다른 key → asc reset. KISS, 명시적 dir override 가능.
- **ViewSwitch state는 URL 단독**: 새로고침/공유 시 view 보존. M15 시점엔 FileTable이 무관심하게 통과 → M16에서 소비.
- **StorageBar는 mock placeholder**: 75% 고정. 실제 quota API 신설 시 `api.getStorageQuota`만 fetch로 교체 (UI/hook 무수정). invalidate(업로드/삭제) 미구현 — staleTime 5분으로 우회.
- **RightPanel 탭은 단일 컴포넌트 useState**: KISS — 별도 파일 분리 X. URL 동기화 X (패널 자체가 `?file`에 종속이고 탭 상태 deep-link 요구는 v1.x).
- **세부정보 외 탭은 명확한 "준비 중" placeholder**: 가짜 컨텐츠 X — 백엔드 API(versions: A5 진행중 / audit: 기존 / permissions: 임시 mock) 통합은 별도 트랙.

### 비범위 (후속)
- Grid View 본체 (FileRow 카드 모드, FileTable 분기) — **M16**
- 버전/활동/권한 탭 실내용 — 백엔드 API 별도 트랙
- StorageBar 실수치 + 업로드/삭제 후 invalidate — 백엔드 quota API 후
- SortChip/ViewSwitch 키보드 단축키 — KISS

### 다음 세션 컨텍스트
- 시퀀스 다음: **M16 Grid View** (FileTable grid 모드 + ViewSwitch 토글 통합).

---

## 2026-04-29 — 🏁 M14 Visual Identity (Lucide + Avatar + StatusBar)

### 범위
docs/01 §4 트리 + 시각적 통일. SearchBar 아이콘 / TopBar Avatar / FileRow 이모지 → Lucide / 하단 StatusBar.

### 변경
- **SearchBar (M14.1)**: input prefix `Search` Lucide 아이콘 (16px, fg-muted). `pl-8` padding 조정.
- **Avatar (M14.2)**: 신규 컴포넌트 — `initial`/`displayName` props (default `"U"`/`"사용자"`). 28px circle, accent bg, `aria-label=displayName`. TopBar 우측 액션 영역에 마운트.
- **FileRow (M14.3)**: 이모지(`📁/📄/...`) → Lucide (`Folder`(accent) / `File` / `FileText` / `FileImage` / `FileSpreadsheet`(fg-muted)). mime 기반 분기 함수 `fileIconFor`. size 16, currentColor.
- **StatusBar (M14.4)**: 신규 — `<footer role="contentinfo">` 좌측 항목 수(`useFilesInFolder`) + 우측 선택 카운트(`useSelectionStore`, 0일 때 숨김, `aria-live=polite`). h-7 / border-t / surface-1 bg.
- **(explorer)/layout.tsx**: `<StatusBar />` main 하단 마운트.

### 검증
- `npx vitest run`: 50 files / 397 tests passed (M14 신규 6 — Avatar 2 + StatusBar 4, 회귀 0).
- `npx tsc --noEmit`: clean.
- `npx eslint`: clean (변경 파일 8개 0 issue).

### 핵심 결정
- **Lucide 단일 아이콘 라이브러리**: 이모지/SVG 혼재 제거. 색상은 `currentColor` + `text-*` 유틸로 일관 제어. 폴더만 `text-accent` 강조.
- **Avatar는 stub만**: 실제 user/session API 미정 → props 기반 placeholder. M16+ 또는 백엔드 auth 후 교체.
- **StatusBar 최소 정보**: 항목 수 + 선택 카운트만. 저장 용량/SSE 동기화/정렬 표시는 M15+에서 확장.
- **선택 카운트 aria-live**: 선택 변동을 스크린리더에 안내. 0일 땐 DOM 자체에서 숨겨 카운트 음성 잡음 방지.

### 비범위 (후속)
- StorageBar / SortChip / ViewSwitch — M15
- Avatar 실제 사용자 데이터 연결 — auth 백엔드 후
- StatusBar 동기화 상태(SSE) — M15+

### 다음 세션 컨텍스트
- 시퀀스 다음: **M15 Layout Extras** (SortChip + ViewSwitch + StorageBar + RightPanel 탭).

---

## 2026-04-29 — 🏁 M8 권한 UI + ShareDialog

### 범위
docs/01 §14 권한 훅 + 조건부 렌더링 + 단일 파일 공유. M8.0 bootstrap → M8.1 api/qk → M8.2 usePermission useQuery + BulkActionBar 마이그레이션 → M8.3 ShareDialog.

### 변경
- **api/qk (M8.1)**: `qk.permissions(nodeId)` (nodeId 없으면 `qk.effectivePermissions()`와 동일). `api.getEffectivePermissions(nodeId?)` mock — admin preset 8 권한(`READ/UPLOAD/EDIT/MOVE/DOWNLOAD/DELETE/SHARE/PERMISSION_ADMIN`, `PURGE` 제외 — docs/03 §3.2).
- **usePermission (M8.2)**: 기존 stub(lowercase 모두 true) → `useQuery` 기반, `Record<Permission, boolean>` 반환 (UPPER_SNAKE_CASE — `types/permission.ts` 미러). 로딩 중 모든 플래그 false (보수 디폴트, 깜빡임 방지). staleTime 60s.
- **BulkActionBar (M8.2/M8.3)**: 4개 필드 `download/move/edit/delete` → `DOWNLOAD/MOVE/EDIT/DELETE` 마이그레이션. 신규 SHARE 버튼 (단일 **파일** 선택 시만 활성, 폴더 공유는 v1.x).
- **stores/shareUi (M8.3)**: `useShareUiStore` (open/close + fileId + fileName).
- **ShareDialog (M8.3)**: focus trap (close 버튼) + Esc + 닫기. 링크 placeholder `https://ibiz.example/share/{fileId}` + `navigator.clipboard.writeText` + sonner 토스트. 만료/권한 옵션은 v1.x.
- **ClientFilesPage**: ShareDialog 마운트.

### 검증
- `npx vitest run`: 48 files / 391 tests passed (M8 신규 20 — api.permissions 4 + usePermission 4 + shareUi 3 + ShareDialog 6 + BulkActionBar 공유 3, 회귀 0).
- `npx tsc --noEmit`: clean.
- `npx eslint .`: clean.

### 핵심 결정
- **권한 enum 단일 진실**: `types/permission.ts` (UPPER_SNAKE_CASE 백엔드 미러)가 계약. usePermission이 그 enum 그대로 키로 노출. 기존 lowercase API는 정리.
- **로딩 중 모든 false**: docs/01 §14.3 보수적 패턴. 깜빡임은 staleTime 60s + admin preset mock 80ms로 거의 없음.
- **vi.mock 권장 패턴**: 기존 컴포넌트 테스트는 `vi.mock('@/hooks/usePermission')`로 admin preset 고정 → 권한 검증과 무관한 본 테스트 의도 보존. usePermission 자체는 별도 dedicated test.
- **단일 파일만 공유**: 폴더/다중 공유는 v1.x — 백엔드 endpoint 미정.
- **clipboard 폴백**: `navigator.clipboard?.writeText` optional chaining + try/catch — jsdom safe.

### 비범위 (후속)
- 403 글로벌 핸들러 (toast + qk.permissions invalidate) — api fetch wrapper 정리 후 별도 PR
- ShareDialog 만료/권한 옵션 — 백엔드 `POST /api/files/:id/share` endpoint 신설 후
- 폴더 공유 / 다중 파일 공유 — v1.x
- FileRow 우클릭/단축키 공유 진입점 — KISS

### 다음 세션 컨텍스트
- 시퀀스 다음: **M14 Visual Identity** (TopBar 정비 + Lucide 아이콘 + StatusBar + FileRow 밀도).

---

## 2026-04-29 — 🏁 M9 휴지통 + 5초 Undo + /trash 페이지

### 범위
soft-delete 기반 휴지통 (frontend-only mock). M9.0 bootstrap → M9.1 mock api soft-delete → M9.2 hooks → M9.3 BulkActionBar Undo → M9.4 /trash + Sidebar TrashLink.

### 변경
- **types/api (M9.1)**: `FileItem.deletedAt? + originalParentId?` 추가. `api.deleteBulk` hard splice → soft delete (`deletedAt = now`, `originalParentId = parentId` 스냅샷). `api.listTrash` (deletedAt 내림차순) / `restoreBulk` (clear deletedAt + parentId restore from originalParentId, root fallback if parent missing) / `purgeBulk` (hard splice) 신설. `getFilesInFolder`/`searchFiles`에 `!f.deletedAt` 필터 추가.
- **queryKeys (M9.1)**: `qk.trash() / qk.trashList()` 추가. `invalidations.afterDelete` → `[filesListPrefix(folder), trash(), search()]` 확장. `afterRestore(opts.folderIds[])` / `afterPurge` 신설.
- **hooks (M9.2)**: `useTrashList` (staleTime: 0) + `useRestoreBulk({ids, originalParentIds?})` + `usePurgeBulk({ids})` — 옵션 onSuccess/onError forward, invalidations 자동 호출.
- **UI (M9.3)**: `BulkActionBar` Delete onSuccess 시 `toast.success(..., {duration: 5000, action: {label: '되돌리기', onClick: restoreMut.mutate({ids, originalParentIds:[folderIdAtStart]})}})`. onError 시 `toast.error`.
- **UI (M9.4)**: `/app/(explorer)/trash/page.tsx` (section header + TrashTable) + `TrashTable` (role=grid, 컬럼: 이름/삭제 시각/원위치/액션, 로딩/에러/빈 분기, 복원 + 영구삭제 confirm) + `TrashLink` (Sidebar 하단, usePathname 기반 active aria-current). `(explorer)/layout.tsx`에 `<TrashLink />` 추가 (mt-auto pt-2 border-t).

### 검증
- `npm run test`: 44 files / 371 tests passed (M9 신규 27 — api.trash 7 + qk/invalidations 4 + hooks 4 + BulkActionBar Undo 3 + TrashTable 6 + TrashLink 3).
- `npm run typecheck`: clean.
- `npm run lint`: clean.

### 핵심 결정
- **소프트 삭제 = mock 한정** — backend A6 cascade 정책(folder 단위)과 frontend mock(file 단위)은 별개 트랙. 백엔드 file delete endpoint 신설 시 api.deleteBulk fetch만 교체, hook/UI 무수정.
- **Undo는 BulkActionBar 경유 시만** — FileTable Delete 단축키 Undo는 KISS로 분리 (별도 PR).
- **restoreBulk parent fallback** — `originalParentId` 가리키는 폴더가 (사용자 액션으로) 사라진 경우 root로 복원. backend는 `FolderNotFoundException`(A6)으로 강제하지만 frontend mock은 UX 우선.
- **vi.hoisted로 옵션 캡처** — `useDeleteBulk(opts)` 내부에서 `optionsCapture.current = opts`로 저장 → 테스트가 onSuccess 콜백 직접 트리거 가능 (mutate spy + onSuccess 분리 검증).

### 다음 세션 컨텍스트
- 시퀀스 다음: **M8 share dialog + 권한 확장** (docs/01 §14, backend A3 권한 매트릭스 활용).

---

## 2026-04-29 — 🏁 M11 검색 (debounce + normalize + AbortController)

### 범위
TopBar 글로벌 검색 (frontend-only). M11.0 bootstrap → M11.1 lib infra → M11.2 useSearch → M11.3 SearchBar/SearchResults UI.

### 변경
- **lib infra (M11.1)**: `qk.search()` / `qk.searchResults(normalized, filters)` 추가; `api.searchFiles({q, filters}, {signal})` mock (200ms latency + `normalizeForSearch` + AbortSignal); `useDebounce<T>(value, delayMs)` 신설.
- **useSearch (M11.2)**: 300ms debounce → `normalizeForSearch` → `useQuery` (`enabled: normalized.length>=2`, `placeholderData: keepPreviousData`, `staleTime: 30s`, signal forward).
- **UI (M11.3)**: `SearchBar` (searchbox role, `/` 단축키 focus via `FOCUS_SEARCH_EVENT`, Esc → clear+close, blur 120ms 지연); `SearchResults` (1자/에러/로딩/빈/파일·폴더 분기 — 파일 → `useOpenFile().open(id)`, 폴더 → `router.push(buildCanonicalPath)`); `TopBar` 좌측 슬롯 통합 (justify-end → justify-between).

### 검증
- `npm run test`: 40 files / 344 tests passed (신규 28 — useDebounce 4 + api.search 6 + qk.search 2 + useSearch 5 + SearchBar 4 + SearchResults 7).
- `npm run typecheck`: clean.
- `npm run lint`: clean (eslint exit 0).

### 핵심 결정
- mock api는 setTimeout 200ms + AbortSignal 처리 → fake-timer 환경에서는 `vi.spyOn(api, 'searchFiles').mockResolvedValue(...)`로 즉시 resolve, 별도 integration 1건만 real timers로 실제 mock 동작 검증.
- `useGlobalShortcuts`는 이미 `/` 키 → `FOCUS_SEARCH_EVENT` 디스패치 + input focus 가드 보유 → 추가 작업 없음.
- 백엔드 `/api/search` 도입 시 api 내부 fetch 교체만으로 호환 (계약 동일).

### 다음 세션 컨텍스트
- 시퀀스 다음: M9 휴지통 페이지 + Undo (api.listTrash mock 이미 존재 가능성 확인 필요).

---

## 2026-04-30 — 🏁 A6 마일스톤 종료 (Folder Delete/Restore + Descendant Cascade)

### 범위

A6.0 (docs/02 §7.5 cascade 정책 + restore-self 본문 정합, no-code) → A6.1+A6.2+A6.3 통합 (`FolderMutationService.delete/restore` + 후손 BFS cascade(folder + file batch UPDATE) + `FolderController` DELETE/restore endpoint + `FolderRestoreConflictException` + `RESTORE_CONFLICT` envelope) → refactor (cascade 후손 `originalParentId` 스냅샷 + restore가 soft-deleted parent 위로 시도 시 일관 NotFound) → A6.4 (PR #15 squash-merge `4111990` + dev-docs archive).

### 회고

- **commits**: 4 on top of A5 close `d23270e` (worktree branch `feature/a6-folder-mutation-delete`) → squash-merge `4111990` on `master`. PR #15 single, CI green (backend junit 3m9s + frontend vitest 1m10s 모두 SUCCESS).
- **production 파일**: 5 수정 + 1 신설 (`FolderMutationService` +182 lines, `FolderRepository` +36, `FolderController` +endpoints, `FileRepository` +24, `GlobalExceptionHandler` +handler, `FolderRestoreConflictException` NEW). frontend 무수정.
- **test 파일**: 2 수정 (`FolderMutationServiceTest` +5 cases — cascade/not-found/restore/conflict/cascade-child-restore-not-found, `FolderControllerTest` +2 cases — delete/restore endpoint).
- **endpoint 신규**: 2개 — `DELETE /api/folders/{id}` (204) + `POST /api/folders/{id}/restore` (200).
- **envelope code**: `RESTORE_CONFLICT` 매핑 신설 (docs/02 §8 line 1221에 이미 등록되어 있어 본문 patch 불필요).
- **A4 회귀 0**: PermissionEvaluatorIntegrationTest 13/13 GREEN 유지.

### 핵심 결정 (A6 트랙, 확정)

1. **Audit root만** — cascade 후손 FOLDER_DELETED 미발행, root 1건에 `descendantFolders/Files` 카운트 보존 (audit_log 폭증 회피, docs/02 §7.5 + CLAUDE.md §3 원칙 8).
2. **Restore self만** — 자기 자신만 복원, 후손 휴지통 잔존. `original_parent_id`가 soft-deleted면 404 ("부모 먼저 복원" UX 강제).
3. **Service 레벨 BFS** — `assertNoCycle` 패턴 일관성, MAX_CASCADE_NODES=100k 안전 한도. 성능 이슈 시 WITH RECURSIVE 전환 + ADR.
4. **Cascade 후손 originalParentId 스냅샷** — `FileRepository.softDeleteByFolderIds`가 `originalFolderId = folderId`를 set하는 것과 대칭. 후손 폴더도 개별 restore 시도 가능 (소프트-삭제된 부모 위로 복원 시도하면 `FolderNotFoundException`).
5. **Integration class 미신설 (KISS)** — `FolderControllerIntegrationTest` 별도 작성 안 함. PermissionEvaluatorIntegrationTest 13/13가 SpEL `hasPermission(folder, DELETE)` 동일 evaluator 경로 보장 + 회귀 0이 곧 권한 매트릭스 정합.
6. **단일 PR / 통합 commit** — A6.1~A6.3 테스트 상호의존(controller endpoint 미구현 시 controller test 컴파일 실패) → 단일 feature commit으로 처리.
7. **File mutation 트랙은 cascade 미참여** — A4.8(`4e720eb`)에서 닫힘. `FileMutationService.delete` 미호출, `FileRepository.softDeleteByFolderIds` batch UPDATE만 사용 (audit 정책 일관성).

### accepted-deviation (후속 backlog)

- **Hard purge job** — `purge_after` 경과 row 영구 삭제 + S3 객체 삭제. docs/04 §13 배치 트랙.
- **후손 cascade restore endpoint** — `?cascade=true` 또는 별도 path. UX 결정 후 신설.
- **Frontend 휴지통 UI** (docs/01 §13) — backend 계약 안정화 완료, 진입 가능.

### DoD 10/10

1. ✅ `FolderMutationService.delete` + 후손 BFS cascade + `MAX_CASCADE_NODES` 안전 한도
2. ✅ `FolderRepository.softDeleteByIds` (`originalParentId` 스냅샷 포함) + `FileRepository.softDeleteByFolderIds`
3. ✅ Audit root만 발행, `after_state.descendantFolders/Files` 카운트 보존
4. ✅ `FolderMutationService.restore` + parent 활성 검증 + `existsActiveByParentAndNormalizedNameExcludingId` 충돌 검사
5. ✅ `FolderRestoreConflictException` 신설 + `GlobalExceptionHandler` → 409 `RESTORE_CONFLICT` 매핑
6. ✅ `FolderController.delete` (204) + `restore` (200) endpoint + `@PreAuthorize("hasPermission(#id, 'folder', 'DELETE')")`
7. ✅ FolderMutationServiceTest 5 cases + FolderControllerTest 2 cases GREEN
8. ✅ A4 PermissionEvaluatorIntegrationTest 13/13 GREEN, frontend test 회귀 0
9. ✅ docs/02 §7.5 cascade 정책 + restore-self 본문 정합 (line 881~922)
10. ✅ PR #15 CI green + master squash-merge `4111990` + dev-docs `dev/active/a6-folder-mutation-delete/` → `dev/completed/`

### 다음 단계

- **A7 후보**: hard purge job (docs/04 §13) 또는 frontend 휴지통/탐색기 UI (docs/01 §13).
- **docs/03 §5~§8**: 저장소 보안 / Legal Hold / 데이터 보호 / 보안 회귀 가드 (코드 0줄 트랙).
- **docs/04 본문**: 관리자 페이지 / 쿼터 / 백업.

---

## 2026-04-29 — A6.0 docs/02 §7.5 cascade 정책 + restore-self 명시 (no-code)

### 범위
A6 마일스톤 진입점. folder delete/restore 트랙의 §7.5 응답 본문 정합 patch.

### 변경
- `docs/02 §7.5` `DELETE /api/folders/:id` 행 SoftDel 컬럼에 `(재귀: 후손 폴더/파일 cascade — root 1회 audit)` 보강.
- `docs/02 §7.5` `POST /api/folders/:id/restore` 행 SoftDel 컬럼에 `(자기 자신만 복원, 후손 잔존)` 보강.
- `docs/02 §7.5` 응답 본문(line ~915) DELETE/restore TX 의사코드에 cascade BFS + audit root-only + restore-self 정책 명시.

### 검증
- A6.0 commit 자체는 docs 1파일만 staged (코드 0줄).
- §8 `RESTORE_CONFLICT`는 line 1221에 이미 등록(A4 마일스톤 closure 시점) — 별도 patch 불필요.
- 다음 phase A6.1 `FolderMutationService.delete` 구현이 본 §7.5 본문과 1:1 정합.

---

## 2026-04-29 — 🏁 A5 마일스톤 종료 (FileVersion Domain — entity + GET /versions)

### 범위

A5.0 (docs/02 §7.6 응답 스키마 + ADR #29 트리거 마커, no-code) → A5.1 (`VersionScanStatus` enum + converter + `FileVersion` @Entity + `FileVersionRepository` + Testcontainers 7건) → A5.2 (`FileVersionDto` record + `FileVersionController` GET `/api/files/{fileId}/versions` + `@PreAuthorize` READ + 권한/404 매트릭스 통합 테스트 7건) → A5.3 (CI green wait + squash-merge `5155e00` + ADR #29 closed + dev-docs archive).

### 회고

- **commits**: 7 on top of A4 close `48e23a3` (worktree branch `feature/a5-file-versions`) → squash-merge `5155e00` on `master`. PR #13 single, CI green (backend junit + frontend vitest 모두 SUCCESS).
- **production 파일**: 5 신설 (backend `file/VersionScanStatus.java`, `file/VersionScanStatusConverter.java`, `file/FileVersion.java`, `file/FileVersionRepository.java`, `file/dto/FileVersionDto.java`, `file/FileVersionController.java`). frontend 무수정.
- **test 파일**: 2 신설 (`FileVersionRepositoryTest` 7건, `FileVersionControllerTest` 7건). A4 401건 테스트 회귀 0.
- **endpoint 신규**: 1개 — `GET /api/files/{fileId}/versions` (DESC 정렬, `isCurrent` 계산, soft-delete 404, `@PreAuthorize` READ).
- **ADR 변경**: **#29 closed** (deferred → "closed (A5, squash-merge `5155e00`)"). FileVersion entity/repo + GET versions까지 도입. POST `/versions` (업로드 commit) + restore는 A6+ 이월.
- **schema-validation issue**: 최초 commit에서 `FileVersion.checksumSha256`을 `columnDefinition="char(64)"`로 매핑 → CI에서 Hibernate logical type VARCHAR ↔ Postgres bpchar(CHAR) 불일치로 ApplicationContext 부트 실패 (Testcontainers 125건 cascade FAILED). `@JdbcTypeCode(SqlTypes.CHAR)` 주입 fix 후 CI green.
- **camelCase 정합**: `FileVersionDto`는 기존 `FileDto`/`FolderDto`와 동일하게 camelCase JSON 키. docs/02 §7.6 본문 예시는 snake_case로 적혀있어 closure에서 §7.6 본문 정합 patch (별도 commit).

### 핵심 결정 (A5 트랙, 확정)

1. **FileVersion entity Lombok-free** — A4 entity 패턴(`Folder`/`FileItem`) 보존. JPA AttributeConverter (`autoApply=false`) + `VersionScanStatusConverter`로 DB lowercase ↔ Java UPPERCASE 변환.
2. **`scan_result JSONB` entity 미매핑** — A5 list endpoint 응답 스키마 미포함 + JSONB↔JPA 의존성 회피 (audit 모듈과 동일 — JdbcTemplate 분리). 스캐너 워커 도입 시점에 별도 매핑 검토 (KISS — YAGNI).
3. **FileItem.currentVersionId 매핑 = UUID 컬럼** — `@OneToOne` 대신 단순 UUID. lazy proxy 비용/cycle 위험 회피 + service layer가 명시적 fetch.
4. **GET /versions 만 도입** — POST `/versions` (업로드 commit), restore, comment 수정 등은 A6+ 이월. ADR #29 deferred는 GET까지로 close 처리.
5. **CHAR vs VARCHAR 매핑 표준화** — `CHAR(N)` 컬럼은 entity에서 `@JdbcTypeCode(SqlTypes.CHAR) + @Column(length=N)`로 매핑. `columnDefinition` string은 logical type 결정에 무력 — 회귀 가드.

### accepted-deviation (A6+ 이월)

- POST `/api/files/{fileId}/versions` (업로드 commit + 멀티파트 + scan-status='pending' enqueue) — A4.8 file mutation 트랙과 별개로 진입 시점 미정.
- `FileVersion.scanResult` JSONB 매핑 — 스캐너 워커 도입과 동시 (위 결정 #2).
- 버전 restore (`PATCH /api/files/{id}/restore-version`) — 사용자 주도 롤백 UX와 함께 후속.
- docs/02 §7.6 본문 snake_case 예시 → camelCase 정합 patch — 본 closure commit에서 동시 처리.

### DoD 10/10

1. ✅ `VersionScanStatus` enum + converter — DB lowercase ↔ Java UPPERCASE 라운드트립 GREEN
2. ✅ `FileVersion` @Entity + V5 schema-validation GREEN (`ddl-auto=validate`)
3. ✅ `FileVersionRepository.findByFileIdOrderByVersionNumberDesc` + `existsByStorageKey` — Testcontainers 7건 GREEN
4. ✅ `FileVersionDto` record + `from(v, currentVersionId)` factory — `isCurrent` 계산 정확
5. ✅ `FileVersionController` GET endpoint + `@PreAuthorize("hasPermission(#fileId, 'file', 'READ')")` + soft-delete 404
6. ✅ Controller 통합 테스트 7건 — ADMIN/AUDITOR/MEMBER(no-grant 403)/MEMBER(grant 200)/missing 404/soft-deleted 404/currentVersionId NULL → 모두 GREEN
7. ✅ A4 evaluator 13건 + audit 회귀 0, audit_log REVOKE 정책 무영향 (read-only endpoint, audit emission 없음)
8. ✅ ADR #29 status "deferred" → "closed (A5, `5155e00`)" 정합. docs/00 §5 갱신
9. ✅ PR #13 CI green (backend junit + frontend vitest 모두 SUCCESS) + master squash-merge `5155e00`
10. ✅ dev-docs `dev/active/a5-file-versions/` → `dev/completed/` archive + docs/02 §7.6 camelCase/`NOT_FOUND` envelope 정합 patch

### 다음 단계 — A6 또는 docs/03~04 본문

- **A6 (이미 active)**: folder delete/restore + descendant cascade soft-delete + RESTORE_CONFLICT envelope (별도 worktree `dev/active/a6-folder-mutation-delete/`).
- **A5 잔여 → A6+ 이월**: POST `/versions` (업로드 commit), 버전 restore, scan worker, scanResult JSONB 매핑.
- **docs/03 §5~§8 본문**: 저장소 보안(KMS/presigned TTL), Legal Hold, 데이터 보호, 보안 회귀 가드 (코드 0줄 트랙).
- **docs/04 본문**: 관리자 페이지·쿼터·백업 (별도 트랙).

---

## 2026-04-29 — A5.0 docs/02 §7.6 + ADR #29 트리거 마커 (no-code)

### 범위
A5 마일스톤 진입점. ADR #29 deferred 클리어를 위한 docs 정합 patch.

### 변경
- `docs/02 §7.6 (Files)` GET `/api/files/:id/versions` 응답 스키마 본문 보강 (`versions` 배열 + `is_current` 플래그 + 정렬(version_number DESC)·soft-delete 404 정책 명시). _주: dev-docs(plan/tasks)는 §7.7로 표기되어 있으나 실제 표 위치는 §7.6 — A5 후속 phase에서 dev-docs drift 정정 예정._
- `docs/00 §5 ADR #29` 본문에 "A5 진입 (2026-04-29)" 트리거 마커 1줄 추가 (close는 A5.3 commit hash로 갱신 예정).

### 검증
- A5.0 commit 자체는 docs 3파일만 staged (코드 0줄). _작업 트리에 별도 세션 발 backend 변경 잔재 발견 — 본 commit 범위 외, 사용자 확인 대기._
- 다음 phase A5.1 FileVersion entity 작성이 본 §7.6 스키마와 1:1 정합.

---

## 2026-04-29 — 🏁 A4 마일스톤 종료 (Folder/File Domain + Resource-Level Permissions)

### 범위

A4.0 (docs/02 §2.3/§2.6/§7.10 정합 patch + ADR #27/#28/#29) → A4.1 (V5 마이그레이션 — folders/files/file_versions/permissions + UNIQUE COALESCE + DEFERRABLE FK + V5MigrationIT) → A4.2 (file/permission entity·repo + 재귀 CTE `findEffective`, Folder는 ownership 충돌로 A4.5 흡수) → A4.3 (`IbizDrivePermissionEvaluator` 내부 resource-level 교체 + 상속, A3 13/13 회귀 보존) → A4.4 (PermissionController 4 endpoint + `permission.granted/revoked` 실 emission, ADR #26 close) → A4.5 (Folder JPA entity + FolderRepository, A4.2 deferred 흡수) → A4.6 (FolderMutationService + create/rename/move + audit emit + cycle 가드 — delete/restore는 A6로 이월) → A4.7 (FolderController + 3 REST endpoint + RENAME_CONFLICT envelope + ADR #30 root=ADMIN-only).

### 회고

- **commits**: 9 (no-merges) on top of A3 close `6f0820d` → master HEAD `48e23a3`. PR 6건 (#6 A4-data, #7 evaluator, #8 perm-endpoint, #9 folder-entity, #10 mutation-service, #11 folder-endpoint) 모두 squash-merged + CI green.
- **production 파일**: 26 신설/변경 (backend `folder/` 11 + `permission/` 11 + `file/` 2 + `audit/` 1 + `common/error/` 1) + frontend는 enum mirror 무수정 (A3 1:1 mirror 그대로 유효).
- **test 파일**: 13 신설 (A4 신규 — `V5MigrationIT`, `FolderRepositoryTest`, `FolderMutationServiceTest`, `FolderControllerTest`, `PermissionRepositoryTest`, `PermissionResolverTest`, `IbizDrivePermissionEvaluatorTest`, `PermissionControllerTest`, `PermissionServiceGrantRevokeTest`, `PermissionAuditListenerTest` 등). A3 13개 evaluator 통합 테스트 회귀 보존.
- **마이그레이션**: V5 — `folders` (parent UNIQUE = `COALESCE(parent_id, ZERO_UUID)` 보강, soft delete `WHERE deleted_at IS NULL`) + `files` (`folder_id` FK, normalized_name UNIQUE) + `file_versions` (테이블만 도입, entity는 ADR #29로 A5 이월) + `permissions` (preset 단일 컬럼, ADR #28).
- **endpoint 신규**: 7개 — `POST/DELETE/GET /api/{resource}/{id}/permissions`, `GET /api/me/effective-permissions` (A4.4) + `POST /api/folders`, `PATCH /api/folders/{id}`, `POST /api/folders/{id}/move` (A4.7).
- **ADR 변경**: **#26 closed** (A4.4 — `permission.granted/revoked` 실 emission + 4 endpoint 도입), **#27/#28/#29 신규** (A4 PR 분할 / preset 단일 / FileVersion A5 이월), **#30 신규** (A4.7 — root parent 작업은 ROLE ADMIN-only SpEL 삼항 분기).
- **재귀 CTE**: `PermissionRepository.findEffective(userId, resourceType, resourceId)` — 부모→자식 상속 + grant 우선 lookup (ADR #28). evaluator는 SpEL 시그니처 `hasPermission(#id, 'folder', 'READ')` 그대로 보존, 내부만 교체 (ADR #26 보장사항 충족).
- **트랜잭션**: 모든 mutation = `@Transactional` + `SELECT FOR UPDATE` (FolderMutationService). audit emit은 `REQUIRES_NEW`로 분리 (ADR #24 보존). audit append-only `42501` 회귀 0 (V4 REVOKE 무영향).

### 핵심 결정 (A4 트랙 6+1, 확정)

1. **PR 2개 분할** — A4-data + A4-controllers, 의존 단방향. 추가로 A4-controllers 내부도 evaluator/perm-endpoint/folder-entity/mutation-service/folder-endpoint 5 sub-track으로 분기 (ADR #27)
2. **permissions = preset 단일 컬럼**, deny semantics v1.x 이월 (ADR #28). evaluator는 grant 우선 lookup만 (allow 발견 → true, 어디서도 grant 없으면 false)
3. **FileVersion entity/repo/CRUD A5 이월** — V5 schema는 테이블 + DEFERRABLE FK만 (ADR #29). `current_version_id` NULL 허용 유지
4. **root parent 작업 = ROLE ADMIN-only** — SpEL 삼항 `#req.parentId == null ? hasRole('ADMIN') : hasPermission(...)` (ADR #30). 노드 admin preset이 root 트리 구조 변경으로 번지지 않게 차단
5. **SpEL 호출 시그니처 0변경** — A3 `hasPermission(#id, 'folder', 'READ')` 그대로, evaluator 내부만 user-level → resource-level. controller `@PreAuthorize` 회귀 0 (ADR #26 보장사항 충족)
6. **Folder ownership 충돌 → A4.5 흡수** — A4.2에서 file/permission만 진행, master worktree `dev/process/20260428-a3-folder-mutation-service.md` ownership 해제 후 a4-folder-entity 세션에서 정리 + Folder JPA entity 신설

### accepted-deviation (A5 이월)

- `file_versions` entity/repository/CRUD endpoint — 버전 이력 UI/API는 별도 기능 (ADR #29). V5 schema는 테이블만 도입했으므로 A5는 entity/repo/endpoint만 추가
- 명시 deny semantics — `permissions.deny BOOLEAN` 또는 별도 `denies` 테이블 (ADR #28). 현 schema 호환 (컬럼 추가만으로 확장)
- LTREE 부서 계층 + `includeDescendants` — 부서 모델 자체가 A1.5 후속 (track 미정)
- File mutation/CRUD endpoint — A4는 Folder MVP까지만, File mutation은 A5 또는 별도 트랙 (5 endpoint 미구현)

### DoD 11/11

1. ✅ V5 마이그레이션 GREEN — 4테이블 + UNIQUE COALESCE + DEFERRABLE FK + REVOKE 무충돌 (V5MigrationIT 7+ 케이스)
2. ✅ `Folder`/`FileItem`/`PermissionRow` entity + repository + 재귀 CTE `findEffective`
3. ✅ A3 `PermissionEvaluatorIntegrationTest` 13/13 GREEN 회귀 보존 + resource-level 신규 테스트 GREEN
4. ✅ `PermissionService.grantPermission/revokePermission` + `PermissionGrantedEvent/RevokedEvent` + `PermissionAuditListener` 확장 — `permission.granted/revoked` 실 emission (REQUIRES_NEW)
5. ✅ `PermissionController` 4 endpoint + 가드 + 403 envelope `PERMISSION_DENIED`
6. ✅ `FolderMutationService` create/rename/move — `@Transactional` + `SELECT FOR UPDATE` + cycle 가드 (delete/restore는 A6로 이월)
7. ✅ `FolderController` 3 endpoint (create/rename/move) + `RENAME_CONFLICT` 409 envelope
8. ✅ ADR #26 status "deferred" → "closed (A4.4)" 표기 정합. ADR #27/#28/#29/#30 docs/00 §5 등록
9. ✅ A2 audit append-only 회귀 0 — V4 REVOKE 정책 무영향, `42501` 가드 보존
10. ✅ PR #6/#7/#8/#9/#10/#11 모두 CI green (backend junit + frontend vitest 둘 다 SUCCESS) + master squash-merge
11. ✅ dev-docs `dev/active/a4-folder-file-domain/` + sub-track 5건 → `dev/completed/` archive (parent + 5 sub-tracks 통합 closure)

### 다음 단계 — A5 또는 docs/03~04 본문

- **A5 후보**: `FileVersion` entity/repo + 버전 생성/조회/복원 endpoint (ADR #29 deferred 클리어). File mutation endpoint(rename/move/delete/restore) 흡수 검토.
- **docs/03 §5~§8 본문**: 저장소 보안(KMS/presigned TTL), Legal Hold, 데이터 보호, 보안 회귀 가드 (코드 0줄 트랙).
- **docs/04 본문**: 관리자 페이지·쿼터·백업 (별도 트랙).
- **stale PR 정리**: #3 (codex/a3-mutation-domain), #4 (codex/a3-folder-mutation-service) — A3 머지가 다른 경로로 끝났으므로 close 예정.

---

## 2026-04-29 — A4.0 docs/02 정합 patch (no-code)

### 범위
A4-data 트랙 진입점. ADR #27/#28/#29 결정을 docs/02 본문에 반영.

### 변경
- `docs/02 §2.3 folders` UNIQUE 인덱스: `parent_id` → `COALESCE(parent_id, ZERO_UUID)` 보강 + 보강 사유 주석 1줄 (root parent 다중 NULL 차단, ADR #27 보장사항).
- `docs/02 §2.6 permissions` `preset` 컬럼 코멘트에 "preset 단일 — deny v1.x 이월, ADR #28" 주석 1줄.
- `docs/02 §7.10` POST `/api/:resource/:id/permissions` Guard `'ADMIN'` → `'PERMISSION_ADMIN'` 정합 (구표기 alias 명시).

### 검증
- `git diff --stat backend/ frontend/` → 비어있음 (코드 0줄).
- 다음 phase A4.1 V5 SQL이 본 §2.3/§2.6 본문과 1:1 정합.

---

## 2026-04-29 — 🏁 A3 마일스톤 종료 (Permission Matrix + PermissionService)

### 범위
A3.0 (docs/03 §3 정합화 + ADR #26) → A3.1 (`Permission`/`Preset` enum + frontend 1:1 mirror) → A3.2 (`PermissionService` + `IbizDrivePermissionEvaluator` + 403 envelope) → A3.3 (`effectivePermissionsCacheKey` 정적값 → SHA-256 hex prefix 16자) → A3.4 (`permission.changed` audit emission via `RoleChangedEvent` + listener) → A3.5 (full `@SpringBootTest` + Testcontainers E2E — 매트릭스 + role change).

### 회고
- **commits**: 8개 (`ff5156c` dev-docs/ADR #26 → `aec7b74` docs/03 §3 표기 정합 + `permission.ts` placeholder → `4458feb` A3.1 enum + frontend mirror → `e1083e4` A3.2~A3.4 backbone (cache key hash + permission.changed) → `ccd766d` A3.5 E2E + closure 2건). diff vs origin/master 예상: backend +production 7파일 / +test 9파일, frontend +1파일.
- **테스트**: backend +6 단위 클래스 (`PermissionEnumTest`, `PresetMappingTest`, `PermissionServiceTest`, `PermissionServiceChangeRoleTest`, `PermissionCacheKeyServiceTest`, `PermissionAuditListenerTest`) + 1 슬라이스 (`PermissionEvaluatorIntegrationTest` 13 케이스) + 2 E2E (`PermissionEndpointE2ETest` 11 + `RoleChangeE2ETest` 2). 게이트 1 235 → 게이트 2 248 → A3.5 261 (총 +26). frontend 305 → 316 (`permission.test.ts` 11 케이스).
- **production 클래스**: 7 (permission/ 6 + common/error/ 1). `Permission`(9 values) / `Preset`(5 values + `permissions()`) / `PermissionService`(check + changeRole) / `IbizDrivePermissionEvaluator` / `PermissionCacheKeyService` / `PermissionDenyContext` / `RoleChangedEvent` / `MethodSecurityConfig` / `GlobalExceptionHandler.handleAccessDenied` (`PERMISSION_DENIED` envelope).
- **마이그레이션**: 0 — A3는 user-level (Role 기반) MVP, `permissions` 테이블 미도입 (resource-level은 A4 의존).
- **endpoint 신규**: 0 production (권한 grant/revoke endpoint는 file/folder 도메인 의존 → A4 이월). 기존 `/api/auth/login` + `/api/auth/me` 응답의 `effectivePermissionsCacheKey`만 hash로 wire 변경 (shape 동일, 값만 hex).
- **ADR 등록**: **#26** (`PermissionEvaluator` MVP 범위 — user-level만, resource-level 평가는 A4에서 evaluator 내부만 교체, SpEL 호출 시그니처 `hasPermission(#id, 'folder', 'READ')`는 docs/02 §7.10 그대로 채택, `permission.granted/revoked` emit도 A4 이월, `effectivePermissionsCacheKey`는 SHA-256 hex prefix 16자 — A1 deviation #2 해소).
- **frontend 통합**: `frontend/src/types/permission.ts` 신설 (1:1 mirror). 사용처는 UX 게이트 한정 (보안 boundary 아님 — CLAUDE.md §3 원칙 10). `effectivePermissionsCacheKey` 응답 shape 동일 — opaque token으로만 사용.

### 핵심 결정 (트랙 5+1, 확정)
1. user-level (Role 기반) MVP — `permissions` 테이블 + 재귀 CTE 상속 평가는 A4 이월. SpEL 호출 시그니처는 docs/02 §7.10 그대로 동결 (ADR #26)
2. PURGE 가드 = `hasRole('ADMIN')` — `Preset.admin`이 PURGE를 의도적으로 제외해 `hasPermission` 경로로는 어떤 role도 통과 불가 (docs/03 §3.2 line 333). 회귀 가드는 `PermissionEndpointE2ETest`의 `/purge` 매트릭스로 락
3. cacheKey = SHA-256 hex prefix 16자 — 입력 `<userId>:<ROLE>:v1` (`MATRIX_VERSION` bump → 일괄 invalidate hook). frontend는 opaque token으로만 사용 — 역파싱 금지 보장으로 매트릭스 변경 안전 (ADR #26)
4. emission = `RoleChangedEvent` + `PermissionAuditListener` (ADR #24 동형 — `AuthAuditListener` 패턴 1:1 채택). `AuditService.record`의 REQUIRES_NEW로 audit 무결성 (ADR #25 보존)
5. 403 envelope = `{ error: { code: 'PERMISSION_DENIED', message, details: { required, have } } }` — `PermissionDenyContext` (ThreadLocal) 1회 consume으로 evaluator → exception handler 정보 전달 (docs/03 §3.6)
6. enum 단일 진실 = 백엔드, frontend는 mirror — A2 패턴 동일

### accepted-deviation (A4 이월)
- `permission.granted` / `permission.revoked` emission — resource-level grant endpoint (POST/DELETE `/api/:resource/:id/permissions`)는 file/folder 도메인 부재로 A4. 본 phase는 `permission.changed`(role 변경)만 실 emit. enum 자체는 A2에서 등록 완료 (ADR #26)
- LTREE 부서 계층 + `includeDescendants` 평가 — 부서 모델 자체가 A1.5 후속 (A4)
- 권한 상속 재귀 CTE (docs/03 §3.4) — folder tree 부재 (A4)
- `effectivePermissions` resource-level 캐시 store — A3는 user-level hash key만, 실제 캐시 store는 v1.x

### DoD 11/11
1. ✅ docs/03 §3.1~§3.6 표기 정합 + 본문이 docs/02 §7 Guard 컬럼과 1:1 일치 (`aec7b74`)
2. ✅ `Permission` enum 9 values (`PermissionEnumTest`)
3. ✅ `Preset` enum 5 values + preset→permission set §3.2 표 동치 (`PresetMappingTest`)
4. ✅ `frontend/src/types/permission.ts` 1:1 mirror (`permission.test.ts` 11 케이스)
5. ✅ `PermissionService.check` 단일 진입점 + `IbizDrivePermissionEvaluator` SpEL hook (`PermissionEvaluatorIntegrationTest` 13 + `PermissionEndpointE2ETest` 11)
6. ✅ user-level MVP 평가 정책 (ADMIN=all, AUDITOR=READ, MEMBER=∅, PURGE는 hasRole(ADMIN) 가드)
7. ✅ `effectivePermissionsCacheKey` SHA-256 hex prefix 16자 — deterministic + collision-free (`PermissionCacheKeyServiceTest` 7 케이스)
8. ✅ `permission.changed` emission policy + 실 emit (`PermissionAuditListenerTest` 2 + `RoleChangeE2ETest` 2). granted/revoked는 ADR #26로 A4 이월 명시
9. ✅ `@SpringBootTest` E2E 매트릭스 (ADMIN/AUDITOR/MEMBER × READ/EDIT/PURGE) + role change scenario
10. ✅ `gradle test` + `pnpm test` 로컬 GREEN + PR #5 CI 그린 최종 확정 (run `25075778972`: backend junit + frontend vitest 둘 다 SUCCESS, commit `8ecff7d`)
11. ✅ ADR #26 docs/00 §5에 등록 (`ff5156c`)

### 다음 단계 — A4 진입점
- 폴더/파일 도메인 (`folders`, `files`, `permissions` 테이블 + UNIQUE 제약 + LTREE)
- POST/DELETE `/api/:resource/:id/permissions` endpoint → `permission.granted/revoked` 실 emit 호출처
- `IbizDrivePermissionEvaluator` 내부 교체 — resource-level grant + 재귀 CTE 상속 평가 (SpEL 호출 시그니처는 보존)
- `effectivePermissions` resource-level 캐시 store (v1.x 후보)

---

## 2026-04-29 — A3.2~A3.4 (게이트 2)

### 완료
- **A3.2** PermissionService + IbizDrivePermissionEvaluator + 403 envelope
  - `PermissionService.check(userId, role, resource, resourceId, permission)` user-level MVP (ADMIN=ALL, AUDITOR=READ, MEMBER=∅)
  - `IbizDrivePermissionEvaluator implements PermissionEvaluator` — Spring Security `@PreAuthorize("hasPermission(#id,'folder','READ')")` SpEL hook
  - `MethodSecurityConfig` (`@EnableMethodSecurity` + `DefaultMethodSecurityExpressionHandler` 빈에 evaluator 주입)
  - `PermissionDenyContext` (ThreadLocal) — evaluator deny 판정 시 required/have set을 1회 consume 형식으로 ExceptionHandler에 전달
  - `ApiError` (docs/02 §7.2 envelope) + `GlobalExceptionHandler.handleAccessDenied` → 403 `PERMISSION_DENIED` + `details.required`/`details.have`
  - `TestPermissionController` (`src/test/java`) + `PermissionEvaluatorIntegrationTest` 10 케이스 (ADMIN/AUDITOR/MEMBER × READ/EDIT/PURGE + 익명 401)
- **A3.3** effectivePermissionsCacheKey hash 교체
  - `PermissionCacheKeyService.computeKey(userId, role)` — SHA-256 hex prefix 16자 (lowercase), 입력 `<userId>:<ROLE>:v1` (`MATRIX_VERSION` bump → 일괄 invalidate hook). 7 unit tests
  - `LoginResponse.from(User, String cacheKey)`로 시그니처 변경, `AuthService.login` / `AuthController.me`에 service 주입 + 사전 산출 키 사용 (session attribute도 동일 키)
  - `AuthMeLogoutIntegrationTest` plaintext assertion → `[0-9a-f]{16}` regex로 교체
- **A3.4** permission.changed audit emission
  - `RoleChangedEvent(actorId, targetUserId, from, to)` record (publish는 트랜잭션 커밋 직전)
  - `PermissionAuditListener` — `@EventListener` for RoleChangedEvent → `AuditEventType.PERMISSION_CHANGED` row + `before`/`after` JSON `{"role":"..."}` (REQUIRES_NEW 보존, swallow + ERROR 로그)
  - `PermissionService.changeRole(targetUserId, newRole, actorId)` (`@Transactional`) — user.role 갱신 + repository.save + event publish, 같은 role no-op
  - `User.changeRoleTo(Role)` 도메인 mutator
  - 4 unit (`PermissionServiceChangeRoleTest`) + 2 unit (`PermissionAuditListenerTest`) + `PermissionServiceTest` constructor 적응

### 검증
- backend `./gradlew test`: **248 tests, 0 failures, 100% successful** (게이트 1 235 → +13)
- frontend `pnpm test`: **316 tests, 0 failures**

### accepted-deviation
- `permission.granted` / `permission.revoked` emission은 A4 (resource-level grant endpoint 도입 시점). 본 phase는 `permission.changed`만 실 emit (ADR #26)

### 다음 단계 (게이트 2 OK 대기 → A3.5)
- A3.5 E2E: ADMIN→MEMBER 변경 후 다음 요청 403 + audit `permission.changed` 1건 (full SpringBootTest + Testcontainers)
- 권한 매트릭스 전체 E2E: ADMIN/AUDITOR/MEMBER × hasPermission READ/EDIT/PURGE

---

## 2026-04-29 — A3.0 docs 정합 + ADR #26 (no-code phase)

### 완료
- **docs/03-security-compliance.md** 헤더 `현재 상태: 스켈레톤` → `§1·§2·§3·§4 본문 활성, §5·§6·§7·§8 일부 본문 진행 중` (line 4) — §3 본문은 이미 §3.1~§3.6 작성 완료 상태였으나 헤더가 stale했던 표기 정합
- **CLAUDE.md** §2 라우팅 표 "권한 매트릭스 (작성 예정)" → "권한 매트릭스 (§3)" / §4 계약 파일 표 `src/types/permission.ts` "(예정)" 제거
- **docs/00-overview.md** §5에 **ADR #26** 추가 — `PermissionEvaluator` MVP는 user-level (Role 기반) 평가만, resource-level은 A4 이월. SpEL 호출 시그니처(`hasPermission(#id, 'folder', 'READ')`)는 docs/02 §7.10 그대로 채택해 A4에서 evaluator 내부만 교체. `permission.granted/revoked` emit도 A4 이월 (A3는 `permission.changed`만), `effectivePermissionsCacheKey` SHA-256 hex prefix 16자 (A1 deviation #2 해소 예고)
- **frontend/src/types/permission.ts** placeholder 신설 (`export {}` + JSDoc backlink)

### 검증
- `pnpm typecheck` PASS, `pnpm lint` PASS

### 다음 단계 (A3.1 진입)
- backend `com.ibizdrive.permission.Permission` enum 9 values + `Preset` enum 5 values + frontend 1:1 mirror (RED→GREEN)

---

## 2026-04-28 — 🏁 A2 마일스톤 종료 (Audit Log Backbone)

### 범위
A2.0 (V3 audit_log 스키마 + V4 REVOKE) → A2.1a (AuditService + Enums + REQUIRES_NEW) → A2.1b (`@Audited` AOP + WebRequestContextHolder) → A2.2 (append-only 강제 검증, A2.0과 통합) → A2.3 (`GET /api/admin/audit` + role-based scope) → A2.4 (A1 인증 이벤트 emission via `ApplicationEventPublisher` + listener) → A2.5 (E2E 통합 — auth → audit_log → query) → A2.6 (frontend mock → real fetch).

### 회고
- **commits**: 20개 (a6076f0 dev-docs/ADR → 440b0b0 V3+V4 → fd28368/1196e11/cf0be93 A2.1a + fix → a0f9f7e A2.1b → 2fdad2d A2.3 → 7aaea19 A2.4 → 14cec52/3fd8c57/f1ab7a6/b8cc4f2 A2.5 + CI 부트스트랩 → 36896a8 A2.6 → 진행 doc 5건). diff vs origin/master: +3,372 / -131, 36 파일.
- **테스트**: backend +9 클래스 (`AuditLogSchemaTest`, `AuditLogAppendOnlyTest`, `AuditServiceTest`, `AuditedAspectTest`, `AuthAuditListenerTest`, `AuditQueryControllerTest`, `AuditQueryServiceTest`, `AuthAuditE2ETest`, `AuditQueryE2ETest`) — 단위/슬라이스/E2E 모두 그린. frontend `api.audit.test.ts` 7 케이스로 재작성 후 305/305 PASS.
- **production 클래스**: 13 (audit/ 11 + audit/dto/ 2). `AuditEvent`/`AuditEventType`(38) /`AuditTargetType`(7)/`AuditService`/`AuditQueryController`/`AuditQueryFilters`/`AuditQueryService`/`Audited`/`AuditedAspect`/`AuthAuditListener`/`WebRequestContextHolder` + `AuditLogEntryDto`/`AuditLogPageDto`.
- **마이그레이션 2종**: `V3__audit_log.sql` (스키마 + `target_type` CHECK 7값 + 인덱스 4개), `V4__audit_log_revoke.sql` (`app_user` role 생성 idempotent + REVOKE UPDATE/DELETE + GRANT INSERT/SELECT).
- **endpoint 1종**: `GET /api/admin/audit?fromDate&toDate&actorQuery&eventType&page&pageSize` — ADMIN/AUDITOR 전체, MEMBER scope=self, 익명 401 (service 단일 분기, controller `isAuthenticated()`).
- **ADR 등록**: **#24** (AOP `@Audited` + Spring Security `ApplicationEvent` 하이브리드 — `publishEvent(...)` 호출은 cross-cutting 신호로 침투 허용, 비즈니스 로직 0줄), **#25** (DB role 분리 + REVOKE UPDATE/DELETE → `42501`로 append-only 증명).
- **frontend 통합**: `api.getAuditLogs` mock 분기 + 60-row generator 완전 제거 → `fetch('/api/admin/audit?...', { credentials: 'include' })`. `next.config.ts` rewrite로 dev에서 same-origin 쿠키 흐름. wire shape는 M12 mock 표면과 1:1 동치.
- **DoD 10/10 충족**: (1) audit_log + 4 인덱스 ✅ (2) `42501` 증명 (`AuditLogAppendOnlyTest`) ✅ (3) Java enum 38값 = ts mirror 1:1 ✅ (4) `AuditService.record()` 단일 진입점 + AOP + listener 하이브리드 ✅ (5) AuthService 비즈니스 로직 0줄 변경 (publish 4지점만 추가, ADR #24 갱신) ✅ (6) role 기반 scope 분기 ✅ (7) `api.audit.test.ts` 7 케이스 PASS ✅ (8) ADR #24/#25 docs/00 §5 등록 ✅ (9) `gradle test` + `pnpm test` CI 그린 (run 25023235347) ✅ (10) backup 브랜치 `backup/pre-reset-20260427-0036` 보존 ✅.

### 핵심 결정 (트랙 5+1, 확정)
1. emission 위치 = AOP `@Audited`(`@AfterReturning`) + Spring Security `ApplicationEventPublisher` 하이브리드 — annotation grep 가능, 트랜잭션 롤백 자동 처리, AuthService 침투는 publish 호출만 (ADR #24)
2. 보존 3년 + Legal Hold 무기한 — docs/03 §4.3 명시값 채택, 월별 파티셔닝은 v1.x로 deferred (단일 테이블 MVP)
3. append-only 강제 = DB role 분리 (`app_user` INSERT/SELECT only) + REVOKE — `42501` SQLState로 RED 증명 (ADR #25)
4. read 권한 = `Role` enum 재사용. ADMIN/AUDITOR 전체, MEMBER `actor_id=self` — A3 권한 시스템 비의존
5. enum 단일 진실 = 백엔드, ts는 mirror — CI lint는 후속(MVP는 수동 동기 + frontend `audit.test.ts` 계약으로 회귀 가드)
6. frontend는 `api.getAuditLogs`만 fetch 교체 — UI/테스트 변경 0, M12 표면 보존

### 잔여 accepted-deviation 5건 (후속 phase 추적)
| # | 항목 | 추적 phase |
|---|---|---|
| 1 | 월별 파티셔닝 자동화 (현재 단일 테이블, 파티션 SQL 함수만 docs/02 §9.4 주석 보존) | v1.x |
| 2 | 콜드 스토리지 아카이빙 cron | v1.x |
| 3 | `audit.exported` runtime emission (CSV export endpoint 자체가 v1.x — enum만 정의) | v1.x |
| 4 | `file.viewed` emission (`audit_level=strict` 폴더 도입 필요 → 폴더 테이블 부재) | A4 |
| 5 | `user.password.changed` (PW 변경 endpoint 자체가 v1.x) | v1.x |
| 6 | dev seed 60건 + `frontend/e2e/audit.e2e.ts` (admin 로그인 UI 의존) | A1 frontend 인증 + admin 라우팅 선행 |

### 핵심 함정 회고
- **REQUIRES_NEW + `@DataJpaTest` visibility**: 외부 트랜잭션 commit 전 INSERT는 별도 connection에서 미가시 → FK 23503 위반. 해결: seed를 `TransactionTemplate(REQUIRES_NEW)`로 즉시 commit (A2.1a `1196e11`)
- **PostgreSQL inet 호스트 표기**: `203.0.113.42/32` 입력 → `/32` mask 자동 생략. 가정 금지, 실제 SELECT로 검증 (`cf0be93`)
- **CI testLogging 기본 격차**: 로컬 lenient / CI strict (Hibernate Validator email RFC 5321 64자 local-part). `testLogging.exceptionFormat = FULL` 영구 적용으로 향후 회귀 디버깅 도움 (`f1ab7a6` → `b8cc4f2`)
- **AuthService 표준 이벤트 부재**: custom flow(`AuthenticationManager` 미사용)라 자동 publish 없음. Option D = `publishEvent(...)` 호출 명시 추가 + ADR #24 갱신본 (cross-cutting 신호, 비즈니스 로직 0줄)

### 다음 마일스톤 안내
- **A3 — 권한 매트릭스 + `PermissionService`**:
  - `@PreAuthorize` + 권한 시스템 백엔드 권위 (HANDOFF 미정의)
  - `effectivePermissionsCacheKey` 단순 문자열 → 권한 변경 trigger 기반 hash로 교체
  - `permission.granted/revoked/changed` audit 이벤트 emit 시점 (현재 enum만)
- **A4 — 폴더/파일 도메인 + `audit_level=strict` (file.viewed)**
- A3 진입 전 본 PR(#tbd) 머지 + master 동기화

---

## 2026-04-26 — 🏁 A1 마일스톤 종료 (Backend Authentication)

### 범위
A1.0 (User schema + JPA) → A1.1 (PasswordEncoder + DbUserDetailsService) → A1.2 (SecurityConfig 본 wiring + CSRF) → A1.3 (LoginController + in-memory lockout) → A1.4 (`/me` + `/logout` + SecurityContext gap fix) → A1.5 (통합 시나리오 + dev-docs 기반 audit) → **A1.6 (session timeout 정책 — must-fix #1 close)**.

### 회고
- **commits**: 7개 (308c041 / 0dd2d65 / 10a524b / 06b9238 / ca4e309 / c34e640 / A1.6 신규)
- **테스트**: **156 tests** (152 pass + 4 Docker SKIP, 0 fail) — 6 클래스 (`SecurityIntegrationTest` 5 / `LoginAttemptTrackerTest` 4 / `LoginControllerIntegrationTest` 8 / `AuthMeLogoutIntegrationTest` 5 / `AuthScenarioIntegrationTest` 1 / `SessionValidityFilterTest` 4) + slice + repository (152 합산)
- **production 클래스**: 16 (auth/ 7 + auth/dto/ 2 + config/ 1 + user/ 4 + common/error/ 2)
- **endpoint 4종**: `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, `GET /api/auth/csrf` (docs/02 §7.4 매트릭스 충족)
- **ADR 등록**: #19 (BCrypt strength=12 + DelegatingPasswordEncoder), #20 (idle 30m sliding + absolute 8h + 5/15min lockout), #22 (`/me` shape — identity + role + permissionsCacheKey), #23 (in-memory lockout backing — MVP 단일 인스턴스). #20은 A1.6에서 full pass.
- **hidden gap fix 1건**: Spring Security 6 `SecurityContextHolderFilter` load-only 동작으로 `AuthService.login`의 명시 `saveContext` 누락 (A1.4 ca4e309에서 수정)
- **must-fix → resolved 1건**: must-fix #1 (세션 timeout 정책) — A1.6에서 `SessionValidityFilter` + `application.yml PT30M`로 close

### 잔여 accepted-deviation 3건 (후속 phase 추적)
| # | 항목 | 추적 phase |
|---|---|---|
| 1 | `audit_log` emission 미구현 — A1 plan에서 명시 deferred (`AuthService.login` 주석 `// (후속) audit insert`) | **A2** (audit + 권한 매트릭스 backbone) |
| 2 | `400 PASSWORD_CHANGE_REQUIRED` 분기 미구현 (현재 `mustChangePassword=true` flag만 응답에 포함) — ADR #21 | PW change endpoint phase (미배정) |
| 3 | `AuthScenarioIntegrationTest` 로컬 SKIP — Windows Docker 미가용. CI ubuntu-latest에서 실행 | PR push 후 `gh pr checks 1` 그린 = close 게이트 |

### 다음 마일스톤 안내 (A2)
- **A2 — Audit log backbone + 권한 매트릭스**:
  - `audit_log` 테이블 + append-only constraint (REVOKE UPDATE/DELETE — docs/03 §4 + CLAUDE.md §3 원칙 8)
  - `AuthService.login`의 `// (후속) audit insert`를 실제 emission으로 채움
  - `@PreAuthorize` + `PermissionService` (HANDOFF의 권한 매트릭스 백엔드 권위)
  - `effectivePermissionsCacheKey` 단순 문자열(`userId:role:v0`) → 권한 변경 trigger 기반 hash로 교체
- A2 진입 전 본 PR(#1) 머지 + master 동기화

---

## 2026-04-26 — A1.6 Session Timeout Policy (must-fix #1 close, TDD)

### 완료
- [A1.6] **`SessionValidityFilter`** — `OncePerRequestFilter`, `Clock` 주입(`Clock systemUTC` `@Bean` 신규). `req.getSession(false)` → 세션 부재면 pass-through. `issuedAt` attribute 부재(또는 Long 아님)면 pass-through (인증 전 요청). `(clock.millis() - issuedAt) >= 8h(ABSOLUTE_TTL_MS)` → `session.invalidate()` + `res.sendError(401)` + chain 차단.
- [A1.6] **`SecurityConfig` 변경** — `Clock` `@Bean`(systemUTC) + `addFilterAfter(sessionValidityFilter, SecurityContextHolderFilter.class)`. SecurityContext 로드 직후 absolute 만료 검사 → 만료 세션의 인증 컨텍스트가 다운스트림 인가 필터에 노출되는 것 회피.
- [A1.6] **`application.yml`** — `spring.session.timeout: PT8H` → `PT30M` (idle 진실 출처). 주석으로 분담 명시 (idle=Spring Session JDBC, absolute=SessionValidityFilter).
- [A1.6] **`SessionValidityFilterTest` 4건** — 단위 (Mockito + mutable `Clock`):
  1. 세션 없음 → pass-through, 응답 미터치
  2. 세션 있고 `issuedAt` 없음 → pass-through, invalidate 호출 안 됨
  3. 7h59m59s 경과 → pass-through, invalidate 호출 안 됨
  4. 정확히 8h 경과 → invalidate(1) + sendError(401) + chain 미호출
- [A1.6] **회귀** — 152 → **156 tests**, 0 fail, 0 err, 4 Docker SKIP 동일

### 핵심 결정 (filter SoT 정책)
- **idle 만료 진실 출처 = `application.yml` `spring.session.timeout: PT30M`** — Spring Session JDBC가 매 요청마다 `lastAccessedTime`을 갱신, 30분 무활동 시 자동 invalidate. 컨테이너 레벨에서 sliding 처리 → 별도 코드 불필요 (KISS).
- **absolute 만료 진실 출처 = `SessionValidityFilter`** — `AuthService.login`이 이미 set 중인 `issuedAt`(epoch millis) attribute + 8h 경계. yml만으로는 ADR #20 absolute 한도 강제 불가 → 본 필터가 must-fix #1의 정확 사유 close.
- **Clock 주입** — production은 `Clock.systemUTC()` `@Bean`, 테스트는 mutable `Clock`을 단위 테스트에서 직접 주입(필터 단독 생성). `@SpringBootTest` 통합 추가 안 함 — 8h 경계를 SpringBootTest로 검증하면 비용만 큼 (단위 4건으로 logic full coverage).
- **`OncePerRequestFilter` + `addFilterAfter(SecurityContextHolderFilter)` 위치** — SecurityContext 로드 직후, 인가 필터 이전. 만료 세션의 `Authentication`이 컨트롤러까지 도달하지 않음.

### 변경 파일
- `backend/src/main/java/com/ibizdrive/auth/SessionValidityFilter.java` (신규)
- `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java` (Clock @Bean + filter wire + SecurityContextHolderFilter import)
- `backend/src/main/resources/application.yml` (timeout PT8H → PT30M + 주석)
- `backend/src/test/java/com/ibizdrive/auth/SessionValidityFilterTest.java` (신규)
- `dev/active/a1-auth-impl/a1-auth-impl-plan.md` (A1.6 phase 추가)
- `dev/active/a1-auth-impl/a1-auth-impl-audit.md` (must-fix #1 RESOLVED 갱신, 통계 +4 tests)

### 블로커
- 없음

### 다음 세션 컨텍스트
- A1 마일스톤 종료 commit (`chore(A1): close milestone`) + PR body draft 사용자 확인 → push → CI 그린 확인 → A2 진입

---

## 2026-04-26 — A1.5 통합 시나리오 + 마일스톤 audit

### 완료
- [A1.5] **`AuthScenarioIntegrationTest`** — `@SpringBootTest` + Testcontainers Postgres 15-alpine. 9-step 종합 시나리오: CSRF 발급 → 로그인(200) → `/me`(200) → 4회 wrong PW(401×4) → 5회째(401)+카운터 5 → 6회째(423) → mutable Clock 16분 진행 → 재시도 성공(200, lockout TTL 만료 lazy 해제) → logout(204) → 로그아웃 후 `/me`(401). `disabledWithoutDocker=true` (로컬 Windows SKIP / CI ubuntu 실행). `LoginAttemptTracker`를 `@TestConfiguration` `@Primary` 빈으로 mutable Clock 주입 — 운영 빈 영향 0.
- [A1.5] **회귀** — `./gradlew test` 152 tests (148 pass + 4 Docker SKIP, 0 fail). commit `c34e640`
- [A1.5] **dev-docs 기반 수동 audit** — `gsd-audit-milestone` 스킬은 GSD `.planning/ROADMAP.md` + `phases/*/VERIFICATION.md` 구조 전제. 본 프로젝트는 dev-docs(Superpowers) 구조 → 스킬 강행 시 모든 step fail. 사용자 승인으로 plan 목표 상태(line 30~38)를 DoD source로 채택, audit 산출물 `dev/active/a1-auth-impl/a1-auth-impl-audit.md` 작성.
- [A1.5] **audit 결과** — DoD 5/7 pass + must-fix #1 (세션 timeout 정책 미구현) + accepted-deviation 3건. ADR #19/22/23 ✅ / #20 partial. 사용자 결정 (B): A1.6에서 즉시 fix → A1.6에서 #20 full pass + must-fix #1 RESOLVED.

### 핵심 결정
- **dev-docs 기반 수동 audit** — `.planning/` GSD 구조 부재로 `gsd-audit-milestone` / `gsd-sdk` 호출 불가. CLAUDE.md ULTIMATE INVARIANT #10 ("중단 및 보고")에 따라 강행 대신 dev-docs 구조에 맞춘 수동 audit 채택. audit 산출물 위치 = `dev/active/a1-auth-impl/a1-auth-impl-audit.md` (plan/context/tasks와 동일 디렉토리, 마일스톤 archive 시 함께 이동).
- **anti-pattern 기록** — HANDOFF의 `next_action`을 검증 없이 그대로 실행하는 패턴 (이전 HANDOFF가 작성한 `gsd-audit-milestone A1`이 본 프로젝트에서 fail). resume 후 next_action 첫 단계는 도구/구조 가용성 검증부터 (CLAUDE.md #10).

### 블로커
- 없음 (must-fix #1은 사용자 결정으로 A1.6 즉시 fix 채택 → A1.6에서 close)

### 다음 세션 컨텍스트 (A1.6)
- 사용자 결정 (B) — must-fix #1 즉시 fix. SessionValidityFilter RED+GREEN+회귀 + audit.md must-fix #1 RESOLVED 갱신.

---

## 2026-04-26 — A1.4 `/api/auth/me` + `/api/auth/logout` (+ A1.3 SecurityContext gap fix)

### 완료
- [A1.4] **`GET /api/auth/me`** — `AuthController.me(@AuthenticationPrincipal IbizDriveUserDetails)`. 응답 shape는 `LoginResponse` 재사용 (docs/02 §7.4 line 847-868: `{user, departments, roles, effectivePermissionsCacheKey}` — login과 동일). 미인증 → `anyRequest().authenticated()` + `HttpStatusEntryPoint(401)` 자동 차단
- [A1.4] **`POST /api/auth/logout`** — `session.invalidate()` + `SecurityContextHolder.clearContext()` + `Set-Cookie SESSION=; Max-Age=0; Path=/; HttpOnly`. 응답 204 (docs/02 §7.4 line 836-845). CSRF 미제공 → CsrfFilter 403, 미인증 → 401
- [A1.4] **A1.3 hidden gap 수정** — Spring Security 6의 `SecurityContextHolderFilter`는 load-only (5.x의 `SecurityContextPersistenceFilter` auto-save 제거). 인증 메커니즘이 `SecurityContextRepository.saveContext`를 명시 호출해야 세션에 컨텍스트가 영속화됨. `AuthService.login`은 session attribute만 set하여 후속 `/me`가 항상 401이 되는 hidden bug. `DelegatingSecurityContextRepository` 빈(HttpSession + RequestAttribute 동시 저장) + `HttpSecurity.securityContext()` wire + `AuthService`에 `SecurityContextRepository` 주입하여 `changeSessionId()` 직후 `saveContext` 호출
- [A1.4] **`AuthMeLogoutIntegrationTest` 5건** — `@WebMvcTest` slice. `/me` 인증/미인증, `/logout` 인증+CSRF / 인증+CSRF 미제공 / 미인증+CSRF (4 클래스, 22 테스트 PASS, 1m33s)
- [A1.4] commit `feat(A1.4): /api/auth/me + /api/auth/logout` (ca4e309)

### 핵심 결정
- **`/me`는 `LoginResponse` 재사용 (`MeResponse` 미생성)** — docs/02 §7.4 line 847-868의 `/me` 응답 shape는 `LoginResponse`(`{user, departments, roles, effectivePermissionsCacheKey}`)와 완전 동일. 별도 DTO 생성은 YAGNI 위반 (CLAUDE.md ULTIMATE INVARIANTS 원칙 3 — 기존 구조 우선). 추후 `/me` 전용 필드 도입 시점에 분리
- **`DelegatingSecurityContextRepository(HttpSession + RequestAttribute)`** — HttpSession은 영속, RequestAttribute는 단일 요청 캐시. 같은 요청 내 후속 필터/핸들러가 동일 컨텍스트를 일관되게 본다. `AuthService.login`이 `changeSessionId()` 직후 `saveContext(req, res)` 호출하여 새 세션에 컨텍스트 저장
- **logout: `session.invalidate` + `clearContext` + `Cookie SESSION; Max-Age=0`** — `clearContext`는 현재 요청 thread-local 정리. `Set-Cookie`는 클라이언트 브라우저 SESSION 쿠키 즉시 만료. `anyRequest().authenticated()` 가드로 미인증→401, CsrfFilter가 CSRF 미제공→403 자동 처리

### 다음 세션 컨텍스트 (A1.5)
- **A1.5** — `AuthScenarioIntegrationTest` `@SpringBootTest` + Testcontainers Postgres 15-alpine 1건 (`disabledWithoutDocker=true`, 로컬 SKIP / CI ubuntu 실행). CSRF 발급→로그인→/me→5회 wrong PW(401×5)→6회째=423→Clock 16분 진행→재시도 성공→logout(204)→/me=401. `LoginAttemptTracker`는 `@TestConfiguration` `@Primary` 빈으로 mutable Clock 주입
- 마일스톤 종료: `gsd-audit-milestone A1` + `progress.md` 마일스톤 블록 + commit + push + `gh pr checks` 그린

---

## 2026-04-26 — A1.3 LoginController + in-memory lockout (ADR #23)

### 완료
- [A1.3] **`POST /api/auth/login`** — `AuthController` + `AuthService.login` (`@Transactional`). 성공 시 `last_login_at` UPDATE + `changeSessionId()` (session fixation 방어). 실패 시 5/15min lockout 카운트
- [A1.3] **`LoginAttemptTracker`** — in-memory `ConcurrentHashMap<String, Attempt>`, key=lowercased email, 5회 실패 → 15분 잠금. `Clock` 주입 (테스트 시간 제어). 만료는 lazy 검증 (`isLocked` 호출 시점에 시계 기반 판정)
- [A1.3] **timing-safe BCrypt verify** — `@PostConstruct`에서 `passwordEncoder.encode()`로 dummy hash 동적 생성. 미존재 user / `is_active=false` / `locked_at != null` 모두 dummy verify 호출 → 실제 verify와 동일 시간. 응답은 INVALID_CREDENTIALS (계정 상태 누설 금지, docs/03 §2.3 enumeration 방지)
- [A1.3] **flat error shape** — `ErrorResponse(code, reason, retryAfterSec)` + `JsonInclude.NON_NULL`. `AuthExceptionHandler(@RestControllerAdvice)` → InvalidCredentialsException → 401, AccountLockedException → 423 + retryAfterSec. docs/02 §7.4 인증 specific 응답 (일반 envelope §7.2와 별개)
- [A1.3] **`User.recordLoginAt(OffsetDateTime)`** entity 메서드 — `last_login_at` setter (V2 컬럼)
- [A1.3] **`LoginAttemptTrackerTest` 4건 + `LoginControllerIntegrationTest` 8건** — 모두 PASS. @WebMvcTest slice. tracker singleton 격리는 `@BeforeEach`에서 `recordSuccess(테스트 키들)` 호출
- [A1.3] **ADR #23 docs/00 §5 등록** — lockout backing = in-memory ConcurrentHashMap (MVP 단일 인스턴스 가정). 다중 인스턴스/Redis 도입 시 `LoginAttemptTracker` interface 교체

### 핵심 결정
- **timing-safe dummy hash 동적 생성** — 정적 상수는 형식 오류 시 BCrypt iterations 미실행 → timing leak 위험. 부팅 +200ms 수용
- **비활성·관리자 잠금도 INVALID_CREDENTIALS로 매핑** — docs/03 §2.3 enumeration 방지. 잠금 누적은 별개로 ACCOUNT_LOCKED + retryAfterSec 노출
- **@Transactional은 AuthService.login에 적용** — last_login_at UPDATE + (후속) audit insert 단일 단위, docs/02 §7.4 TX=REQUIRED 충족
- **컨텍스트 9% 한계** — 본 세션은 commit만 수행. dev sync (context.md/tasks.md SESSION PROGRESS 갱신)는 다음 세션 시작 시 dev-docs-update로 처리

### 다음 세션 컨텍스트 (A1.4 ~ A1.5)
- **A1.4** — `GET /api/auth/me` (docs/02 §7.4 line 847-868: `{user, departments, roles, effectivePermissionsCacheKey}`. departments는 빈 배열 stub, kind='human' 하드코딩, roles=`[role]`, cacheKey=`userId:role:v0`) + `POST /api/auth/logout` (`session.invalidate()` + `Set-Cookie SESSION=; Max-Age=0`). 모두 `anyRequest authenticated`로 SecurityConfig 매처 변경 불필요
- **A1.5** — Testcontainers Postgres @SpringBootTest 종합 시나리오 1건 + `gsd-audit-milestone A1` + PR 머지

### 진행 정책
- 자율 모드 유지. 사용자 큐: A1.4 종료 시점 컨텍스트 65% 초과 시 자동 pause-work, 아니면 A1.5까지 이어감
- 다음 세션 첫 작업: dev-docs-update (이번 세션의 A1.3 SESSION PROGRESS 동기화) → A1.4 진입 (TDD RED 5건부터)

### 블로커
- 없음

---

## 2026-04-26 — A1.2 SecurityConfig 본 wiring (TDD, dev + Superpowers 첫 적용)

### 완료
- [A1.2] **`SecurityConfig` 본 wiring** — httpBasic/formLogin/logout 모두 disable, custom AuthController(A1.3+) 자리 마련. 매처 분리: `/api/health` + `/api/auth/csrf` + `/api/auth/login` permitAll, anyRequest authenticated. `HttpStatusEntryPoint(401)` — SPA용 (redirect 대신 401, docs/03 §2.4)
- [A1.2] **`CsrfTokenRepository` bean 추출** — `CookieCsrfTokenRepository.withHttpOnlyFalse()`. `CsrfTokenController`에서 `saveToken()` 명시 호출하여 deferred 모드 우회
- [A1.2] **`CsrfTokenController`** (`GET /api/auth/csrf`) — permitAll, `XSRF-TOKEN` cookie + `{ csrfToken }` body 동시 반환. Spring Security 6 deferred CSRF 함정(`getToken()`만으로는 cookie 자동 발급 보장 안 됨) 해결 — `repo.saveToken(token, req, res)` 명시 호출
- [A1.2] **`SecurityIntegrationTest` 5건** (@WebMvcTest slice, DB 무관) — getCsrf+cookie / POST without CSRF→403 / POST valid CSRF→401 / GET /me→401 / GET /health→200. **모두 PASS**
- [A1.2] **dev/active/a1-auth-impl/{plan,context,tasks}.md** + dev/process/{session}.md (dev 스킬 첫 bootstrap)

### 핵심 결정
- **CSRF plain handler** (`CsrfTokenRequestAttributeHandler`) — XOR mask 비활성화. docs/02 §7.1 평문 토큰 계약과 일치 (cookie ↔ header 단순 비교)
- **deferred CSRF 우회** — Spring Security 6에서 GET 응답에 cookie 자동 발급은 보장 안 됨 → controller가 `csrfRepo.saveToken()`을 명시 호출
- **로컬 logout disable** — A1.4에서 자체 endpoint로 처리 (Spring 기본 `LogoutFilter`는 form 기반)
- **A1.5 재정의** — HANDOFF.json의 권한 매트릭스 백엔드 권위는 별도 phase로 분리, A1.5 = 통합 시나리오 + 마일스톤 종료 (사용자 자율 결정)

### 다음 세션 컨텍스트 (A1.3 ~ A1.5)
- **A1.3** — LoginController + in-memory `LoginAttemptTracker` (5회 실패/15분 lockout, ConcurrentHashMap, Clock 주입). **ADR #23 docs/00 §5 추가 필요** (lockout backing store). timing attack 회피 (미존재 user에도 dummy BCrypt verify). `User.recordLoginAt(OffsetDateTime)` setter 추가
- **A1.4** — `GET /me` (ADR #22 응답 — `effectivePermissionsCacheKey = "userId:role:v0"` 임시) + `POST /logout` (HttpServletRequest.session.invalidate())
- **A1.5** — Testcontainers Postgres @SpringBootTest 종합 시나리오 1건 + gsd-audit-milestone

### 진행 정책
- 새 환경: dev(4) + Superpowers(12, TDD 강제) + GSD context-only. 트리거 경합 0
- 자율 모드 유지 — A1.3~A1.5는 새 세션(컨텍스트 클린)에서 동일 정책으로 진입

### 블로커
- 없음. A1.3 진입 시 ADR #23만 기록하면 됨

---

### 완료 (옵션 3 +α — 하이브리드 사이클)
- [A] **backend/ Spring Boot 3.3.4 + Java 21 scaffold** — `build.gradle.kts` (Kotlin DSL, toolchain 21), `settings.gradle.kts`, `gradle.properties`, `.gitignore`. 의존성: spring-boot-starter-web/security/data-jpa/validation, **spring-session-jdbc** (Redis 아님, ADR #12), postgresql, flyway-core + flyway-database-postgresql, software.amazon.awssdk:s3:2.28.16, jackson-databind. test workingDir = `projectDir`로 fixtures 상대경로(`../docs/`) 안정화
- [A] **`IbizDriveApplication.java`** — `@SpringBootApplication` 메인. **`HealthController`** `GET /api/health` → `{"status":"ok"}` (보안 설정 검증용)
- [A] **`SecurityConfig` skeleton** — `CookieCsrfTokenRepository.withHttpOnlyFalse()` (XSRF-TOKEN 쿠키 ↔ X-CSRF-Token 헤더, ADR #11), `/api/health` permitAll, 그 외 authenticated, httpBasic placeholder (A1에서 form/SSO 교체)
- [A] **`CorsConfig`** — `allowCredentials=true`, allowedOrigins from `${ibizdrive.cors.allowed-origins}`, exposed: `Tus-Resumable`/`Upload-Offset`/`Location`/`X-Request-Id`/`X-RateLimit-Remaining`. allowed: `Content-Type`/`X-CSRF-Token`/`X-Request-Id`/Tus 헤더 4종
- [A] **`application.yml`** — Spring Session JDBC (table-name `SPRING_SESSION`, **initialize-schema: never** = Flyway가 schema 관리), datasource localhost:5432/ibizdrive, S3 → MinIO localhost:9000, multipart **disabled** (tus 사용, ADR #13), session timeout `PT8H`, cookie `http-only=true`/`secure=false (dev)`/`same-site=lax`/name=`SESSION`
- [A] **`db/migration/V1__init.sql`** — Spring Session JDBC 스키마(`SPRING_SESSION` + `SPRING_SESSION_ATTRIBUTES`, 인덱스 IX1/IX2/IX3, ON DELETE CASCADE) + `users` stub(id UUID PK, email/display_name, password_hash nullable for SSO, deleted_at, partial unique index on `lower(email) WHERE deleted_at IS NULL`). 도메인 테이블은 V2+
- [A] **`docker-compose.yml`** — postgres:15-alpine + minio (RELEASE.2024-10-13) + minio-init 원샷(버킷 자동 생성). **Redis 없음** (JDBC 세션 + in-process SSE). healthcheck 포함
- [A] **A0: backend `NormalizeUtil.java`** — 7-step pipeline 완전 구현. `normalizeFileName` (1-2-3-6-7), `normalizedNameForDedup` (1-2-3-5-6-7, Locale.ROOT), `normalizeForSearch` (1-2-3-5-4-6, collapse). 제어문자 처리: NUL→throw / C0→space / DEL/C1/ZWSP(0x200B-200F)/BIDI(0x202A-202E)/WJ(0x2060)/BOM(0xFEFF) drop. 공백 통일: NBSP/U+2000-200A/202F/205F/3000→space. 검증: empty/length 255/`/`·`\\` 금지/trailing dot/예약어(CON/PRN/AUX/NUL/COM1-9/LPT1-9, base name 기준 case-insensitive). `NormalizationException` (code 필드, fixtures errorCodes 일치)
- [A] **A0: backend `NormalizeUtilTest`** — JUnit `@TestFactory` 동적 테스트, Jackson으로 `../docs/normalize-fixtures.json` 로드(IDE/Gradle 둘 다 동작하도록 fallback 경로 3종), 38 fixtures × 3 함수 = 114 dynamic test
- [A] **A0: frontend `src/lib/normalize.ts` 정식 작성** — backend `NormalizeUtil.java` 1:1 미러. 같은 7-step, 같은 에러 상수 6종, `NormalizationError extends Error` (code 필드)
- [A] **A0: frontend `src/lib/normalize.test.ts`** — Vitest, `readFileSync(resolve(__dirname, '../../../docs/normalize-fixtures.json'))`로 fixtures 로드, 38×3 = **114 tests PASS** (54ms)
- [A] **`.github/workflows/ci.yml`** — frontend(Node 20 + npm ci + typecheck/lint/test) + backend(Temurin 21 + gradle test) 두 잡. ADR #16 게이트: 어느 한쪽이든 fixtures mismatch면 머지 차단
- [A] **검증** — frontend: typecheck PASS · lint PASS · normalize 114 tests PASS. backend: 로컬 JDK 없어 컴파일 미실행 → CI에서 검증

### 핵심 구현 결정
- **Spring Session JDBC, Redis 미도입** — 사용자 명시(ADR #12). docker-compose에서 Redis 제거, application.yml에서 `store-type: jdbc` + `initialize-schema: never` (Flyway가 권위)
- **fixtures workingDir 처리** — Gradle test는 `workingDir = projectDir`(=`backend/`)로 고정, JUnit 테스트는 `../docs/normalize-fixtures.json` + IDE 케이스 대비 2개 fallback. 단일 진실 출처가 빌드 도구 따라 깨지지 않도록
- **NUL은 step 2에서 throw, step 7 검증 전 차단** — fixtures `control_001` 입력 `"a\u0000b.txt"` → ERR_NUL_CHAR. 빈 문자열로 만든 뒤 ERR_EMPTY로 떨어지면 디버깅 불편
- **JS `for...of` + `codePointAt`** — surrogate pair 안전. 공백 통일/제어문자 strip 모두 동일 패턴, Java `Character.charCount` 루프와 1:1 대응
- **터키어 İ 케이스(`case_002`)는 JS `toLowerCase()` 기본 동작과 Java `toLowerCase(Locale.ROOT)` 결과 일치** — `i̇`(i + U+0307). fixtures가 양쪽 cross-validation으로 보장

### 다음 세션 컨텍스트
- **A1 (인증) 진입 — 수동 모드 유지** — Spring Security `UserDetailsService`, BCrypt, login form, CSRF Cookie 전달, 8h 세션, audit 이벤트(LOGIN_SUCCESS/FAIL/LOGOUT). users 테이블 V2 확장(role/locked_at/last_login_at)
- **CI 미검증 영역** — backend Gradle test가 실제 GitHub Actions에서 통과하는지 확인 전. 첫 push 시 워크플로 실패하면 V1__init.sql/Flyway 의존성 정렬 점검 필요
- **로컬 개발 가이드 미작성** — `docker-compose up -d postgres minio minio-init` → `cd backend && ./gradlew bootRun`. README 또는 docs/00 §6에 추가 검토
- **A1.5 권한 매트릭스 코드화** — docs/03 §3 PermissionEnum 9종을 `com.ibizdrive.security.Permission` enum + Preset 매트릭스로 옮기고 `@PreAuthorize` SpEL helper 작성

### 블로커
- 없음. 사용자가 A1 spec 승인하면 진행

---

## 2026-04-25 — Track A 큐 #3: 정규화 spec + fixtures + SSE enum + PURGE 권한

### 완료 (큐 #3 — docs only)
- [A] **docs/00 §5 ADR 7건 추가** (#11~#17): Spring Boot, 쿠키 세션, tus-java-server, SSE, .env build-time, 정규화 fixtures 공유, 권한 매트릭스 백엔드 권위. ADR #7/#8은 §5.1 Superseded 섹션에 standard ADR 패턴으로 보존
- [A] **docs/00 §1.3 스택 갱신** — TBD → Spring Boot 3.x + Java 21 + 부속 스택 명시
- [A] **docs/00 §4.4 백엔드 마일스톤(A0~A7) 신설** — A6→A1.5 흡수, M5.1→A4 흡수, M12→A4 후반 통합
- [A] **docs/02 §7 endpoint 매트릭스 전면 재작성** — Auth/Folders/Files/Upload(tus)/Search/Shares/Permissions/Trash/Admin/SSE 그룹화. Guard(@PreAuthorize)/TX(REQUIRED + FOR UPDATE)/Norm(NormalizeUtil 적용 지점)/SoftDel(WHERE deleted_at)/Errors 5개 컬럼. tus finalize 7-step TX 상세 명시. 인증 헤더 JWT → 쿠키 세션 + CSRF double-submit 갱신
- [A] **docs/02 §3 정규화 spec 정식 명세화** — 7-step pipeline (NFC → 제어문자 strip → 공백 통일 → collapse → lowercase → trim → validate). 함수 분리표(filename/dedup/search), 단계별 제어문자 표(NUL/C0-C1/ZWSP/BIDI/BOM), 길이/금지문자/예약어/trailing dot 검증 규칙
- [A] **docs/normalize-fixtures.json 신규** (38 케이스): nfc(4) / whitespace(8) / control(4) / case(3) / extension(2) / forbidden(3) / reserved(4) / length(3) / dot(2) / unicode(3) / search(2). errorCodes enum 6종. Vitest+JUnit 양쪽 검증 게이트 (CLAUDE.md §3 원칙 11)
- [A] **docs/01 §15 SSE 본문 재작성** — ADR #14에 의해 폴링 폐기, MVP부터 SSE. SseEventType enum 16종(FILE 7 / FOLDER 6 / PERMISSION 3), useRealtimeSync 훅 구현, 이벤트→queryKeys 무효화 매트릭스, 연결 정책(자동 재연결/heartbeat/다중 폴더 구독)
- [A] **docs/02 §7.13.1 SSE enum 동기화** — FOLDER_UPDATED 분리(RENAMED), PERMISSION_GRANTED/REVOKED 추가, FILE_VERSION_CREATED/FOLDER_PURGED 신설
- [A] **docs/03 §3 권한 매트릭스 정식 명세화** — 권한 enum 9종(READ/UPLOAD/EDIT/MOVE/DOWNLOAD/DELETE/SHARE/PERMISSION_ADMIN/**PURGE**), Preset×권한 매트릭스, 시스템 ROLE(MEMBER/AUDITOR/ADMIN), 권한 상속(deny 우선 재귀 CTE), `@PreAuthorize` 패턴 예시, 403 응답 포맷
- [A] **검증** — 코드 변경 없음 → typecheck PASS · lint PASS · 190 tests PASS (회귀 없음)

### 핵심 설계 결정
- **PURGE는 ROLE ADMIN 전용** — 노드 admin preset에도 부여하지 않음. 노드 단위 권한 위임이 영구 삭제로 번지지 않도록 이중 안전장치
- **fixtures 단일 진실 출처** — `docs/normalize-fixtures.json`을 frontend(Vitest) + backend(JUnit) 양쪽이 로드, CI 게이트로 드리프트 차단. 38 케이스로 NFC/공백/제어/대소문자/예약어/길이/다국어 커버
- **터키어 İ는 Locale.ROOT lowercase** — `i̇`(i + U+0307 combining dot above) 결과. JS `toLowerCase()`와 Java `toLowerCase(Locale.ROOT)` 동일 동작 (fixtures `case_002`)
- **SSE 낙관적 setQueriesData 안 씀** — race 회피, 단일 무효화 경로(`invalidateQueries`)로 일관 처리
- **FILE_MOVED는 source+target 양쪽 fan-out** — `scope.folderIds` 배열에 두 폴더 ID 동시 포함, 클라가 양쪽 모두 무효화

### 다음 세션 컨텍스트
- **사용자 결정 대기** — 옵션 1: 로컬 인프라(docker-compose + JDK 21) → #4~#12 자율 / 옵션 2: 백엔드 팀 인계 / 옵션 3: 하이브리드(#4 scaffold만)
- **fixtures 누락 영역** — 현재 38 케이스에서 다루지 않은 영역: combining mark RTL 텍스트 시퀀스, surrogate pair, 일본어 가나/탁점 분리. 백엔드 구현 단계(A0)에서 NormalizeUtil 작성하며 추가 케이스 발견 시 fixtures 확장
- **A0 진입 시 검증** — `frontend/src/lib/normalize.ts` 신규 작성 + 기존 fixtures 로드 테스트. 현 frontend는 `normalizeFileName` 등이 별도 라이브러리에 없음 (mock backend가 모든 정규화 처리) — A0에서 분리 필요

### 블로커
- 없음 (큐 #4 진입은 사용자 결정 게이트)

---

## 2026-04-25 — Track H: docs/03 §1·§2 보강 (위협 모델 + 인증 흐름)

### 완료
- [H] **docs/03 §1 위협 모델** — Assets 표(A1~A7), Trust Boundary 다이어그램, **STRIDE 매트릭스**(Spoofing/Tampering/Repudiation/Info Disclosure/DoS/Elevation 카테고리별 자산·위협·완화책 표), 잔여 위험(out of scope) 명시
- [H] **docs/03 §2 인증** — 인증 방식(SSO/자체+MFA), 토큰 모델(access/refresh/sessionId 표), **시퀀스 다이어그램 3종**(로그인 SSO 콜백 / Access 만료+Refresh 회전 / 로그아웃), 비활성 정책 표, 서비스 계정 정책, audit 이벤트 동기화 메모
- [H] 백엔드 스택 미정 부분은 **"TBD: A 트랙에서 확정"** 으로 명시 (RateLimiter 구현체 / NestJS vs Spring / users.external_id / API 키 회전 주기)
- [H] **검증** — 코드 변경 없음 → typecheck PASS · lint PASS · 190 tests PASS (회귀 없음 확인)

### 핵심 설계 결정
- **클레임 최소화** — JWT는 sub/role/exp/iat/sessionId만, 권한 평가는 항상 DB. 토큰 단독 신뢰 금지(Spoofing 완화)
- **Refresh 1회용 회전 + replay 감지** — 이미 사용된 refresh가 다시 들어오면 세션 전체 강제 종료 + audit
- **app role과 audit role 분리** — DB 레벨 REVOKE UPDATE/DELETE on audit_log (CLAUDE.md §3 원칙 8과 일치)
- **STRIDE를 표 중심으로 정리** — 자산 ID(A1~A7) 참조 가능, 향후 §3 권한 매트릭스/§4 감사 정책에서 cross-link 용이

### 다음 세션 컨텍스트
- **A 트랙 (백엔드 합류)** — TBD 항목 채우기: NestJS or Spring 결정 → users.external_id 매핑 / RateLimiter 구현체 / API 키 회전 정책 / sessions 테이블 스키마
- **§3 권한 매트릭스** — 본 위협 모델의 "프론트 권한 우회"·"권한 상속 버그" 위협을 실제 엔드포인트×권한 매트릭스로 매핑 (현재 §3.1 preset만 존재)
- **§4 감사 정책 보강** — `user.session.revoked` 이벤트 추가 + audit_level=strict 폴더 정의 가이드

### 블로커
- 없음 (A 트랙 합류 전까지는 TBD 표기로 지연 가능)

---

## 2026-04-25 — Track G: BulkActionBar "이름 변경" 버튼

### 완료
- [G] **BulkActionBar 이름 변경 버튼** — 단일 선택 시만 활성, 클릭 시 F2와 동일한 `openRename(id, name)` 호출
- [G] **단일 항목 name lookup** — `useFilesInFolder(folderId, sort, dir)` 캐시에서 단일 선택 항목의 이름 조회. `useSortParams`로 현재 정렬 키 사용 → FileTable과 동일 캐시 슬롯 hit
- [G] **disabled UX** — 다중 선택 시 disabled + `title="단일 선택 시 사용 가능"` tooltip + aria-disabled
- [G] **테스트 4건 신규** — 단일 활성+다이얼로그 오픈 / 다중 비활성+tooltip / cache miss 비활성 / 폴더 단일 활성 (정책)
- [G] **검증** — typecheck PASS · lint PASS · 190 tests PASS (186→+4)

### 핵심 설계 결정
- **정책: count===1 활성, 폴더/파일 구분 없음** — RenameDialog와 백엔드(`api.renameFile`/`renameFile.test`)가 양쪽을 모두 지원하므로 BulkActionBar에서 추가로 막을 이유 없음. 추후 권한 모델(03 §3) 확정 시 `usePermission().edit` 게이트 외 추가 분기 필요 여부 재검토
- **Cache miss 시 안전 비활성** — items=undefined(로딩 중)일 때 `singleItem`이 없으면 disabled로 폴백. 로딩 끝나면 자동으로 활성화
- **`can.edit` 게이트만 사용** — 권한 없는 사용자에게는 버튼 자체 미노출 (BulkActionBar 다른 액션과 동일 패턴)

### 다음 세션 컨텍스트
- **권한 매트릭스(03 §3) 확정 후** — `can.edit` semantic 재검토 (folder rename은 `move` 권한? `edit`?)
- **Rename 외 단일 액션 추가 시** — 같은 패턴(`useFilesInFolder` lookup + count===1 게이트)으로 확장. 다수 단일 액션이면 `useSelectedSingleItem()` 헬퍼 추출 검토

### 블로커
- 없음

---

## 2026-04-25 — Track F: 다크 모드 토글 (TopBar Sun/Moon)

### 완료
- [F] **lib/theme.ts** — `THEME_STORAGE_KEY`/`getStoredTheme`/`getSystemTheme`/`getInitialTheme`/`applyTheme`/`persistTheme`. SSR 안전(window/document 가드), localStorage 에러 swallow (11 tests)
- [F] **hooks/useTheme** — mount effect로 SSR 동기화, toggle/setTheme/theme 반환
- [F] **components/topbar/ThemeToggle** — `lucide-react` Sun/Moon 아이콘, button + aria-pressed + aria-label, 키보드(Enter/Space) 동작 (5 tests)
- [F] **components/topbar/TopBar** — main 상단 banner, 우측 정렬에 ThemeToggle
- [F] **(explorer)/layout** — TopBar 마운트
- [F] **app/layout** — FOUC 방지 inline script (hydration 전 동기 [data-theme] 적용)
- [F] **`lucide-react` 추가** — frontend/package.json dependencies
- [F] **검증** — typecheck PASS · lint PASS · 186 tests PASS (170→+16)

### 핵심 설계 결정
- **`[data-theme="dark"]` on `<html>`** — globals.css가 이미 :root와 [data-theme="dark"]를 정의해 둠. JS는 attribute 토글만 담당
- **localStorage 우선 + prefers-color-scheme fallback** — 사용자가 한 번이라도 토글하면 그 선택을 영속, 그 전까지는 시스템 설정 따라감
- **FOUC 방지 inline script** — Next.js App Router는 RSC 첫 페인트 시점에 React 마운트 전 단계가 있음. `<head>`의 `dangerouslySetInnerHTML` 동기 스크립트로 해결 (theme.ts 로직과 동일 규칙 inline 복제)
- **role=switch 대신 button + aria-pressed** — eslint jsx-a11y 가 role=switch에 aria-checked 요구. button + aria-pressed가 토글 패턴에서 더 보편 (WAI-ARIA Button pattern)
- **lucide-react 도입** — 기존 의존성에 아이콘 라이브러리 없었음. 향후 다른 곳에서도 활용 가능

### 다음 세션 컨텍스트
- **시스템 prefers-color-scheme 변화 감지 미구현** — 사용자가 OS에서 라이트→다크 전환 시 자동 동기화 X. 필요 시 `matchMedia.addEventListener('change', ...)`를 useTheme에 추가
- **다크 모드 시각 검수 필요** — globals.css의 [data-theme="dark"] 토큰이 실제 컴포넌트(FileTable/Breadcrumb/UploadOverlay 등)에서 의도대로 보이는지 e2e 또는 수동 QA 필요
- **/admin 페이지에도 TopBar 적용?** — 현재는 (explorer)/layout만. admin/layout.tsx는 별도 헤더 — 통일 시 공용 컴포넌트로 승격 검토

### 블로커
- 없음

---

## 2026-04-25 — Track D: e2e Playwright 도입

### 완료
- [M_e2e] **@playwright/test 도입** + `playwright.config.ts` (chromium만, webServer 자동 기동, baseURL 3000, retries CI=2)
- [M_e2e] **e2e/routing.e2e.ts** — / → /files 리다이렉트 / 사이드바·breadcrumb / FolderTree 클릭 → URL+breadcrumb 갱신 / `/` 키 → app:focus-search 디스패치 (4 specs)
- [M_e2e] **e2e/move.e2e.ts** — BulkActionBar 이동 다이얼로그 → 대상 선택 → 토스트 / source 폴더 disabled (2 specs)
- [M_e2e] **e2e/trash.e2e.ts** — BulkActionBar 휴지통으로 → 토스트 + bar 사라짐 (1 spec)
- [M_e2e] **e2e/tsconfig.json** — main tsconfig에서 e2e/ 제외 + Playwright types 별도 설정
- [M_e2e] **package.json** — `test:e2e`, `test:e2e:ui` 스크립트 추가
- [M_e2e] **.gitignore** — playwright-report / test-results / playwright/.cache

### 핵심 설계 결정
- **chromium only (v1.0)** — 키바인딩/DnD 안정성 우선, firefox/webkit은 프로덕션 안정화 후
- **DnD 시나리오 → 다이얼로그 경로로 대체** — dnd-kit PointerSensor activationConstraint(distance:5px) + Playwright mouse 시퀀싱이 flaky. DnD 통합은 후속 (E2E_dnd_followup) — `page.mouse.move`를 50ms 단위 다단계 + `force: true` 필요
- **검색 input UI 미구현** — '/' 키 디스패치까지만 검증. 검색 input 도입 (M_search) 후 input.focus 검증 추가
- **휴지통 Undo 미구현** — soft delete + 토스트만. 복원 흐름은 M_trash 후속

### 다음 세션 컨텍스트
- **브라우저 미설치** — 첫 실행 전 `npx playwright install chromium` 필요. CI 통합 시 `actions/setup-node` 후 install step 추가
- **CI 통합은 후속** — `.github/workflows/e2e.yml`에서 webServer reuseExistingServer:false + retries 2
- **mock 데이터 기반** — MOCK_FILES/MOCK_TREE를 변경하면 e2e 셀렉터(영업팀/인사팀/내 드라이브) 동기화 필요
- **vitest 단위 테스트와 분리** — testMatch는 `*.e2e.ts`, vitest는 `*.test.ts(x)` — 충돌 없음

### 블로커
- 검색 input UI / 휴지통 Undo 미구현 — 시나리오 부분 적용. 후속 마일스톤에서 보강 예정

---

## 2026-04-25 — Track B: M12 감사 로그 페이지 (mock)

### 완료
- [M12] **types/audit.ts** — `AuditEventType` (docs/03 §4.1 mirror, audit.exported 포함) + `AuditLogEntry` + `AuditLogFilters` + `AuditLogPage`
- [M12] **api.getAuditLogs** — filters/page/pageSize → 정렬(occurredAt desc) + 60-entry mock 데이터 (6 tests)
- [M12] **lib/auditCsv.ts** — RFC 4180 quoting + `toAuditCsvBlob` (UTF-8 BOM, text/csv MIME) (6 tests)
- [M12] **hooks/useAuditLogs** — useQuery wrapper, queryKey에 filters/page 포함 → 자동 재요청 (2 tests)
- [M12] **/admin/audit/logs** — Filters + Table + Pagination + CSV export 페이지 (docs/04 §7)
- [M12] **components/audit** — AuditFilters (4 tests), AuditTable (6 tests, aria-rowcount/rowindex 포함), AuditPagination
- [M12] **/admin/layout.tsx** — 관리자 헤더 (감사 로그 링크)
- [M12] **docs/03 §4.1** — 클라이언트 mirror 표시 + audit.exported 추가
- [M12] **검증** — typecheck PASS · lint PASS · 170 tests PASS (M10 기준 146 → +24 신규)

### 핵심 설계 결정
- **mock 우선, 백엔드 분리 (A 트랙)** — getAuditLogs는 클라이언트 mock. 실제 연결 시 audit.exported 서버 기록 필요 (docs/04 §7.2)
- **CSV는 현재 페이지만 export (mock)** — 백엔드 연결 후 전체 필터 결과 서버 스트리밍으로 교체
- **필터 변경 시 자동 setPage(1)** — UX 일관성. 페이지네이션 키에 filters 포함되어 자동 refetch
- **UTF-8 BOM Blob** — Excel에서 한글 깨짐 방지. BOM 자체는 toAuditCsv 결과에 prefix
- **resourceType=null 허용** — 시스템 이벤트(system.backup.completed) 처리. UI는 `[type]` 또는 dash로 표시
- **즉시 반영 폼 (Apply 버튼 없음)** — onChange로 즉시 부모 setState. 디바운스는 actorQuery 입력 압박 시 부모에서 추가

### 다음 세션 컨텍스트
- **백엔드 연결 (A 트랙)** — `api.getAuditLogs`를 fetch로 교체 + `audit.exported` 서버 기록 추가
- **상세 뷰 (docs/04 §7.3)** — before/after diff 표시 + 같은 세션 이벤트 연결은 v1.x
- **IP/리소스 필터 (docs/04 §7.1)** — mock 범위 외. 백엔드 연결 후 UI 추가
- **권한 체크** — 감사 로그 접근은 admin role 필수. ProtectedRoute는 §3 권한 매트릭스 작성 후
- **/admin 다른 페이지** — dashboard / users / departments 등 docs/04 §2 라우트 v1.x

### 블로커
- 없음

---

## 2026-04-25 — Track C: 회고/리팩토링 (sonner 분리 + 무효화 헬퍼 + mock 공통화)

### 완료
- [C-1] **sonner 도입** — providers.tsx `<Toaster position="bottom-right" richColors />`
- [C-1] **hooks 토스트 분리** — `useDeleteBulk`/`useMoveBulk`/`useRenameFile`은 결과만 반환, 토스트는 호출부 (`Options.onSuccess/onError`)
- [C-1] **호출부 마이그레이션** — BulkActionBar / MoveFolderDialog / DndProvider / RenameDialog 모두 hook-level 콜백으로 토스트 호출
- [C-2] **lib/queryKeys.ts invalidations** — `afterFilesMoved` / `afterRename` / `afterDelete` 헬퍼. 무효화 매트릭스를 한 곳으로 집결 (3 hooks가 같은 룰 공유)
- [C-2] **filesListPrefix(folderId)** — sort/dir 변종 전체 prefix 매칭용 키 추가 (직접 단일 read는 filesInFolder)
- [C-3] **test/setup.ts** — sonner 글로벌 vi.mock (Toaster=null + toast methods=vi.fn)
- [C-3] **test/mocks/sonner.ts** — `toastSpy(method)` / `resetSonnerToastMock()` 공통 헬퍼
- [C-3] **호출부 테스트 갱신** — MoveFolderDialog.test (toast success/error 추가) / RenameDialog.test (toast success + inline-error-no-toast)
- [C] **검증** — typecheck PASS · lint PASS · 146 tests PASS

### 핵심 설계 결정
- **hook-level Options 패턴** — 호출부가 매번 mutate options을 넘기지 않고 hook 호출 시점에 콜백 등록. mutation 완료 시 컴포넌트가 mount되어 있어야 콜백이 발화 (TanStack Query v5의 onSuccess는 observer 기반)
- **MoveFolderDialog `close()` 즉시 호출** — ClientFilesPage에서 항상 mount되므로 다이얼로그가 isOpen=false로 null 반환해도 hook은 살아있음 → 콜백 발화 보장
- **RenameDialog는 onSuccess만 hook-level** — 실패는 inline alert(setError)로 다이얼로그 유지가 UX 우선
- **vi.mock 글로벌 setup** — vi.mock 팩토리는 호이스트되어 import된 심볼 참조 불가 → setup.ts에서 inline 팩토리로 처리. 헬퍼는 vi.mocked(toast)로 사후 접근

### v1.x 후속 (이번 스코프 제외)
- **ApiError 타입화** — `unknown` throws → 구조화 타입 (status/code/details)
- **vi.hoisted 표준화** — 일부 테스트의 vi.mock 패턴 통일
- **useStorageQuota env-driven** — quota 표시 컴포넌트의 환경변수 readout

### 다음 세션 컨텍스트
- **mutation hooks 추가 시 invalidations 헬퍼 재사용** — 새 패턴 발생 시 헬퍼에 추가
- **ApiError 타입은 백엔드 연결 (A 트랙) 시 필수** — 현재 mock은 `{ status, code }` plain object를 throw

### 블로커
- 없음

---

## 2026-04-25 — M10 완료 (고급 키보드 + 접근성 마무리)

### 완료
- [M10] **api.renameFile mock** — VALIDATION_ERROR (빈 이름) / RENAME_CONFLICT (같은 부모 정규화 중복) / NOT_FOUND, 폴더 시 MOCK_TREE도 갱신 (6 tests)
- [M10] **에러 코드 정합성** — docs/02 §8의 기존 코드 `RENAME_CONFLICT`(409) / `VALIDATION_ERROR`(400) 그대로 사용 (원칙 #12). 신규 코드 추가 없이 계약 유지
- [M10] **stores/renameUi.ts** — `{ isOpen, targetId, targetName, error }` + open/close/setError (3 tests)
- [M10] **useRenameFile** — markPending → renameFile → invalidate(filesInFolder/fileDetail/+folderTree+folder if folder) → unmarkPending + close, 실패 시 setError (5 tests)
- [M10] **RenameDialog** — role=dialog aria-modal, input focus + select-all, 이전 focus 복귀, role=alert 에러, 동일/빈 이름 disabled (7 tests)
- [M10] **useGlobalShortcuts** — `/` 키 → window CustomEvent 'app:focus-search' (input/textarea/contenteditable/modifier 시 무시, JSDOM contentEditable 폴백 포함) (7 tests)
- [M10] **FileTable handleKeyDown 확장** — Shift+↑↓ (anchor 유지 selectRange), Ctrl/Meta+↑↓ (focus only), F2 (단일 선택 또는 focus → openRename), Delete (selection or focus → confirm → useDeleteBulk)
- [M10] **ClientFilesPage 마운트** — RenameDialog + useGlobalShortcuts() 호출
- [M10] **검증** — typecheck PASS · lint PASS · 136 tests PASS (M7 기준 108 → +28 신규)
- [M10] **로드맵** — docs/01 §18 M10 행에 완료 마커(2026-04-25)

### 핵심 설계 결정
- **F2 다이얼로그 (인라인 X)** — 가상화 컨테이너에서 인라인 편집은 row 리렌더링으로 입력 휘발 위험. 다이얼로그는 MoveFolderDialog 패턴 그대로 재사용
- **Shift+↑↓ anchor 안정성** — selectRange가 lastClickedId를 변경하지 않으므로 진동 없음. M4 anchor 폴백(null/pending/폴더 변경) 그대로 동작
- **Ctrl/Meta+↑↓ alias** — 현재 ↑↓가 이미 focus-only이므로 modifier는 §12.1 키맵 명세 만족용 별칭
- **Delete native confirm** — 브라우저 내장 포커스 트랩/스크린리더 지원, MVP zero-cost. 디자인 일관성 필요해지면 ConfirmDialog로 교체 (M14/M15)
- **`/` 트리거는 lazy 이벤트** — 검색 입력 컴포넌트(M11/M14)와 디커플. listener 없으면 no-op
- **원칙 #6 (서버가 진실)** — RenameDialog는 빈 입력 외 client validation X. 충돌은 서버 RENAME_CONFLICT → setError로 다이얼로그 유지
- **에러 코드 계약 준수** — spec 작성 시 `NAME_CONFLICT`/`INVALID_NAME` 가정했으나 docs/02 §8에 이미 `RENAME_CONFLICT`/`VALIDATION_ERROR`가 동일 의미로 존재 → 코드를 docs에 정합 (원칙 #12)

### 다음 세션 컨텍스트
- 검색 입력 컴포넌트(M11) 마운트 시 `useEffect`에서 `window.addEventListener(FOCUS_SEARCH_EVENT, onFocus)` 등록 잊지 말 것 (`useGlobalShortcuts`가 이미 디스패치 중)
- ConfirmDialog 디자인 시스템 컴포넌트화는 M14 Visual Identity와 함께 검토 (현재 native confirm)
- 다중 선택 + F2 일괄 이름 변경(prefix 등)은 v1.x 범위
- 백엔드 연결 시 `api.renameFile`는 `PATCH /files/:id` 또는 `POST /folders/:id/rename`으로 교체
- BulkActionBar에 "이름 변경" 버튼 추가는 M14 Visual Identity와 함께 (단일 선택 시만 활성화)

### 블로커
- 없음

### 마일스톤 상태 (docs/01 §18)
- ✅ M1~M7, M10, M13 완료. M8 (권한 UI)는 docs/03 §3 작성 후 진입 가능. M11 검색은 M10의 `app:focus-search` 트리거 활용 가능.

---

## 2026-04-25 — M7 완료 (DnD 이동: dnd-kit + 다이얼로그 듀얼 경로)

### 완료
- [M7] **@dnd-kit/core 6.x 설치** — 이동 전용 (업로드 native DnD와 분리, 원칙 #7)
- [M7] **lib/folderTreeUtils.ts** — `findNode`/`containsNode`/`isSelfOrDescendantOfAny` 순수 유틸 (12 tests)
- [M7] **api.moveFiles mock** — MOCK_TREE/MOCK_FILES 상태 갱신, self/descendant/target 검증, 3 에러 코드 throw (5 tests)
- [M7] **에러 코드 추가 (docs/02 §8)** — `MOVE_INTO_SELF` / `MOVE_INTO_DESCENDANT` / `TARGET_NOT_FOUND` (원칙 #12)
- [M7] **stores/moveUi.ts** — Zustand 다이얼로그 슬라이스 (`isMoveDialogOpen`/`moveIds`/`moveSourceFolderId`) (3 tests)
- [M7] **components/dnd/types.ts** — `MoveDragData` 타입 + droppable id prefix
- [M7] **useDragPayload** — selection vs single-row 결정, containsFolderIds 캐시 조회 (4 tests)
- [M7] **useMoveBulk** — markPending → moveFiles → invalidate(source/target/folderTree/fileDetail) → unmarkPending (3 tests)
- [M7] **MoveDragOverlay** — `role="status" aria-live="polite"` 카운트 배지 (행 복제 X)
- [M7] **useFolderDroppable** — useDndContext로 active 읽음, isInvalid/isSameFolder/isOver/isDragging 플래그
- [M7] **DndProvider** — PointerSensor distance:5px (클릭 vs 드래그 구분), DragEnd 시 self/descendant/같은-폴더 no-op
- [M7] **explorer/layout.tsx** — DndProvider 마운트 (sidebar+main 모두 droppable 영역)
- [M7] **FolderTree / Breadcrumb / FileRow(폴더)** — drop 타겟 통합 + 시각화
- [M7] **FileRow draggable** — useDraggable + dragData 합성, hook 호출 순서 안정화 위해 비폴더 행도 droppable hook 호출 (`__not_a_target__`)
- [M7] **BulkActionBar 이동 버튼** — 스텁 제거, openMoveDialog(ids, folderId) 호출
- [M7] **MoveFolderDialog** — radiogroup 폴더 트리 picker, source/self/descendant disabled, Esc/Enter, role="dialog" aria-modal (5 tests)
- [M7] **ClientFilesPage 마운트** — UploadConflictDialog 옆에 MoveFolderDialog 마운트
- [M7] **린트 정리** — useDragPayload/useMoveBulk 테스트 wrapper에 displayName 부여
- [M7] **검증** — typecheck PASS · lint PASS · 108 tests PASS (M5 기준 76 → +32)
- [M7] **로드맵** — docs/01 §18 M7 행에 완료 마커(2026-04-25) + 핵심 DoD 추가

### 핵심 설계 결정
- **듀얼 진입**: 마우스(DnD) + 키보드(BulkActionBar 다이얼로그). 두 경로 모두 `useMoveBulk` mutation으로 수렴 (단일 책임)
- **DragOverlay = 카운트 배지** (`📎 N개 항목 이동 중`). 행 복제 X — 가상화/접근성 충돌 회피
- **자기/후손 차단 3중 방어**: ① useFolderDroppable.disabled (드롭 자체 불가) ② DndProvider.handleDragEnd 재검증 ③ api.moveFiles에서 throw
- **낙관적 업데이트 X** (원칙 #3): selection.markPending → mutation 완료 후 invalidate. 실패 시 unmarkPending만.
- **무효화 매트릭스 (원칙 #6)**: source/target `filesInFolder` (모든 sort/dir 변종, prefix 매치) + `folderTree` + 각 id의 `fileDetail`
- **`__not_a_target__` 트릭**: FileRow에서 폴더가 아닌 행도 useFolderDroppable을 호출해 React Hook 순서 안정화. 트리에 없는 id이므로 자동으로 드롭 비대상.

### 다음 세션 컨텍스트
- 백엔드 연결 시 api.moveFiles는 `POST /folders/:id/move` 또는 `POST /files/move-bulk`로 교체 (mock fakeXHR 패턴 유지)
- DragOverlay 카운트 배지의 i18n 처리는 v1.x 검색 마일스톤(M11)과 함께 처리
- Task 17 DnDProvider 통합 테스트는 jsdom DnD 한계로 스킵 — Playwright e2e에서 검증 (관련 항목은 M10 접근성/키보드 마일스톤에 함께)
- M8 권한 UI는 `usePermission()` 훅 이미 BulkActionBar에 사용 중 → 권한 매트릭스 (docs/03 §3) 작성 필요

### 블로커
- 없음

### 마일스톤 상태 (docs/01 §18)
- ✅ M1~M7 완료. M8(권한 UI)는 docs/03 §3 작성 후 진입 가능. M13(디자인 토큰) 별도 완료.

---

## 2026-04-25 — M13 완료 (Claude 디자인 시스템 토큰 적용)

### 완료
- [M13] `design-reference/` 번들 추가 (Claude 핸드오프: `IbizDrive.html` + `styles.css` 1318줄 + `README.md` + 4 jsx 참고용)
- [M13] `docs/design-system.md` 갱신 — §5에 M5 업로드 컴포넌트 매핑 섹션 추가, §10 Open Questions(다크 모드 토글, variant 범위, 폰트 로딩, 6열 확장)
- [M13] M5 업로드 컴포넌트 4종 className만 styles.css 치수에 맞춰 조정 (JSX/props/handlers/aria 변경 없음):
  - `UploadButton`: h-7 px-2.5 + border-accent (`.btn-primary` 매핑)
  - `UploadOverlay`: inset-2 + backdrop-blur-[2px] + accent 8% + rounded-lg (`.drop-overlay`)
  - `UploadQueueDock`: w-[340px] max-h-[420px] + border-border-strong + header bg-surface-2 + progress h-[2px] bg-surface-3
  - `UploadConflictDialog`: max-w-[460px] + 백드롭 rgba(0,0,0,.32)
- [M13] `.gitignore` 초기 생성 (`.tmp/`, `.claude/`, `node_modules/`, `.next/`, etc.)

### DoD
- ✅ typecheck / lint / test 76/76 통과
- ✅ SSR HTML 검증: `bg-bg`, `bg-surface-1`, `bg-accent`, `text-fg` 등 토큰 클래스 렌더링 확인
- ✅ CSS 번들 검증: `.bg-accent { background-color: var(--accent); }` 등 13종 토큰 유틸 정상 생성
- ✅ 코드베이스 전수 검사: `bg-white` / `bg-gray-*` / `text-gray-*` 0건
- ✅ 사용자 시각 확인 (스크린샷): 따뜻한 회색 배경, 인디고 accent 버튼, surface-1 사이드바 모두 토큰 값 적용 확인

### 원칙 체크
- ✅ 디자인 진실 출처: `design-reference/styles.css` → `globals.css` (이미 prior 세션 0315a04에서 적용) → `@theme inline`으로 Tailwind 유틸 노출
- ✅ "토큰만, 구조 변경 없음" 원칙 준수: JSX 트리, props, handlers, aria 속성 모두 그대로

### 사용자 결정 (2026-04-25 세션)
시각적 임팩트가 큰 추가 작업(TopBar / Lucide 아이콘 / FileRow 밀도 / StatusBar / SortChip / ViewSwitch / 6열 테이블 / RightPanel 탭)은 M13 범위에서 명시적으로 제외하고 후속 마일스톤으로 분리:
- **M14 Visual Identity** — TopBar + Lucide 아이콘 + FileRow 밀도 + StatusBar
- **M15 Layout Extras** — SortChip + ViewSwitch + StorageBar + RightPanel 탭
- **M16 Grid View** — FileTable grid 모드 (M14 ViewSwitch 의존)

→ `docs/01-frontend-design.md §18` 로드맵에 추가됨.

### 다음 세션 컨텍스트
**M14 진입 시 필요**
- 의존성 1개 추가 검토: `lucide-react` (아이콘) — 사용자 confirm 필요
- TopBar는 새 컴포넌트 (`src/components/layout/TopBar.tsx`) — `app/(explorer)/layout.tsx` grid를 `grid-rows-[48px_1fr]`로 재구성
- FileRow는 emoji → SVG 아이콘 매핑 테이블 도입 (mime → 아이콘)
- StatusBar는 사이드바 하단 또는 main 하단 고정 — 디자인 결정 필요
- 테스트 영향: FileRow 테스트가 emoji assertion을 쓰면 수정 필요 (현재 없음 확인 → 영향 0)

**브랜드 다크 모드 토글 UI**
- M13에서 토큰만 정의됨. 토글 UI는 M14 TopBar에 포함 검토.

### 블로커
- 없음

---

## 2026-04-25 — M5 완료 (업로드: multipart + 충돌 + 실패 분류)

### 완료
- [M5] `lib/uploadErrors.ts` 5종 분류 (network/permission/quota/server/conflict) + 단위 테스트 8개
- [M5] `lib/fakeXhr.ts` + 매직 파일명 6종 (normal/conflict.pdf/huge.bin/deny.txt/srv_500.any/net_fail.any) + 단위 테스트 7개
- [M5] `lib/api.ts#uploadFile` — FakeXHR 반환 (교체 경계: 실제 XHR로 교체 시 소비자 변경 없음)
- [M5] `stores/upload.ts` — queue/applyToAll/enqueue/updateTask/resolveConflict/retry/cancel/clearDone + 단위 테스트 8개
- [M5] `hooks/useUpload.ts` — store subscribe 기반 XHR orchestration + done시 `filesInFolder` invalidate + 단위 테스트 8개 (DoD o 포함)
- [M5] `hooks/useNativeFileDrop.ts` — `types.includes('Files')` 가드로 dnd-kit 분리 (원칙 #7), depth counter + 단위 테스트 4개
- [M5] `hooks/useUploadBeforeUnload.ts` — pending(`queued|uploading|conflict`) > 0 시 경고
- [M5] UI 5개: `UploadButton` / `FolderToolbar` / `UploadOverlay` / `UploadQueueDock` / `UploadConflictDialog`
  - UploadOverlay (2 tests), UploadQueueDock (4 tests), UploadConflictDialog (5 tests) — a11y (aria-modal, aria-labelledby/describedby, Esc=skip, Tab 포커스트랩, applyToAll)
- [M5] `FileTable` — `containerRef` + `useNativeFileDrop` + `UploadOverlay` 통합, early return을 body 변수로 리팩토링하여 Empty/Error/Forbidden 상태에도 drop 동작
- [M5] `ClientFilesPage` — `FolderToolbar` + `UploadQueueDock` + `UploadConflictDialog` + `useUploadBeforeUnload` 통합
- [M5] `FileTableEmpty` — `UploadButton` CTA 삽입 (재사용)
- [M5] `docs/01 §5.3` 구현 노트 추가 (paused/tusUrl/overwrite 제외 사유, pendingCount/enqueue/applyToAll/cancel 의미론)

### DoD
- ✅ typecheck / lint / test 전체 통과 (기존 30 + M5 46 = 76 passing)
- ⏳ 수동 검증 a~l, n — 사용자 브라우저 확인 대기 (pnpm dev → /files/root → 시나리오 12종)

### 원칙 체크
- ✅ #1 URL folderId canonical — `task.targetFolderId`는 `enqueue` 시점의 folderId 스냅샷 (Zustand는 "무엇을" 올릴지만 가짐)
- ✅ #3 낙관적 업데이트 비파괴적만 — 업로드 결과 낙관 append 금지, done 시 `invalidateQueries({ queryKey: [...qk.files(), 'list', folderId] })`로 prefix match
- ✅ #7 DnD 분리 — native는 `FileTable` 컨테이너만, `types.includes('Files')` 가드로 dnd-kit 이벤트 무시
- ✅ #12 에러 코드 — `lib/uploadErrors.ts`가 docs/02 §8의 status 코드 매핑 유지

### 다음 세션 컨텍스트
**M5.1 (tus 재개 업로드)**
- `UploadTask`에 `tusUrl`, `paused` 상태 도입
- `useUpload`의 transport만 교체 (store/UI 변경 없음)

**실제 백엔드 연결**
- `api.uploadFile` 내부를 실제 `XMLHttpRequest`로 교체 (인터페이스 동일)
- `FakeXHR` 파일 및 매직 파일명 테스트는 제거 또는 e2e로 이관

**M7 DnD 이동 + 드롭 타겟 확장**
- `useNativeFileDrop`을 `FolderTree` 노드에도 연결 (dnd-kit 이동과 공존)
- 동일 가드(`types.includes('Files')`)로 분리 유지

### 블로커
- 없음

---

## 2026-04-25 — 디자인 시스템 적용 (M5 업로드 구현 진입 전)

### 완료
- [DS] `docs/design-system.md` 신규 — 토큰 2-layer 전략 (base CSS vars + `@theme inline` 매핑), 컴포넌트별 클래스 매핑
- [DS] `frontend/src/app/globals.css` 재작성 — color/typography/radius/shadow/spacing 토큰, `[data-theme="dark"]` 다크 모드, focus-visible 전역 링, 스크롤바 스타일
- [DS] `(explorer)/layout.tsx` — 3-col 레이아웃 + 사이드바 브랜드 마크 (22px accent 사각형 + "IbizDrive" 14px semibold)
- [DS] `FolderTree.tsx` — active: `bg-accent-soft text-accent font-medium`, inactive: `hover:bg-surface-2 hover:text-fg`, 깊이 들여쓰기 유지
- [DS] `Breadcrumb.tsx` — 마지막 노드 `text-[15px] font-semibold text-fg`, 구분자 `›` `text-fg-subtle`
- [DS] `BulkActionBar.tsx` — `bg-accent-soft border-y` 바, `h-7 px-2.5 rounded` 버튼 패턴, 위험: `hover:bg-[color-mix(in_oklch,var(--danger)_12%,transparent)] hover:text-danger`
- [DS] `FileTable.tsx` — `GRID_COLS` 상수 추출 후 FileRow에 prop 전달, 헤더 `h-[30px] bg-surface-1 text-[11px] uppercase tracking-[0.04em]`
- [DS] `FileRow.tsx` — `gridCols` prop 수신, 상태별 class 토큰화 (pending/selected/hover), focus-visible은 globals.css 전역 링이 담당
- [DS] `RightPanel.tsx` — `w-[360px] bg-surface-1 border-l`, 상세 그리드 `grid-cols-[80px_1fr] text-[12px]`, 에러 `text-danger`
- [DS] Empty/Error/Forbidden/Skeleton 4개 상태 컴포넌트 — `flex-1 flex flex-col items-center justify-center gap-3 py-[60px]` 공통, danger 변형은 `bg-[color-mix(in_oklch,var(--danger)_10%,transparent)]`
- [DS] `ClientFilesPage.tsx` — 2-pane 래퍼 `flex flex-1 min-h-0`, 메인에 `bg-bg`
- [DS] route-level states — `loading.tsx` / `error.tsx` / `not-found.tsx` 모두 center-layout 상태 패턴으로 통일

### 원칙 준수 체크
- ✅ 구조·로직 변경 없음 (className만 수정)
- ✅ aria 속성 전원 유지 (aria-rowcount/rowindex, role=grid/row/gridcell, role=toolbar, aria-live, role=alert)
- ✅ focus-visible 링 가시성 유지 (globals.css에서 전역 `:focus-visible` 스타일)
- ✅ §19 원칙 1~5 영향 없음 (URL 진실 출처, query-param RightPanel, pending 낙관, DnD 분리 원칙 모두 그대로)
- ✅ 새 의존성 추가 없음 (폰트/아이콘 패키지 생략, 시스템 폰트 + 이모지 유지)

### 토큰 설계 요약
- Base vars는 `:root`에 raw 값(`oklch`/`#hex`)으로 정의, `[data-theme="dark"]`가 오버라이드
- Tailwind 4의 `@theme inline`이 base vars를 util class로 노출: `--color-bg`, `--color-surface-1`, `--color-accent`, `--color-fg-muted` 등
- 결과: `bg-bg` / `bg-surface-1` / `text-fg-muted` / `text-accent` 같은 utility가 `var(--bg)`를 참조 → 다크 모드 전환 시 className 변경 0건

### DoD
- typecheck: 통과
- lint: 통과
- test: 30/30 통과 (기존 테스트 regressions 없음 — className 전용 변경이라 snapshot/DOM 쿼리 영향 없음)
- 브라우저 시각 검증: 대기 (사용자 확인 권장)

### 다음 세션 컨텍스트
**M5 (업로드) 구현 재개**
- 승인된 spec: `docs/superpowers/specs/2026-04-25-m5-upload-design.md`
- 다음 단계: writing-plans skill → implementation plan 작성 후 구현 진입
- 새 UI 요소(UploadButton, UploadToasts, ConflictDialog)는 이번 디자인 토큰 체계 위에 구축

**다크 모드 활성화**
- 현재 `[data-theme="dark"]` 셀렉터로 정의됨. 토글 UI는 M9(설정)로 보류
- 테스트: DevTools에서 `<html data-theme="dark">` 수동 설정 시 전환 확인 가능

### 블로커
- 없음

---

## 2026-04-25 — M6 완료 (RightPanel + useOpenFile + ?file= 자동 제거) ✅ 브라우저 검증 통과

### 완료
- [M6] `hooks/useOpenFile.ts` — §17.5 설계 그대로. `?file=` query param 진실 출처. open/close/fileId 반환, replace + scroll:false, 다른 param 보존
- [M6] `hooks/useFileDetail.ts` — `qk.fileDetail(id)` 캐시 키, enabled:Boolean(id), staleTime 30s
- [M6] `api.getFileDetail(id)` MOCK — 404 throw 포함
- [M6] `components/files/RightPanel.tsx` — role=complementary, 이름/유형/크기/수정일/수정자 dl, 로딩 스켈레톤, 에러 role=alert, 닫기 버튼, document keydown Esc 리스너 (fileId 있을 때만 등록)
- [M6] `ClientFilesPage` 2-pane 레이아웃: 좌측 Breadcrumb+BulkActionBar+FileTable, 우측 RightPanel (fileId 없으면 null 반환해 공간 미차지)
- [M6] `FileTable.handleOpen` 리팩터 — 인라인 URL 조작 제거, `useOpenFile().open()` 사용. 분기 확정: folder → router.push, file → openFile(id)
- [M6] `hooks/useCloseFileOnFolderChange.ts` — 폴더 변경 시 `?file=` 자동 제거. prevRef로 초기 마운트는 건너뜀 (딥링크 보존). M3 "folderId 변경 시 focus/selection reset"과 대칭
- [M6] test/setup.ts에 afterEach cleanup 추가 (testing-library v16 + globals:false 조합에서 자동 cleanup 미작동 → 문서 레벨 리스너/DOM 누적 버그 방지)

### 계약 파일 추가/수정
- frontend/src/hooks/useOpenFile.ts                          신규 (docs/01 §17.5)
- frontend/src/hooks/useFileDetail.ts                        신규 (docs/01 §6.1 키 사용)
- frontend/src/hooks/useCloseFileOnFolderChange.ts           신규 (M3 대칭 정책)
- frontend/src/hooks/useOpenFile.test.ts                     신규 (5 tests)
- frontend/src/hooks/useCloseFileOnFolderChange.test.ts      신규 (5 tests)
- frontend/src/components/files/RightPanel.tsx               신규 (docs/01 §11, §12.1)
- frontend/src/components/files/RightPanel.test.tsx          신규 (5 tests)
- frontend/src/lib/api.ts                                    수정 (getFileDetail 추가)
- frontend/src/components/files/FileTable.tsx                수정 (useOpenFile로 refactor)
- frontend/src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx 수정 (RightPanel 통합, 2-pane, useCloseFileOnFolderChange 호출)
- frontend/src/test/setup.ts                                 수정 (cleanup 추가)

### 미결 결정 사항 (이번 세션 확정)
1. **폴더 이동 시 `?file=` 자동 제거: YES** — 파일은 특정 폴더 컨텍스트이므로 폴더가 바뀌면 패널은 의미 없음. M3 focus/selection reset과 대칭. 딥링크는 prevRef=null 조건으로 보존
2. **단일 클릭 = 패널 열기: 유지 (현재 Enter/더블클릭만)** — 단일 클릭은 M4에서 "선택"으로 합의됨. Windows/Mac 파일 탐색기 표준 UX와 일치. 빠른 미리보기는 향후 M10 별도 설계

### 원칙 준수 체크
- ✅ §19 원칙 2 (RightPanel은 query param, parallel route 아님) — `?file=` 유지
- ✅ §19 원칙 1 (URL이 "어디"를 소유) — 파일 선택 상태도 URL query에 존재
- ✅ Esc 정책 (§12.1) — RightPanel 전역 리스너가 닫기 담당. FileTable grid의 Esc(선택 해제)는 독립. 둘 다 누르면 각자 동작 (상호 방해 없음)

### DoD
- typecheck: 통과
- lint: 통과
- test: 30/30 통과 (기존 15 + useOpenFile 5 + RightPanel 5 + useCloseFileOnFolderChange 5 = M6 15개 신규)
- 브라우저 수동 검증: 통과 (A~F 섹션 15 시나리오)

### 회고 — testing-library v16 + vitest globals:false cleanup 이슈
- 증상: RightPanel 테스트에서 "Found multiple elements with role button and name 패널 닫기"
- 원인: vitest.config의 `globals: false` → `@testing-library/react`의 자동 afterEach cleanup이 비활성. 테스트 간 DOM이 `document.body`에 누적되어 이전 테스트의 aside가 남아 있었음. 동시에 RightPanel의 `document.addEventListener('keydown')` 리스너도 누적됨
- 해결: `test/setup.ts`에서 `afterEach(cleanup)` 수동 등록. 앞으로 컴포넌트 테스트 추가 시 자동 적용됨
- 교훈: `globals: false`를 선호한다면 cleanup/matchers는 setup에 명시적으로 넣어야 함

### 회고 — 폴더 변경 시 ?file= 자동 제거 설계
- 자연 네비게이션(FolderTree/Breadcrumb Link 클릭, handleOpen router.push)은 이미 ?file=을 자동 탈락시킴. 따라서 `useCloseFileOnFolderChange`는 엣지 케이스(back/forward, 프로그래밍 navigation, 딥링크 뒤 이동)를 위한 안전망 역할
- 딥링크(`/files/foo?file=x` 초기 마운트) 보존을 위해 `prevRef.current === null`일 때는 건너뜀. 이후 이동부터 동작
- 훅 분리 이유: 단일 callsite이지만 ref+초기 스킵 로직이 비자명 → 단위 테스트로 회귀 방지 (CLAUDE.md의 "premature abstraction 금지" 원칙에 근접하나 테스트 가치로 상쇄)

### 다음 세션 컨텍스트
**M5 (백엔드 API 연결)**
- MOCK(api.ts) 전체 → 실제 fetch로 교체. 계약은 docs/02 §7
- `getFileDetail`은 현재 `FileItem` 그대로 반환. 백엔드 계약에서 권한·버전·공유자 등 포함하면 `FileDetail` 타입 분리 필요
- 에러 코드 (docs/02 §8) 매핑 후 RightPanel 에러 상태 메시지 세분화

**M7 (권한)**
- RightPanel에 권한 기반 액션 버튼 (다운로드/공유/이름변경) 추가 자리 있음
- usePermission 실제 훅 교체 후 확장

**M10 (고급 키보드)**
- 단일 클릭 = 빠른 미리보기 UX 재검토 지점 (이번 세션에서 유지 결정)
- Space로 패널 토글 등 탐색기 스타일 옵션 검토

**후속 개선 (우선순위 낮음)**
- ClientFilesPage의 canonical redirect가 현재 pathname만 비교, `router.replace(canonical)`에서 query를 전부 탈락시킴. `?file=` 및 `?sort=` 등이 의도치 않게 날아갈 가능성. 재현되면 canonical redirect에 query 보존 로직 추가

### 블로커
- 없음

---

## 2026-04-25 — M4 완료 (선택 모델 + BulkActionBar) ✅ 브라우저 검증 통과

### 완료
- [M4] selection store (stores/selection.ts) + Vitest 단위 테스트 12개
  - §5.1 스펙 + markPending이 selection에서 자동 제거 (상호배제)
  - selectRange 앵커 폴백 3케이스 (null / pending / 폴더 외)
- [M4] usePermission 스텁 훅 (§14.2 시그니처, M7 교체 예정)
- [M4] useDeleteBulk 훅 + 3케이스 단위 테스트 (성공 / 실패+같은폴더 / 실패+다른폴더)
- [M4] BulkActionBar (role=toolbar, aria-live=polite, count===0 숨김)
- [M4] FileRow: aria-selected 실제 연결, pending 시 opacity+스피너+aria-disabled, onClick(item, MouseEvent) 시그니처
- [M4] FileTable: aria-multiselectable 복원, 키보드 Space/Ctrl+A/Esc clear, ArrowUp/Down pending 스킵, markPending focus 보정 useEffect, 폴더 변경 시 clear
- [M4] Vitest + jsdom + @testing-library/react 테스트 인프라 세팅
- [M4] api.deleteBulk mock 추가
- [M4] eslint.config.mjs: .next/build ignore 추가 (lint 오경보 수정)
- [M4] BulkActionBar selector 무한 렌더 버그 수정 (a589032)
- [M4] next.config.ts allowedDevOrigins 추가 (127.0.0.1 dev 접근 허용)

### 계약 파일 추가/수정
- frontend/src/stores/selection.ts               신규 (docs/01 §5.1)
- frontend/src/hooks/usePermission.ts            신규 (docs/01 §14.2 스텁)
- frontend/src/hooks/useDeleteBulk.ts            신규 (설계안 §2.5)
- frontend/src/components/files/BulkActionBar.tsx 신규 (docs/01 §8.2)
- frontend/src/components/files/FileRow.tsx      수정 (Props 시그니처 변경)
- frontend/src/components/files/FileTable.tsx    수정 (selection 연결)
- frontend/src/lib/api.ts                        수정 (deleteBulk 추가)
- frontend/src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx  수정 (BulkActionBar 렌더)

### 설계 문서 업데이트
- docs/01 §5.1 하단에 구현 노트 (상호배제, 앵커 폴백) 추가
- docs/superpowers/specs/2026-04-25-m4-selection-bulkactionbar-design.md 신규 작성
- docs/superpowers/plans/2026-04-25-m4-selection-bulkactionbar.md 신규 작성

### DoD
- typecheck: 통과
- lint: 통과
- test: 15/15 통과 (selection 12 + useDeleteBulk 3)
- **브라우저 수동 검증: 12/12 시나리오 통과** (클릭/Ctrl+click/Shift+click/Space/Ctrl+A/Esc/폴더이동 clear/휴지통 pending→삭제→invalidate 포함)

### 버그 재발 방지 — Zustand v5 selector 무한 렌더
- **증상**: "Maximum update depth exceeded" (BulkActionBar 마운트 직후)
- **루트 원인**: `useSelectionStore((s) => Array.from(s.ids))` — selector가 매 snapshot 호출마다 **새 배열 참조** 반환
- **메커니즘**: Zustand v5는 `useSyncExternalStore` 기반. React가 매 render마다 이전/신규 snapshot 참조 비교 → 항상 "변경됨" 판정 → 무한 재렌더
- **올바른 패턴**: selector는 store에 **이미 존재하는 안정 참조**(Set/Map/객체 자체)만 반환. 파생 변환(Array.from, filter, map, spread)은 selector 밖 render 본문에서 수행
  ```tsx
  // ❌ 금지
  const ids = useSelectionStore((s) => Array.from(s.ids))
  // ✅ 권장
  const idsSet = useSelectionStore((s) => s.ids)
  const ids = Array.from(idsSet)
  ```
- **예외**: 변환이 꼭 필요하면 `useShallow` 또는 `zustand/shallow` equality 함수 명시. 하지만 대부분은 위 패턴이 충분하고 단순함

### 다음 세션 컨텍스트
**M5 (백엔드 API 연결)**
- MOCK(api.ts)을 실제 fetch로 교체. 계약은 docs/02 §7
- useDeleteBulk 실패 경로: 현재 console.warn → 토스트 라이브러리 통합 (sonner 후보)
- 에러 코드 (docs/02 §8) 매핑: 409 CONFLICT / 403 FORBIDDEN / 423 LOCKED 등 UI 메시지

**M6 (DnD 이동)**
- BulkActionBar 이동 버튼: 현재 스텁 → dnd-kit + 이동 다이얼로그
- 업로드 DnD(window native)와 컨텍스트 분리 원칙(§19) 유지
- pendingIds 재사용 (이동 중인 row는 pending UI)

**M7 (권한)**
- usePermission 스텁 → `useQuery + api.getEffectivePermissions(nodeId)` 로 교체 (docs/01 §14.2)
- BulkActionBar 버튼 표시 조건(can.download/move/delete) 실제 동작
- docs/03 §3 권한 매트릭스 확정 선행 필요 (현재 스켈레톤)

**M10 (고급 키보드)**
- Shift+↑↓ 범위 선택, Ctrl+↑↓ 포커스-only 이동, F2 rename, Delete 삭제, `/` 검색 포커스
- 설계 §12 참고, M4에서 anchor/pending 인프라 이미 확보됨

### 블로커
- 없음 (M5 진입 가능. docs/03 §3 권한 매트릭스는 M7 시작 전 확정 필요)

---

## 2026-04-25 — M3 브라우저 검증 완료

### 검증 결과
- /files/root — 5개 항목 정상 렌더링 (📁영업팀, 📁인사팀, 📄제안서, 📊예산안, 📝회의록)
- /files/folder_contracts — 계약서 2개 정상
- /files/folder_hr — empty state ("이 폴더는 비어 있습니다") 정상
- 키보드 ↑↓ Enter Esc 모두 정상 동작
- hydration 에러는 브라우저 확장(testim) 주입 문제, 코드 무관

### 다음 세션 컨텍스트 (M4: 선택 모델 + BulkActionBar)
- FileRow aria-selected 항상 false → M4에서 useSelectionStore 연결
- FileRow onClick → M4에서 selectOnly/toggle/range 로직 연결
- aria-multiselectable={true} → M4에서 grid에 다시 추가
- 컬럼 헤더 정렬 토글 UI 미구현 (sort/dir은 URL param으로 동작)
- F2/Delete/Ctrl+A/검색(/) 키보드는 M10으로 미룸
- 3000~4000 포트 대역은 다른 앱이 점유 중 → dev 서버는 4100+ 사용 권장

---

## 2026-04-24 — M3 구현

### 완료
- [M3] FileTable — TanStack Virtual 가상화 (overscan: 10, 10k+ 행 대응)
- [M3] 4가지 상태 컴포넌트: Skeleton, Empty, Error(onRetry), Forbidden(403)
- [M3] FileRow — 아이콘, 크기/날짜 포맷, roving tabIndex
- [M3] WAI-ARIA grid 패턴: role="grid/row/gridcell/columnheader", aria-rowcount/rowindex/selected
- [M3] 기본 키보드: ↑↓ 포커스 이동 + DOM focus 동기화, Enter 열기, Esc 해제
- [M3] useFilesInFolder 훅 (qk.filesInFolder 캐시 키)
- [M3] useSortParams 훅 (URL searchParams에서 sort/dir 읽기)
- [M3] ClientFilesPage에 FileTable 통합

### 계약 파일 추가/수정
- src/types/file.ts            (FileItem, SortKey 타입)
- src/lib/queryKeys.ts 수정     (files, filesInFolder, fileDetail 키 추가)
- src/lib/api.ts 수정           (MOCK_FILES + getFilesInFolder 추가)

### 코드 리뷰 후 수정
- role="grid"를 외부 컨테이너로 이동 (header row가 grid 안에 포함되도록)
- aria-multiselectable 제거 (M4 선택 기능 전까지 premature)
- folderId 변경 시 focusedIndex 리셋 useEffect 추가
- 포커스 시 DOM .focus() 호출 추가 (스크린 리더 대응)
- role="gridcell", role="columnheader" 추가

### 다음 세션 컨텍스트 (M4: 선택 모델 + BulkActionBar)
- FileRow의 aria-selected는 현재 항상 false. M4에서 useSelectionStore 연결 필요
- FileRow onClick prop은 현재 focusedIndex만 설정. M4에서 selectOnly/toggle/range 연결
- aria-multiselectable={true}는 M4에서 다시 추가
- 컬럼 헤더 정렬 토글 UI 미구현 (sort/dir은 URL param으로 동작)
- api.ts mock 데이터: folder_sales/folder_hr가 MOCK_TREE와 MOCK_FILES에 이중 존재 — 실제 API 계약 확정 시 정리 필요
- F2/Delete/Ctrl+A/검색(/) 키보드는 M10으로 미룸

### 블로커
- 없음

### 설계 문서 업데이트 필요
- 없음 (docs/01 §4, §6, §11, §12 스펙 그대로 반영)

---

## 2026-04-24 — M1 완료

### 완료
- [M1] folderId 중심 catch-all 라우팅 (`/files/[...parts]`)
- [M1] FolderTree / Breadcrumb URL 동기화
- [M1] canonical redirect (decodeURI 비교, 한글 URL 대응)
- [M1] 프로젝트 기본 셋업 (Providers, 훅, 스토어)
- [M1] `/files` → `/files/root` 리다이렉트
- [M1] Explorer 레이아웃 (사이드바 + 메인)
- [M1] loading / error / not-found 상태 페이지

### 계약 파일 추가
- src/lib/normalize.ts      (docs/02 §3)
- src/lib/queryKeys.ts       (docs/01 §6.1)
- src/lib/folderPath.ts      (docs/01 §17.3)
- src/lib/api.ts             (MOCK — M5에서 실제 API로 교체)

### 다음 세션 컨텍스트 (M2: FolderTree 심화 + TrashLink + QuickAccess 또는 M3: FileTable)
- api.ts는 현재 mock. 백엔드 나오면 실제 fetch로 교체. 계약은 docs/02 §7.3
- 서버 컴포넌트 전환은 M3에서 (notFound/redirect 조합)
- canonical redirect는 클라이언트에서 useEffect. 깜빡임 있으면 M3에서 서버 redirect로
- Next.js 16에서 Windows pnpm EPERM 이슈 발생 → 15.3.2로 다운그레이드, npm 사용
- next-env.d.ts의 `.next/types/routes.d.ts` import는 Next.js 16 전용 → 15에서는 무시됨

### 블로커
- 없음

### 설계 문서 업데이트 필요
- 없음 (코드 템플릿 그대로 반영)

---

## 2026-04-26 — A1.0 완료 (User schema + JPA) + 드리프트 정리

### 완료
- [드리프트 정리] ADR #12 Spring Session(Redis) → JDBC 정정 4곳 (docs/00 §1.3/§4.4/§5 + docs/02 §7.1) — Redis는 MVP 인프라 제외, JDBC 단일 백엔드로 통일
- [드리프트 정리] 권한 enum 9종 (READ/UPLOAD/EDIT/MOVE/DOWNLOAD/DELETE/SHARE/PERMISSION_ADMIN/PURGE) docs/00 §3.2 ↔ docs/03 §3.1 동기화
- [드리프트 정리] users 테이블 컬럼 정렬: V1 stub의 display_name 유지 + docs/02 §2.1 동일 컬럼명으로 정렬 + role 대문자(MEMBER/AUDITOR/ADMIN)
- [신규 ADR] #18~#22 추가: MVP 인증 범위 / BCrypt 정책 / 세션 만료·잠금 / 관리자 초대 only / `/me` 응답 최소화
- [docs/02 §7.4] /api/auth/login·logout·me·csrf endpoint 상세 (CSRF 헤더, side-effects, 423 ACCOUNT_LOCKED 등)
- [docs/02 §8] 423 LOCKED → ACCOUNT_LOCKED + FILE_LOCKED 분리
- [docs/03 §2] TBD 스캐폴딩 → 본 스펙 (시퀀스 4종, §2.6 만료·잠금, §2.7 BCrypt 정책, §2.8 관리자 초대, §2.10 audit 매트릭스)
- [A1.0] V2 마이그레이션: users 5컬럼 추가 (role + is_active + last_login_at + locked_at + must_change_password)
- [A1.0] User @Entity + Role enum + UserRepository (findActiveByEmail, lowercase 정규화는 caller 책임)
- [A1.0] UserRepositoryTest (Testcontainers Postgres 15-alpine, `disabledWithoutDocker=true`로 로컬 dev pass)
- gradle 8.14.4 → 8.10 정렬 (#4 wrapper 후속)

### 다음 세션 컨텍스트
- A1.1 (PasswordEncoder + UserDetailsService) 진입 — `BCryptPasswordEncoder(12)` 래핑한 `DelegatingPasswordEncoder` + User→UserDetails 어댑터
- A1 잔여: A1.1 → A1.2 (SecurityConfig: CSRF double-submit + 세션 필터 + /api/auth/csrf) → A1.3 (LoginController + lockout) → A1.4 (Logout + /me)
- PR 분기점은 A1.2 종료 시점 권장 (Security 인프라 단위)

### 블로커
- A1.3 진입 전 해결 필요: lockout 카운터 backing store 결정 (docs/03 §2.3 footnote). ADR #12에서 Redis MVP 제외 → 후보 (a) in-memory `ConcurrentHashMap` (b) DB `login_failures` 테이블 (c) v1.x Redis ADR 선결정. A1.1 작업 중 ADR로 결정.

### 설계 문서 업데이트 필요
- docs/03 §2.3/§2.6 — lockout 카운터 Redis 표기를 결정된 backing 표기로 정정 (A1.1 ADR 후)
- docs/03 §4.1 audit enum — `user.login.success/failed/logout/session.expired/locked/unlocked` 추가 (A1.3 진입 시)
