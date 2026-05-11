---
task: trash-retention-mutation
last_updated: 2026-05-11
---

# Tasks

## Phase A — spec/plan (본 PR)

- [ ] **A1** dev-docs 3종 작성 (plan/tasks/context)
- [ ] **A2** `docs/04 §8.3` 정책 mutation UI 항목 마커: "v1.x deferred" → "본 트랙 (Phase B/C)"
- [ ] **A3** `docs/04 §9.2` 보존 정책 — yml 외 DB-backed mutation 도입 명시 (V17 + service + endpoint)
- [ ] **A4** `docs/02-backend-data-model.md §7.x` PUT `/api/admin/trash/policy` 명세 (request body, response, error codes)
- [ ] **A5** `docs/02-backend-data-model.md §2` schema 표에 `trash_policy` 추가 (V17)
- [ ] **A6** `docs/03-security-compliance.md §4` `RETENTION_POLICY_CHANGED` audit event 추가
- [ ] **A7** `docs/01-frontend-design.md §16` (or 적절 위치) — `RetentionPolicyEditor` 컴포넌트 명세
- [ ] **A8** `docs/04 §15.4` 운영 런북 — 단일-approver MVP 명시 + 2인 승인은 v1.x 명시 유지
- [ ] **A9** progress.md 세션 기록 (Phase A 종료)
- [ ] **A10** commit + PR (docs only)

## Phase B — backend (별도 PR)

- [ ] **B1** V17 migration `trash_policy` (single-row, CHECK days BETWEEN 7 AND 90, FK updated_by)
- [ ] **B2** `TrashPolicy` entity + `TrashPolicyRepository`
- [ ] **B3** `TrashPolicyService` (`getRetentionDays`, `updateRetentionDays`)
- [ ] **B4** `RetentionPolicyChangedEvent` + `TrashPolicyAuditListener`
- [ ] **B5** `AuditEventType.RETENTION_POLICY_CHANGED` + `AuditTargetType.TRASH_POLICY`
- [ ] **B6** `AdminTrashPolicyController.update(PUT)` + DTO + GlobalExceptionHandler 매핑
- [ ] **B7** `FileMutationService` + `FolderMutationService` `TrashRetentionProperties.days()` → `trashPolicyService.getRetentionDays()`
- [ ] **B8** `TrashRetentionProperties` 처리 — V17 migration이 row 미존재 시 yml 값으로 INSERT (backward compat)
- [ ] **B9** unit + WebMvc slice tests (TrashPolicyServiceTest, AdminTrashPolicyControllerTest, TrashPolicyAuditListenerTest)
- [ ] **B10** 기존 FileMutationService/FolderMutationService 테스트 mock 전환
- [ ] **B11** `./gradlew test` 통과
- [ ] **B12** progress.md 기록 + commit + PR

## Phase C — frontend (별도 PR)

- [ ] **C1** `frontend/src/lib/api.ts` `updateTrashPolicy(days)` 추가 (CSRF + PUT body)
- [ ] **C2** `api.updateTrashPolicy.test.ts` 회귀 가드
- [ ] **C3** `frontend/src/hooks/useUpdateTrashPolicy.ts` (`useMutation` + onSuccess invalidate)
- [ ] **C4** `useUpdateTrashPolicy.test.tsx` 회귀 가드
- [ ] **C5** `frontend/src/components/admin/RetentionPolicyEditor.tsx` (input + 경고 + ConfirmDialog flow)
- [ ] **C6** `RetentionPolicyEditor.test.tsx` 회귀 가드 (input 변경 / 감소 경고 / submit body / confirm flow)
- [ ] **C7** `app/admin/retention/page.tsx` mutation 섹션 wire (admin 보유 + 데이터 로드 시 노출)
- [ ] **C8** `pnpm typecheck && lint && test --run` 통과
- [ ] **C9** progress.md 기록 + commit + PR
