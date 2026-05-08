# yml-enabled-cleanup 설계 (admin-cron-toggle 직접 후속)

- 작성일: 2026-05-09
- 트랙: `yml-enabled-cleanup`
- 후속 plan: `docs/superpowers/plans/2026-05-09-yml-enabled-cleanup.md`
- 직전 트랙: `admin-cron-toggle` (PR #102, 2026-05-08)

## 1. Goal

admin-cron-toggle 트랙에서 cron 4종 enabled 토글이 DB(`cron_policy`)로 전환되며 yml의 `app.*.enabled`와 4 `*Properties` 클래스의 `enabled` 필드는 dead config가 됨. 본 트랙은 이를 완전히 제거하고, `/admin/system` viewer가 토글 직후 즉시 갱신되도록 backend 응답을 DB source로 전환한다.

### 핵심 문제

직전 트랙(admin-cron-toggle) 후 발견된 정합성 갭:

- `AdminSystemController.getCronStatus()`가 `xxxx.enabled()`(yml) 그대로 노출 → 토글 후 viewer가 stale yml 값을 보여줌
- yml의 `enabled` 필드는 cron 동작에 영향 없으나(P4에서 `@ConditionalOnProperty` 제거 + DB lookup 가드로 전환) 운영자가 dead config임을 모르고 변경 시도 가능

본 트랙으로 dead config + viewer staleness 동시 해결.

## 2. 비-범위 (v1.x)

- schedule(cron expression) 자체 UI 편집
- 2인 승인 워크플로
- batchSize/maxPerRun/graceHours UI 편집

## 3. 핵심 결정

### 3.1 yml의 `app.*.enabled` 4행 제거

`application.yml` + `application-prod.yml` 양쪽에서 `enabled` 키 제거. 4 cron 모두.

### 3.2 4 `*Properties` 클래스에서 `enabled` 필드 제거

`HardPurgeProperties` / `ShareExpirationProperties` / `PermissionExpirationProperties` / `StorageOrphanCleanupProperties` 4종의 record param에서 `boolean enabled` 제거. Javadoc도 갱신("false면 빈 미등록" 같은 stale 설명 제거).

### 3.3 `AdminSystemController.getCronStatus()`를 DB source로 전환

`CronPolicyRepository`를 의존성으로 주입. 4 cron 응답에서 `xxxx.enabled()` 호출을 `cronPolicyRepository.isEnabled(KEY)`로 교체. 토글 후 viewer가 즉시 갱신.

## 4. 변경 파일

### 수정 (backend)

| 경로 | 변경 |
|---|---|
| `backend/src/main/resources/application.yml` | `app.*.enabled: false` 4행 + 라인 62 stale 주석("개별 잡이 enabled 게이트") 제거 |
| `backend/src/main/resources/application-prod.yml` | `app.*.enabled: true` 4행 + 라인 42 stale 주석 제거 |
| `backend/src/main/java/com/ibizdrive/purge/HardPurgeProperties.java` | record param `boolean enabled` 제거. Javadoc "false면 빈 미등록" 정정 |
| `backend/src/main/java/com/ibizdrive/share/ShareExpirationProperties.java` | 동일 패턴 |
| `backend/src/main/java/com/ibizdrive/permission/PermissionExpirationProperties.java` | 동일 |
| `backend/src/main/java/com/ibizdrive/storage/StorageOrphanCleanupProperties.java` | 동일 |
| `backend/src/main/java/com/ibizdrive/admin/AdminSystemController.java` | `CronPolicyRepository` 의존성 주입 + `getCronStatus()` 4 응답에서 `xxxx.enabled()` → `cronPolicyRepository.isEnabled(KEY)` |
| `backend/src/test/java/com/ibizdrive/admin/AdminSystemControllerTest.java` | viewer slice 테스트의 `CronPolicyRepository` mock setup 추가 또는 갱신. 기존 토글 PUT 케이스 회귀 유지 |

### 잠재 영향 — 4 `*Properties` 생성자 호출 사이트

- 4 cron job 클래스 (P4에서 이미 적응): record가 enabled 빠지면 생성자 시그니처 변경 → 자동 spring binding 재바인드 OK
- 단위 테스트의 `new XxxProperties(...)` 직접 호출: enabled 없이 생성. 테스트 갱신 필요한지 grep으로 확인

### 수정 (docs)

| 경로 | 변경 |
|---|---|
| `docs/04-admin-operations.md` §15.4 | "schedule/zone/batchSize/maxPerRun은 yml + 재기동" 라인은 유지. yml의 dead `enabled` 언급(있으면) 제거 |
| `docs/progress.md` | 트랙 closure 엔트리 |
| `BETA-RELEASE.md` | 변경 없음 (admin-cron-toggle closure에서 이미 cleanup 명시) |
| `dev/active/yml-enabled-cleanup/README.md` | 트랙 메타 |

## 5. 회귀 검증

- backend `./gradlew test` BUILD SUCCESSFUL
  - AdminSystemControllerTest: viewer GET이 DB source로 전환 후 4종 enabled 정확히 반영 (mock에서 isEnabled true/false 다양화)
  - 4 cron job test: P4의 패턴 그대로
- frontend 변경 0 (응답 DTO `CronJobStatusResponse.enabled`는 그대로 — backend의 source가 yml에서 DB로 바뀌었을 뿐 wire format 동일)

## 6. 핵심 원칙 검토 (CLAUDE.md §3)

| 원칙 | 본 트랙 적용 |
|---|---|
| 6. DB 제약이 진실 출처 | `cron_policy` 단일 source — viewer/job 모두 같은 source ✅ |
| 8. audit_log append-only | 새 emit 0 |
| 12. 에러 코드 계약 | 신규 코드 0 |

## 7. 참고

- admin-cron-toggle (PR #102, 2026-05-08): V11 마이그레이션 + 토글 mutation + cron job 가드. 본 트랙은 그 트랙의 viewer staleness + dead config 정리.
