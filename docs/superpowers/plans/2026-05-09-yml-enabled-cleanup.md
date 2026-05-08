# yml-enabled-cleanup Implementation Plan

**Goal:** admin-cron-toggle 후 dead config가 된 yml `app.*.enabled` 4행 + 4 `*Properties.enabled` 필드 제거 + `AdminSystemController` viewer를 DB source로 전환.

**Architecture:** 단일 PR. 4 phase: (P1) Properties + yml cleanup, (P2) Controller viewer DB source 전환 + test, (P3) docs, (P4) gates + PR.

**Tech Stack:** Spring Boot 3 / `@ConfigurationProperties` record / `@WebMvcTest` slice. 작업 디렉토리: `cd backend && ./gradlew ...`.

**설계 근거:** `docs/superpowers/specs/2026-05-09-yml-enabled-cleanup-design.md`

---

## P0: bootstrap

- [x] `dev/active/yml-enabled-cleanup/README.md` 생성 (이 plan과 함께 별도 commit)

## P1 (BE-1): yml + 4 Properties cleanup

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-prod.yml`
- Modify: `backend/src/main/java/com/ibizdrive/purge/HardPurgeProperties.java`
- Modify: `backend/src/main/java/com/ibizdrive/share/ShareExpirationProperties.java`
- Modify: `backend/src/main/java/com/ibizdrive/permission/PermissionExpirationProperties.java`
- Modify: `backend/src/main/java/com/ibizdrive/storage/StorageOrphanCleanupProperties.java`

### Steps

- [ ] application.yml: `app.purge.enabled: false`, `app.share.expiration.enabled: false`, `app.permission.expiration.enabled: false`, `app.storage.orphanCleanup.enabled: false` 4행 제거. 라인 62 주석을 dead config 표식으로 갱신 또는 단순화.
- [ ] application-prod.yml: 4행 `enabled: true` 제거. 라인 42 주석 갱신.
- [ ] 4 Properties record에서 `boolean enabled` param 제거 + Javadoc "enabled" 관련 줄 정정.
- [ ] `./gradlew compileJava` — 호출 사이트 컴파일 에러 잡기 (`AdminSystemController` + 잠재 cron job 단위 테스트의 `new XxxProperties(...)`).
- [ ] 컴파일 에러 fix: `AdminSystemController.getCronStatus()`의 `xxxx.enabled()` 호출은 P2에서 DB source로 전환할 예정 — P1에서는 임시로 hard-coded `false`로 두지 말고 P2와 묶어 단일 commit으로 처리.

> 주: P1과 P2를 단일 commit으로 묶는다. record param 변경이 viewer 호출 사이트와 직접 연결되어 분리 시 컴파일 GREEN 상태가 깨짐.

## P2 (BE-2): Controller viewer DB source 전환 + test

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/admin/AdminSystemController.java`
- Modify: `backend/src/test/java/com/ibizdrive/admin/AdminSystemControllerTest.java`

### Steps

- [ ] `AdminSystemController` 생성자에 `CronPolicyRepository cronPolicyRepository` 추가 (5번째 서비스 의존성 옆).
- [ ] `getCronStatus()`의 4 response builder에서 `xxxx.enabled()` 호출을 `cronPolicyRepository.isEnabled(KEY)`로 교체:
  - `purge` → `cronPolicyRepository.isEnabled("purge.expired")`
  - `shareExpiration` → `cronPolicyRepository.isEnabled("share.expire")`
  - `permissionExpiration` → `cronPolicyRepository.isEnabled("permission.expire")`
  - `storageOrphanCleanup` → `cronPolicyRepository.isEnabled("storage.orphan.cleanup")`
- [ ] `AdminSystemControllerTest`에 `@MockBean private CronPolicyRepository cronPolicyRepository;` 추가. viewer 테스트의 `@BeforeEach`에서 `when(cronPolicyRepository.isEnabled(...)).thenReturn(...)` 4 setup 추가 (또는 default false).
- [ ] viewer 회귀 케이스 1개 추가: ADMIN GET 호출 시 `cronPolicyRepository.isEnabled("purge.expired") == true`이면 응답 row의 `enabled == true` (DB source 검증).
- [ ] `./gradlew test --tests AdminSystemControllerTest` GREEN.
- [ ] `./gradlew test` 전체 GREEN (4 cron job test 포함 회귀 0).
- [ ] **단일 commit**: `chore(yml-enabled-cleanup): drop yml app.*.enabled + Properties.enabled, switch viewer to cron_policy DB source`.

