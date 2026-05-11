/**
 * AdminRetention mock 데이터 — 디자인 핸드오프 2026-05-10 admin.jsx
 * §AdminRetention LegalRow (L913~919, L924~944) 1:1 매핑.
 *
 * <p>본 모듈은 design fidelity sweep Phase 3d 의 frontend-only 재현이며, Legal
 * Hold 본 기능은 `docs/03 §6.3` 에 v2.x deferred 로 명시되어 있다. 따라서 본
 * 모듈은 fetch 없이 정적 export 만 제공한다.
 *
 * <p>backend Legal Hold endpoint (`GET/PUT /api/admin/legal-holds`) 가 합류하면
 * 본 파일은 그대로 두고 hook(`useAdminLegalHolds`)을 신규로 추가, 페이지가 hook
 * 데이터를 우선하도록 교체한다 (mock 은 storybook/test fixture 로 살린다).
 */

/** 법적 보존 항목 1건 — design admin.jsx LegalRow props 와 1:1. */
export interface AdminLegalHold {
  /** 보존 id (예: "lh_01") */
  id: string
  /** 보존 대상 파일명 */
  file: string
  /** 보존 사유 */
  reason: string
  /** 보존 만료 일자 (YYYY-MM-DD) */
  until: string
  /** 보존 설정한 actor user id (예: "u_me") — design mock 은 id-only */
  by: string
  /** 설정한 actor 표시 이름 — design 의 USERS map 을 frontend mock 으로 보강 */
  byName: string
}

/**
 * 법적 보존 항목 mock — design admin.jsx §AdminRetention (L913~919) 의 3건.
 * 보존 만료가 가장 가까운 순서가 아닌 design 원본 순서를 유지한다.
 */
export const ADMIN_LEGAL_HOLDS: readonly AdminLegalHold[] = [
  {
    id: 'lh_01',
    file: '2026년 Q1 재무 결산 보고서.pdf',
    reason: '감사 진행 중',
    until: '2027-05-08',
    by: 'u_me',
    byName: '나',
  },
  {
    id: 'lh_02',
    file: '2025 인사 평가 자료.zip',
    reason: '노무 분쟁 계류',
    until: '2026-12-31',
    by: 'u_jiyoung',
    byName: '이지영',
  },
  {
    id: 'lh_03',
    file: '고객 계약서 — Globex Corp.pdf',
    reason: '계약 만료 후 7년',
    until: '2032-08-15',
    by: 'u_jiyoung',
    byName: '이지영',
  },
]
