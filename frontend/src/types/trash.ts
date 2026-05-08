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
}

export interface TrashPage {
  items: TrashItem[]
  /** 다음 페이지가 있을 때만 backend가 echo. 없으면 null. */
  nextCursor: string | null
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
  sizeBytes: number | null
}

export interface AdminTrashFilters {
  q: string
  type: 'file' | 'folder' | null
  ownerId: string | null
  /**
   * deletedAt 하한(inclusive). `YYYY-MM-DD`. 빈 문자열은 null과 동치 — UI는 항상
   * `null`로 정규화 후 송신. backend가 UTC 00:00:00Z 경계로 변환.
   */
  deletedFrom: string | null
  /**
   * deletedAt 상한(exclusive — 입력일 종일 포함). `YYYY-MM-DD`. backend가 입력일+1의
   * UTC 00:00:00Z 경계로 변환. 양쪽 모두 적용 시 deletedFrom < deletedTo여야 함.
   */
  deletedTo: string | null
}

export interface AdminTrashPage {
  items: AdminTrashItem[]
  nextCursor: string | null
}
