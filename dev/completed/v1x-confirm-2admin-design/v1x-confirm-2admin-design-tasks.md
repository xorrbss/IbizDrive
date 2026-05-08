---
Last Updated: 2026-05-09
Status: design-only phase. v1.x 진입 시 §B 부터 실행.
---

# Tasks — v1x-confirm-2admin-design

## §A. 설계 정합화 (현재 트랙 — 코드 0줄)

- [x] A.1 ADR #47 신규 (docs/00 §5) — Generic dual-approval framework
- [x] A.2 ADR #46 보강 (docs/00 §5) — Legal Hold release framework 이관 명시
- [x] A.3 docs/02 §2.11 `pending_admin_approvals` 스키마 reserve + 인덱스 4개 + state machine
- [x] A.4 docs/02 §2.12 ER 요약에 pending_admin_approvals edge 추가
- [x] A.5 docs/02 §8 에러 코드 4종 추가 — `APPROVAL_REQUIRED` 202, `APPROVAL_SELF` 403, `APPROVAL_NOT_FOUND` 404, `APPROVAL_ALREADY_DECIDED` 409
- [x] A.6 docs/03 §3.1 `APPROVE_ADMIN_ACTION` 권한 enum 추가
- [x] A.7 docs/03 §3.2.5 ROLE 매트릭스 — ADMIN에 `APPROVE_ADMIN_ACTION` 명시
- [x] A.8 docs/03 §4.1 audit enum 4종 추가 — `admin.approval.requested/granted/rejected/expired`
- [x] A.9 docs/03 §6.4 본문화 — 정책/데이터 모델/Tier 0/self-approval/API/audit/per-action 게이트/만료/v1.x 작업 분해 9 sub-section
- [x] A.10 docs/04 §16 본문화 — Tier 0/활성화 정책/운영 흐름/관리자 페이지/운영 런북/v1.x 분해 6 sub-section
- [x] A.11 dev/active/v1x-confirm-2admin-design/ plan/context/tasks 3파일 작성
- [x] A.12 docs/progress.md 갱신 — 본 세션 핵심 결정 기록

## §B. v1.x 활성화 (현재 미실행 — 트리거: v1.x 진입 시점)

### B.1 스키마 + 권한

- [ ] V_ 마이그레이션: `pending_admin_approvals` 테이블 + 인덱스 4개 (docs/02 §2.11)
- [ ] `Permission.APPROVE_ADMIN_ACTION` enum 추가 + `IbizDrivePermissionEvaluator` ROLE=ADMIN 매핑
- [ ] `frontend/src/types/permission.ts` mirror 갱신
- [ ] `AuditEventType` 신규 4종 추가 (`admin.approval.requested/granted/rejected/expired`)
- [ ] `frontend/src/types/audit.ts` mirror 갱신

### B.2 도메인 (entity / repository / service)

- [ ] `PendingAdminApproval` entity + `PendingAdminApprovalRepository`
  - `findByActionTypeAndStatus(actionType, status, Pageable)` — pending 목록
  - `findByRequesterAndStatusIn(userId, statuses[], Pageable)` — 요청자 history
  - `findExpiredRequested(now, Pageable)` — expiration cron 후보
  - `lockById(id)` — pessimistic lock (decision/cron 직렬화)
- [ ] `PendingAdminApprovalService`
  - `enqueue(actionType, payload, requestedBy)` — INSERT + REQUESTED + secondary 후보 알림
  - `approve(approvalId, secondary, decisionReason?)` — self-approval 차단 + state 검증 + payload deserialize → action 실행 + status=APPROVED + audit
  - `reject(approvalId, secondary, decisionReason)` — self-approval 차단 + state 검증 + status=REJECTED
  - `cancel(approvalId, requesterId)` — requested_by 본인만 + state 검증 + status=CANCELLED
  - `expire(approvalId)` — cron 호출, status=EXPIRED + actor=NULL
- [ ] `PayloadDeserializer` per action_type — `role_change` → `RoleChangePayload(userId, fromRole, toRole, reason)` 등
- [ ] `PendingAdminApprovalExpirationJob` — `@Scheduled(cron)`, share-expired-cron 동형 (default `enabled=false`)

### B.3 Audit + Email

