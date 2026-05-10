'use client'
import Link from 'next/link'
import { useWorkspaces } from '@/hooks/useWorkspaces'

/**
 * 휴지통 workspace 탭 바 — /trash/d/:deptId | /trash/t/:teamId 간 전환 (Plan E T12).
 * - archived 팀은 dim(opacity-60) + 🔒 prefix — clickable, listing 노출 가능.
 * - aria-selected으로 active 탭 표시 (codebase 패턴: RightPanel, FileCard).
 */
export function TrashWorkspaceTabs(props: {
  activeScope: { type: 'department' | 'team'; id: string }
}) {
  const { activeScope } = props
  const { data } = useWorkspaces()

  if (!data) return null

  return (
    <nav role="tablist" aria-label="휴지통 workspace 선택" className="flex gap-2 px-6 pt-4 border-b">
      {data.department && (
        <TabLink
          href={`/trash/d/${data.department.id}`}
          active={activeScope.type === 'department' && activeScope.id === data.department.id}
        >
          {data.department.name}
        </TabLink>
      )}
      {data.teams.map(t => (
        <TabLink
          key={t.id}
          href={`/trash/t/${t.id}`}
          active={activeScope.type === 'team' && activeScope.id === t.id}
          archived={Boolean(t.archivedAt)}
        >
          {t.archivedAt ? <>{'🔒'} {t.name}</> : t.name}
        </TabLink>
      ))}
    </nav>
  )
}

function TabLink(props: {
  href: string
  active: boolean
  archived?: boolean
  children: React.ReactNode
}) {
  return (
    <Link
      href={props.href}
      role="tab"
      aria-selected={props.active}
      className={[
        'px-3 py-2 text-sm border-b-2 -mb-px',
        props.active ? 'border-accent text-accent' : 'border-transparent text-fg-muted',
        props.archived ? 'opacity-60' : '',
      ].filter(Boolean).join(' ')}
    >
      {props.children}
    </Link>
  )
}
