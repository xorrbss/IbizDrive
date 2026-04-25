import { TrashTable } from '@/components/files/TrashTable'

export default function TrashPage() {
  return (
    <div className="flex flex-col flex-1 min-h-0 overflow-hidden">
      <header className="flex items-center gap-3 px-4 py-2.5 border-b border-border">
        <h1 className="text-[14px] font-semibold tracking-tight text-fg">휴지통</h1>
        <span className="text-[11.5px] text-fg-muted">
          삭제한 항목이 30일간 보관된 후 영구 삭제됩니다.
        </span>
      </header>
      <TrashTable />
    </div>
  )
}
