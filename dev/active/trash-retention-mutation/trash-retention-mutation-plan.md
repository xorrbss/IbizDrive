---
task: trash-retention-mutation
status: in_progress
created: 2026-05-11
spec: docs/04 §8.3 (현재 read-only viewer) + §9.2 (보존 정책 외부화) + §15.4 (2인 승인 deferred)
branch: feat/trash-retention-mutation
worktree: C:/project/IbizDrive/.claude/worktrees/trash-policy
---

# Plan — 휴지통 보존 정책 mutation UI

## 0. 배경

현재 `app.trash.retention.days` (default 30)는 `@ConfigurationProperties` 부팅 바인딩 — 운영자가 변경하려면 `application.yml` 수정 + backend 재기동. read-only viewer만 제공 (`/admin/retention`, wave2-trash-policy-viewer).

운영자가 무중단으로 보존 일수를 조정할 수 있게 mutation 도입. 단, **일수 감소**는 hard purge 폭증 위험이 있어 운영적 안전장치 필요(2인 승인 framework는 v1.x deferred — 본 트랙은 단일-approver MVP + 경고 UX).

## 1. Scope

### in-scope (본 트랙 — 3 phase)

- **Phase A** (docs only): spec 설계 + 핵심 결정 명시 (저장 전략, audit event, 신규 row 적용 정책, 경고 UX 정책).
- **Phase B** (backend): V17 single-row 테이블 (`trash_policy`) + `TrashPolicyService` (DB-backed) + `PUT /api/admin/trash/policy` + `RETENTION_POLICY_CHANGED` audit event + `FileMutationService`/`FolderMutationService` 호출 변경.
- **Phase C** (frontend): `/admin/retention` mutation UI — input + confirm dialog (감소 시 경고) + api wrapper + hook + 회귀 가드 vitest.

### out-of-scope (v1.x++ deferred)

- **2인 승인 framework** — §15.4 `app.dual-approval.retention-change.enabled` 토글 명시. 본 트랙은 단일-approver(ADMIN role 보유자가 자기 권한으로 mutation). 2인 승인은 별도 큰 트랙.
- **기존 trash row의 `purge_after` 재계산** — 정책 변경은 신규 soft-delete만 적용. 기존 row 일괄 재계산은 hard purge 폭증 위험 회피 (점진적 자연 만료).
- **이력 viewer** — 변경 history는 audit_log로만. 별도 UI는 v2.x.
- **Quota mutation UI** — 같은 패턴이지만 별도 트랙.

## 2. 핵심 결정

### 2.1 저장 전략: Option A (single-row 테이블)

```sql
CREATE TABLE trash_policy (
  id              SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  retention_days  INT NOT NULL CHECK (retention_days BETWEEN 7 AND 90),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_by      UUID REFERENCES users(id) ON DELETE SET NULL
);
INSERT INTO trash_policy (id, retention_days) VALUES (1, 30);
```

**근거**:
- 단일 정책 row만 — JPA `findById(1)` 로 단순 조회.
- `id = 1` CHECK 제약으로 다중 row 삽입 차단 (operator 실수 방지).
- `BETWEEN 7 AND 90` CHECK는 docs/04 §8.1 명시 범위 (관리자 조정 가능: 7~90일).
- `app_settings(key, value)` 일반화 안 함 — YAGNI. quota 등 후속 트랙에서 패턴 확정 후 도입 검토.

**대안 폐기**:
- Option B (`app_settings(key, value)`): 일반화 좋지만 본 트랙 단일 정책엔 over-engineering.
- Option C (yml 파일 rewrite): 파일 권한/배포 상태에 의존, Spring `@RefreshScope` 부재 등 fragile.

### 2.2 신규 row 적용 정책

정책 변경은 **변경 시점 이후 soft-delete만 새 일수 적용**. 기존 trash row의 `purge_after`는 **재계산 안 함**.

**근거**:
- 일수 감소(예: 30 → 7) 시 기존 row 재계산하면 즉시 hard purge 후보 폭증 → 데이터 유실 위험.
- 기존 row는 자연 만료 — 점진적 정리.
- audit event에 `applies_to_new_only: true` 메모 + UI confirm dialog에 명시.

### 2.3 audit event

신규 `RETENTION_POLICY_CHANGED` (TargetType=`trash_policy`, target_id=`1`):
```json
{
  "before": { "retentionDays": 30 },
  "after":  { "retentionDays": 14 },
  "appliesTo": "new-deletes-only"
}
```

### 2.4 권한

- `PUT /api/admin/trash/policy`: `@PreAuthorize("hasRole('ADMIN')")` — Wave 2 T9 admin endpoints 동형. AUDITOR 차단.
- 2인 승인은 deferred → 단일 ADMIN으로 즉시 적용. 운영 런북 docs/04 §15.4에 단일-approver MVP임을 명시.

### 2.5 경고 UX

frontend 다이얼로그:
- 입력 변경 시 미리보기 ("30일 → 14일")
- **감소** 시: "기존 휴지통 항목은 영향받지 않습니다. 신규 삭제부터 14일 후 영구 삭제됩니다."
- 동일/증가 시 별도 경고 없음
- ConfirmDialog (ShareDialog 패턴 답습) — "정책 변경" 버튼 → "변경하시겠습니까?" 재확인

