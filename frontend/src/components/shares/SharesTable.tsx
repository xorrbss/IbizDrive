'use client'
import { useSharesWithMe } from '@/hooks/useSharesWithMe'
import type { ShareDto, SharePreset } from '@/types/share'

/**
 * 받은 공유 테이블 (F4 → F5.1 → A13, docs/01 §17). 단순 list (MVP는 가상화 없음).
 * docs/01 §11 (4상태) + §12 (aria-rowcount/rowindex) mirror.
 *
 * A13 변경 — backend `ShareDto`에 `preset` 필드가 permissions join을 통해 복원됨:
 * - 컬럼 3 → 4: 항목 / 공유한 사람 / 권한 / 만료. preset 한국어 라벨로 표기.
 *
 * F5.1에서 굳어진 정책 (유지):
 * - 항목 식별: file 공유 row면 fileId, folder 공유 row면 folderId 표시 (XOR). raw UUID 노출.
 *   (file/folder 이름 join은 별도 backend 트랙.)
 * - 받은 공유는 backend `canRevoke` 정책상 revoke 불가 — 액션 컬럼 미노출.
 */
const GRID_COLS = 'grid grid-cols-[1fr_160px_100px_180px] gap-3 items-center px-4'

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
        <span role="columnheader">권한</span>
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
              className={`${GRID_COLS} h-[var(--row-h)] border-b border-border text-[13px] hover:bg-surface-2`}
            >
              <span className="truncate" role="gridcell">
                <span aria-hidden className="mr-1.5">{isFolder ? '📁' : '📄'}</span>
                {itemId}
              </span>
              <span role="gridcell" className="truncate text-fg-muted">
                {it.sharedBy}
              </span>
              <span role="gridcell" className="text-fg-muted">
                {presetLabel(it.preset)}
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

function presetLabel(p: SharePreset): string {
  switch (p) {
    case 'read':
      return '읽기'
    case 'upload':
      return '업로드'
    case 'edit':
      return '편집'
    case 'admin':
      return '관리'
  }
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
