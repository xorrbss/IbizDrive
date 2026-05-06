import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

/**
 * /admin/audit/logs — Wave 1 — T2 server-side export 동작 검증.
 *
 * <p>핵심 가드:
 * <ul>
 *   <li>"CSV 내보내기" 버튼은 anchor element(`<a>`)이며 href가 `/api/admin/audit/export`로 시작</li>
 *   <li>현재 적용된 필터(eventType, fromDate, toDate, actorQuery)가 query string으로 동봉</li>
 *   <li>다운로드 트리거에 fetch/axios 호출이 발생하지 않음 — 브라우저 navigation 위임</li>
 * </ul>
 *
 * <p>useAuditLogs는 mock — table/pagination 렌더 자체가 아닌 export 링크만 검증.
 */

vi.mock('@/hooks/useAuditLogs', () => ({
  useAuditLogs: () => ({
    data: { entries: [], total: 0, page: 1, pageSize: 20 },
    isLoading: false,
    isError: false,
  }),
}))

import AuditLogsPage from './page'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

describe('/admin/audit/logs — server-side CSV export', () => {
  it('renders export link with default URL when no filters applied', () => {
    wrap(<AuditLogsPage />)
    const link = screen.getByTestId('audit-export-link') as HTMLAnchorElement
    expect(link.tagName).toBe('A')
    expect(link.getAttribute('href')).toBe('/api/admin/audit/export')
    // download 속성이 비어 있어야 backend Content-Disposition filename을 우선시.
    expect(link.getAttribute('download')).toBe('')
  })

  it('reflects current eventType filter into export URL', () => {
    wrap(<AuditLogsPage />)
    // 필터 UI를 통해 eventType을 변경. AuditFilters는 select 한 개 + 날짜 input들로 구성됨.
    // accessible name이 잡히지 않으면 select 첫 번째를 직접 가져와 변경.
    const select = document.querySelector('select') as HTMLSelectElement
    expect(select).not.toBeNull()
    fireEvent.change(select, { target: { value: 'user.login.failed' } })

    const link = screen.getByTestId('audit-export-link') as HTMLAnchorElement
    expect(link.getAttribute('href')).toBe(
      '/api/admin/audit/export?eventType=user.login.failed',
    )
  })

  it('does not invoke fetch on click — relies on browser navigation', () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('ok'))
    wrap(<AuditLogsPage />)
    const link = screen.getByTestId('audit-export-link')
    // jsdom은 navigation을 실제 수행하지 않지만, fetch가 호출되지 않는다는 사실로 충분.
    fireEvent.click(link)
    expect(fetchSpy).not.toHaveBeenCalled()
    fetchSpy.mockRestore()
  })
})
