/**
 * 휴지통 타입 — M9 (docs/02 §7.11, ADR #32 mirror).
 *
 * backend `TrashItemDto` / `TrashPage` (com.ibizdrive.trash.*) 의 wire 1:1.
 * - `TrashItemType` wire는 lower-case ('file' | 'folder') — backend `TrashItemType.wire()`와 동일.
 * - `deletedAt` / `purgeAfter`는 ISO 8601 문자열 (Jackson `Instant` → `"2026-04-30T12:34:56Z"`).
 * - `originalParentId`는 root 위치였던 폴더의 경우 `null` 가능. file은 항상 non-null.
 *
 * 본 파일은 backend 응답을 그대로 표현하므로 형태 drift는 backend wire 변경 ADR을 동반해야 한다.
 */

export type TrashItemType = 'file' | 'folder'

export interface TrashItem {
  id: string
  name: string
  type: TrashItemType
  /** 삭제(휴지통 이동) 시각. ISO 8601. */
  deletedAt: string
  /** 자동 hard purge 예정 시각 (deletedAt + 30d 등 backend 정책). ISO 8601. */
  purgeAfter: string
  /** 원위치 부모 폴더 id. root였던 폴더는 null (file은 항상 non-null). */
  originalParentId: string | null
  /**
   * 원위치 부모 폴더의 절대 경로. leading `/`, trailing slash 없음 (예: `/회사/팀A/문서`).
   * `originalParentId`가 null이거나 backend의 부모 chain 종착 실패(데이터 corruption 또는
   * depth 100 초과) 시 null — UI는 NULL일 때 "원위치 미상" 폴백.
   *
   * <p>backend가 페이지 단위 recursive CTE로 일괄 계산 ({@link AdminTrashItem.originalParentPath}와
   * 동일 source `FolderRepository.findAncestorPaths`).
   */
  originalParentPath: string | null
}

export interface TrashPage {
  items: TrashItem[]
  /** 다음 페이지가 있을 때만 backend가 echo. 없으면 null. */
  nextCursor: string | null
}

// ───────────────────────────────────────────────────────────────────────────
// Plan E T13 — RestoreConflict reason 분기.
//
// backend `RESTORE_CONFLICT` envelope `details.reason` 와 1:1 매핑 (T3).
// - `name_conflict` — 원위치에 같은 이름의 항목이 이미 있음 (v1.x 기존 분기).
// - `scope_mismatch` — 원위치 폴더가 다른 workspace로 이동되어 복원 불가.
// ───────────────────────────────────────────────────────────────────────────

export type RestoreConflictReason = 'name_conflict' | 'scope_mismatch'

/**
 * RestoreConflictDialog 분기에 필요한 페이로드.
 * backend `RESTORE_CONFLICT` envelope `details.*` 의 프론트 표현.
 */
export interface RestoreConflictPayload {
  reason: RestoreConflictReason
  /** 충돌 대상 resource id (folder/file id). backend `details.resourceId`. */
  resourceId?: string
  // scope_mismatch 시 추가 컨텍스트 (관리자 문의용 메타) — backend `details.*`에서 echo.
  expectedScopeType?: 'department' | 'team'
  expectedScopeId?: string
  actualScopeType?: 'department' | 'team'
  actualScopeId?: string
}

// Wave 2 T9 — admin global trash (spec §4.4)
export interface AdminTrashItem {
  id: string
  name: string
  type: 'file' | 'folder'
  deletedAt: string  // ISO-8601
  purgeAfter: string
  ownerId: string
  ownerEmail: string
  originalParentId: string | null
  originalParentName: string | null
  /**
   * full-path-resolve follow-up — 삭제 직전 부모 폴더의 절대 경로. leading `/`,
   * trailing slash 없음 (예: `/회사/팀A/문서`). 부모가 root였으면 `/<parentName>`.
   * `originalParentId`가 null이면 path도 null. 데이터 corruption 또는 cycle 등으로 path 계산
   * 실패 시 null이며, 이때 UI는 `originalParentName` 단일 segment를 fallback으로 표시.
   */
  originalParentPath: string | null
  sizeBytes: number | null
  /**
   * V10 — 삭제 actor user id. NULL 의미: V10 적용 이전 row(backfill 미실시),
   * deleter 계정 hard-delete(FK ON DELETE SET NULL), unknown. UI는 "—" 렌더.
   */
  deletedById: string | null
  deletedByEmail: string | null
}

export interface AdminTrashFilters {
  q: string
  type: 'file' | 'folder' | null
  ownerId: string | null
  /**
   * deletedAt 하한(inclusive). `YYYY-MM-DD`. 빈 문자열은 null과 동치 — UI는 항상
   * `null`로 정규화 후 송신. backend가 KST(`Asia/Seoul`) 00:00 경계로 변환.
   */
  deletedFrom: string | null
  /**
   * deletedAt 상한(exclusive — 입력일 KST 종일 포함). `YYYY-MM-DD`. backend가 입력일+1의
   * KST 00:00 경계로 변환. 양쪽 모두 적용 시 deletedFrom < deletedTo여야 함.
   */
  deletedTo: string | null
}

export interface AdminTrashPage {
  items: AdminTrashItem[]
  nextCursor: string | null
}

// Wave 2 T9 follow-up — bulk restore/purge (spec §3)

/** wire 상수 — backend `BulkAction.from`이 정확 일치 검증. */
export type AdminTrashBulkAction = 'restore' | 'purge'

/** request item — `type` wire는 lower-case (`TrashItemType` 동일). */
export interface AdminTrashBulkItem {
  type: TrashItemType
  id: string
}

export interface AdminTrashBulkRequest {
  action: AdminTrashBulkAction
  /** 1..200개. 0 또는 201+은 backend가 400으로 거부. */
  items: AdminTrashBulkItem[]
}

/**
 * 응답은 항상 200 (부분 실패 허용). cap/action 검증 실패만 4xx.
 *
 * `failed[].error`는 안정적 enum-like 문자열: `"NOT_FOUND"`, `"NAME_CONFLICT"`,
 * `"INVALID_ITEM"`, `"INVALID_TYPE"` 등. UI는 toast에 그대로 노출.
 */
export interface AdminTrashBulkResponse {
  succeeded: AdminTrashBulkItem[]
  failed: Array<AdminTrashBulkItem & { error: string }>
}
