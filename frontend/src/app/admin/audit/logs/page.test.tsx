import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

/**
 * /admin/audit/logs — Wave 1 T2 server-side export + audit-export-json 트랙 (format 분기).
 *
 * <p>핵심 가드:
 * <ul>
 *   <li>"CSV 내보내기" / "JSON 내보내기" 두 anchor (`<a>`) 모두 href가 `/api/admin/audit/export?`로 시작</li>
 *   <li>각 anchor는 `format=csv` / `format=json`을 query에 포함</li>
 *   <li>현재 적용된 필터(eventType 등)가 두 anchor 모두에 그대로 반영</li>
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

describe('/admin/audit/logs — server-side export (csv + json)', () => {
  it('CSV anchor가 format=csv를 포함', () => {
    wrap(<AuditLogsPage />)
    const link = screen.getByTestId('audit-export-link-csv') as HTMLAnchorElement
    expect(link.tagName).toBe('A')
    expect(link.getAttribute('href')).toContain('/api/admin/audit/export?')
    expect(link.getAttribute('href')).toContain('format=csv')
    // download 속성이 비어 있어야 backend Content-Disposition filename을 우선시.
    expect(link.getAttribute('download')).toBe('')
  })

  it('JSON anchor가 format=json을 포함', () => {
    wrap(<AuditLogsPage />)
    const link = screen.getByTestId('audit-export-link-json') as HTMLAnchorElement
    expect(link.tagName).toBe('A')
    expect(link.getAttribute('href')).toContain('/api/admin/audit/export?')
    expect(link.getAttribute('href')).toContain('format=json')
    expect(link.getAttribute('download')).toBe('')
  })

  it('필터 적용 후 두 anchor 모두 query에 필터 + 각자 format 반영', () => {
    wrap(<AuditLogsPage />)
    const select = document.querySelector('select') as HTMLSelectElement
    expect(select).not.toBeNull()
    fireEvent.change(select, { target: { value: 'user.login.failed' } })

    const csv = screen.getByTestId('audit-export-link-csv').getAttribute('href')!
    const json = screen.getByTestId('audit-export-link-json').getAttribute('href')!
    expect(csv).toContain('eventType=user.login.failed')
    expect(csv).toContain('format=csv')
    expect(json).toContain('eventType=user.login.failed')
    expect(json).toContain('format=json')
  })

  it('does not invoke fetch on click — relies on browser navigation', () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('ok'))
    wrap(<AuditLogsPage />)
    const csv = screen.getByTestId('audit-export-link-csv')
    const json = screen.getByTestId('audit-export-link-json')
    fireEvent.click(csv)
    fireEvent.click(json)
    expect(fetchSpy).not.toHaveBeenCalled()
    fetchSpy.mockRestore()
  })
})
