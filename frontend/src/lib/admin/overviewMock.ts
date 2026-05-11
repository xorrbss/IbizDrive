/**
 * AdminOverview mock 데이터 — 디자인 핸드오프 2026-05-10 data.js
 * (ADMIN_UPLOADS_28D L203~208, ADMIN_DEPARTMENTS L211~220) 1:1 매핑.
 *
 * <p>본 모듈은 design fidelity sweep Phase 3b 의 frontend-only 재현이며, 실제
 * upload-trend / dept-usage backend endpoint(GET /api/admin/metrics/uploads,
 * GET /api/admin/metrics/dept-usage)는 v1.x backlog 에 남아 있다
 * (`docs/v1x-backlog.md`). 따라서 본 모듈은 fetch 없이 정적 export 만 제공한다.
 *
 * <p>backend endpoint 가 합류하면 본 파일은 그대로 두고 hook(`useAdminUploadTrend`,
 * `useAdminDeptUsage`)을 신규로 추가, 페이지가 hook 데이터를 우선하도록 교체한다
 * (mock은 storybook/test fixture로 살린다).
 *
 * <p>플래그된 공유 mini 요약은 기존 `sharingMock` 의 `ADMIN_FLAGGED` 를 그대로 재사용
 * 한다 (data.js 상에서도 동일 source).
 */

/** 부서별 저장공간 사용량 (data.js ADMIN_DEPARTMENTS) */
export interface AdminDeptUsage {
  /** 부서 id (예: "d_eng") */
  id: string
  /** 부서명 (한글) */
  name: string
  /** 멤버 수 */
  members: number
  /** 사용량 (bytes, decimal) */
  used: number
  /** 할당량 (bytes, decimal) */
  quota: number
  /** 파일 개수 */
  files: number
  /** 전월 대비 사용량 증감비 (0.124 = +12.4%) */
  delta: number
  /** 부서별 색상 (hex) — legend dot / bar 색상 */
  color: string
}

/**
 * 일별 업로드 양 — 최근 28일 (bytes).
 * design data.js 의 GB 값(`[4.2, 3.8, ...]`)을 `bytes = gb * 1024^3` 로 변환한 값.
 */
export const ADMIN_UPLOADS_28D: readonly number[] = [
  4.2, 3.8, 5.1, 6.7, 4.4, 2.1, 1.6,
  5.8, 7.2, 6.4, 8.9, 7.8, 3.2, 2.0,
  6.6, 9.1, 8.4, 11.2, 9.8, 4.1, 2.8,
  10.4, 12.6, 11.0, 13.8, 14.2, 5.6, 3.9,
].map((gb) => Math.round(gb * 1024 * 1024 * 1024))

/**
 * 부서별 저장공간 사용량 — design data.js ADMIN_DEPARTMENTS 8건.
 * overview 위젯은 `slice(0, 5)` 만 노출한다.
 */
export const ADMIN_DEPARTMENTS: readonly AdminDeptUsage[] = [
  { id: 'd_eng',       name: '엔지니어링', members: 38, used: 412_000_000_000, quota: 600_000_000_000, files: 14_280, delta: 0.124, color: '#5B7FCC' },
  { id: 'd_design',    name: '디자인',     members: 14, used: 318_000_000_000, quota: 400_000_000_000, files:  8_412, delta: 0.218, color: '#C16A8B' },
  { id: 'd_sales',     name: '영업',       members: 26, used: 184_000_000_000, quota: 300_000_000_000, files:  9_104, delta: 0.041, color: '#5BA08A' },
  { id: 'd_marketing', name: '마케팅',     members: 11, used: 142_000_000_000, quota: 200_000_000_000, files:  5_682, delta: 0.176, color: '#C9925A' },
  { id: 'd_hr',        name: '인사',       members:  8, used:  68_400_000_000, quota: 150_000_000_000, files:  2_104, delta: 0.012, color: '#7C6BB5' },
  { id: 'd_finance',   name: '재무',       members:  9, used:  92_000_000_000, quota: 150_000_000_000, files:  3_220, delta: 0.034, color: '#A56FB8' },
  { id: 'd_ops',       name: '오퍼레이션', members: 12, used:  44_000_000_000, quota: 100_000_000_000, files:  1_840, delta: -0.018, color: '#7B8A9C' },
  { id: 'd_legal',     name: '법무',       members:  4, used:  27_000_000_000, quota:  80_000_000_000, files:    571, delta: 0.008, color: '#B9824D' },
]

/**
 * Decimal(1000-base) TB/GB 포매터 — design data.js `formatTBGB` 와 동일 로직.
 * 기존 `lib/formatBytes` 는 binary(1024) 단위로 storage KPI 와 일치하므로 분리.
 * design admin 카드 / upload chart / dept usage 는 모두 decimal 표기를 사용한다.
 */
export function formatTBGB(bytes: number): string {
  if (!Number.isFinite(bytes)) return '-'
  const tb = bytes / 1_000_000_000_000
  if (tb >= 1) return `${tb.toFixed(2)} TB`
  const gb = bytes / 1_000_000_000
  return `${gb.toFixed(1)} GB`
}

/** `formatPct(0.124)` → "12.4%". design data.js 와 동일. */
export function formatPct(n: number, decimals = 1): string {
  if (!Number.isFinite(n)) return '-'
  return `${(n * 100).toFixed(decimals)}%`
}
