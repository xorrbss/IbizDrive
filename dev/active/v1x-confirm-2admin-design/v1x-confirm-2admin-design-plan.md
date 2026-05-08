---
Last Updated: 2026-05-09
Status: design-only (코드 0줄). v1.x 진입 시 본 plan을 그대로 실행.
---

# Plan — v1x-confirm-2admin-design

## 요약

Generic dual-approval framework — **v1.x deferred** (docs/00 §5 ADR #47). 본 트랙은 활성화 작업이 아니라 **설계 명세 정합화**만 수행한다. 산출물:

1. ADR #47 신규 (Generic dual-approval framework: `pending_admin_approvals` + state machine + per-action 게이트)
2. ADR #46 보강 (Legal Hold release를 framework로 이관 명시 — v2.x 진입 시 함께 처리)
3. docs/02 §2.11 `pending_admin_approvals` 테이블 reserve + 인덱스 4종 + state machine
4. docs/02 §8 신규 에러 코드 4종 (`APPROVAL_REQUIRED` 202, `APPROVAL_SELF` 403, `APPROVAL_NOT_FOUND` 404, `APPROVAL_ALREADY_DECIDED` 409)
5. docs/03 §3.1 권한 enum `APPROVE_ADMIN_ACTION` + §3.2.5 ROLE 매트릭스
6. docs/03 §4.1 audit enum 신규 4종 (`admin.approval.requested/granted/rejected/expired`)
7. docs/03 §6.4 본문화 (Dual-approval Framework 9 sub-section)
8. docs/04 §16 본문화 (운영 명세 6 sub-section: Tier 0 / 활성화 정책 / 운영 흐름 / `/admin/approvals` UI / 운영 런북 / v1.x 작업 분해)

**v1.x 활성화 시점에 본 plan + tasks를 직접 실행** — 설계 변경 없이 task 분해 그대로 진행 가능.

## 핵심 결정 (ADR #47)

**데이터 모델 = Generic framework + per-action config 게이트**

- `pending_admin_approvals` 단일 메타 테이블 + `action_type` ENUM + `payload_json` JSONB
- State machine: `REQUESTED → APPROVED|REJECTED|CANCELLED|EXPIRED` (terminal 4종)
- Per-action 게이트: `app.dual-approval.{role-change|trash-purge|retention-change}.enabled` (default false)
- TTL: `app.dual-approval.ttl-days` (default 7일) + expiration cron (share-expired/permission-expired/legal-hold-expiration 동형)

**거부된 대안**:
- A. 각 액션 자기 테이블에 dual_approval 컬럼 추가 — Legal Hold만 자기 메타 테이블이라 자연스러웠음. cron toggle / role change / retention change 등 단발성 mutation은 별도 테이블 두기 부적절. N개 액션마다 컬럼 폭발.

## Tier 0 적용 액션 (1차)

| action_type | 위험도 | 진입점 변형 | payload_json |
|---|---|---|---|
| `role_change` | 보안 critical | `PATCH /api/admin/users/:id` (role 필드) | `{userId, fromRole, toRole, reason}` |
| `trash_purge` | 회복 불가 | `DELETE /api/admin/trash/:type/:id`, `POST /api/admin/trash/bulk` | `{type, ids[], reason?}` |
| `retention_change` | 데이터 손실 | `PUT /api/admin/trash/policy` (deferred) | `{fromDays, toDays, reason}` |

## Tier 1 backlog (v1.x 후속)

- `cron_toggle` — admin-cron-toggle (#102) "파괴적 토글 보호"
- `user_deactivate` — 로그인 차단

## N/A (이관)

- `legal_hold_release` — ADR #46 framework로 이관 (v2.x V_ 마이그레이션 + payload_json='legal_hold_release')

## Self-approval 차단

- 모든 action_type 공통: `secondary != requested_by`
- `role_change`: secondary != `payload.userId`
- `trash_purge`/`retention_change`: 추가 체크 없음

## v1.x 진입 시 task 분해

→ `v1x-confirm-2admin-design-tasks.md` 참조. 14단계 (V_ 마이그레이션 → permission/audit enum → entity/repository/service → controller → frontend → 검증 → ADR #46 보강 → 운영 런북).

## 참조 문서

- docs/00 §5 ADR #47, ADR #46 보강
- docs/02 §2.11 (스키마 reserve), §8 (에러 코드)
- docs/03 §3.1 (권한 enum), §3.2.5 (ROLE 매트릭스), §4.1 (audit enum), §6.4 (본문)
- docs/04 §16 (운영 명세), §15 (베타 운영 런북 — 진입 시 sub-section 추가)
- ADR #42/45 (EmailService — 알림 재사용)
- ADR #34 (share-expired-cron), permissions-expired-cron, legal-hold-expiration (cron 패턴 답습)
- ADR #46 (Legal Hold dual-approval — framework로 이관 대상)
