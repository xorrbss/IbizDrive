'use client'
import { useRef, ChangeEvent } from 'react'
import { useRouter } from 'next/navigation'
import { Upload, FolderPlus } from 'lucide-react'
import { useMe } from '@/hooks/useMe'
import { useWorkspaces } from '@/hooks/useWorkspaces'
import { useUpload } from '@/hooks/useUpload'
import { buildWorkspacePath } from '@/lib/workspacePath'

/**
 * User Home Dashboard ① — 환영 헤더 + quick action 2 (업로드 / 새 폴더).
 *
 * <p>업로드: hidden &lt;input type=file&gt; 즉시 click → useUpload.enqueue → workspaceRoot 로 navigate.
 * user gesture 보존 (브라우저 popup blocker 회피).
 *
 * <p>새 폴더: workspaceRoot?action=new-folder 로 push → explorer page 의 useQuickActionParam 이
 * CreateFolderDialog 를 mount + URL 1-shot consume.
 *
 * <p>default workspace: 부서 우선 → 없으면 첫 팀. 0 workspace 시 두 버튼 모두 disabled.
 *
 * <p>spec: `docs/superpowers/specs/2026-05-15-quick-action-dialog-design.md`.
 */
export function WelcomeHeader() {
  const { data: session } = useMe()
  const { data: workspaces } = useWorkspaces()
  const router = useRouter()
  const { enqueue } = useUpload()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const name = session?.user?.name ?? '사용자'
  const department = workspaces?.department
  const firstTeam = workspaces?.teams?.[0]
  const teamCount = workspaces?.teams?.length ?? 0
  const hasWorkspace = !!department || teamCount > 0

  const defaultWorkspace = department
    ? { kind: 'department' as const, id: department.id, rootFolderId: department.rootFolderId }
    : firstTeam
    ? { kind: 'team' as const, id: firstTeam.id, rootFolderId: firstTeam.rootFolderId }
    : null

  const workspaceLink = defaultWorkspace
    ? buildWorkspacePath(
        { kind: defaultWorkspace.kind, workspaceId: defaultWorkspace.id },
        defaultWorkspace.rootFolderId,
        [],
      )
    : null

  const handleUploadClick = () => {
    if (!defaultWorkspace) return
    fileInputRef.current?.click()
  }

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files
    e.target.value = ''
    if (!files || files.length === 0) return
    if (!defaultWorkspace || !workspaceLink) return
    enqueue(Array.from(files), defaultWorkspace.rootFolderId)
    router.push(workspaceLink)
  }

  const handleNewFolderClick = () => {
    if (!workspaceLink) return
    router.push(`${workspaceLink}?action=new-folder`)
  }

  return (
    <div className="flex items-end justify-between gap-4">
      <div>
        <h1 className="text-[20px] font-semibold text-fg mb-1">
          안녕하세요, {name}님
        </h1>
        {hasWorkspace ? (
          <p className="text-[13px] text-fg-2">
            {department?.name ?? '소속 부서 없음'}
            {teamCount > 0 && ` · 팀 ${teamCount}개`}
          </p>
        ) : (
          <p className="text-[13px] text-fg-2">
            아직 소속된 workspace 가 없습니다. 관리자에게 부서 배정을 요청하거나, 팀을 만드세요.
          </p>
        )}
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <button
          type="button"
          aria-label="업로드"
          disabled={!defaultWorkspace}
          onClick={handleUploadClick}
          className="h-8 px-3 inline-flex items-center gap-1.5 rounded bg-accent text-accent-fg text-[12.5px] font-medium hover:opacity-90 disabled:opacity-40 disabled:cursor-not-allowed transition-opacity"
        >
          <Upload size={13} aria-hidden />
          <span>업로드</span>
        </button>
        <button
          type="button"
          aria-label="새 폴더"
          disabled={!defaultWorkspace}
          onClick={handleNewFolderClick}
          className="h-8 px-3 inline-flex items-center gap-1.5 rounded border border-border text-fg-2 text-[12.5px] hover:bg-surface-2 hover:text-fg disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          <FolderPlus size={13} aria-hidden />
          <span>새 폴더</span>
        </button>
      </div>
      <input
        ref={fileInputRef}
        type="file"
        multiple
        hidden
        aria-hidden
        onChange={handleFileChange}
      />
    </div>
  )
}
