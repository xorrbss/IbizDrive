import { formatTBGB } from '@/lib/admin/overviewMock'

/**
 * 업로드 추이 SVG 차트 — 디자인 핸드오프 2026-05-10 admin.jsx §UploadChart
 * (L230~275) 1:1 매핑.
 *
 * <p>최근 28일 일별 업로드량(byte) 배열을 입력으로 받아 line + area + grid + axis 4가지를
 * 720x140 SVG viewBox 에 렌더. accent 컬러는 `var(--accent)` 사용 (라이트/다크 동일 토큰).
 * Y축 0/50/100% 3개 grid line + bytes 라벨, X축 D-27 / D-21 / D-14 / D-7 / 오늘 5개 tick.
 *
 * <p>SVG 좌표 계산은 design 코드 그대로 답습 — `pad`, `step`, `points`, `area` 식 모두
 * 동일. 시각 fidelity 가 목적이므로 데이터/스케일 변경 없이 그대로 포팅한다.
 *
 * <p>style: `.chart-svg`, `.chart-grid`, `.chart-axis-text` (admin.css L254~256).
 *
 * <p>backend wiring: v1.x backlog. `GET /api/admin/metrics/uploads?days=28` 합류 시
 * `data` prop 만 hook 결과로 교체하면 된다 (display-only 컴포넌트).
 */
export interface UploadChartProps {
  /** 일별 업로드 byte 합계 — 최근 N일 (overview 는 N=28). 최소 길이 2. */
  data: readonly number[]
}

export function UploadChart({ data }: UploadChartProps) {
  if (data.length < 2) {
    return <div className="empty-mini">데이터 없음</div>
  }

  const max = Math.max(...data)
  const w = 720
  const h = 140
  const pad = { l: 32, r: 8, t: 8, b: 22 }
  const innerW = w - pad.l - pad.r
  const innerH = h - pad.t - pad.b
  const step = innerW / (data.length - 1)

  const points: Array<[number, number]> = data.map((v, i) => [
    pad.l + step * i,
    pad.t + innerH - (v / max) * innerH,
  ])
  const path = points
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${p[0].toFixed(1)},${p[1].toFixed(1)}`)
    .join(' ')
  const last = points[points.length - 1]
  const area = `${path} L${last[0]},${pad.t + innerH} L${pad.l},${pad.t + innerH} Z`

  // Y axis ticks (0 / 50% / 100%)
  const ticks = [0, 0.5, 1].map((t) => ({
    y: pad.t + innerH - innerH * t,
    label: t === 0 ? '0' : formatTBGB(max * t).replace('.0 ', ' '),
  }))

  // X axis labels (D-27, D-21, D-14, D-7, 오늘)
  const xLabels = [0, 7, 14, 21, 27]

  return (
    <svg
      className="chart-svg"
      viewBox={`0 0 ${w} ${h}`}
      preserveAspectRatio="none"
      role="img"
      aria-label="최근 28일 업로드 추이"
    >
      {ticks.map((t, i) => (
        <g key={i}>
          <line x1={pad.l} y1={t.y} x2={w - pad.r} y2={t.y} className="chart-grid" />
          <text x={pad.l - 6} y={t.y + 3} textAnchor="end" className="chart-axis-text">
            {t.label}
          </text>
        </g>
      ))}
      <path d={area} fill="url(#chartGrad)" />
      <path d={path} fill="none" stroke="var(--accent)" strokeWidth="1.5" />
      {points.map((p, i) =>
        i % 4 === (data.length - 1) % 4 ? (
          <circle key={i} cx={p[0]} cy={p[1]} r="2" fill="var(--accent)" />
        ) : null,
      )}
      {xLabels.map((i) => (
        <text
          key={i}
          x={pad.l + step * i}
          y={h - 6}
          textAnchor="middle"
          className="chart-axis-text"
        >
          {i === 27 ? '오늘' : `D-${27 - i}`}
        </text>
      ))}
      <defs>
        <linearGradient id="chartGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.18" />
          <stop offset="100%" stopColor="var(--accent)" stopOpacity="0" />
        </linearGradient>
      </defs>
    </svg>
  )
}
