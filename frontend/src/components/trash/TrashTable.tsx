'use client'
import { useTrashList } from '@/hooks/useTrashList'
import { TrashRowActions } from './TrashRowActions'
import type { TrashItem } from '@/types/trash'

/**
 * 휴지통 테이블 (M9.3). 단순 list (MVP는 가상화 없음 — 휴지통은 일반적으로 ≤ 수백건).
 * docs/01 §11 (4상태) + §12 (aria-rowcount/rowindex) + §13 UX.
 *
 * 컬럼: 이름 / 타입 / 원위치 / 삭제 시각 / 영구 삭제 예정 / 행 액션
 *
 * <p>원위치 path: backend `TrashItemDto.originalParentPath` (페이지 단위 recursive CTE batch 계산,
 * admin trash와 동일 source). `originalParentId == null`이면 "최상위", path가 null이면 "원위치 미상"
 * (데이터 corruption 또는 chain 종착 실패) — flat folderTree 의존 제거 (2026-05-11).
 */
const GRID_COLS =
  'grid grid-cols-[1fr_60px_180px_140px_140px_160px] gap-3 items-center px-4'

// scope props (scopeType/scopeId) 는 ClientWorkspaceTrashPage 가 라우트 파라미터에서 추출해 전달한다.
// archived prop (Plan E T13): archive된 team scope 일 때 행 액션의 복원 버튼을 비활성화한다.
export function TrashTable(props: {
  scopeType: 'department' | 'team'
  scopeId: string
  /** archive된 workspace 여부 — `TrashRowActions.disabled` 로 forward (Plan E T13). */
  archived?: boolean
}) {
  const { scopeType, scopeId, archived = false } = props
  const query = useTrashList({ scopeType, scopeId })

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
        휴지통을 불러올 수 없습니다.
      </div>
    )
  }
  const items: TrashItem[] = query.data?.pages.flatMap((p) => p.items) ?? []
  if (items.length === 0) {
    return (
      <div className="p-6 text-[13px] text-fg-muted" role="status">
        휴지통이 비어있습니다
      </div>
    )
  }

  return (
    <div
      role="grid"
      aria-rowcount={items.length + 1}
      aria-label="휴지통 항목"
      className="flex flex-col flex-1 min-h-0 overflow-hidden"
    >
      <div
        role="row"
        aria-rowindex={1}
        className={`${GRID_COLS} h-[30px] bg-surface-1 border-y border-border text-[11px] uppercase tracking-[0.04em] font-medium text-fg-muted`}
      >
        <span role="columnheader">이름</span>
        <span role="columnheader">타입</span>
        <span role="columnheader">원위치</span>
        <span role="columnheader">삭제 시각</span>
        <span role="columnheader">영구 삭제 예정</span>
        <span role="columnheader" className="text-right">
          액션
        </span>
      </div>

      <div className="flex-1 overflow-auto">
        {items.map((it, idx) => {
          // backend `originalParentPath` 직접 사용. id 만 있고 path 없으면 chain 종착 실패 → "원위치 미상".
          const originalPath = it.originalParentId === null
            ? '최상위'
            : (it.originalParentPath ?? '원위치 미상')
          return (
            <div
              key={`${it.type}:${it.id}`}
              role="row"
              aria-rowindex={idx + 2}
              data-trash-id={it.id}
              data-trash-type={it.type}
              className={`${GRID_COLS} h-[40px] border-b border-border text-[13px] hover:bg-surface-2`}
            >
              <span className="truncate" role="gridcell">
                <span aria-hidden className="mr-1.5">
                  {it.type === 'folder' ? '📁' : '📄'}
                </span>
                {it.name}
              </span>
              <span role="gridcell" className="text-fg-muted">
                {it.type === 'folder' ? '폴더' : '파일'}
              </span>
              <span role="gridcell" className="truncate text-fg-muted">
                {originalPath}
              </span>
              <span role="gridcell" className="text-fg-muted">
                {formatDate(it.deletedAt)}
              </span>
              <span role="gridcell" className="text-fg-muted">
                {formatDate(it.purgeAfter)}
              </span>
              <span role="gridcell">
                <TrashRowActions item={it} disabled={archived} />
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
