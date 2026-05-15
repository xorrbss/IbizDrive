'use client'
import { Upload } from 'lucide-react'

type Props = {
  visible: boolean
  /** 현재 폴더 이름. 미지정 시 "현재 폴더" fallback (design zip `${folderName}에 파일이 추가됩니다`). */
  folderName?: string
}

/**
 * OS 드래그 시 FileTable 위에 겹치는 드롭존 오버레이.
 *
 * 디자인 (`prototype/styles.css` `.drop-overlay` + `panels.jsx` `DropOverlay`):
 *   - 컨테이너: inset 2 (= 8px margin) + `accent 8%` 배경 + `blur(2px)` + 2px dashed accent + radius 10
 *   - 중앙 카드: surface-1 + 28px padding + lg shadow + 56px round 아이콘 영역(`accent-soft` bg)
 *   - 타이틀 16px/600, 서브 12.5px/fg-muted (`${folderName}에 파일이 추가됩니다`)
 *   - pointer-events: none (드롭 자체는 window 네이티브 핸들러가 처리)
 */
export function UploadOverlay({ visible, folderName }: Props) {
  if (!visible) return null
  const label = folderName ?? '현재 폴더'
  return (
    <div
      role="presentation"
      className="absolute inset-2 z-30 flex items-center justify-center pointer-events-none bg-[color-mix(in_oklch,var(--accent)_8%,transparent)] backdrop-blur-[2px] border-2 border-dashed border-accent rounded-[10px]"
      aria-hidden
    >
      <div className="bg-surface-1 px-9 py-7 rounded-xl shadow-lg text-center">
        <div className="w-14 h-14 rounded-full bg-accent-soft text-accent inline-flex items-center justify-center mb-2.5">
          <Upload size={28} aria-hidden />
        </div>
        <div className="text-[16px] font-semibold text-fg">여기에 놓아서 업로드</div>
        <div className="text-[12.5px] text-fg-muted mt-1">
          <span className="font-medium text-fg-2">{label}</span>에 파일이 추가됩니다
        </div>
      </div>
    </div>
  )
}
