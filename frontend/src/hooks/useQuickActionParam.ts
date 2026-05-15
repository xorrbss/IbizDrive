'use client'
import { useEffect, useState } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'

/**
 * Dashboard quick action `?action=new-folder` query 감지 + 1-shot consume.
 *
 * <p>WelcomeHeader 의 "새 폴더" 버튼 → `router.push(workspaceRoot?action=new-folder)`.
 * Explorer page (`ClientFilesPage`) 가 본 hook 으로 query 감지 → `CreateFolderDialog` mount.
 * mount 시점에 `router.replace` 로 `?action` 만 제거(다른 query 보존) → URL re-trigger 차단.
 *
 * <p>folderId 가 비어있는 동안(workspace landing → root redirect 중) hook 은 무동작.
 * redirect 완료 후 folderId 채워지면 effect 재실행.
 *
 * <p>spec: `docs/superpowers/specs/2026-05-15-quick-action-dialog-design.md` §5.
 */
export function useQuickActionParam(folderId: string) {
  const router = useRouter()
  const pathname = usePathname()
  const params = useSearchParams()
  const action = params.get('action')

  const [newFolderOpen, setNewFolderOpen] = useState(false)

  useEffect(() => {
    if (action === 'new-folder' && folderId.length > 0) {
      setNewFolderOpen(true)
      const next = new URLSearchParams(params)
      next.delete('action')
      const qs = next.toString()
      router.replace(qs ? `${pathname}?${qs}` : pathname)
    }
  }, [action, folderId, pathname, params, router])

  return {
    newFolderOpen,
    closeNewFolder: () => setNewFolderOpen(false),
  }
}
