import type { FileItem } from '@/types/file'
import type { FileFilters, FileKindId, FileModifiedId } from '@/types/fileFilters'

/**
 * mime/type → FileKindId 매핑. design-sweep-file-type-icons (PR #211) 의 `fileIconKind` 와 동등 정책.
 *
 * <p>folder 는 mime 무관 'folder'. file 은 mime prefix/접미 매칭 — 미매칭 시 'doc' (default) 가 아니라
 * null (필터 어디에도 속하지 않음 — kinds 필터 활성 시 제외).
 */
export function fileItemKind(it: FileItem): FileKindId | null {
  if (it.type === 'folder') return 'folder'
  const mime = (it.mimeType ?? '').toLowerCase()
  if (mime.startsWith('image/')) return 'image'
  if (mime.startsWith('video/')) return 'video'
  if (mime.startsWith('audio/')) return null
  if (mime === 'application/pdf') return 'pdf'
  if (
    mime.includes('spreadsheet') ||
    mime === 'application/vnd.ms-excel' ||
    mime === 'text/csv'
  ) return 'sheet'
  if (
    mime.includes('presentation') ||
    mime === 'application/vnd.ms-powerpoint'
  ) return 'slides'
  if (mime === 'application/zip' || mime === 'application/x-7z-compressed' ||
      mime === 'application/x-rar-compressed' || mime === 'application/x-tar' ||
      mime === 'application/gzip') return 'archive'
  if (mime.includes('figma')) return 'figma'
  if (
    mime === 'text/javascript' || mime === 'application/javascript' ||
    mime === 'application/json' || mime === 'text/x-python' ||
    mime === 'text/x-java' || mime === 'text/x-go'
  ) return 'code'
  if (
    mime.startsWith('text/') ||
    mime.includes('document') ||
    mime === 'application/msword' ||
    mime === 'application/rtf'
  ) return 'doc'
  return null
}

const MS_PER_DAY = 24 * 60 * 60 * 1000

function modifiedThreshold(modified: FileModifiedId, now = Date.now()): number | null {
  switch (modified) {
    case 'today': {
      // 오늘 자정 (local) 기준
      const d = new Date(now)
      d.setHours(0, 0, 0, 0)
      return d.getTime()
    }
    case '7d': return now - 7 * MS_PER_DAY
    case '30d': return now - 30 * MS_PER_DAY
    case '90d': return now - 90 * MS_PER_DAY
    case 'any':
    default:
      return null
  }
}

/**
 * client-side 필터 — useFilesInFolder 결과에 적용. kinds/modified/starred/shared 4 섹션 모두 AND.
 *
 * <p>v1.x: backend filter 트랙은 별도 (paginated 환경에서 client filter 가 의미를 갖는 범위는 작음).
 * folder 당 items 수가 작은 가정에서 충분.
 */
export function applyFileFilters(
  items: FileItem[] | undefined,
  filters: FileFilters,
  now = Date.now(),
): FileItem[] {
  if (!items) return []
  const threshold = modifiedThreshold(filters.modified, now)
  return items.filter((it) => {
    if (filters.kinds.length > 0) {
      const k = fileItemKind(it)
      if (!k || !filters.kinds.includes(k)) return false
    }
    if (filters.starred && !it.starred) return false
    if (filters.shared && !((it.shareCount ?? 0) > 0)) return false
    if (threshold !== null) {
      const t = Date.parse(it.updatedAt)
      if (Number.isNaN(t) || t < threshold) return false
    }
    return true
  })
}

export function isAnyFilterActive(filters: FileFilters): boolean {
  return (
    filters.kinds.length > 0 ||
    filters.modified !== 'any' ||
    filters.starred ||
    filters.shared
  )
}