> 주: cron job 단위 테스트(`HardPurgeJobTest` 등)의 `new XxxProperties(...)` 직접 생성자 호출이 enabled 없이 호출되도록 갱신 필요. P4에서 도입한 `cronPolicyRepository` mock은 그대로.

## P3 (Docs)

**Files:**
- Modify: `docs/04-admin-operations.md` §15.4 (필요 시)
- Modify: `docs/progress.md` (최상단 closure 엔트리)
- Modify: `dev/active/yml-enabled-cleanup/README.md` (PR 링크는 P4 push 후 추가)

### Steps

- [ ] `docs/04 §15.4` 본문에 yml `enabled` 언급이 남아있으면 제거. "schedule/zone/batchSize/maxPerRun은 application-*.yml + 재기동" 라인은 그대로.
- [ ] `docs/progress.md` 최상단(`---` 바로 아래)에 closure 엔트리 추가:

```markdown
## 2026-05-09 — 🏁 yml-enabled-cleanup 트랙 종료 (admin-cron-toggle 직접 후속)

### 범위

admin-cron-toggle (PR #102, 2026-05-08) 후 dead config가 된 yml `app.*.enabled` + 4 `*Properties.enabled` 필드 제거. `AdminSystemController.getCronStatus()` viewer를 DB source(`cron_policy` 테이블)로 전환 — 토글 직후 viewer 즉시 갱신.

### 변경 핵심

**Backend:**
- `application.yml` + `application-prod.yml` — 4 cron의 `enabled` 키 제거 + 관련 stale 주석 정리.
- 4 `*Properties` record (`HardPurgeProperties` / `ShareExpirationProperties` / `PermissionExpirationProperties` / `StorageOrphanCleanupProperties`) — `boolean enabled` param 제거, Javadoc 정정.
- `AdminSystemController.getCronStatus()` — `CronPolicyRepository` 의존성 주입 + 4 응답이 `cronPolicyRepository.isEnabled(KEY)`로 DB source 노출.
- 테스트: `AdminSystemControllerTest` viewer slice에 `@MockBean CronPolicyRepository` + `isEnabled` 분기 검증 1 케이스 추가. 4 cron job 단위 테스트의 `new XxxProperties(...)` 호출 사이트 갱신.

**Docs:**
- `docs/04 §15.4` — yml dead `enabled` 언급 제거.

### 검증

- `cd backend && ./gradlew test` BUILD SUCCESSFUL.
- 신규 audit enum 0, 새 에러 코드 0, schema 변경 0.

### 다음 세션 컨텍스트

- 4 cron의 schedule/zone/batchSize 등 정의는 yml 그대로 유지 (UI 편집은 v1.x).
- 2인 승인 워크플로는 별도 트랙.
```

- [ ] dev/active/yml-enabled-cleanup/README.md PR 링크 갱신 (P4 후).
- [ ] **단일 commit**: `docs(yml-enabled-cleanup): document closure + dead config cleanup procedure`

## P4: gates + PR

- [ ] `cd backend && ./gradlew test` BUILD SUCCESSFUL
- [ ] `cd frontend && pnpm typecheck && pnpm lint && pnpm test --run` 모두 exit 0 (frontend 변경 0이지만 회귀 검증)
- [ ] `git push -u origin chore/yml-enabled-cleanup`
- [ ] `gh pr create --title "chore(yml-enabled-cleanup): drop dead yml app.*.enabled + viewer DB source 전환 (admin-cron-toggle 직접 후속)" ...`
- [ ] CI 두 게이트 GREEN 후 사용자 확인 → squash-merge

---

## Self-Review

**1. Spec coverage:**
- §3.1 yml `app.*.enabled` 제거 → P1
- §3.2 4 Properties `enabled` 필드 제거 → P1
- §3.3 Controller viewer DB source 전환 → P2
- §4 변경 파일 → P1 + P2 + P3
- §5 회귀 검증 → P4

**2. Placeholder scan:** "TBD"/"TODO" 없음.

**3. Risk:**
- record param 제거 시 `@ConfigurationProperties` 자동 binding이 yml의 빠진 키를 무시 (record default 적용 안 되므로 record param 자체에서 제거).
- viewer 응답 모양은 그대로(`CronJobStatusResponse.enabled` 필드 유지) → frontend 영향 0.
- cron job 진입 시 `cronPolicyRepository.isEnabled(KEY)` 가드는 P4(admin-cron-toggle)에서 이미 적용 — 본 트랙은 viewer만.
