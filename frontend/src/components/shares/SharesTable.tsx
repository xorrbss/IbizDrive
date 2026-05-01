'use client'
import { useSharesWithMe } from '@/hooks/useSharesWithMe'
import type { ShareDto } from '@/types/share'

/**
 * 받은 공유 테이블 (F4 → F5.1, docs/01 §17). 단순 list (MVP는 가상화 없음).
 * docs/01 §11 (4상태) + §12 (aria-rowcount/rowindex) mirror.
 *
 * F5.1 변경 — backend `ShareDto` record 정합:
 * - preset 컬럼 제거 (record에 없음 → wire drift). 4컬럼 → 3컬럼.
 * - 항목 식별: file 공유 row면 fileId, folder 공유 row면 folderId 표시 (XOR).
 *   (fileId/folderId는 raw UUID — F5.3에서 join을 위한 backend 별도 트랙. 현재는 식별자 그대로 노출.)
 *
 * 컬럼: 항목(file/folder) / 공유한 사람 / 만료
 * 받은 공유는 backend `canRevoke` 정책상 revoke 불가 — 액션 컬럼 미노출 (보수 정책).
 */
const GRID_COLS = 'grid grid-cols-[1fr_180px_180px] gap-3 items-center px-4'

export function SharesTable() {
  const query = useSharesWithMe()

  if (query.isLoading) {
    return (
      <div role="status" aria-live="polite" className="p-6 text-[13px] text-fg-muted">
        로딩…
      </div>
    )
  }
  if (query.isError) {
    return (
      <div role="alert" className="p-6 text-[13px] text-danger">
        받은 공유를 불러올 수 없습니다.
      </div>
    )
  }
  const items: ShareDto[] = query.data?.pages.flatMap((p) => p.items) ?? []
  if (items.length === 0) {
    return (
      <div className="p-6 text-[13px] text-fg-muted" role="status">
        받은 공유가 없습니다
      </div>
    )
  }

  return (
    <div
      role="grid"
      aria-rowcount={items.length + 1}
      aria-label="받은 공유 항목"
      className="flex flex-col flex-1 min-h-0 overflow-hidden"
    >
      <div
        role="row"
        aria-rowindex={1}
        className={`${GRID_COLS} h-[30px] bg-surface-1 border-y border-border text-[11px] uppercase tracking-[0.04em] font-medium text-fg-muted`}
      >
        <span role="columnheader">항목</span>
        <span role="columnheader">공유한 사람</span>
        <span role="columnheader">만료</span>
      </div>

      <div className="flex-1 overflow-auto">
        {items.map((it, idx) => {
          const isFolder = it.folderId !== null
          const itemId = it.fileId ?? it.folderId ?? ''
          return (
            <div
              key={it.id}
              role="row"
              aria-rowindex={idx + 2}
              data-share-id={it.id}
              className={`${GRID_COLS} h-[40px] border-b border-border text-[13px] hover:bg-surface-2`}
            >
              <span className="truncate" role="gridcell">
                <span aria-hidden className="mr-1.5">{isFolder ? '📁' : '📄'}</span>
                {itemId}
              </span>
              <span role="gridcell" className="truncate text-fg-muted">
                {it.sharedBy}
              </span>
              <span role="gridcell" className="text-fg-muted">
                {it.expiresAt ? formatDate(it.expiresAt) : '없음'}
              </span>
            </div>
          )
        })}
      </div>

      {query.hasNextPage && (
        <div className="p-3 text-center">
          <button
            type="button"
            onClick={() => query.fetchNextPage()}
            disabled={query.isFetchingNextPage}
            className="px-3 py-1.5 text-[12px] rounded-sm border border-border bg-surface-1 hover:bg-surface-2 disabled:opacity-50"
          >
            {query.isFetchingNextPage ? '불러오는 중…' : '더 보기'}
          </button>
        </div>
      )}
    </div>
  )
}

function formatDate(iso: string): string {
  try {
    const d = new Date(iso)
    if (Number.isNaN(d.getTime())) return iso
    return d.toLocaleString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return iso
  }
}
