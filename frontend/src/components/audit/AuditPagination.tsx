'use client'

interface Props {
  page: number
  pageSize: number
  total: number
  onPageChange: (page: number) => void
}

export function AuditPagination({ page, pageSize, total, onPageChange }: Props) {
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const start = total === 0 ? 0 : (page - 1) * pageSize + 1
  const end = Math.min(page * pageSize, total)

  return (
    <nav
      aria-label="감사 로그 페이지"
      className="flex items-center justify-between px-4 py-2 border-t border-border bg-surface-1 text-[12.5px]"
    >
      <span className="text-fg-muted">
        {total === 0 ? '0건' : `${start}–${end} / ${total}건`}
      </span>
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => onPageChange(Math.max(1, page - 1))}
          disabled={page <= 1}
          className="h-7 px-2.5 rounded text-fg-2 hover:bg-surface-2 disabled:opacity-40 disabled:cursor-not-allowed"
        >
          이전
        </button>
        <span aria-current="page" className="text-fg">
          {page} / {totalPages}
        </span>
        <button
          type="button"
          onClick={() => onPageChange(Math.min(totalPages, page + 1))}
          disabled={page >= totalPages}
          className="h-7 px-2.5 rounded text-fg-2 hover:bg-surface-2 disabled:opacity-40 disabled:cursor-not-allowed"
        >
          다음
        </button>
      </div>
    </nav>
  )
}
