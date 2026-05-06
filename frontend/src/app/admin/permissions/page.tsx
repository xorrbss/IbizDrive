'use client'
import { useEffect, useState } from 'react'
import { useAdminPermissions } from '@/hooks/useAdminPermissions'
import {
  ADMIN_PRESETS,
  ADMIN_RESOURCE_TYPES,
  ADMIN_SUBJECT_TYPES,
  type AdminPermissionRow,
  type AdminPreset,
  type AdminResourceType,
  type AdminSubjectType,
} from '@/types/permission'

/**
 * /admin/permissions — 관리자 권한 매트릭스 (admin-permission-matrix, Wave 2 T5, docs/04 §9).
 *
 * <p>read-only viewer — 본 페이지는 mutation 없음. invalidate는 다른 트랙(grant/revoke 직접 CRUD,
 * Wave 3+) 도입 시 동시 구현. 만료된 grant 도 결과에 포함되어 "만료됨" 빨간 배지로 표시 (cron
 * 정리 전 가시화).
 *
 * <p>구성:
 * <ol>
 *   <li>FilterBar — subjectType / subjectId / resourceType / preset / q (300ms debounce).</li>
 *   <li>Table — id, subjectType+subjectName, resourceType+resourceName, preset, grantedByName, grantedAt, expiresAt+isExpired.</li>
 *   <li>Pagination — prev/next + "page X / Y".</li>
 * </ol>
 */
export default function AdminPermissionsPage() {
  return (
    <div className="flex-1 overflow-auto p-6 space-y-6">
      <header>
        <h1 className="text-lg font-semibold">권한 매트릭스</h1>
        <p className="text-[12px] text-fg-2 mt-1">
          폴더/파일 권한 grant 전체 목록을 검색·조회합니다. 만료된 grant 도 표시되며 빨간 배지로
          구분됩니다 (cron 정리 전 가시화).
        </p>
      </header>
      <PermissionsView />
    </div>
  )
}

const PAGE_SIZE = 20

