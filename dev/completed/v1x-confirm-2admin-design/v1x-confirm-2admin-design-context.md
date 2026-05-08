---
Last Updated: 2026-05-09
---

# Context — v1x-confirm-2admin-design

## 트리거

여러 종료 entry "다음 세션 컨텍스트"에서 반복 언급된 "2인 승인" 항목을 spec으로 정합화.

- admin-cron-toggle (#102): "2인 승인 워크플로(파괴적 토글 보호) — Wave 2 closure backlog의 일반 항목"
- wave2-trash-retention-config (#108): "2인 승인" 명시
- audit-export-cap-metadata (#101): "2인 승인" 언급

Legal Hold (#113, ADR #46)이 release dual-approval를 자체 정의했고, 본 트랙은 이를 generic framework로 추출하여 N개 액션에 일반 적용.

## 영향받는 트랙 인벤토리

### 활성 트랙과의 상호작용 (현재 영향 0, v1.x 활성화 시점에 정합 필요)

| 트랙 | 상호작용 | v1.x 영향 |
|---|---|---|
| **admin-cron-toggle** (#102) | cron 4종 toggle endpoint | Tier 1 (`cron_toggle`) — framework `enqueue` 호출만 추가 |
| **AdminUserController** | role 변경 endpoint (`PATCH /api/admin/users/:id`) | **Tier 0** — role_change 분기 + 게이트 검사 |
| **AdminTrashController** | manual + bulk purge | **Tier 0** — trash_purge 분기 |
| **wave2-trash-policy-viewer** (#114) | retention read-only viewer | Tier 0 mutation 활성화 시 — `retention_change` 분기 |
| **legal-hold-design** (#113, ADR #46) | dual-approval를 자기 컬럼으로 가짐 | framework 이관 (deprecated) |
| **share-expired-cron / permissions-expired-cron / legal-hold-expiration** | system expiration 패턴 | dual-approval-expiration-cron 동형 추가 |
| **a1.5 EmailService** (ADR #42/45) | 비동기 발송 | secondary 알림 + 결정 알림에 재사용 |

### Frontend 영향 지점

- `AdminSideNav.tsx` — `/admin/approvals` 신규 항목 추가
- `/admin/approvals` 페이지 신규 (목록, 상세, approve/reject/cancel)
- 기존 admin 페이지 (`/admin/users/:id`, `/admin/trash/*`) — 202 응답 처리 (toast + redirect)
- `lib/queryKeys.ts` — `qk.adminApprovals.*` 키 추가
- `lib/api.ts` — `listApprovals`/`getApproval`/`approveApproval`/`rejectApproval`/`cancelApproval`
- `types/permission.ts` — `APPROVE_ADMIN_ACTION` enum mirror
- `types/audit.ts` — 신규 audit enum 4종 mirror

### Backend 영향 지점 (v1.x 진입 시)

- V_ 마이그레이션: `pending_admin_approvals` 테이블 + 인덱스 4개
- `PendingAdminApproval` 엔티티 + `PendingAdminApprovalRepository`
- `PendingAdminApprovalService` (enqueue/approve/reject/cancel/expire)
- `PendingAdminApprovalController` (admin endpoints)
- `PendingAdminApprovalExpirationJob` (`@Scheduled`, share-expired-cron 동형)
- 기존 controller 진입점 변형:
  - `AdminUserController.updateUser` (role 변경 분기 시 framework enqueue)
  - `AdminTrashController.purge`, `AdminTrashController.bulk` (action='purge' 분기)
  - `AdminTrashPolicyController.update` (PUT 신규 + retention_change 분기)
- `Permission` enum + evaluator 매핑
- `AuditEventType` 신규 4종
- `PendingAdminApproval{Requested,Granted,Rejected,Expired}Event` records
- `PendingAdminApprovalAuditListener` (`@TransactionalEventListener` AFTER_COMMIT, REQUIRES_NEW)
- `PendingAdminApprovalEmailListener` (secondary 알림 + 결정 알림)

## 외부 제약

- **활성화 트리거**: v1.x 진입 시점. Wave 2 closure에는 코드 0줄.
- **Legal Hold ADR #46과 양립**: v2.x ADR #46 활성화 + v1.x ADR #47 활성화 순서 — ADR #47이 먼저 활성화되면 Legal Hold dual-approval은 ADR #46 활성화 시점에 framework 사용으로 직접 작성. 두 ADR 동시 활성화 시 동일 framework 공유.

## 결정 메모 (이전 세션 결과)

(본 세션이 초기 세션이라 prior decision 없음. 새 결정 = ADR #47.)

## 알려진 한계 / backlog

- **Tier 1 액션 미포함** (cron_toggle, user_deactivate): v1.x 1차 컷 미포함. framework는 호환 — controller 진입점에서 `enqueue` 호출만 추가하면 됨.
- **Multi-secondary approval**: 본 spec은 1명 secondary만 정의. N명 합의 (e.g. "2 of 3 admins") 같은 정책은 별도 ADR.
- **Legal Hold dual-approval 이관 V_ 마이그레이션**: ADR #46 활성화 시점에 결정 — 컬럼 drop 또는 NULL 강제 + payload_json='legal_hold_release'로 데이터 이동. 본 spec은 forward-reference만 명시.
- **Approval cancellation audit**: requested_by 본인 cancel은 audit row 미발행 (KISS). 운영자가 history 추적 시 `pending_admin_approvals` 직접 조회 (CANCELLED status 필터).
- **action 자체의 audit emit**: `role_change` APPROVED 시 실행되면 기존 `ADMIN_ROLE_CHANGED` audit이 listener로 emit. 본 framework는 governance trail (`admin.approval.*`)만 담당. 두 audit이 동일 트랜잭션에서 emit됨.
- **Concurrent decision race**: 두 secondary가 동시에 approve/reject 시도 → SELECT FOR UPDATE로 직렬화, 두 번째는 `APPROVAL_ALREADY_DECIDED` 409.
- **TTL 변경 시 기존 row 영향**: `app.dual-approval.ttl-days`는 expires_at 산정 시점에만 사용. 변경 후 새 요청부터 적용. 기존 pending 영향 0.
- **Multi-instance lock**: cron 잡들은 single-instance 가정 (`@SchedulerLock` 미도입, ADR #31/share-expired/permission-expired/legal-hold-expiration 일관). 멀티화 시 별도 ADR.

## 진행 중 결정 (open questions)

(없음 — 본 트랙은 design-only이며 사용자 확인 후 진행. v1.x 활성화 시점의 추가 결정은 task 단계에서 별도 ADR로 처리.)
