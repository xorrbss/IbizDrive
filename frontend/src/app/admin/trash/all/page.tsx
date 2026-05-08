'use client'
import { useState } from 'react'
import {
  useAdminTrashList,
  useAdminRestoreTrashItem,
  useAdminPurgeTrashItem,
  useAdminBulkTrash,
  type AdminTrashTarget,
} from '@/hooks/useAdminTrash'
import type {
  AdminTrashBulkItem,
  AdminTrashBulkResponse,
  AdminTrashFilters,
  AdminTrashItem,
} from '@/types/trash'
import { AdminTrashBulkActionBar } from '@/components/admin/AdminTrashBulkActionBar'
import { formatBytes } from '@/lib/formatBytes'

/**
 * Wave 2 T9 — `/admin/trash/all` (admin-global-trash, spec §5.2, plan §P6.1).
 *
 * <p>FilterBar(q / type / ownerId / 삭제일 범위) + Table(9 cols + 선택 col) + cursor
 * 페이지네이션 + 단건 [복원]/[영구 삭제] + 다중 선택 [일괄 복원]/[일괄 영구삭제].
 *
 * <p>spec §4.5 "필터 변경 시 cursor=null 리셋" + 본 트랙 §3.6.1 "필터/페이지 전환 시 선택
 * 초기화" — `updateFilter` / cursor setter가 모두 보장. 입력 즉시 query (debounce 미도입).
 *
 * <p>권한 가드는 backend `@PreAuthorize("hasRole('ADMIN')")`가 진실. 페이지 자체는
 * 클라이언트 측 권한 체크 안 함 — 401/403 시 hook의 retry false로 즉시 에러 노출.
 *
 * <p>선택 모델은 `Set<string>` (key=`${type}:${id}`) — 본 트랙 한 페이지(cursor 결과) 한정.
 * cursor 다음 페이지로 이동 시 의도치 않은 영구삭제 방지를 위해 자동 누적하지 않는다.
 */
