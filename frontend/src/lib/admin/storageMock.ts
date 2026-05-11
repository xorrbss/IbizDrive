/**
 * AdminStorage mock 데이터 — 디자인 핸드오프 2026-05-10 data.js
 * (ADMIN_CLEANUP) 1:1 매핑.
 *
 * <p>본 모듈은 design fidelity sweep Phase 3c 의 frontend-only 재현이며, 실제
 * cleanup metric backend endpoint(GET /api/admin/storage/cleanup-history)는
 * v1.x backlog 에 남아 있다 (`docs/v1x-backlog.md`). 따라서 본 모듈은 fetch 없이
 * 정적 export 만 제공한다.
 *
 * <p>현재 backend 가 노출하는 storage/overview 응답의 `orphanCleanup` 은 "마지막
 * orphan 정리 1건" 만 다루므로 design 의 4-카테고리 정리 기록(휴지통 자동 /
 * orphan / 만료 공유 / 만료 권한) 시리즈를 재현하지 못한다. 본 mock 은 그 시각
 * fidelity 를 메우는 용도이며 backend endpoint 가 합류하면 hook 으로 교체한다.
 */

/** 정리 작업 카테고리 — design data.js cleanup category enum 과 1:1. */
export type AdminCleanupKind =
  | 'trash-auto'
  | 'orphan'
  | 'expired-share'
  | 'expired-permission'

/** 정리 작업 기록 1건 (data.js ADMIN_CLEANUP entry). */
export interface AdminCleanupEntry {
  /** 기록 id (예: "cl_01") */
  id: string
  /** ISO timestamp — 정리 작업 실행 시각 */
  when: string
  /** 정리 유형 (4종 enum) */
  kind: AdminCleanupKind
  /** 회수된 용량 (bytes, decimal) */
  reclaimedBytes: number
  /** 처리된 객체 수 */
  objects: number
}

/**
 * 정리 기록 mock — design data.js ADMIN_CLEANUP 8건.
 * 시간 역순(최근 → 과거)으로 정렬되어 있다.
 */
export const ADMIN_CLEANUP: readonly AdminCleanupEntry[] = [
  { id: 'cl_01', when: '2026-05-11T02:00:00Z', kind: 'trash-auto',         reclaimedBytes:  82_400_000_000, objects: 1_284 },
  { id: 'cl_02', when: '2026-05-10T17:30:00Z', kind: 'orphan',             reclaimedBytes:  14_200_000_000, objects:   312 },
  { id: 'cl_03', when: '2026-05-10T11:15:00Z', kind: 'expired-share',      reclaimedBytes:           0,      objects:    47 },
  { id: 'cl_04', when: '2026-05-09T02:00:00Z', kind: 'trash-auto',         reclaimedBytes:  61_800_000_000, objects:   942 },
  { id: 'cl_05', when: '2026-05-08T22:45:00Z', kind: 'expired-permission', reclaimedBytes:           0,      objects:   118 },
  { id: 'cl_06', when: '2026-05-08T02:00:00Z', kind: 'trash-auto',         reclaimedBytes:  54_100_000_000, objects:   811 },
  { id: 'cl_07', when: '2026-05-07T17:30:00Z', kind: 'orphan',             reclaimedBytes:   8_700_000_000, objects:   206 },
  { id: 'cl_08', when: '2026-05-06T02:00:00Z', kind: 'trash-auto',         reclaimedBytes:  47_300_000_000, objects:   704 },
]

/** 정리 유형 한글 라벨 — design data.js 의 label map. */
export const ADMIN_CLEANUP_LABEL: Record<AdminCleanupKind, string> = {
  'trash-auto':         '휴지통 자동 정리',
  'orphan':             '고아 객체 정리',
  'expired-share':      '만료 공유 정리',
  'expired-permission': '만료 권한 정리',
}
