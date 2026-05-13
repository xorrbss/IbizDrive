'use client'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { DashboardCard } from './DashboardCard'
import { useMySharedWithMe } from '@/hooks/useMySharedWithMe'
import { buildWorkspacePath } from '@/lib/workspacePath'
import type { MySharedWithMeItem } from '@/types/dashboard'

/**
 * User Home Dashboard ④ — 공유받은 항목 (USER subject direct grant) 5건.
 *
 * <p>backend `GET /api/me/shared-with-me?limit=5`. department/role/everyone indirect grant 제외.
 *
 * <p>row 클릭 시 follow-up (2026-05-14): file → `buildWorkspacePath(workspace, file.folder_id) + ?file=resourceId`,
 * folder → `buildWorkspacePath(workspace, folder.id)`. 권한 가드는 explorer 페이지가 자체 처리
 * (sharer 워크스페이스 비멤버라도 grant 보유 시 진입 가능 — backend evaluator).
 */
export function SharedWithMeCard() {
  const router = useRouter()
  const { data, isLoading, isError } = useMySharedWithMe(5)
  const items: MySharedWithMeItem[] = data?.items ?? []

  function navigateTo(it: MySharedWithMeItem) {
    const url = buildWorkspacePath(
      { kind: it.workspace.kind, workspaceId: it.workspace.id },
      it.navigationFolderId,
      [],
    )
    router.push(it.resourceType === 'file' ? `${url}?file=${it.resourceId}` : url)
  }

  return (
    <DashboardCard
      title="공유받은 항목"
      subtitle="다른 사용자가 직접 공유한 파일/폴더"
      right={
        <Link href="/shared" className="text-[12px] text-fg-2 hover:text-fg">
          전체 보기 →
        </Link>
      }
    >
      {isLoading && <div className="text-[13px] text-fg-muted">불러오는 중…</div>}
      {isError && (
        <div className="text-[13px] text-fg-muted">공유 목록을 불러올 수 없습니다.</div>
      )}
      {!isLoading && !isError && items.length === 0 && (
        <div className="text-[13px] text-fg-muted">공유받은 항목이 없습니다.</div>
      )}
      {items.length > 0 && (
        <ul role="list" className="space-y-1">
          {items.map((it) => (
            <li key={it.permissionId} className="text-[13px]">
              <button
                type="button"
                onClick={() => navigateTo(it)}
                className="flex w-full items-center justify-between gap-2 py-1 px-1 -mx-1 rounded hover:bg-bg-2 text-left"
                aria-label={`${it.name} 열기`}
              >
                <div className="flex items-center gap-2 min-w-0 flex-1">
                  <span className="truncate text-fg">{it.name}</span>
                  <span className="text-[11px] px-1.5 py-0.5 rounded bg-bg-2 text-fg-2 shrink-0">
                    {it.preset}
                  </span>
                </div>
                <span className="text-[12px] text-fg-muted shrink-0 ml-2">
                  {it.grantedBy.name}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </DashboardCard>
  )
}
