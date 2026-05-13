import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Users, HardDrive } from 'lucide-react'
import { DashboardKpiCard } from './DashboardKpiCard'

/**
 * DashboardKpiCard — props별 단위 가드.
 *
 * 디자인 §KPICard L184 1:1 매핑 검증. delta/tone/progress는 기존 동작 회귀 보호,
 * icon은 신규 prop의 SVG 렌더 가드.
 */
describe('DashboardKpiCard', () => {
  it('icon prop 미지정 → kpi-head에 SVG 미렌더', () => {
    const { container } = render(<DashboardKpiCard label="등록 사용자" value={12} />)
    expect(container.querySelector('.kpi-head svg')).toBeNull()
  })

  it('icon prop 지정 → kpi-head 내부에 SVG 렌더 + width/height=13 + aria-hidden', () => {
    const { container } = render(
      <DashboardKpiCard label="등록 사용자" value={12} icon={Users} />,
    )
    const svg = container.querySelector('.kpi-head svg')
    expect(svg).toBeTruthy()
    // lucide-react는 width/height attribute로 노출
    expect(svg?.getAttribute('width')).toBe('13')
    expect(svg?.getAttribute('height')).toBe('13')
    expect(svg?.getAttribute('aria-hidden')).toBe('true')
  })

  it('icon prop은 라벨 다음으로 렌더 — kpi-label과 같은 head 컨테이너 내부', () => {
    // 디자인 §KPICard L192-195: <span className="kpi-label">{label}</span> 다음에 icon 위치.
    const { container } = render(
      <DashboardKpiCard label="스토리지 사용량" value="1.5 GB" icon={HardDrive} />,
    )
    const head = container.querySelector('.kpi-head')
    expect(head).toBeTruthy()
    const children = Array.from(head!.children)
    expect(children[0].classList.contains('kpi-label')).toBe(true)
    expect(children[1].tagName.toLowerCase()).toBe('svg')
  })

  it('value/sub/label 기본 렌더 (회귀 가드)', () => {
    render(
      <DashboardKpiCard label="등록 사용자" value={12} sub="활성 10/12" />,
    )
    expect(screen.getByText('등록 사용자')).toBeTruthy()
    expect(screen.getByText('12')).toBeTruthy()
    expect(screen.getByText('활성 10/12')).toBeTruthy()
  })

  it('delta>0 → ↑ + 양수 라벨 + up class', () => {
    const { container } = render(
      <DashboardKpiCard label="활성 사용자" value={10} delta={0.124} />,
    )
    const delta = container.querySelector('.kpi-delta')
    expect(delta?.classList.contains('up')).toBe(true)
    expect(delta?.textContent).toContain('↑')
    expect(delta?.textContent).toContain('+12.4%')
  })

  it('delta<0 → ↓ + 음수 라벨 + down class', () => {
    const { container } = render(
      <DashboardKpiCard label="휴지통 파일" value={3} delta={-0.018} />,
    )
    const delta = container.querySelector('.kpi-delta')
    expect(delta?.classList.contains('down')).toBe(true)
    expect(delta?.textContent).toContain('↓')
    expect(delta?.textContent).toContain('-1.8%')
  })

  it('tone="primary" → kpi-card.tone-primary class', () => {
    const { container } = render(
      <DashboardKpiCard label="스토리지" value="1.5 GB" tone="primary" />,
    )
    expect(container.querySelector('.kpi-card.tone-primary')).toBeTruthy()
  })

  it('progress > 0 → kpi-bar 렌더 + width style', () => {
    const { container } = render(
      <DashboardKpiCard label="스토리지" value="1.5 GB" progress={0.42} />,
    )
    const bar = container.querySelector('.kpi-bar > div')
    expect((bar as HTMLElement | null)?.style.width).toBe('42%')
  })

  it('progress === 0 → kpi-bar 미렌더 (가드 ` > 0`)', () => {
    const { container } = render(
      <DashboardKpiCard label="스토리지" value="0 B" progress={0} />,
    )
    expect(container.querySelector('.kpi-bar')).toBeNull()
  })
})
