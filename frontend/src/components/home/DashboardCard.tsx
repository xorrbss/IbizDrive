import type { ReactNode } from 'react'

/**
 * User Home Dashboard 위젯 공통 카드 — `<SectionCard>` (admin) 와 동일한 시각이지만 dashboard 전용
 * tailwind 인라인 구현으로 admin.css 와 결합 회피 (dashboard 페이지가 admin.css 를 import 하지 않게).
 *
 * 4 위젯 (Welcome 제외 3개) 이 reuse. body 영역은 children 자유.
 */
export interface DashboardCardProps {
  title: string
  subtitle?: string
  right?: ReactNode
  children: ReactNode
}

export function DashboardCard({ title, subtitle, right, children }: DashboardCardProps) {
  return (
    <section className="rounded-lg border border-border bg-bg-1 p-4">
      <header className="mb-3 flex items-start justify-between gap-2">
        <div>
          <h2 className="text-[14px] font-semibold text-fg">{title}</h2>
          {subtitle && <p className="text-[12px] text-fg-2 mt-0.5">{subtitle}</p>}
        </div>
        {right}
      </header>
      <div>{children}</div>
    </section>
  )
}