## 3. 인터페이스

### 3.1 Backend

**Entity**: `TrashPolicy` (id=1, retentionDays, updatedAt, updatedBy)
**Repository**: `TrashPolicyRepository extends JpaRepository<TrashPolicy, Short>` — `findById((short) 1)` 단일 조회.
**Service**: `TrashPolicyService` (`@Service`)
- `int getRetentionDays()` — `findById(1).get().getRetentionDays()`. 미존재 시 `IllegalStateException` (V17 migration 전제).
- `void updateRetentionDays(int newDays, UUID actorId)` — 7~90 검증 + `findById(1).get()` row 업데이트 + `setUpdatedBy(actorId)` + `setUpdatedAt(now)` + `RetentionPolicyChangedEvent` publish.
**Audit listener**: `TrashPolicyAuditListener` — `@TransactionalEventListener(AFTER_COMMIT)`로 event를 audit_log row로 변환 (`TeamAuditListener` 패턴 동형).
**Controller**: `AdminTrashPolicyController.update(PUT)` — body `{ days: number }`, 응답 `200 { retentionDays: 14 }` 또는 `400 VALIDATION_ERROR` (범위 위반).

`FileMutationService` / `FolderMutationService`: `TrashRetentionProperties.days()` → `trashPolicyService.getRetentionDays()` 로 호출 변경. `TrashRetentionProperties` 자체는 backward-compat용 default 값 source로 유지 (V17 migration이 row 부재 시 `INSERT (1, retention.days)`로 초기화).

### 3.2 Frontend

**API wrapper**: `api.updateTrashPolicy(days: number): Promise<{ retentionDays: number }>` — `PUT /api/admin/trash/policy` + CSRF.
**Hook**: `useUpdateTrashPolicy()` — `useMutation`, onSuccess invalidate `qk.adminTrashPolicy()`.
**UI**: `/admin/retention` page에 mutation 섹션 추가:
- input (number, 7~90, default 현재 값)
- "정책 변경" 버튼 (현재 값과 다를 때만 활성화)
- 감소 시 경고 텍스트 인라인 노출
- 클릭 → ConfirmDialog → mutate
- 성공 시 toast.success + 페이지 자동 갱신

## 4. 회귀 가드

### Backend
- `TrashPolicyServiceTest` — get/update 단위 테스트 (mock repo)
- `AdminTrashPolicyControllerTest` (WebMvc slice) — GET 200 / PUT 200/400/403 (non-ADMIN)
- `TrashPolicyAuditListenerTest` — RetentionPolicyChangedEvent → audit row 변환

### Frontend
- `api.updateTrashPolicy.test.ts` — PUT 메서드 + URL + CSRF + body shape + 4xx envelope
- `useUpdateTrashPolicy.test.tsx` — invalidate 검증
- `RetentionPolicyEditor.test.tsx` (또는 page test 확장) — input 변경 / 감소 경고 노출 / submit body shape / confirm dialog flow

## 5. 검증 계획

### Phase A
- 코드 0줄, docs 정합성 self-review.

### Phase B
- `./gradlew test --tests "*TrashPolicy*"` 통과
- `./gradlew test --tests "*AdminTrashPolicyControllerTest"` 통과
- 회귀: `FileMutationService`/`FolderMutationService` 기존 테스트 PASS (mock으로 `TrashPolicyService` 주입 변경 필요)

### Phase C
- `pnpm typecheck && lint && test --run` 통과
- 신규 vitest 추가, 기존 `/admin/retention` 페이지 테스트 회귀 zero

## 6. 위험 / 함정

- **CHECK 제약 위반 시 backend 응답** — 7~90 범위 밖 입력 시 DataIntegrityViolation → service에서 사전 검증 + 400 mapping. UI도 `min/max` HTML5 가드.
- **2인 승인 deferred 명시** — UI confirm dialog 텍스트에 "단일 ADMIN 즉시 적용" 표현 (운영자가 2인 승인 기대했다가 의외 변경 방지).
- **기존 row 재계산 안 함 명시** — confirm dialog에 명시 + spec audit comment.
- **AdminTrashPolicyController GET 응답 shape 변경 없음** — 기존 frontend `useAdminTrashPolicy` hook 무영향 확인.

## 7. acceptance criteria

### Phase A (본 PR)
- [ ] dev-docs 3종 작성 (plan/tasks/context)
- [ ] docs/04 §8.3 + §9.2 mutation UI 명세 추가 (현재 deferred → 본 트랙 도입 마커)
- [ ] docs/02 §7.x PUT endpoint 명세 추가
- [ ] docs/03 §4 RETENTION_POLICY_CHANGED audit event 추가
- [ ] docs/01 §16 (or 통합) mutation UI 컴포넌트 명세 추가

### Phase B (별도 PR)
- [ ] V17 migration + entity + repo + service + listener + controller PUT
- [ ] FileMutationService/FolderMutationService 호출 전환
- [ ] 회귀 가드 backend test 추가
- [ ] master 그린

### Phase C (별도 PR)
- [ ] api wrapper + hook + UI editor
- [ ] confirm dialog + 경고 UX
- [ ] 회귀 가드 vitest 추가
- [ ] master 그린
