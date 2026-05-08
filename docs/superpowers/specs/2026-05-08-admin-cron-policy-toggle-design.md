# admin-cron-policy-toggle 설계 (Wave 2 closure 후속)

- 작성일: 2026-05-08
- 트랙: `admin-cron-policy-toggle`
- 후속 plan: `docs/superpowers/plans/2026-05-08-admin-cron-policy-toggle.md` (TBD)

## 1. Goal

사내 베타 운영자(ADMIN)가 운영 cron 4종을 **재기동 없이 UI로 토글**할 수 있게 한다. 현재는 `application-prod.yml`에서 `enabled` 값을 변경 + 재기동만 가능 (docs/04 §15.4). 토글 결과는 DB에 영구 저장되어 재기동 후에도 유지되며, audit_log로 영구 추적된다.

### 사용 시나리오

- "지금 `permission.expire`가 너무 자주 도니 잠시 끄자" → 운영자가 UI에서 토글, 즉시 다음 tick부터 skip
- "주말 동안 `storage.orphan.cleanup` 끄자" → 토글 후 재기동돼도 OFF 유지, 월요일에 다시 ON
- "누가 언제 `purge.expired`를 껐지?" → audit_log `admin.cron.toggled` lookup

## 2. 비-범위 (v1.x)

본 트랙에서 다루지 **않는** 항목:

- 2인 승인 워크플로 (Wave 2 closure backlog) — 본 트랙은 단순 toggle, 정책 변경 위험은 audit_log로 감사
- schedule (cron expression) UI 편집 — `application-*.yml` + 재기동 그대로
- `application.yml`의 `app.*.enabled` dead 필드 cleanup — 후속 트랙
- cron job별 retry / timeout / 실행 이력 (last-run-status) UI

## 3. 핵심 설계 결정

### 3.1 UI 위치 — `/admin/system`에 통합

