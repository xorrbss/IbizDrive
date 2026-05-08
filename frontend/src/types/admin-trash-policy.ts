/**
 * `/admin/trash/policy` 응답 타입 — wave2-trash-policy-viewer (Wave 2 T9 follow-up).
 *
 * 백엔드 `AdminTrashPolicyDto` (record(int retentionDays)) wire-호환.
 *
 * cron 운영 상태(enabled/cron/zone)는 `/admin/system` (Wave 1 T3, admin-cron-toggle 진행 중)
 * 가 진실의 출처라 본 타입에는 포함하지 않는다 — 정적 yml-bound retention만 본다.
 */
export interface AdminTrashPolicy {
  retentionDays: number
}
