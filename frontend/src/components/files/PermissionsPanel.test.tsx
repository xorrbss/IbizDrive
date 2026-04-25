import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const getNodePermissionGrantsMock = vi.fn()
vi.mock('@/lib/api', () => ({
  api: { getNodePermissionGrants: (...args: unknown[]) => getNodePermissionGrantsMock(...args) },
}))

import { PermissionsPanel } from './PermissionsPanel'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

describe('PermissionsPanel', () => {
  beforeEach(() => {
    getNodePermissionGrantsMock.mockReset()
  })

  it('grants 로딩 후 사용자/그룹 + 역할 표시', async () => {
    getNodePermissionGrantsMock.mockResolvedValue([
      { id: 'g1', subjectType: 'user', subjectName: '나', role: 'owner', inherited: false },
      { id: 'g2', subjectType: 'group', subjectName: '개발팀', role: 'editor', inherited: true },
    ])
    wrap(<PermissionsPanel fileId="file_abc" />)
    await waitFor(() => expect(screen.getByText('나')).toBeTruthy())
    expect(screen.getByText('개발팀')).toBeTruthy()
    expect(screen.getByText('소유자')).toBeTruthy()
    expect(screen.getByText('편집')).toBeTruthy()
    expect(screen.getByText('상속됨')).toBeTruthy()
  })

  it('grants 비어있으면 안내 문구', async () => {
    getNodePermissionGrantsMock.mockResolvedValue([])
    wrap(<PermissionsPanel fileId="file_abc" />)
    await waitFor(() => expect(screen.getByText(/부여된 권한이 없습니다/)).toBeTruthy())
  })

  it('error 시 alert', async () => {
    getNodePermissionGrantsMock.mockRejectedValue(new Error('boom'))
    wrap(<PermissionsPanel fileId="file_abc" />)
    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy())
  })
})
