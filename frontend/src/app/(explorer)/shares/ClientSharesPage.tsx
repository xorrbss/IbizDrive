'use client'
import { SharesTable } from '@/components/shares/SharesTable'

/**
 * /shares 페이지 (F4, docs/01 §17). 받은 공유 목록.
 * 4상태 분기는 SharesTable 내부에서 처리 — 본 컴포넌트는 헤더 + 컨테이너만.
 * /trash 패턴 mirror.
 */
export function ClientSharesPage() {
  return (
    <div className="flex-1 min-w-0 flex flex-col bg-bg">
      <div className="flex items-center px-4 h-[44px] border-b border-border">
        <h1 className="text-[14px] font-semibold tracking-tight">받은 공유</h1>
      </div>
      <SharesTable />
    </div>
  )
}
