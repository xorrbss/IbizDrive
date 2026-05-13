'use client'
import { useState } from 'react'
import { useMe } from '@/hooks/useMe'
import { useAdminApprovals } from '@/hooks/useAdminApprovals'
import { AdminGuard } from '@/components/auth/AdminGuard'
import { AdminApprovalsTable } from '@/components/admin/AdminApprovalsTable'
import type { AdminApprovalActionType } from '@/types/admin-approval'

/**
 * /admin/approvals — dual-approval framework Phase 4 (ADR #47, docs/02 §2.11).
 *
 * <p>운영자가 다른 admin이 요청한 파괴적 액션을 secondary approver로서 승인/거부.
 * 본인이 요청한 row는 "내 요청" 라벨 + 취소 버튼만 노출 (self-approval 차단).
 *
 * <p>actionType 필터: 전체 / role_change / retention_change / trash_purge. 페이지네이션은
 * /admin/permissions와 동일 패턴 (prev/next + "page X / Y").
 *
 * <p>가드: ADMIN-only — backend `@PreAuthorize("hasRole('ADMIN')")` 가 진실의 출처.
 * default `<AdminGuard>` (wave1.5-auditor-admin-ui-access).
 */
export default function AdminApprovalsPage() {
  return (
    <AdminGuard>
      <div className="admin-grid">
        <header className="flex items-start justify-between gap-3">
          <div>
            <h1 className="text-lg font-semibold">2인 승인 대기</h1>
            <p className="text-[12px] text-fg-2 mt-1">
              역할 변경 · 보존 기간 변경 · 휴지통 영구 삭제는 본인이 아닌 다른 관리자의 승인이 필요합니다.
            </p>
          </div>
        </header>
        <ApprovalsView />
      </div>
    </AdminGuard>
  )
}

const PAGE_SIZE = 50

function ApprovalsView() {
  const { data: me } = useMe()
  const [actionType, setActionType] = useState<AdminApprovalActionType | ''>('')
  const [page, setPage] = useState(0)
  const result = useAdminApprovals({
    actionType: actionType || undefined,
    page,
    size: PAGE_SIZE,
  })
  const currentUserId = me?.user?.id ?? ''

  return (
    <section aria-labelledby="approvals-list-title">
      <h2 id="approvals-list-title" className="sr-only">
        승인 대기 목록
      </h2>

      <FilterBar
        actionType={actionType}
        onActionType={(v) => {
          setActionType(v)
          setPage(0)
        }}
      />

      {result.isLoading && <p className="text-sm text-fg-2">불러오는 중…</p>}
      {result.isError && (
        <p role="alert" className="text-sm text-red-600">
          승인 목록을 불러오지 못했습니다.
        </p>
      )}

      {result.data && (
        <>
          {result.data.content.length === 0 ? (
            <div className="rounded-md border border-border p-6 text-center text-fg-2">
              대기 중인 승인 요청이 없습니다.
            </div>
          ) : (
            <AdminApprovalsTable
              approvals={result.data.content}
              currentUserId={currentUserId}
            />
          )}

          <Pagination
            page={result.data.number}
            totalPages={Math.max(1, result.data.totalPages)}
            onChange={setPage}
          />
        </>
      )}
    </section>
  )
}

// ── FilterBar ─────────────────────────────────────────────────────────────

function FilterBar(props: {
  actionType: AdminApprovalActionType | ''
  onActionType: (v: AdminApprovalActionType | '') => void
}) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-3 mb-4">
      <label className="flex flex-col gap-1 text-sm">
        <span className="text-fg-2">작업 유형</span>
        <select
          value={props.actionType}
          onChange={(e) =>
            props.onActionType(e.target.value as AdminApprovalActionType | '')
          }
          className="px-2 py-2 rounded border border-border bg-bg"
          aria-label="작업 유형 필터"
        >
          <option value="">전체</option>
          <option value="role_change">역할 변경</option>
          <option value="retention_change">보존 기간 변경</option>
          <option value="trash_purge">휴지통 영구 삭제</option>
        </select>
      </label>
    </div>
  )
}

// ── Pagination ────────────────────────────────────────────────────────────

function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number
  totalPages: number
  onChange: (page: number) => void
}) {
  return (
    <nav
      aria-label="페이지 탐색"
      className="flex items-center justify-end gap-2 mt-3 text-sm"
    >
      <button
        type="button"
        disabled={page <= 0}
        onClick={() => onChange(Math.max(0, page - 1))}
        className="px-2 py-1 rounded border border-border disabled:opacity-50"
      >
        이전
      </button>
      <span className="text-fg-2">
        {page + 1} / {totalPages}
      </span>
      <button
        type="button"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
        className="px-2 py-1 rounded border border-border disabled:opacity-50"
      >
        다음
      </button>
    </nav>
  )
}
