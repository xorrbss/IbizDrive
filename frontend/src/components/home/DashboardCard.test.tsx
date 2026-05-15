import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { DashboardCard } from './DashboardCard'

describe('DashboardCard', () => {
  it('title + body 렌더 + outer section 배경 토큰 가드', () => {
    // 회귀 가드 — invalid Tailwind token (`bg-bg-1`) 재발 방지.
    // globals.css `@theme inline` 은 `--color-surface-1` 만 노출하고 `--color-bg-1` 은 미정의.
    // PR #267 systemic sweep 이후 DashboardCard 가 4 dashboard 위젯(Welcome 제외)의 wrapper 라
    // 단일 가드로 4 위젯 배경을 동시에 보호.
    const { container, getByText } = render(
      <DashboardCard title="내 저장공간" subtitle="할당량 대비 사용량">
        <div>body</div>
      </DashboardCard>,
    )
    expect(getByText('내 저장공간')).toBeTruthy()
    expect(getByText('할당량 대비 사용량')).toBeTruthy()
    const section = container.querySelector('section') as HTMLElement
    expect(section).not.toBeNull()
    expect(section.className).toContain('bg-surface-1')
    expect(section.className).not.toMatch(/\bbg-bg-\d/)
  })
})
