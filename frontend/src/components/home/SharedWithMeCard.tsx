'use client'
import Link from 'next/link'
import { DashboardCard } from './DashboardCard'
import { useMySharedWithMe } from '@/hooks/useMySharedWithMe'

/**
 * User Home Dashboard ④ — 공유받은 항목 (USER subject direct grant) 5건.
 *
 * <p>backend `GET /api/me/shared-with-me?limit=5`. department/role/everyone indirect grant 제외.
 *
 * <p>row 표시: name + preset chip + grantedBy.name. row 클릭 시 resource URL 이동은 follow-up
 * (workspace/folderPath resolve 필요).
 */
export function SharedWithMeCard() {
  const { data, isLoading, isError } = useMySharedWithMe(5)
  const items = data?.items ?? []

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
            <li
              key={it.permissionId}
              className="flex items-center justify-between py-1 text-[13px]"
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
            </li>
          ))}
        </ul>
      )}
    </DashboardCard>
  )
}
