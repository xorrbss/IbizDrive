'use client'
import { useEffect, useRef, useState } from 'react'
import { useOpenFile } from '@/hooks/useOpenFile'
import { useFileDetail } from '@/hooks/useFileDetail'
import { PermissionsPanel } from './PermissionsPanel'
import type { FileItem } from '@/types/file'

type TabKey = 'details' | 'versions' | 'activity' | 'permissions'
const TABS: { key: TabKey; label: string }[] = [
  { key: 'details', label: '세부정보' },
  { key: 'versions', label: '버전' },
  { key: 'activity', label: '활동' },
  { key: 'permissions', label: '권한' },
]

/**
 * RightPanel: ?file=<id> 에 대응하는 파일 상세 패널.
 *
 * - URL query param이 진실 출처 (docs/01 §2.3)
 * - Esc 전역 리스너로 닫기 (§12.1)
 * - Parallel route 대신 query param 사용 (§19 원칙 2)
 *
 * 설계: docs/01 §11 (로딩/에러/빈 상태), §17.5 (useOpenFile)
 */
export function RightPanel() {
  const { fileId, close } = useOpenFile()
  const { data, isLoading, error } = useFileDetail(fileId)
  const [tab, setTab] = useState<TabKey>('details')
  const tablistRef = useRef<HTMLDivElement>(null)

  // Esc 전역 핸들러 — 포커스 위치와 무관하게 패널 닫기
  useEffect(() => {
    if (!fileId) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.stopPropagation()
        close()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [fileId, close])

  // 패널 열릴 때 details로 리셋 (file 바뀔 때마다)
  useEffect(() => {
    setTab('details')
  }, [fileId])

  const handleTabKey = (e: React.KeyboardEvent) => {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight' && e.key !== 'Home' && e.key !== 'End') return
    e.preventDefault()
    const idx = TABS.findIndex((t) => t.key === tab)
    let next = idx
    if (e.key === 'ArrowLeft') next = (idx - 1 + TABS.length) % TABS.length
    else if (e.key === 'ArrowRight') next = (idx + 1) % TABS.length
    else if (e.key === 'Home') next = 0
    else if (e.key === 'End') next = TABS.length - 1
    setTab(TABS[next].key)
    requestAnimationFrame(() => {
      const btn = tablistRef.current?.querySelector(
        `[data-tab-key="${TABS[next].key}"]`,
      ) as HTMLElement | null
      btn?.focus()
    })
  }

  if (!fileId) return null

  return (
    <aside
      role="complementary"
      aria-label="파일 상세"
      className="w-[360px] shrink-0 border-l border-border bg-surface-1 flex flex-col min-h-0 overflow-hidden"
    >
      <header className="px-3.5 pt-3 pb-2 border-b border-border flex items-center gap-2">
        <h2 className="flex-1 text-[13.5px] font-semibold text-fg truncate">
          {isLoading ? '로딩…' : (data?.name ?? '파일')}
        </h2>
        <button
          type="button"
          onClick={close}
          aria-label="패널 닫기"
          className="w-7 h-7 inline-flex items-center justify-center rounded text-fg-muted hover:bg-surface-2 hover:text-fg transition-colors"
        >
          ×
        </button>
      </header>

      <div
        ref={tablistRef}
        role="tablist"
        aria-label="파일 정보 탭"
        onKeyDown={handleTabKey}
        className="flex border-b border-border px-1.5"
      >
        {TABS.map((t) => {
          const active = tab === t.key
          return (
            <button
              key={t.key}
              type="button"
              role="tab"
              data-tab-key={t.key}
              id={`tab-${t.key}`}
              aria-selected={active}
              aria-controls={`tabpanel-${t.key}`}
              tabIndex={active ? 0 : -1}
              onClick={() => setTab(t.key)}
              className={`px-2.5 py-1.5 text-[12px] border-b-2 -mb-px transition-colors ${
                active
                  ? 'border-accent text-fg font-medium'
                  : 'border-transparent text-fg-muted hover:text-fg'
              }`}
            >
              {t.label}
            </button>
          )
        })}
      </div>

      <div
        role="tabpanel"
        id={`tabpanel-${tab}`}
        aria-labelledby={`tab-${tab}`}
        className="flex-1 overflow-y-auto px-3.5 py-3 text-[12px]"
      >
        {tab === 'details' && (
          <>
            {isLoading && <PanelSkeleton />}
            {!isLoading && error && <PanelError />}
            {!isLoading && !error && data && <PanelBody file={data} />}
          </>
        )}
        {tab === 'versions' && <PanelStub label="버전 기록" />}
        {tab === 'activity' && <PanelStub label="활동 로그" />}
        {tab === 'permissions' && fileId && <PermissionsPanel fileId={fileId} />}
      </div>
    </aside>
  )
}

function PanelStub({ label }: { label: string }) {
  return (
    <div className="text-fg-subtle text-[12px] py-4">
      {label} — 준비 중입니다
    </div>
  )
}

function PanelSkeleton() {
  return (
    <div className="space-y-2 animate-pulse" aria-hidden>
      <div className="h-4 bg-surface-2 rounded w-3/4" />
      <div className="h-4 bg-surface-2 rounded w-1/2" />
      <div className="h-4 bg-surface-2 rounded w-2/3" />
    </div>
  )
}

function PanelError() {
  return (
    <div role="alert" className="text-[12px] text-danger">
      파일 정보를 불러오지 못했습니다.
    </div>
  )
}

function PanelBody({ file }: { file: FileItem }) {
  return (
    <dl className="grid grid-cols-[80px_1fr] gap-x-2.5 gap-y-2 text-[12px]">
      <dt className="text-fg-muted">이름</dt>
      <dd className="text-fg break-all">{file.name}</dd>

      <dt className="text-fg-muted">유형</dt>
      <dd className="text-fg">{file.type === 'folder' ? '폴더' : (file.mimeType ?? '파일')}</dd>

      <dt className="text-fg-muted">크기</dt>
      <dd className="text-fg tabular-nums">{formatSize(file.size)}</dd>

      <dt className="text-fg-muted">수정일</dt>
      <dd className="text-fg tabular-nums">{formatDate(file.updatedAt)}</dd>

      <dt className="text-fg-muted">수정자</dt>
      <dd className="text-fg">{file.updatedBy}</dd>
    </dl>
  )
}

function formatSize(bytes: number | null): string {
  if (bytes == null) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
