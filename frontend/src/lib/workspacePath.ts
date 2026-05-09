/**
 * Workspace URL canonical builder + parser — Plan B.
 *
 * spec §5.1: `/d/:deptSlug/[...parts]`, `/t/:teamSlug/[...parts]`, `/shared/[...parts]`.
 * MVP는 slug = workspace UUID (KISS) — 후속 트랙에서 SEO slug + server lookup 도입.
 *
 * `parts[0]`은 backend 호환을 위해 folderId(UUID) 위치 그대로 답습 (기존 `/files/[...parts]` 패턴).
 * 나머지 segment는 SEO/canonical용 폴더 이름 slug.
 */

export type SidebarSectionKind = 'department' | 'team' | 'shared'

export type WorkspaceLocator =
  | { kind: 'department' | 'team'; workspaceId: string }
  | { kind: 'shared' }

export interface ParsedWorkspaceUrl {
  section: SidebarSectionKind
  /** department/team URL은 workspaceId 보유, shared는 null. */
  workspaceId: string | null
  /** 경로에 folderId가 있을 때만 string. workspace landing(/d/:id 만)에서는 null. */
  folderId: string | null
  slugPath: string[]
}

const SECTION_PREFIX: Record<SidebarSectionKind, string> = {
  department: 'd',
  team: 't',
  shared: 'shared',
}

export function buildWorkspacePath(
  loc: WorkspaceLocator,
  folderId: string | null,
  slugPath: string[],
): string {
  const head =
    loc.kind === 'shared'
      ? `/${SECTION_PREFIX.shared}`
      : `/${SECTION_PREFIX[loc.kind]}/${encodeURIComponent(loc.workspaceId)}`

  const folderSegment = folderId ? `/${encodeURIComponent(folderId)}` : ''
  const slugSegment = slugPath.length
    ? '/' + slugPath.map(encodeURIComponent).join('/')
    : ''
  return `${head}${folderSegment}${slugSegment}`
}

export function parseWorkspaceUrl(pathname: string): ParsedWorkspaceUrl | null {
  // pathname always starts with '/'
  const segs = pathname.split('/').filter(Boolean)
  if (segs.length === 0) return null

  if (segs[0] === 'd' && segs.length >= 2) {
    return {
      section: 'department',
      workspaceId: decodeURIComponent(segs[1]),
      folderId: segs[2] ? decodeURIComponent(segs[2]) : null,
      slugPath: segs.slice(3).map(decodeURIComponent),
    }
  }
  if (segs[0] === 't' && segs.length >= 2) {
    return {
      section: 'team',
      workspaceId: decodeURIComponent(segs[1]),
      folderId: segs[2] ? decodeURIComponent(segs[2]) : null,
      slugPath: segs.slice(3).map(decodeURIComponent),
    }
  }
  if (segs[0] === 'shared') {
    return {
      section: 'shared',
      workspaceId: null,
      folderId: segs[1] ? decodeURIComponent(segs[1]) : null,
      slugPath: segs.slice(2).map(decodeURIComponent),
    }
  }
  return null
}
