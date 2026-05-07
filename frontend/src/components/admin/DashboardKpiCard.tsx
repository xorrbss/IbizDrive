/**
 * Dashboard KPI 단일 카드 (admin-dashboard 트랙).
 *
 * <p>label + 큰 수치 + (선택) sublabel(작은 보조 수치). 8개 KPI에 동일 모양으로 재사용.
 * 상호작용 없음 — 순수 표시 컴포넌트. 색상/배경은 admin landing의 카드 스타일 mirror.
 */
export interface DashboardKpiCardProps {
  label: string
  value: string | number
  /** 보조 라벨 (예: "활성 10/12"). 없으면 미표시. */
  sub?: string
}

export function DashboardKpiCard({ label, value, sub }: DashboardKpiCardProps) {
  return (
    <div className="p-4 rounded border border-border bg-surface-1">
      <div className="text-[12px] text-fg-2 mb-2">{label}</div>
      <div className="text-[24px] font-semibold tabular-nums text-fg leading-none">
        {value}
      </div>
      {sub ? <div className="text-[11px] text-fg-muted mt-2">{sub}</div> : null}
    </div>
  )
}
