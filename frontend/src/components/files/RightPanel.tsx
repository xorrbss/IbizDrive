'use client'
import { useEffect, useState } from 'react'
import { useOpenFile } from '@/hooks/useOpenFile'
import { useFileDetail } from '@/hooks/useFileDetail'
import type { FileItem } from '@/types/file'
import { VersionsTab } from './VersionsTab'
import { PermissionsTab } from './PermissionsTab'
import { ActivityTab } from './ActivityTab'

/**
 * RightPanel: ?file=<id> 에 대응하는 파일 상세 패널.
 *
 * - URL query param이 진실 출처 (docs/01 §2.3)
 * - Esc 전역 리스너로 닫기 (§12.1)
 * - Parallel route 대신 query param 사용 (§19 원칙 2)
 * - M15: 4-tab 도입 (세부정보/버전/활동/권한)
 * - M-RP.1: 버전 탭 wiring 활성.
 * - M-RP.3: 권한 탭 wiring 활성 (read-only).
 * - M-RP.4: 활동 탭 wiring 활성 (ADR #40 RP-2 — file scoped activity).
 *
 * 설계: docs/01 §11 (로딩/에러/빈 상태), §17.5 (useOpenFile), §18 row 15 (M15)
 */
type TabId = 'detail' | 'versions' | 'activity' | 'permissions'

const TABS: { id: TabId; label: string }[] = [
  { id: 'detail', label: '세부정보' },
  { id: 'versions', label: '버전' },
  { id: 'activity', label: '활동' },
  { id: 'permissions', label: '권한' },
]

export function RightPanel() {
  const { fileId, close } = useOpenFile()
  const { data, isLoading, error } = useFileDetail(fileId)
  const [tab, setTab] = useState<TabId>('detail')

  // 파일이 바뀌면 detail 탭으로 리셋
  useEffect(() => {
    setTab('detail')
  }, [fileId])

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
        role="tablist"
        aria-label="상세 탭"
        className="flex items-center gap-0 px-2 border-b border-border bg-surface-1"
      >
        {TABS.map((t) => {
          const active = tab === t.id
          return (
            <button
              key={t.id}
              type="button"
              role="tab"
              aria-selected={active}
              aria-controls={`right-panel-tab-${t.id}`}
              onClick={() => setTab(t.id)}
              className={`px-2.5 py-1.5 text-[12px] -mb-px border-b-2 transition-colors ${
                active
                  ? 'border-accent text-fg'
                  : 'border-transparent text-fg-muted hover:text-fg'
              }`}
            >
              {t.label}
            </button>
          )
        })}
      </div>

      <div
        id={`right-panel-tab-${tab}`}
        role="tabpanel"
        className="flex-1 overflow-y-auto px-3.5 py-3 text-[12px]"
      >
        {tab === 'detail' && (
          <>
            {isLoading && <PanelSkeleton />}
            {!isLoading && error && <PanelError />}
            {!isLoading && !error && data && <PanelBody file={data} />}
          </>
        )}
        {/* M-RP.1: versions 탭 활성화. 조건부 렌더로 비활성 탭에서는 mount 안 됨 → fetch 차단. */}
        {tab === 'versions' && <VersionsTab fileId={fileId} />}
        {/* M-RP.4: activity 탭 활성화. 조건부 렌더로 비활성 탭에서는 mount 안 됨 → fetch 차단. */}
        {tab === 'activity' && <ActivityTab fileId={fileId} />}
        {/* M-RP.3: permissions 탭 활성화. 조건부 렌더로 비활성 탭에서는 mount 안 됨 → fetch 차단. */}
        {tab === 'permissions' && <PermissionsTab fileId={fileId} />}
      </div>
    </aside>
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

      {/* P_panel-B: backend FileDetailResponse(P_panel-A)가 채워 보낸 owner/sharedWith/folderPath
          를 detail 탭에서 시각화. 모두 optional이므로 부재 시 row 자체 미렌더. */}
      {file.owner && (
        <>
          <dt className="text-fg-muted">소유자</dt>
          <dd className="text-fg flex items-center gap-1.5 min-w-0">
            <AvatarBadge name={file.owner.displayName} />
            <span className="truncate">{file.owner.displayName}</span>
            <span className="text-fg-muted text-[11px] truncate">{file.owner.email}</span>
          </dd>
        </>
      )}

      {file.sharedWith && file.sharedWith.length > 0 && (
        <>
          <dt className="text-fg-muted">공유</dt>
          <dd className="text-fg" aria-label="공유 대상">
            <SharedWithStack subjects={file.sharedWith} />
          </dd>
        </>
      )}

      {file.folderPath && file.folderPath.length > 0 && (
        <>
          <dt className="text-fg-muted">경로</dt>
          <dd className="text-fg break-all" aria-label="폴더 경로">
            <FolderPathInline path={file.folderPath} />
          </dd>
        </>
      )}
    </dl>
  )
}

/**
 * 사용자 이름의 첫 글자를 원형 배지로 표시. 익명/빈 이름 fallback "?". RightPanel detail의 owner row와
 * sharedWith stack에서 재사용 (소형 UX 요소이므로 모듈 분리 없이 inline).
 */
function AvatarBadge({ name, size = 18 }: { name: string | null | undefined; size?: number }) {
  const initial = name && name.length > 0 ? name.trim().charAt(0).toUpperCase() : '?'
  return (
    <span
      aria-hidden
      style={{ width: size, height: size, fontSize: Math.max(9, size * 0.55) }}
      className="inline-flex shrink-0 items-center justify-center rounded-full bg-surface-3 text-fg-muted border border-border tabular-nums leading-none"
    >
      {initial}
    </span>
  )
}

/**
 * 공유 대상 스택 — 상위 maxVisible명 아바타 + "+N" overflow. everyone subject 는 backend 가
 * subjectName="전체"로 세팅하므로 AvatarBadge의 initial 폴백("?")이 아닌 "전"이 표시된다.
 */
function SharedWithStack({
  subjects,
  maxVisible = 5,
}: {
  subjects: NonNullable<FileItem['sharedWith']>
  maxVisible?: number
}) {
  const visible = subjects.slice(0, maxVisible)
  const overflow = subjects.length - visible.length
  return (
    <span className="inline-flex items-center gap-1">
      <span className="inline-flex -space-x-1.5">
        {visible.map((s, i) => (
          <span
            key={`${s.subjectType}-${s.subjectId ?? 'everyone'}-${i}`}
            title={`${s.subjectName ?? '(이름 없음)'} · ${s.preset}`}
            className="inline-flex"
          >
            <AvatarBadge name={s.subjectName} />
          </span>
        ))}
      </span>
      {overflow > 0 && (
        <span className="text-fg-muted text-[11px] tabular-nums">+{overflow}</span>
      )}
    </span>
  )
}

/**
 * 폴더 경로 — root → 직접 부모 순서 (backend folderPath와 동일 순). " / " 구분.
 * v1.x는 plain text (클릭 navigation은 후속 — workspace 라우팅 컨텍스트 필요).
 */
function FolderPathInline({ path }: { path: NonNullable<FileItem['folderPath']> }) {
  return (
    <span className="text-fg-muted">
      {path.map((p, i) => (
        <span key={p.id}>
          {i > 0 && <span className="text-border mx-1">/</span>}
          <span className="text-fg">{p.name}</span>
        </span>
      ))}
    </span>
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