- [ ] `PendingAdminApproval{Requested,Granted,Rejected,Expired}Event` records
- [ ] `PendingAdminApprovalAuditListener` — `@TransactionalEventListener(phase=AFTER_COMMIT)`, `REQUIRES_NEW`로 audit_log INSERT
- [ ] `PendingAdminApprovalEmailListener` — async fire-and-forget (ADR #45 패턴):
  - `requested` → 모든 ADMIN (requested_by 제외) 알림
  - `granted`/`rejected`/`expired` → requested_by 알림

### B.4 Controller / endpoint

- [ ] `PendingAdminApprovalController` (`/api/admin/approvals`)
  - `GET` (목록 + 필터: status, actionType, page) — `@PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")` (목록 조회는 둘 다 가능)
  - `GET /:id` (상세) — 동일
  - `POST /:id/approve` `{decisionReason?}` — `@PreAuthorize("hasAuthority('APPROVE_ADMIN_ACTION')")`
  - `POST /:id/reject` `{decisionReason}` (reason 필수)
  - `DELETE /:id` (cancel — requested_by 본인만, service-level 검증)
- [ ] `PendingAdminApprovalDto` / `ApprovalDecisionRequest`
- [ ] envelope: 4종 에러 응답 details

### B.5 기존 controller 진입점 변형

- [ ] `AdminUserController.updateUser` (role 필드 변경 분기):
  - `app.dual-approval.role-change.enabled=false` → 즉시 실행 (기존)
  - `=true` + role 변경 있음 → `pendingAdminApprovalService.enqueue('role_change', {...})` + 202 + `APPROVAL_REQUIRED` envelope
- [ ] `AdminTrashController.purge` / `AdminTrashController.bulk` (action='purge'):
  - 동일 패턴 — `app.dual-approval.trash-purge.enabled` 검사
- [ ] `AdminTrashPolicyController.update` (PUT 신규 — retention 변경):
  - 동일 패턴 — `app.dual-approval.retention-change.enabled` 검사

### B.6 ADR #46 보강 (Legal Hold dual-approval 이관)

- [ ] V_ 마이그레이션 — `legal_holds.dual_approval_status`/`secondary_approver_id`/`secondary_approved_at` 컬럼 deprecation:
  - 옵션 1: column drop + 기존 active dual-approval row를 framework로 데이터 이동
  - 옵션 2: NULL 강제 (column 보존 + check constraint)
- [ ] `LegalHoldService.releaseLegalHold` 재작성 — framework `enqueue('legal_hold_release', {holdId, releaseReason})` 사용
- [ ] ADR #46 본문 보강 — 이관 V_ 마이그레이션 # 명시 + status: 부분 deprecated
- [ ] (v2.x 활성화 + v1.x 활성화 순서에 따라 마이그레이션 본문 결정)

### B.7 Frontend

- [ ] `lib/queryKeys.ts` — `qk.adminApprovals.list(filters)`, `.detail(id)`, `.byRequester(userId)`
- [ ] `lib/api.ts` — list/get/approve/reject/cancel wrapper
- [ ] `hooks/useAdminApprovals.ts` + `useAdminApproval(id)` + mutation hooks
- [ ] `app/admin/approvals/page.tsx` — 목록 + 필터
- [ ] `app/admin/approvals/[id]/page.tsx` — 상세 + decision dialog
- [ ] `components/admin/AdminSideNav.tsx` — `/admin/approvals` 항목 추가 (pending 배지 포함)
- [ ] `app/admin/users/[id]/page.tsx` — 202 응답 처리 (toast + redirect)
- [ ] `app/admin/trash/*` — 202 응답 처리
- [ ] `app/admin/trash/policy/page.tsx` — mutation 도입 시 202 처리

### B.8 검증

- [ ] 단위 테스트: `PendingAdminApprovalServiceTest` (enqueue/approve/reject/cancel/expire/self-approval/state machine)
- [ ] 단위 테스트: 각 action_type payload deserializer test
- [ ] 통합 테스트: `PendingAdminApprovalControllerIntegrationTest` (REST entry → service → DB)
- [ ] 통합 테스트: 진입점 변형 (`AdminUserController` role_change 분기 등)
- [ ] 통합 테스트: `PendingAdminApprovalExpirationJobIntegrationTest`
- [ ] e2e (Playwright): role 변경 dual-approval 흐름 (admin A 요청 → admin B 승인 → 적용)
- [ ] CLAUDE.md §3 핵심 원칙 6/7 정합 검증 — state transition + SELECT FOR UPDATE로 race-free 보장

### B.9 운영 / 문서

- [ ] docs/04 §15 베타 운영 런북에 dual-approval sub-section 추가 (긴급 우회 절차 + 만료 누적 모니터링)
- [ ] docs/03 §6.4 / docs/04 §16 "v1.x deferred" 표식 제거 + 활성화 일자 기록
- [ ] ADR #47 Status: Active (현재 deferred → active로 변경)
- [ ] (v2.x 진입 시) ADR #46 supersede 마무리

## §C. v1.x 후반 (Tier 1 적용)

- [ ] `cron_toggle` action_type 추가 — admin-cron-toggle (#102) 진입점 변형
- [ ] `user_deactivate` action_type 추가 — `AdminUserController.deactivate` 변형
- [ ] N명 secondary 합의 ("2 of 3 admins") 정책 — 별도 ADR
- [ ] approval cancellation audit enum 도입 검토 — 운영자 요구 시
- [ ] dual-approval 통계 대시보드 (KPI 추가 — 평균 결정 시간 / 만료율 / 거부율)

## §D. 산출물 위치

- ADR: docs/00 §5 (#47 신규, #46 보강)
- 스키마: docs/02 §2.11 (reserve), V_ 마이그레이션 (B.1 활성화 시)
- 에러 코드: docs/02 §8
- 보안/컴플라이언스: docs/03 §6.4
- 권한 enum: docs/03 §3.1, §3.2.5
- Audit enum: docs/03 §4.1
- 운영 명세: docs/04 §16, §15 (활성화 시 sub-section 추가)
- dev-docs: 본 디렉터리 — v1.x closure 시 dev/completed/v1x-confirm-2admin-design/ 으로 이동
