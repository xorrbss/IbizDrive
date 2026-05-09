'use client'
import { useWorkspaces } from '@/hooks/useWorkspaces'
import { useExpandPathOnNavigate } from '@/hooks/useExpandPathOnNavigate'
import { WorkspaceSection } from './WorkspaceSection'
import { SharedWithMeSection } from './SharedWithMeSection'
import { TeamCreateButton } from './TeamCreateButton'

/**
 * 3-section 사이드바 shell — spec §4.5.
 * Section 1: 내 부서 (단일 workspace, optional)
 * Section 2: 내 팀 (0..N workspaces) + [+ 새 팀] CTA
 * Section 3: 공유받음 (별도 컴포넌트)
 */
export function SidebarSections() {
  useExpandPathOnNavigate()
  const { data, isLoading } = useWorkspaces()

  if (isLoading) return <SectionsSkeleton />

  return (
    <nav aria-label="workspace 트리" className="text-[12.5px] flex flex-col gap-2">
      {data?.department && (
        <section aria-label="내 부서">
          <SectionHeader icon="🏢" label="내 부서" />
          <WorkspaceSection
            kind="department"
            workspaceId={data.department.id}
            title={data.department.name}
            rootFolderId={data.department.rootFolderId}
          />
        </section>
      )}

      {!data?.department && (
        <section aria-label="내 부서" className="px-3 py-2 text-fg-muted text-[12px]">
          부서 미배정 — 관리자에게 문의
        </section>
      )}

      <section aria-label="내 팀">
        <SectionHeader icon="👥" label={`내 팀 (${data?.teams.length ?? 0})`} />
        {data?.teams.map((t) => (
          <WorkspaceSection
            key={t.id}
            kind="team"
            workspaceId={t.id}
            title={t.name}
            rootFolderId={t.rootFolderId}
          />
        ))}
        <TeamCreateButton />
      </section>

      <SharedWithMeSection />
    </nav>
  )
}

function SectionHeader({ icon, label }: { icon: string; label: string }) {
  return (
    <h2 className="px-2 pt-2 pb-1 text-[11px] font-semibold uppercase tracking-wide text-fg-muted">
      <span aria-hidden className="mr-1">{icon}</span>
      {label}
    </h2>
  )
}

function SectionsSkeleton() {
  return (
    <div className="space-y-2 animate-pulse" aria-hidden>
      {[1, 2, 3].map((i) => (
        <div key={i} className="space-y-1">
          <div className="h-4 w-20 bg-surface-2 rounded mx-2" />
          <div className="h-6 bg-surface-2 rounded" />
        </div>
      ))}
    </div>
  )
}
