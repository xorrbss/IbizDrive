'use client'
import { useTrashList } from '@/hooks/useTrashList'
import { findFolderPath } from '@/lib/folderTreeUtils'
import { TrashRowActions } from './TrashRowActions'
import type { TrashItem } from '@/types/trash'
import type { FolderNode } from '@/types/folder'

// TODO: [BLOCKED]
//   violated: 기존 구조 우선
//   reason: useFolderTree (flat tree) 제거됨. Plan B lazy per-workspace tree (Tasks 17+) 미구현.
//   required_change: Tasks 17+ 구현 후 per-workspace tree로 원위치 path 표시 복원.
//   현재: tree=undefined → originalParentId가 있어도 "원위치 폴더 삭제됨" 폴백 (안전 degradation).

/**
 * 휴지통 테이블 (M9.3). 단순 list (MVP는 가상화 없음 — 휴지통은 일반적으로 ≤ 수백건).
 * docs/01 §11 (4상태) + §12 (aria-rowcount/rowindex) + §13 UX.
 *
 * 컬럼: 이름 / 타입 / 원위치 / 삭제 시각 / 영구 삭제 예정 / 행 액션
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
  const tree: FolderNode | undefined = undefined // Tasks 17+: per-workspace lazy tree

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
          let originalPath = '최상위'
          if (it.originalParentId) {
            const path = tree ? findFolderPath(tree, it.originalParentId) : null
            if (path) {
              originalPath = path.map((n) => (n.id === 'root' ? '내 드라이브' : n.name)).join(' / ')
            } else {
              originalPath = '원위치 폴더 삭제됨'
            }
          }
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
