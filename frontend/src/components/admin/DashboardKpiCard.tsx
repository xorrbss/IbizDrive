/**
 * Dashboard KPI 단일 카드 — 디자인 핸드오프 2026-05-10 admin.jsx §KPICard
 * (L184~210) 1:1 매핑 (Phase 3e fidelity 보강).
 *
 * <p>props:
 * <ul>
 *   <li><code>label</code> — 카드 라벨 (uppercase, kpi-label).</li>
 *   <li><code>value</code> — 큰 수치 (kpi-value).</li>
 *   <li><code>sub</code> — 보조 라벨 (kpi-meta 내).</li>
 *   <li><code>delta</code> — 증감비 (0.124 = +12.4%). 양수면 ↑+초록, 음수면
 *     ↓+빨강. backend AdminDashboardSummary 가 아직 delta 컬럼을 노출하지
 *     않으므로 본 prop 은 후속 wiring 용 (optional). 현재 KISS — mock 산출
 *     금지.</li>
 *   <li><code>tone</code> — 카드 강조 (warn/danger/primary). admin.css 의
 *     .kpi-card.tone-{tone} 모디파이어.</li>
 *   <li><code>progress</code> — 0~1 범위의 진행률. 0보다 크면 kpi-bar 노출.</li>
 * </ul>
 *
 * <p>이전 tailwind 유틸 (p-4/rounded/border-border) 은 admin.css 의 .kpi-card
 * 클래스로 교체 — 디자인 토큰(--surface-1/--border/--radius-lg) 1:1.
 */
export interface DashboardKpiCardProps {
  label: string
  value: string | number
  /** 보조 라벨 (예: "활성 10/12"). 없으면 미표시. */
  sub?: string
  /** 증감비 (-1 ~ 1). 양수=↑green, 음수=↓red. */
  delta?: number
  /** 카드 톤 (admin.css .kpi-card.tone-{warn|danger|primary}). */
  tone?: 'warn' | 'danger' | 'primary'
  /** 진행률 (0 ~ 1). >0 이면 kpi-bar 노출. */
  progress?: number
}

export function DashboardKpiCard({
  label,
  value,
  sub,
  delta,
  tone,
  progress,
}: DashboardKpiCardProps) {
  const isUp = delta != null && delta > 0
  const isDown = delta != null && delta < 0
  const classes = ['kpi-card']
  if (tone) classes.push(`tone-${tone}`)

  return (
    <div className={classes.join(' ')}>
      <div className="kpi-head">
        <span className="kpi-label">{label}</span>
      </div>
      <div className="kpi-value">{value}</div>
      {(sub || delta != null) && (
        <div className="kpi-meta">
          {delta != null && (
            <span
              className={`kpi-delta ${isUp ? 'up' : isDown ? 'down' : ''}`}
              aria-label={`전월 대비 ${isUp ? '증가' : isDown ? '감소' : '동일'}`}
            >
              {isUp ? '↑' : isDown ? '↓' : '·'} {formatDelta(delta)}
            </span>
          )}
          {sub && <span className="kpi-sub">{sub}</span>}
        </div>
      )}
      {progress != null && progress > 0 && (
        <div className="kpi-bar" aria-hidden="true">
          <div style={{ width: `${Math.min(progress, 1) * 100}%` }} />
        </div>
      )}
    </div>
  )
}

/** `formatDelta(0.124)` → "+12.4%", `formatDelta(-0.018)` → "-1.8%". */
function formatDelta(n: number): string {
  if (!Number.isFinite(n)) return '-'
  const pct = n * 100
  const sign = pct > 0 ? '+' : ''
  return `${sign}${pct.toFixed(1)}%`
}
