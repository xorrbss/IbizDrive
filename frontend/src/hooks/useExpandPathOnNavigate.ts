'use client'
import { useEffect } from 'react'
import { useCurrentFolder } from './useCurrentFolder'
import { useSidebarTreeStore } from '@/stores/sidebarTree'

/**
 * URL 변경 → breadcrumb의 모든 ancestor 폴더 ID를 expand.
 * - workspace root 자체는 expand 대상 아님 (항상 visible).
 * - breadcrumb은 useCurrentFolder가 backend에서 조립한 단일 진실.
 */
export function useExpandPathOnNavigate() {
  const { breadcrumb } = useCurrentFolder()
  const expandFolder = useSidebarTreeStore((s) => s.expandFolder)

  useEffect(() => {
    if (!breadcrumb || breadcrumb.length === 0) return
    // breadcrumb 마지막 entry는 현재 폴더 자체 — expand 불필요.
    // breadcrumb[0]은 workspace root — 항상 visible이므로 굳이 expand 안 함.
    for (const crumb of breadcrumb.slice(0, -1)) {
      expandFolder(crumb.id)
    }
  }, [breadcrumb, expandFolder])
}
