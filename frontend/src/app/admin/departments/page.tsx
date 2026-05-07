'use client'
import { useEffect, useState } from 'react'
import {
  useAdminCreateDepartment,
  useAdminDepartments,
  useAdminUpdateDepartment,
} from '@/hooks/useAdminDepartments'
import { AdminGuard } from '@/components/auth/AdminGuard'
import type { AdminDepartmentSummary } from '@/types/department'

/**
 * /admin/departments — 관리자 부서 CRUD (admin-department-crud, Wave 2 T4, docs/04 §5).
 *
 * <p>구성:
 * <ol>
 *   <li>상단 생성 폼 — 이름 입력 + 추가 버튼.</li>
 *   <li>중간 검색 입력(300ms debounce) — 부분 일치 검색어로 list 재조회.</li>
 *   <li>하단 부서 목록 — rename inline + 비활성/재활성 버튼.</li>
 * </ol>
 *
 * <p>self-protection은 부서 도메인에 없음 (사용자 self-demote 방지와 다른 레이어). 권한 가드는
 * backend {@code @PreAuthorize("hasRole('ADMIN')")}가 진실 — UI는 UX용. admin-user-mgmt 페이지의
 * 매트릭스를 1:1 답습.
 *
 * <p>가드: ADMIN-only mutation 페이지 — default `<AdminGuard>`로 좁힌다
 * (wave1.5-auditor-admin-ui-access).
 */
export default function AdminDepartmentsPage() {
  return (
    <AdminGuard>
      <div className="flex-1 overflow-auto p-6 space-y-10">
        <CreateSection />
        <ListSection />
      </div>
    </AdminGuard>
  )
}

// ── 생성 폼 ───────────────────────────────────────────────────────────────

function CreateSection() {
  const [name, setName] = useState('')
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)
  const create = useAdminCreateDepartment()

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMsg(null)
    setSuccessMsg(null)
    try {
      const result = await create.mutateAsync({ name })
      setSuccessMsg(`"${result.name}" 부서를 생성했습니다.`)
      setName('')
    } catch (e) {
      setErrorMsg(createErrorMessage(e))
    }
  }

  const disabled = create.isPending || name.trim().length === 0

  return (
    <div className="max-w-md">
      <header className="mb-6">
        <h1 className="text-lg font-semibold">부서 생성</h1>
        <p className="text-[12px] text-fg-2 mt-1">
          신규 부서를 추가합니다. 동일 이름의 활성 부서가 이미 있으면 거부됩니다.
        </p>
      </header>

      <form
        onSubmit={onSubmit}
        className="flex flex-col gap-4 p-6 rounded-md bg-surface-1 border border-border"
        aria-labelledby="create-dept-title"
      >
        <span id="create-dept-title" className="sr-only">부서 생성 폼</span>

        <label className="flex flex-col gap-1 text-sm">
          <span>부서 이름</span>
          <input
            type="text"
            required
            maxLength={100}
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="px-3 py-2 rounded border border-border bg-bg"
          />
        </label>

        {errorMsg && (
          <p role="alert" className="text-sm text-red-600">
            {errorMsg}
          </p>
        )}

        {successMsg && (
          <p role="status" className="text-sm text-emerald-600">
            {successMsg}
          </p>
        )}

        <button
          type="submit"
          disabled={disabled}
          className="px-3 py-2 rounded bg-accent text-accent-fg text-sm font-medium disabled:opacity-50"
        >
          {create.isPending ? '생성 중…' : '추가'}
        </button>
      </form>
    </div>
  )
}

function createErrorMessage(e: unknown): string {
  const err = e as { status?: number; code?: string }
  if (err.status === 409 && err.code === 'DEPARTMENT_CONFLICT') {
    return '같은 이름의 활성 부서가 이미 존재합니다.'
  }
  if (err.status === 400) return '입력값을 확인해주세요.'
  if (err.status === 403) return '권한이 없습니다.'
  return '부서 생성에 실패했습니다.'
}

// ── 검색 + 목록 ──────────────────────────────────────────────────────────

const PAGE_SIZE = 50

