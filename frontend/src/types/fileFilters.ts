/**
 * 파일/폴더 목록 필터 타입 — design-zip components.jsx §FilterPopover/§FilterChips (L611~L757) 1:1.
 *
 * <p>v1.x scope: kinds / modified / starred / shared 4 섹션. owner 섹션은 backend list response 에
 * `owner.id` 부재로 본 PR 에서 제외 (디자인 zip 의 4 owner 옵션 중 'shared' 만 별도 'shared' 토글로
 * 분리, 나머지는 follow-up 트랙에서 backend 확장 후 추가).
 */

export type FileKindId =
  | 'folder'
  | 'doc'
  | 'pdf'
  | 'sheet'
  | 'slides'
  | 'image'
  | 'video'
  | 'figma'
  | 'code'
  | 'archive'

export type FileModifiedId = 'any' | 'today' | '7d' | '30d' | '90d'

export interface FileFilters {
  kinds: FileKindId[]
  modified: FileModifiedId
  starred: boolean
  shared: boolean
}

export const DEFAULT_FILE_FILTERS: FileFilters = {
  kinds: [],
  modified: 'any',
  starred: false,
  shared: false,
}

export const FILTER_KIND_OPTIONS: { id: FileKindId; label: string }[] = [
  { id: 'folder', label: '폴더' },
  { id: 'doc', label: '문서' },
  { id: 'pdf', label: 'PDF' },
  { id: 'sheet', label: '스프레드시트' },
  { id: 'slides', label: '프레젠테이션' },
  { id: 'image', label: '이미지' },
  { id: 'video', label: '비디오' },
  { id: 'figma', label: '디자인' },
  { id: 'code', label: '코드' },
  { id: 'archive', label: '압축' },
]

export const FILTER_MODIFIED_OPTIONS: { id: FileModifiedId; label: string }[] = [
  { id: 'any', label: '전체 기간' },
  { id: 'today', label: '오늘' },
  { id: '7d', label: '최근 7일' },
  { id: '30d', label: '최근 30일' },
  { id: '90d', label: '최근 90일' },
]
