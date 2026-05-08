import type { TrashItemType } from '@/types/trash'

/**
 * RestoreConflictDialog 의 자동 제안 이름. MVP — " (1)" 한 번만 추가, 시퀀스 자동 증분은 v1.x.
 *
 * 파일은 마지막 `.` 이전(base) + " (1)" + 확장자로 분리. `.dotfile` (확장자 없음 / 시작이 dot)은
 * 전체를 base 로 취급. 폴더는 단순 ` (1)` 접미사.
 *
 * 예:
 * - `report.pdf` (file) → `report (1).pdf`
 * - `archive.tar.gz` (file) → `archive.tar (1).gz` (마지막 `.` 만 분리)
 * - `.dotfile` (file) → `.dotfile (1)`
 * - `Reports` (folder) → `Reports (1)`
 */
export function suggestRestoreName(originalName: string, type: TrashItemType): string {
  if (type !== 'file') {
    return `${originalName} (1)`
  }
  const lastDot = originalName.lastIndexOf('.')
  // 확장자 없음 (`Readme`) 또는 dotfile (`.gitignore` — 시작이 dot)
  if (lastDot <= 0) {
    return `${originalName} (1)`
  }
  const base = originalName.slice(0, lastDot)
  const ext = originalName.slice(lastDot) // includes leading dot
  return `${base} (1)${ext}`
}