기존 read-only viewer 페이지(`frontend/src/app/admin/system/page.tsx`, Wave 1.5 PR #73)에 토글 mutation을 추가한다. 별도 `/admin/system/policy` 분리 없음.

**이유**:
- 같은 도메인(cron operational policy)이 두 URL에 흩어지면 응집도 깨짐
- AdminSideNav 항목 증가 0 (UX 일관성)
- AUDITOR는 viewer 그대로 read-only, ADMIN만 토글 노출

### 3.2 메커니즘 — DB 테이블 영구 저장

신규 `cron_policy` 테이블. `enabled` flag를 DB row로 보관. cron job 진입 시 매 tick DB lookup → if disabled → skip.

**이유**:
- 운영자가 토글한 상태가 재기동에도 유지 (운영 사용성 ↑)
- in-memory mutable flag는 재기동 시 yml 기본값으로 복원 → 운영자 입장에서 매번 재토글 부담
- DB lookup 비용은 cron tick(5분/자정/새벽 1시) 단위라 미미

### 3.3 yml–DB 관계 — V11 마이그레이션 1회 시드 후 DB-only

V11 마이그레이션이 `application.yml`의 enabled 기본값(전부 false) 4 row를 `cron_policy`에 시드. 이후 cron 동작은 **DB만** 본다. yml의 `enabled` 필드는 dead config (제거는 v1.x).

**이유**:
- 동일 책임의 진실 출처 분산 = 혼란
- DB는 `enabled`만, yml은 `schedule/zone/batchSize/maxPerRun/graceHours` 등 cron 정의 (변경 빈도 낮음) — 책임 분리 명확
- "DB row 있으면 override" 패턴은 마이그레이션 시 빈 테이블이지만 시드 정책 모호

## 4. Architecture

```
[ADMIN UI]                    [Backend]                      [DB]
┌─────────────┐               ┌─────────────────────────┐   ┌─────────────┐
│ Toggle      │ PUT /api/     │ AdminSystemController    │   │ cron_policy │
│ Switch      │ admin/system/ │ .toggleCron(key, body)   │   │ (4 rows)    │
│ + Confirm   │ cron/{key}    │   ↓                      │   │             │
│ Dialog      │               │ AdminSystemService       │   │ key (PK)    │
└─────────────┘               │ .toggleCron(...)         │   │ enabled     │
       ↓                      │   ↓ @Transactional       │←──│ updated_at  │
useAdminToggleCron()          │ CronPolicyRepository     │   │ updated_by  │
       ↓                      │ .updateEnabled(...)      │   └─────────────┘
onSuccess: invalidate         │   ↓                      │          ↑
qk.adminSystem()              │ ApplicationEventPublisher │          │
                              │ .publishEvent(            │          │
                              │   AdminCronToggledEvent)  │          │
                              └─────────────────────────┘          │
                                                                    │
                              [AFTER_COMMIT REQUIRES_NEW]            │
                              ┌─────────────────────────┐            │
                              │ AdminCronToggledListener │            │
                              │ → emit AUDIT_LOG row    │────────────┤
                              │   ADMIN_CRON_TOGGLED    │            │
                              └─────────────────────────┘   audit_log│
                                                                    │
[Cron tick]                                                         │
┌─────────────────────┐                                             │
│ ScheduledJob (4종)  │ → cronPolicyRepository.isEnabled(KEY) ──────┘
│ .run()              │ → if !enabled → return; (skip)
│   - first line      │ → 정상 실행
│     guard           │
└─────────────────────┘
```

## 5. DB Schema (V11 마이그레이션)

`backend/src/main/resources/db/migration/V11__cron_policy.sql`:

```sql
-- Wave 2 closure 후속: cron 4종 enabled 토글을 DB로 영구 저장.
-- yml의 app.*.enabled는 시드 후 동작에 영향 없음 (cleanup은 v1.x).
CREATE TABLE cron_policy (
    key          VARCHAR(64)  PRIMARY KEY,
    enabled      BOOLEAN      NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by   UUID         REFERENCES users(id) ON DELETE SET NULL
);

-- 4종 시드 — yml 기본값(전부 false)을 그대로 반영.
-- updated_by=NULL은 system seed 표식.
INSERT INTO cron_policy (key, enabled) VALUES
    ('purge.expired',          false),
    ('share.expire',           false),
    ('permission.expire',      false),
    ('storage.orphan.cleanup', false);
```

### 제약

- `key`는 PK (4종 enum 값과 1:1)
- `updated_by` ON DELETE SET NULL (운영자 사용자 비활성화 시 audit는 audit_log에 영구 보존)
- 신규 인덱스 0 (4 row 테이블이라 PK 인덱스로 충분)

## 6. Backend

### 6.1 신규 파일

| 경로 | 역할 |
|---|---|
| `backend/src/main/java/com/ibizdrive/admin/CronPolicy.java` | JPA `@Entity` (key/enabled/updatedAt/updatedBy) |
| `backend/src/main/java/com/ibizdrive/admin/CronPolicyRepository.java` | `JpaRepository<CronPolicy, String>` + 헬퍼 `boolean isEnabled(String key)` |
| `backend/src/main/java/com/ibizdrive/admin/AdminCronToggledEvent.java` | record(actorId, ip, userAgent, jobKey, fromEnabled, toEnabled) |
| `backend/src/main/java/com/ibizdrive/admin/AdminCronToggledListener.java` | `@TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW)`, audit_log emit |
| `backend/src/main/resources/db/migration/V11__cron_policy.sql` | 위 §5 스크립트 |

### 6.2 수정 파일

| 경로 | 변경 |
|---|---|
| `backend/src/main/java/com/ibizdrive/admin/AdminSystemController.java` | `PUT /api/admin/system/cron/{key}` 추가. body `{enabled: bool}`. `@PreAuthorize("hasRole('ADMIN')")` (read endpoint와 권한 분리). request body validation은 `@Valid + @NotNull`. 응답: `204 No Content`. |
| `backend/src/main/java/com/ibizdrive/admin/AdminSystemService.java` | `toggleCron(String key, boolean requested, IbizDriveUserDetails actor)` 메서드 추가. 트랜잭션 내 `findById` → enable 변경 → save → event publish. unknown key는 `IllegalArgumentException` (글로벌 핸들러 400). |
| `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` | `ADMIN_CRON_TOGGLED("admin.cron.toggled")` enum 추가. cumulative 47 → 48. |
| 4개 cron job 클래스 (실제 위치는 implementation phase에서 확인) | 각 메서드 첫 줄에 `if (!cronPolicyRepository.isEnabled(KEY)) { log.debug("cron {} disabled, skipping", KEY); return; }` 가드. |

### 6.3 endpoint contract

```
PUT /api/admin/system/cron/{key}
  Auth: ADMIN session (AUDITOR/MEMBER 403)
  Path: key ∈ {purge.expired, share.expire, permission.expire, storage.orphan.cleanup}
  Body: {"enabled": <boolean>}
  Response:
    204 No Content (성공)
    400 BAD_REQUEST (key 미지원, body 형식 오류)
    401 (세션 없음)
    403 (권한 부족)
```

### 6.4 audit_log metadata

```json
{
  "key": "permission.expire",
  "fromEnabled": true,
  "toEnabled": false
}
```

`actor_id` = 토글한 ADMIN user id, `target_type` = `"system"`, `target_id` = NULL (cron은 단일 시스템 자원).

## 7. Frontend

### 7.1 신규 파일

| 경로 | 역할 |
|---|---|
| `frontend/src/lib/api.adminToggleCron.test.ts` | URL/body/method 단위 테스트 |

### 7.2 수정 파일

| 경로 | 변경 |
|---|---|
| `frontend/src/lib/api.ts` | `adminToggleCron(key: string, enabled: boolean): Promise<void>` 추가. PUT 메서드, body JSON. |
| `frontend/src/hooks/useAdminSystem.ts` | `useAdminToggleCron()` mutation hook 추가. onSuccess에서 `qk.adminSystem()` prefix 무효화. |
| `frontend/src/app/admin/system/page.tsx` | 4 카드 각각에 `<CronToggleSwitch>` 추가. ADMIN 세션에서만 노출. ConfirmDialog로 "정책 X을(를) (비)활성화합니다…" 확인. |
| `frontend/src/app/admin/system/page.test.tsx` | ADMIN 노출 / AUDITOR 미노출 / 토글 mutation 호출 / 무효화 검증 |
| `frontend/src/types/audit.ts` | `'admin.cron.toggled'` enum string 추가 |

### 7.3 권한 노출 정책

- ADMIN 세션: 4 카드 모두에 토글 switch + ConfirmDialog 표시
- AUDITOR 세션: 토글 switch 미노출, 기존 read-only badge만 (status 그대로 보임)
- MEMBER 세션: `/admin/system` 접근 자체가 403 (Wave 1.5 가드)

frontend 권한 체크는 UX 안내용 (배지/숨김). **진실 출처는 backend 가드**.

## 8. UI 명세

### 8.1 카드 레이아웃 (ADMIN 시점)

```
┌───────────────────────────────────────────────┐
│  [●] purge.expired             [Toggle: ON]   │  ← 토글 노출 (ADMIN만)
│  schedule: 0 0 0 * * *                        │
│  zone: Asia/Seoul                             │
│  maxPerRun: 10000                             │
└───────────────────────────────────────────────┘
```

### 8.2 ConfirmDialog 텍스트

| 액션 | 제목 | 본문 |
|---|---|---|
| OFF → ON | "정책 활성화" | "‘<label>’ cron을 활성화합니다. 다음 실행부터 예약 schedule에 따라 동작합니다. 계속하시겠습니까?" |
| ON → OFF | "정책 비활성화" | "‘<label>’ cron을 비활성화합니다. 다음 실행부터 skip되며, 진행 중인 작업은 정상 완료됩니다. 계속하시겠습니까?" |

`<label>` = `CronJobStatusResponse.label` 한국어 (이미 backend가 노출).

### 8.3 즉시 효과

- 토글 직후 mutation 응답 → query 무효화 → 새 상태 표시
- cron 동작은 **다음 tick부터** 적용 (in-tick DB lookup이 첫 줄)
- 5분 단위 cron(share/permission)은 최대 5분 후 반영
- 자정 cron(purge)은 다음 자정, `storage.orphan.cleanup`은 다음 새벽 1시

## 9. Tests

### 9.1 Backend

| 테스트 | 종류 | 검증 |
|---|---|---|
| `CronPolicyRepositoryTest` | `@DataJpaTest` slice | 4 row 시드 / `isEnabled` / `updateEnabled` |
| `AdminSystemServiceToggleCronTest` | 서비스 단위 (mock event publisher) | 트랜잭션 내 enabled 갱신 + event publish 검증 |
| `AdminCronToggledListenerTest` | listener 단위 | `AUDIT_EXPORTED` 패턴 미러. metadata key/fromEnabled/toEnabled |
| `AdminSystemControllerTest` | `@WebMvcTest` slice | ADMIN 204 / AUDITOR 403 / MEMBER 403 / unknown key 400 / body 누락 400 |
| `AdminCronToggleE2ETest` | Testcontainers | 로그인 → 토글 → DB row 갱신 + audit_log emit + cron 다음 tick skip |
| 4 cron job 단위 테스트 | 기존 슈트 | `isEnabled=false` 시 skip + run 메서드 미실행 (mock service) |

### 9.2 Frontend

| 테스트 | 종류 | 검증 |
|---|---|---|
| `api.adminToggleCron.test.ts` | 단위 | URL `PUT /api/admin/system/cron/{key}` + body `{enabled}` + 204 응답 처리 |
| `useAdminSystem.test.tsx` | hook | `useAdminToggleCron` mutation onSuccess 시 `qk.adminSystem()` invalidate |
| `app/admin/system/page.test.tsx` | 페이지 | ADMIN 토글 노출 / AUDITOR 미노출 / ConfirmDialog 동작 / mutation 호출 / aria-label |

## 10. Migration & Rollout

### 10.1 마이그레이션

V11 (위 §5)이 운영 DB에 cron_policy 테이블을 만들고 4 row 시드한다.

### 10.2 운영 절차 변경 (docs/04 §15.4 갱신)

기존:
> "운영 cron 4종 변경 절차 — `application-prod.yml` 편집 + 재기동"

신규:
> "운영 cron 4종 enabled 토글 — ADMIN UI(`/admin/system`)에서 토글. 즉시 효과(다음 tick부터). 변경 이력은 audit_log `admin.cron.toggled`로 추적. schedule/zone/batch 등 cron 정의 변경은 `application-prod.yml` + 재기동(기존 절차)."

### 10.3 prod 첫 적용 주의

V11 시드는 모든 cron을 `enabled=false`로 둔다. prod 첫 부트 시 기존에 `application-prod.yml`에서 ON으로 운영 중이던 cron이 있으면 마이그레이션 후 OFF로 전환된다.

**대응**: 운영자가 V11 적용 직후 UI에서 4종 토글로 원하는 상태로 설정. release note에 명시.

대안(고려했으나 기각): V11이 `application-prod.yml`을 read해서 시드 — 환경 분리(dev/staging/prod)별 다른 yml을 마이그레이션이 의식해야 해 KISS 위배.

## 11. 핵심 원칙 검토 (CLAUDE.md §3)

| 원칙 | 본 트랙 적용 |
|---|---|
| 1. URL이 어디 소유 | N/A (admin 페이지, 라우팅 변경 없음) |
| 6. DB 제약이 진실 출처 | `cron_policy.key PK` ✅ |
| 7. 트랜잭션 + SELECT FOR UPDATE | toggle은 단순 UPDATE, race condition 무관 (PK 단일 row, 마지막 write wins + audit_log로 추적) |
| 8. audit_log append-only | 신규 enum 1개 추가, 기존 패턴 그대로 ✅ |
| 10. 파괴적 액션 백엔드 재검증 | `@PreAuthorize("hasRole('ADMIN')")` + service 단 actor 검증 ✅ |
| 12. 에러 코드 계약 | 신규 에러 코드 0 (기존 `BAD_REQUEST` / `FORBIDDEN` 재사용) |

## 12. 참고

- Wave 1.5 `auditor-cron-readonly` (PR #73): viewer 패턴 + `CronJobStatusResponse` 공유 DTO
- Wave 2 T9 admin-global-trash (PR #79): admin mutation + ConfirmDialog 패턴
- audit-export-json (PR #85): `AuditExportEvent` + listener 패턴 — `AdminCronToggledListener`가 미러
- Wave 2 closure (PR #82): `docs/04 §15.4`에 운영 cron 절차 봉인 — 본 트랙으로 갱신
