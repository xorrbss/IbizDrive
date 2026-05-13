'use client'
import { useEffect, useState } from 'react'
import { Download, UserPlus, MoreHorizontal, Lock } from 'lucide-react'
import { useOpenFile } from '@/hooks/useOpenFile'
import { useFileDetail } from '@/hooks/useFileDetail'
import { api } from '@/lib/api'
import { fileIconKind, kindLabel } from '@/lib/fileIcon'
import { FileTypeIcon, type FileTypeIconKind } from '@/components/icons/FileTypeIcon'
import type { FileItem, SubjectGrantBrief } from '@/types/file'
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
 * - M-RP.1/3/4: 버전/권한/활동 탭 wiring 활성
 * - P_panel-B (rightpanel-frontend-wire): 헤더 파일 아이콘 + 액션 3개 (다운로드/공유/더보기) +
 *   PreviewCard placeholder + detail 탭 7 row 풀세트 (viewCount는 ADR #9 blocker로 분리).
 *   디자인 진실의 출처: `design-reference/panels.jsx` L8~184.
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

  // kind는 data 도착 후에만 의미. 로딩/에러 동안엔 헤더 아이콘에 fallback 'doc' 사용.
  const kind: FileTypeIconKind = data ? fileIconKind(data) : 'doc'
  const actionsDisabled = isLoading || !!error || !data

  const onDownload = () => {
    if (fileId) api.downloadFile(fileId)
  }
  // 디자인 onOpenPermissions 의미 = 권한 탭 전환 (ShareDialog는 BulkActionBar 별도 entry).
  const onShare = () => setTab('permissions')

  return (
    <aside
      role="complementary"
      aria-label="파일 상세"
      className="w-[360px] shrink-0 border-l border-border bg-surface-1 flex flex-col min-h-0 overflow-hidden"
    >
      <header className="px-3.5 pt-3 pb-2 border-b border-border flex flex-col gap-2">
        <div className="flex items-center gap-2">
          <FileTypeIcon kind={kind} size={18} className="shrink-0" />
          <h2
            className="flex-1 text-[13.5px] font-semibold text-fg truncate"
            title={data?.name}
          >
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
        </div>
        <div
          role="toolbar"
          aria-label="파일 액션"
          className="flex items-center gap-0.5"
        >
          <ActionBtn
            icon={Download}
            label="다운로드"
            onClick={onDownload}
            disabled={actionsDisabled}
          />
          <ActionBtn
            icon={UserPlus}
            label="공유"
            onClick={onShare}
            disabled={actionsDisabled}
          />
          <button
            type="button"
            aria-label="더보기"
            // TODO: dropdown menu (rename / delete 등). 디자인엔 dots 아이콘만 있고
            // 동작 미정 → 별도 트랙. 본 트랙은 placeholder.
            disabled
            className="inline-flex items-center justify-center w-7 h-7 rounded text-fg-muted hover:bg-surface-2 hover:text-fg disabled:opacity-40 disabled:hover:bg-transparent transition-colors"
          >
            <MoreHorizontal className="w-3.5 h-3.5" />
          </button>
        </div>
      </header>

      {data && !isLoading && !error && <PreviewCard kind={kind} />}

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
        {tab === 'versions' && <VersionsTab fileId={fileId} />}
        {tab === 'activity' && <ActivityTab fileId={fileId} />}
        {tab === 'permissions' && <PermissionsTab fileId={fileId} />}
      </div>
    </aside>
  )
}

