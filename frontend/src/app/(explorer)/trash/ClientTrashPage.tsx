'use client'
import { TrashTable } from '@/components/trash/TrashTable'

/**
 * /trash 페이지 (M9.3, docs/01 §13).
 * 4상태 분기는 TrashTable 내부에서 처리 — 본 컴포넌트는 헤더 + 컨테이너만.
 */
export function ClientTrashPage() {
  return (
    <div className="flex-1 min-w-0 flex flex-col bg-bg">
      <div className="flex items-center px-4 h-[44px] border-b border-border">
        <h1 className="text-[14px] font-semibold tracking-tight">휴지통</h1>
      </div>
      <TrashTable />
    </div>
  )
}