export default function AdminTrashAllPage() {
  const [filters, setFilters] = useState<AdminTrashFilters>({
    q: '',
    type: null,
    ownerId: null,
    deletedFrom: null,
    deletedTo: null,
  })
  const [cursor, setCursor] = useState<string | null>(null)
  const [confirmTarget, setConfirmTarget] = useState<AdminTrashTarget | null>(null)
  const [bulkConfirmOpen, setBulkConfirmOpen] = useState(false)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [bulkResult, setBulkResult] = useState<AdminTrashBulkResponse | null>(null)
  const [bulkResultDetailsOpen, setBulkResultDetailsOpen] = useState(false)

  const list = useAdminTrashList(filters, cursor)
  const restore = useAdminRestoreTrashItem()
  const purge = useAdminPurgeTrashItem()
  const bulk = useAdminBulkTrash()

  function clearSelection() {
    setSelected(new Set())
  }

  function updateFilter<K extends keyof AdminTrashFilters>(
    key: K,
    value: AdminTrashFilters[K],
  ) {
    setFilters((prev) => ({ ...prev, [key]: value }))
    setCursor(null)
    clearSelection()
  }

  function goNextPage(next: string) {
    setCursor(next)
    clearSelection()
  }

  function rowKey(it: AdminTrashItem) {
    return `${it.type}:${it.id}`
  }

  function toggleRow(it: AdminTrashItem) {
    setSelected((prev) => {
      const next = new Set(prev)
      const key = rowKey(it)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  function toggleSelectAll(items: AdminTrashItem[]) {
    setSelected((prev) => {
      const allKeys = items.map(rowKey)
      const allSelected = allKeys.every((k) => prev.has(k))
      if (allSelected) return new Set()
      return new Set(allKeys)
    })
  }

  function onRestoreSingle(item: AdminTrashItem) {
    restore.mutate({ id: item.id, type: item.type })
  }

  function onConfirmPurge() {
    if (!confirmTarget) return
    purge.mutate(confirmTarget)
    setConfirmTarget(null)
  }

  function buildBulkItems(): AdminTrashBulkItem[] {
    return Array.from(selected).map((key) => {
      const [type, id] = key.split(':') as ['file' | 'folder', string]
      return { type, id }
    })
  }

  function runBulk(action: 'restore' | 'purge') {
    const items = buildBulkItems()
    if (items.length === 0) return
    setBulkResult(null)
    setBulkResultDetailsOpen(false)
    bulk.mutate(
      { action, items },
      {
        onSuccess: (res) => {
          setBulkResult(res)
          clearSelection()
        },
      },
    )
  }

  function onBulkConfirmPurge() {
    setBulkConfirmOpen(false)
    runBulk('purge')
  }

  const items = list.data?.items ?? []
  const allSelected = items.length > 0 && items.every((it) => selected.has(rowKey(it)))

  return (
    <div className="flex-1 overflow-auto p-6 space-y-4">
      <header>
        <h1 className="text-lg font-semibold">전역 휴지통</h1>
        <p className="text-[12px] text-fg-2 mt-1">
          시스템의 모든 사용자의 휴지통 항목을 검색·복원·영구 삭제합니다.
        </p>
      </header>

      <div className="flex gap-2 items-center text-[13px]">
        <input
          aria-label="이름 검색"
          placeholder="이름 검색"
          value={filters.q}
          onChange={(e) => updateFilter('q', e.target.value)}
          className="px-2 py-1 border border-border rounded bg-bg"
        />
        <select
          aria-label="타입"
          value={filters.type ?? ''}
          onChange={(e) =>
            updateFilter('type', (e.target.value || null) as AdminTrashFilters['type'])
          }
          className="px-2 py-1 border border-border rounded bg-bg"
        >
          <option value="">전체</option>
          <option value="file">파일</option>
          <option value="folder">폴더</option>
        </select>
        <input
          aria-label="소유자 ID"
          placeholder="소유자 UUID"
          value={filters.ownerId ?? ''}
          onChange={(e) => updateFilter('ownerId', e.target.value || null)}
          className="px-2 py-1 border border-border rounded bg-bg w-[280px]"
        />
        <label className="flex items-center gap-1 text-fg-2">
          <span>삭제일</span>
          <input
            type="date"
            aria-label="삭제일 시작"
            value={filters.deletedFrom ?? ''}
            onChange={(e) => updateFilter('deletedFrom', e.target.value || null)}
            className="px-2 py-1 border border-border rounded bg-bg"
          />
          <span aria-hidden="true">~</span>
          <input
            type="date"
            aria-label="삭제일 종료"
            value={filters.deletedTo ?? ''}
            onChange={(e) => updateFilter('deletedTo', e.target.value || null)}
            className="px-2 py-1 border border-border rounded bg-bg"
          />
        </label>
      </div>

      {selected.size > 0 && (
        <AdminTrashBulkActionBar
          selectedCount={selected.size}
          onRestore={() => runBulk('restore')}
          onPurgeRequest={() => setBulkConfirmOpen(true)}
          onClear={clearSelection}
          disabled={bulk.isPending}
        />
      )}

      {bulkResult && (
        <BulkResultBanner
          result={bulkResult}
          detailsOpen={bulkResultDetailsOpen}
          onToggleDetails={() => setBulkResultDetailsOpen((v) => !v)}
          onDismiss={() => setBulkResult(null)}
        />
      )}

      {list.isLoading && <p className="text-sm text-fg-2">불러오는 중…</p>}
      {list.isError && (
        <p role="alert" className="text-sm text-red-600">
          목록을 불러오지 못했습니다.
        </p>
      )}

      {list.data && items.length === 0 && (
        <div className="text-fg-muted py-8 text-center text-[13px]">
          휴지통이 비어 있습니다
        </div>
      )}

      {list.data && items.length > 0 && (
        <div className="overflow-x-auto rounded-md border border-border">
          <table className="w-full text-sm">
            <thead className="bg-surface-1 text-fg-2">
              <tr>
                <th className="px-3 py-2 w-8">
                  <input
                    type="checkbox"
                    aria-label="전체 선택"
                    checked={allSelected}
                    onChange={() => toggleSelectAll(items)}
                  />
                </th>
                <th className="text-left px-3 py-2">이름</th>
                <th className="text-left px-3 py-2">타입</th>
                <th className="text-left px-3 py-2">소유자</th>
                <th className="text-left px-3 py-2">원위치</th>
                <th className="text-left px-3 py-2">크기</th>
                <th className="text-left px-3 py-2">삭제자</th>
                <th className="text-left px-3 py-2">삭제일</th>
                <th className="text-left px-3 py-2">영구삭제 예정</th>
                <th className="text-left px-3 py-2">작업</th>
              </tr>
            </thead>
            <tbody>
              {items.map((it) => (
                <TrashRow
                  key={rowKey(it)}
                  item={it}
                  selected={selected.has(rowKey(it))}
                  onToggle={() => toggleRow(it)}
                  onRestore={onRestoreSingle}
                  onPurgeRequest={(target) => setConfirmTarget(target)}
                  restorePending={restore.isPending}
                  purgePending={purge.isPending}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}

      {list.data?.nextCursor && (
        <div>
          <button
            type="button"
            onClick={() => goNextPage(list.data!.nextCursor!)}
            className="px-3 py-1 border border-border rounded text-[13px]"
          >
            다음 페이지
          </button>
        </div>
      )}

      {confirmTarget && (
        <ConfirmPurgeDialog
          message="이 동작은 되돌릴 수 없습니다."
          onCancel={() => setConfirmTarget(null)}
          onConfirm={onConfirmPurge}
        />
      )}

      {bulkConfirmOpen && (
        <ConfirmPurgeDialog
          message={`이 동작은 되돌릴 수 없습니다. 선택한 ${selected.size}개를 영구 삭제하시겠습니까?`}
          onCancel={() => setBulkConfirmOpen(false)}
          onConfirm={onBulkConfirmPurge}
        />
      )}
    </div>
  )
}

// ── Row ────────────────────────────────────────────────────────────────────

function TrashRow({
  item,
  selected,
  onToggle,
  onRestore,
  onPurgeRequest,
  restorePending,
  purgePending,
}: {
  item: AdminTrashItem
  selected: boolean
  onToggle: () => void
  onRestore: (item: AdminTrashItem) => void
  onPurgeRequest: (target: AdminTrashTarget) => void
  restorePending: boolean
  purgePending: boolean
}) {
  return (
    <tr className="border-t border-border">
      <td className="px-3 py-2">
        <input
          type="checkbox"
          aria-label={`${item.name} 선택`}
          checked={selected}
          onChange={onToggle}
        />
      </td>
      <td className="px-3 py-2">{item.name}</td>
      <td className="px-3 py-2">{item.type === 'file' ? '파일' : '폴더'}</td>
      <td className="px-3 py-2">{item.ownerEmail}</td>
      <td
        className="px-3 py-2 max-w-[320px] truncate"
        title={item.originalParentPath ?? undefined}
      >
        {item.originalParentPath ?? item.originalParentName ?? (
          <span className="text-fg-muted">(루트)</span>
        )}
      </td>
      <td className="px-3 py-2 tabular-nums">
        {item.sizeBytes != null ? formatBytes(item.sizeBytes) : '-'}
      </td>
      <td className="px-3 py-2">
        {item.deletedByEmail ?? <span className="text-fg-muted">—</span>}
      </td>
      <td className="px-3 py-2 text-fg-2">{formatDate(item.deletedAt)}</td>
      <td className="px-3 py-2 text-fg-2">{formatDate(item.purgeAfter)}</td>
      <td className="px-3 py-2">
        <div className="flex gap-2">
          <button
            type="button"
            disabled={restorePending}
            onClick={() => onRestore(item)}
            className="px-2 py-0.5 border border-border rounded disabled:opacity-50"
          >
            복원
          </button>
          <button
            type="button"
            disabled={purgePending}
            onClick={() => onPurgeRequest({ id: item.id, type: item.type })}
            className="px-2 py-0.5 border border-border rounded text-red-600 disabled:opacity-50"
          >
            영구 삭제
          </button>
        </div>
      </td>
    </tr>
  )
}

function formatDate(iso: string): string {
  // Locale 비종속 ISO 표시 — 시각 추적 보조용 (admin tool 한정).
  return iso.replace('T', ' ').replace(/\..*$/, '')
}

// ── BulkResult ────────────────────────────────────────────────────────────

function BulkResultBanner({
  result,
  detailsOpen,
  onToggleDetails,
  onDismiss,
}: {
  result: AdminTrashBulkResponse
  detailsOpen: boolean
  onToggleDetails: () => void
  onDismiss: () => void
}) {
  const sN = result.succeeded.length
  const fN = result.failed.length
  return (
    <div
      role="status"
      aria-live="polite"
      data-testid="admin-trash-bulk-result"
      className="flex items-start gap-2 text-[13px] bg-surface-1 border border-border rounded px-3 py-2"
    >
      <div className="flex-1">
        <span>{`성공 ${sN}개, 실패 ${fN}개`}</span>
        {fN > 0 && (
          <button
            type="button"
            onClick={onToggleDetails}
            className="ml-2 text-fg-2 hover:text-fg underline"
          >
            {detailsOpen ? '접기' : '자세히'}
          </button>
        )}
        {detailsOpen && fN > 0 && (
          <ul className="mt-2 space-y-0.5 text-fg-2 text-[12px]">
            {result.failed.map((f) => (
              <li key={`${f.type}:${f.id}`}>
                {f.type === 'file' ? '파일' : '폴더'} {f.id} — {f.error}
              </li>
            ))}
          </ul>
        )}
      </div>
      <button
        type="button"
        onClick={onDismiss}
        aria-label="결과 닫기"
        className="text-fg-2 hover:text-fg"
      >
        ✕
      </button>
    </div>
  )
}

// ── ConfirmDialog ──────────────────────────────────────────────────────────
// 재사용 가능한 ConfirmDialog 컴포넌트 미존재 (RenameDialog/MoveFolderDialog 등은 모두
// 도메인별 전용 상태 store에 종속). KISS — purge(단건/일괄)만 쓰는 인라인 modal로 유지.
// 본 트랙에서 message prop을 추가해 단건/일괄 모두 동일 컴포넌트 재사용.

function ConfirmPurgeDialog({
  message,
  onCancel,
  onConfirm,
}: {
  message: string
  onCancel: () => void
  onConfirm: () => void
}) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="purge-confirm-title"
      tabIndex={-1}
      onKeyDown={(e) => {
        if (e.key === 'Escape') onCancel()
      }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
    >
      <div className="bg-surface-1 border border-border rounded-md w-[360px] flex flex-col p-4 gap-3 shadow-2xl">
        <h2 id="purge-confirm-title" className="text-[14px] font-semibold text-fg">
          영구 삭제하시겠습니까?
        </h2>
        <p className="text-[12.5px] text-fg-muted">{message}</p>
        <div className="flex justify-end gap-2 mt-1">
          <button
            type="button"
            onClick={onCancel}
            className="h-8 px-3 rounded text-fg-2 text-[12.5px] hover:bg-surface-2"
          >
            취소
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="h-8 px-3 rounded bg-red-600 text-white text-[12.5px] font-medium hover:opacity-90"
          >
            영구 삭제
          </button>
        </div>
      </div>
    </div>
  )
}
