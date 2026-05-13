'use client'
import { WelcomeHeader } from './WelcomeHeader'
import { StarredCard } from './StarredCard'
import { QuotaCard } from './QuotaCard'
import { SharedWithMeCard } from './SharedWithMeCard'

/**
 * User Home Dashboard 컨테이너 — root `/` 의 personal entry.
 *
 * <p>레이아웃 (위에서 아래):
 * <ul>
 *   <li>WelcomeHeader (이름 + workspace 메타)</li>
 *   <li>StarredCard 50% · QuotaCard 50% (md 이상 2열, 모바일 가정 외)</li>
 *   <li>SharedWithMeCard 풀폭</li>
 * </ul>
 *
 * <p>사이드바/TopBar 는 (explorer) layout reuse — workspace 진입은 사이드바 트리.
 */
export function HomeDashboard() {
  return (
    <div className="flex-1 overflow-auto p-6">
      <div className="mx-auto flex max-w-[1400px] flex-col gap-4">
        <WelcomeHeader />
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <StarredCard />
          <QuotaCard />
        </div>
        <SharedWithMeCard />
      </div>
    </div>
  )
}
