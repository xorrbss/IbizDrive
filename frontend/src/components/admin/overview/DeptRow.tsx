import { type AdminDeptUsage, formatPct, formatTBGB } from '@/lib/admin/overviewMock'

/**
 * 부서별 저장공간 row — 디자인 핸드오프 2026-05-10 admin.jsx §DeptRow
 * (L519~538) 1:1 매핑.
 *
 * <p>3-row vertical 레이아웃:
 * <ul>
 *   <li>head: legend dot + 부서명 + 멤버 수 + 우측 사용 %</li>
 *   <li>bar: 사용 비율 progress bar (부서 색상)</li>
 *   <li>foot: "used / quota" 텍스트</li>
 * </ul>
 *
 * <p>85% 초과 시 `.dept-pct.over` 적용 (admin.css L395 — danger 컬러). pct 계산은
 * `used / quota`, design 그대로.
 *
 * <p>style: `.dept-row`, `.dept-row-head/-foot`, `.dept-name`, `.dept-mem`,
 * `.dept-pct`/`.over`, `.dept-bar`, `.legend-dot` (admin.css L389~402, L427).
 */
export interface DeptRowProps {
  dept: AdminDeptUsage
}

export function DeptRow({ dept }: DeptRowProps) {
  const pct = dept.used / dept.quota
  const overload = pct > 0.85

  return (
    <div className="dept-row">
      <div className="dept-row-head">
        <span
          className="legend-dot"
          style={{ background: dept.color }}
          aria-hidden="true"
        />
        <span className="dept-name">{dept.name}</span>
        <span className="dept-mem">{dept.members}명</span>
        <span className={`dept-pct ${overload ? 'over' : ''}`}>{formatPct(pct, 0)}</span>
      </div>
      <div className="dept-bar">
        <div style={{ width: `${pct * 100}%`, background: dept.color }} />
      </div>
      <div className="dept-row-foot">
        <span>
          {formatTBGB(dept.used)} / {formatTBGB(dept.quota)}
        </span>
      </div>
    </div>
  )
}