function ListSection() {
  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [page, setPage] = useState(0)

  // 300ms debounce — 입력 도중 매 키스트로크마다 list 재조회 회피.
  // useEffect cleanup으로 직전 timer 취소 → 마지막 입력 후 300ms idle 시점에만 query 갱신.
  useEffect(() => {
    const t = window.setTimeout(() => {
      setDebouncedQuery(query)
      setPage(0) // 검색어 변경 시 페이지 리셋 — 새 결과셋의 page=0이 의미상 일관.
    }, 300)
    return () => window.clearTimeout(t)
  }, [query])

  const queryResult = useAdminDepartments(page, PAGE_SIZE, debouncedQuery)

  return (
    <section aria-labelledby="dept-list-title">
      <header className="mb-4">
        <h2 id="dept-list-title" className="text-lg font-semibold">
          부서 목록
        </h2>
        <p className="text-[12px] text-fg-2 mt-1">
          이름 변경과 비활성/재활성은 즉시 반영되며 감사 로그에 기록됩니다.
        </p>
      </header>

      <div className="mb-3">
        <label className="flex flex-col gap-1 text-sm max-w-md">
          <span className="sr-only">부서 검색</span>
          <input
            type="search"
            placeholder="부서 이름으로 검색"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="부서 검색"
            className="px-3 py-2 rounded border border-border bg-bg"
          />
        </label>
      </div>

      {queryResult.isLoading && (
        <p className="text-sm text-fg-2">불러오는 중…</p>
      )}
      {queryResult.isError && (
        <p role="alert" className="text-sm text-red-600">
          목록을 불러오지 못했습니다.
        </p>
      )}

      {queryResult.data && (
        <>
          <div className="overflow-x-auto rounded-md border border-border">
            <table className="w-full text-sm">
              <thead className="bg-surface-1 text-fg-2">
                <tr>
                  <th className="text-left px-3 py-2">이름</th>
                  <th className="text-left px-3 py-2">상태</th>
                  <th className="text-left px-3 py-2">동작</th>
                </tr>
              </thead>
              <tbody>
                {queryResult.data.content.map((d) => (
                  <DepartmentRow key={d.id} department={d} />
                ))}
                {queryResult.data.content.length === 0 && (
                  <tr>
                    <td colSpan={3} className="px-3 py-6 text-center text-fg-2">
                      부서가 없습니다.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <Pagination
            page={queryResult.data.number}
            totalPages={Math.max(1, queryResult.data.totalPages)}
            onChange={setPage}
          />
        </>
      )}
    </section>
  )
}

function DepartmentRow({ department }: { department: AdminDepartmentSummary }) {
  const update = useAdminUpdateDepartment()
  const [editing, setEditing] = useState(false)
  const [editName, setEditName] = useState(department.name)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  const onStartEdit = () => {
    setEditName(department.name)
    setErrorMsg(null)
    setEditing(true)
  }

  const onSubmitRename = async (e: React.FormEvent) => {
    e.preventDefault()
    if (editName.trim() === department.name) {
      setEditing(false)
      return
    }
    setErrorMsg(null)
    try {
      await update.mutateAsync({ id: department.id, body: { name: editName.trim() } })
      setEditing(false)
    } catch (e) {
      setErrorMsg(updateErrorMessage(e))
    }
  }

  const onToggleActive = async () => {
    setErrorMsg(null)
    try {
      await update.mutateAsync({
        id: department.id,
        body: { isActive: !department.isActive },
      })
    } catch (e) {
      setErrorMsg(updateErrorMessage(e))
    }
  }

  return (
    <tr className="border-t border-border">
      <td className="px-3 py-2">
        {editing ? (
          <form onSubmit={onSubmitRename} className="flex gap-2 items-center">
            <input
              type="text"
              value={editName}
              maxLength={100}
              onChange={(e) => setEditName(e.target.value)}
              aria-label={`${department.name} 이름 편집`}
              className="px-2 py-1 rounded border border-border bg-bg text-sm flex-1"
              autoFocus
            />
            <button
              type="submit"
              disabled={update.isPending || editName.trim().length === 0}
              className="px-2 py-1 rounded bg-accent text-accent-fg text-sm disabled:opacity-50"
            >
              저장
            </button>
            <button
              type="button"
              onClick={() => setEditing(false)}
              className="px-2 py-1 rounded border border-border text-sm"
            >
              취소
            </button>
          </form>
        ) : (
          <span className={department.isActive ? '' : 'text-fg-2 line-through'}>
            {department.name}
          </span>
        )}
      </td>
      <td className="px-3 py-2">
        {department.isActive ? (
          <span className="text-emerald-600">활성</span>
        ) : (
          <span className="text-fg-2">비활성</span>
        )}
      </td>
      <td className="px-3 py-2">
        {!editing && (
          <button
            type="button"
            aria-label={`${department.name} 이름 변경`}
            disabled={update.isPending}
            onClick={onStartEdit}
            className="px-2 py-1 rounded border border-border text-sm disabled:opacity-50"
          >
            이름 변경
          </button>
        )}
        <button
          type="button"
          aria-label={
            department.isActive
              ? `${department.name} 비활성화`
              : `${department.name} 재활성화`
          }
          disabled={update.isPending}
          onClick={onToggleActive}
          className="ml-2 px-2 py-1 rounded border border-border text-sm disabled:opacity-50"
        >
          {department.isActive ? '비활성화' : '재활성화'}
        </button>
        {errorMsg && (
          <span role="alert" className="ml-2 text-xs text-red-600">
            {errorMsg}
          </span>
        )}
      </td>
    </tr>
  )
}

function updateErrorMessage(e: unknown): string {
  const err = e as { status?: number; code?: string }
  if (err.status === 409 && err.code === 'DEPARTMENT_CONFLICT') {
    return '같은 이름의 활성 부서가 이미 존재합니다.'
  }
  if (err.status === 404) return '부서를 찾을 수 없습니다.'
  if (err.status === 400) return '잘못된 요청입니다.'
  if (err.status === 403) return '권한이 없습니다.'
  return '변경에 실패했습니다.'
}

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
