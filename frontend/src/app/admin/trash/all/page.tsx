'use client'
import { useState } from 'react'
import {
  useAdminTrashList,
  useAdminRestoreTrashItem,
  useAdminPurgeTrashItem,
  type AdminTrashTarget,
} from '@/hooks/useAdminTrash'
import type { AdminTrashFilters, AdminTrashItem } from '@/types/trash'

/**
 * Wave 2 T9 — `/admin/trash/all` (admin-global-trash, spec §5.2, plan §P6.1).
 *
 * <p>FilterBar(q / type / ownerId) + Table(8 cols) + cursor 페이지네이션 +
 * 단건 [복원] / [영구 삭제]. 복원은 즉시 mutate, 영구 삭제만 ConfirmDialog 거침.
 *
 * <p>spec §4.5 "필터 변경 시 cursor=null 리셋" — `updateFilter`가 cursor 초기화 보장.
 * 입력 즉시 query (debounce 미도입). KISS — keepPreviousData가 깜박임 흡수.
 *
 * <p>권한 가드는 backend `@PreAuthorize("hasRole('ADMIN')")`가 진실 (docs/03 §3 + 핵심
 * 원칙 #10). 페이지 자체는 클라이언트 측 권한 체크 안 함 — 401/403 시 hook의 retry false로
 * 즉시 에러 노출.
 */
export default function AdminTrashAllPage() {
  const [filters, setFilters] = useState<AdminTrashFilters>({
    q: '',
    type: null,
    ownerId: null,
  })
  const [cursor, setCursor] = useState<string | null>(null)
  const [confirmTarget, setConfirmTarget] = useState<AdminTrashTarget | null>(null)

  const list = useAdminTrashList(filters, cursor)
  const restore = useAdminRestoreTrashItem()
  const purge = useAdminPurgeTrashItem()

  function updateFilter<K extends keyof AdminTrashFilters>(
    key: K,
    value: AdminTrashFilters[K],
  ) {
    setFilters((prev) => ({ ...prev, [key]: value }))
    setCursor(null)
  }

  function onRestore(item: AdminTrashItem) {
    restore.mutate({ id: item.id, type: item.type })
  }

  function onConfirmPurge() {
    if (!confirmTarget) return
    purge.mutate(confirmTarget)
    setConfirmTarget(null)
  }

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
      </div>

      {list.isLoading && <p className="text-sm text-fg-2">불러오는 중…</p>}
      {list.isError && (
        <p role="alert" className="text-sm text-red-600">
          목록을 불러오지 못했습니다.
        </p>
      )}

      {list.data && list.data.items.length === 0 && (
        <div className="text-fg-muted py-8 text-center text-[13px]">
          휴지통이 비어 있습니다
        </div>
      )}

      {list.data && list.data.items.length > 0 && (
        <div className="overflow-x-auto rounded-md border border-border">
          <table className="w-full text-sm">
            <thead className="bg-surface-1 text-fg-2">
              <tr>
                <th className="text-left px-3 py-2">이름</th>
                <th className="text-left px-3 py-2">타입</th>
                <th className="text-left px-3 py-2">소유자</th>
                <th className="text-left px-3 py-2">원위치</th>
                <th className="text-left px-3 py-2">크기</th>
                <th className="text-left px-3 py-2">삭제일</th>
                <th className="text-left px-3 py-2">영구삭제 예정</th>
                <th className="text-left px-3 py-2">작업</th>
              </tr>
            </thead>
            <tbody>
              {list.data.items.map((it) => (
                <TrashRow
                  key={`${it.type}:${it.id}`}
                  item={it}
                  onRestore={onRestore}
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
            onClick={() => setCursor(list.data!.nextCursor)}
            className="px-3 py-1 border border-border rounded text-[13px]"
          >
            다음 페이지
          </button>
        </div>
      )}

      {confirmTarget && (
        <ConfirmPurgeDialog
          onCancel={() => setConfirmTarget(null)}
          onConfirm={onConfirmPurge}
        />
      )}
    </div>
  )
}

// ── Row ────────────────────────────────────────────────────────────────────

function TrashRow({
  item,
  onRestore,
  onPurgeRequest,
  restorePending,
  purgePending,
}: {
  item: AdminTrashItem
  onRestore: (item: AdminTrashItem) => void
  onPurgeRequest: (target: AdminTrashTarget) => void
  restorePending: boolean
  purgePending: boolean
}) {
  return (
    <tr className="border-t border-border">
      <td className="px-3 py-2">{item.name}</td>
      <td className="px-3 py-2">{item.type === 'file' ? '파일' : '폴더'}</td>
      <td className="px-3 py-2">{item.ownerEmail}</td>
      <td className="px-3 py-2">
        {item.originalParentName ?? <span className="text-fg-muted">(루트)</span>}
      </td>
      <td className="px-3 py-2">
        {item.sizeBytes != null ? `${item.sizeBytes} B` : '-'}
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

// ── ConfirmDialog ──────────────────────────────────────────────────────────
// 재사용 가능한 ConfirmDialog 컴포넌트 미존재 (RenameDialog/MoveFolderDialog 등은 모두
// 도메인별 전용 상태 store에 종속). KISS — purge 1 개소만 쓰는 인라인 modal로 유지.

function ConfirmPurgeDialog({
  onCancel,
  onConfirm,
}: {
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
        <p className="text-[12.5px] text-fg-muted">
          이 동작은 되돌릴 수 없습니다.
        </p>
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
