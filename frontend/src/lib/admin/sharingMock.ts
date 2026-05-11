/**
 * AdminSharing mock 데이터 — 디자인 핸드오프 2026-05-10 data.js
 * (ADMIN_FLAGGED L258~261, ADMIN_POLICIES L271~280) 1:1 매핑.
 *
 * <p>본 페이지(/admin/sharing)는 design fidelity sweep Phase 3a의 frontend-only
 * 재현이며, 실제 backend sharing-policy endpoint(POST/PUT)는 v1.x backlog에
 * 남아 있다 (`docs/v1x-backlog.md`). 따라서 본 모듈은 fetch 없이 정적 export만
 * 제공하며, 페이지 컴포넌트는 `useState`로 frontend-only mutation을 시뮬레이션한다.
 *
 * <p>backend endpoint가 합류하면 본 파일은 그대로 두고 hook(`useAdminSharingPolicy`,
 * `useUpdateAdminSharingPolicy`)을 신규로 추가, 페이지가 hook 데이터를 우선하도록
 * 교체한다 (mock은 storybook/test fixture로 살린다).
 */

/** 플래그된 공유 검토 큐 항목 (data.js ADMIN_FLAGGED) */
export interface AdminFlaggedShare {
  /** flag id (예: "fl_01") */
  id: string
  /** 파일명 (한글/원본 그대로) */
  file: string
  /** 작성/공유 actor user id (예: "u_taeho") — design mock은 id-only */
  actor: string
  /** ISO timestamp */
  when: string
  /** flag 이유 텍스트 */
  reason: string
  /** action 상태 — design mock은 "검토 대기" 단일 */
  action: string
}

/** 외부 공유 정책 (data.js ADMIN_POLICIES) */
export interface AdminSharingPolicies {
  /** 휴지통 보존 일수 — /admin/retention 페이지가 진실. 본 페이지는 참조하지 않음 */
  trashRetentionDays: number
  /** 외부 공유 기본 만료 정책 (예: "expires-30d") */
  externalShareDefault: string
  /** 공개 링크 정책 (예: "admin-approval") */
  publicLinks: string
  /** 허용 도메인 목록 */
  domainAllowlist: string[]
  /** 차단 도메인 목록 */
  domainBlocklist: string[]
  /** MFA 강제 여부 */
  mfaRequired: boolean
  /** SSO 제공자 (예: "Okta") */
  ssoProvider: string
  /** 파일 버전 보관 개수 */
  fileVersionLimit: number
}

export const ADMIN_FLAGGED: readonly AdminFlaggedShare[] = [
  {
    id: 'fl_01',
    file: 'ingest-pipeline.py',
    actor: 'u_taeho',
    when: '2026-05-09T12:08:44',
    reason: '공개 링크 — 코드 자산 외부 노출 가능',
    action: '검토 대기',
  },
  {
    id: 'fl_02',
    file: '고객 명부 2026.xlsx',
    actor: 'u_yuna',
    when: '2026-05-08T15:21:09',
    reason: 'PII 포함 가능 파일을 외부 도메인 공유',
    action: '검토 대기',
  },
]

export const ADMIN_POLICIES: AdminSharingPolicies = {
  trashRetentionDays: 30,
  externalShareDefault: 'expires-30d',
  publicLinks: 'admin-approval',
  domainAllowlist: ['ibizsoft.net', 'partner.de'],
  domainBlocklist: ['temp-mail.com'],
  mfaRequired: true,
  ssoProvider: 'Okta',
  fileVersionLimit: 50,
}

/** flagged actor id → 표시 이름 (design data.js의 userById 대체). 알 수 없는 id는 id 그대로 표시 */
export const FLAGGED_ACTOR_NAMES: Readonly<Record<string, string>> = {
  u_taeho: '강태호',
  u_yuna: '박유나',
}
