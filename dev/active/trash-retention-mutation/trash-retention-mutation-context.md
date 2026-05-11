---
task: trash-retention-mutation
last_updated: 2026-05-11
---

# Context

## 핵심 참조 파일

| 파일 | 역할 |
|---|---|
| `docs/04-admin-operations.md` §8 | 휴지통 정책 spec |
| `docs/04-admin-operations.md` §9.2 | 보존 정책 spec |
| `docs/04-admin-operations.md` §15.4 | 운영 런북 — 2인 승인 framework deferred 명시 |
| `backend/src/main/java/com/ibizdrive/trash/TrashRetentionProperties.java` | 현재 yml-bound config (record) |
| `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashPolicyController.java` | 현재 GET-only controller |
| `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashPolicyDto.java` | 응답 DTO (`{ retentionDays }`) |
| `backend/src/main/java/com/ibizdrive/file/FileMutationService.java` (line 76, 267) | retention.days() 사용처 |
| `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java` (line 89, 438) | 동형 |
| `backend/src/main/java/com/ibizdrive/team/TeamAuditListener.java` | audit listener 패턴 답습 대상 |
| `backend/src/main/resources/application.yml` `app.trash.retention.days` | yml default 30 |
| `frontend/src/app/admin/retention/page.tsx` | 현재 read-only viewer |
| `frontend/src/hooks/useAdminTrashPolicy.ts` | 현재 GET hook |
| `frontend/src/components/files/GrantPermissionDialog.tsx` | confirm dialog 패턴 답습 대상 |

## 패턴 결정

### Single-row 테이블 패턴
JPA로는 `findById((short) 1)` 단일 lookup. INSERT는 V17 migration이 한 번 (yml default 값으로). 운영 중 INSERT 없음, UPDATE만.

### Audit 패턴
`TeamAuditListener` 답습 — `@TransactionalEventListener(phase = AFTER_COMMIT)`. service가 `eventPublisher.publishEvent(new RetentionPolicyChangedEvent(...))`, listener가 audit_log row INSERT. ADR #24 (audit emit은 도메인 commit 이후).

### Frontend confirm dialog
`GrantPermissionDialog` 패턴 답습 — `role="dialog"` + `aria-modal="true"` + Esc/닫기/제출 + focus trap. 단, 본 트랙은 더 작은 confirm-only dialog (input은 페이지 본문, dialog는 "정말 변경하시겠습니까?" 재확인만).

## 위험 / 함정

### V17 migration data preservation
yml default 30이 V17 적용 시점에 운영자가 yml override했을 수도 있음 (예: 14일로 설정). V17 migration은:
1. `CREATE TABLE trash_policy ...`
2. `INSERT INTO trash_policy (id, retention_days) SELECT 1, 30 WHERE NOT EXISTS (SELECT 1 FROM trash_policy)` — yml 값 무시 (default 30)
3. **OR** 운영자에게 manual seed 요구 (V17 INSERT 안 함, app 부팅 시 service가 row 부재면 yml 값으로 INSERT)

**채택**: 옵션 3 (app 부팅 시 service가 row 부재면 yml `TrashRetentionProperties.days()` 값으로 INSERT). 이렇게 하면 기존 yml override가 보존됨. `TrashPolicyService.@PostConstruct` 또는 `ApplicationReadyEvent` listener에서 idempotent INSERT.

### 기존 trash row purge_after 재계산
**안 함**. confirm dialog + audit metadata에 `appliesTo: "new-deletes-only"` 명시. 일수 감소 시 기존 row 즉시 hard purge 폭증 위험 회피.

### Hibernate L1 cache stale
`TrashPolicyService.getRetentionDays()`는 매 호출마다 `findById(1)` — Hibernate가 same-tx 캐시. mutation 후 다른 tx에서 stale 가능성? `@Transactional` boundary로 service가 새 EntityManager session 받으므로 정상.

### CSRF + admin endpoint
PUT는 mutation → CSRF 필수. csrf-helper-sweep(PR #165) 후 frontend는 `await ensureCsrfToken()` 패턴.

### dual-approval `app.dual-approval.retention-change.enabled`
docs/04 §15.4 / docs/04 line 853에 `retention_change` action_type + `app.dual-approval.retention-change.enabled` config flag 언급. 본 트랙은 단일-approver 즉시 적용 — flag 자체는 추가하지 않고, 2인 승인 framework가 v1.x++ 별도 트랙에서 framework 전체와 함께 도입.

## 향후 트랙 (본 트랙 외)

- **2인 승인 framework** — `app_settings(approval_request)` table + workflow + UI. 본 트랙 backend mutation을 hook point로 사용.
- **Quota mutation UI** — 같은 패턴 (single-row `quota_policy` table or `app_settings` 일반화 검토 시점).
- **Audit log "보존 일수 변경" 항목** — `RETENTION_POLICY_CHANGED` 이미 §4에 추가되므로 audit viewer에서 자연 노출.
