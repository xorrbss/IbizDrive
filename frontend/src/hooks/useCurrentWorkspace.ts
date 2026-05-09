'use client'
import { usePathname } from 'next/navigation'
import { parseWorkspaceUrl, type ParsedWorkspaceUrl } from '@/lib/workspacePath'

/**
 * URL → 현재 workspace 컨텍스트 파생.
 * - URL이 진실 (CLAUDE.md §3 원칙 1, spec §5.1).
 * - workspace 외 라우트(/admin/*, /login, /trash …)에서는 null 반환.
 */
export function useCurrentWorkspace(): ParsedWorkspaceUrl | null {
  const pathname = usePathname()
  return parseWorkspaceUrl(pathname ?? '/')
}