function ActionBtn({
  icon: Icon,
  label,
  onClick,
  disabled,
}: {
  icon: React.ComponentType<{ className?: string }>
  label: string
  onClick: () => void
  disabled?: boolean
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="inline-flex items-center gap-1 px-2 py-1 rounded text-[12px] text-fg-muted hover:bg-surface-2 hover:text-fg disabled:opacity-40 disabled:hover:bg-transparent transition-colors"
    >
      <Icon className="w-3 h-3" />
      <span>{label}</span>
    </button>
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
  const kind = fileIconKind(file)
  const shared = file.sharedWith ?? []
  const path = file.folderPath ?? []
  return (
    <div className="space-y-2">
      <DetailRow label="종류" value={kindLabel(kind)} />
      <DetailRow label="크기" value={formatSize(file.size)} />
      <DetailRow
        label="소유자"
        value={
          file.owner ? (
            <DetailUser
              id={file.owner.id}
              name={file.owner.displayName}
            />
          ) : (
            '—'
          )
        }
      />
      <DetailRow
        label="수정한 사람"
        value={<DetailUser id={file.updatedBy} name={file.updatedBy} />}
      />
      <DetailRow label="수정일" value={formatDate(file.updatedAt)} />
      <DetailRow
        label="공유됨"
        value={
          shared.length > 0 ? (
            <DetailSharedStack grants={shared} />
          ) : (
            <span className="text-fg-muted">비공개</span>
          )
        }
      />
      <DetailRow
        label="경로"
        value={
          path.length > 0 ? (
            <span className="text-fg truncate" title={path.map((p) => p.name).join(' / ')}>
              {['내 드라이브', ...path.map((p) => p.name)].join(' / ')}
            </span>
          ) : (
            <span className="text-fg-muted">—</span>
          )
        }
      />
      <DetailRow
        label="위치"
        value={
          file.restricted ? (
            <span className="inline-flex items-center gap-1 text-fg-muted">
              <Lock className="w-2.5 h-2.5" />
              <span>권한 제한</span>
            </span>
          ) : (
            <span className="text-fg-muted">공개 링크 없음</span>
          )
        }
      />
    </div>
  )
}

function DetailRow({
  label,
  value,
}: {
  label: string
  value: React.ReactNode
}) {
  return (
    <div className="flex items-start gap-2.5 text-[12px]">
      <span className="w-20 shrink-0 text-fg-muted leading-[18px]">{label}</span>
      <span className="flex-1 min-w-0 text-fg leading-[18px]">{value}</span>
    </div>
  )
}

function DetailUser({ id, name }: { id: string; name: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <MiniAvatar id={id} name={name} />
      <span className="text-fg truncate">{name}</span>
    </span>
  )
}

function DetailSharedStack({ grants }: { grants: SubjectGrantBrief[] }) {
  const max = 4
  const visible = grants.slice(0, max)
  const rest = grants.length - visible.length
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className="inline-flex -space-x-1">
        {visible.map((g, idx) => (
          <MiniAvatar
            key={`${g.subjectType}:${g.subjectId ?? g.subjectName ?? idx}`}
            id={g.subjectId ?? g.subjectType}
            name={g.subjectName ?? '?'}
            ring
          />
        ))}
        {rest > 0 && (
          <span className="w-[18px] h-[18px] rounded-full bg-surface-2 text-fg-muted text-[9px] inline-flex items-center justify-center ring-2 ring-surface-1">
            +{rest}
          </span>
        )}
      </span>
      <span className="text-fg-muted">{grants.length}명</span>
    </span>
  )
}

// mini-avatar 색상 — admin/teams/Avatars.tsx PAvatar 와 동일 8색 팔레트 + hash.
// `.p-avatar` 가 admin.css scope 라서 (explorer) 레이아웃에서 재사용 불가 → 인라인.
const AVATAR_PALETTE = [
  '#5B7FCC',
  '#C16A8B',
  '#5BA08A',
  '#C9925A',
  '#7C6BB5',
  '#A56FB8',
  '#5C9B9B',
  '#B9824D',
] as const

function colorForUser(userId: string): string {
  let hash = 0
  for (let i = 0; i < userId.length; i++) {
    hash = (hash * 31 + userId.charCodeAt(i)) | 0
  }
  return AVATAR_PALETTE[Math.abs(hash) % AVATAR_PALETTE.length]
}

function initialOf(name: string): string {
  const trimmed = name.trim()
  if (!trimmed) return '?'
  return Array.from(trimmed)[0].toUpperCase()
}

function MiniAvatar({
  id,
  name,
  ring,
}: {
  id: string
  name: string
  ring?: boolean
}) {
  return (
    <span
      aria-label={name}
      title={name}
      className={`inline-flex items-center justify-center w-[18px] h-[18px] rounded-full text-white text-[9px] font-semibold select-none shrink-0 ${
        ring ? 'ring-2 ring-surface-1' : ''
      }`}
      style={{ background: colorForUser(id) }}
    >
      {initialOf(name)}
    </span>
  )
}

// kind 별 PreviewCard soft 배경 — design-reference/panels.jsx L196~200.
const PREVIEW_BG: Record<FileTypeIconKind, string> = {
  folder: '#E0EBFB',
  doc: '#E8F0FB',
  pdf: '#FCE8E4',
  sheet: '#E3F3E9',
  slides: '#FCEEDA',
  image: '#F1E6F6',
  video: '#F9E3EA',
  figma: '#EFE6FA',
  code: '#E5EAEF',
  archive: '#EEEBE2',
}

function PreviewCard({ kind }: { kind: FileTypeIconKind }) {
  return (
    <div
      aria-hidden
      data-testid="rp-preview-card"
      className="mx-3.5 mt-3 mb-1 h-28 rounded-md flex flex-col items-center justify-center gap-2 px-3 shrink-0"
      style={{ background: PREVIEW_BG[kind] }}
    >
      <FileTypeIcon kind={kind} size={42} />
      <div className="w-full max-w-[180px] space-y-1">
        <div className="h-1 rounded-full bg-fg/15" />
        <div className="h-1 rounded-full bg-fg/15" style={{ width: '75%' }} />
        <div className="h-1 rounded-full bg-fg/15" style={{ width: '85%' }} />
        <div className="h-1 rounded-full bg-fg/15" style={{ width: '55%' }} />
      </div>
    </div>
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
