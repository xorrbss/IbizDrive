import { TrashTable } from '@/components/trash/TrashTable'

export default function TrashPage() {
  return (
    <section
      aria-label="휴지통"
      className="flex-1 min-w-0 overflow-y-auto bg-bg"
    >
      <header className="sticky top-0 z-10 px-4 py-3 border-b border-border bg-surface-1">
        <h1 className="text-[14px] font-semibold text-fg">휴지통</h1>
        <p className="text-[12px] text-fg-muted mt-0.5">
          삭제된 항목은 복원하거나 영구 삭제할 수 있습니다.
        </p>
      </header>
      <TrashTable />
    </section>
  )
}