function PermissionsView() {
  const [subjectType, setSubjectType] = useState<AdminSubjectType | ''>('')
  const [subjectId, setSubjectId] = useState('')
  const [resourceType, setResourceType] = useState<AdminResourceType | ''>('')
  const [preset, setPreset] = useState<AdminPreset | ''>('')
  const [q, setQ] = useState('')
  const [debouncedQ, setDebouncedQ] = useState('')
  const [page, setPage] = useState(0)

  // 300ms debounce on q (T4 패턴 답습)
  useEffect(() => {
    const t = window.setTimeout(() => {
      setDebouncedQ(q)
      setPage(0)
    }, 300)
    return () => window.clearTimeout(t)
  }, [q])

  // 다른 filter 변경 시 page 리셋
  useEffect(() => {
    setPage(0)
  }, [subjectType, subjectId, resourceType, preset])

  const result = useAdminPermissions({
    subjectType: subjectType || undefined,
    subjectId: subjectId || undefined,
    resourceType: resourceType || undefined,
    preset: preset || undefined,
    q: debouncedQ || undefined,
    page,
    size: PAGE_SIZE,
  })

  return (
    <section aria-labelledby="perm-list-title">
      <h2 id="perm-list-title" className="sr-only">
        권한 목록
      </h2>

      <FilterBar
        subjectType={subjectType}
        onSubjectType={setSubjectType}
        subjectId={subjectId}
        onSubjectId={setSubjectId}
        resourceType={resourceType}
        onResourceType={setResourceType}
        preset={preset}
        onPreset={setPreset}
        q={q}
        onQ={setQ}
      />

      {result.isLoading && <p className="text-sm text-fg-2">불러오는 중…</p>}
      {result.isError && (
        <p role="alert" className="text-sm text-red-600">
          목록을 불러오지 못했습니다.
        </p>
      )}

      {result.data && (
        <>
          <div className="overflow-x-auto rounded-md border border-border">
            <table className="w-full text-sm">
              <thead className="bg-surface-1 text-fg-2">
                <tr>
                  <th className="text-left px-3 py-2">대상</th>
                  <th className="text-left px-3 py-2">리소스</th>
                  <th className="text-left px-3 py-2">권한</th>
                  <th className="text-left px-3 py-2">부여자</th>
                  <th className="text-left px-3 py-2">부여 시각</th>
                  <th className="text-left px-3 py-2">만료</th>
                </tr>
              </thead>
              <tbody>
                {result.data.content.map((row) => (
                  <PermissionRow key={row.id} row={row} />
                ))}
                {result.data.content.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-3 py-6 text-center text-fg-2">
                      조건에 맞는 권한이 없습니다.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

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
  subjectType: AdminSubjectType | ''
  onSubjectType: (v: AdminSubjectType | '') => void
  subjectId: string
  onSubjectId: (v: string) => void
  resourceType: AdminResourceType | ''
  onResourceType: (v: AdminResourceType | '') => void
  preset: AdminPreset | ''
  onPreset: (v: AdminPreset | '') => void
  q: string
  onQ: (v: string) => void
}) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-5 gap-3 mb-4">
      <label className="flex flex-col gap-1 text-sm">
        <span className="text-fg-2">대상 종류</span>
        <select
          value={props.subjectType}
          onChange={(e) => props.onSubjectType(e.target.value as AdminSubjectType | '')}
          className="px-2 py-2 rounded border border-border bg-bg"
          aria-label="대상 종류 필터"
        >
          <option value="">전체</option>
          {ADMIN_SUBJECT_TYPES.map((s) => (
            <option key={s} value={s}>
              {SUBJECT_LABEL[s]}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1 text-sm">
        <span className="text-fg-2">대상 ID</span>
        <input
          type="text"
          value={props.subjectId}
          onChange={(e) => props.onSubjectId(e.target.value)}
          placeholder="UUID"
          aria-label="대상 ID 필터"
          className="px-2 py-2 rounded border border-border bg-bg"
        />
      </label>

      <label className="flex flex-col gap-1 text-sm">
        <span className="text-fg-2">리소스 종류</span>
        <select
          value={props.resourceType}
          onChange={(e) => props.onResourceType(e.target.value as AdminResourceType | '')}
          className="px-2 py-2 rounded border border-border bg-bg"
          aria-label="리소스 종류 필터"
        >
          <option value="">전체</option>
          {ADMIN_RESOURCE_TYPES.map((r) => (
            <option key={r} value={r}>
              {RESOURCE_LABEL[r]}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1 text-sm">
        <span className="text-fg-2">프리셋</span>
        <select
          value={props.preset}
          onChange={(e) => props.onPreset(e.target.value as AdminPreset | '')}
          className="px-2 py-2 rounded border border-border bg-bg"
          aria-label="프리셋 필터"
        >
          <option value="">전체</option>
          {ADMIN_PRESETS.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1 text-sm">
        <span className="text-fg-2">검색</span>
        <input
          type="search"
          value={props.q}
          onChange={(e) => props.onQ(e.target.value)}
          placeholder="이름 부분일치"
          aria-label="이름 검색"
          className="px-2 py-2 rounded border border-border bg-bg"
        />
      </label>
    </div>
  )
}

const SUBJECT_LABEL: Record<AdminSubjectType, string> = {
  user: '사용자',
  department: '부서',
  role: '역할',
  everyone: '전사',
}

const RESOURCE_LABEL: Record<AdminResourceType, string> = {
  folder: '폴더',
  file: '파일',
}

// ── Row ──────────────────────────────────────────────────────────────────

function PermissionRow({ row }: { row: AdminPermissionRow }) {
  return (
    <tr className="border-t border-border">
      <td className="px-3 py-2">
        <div>
          <span className="text-fg-2 mr-1">[{SUBJECT_LABEL[row.subjectType]}]</span>
          <span>{row.subjectName ?? <span className="text-fg-muted">(이름 없음)</span>}</span>
        </div>
        {row.subjectId && (
          <div className="text-[11px] text-fg-muted font-mono">{row.subjectId}</div>
        )}
      </td>
      <td className="px-3 py-2">
        <div>
          <span className="text-fg-2 mr-1">[{RESOURCE_LABEL[row.resourceType]}]</span>
          <span>{row.resourceName ?? <span className="text-fg-muted">(이름 없음)</span>}</span>
        </div>
        <div className="text-[11px] text-fg-muted font-mono">{row.resourceId}</div>
      </td>
      <td className="px-3 py-2">{row.preset}</td>
      <td className="px-3 py-2">
        {row.grantedByName ?? <span className="text-fg-muted">(이름 없음)</span>}
      </td>
      <td className="px-3 py-2 text-fg-2">{formatDate(row.grantedAt)}</td>
      <td className="px-3 py-2">
        {row.expiresAt ? (
          <div className="flex items-center gap-2">
            <span className="text-fg-2">{formatDate(row.expiresAt)}</span>
            {row.isExpired && (
              <span className="text-[10px] px-1.5 py-0.5 rounded bg-red-100 text-red-700">
                만료됨
              </span>
            )}
          </div>
        ) : (
          <span className="text-fg-muted">무기한</span>
        )}
      </td>
    </tr>
  )
}

function formatDate(iso: string): string {
  // Locale 비종속 ISO 표시 — 시각 추적 보조용. 별도 i18n은 도입하지 않음 (admin tool).
  return iso.replace('T', ' ').replace(/\..*$/, '')
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
