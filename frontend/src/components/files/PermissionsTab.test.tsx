import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

// api 모킹 — getEffectivePermissions (usePermission 내부) + listResourcePermissions
// (M8.1 ResourcePermissionsList 내부) 둘 다 사용.
const getEffectivePermissionsMock = vi.fn()
const listResourcePermissionsMock = vi.fn()
vi.mock('@/lib/api', () => ({
  api: {
    getEffectivePermissions: (...args: unknown[]) =>
      getEffectivePermissionsMock(...args),
    listResourcePermissions: (...args: unknown[]) =>
      listResourcePermissionsMock(...args),
  },
}))

import { PermissionsTab } from './PermissionsTab'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>{node}</QueryClientProvider>,
  )
}

describe('PermissionsTab — M-RP.3', () => {
  beforeEach(() => {
    getEffectivePermissionsMock.mockReset()
    listResourcePermissionsMock.mockReset()
    listResourcePermissionsMock.mockResolvedValue([])
  })

  it('9개 권한 chip을 모두 렌더한다', async () => {
    getEffectivePermissionsMock.mockResolvedValue([])
    wrap(<PermissionsTab fileId="file_a" />)
    await waitFor(() => {
      expect(getEffectivePermissionsMock).toHaveBeenCalled()
    })
    const list = await screen.findByLabelText('파일 권한 목록')
    expect(list.querySelectorAll('li').length).toBe(9)
  })

  it('보유 권한은 data-held=true, 미보유는 false로 시각 구분', async () => {
    getEffectivePermissionsMock.mockResolvedValue(['READ', 'DOWNLOAD'])
    wrap(<PermissionsTab fileId="file_a" />)

    await waitFor(() => {
      const read = document.querySelector('[data-permission="READ"]')
      expect(read?.getAttribute('data-held')).toBe('true')
    })

    expect(
      document
        .querySelector('[data-permission="DOWNLOAD"]')
        ?.getAttribute('data-held'),
    ).toBe('true')
    expect(
      document
        .querySelector('[data-permission="EDIT"]')
        ?.getAttribute('data-held'),
    ).toBe('false')
    expect(
      document
        .querySelector('[data-permission="PURGE"]')
        ?.getAttribute('data-held'),
    ).toBe('false')
  })

  it('aria-label로 보유/미보유 상태 노출', async () => {
    getEffectivePermissionsMock.mockResolvedValue(['SHARE'])
    wrap(<PermissionsTab fileId="file_a" />)
    await waitFor(() => {
      expect(screen.getByLabelText('공유 권한 보유')).toBeTruthy()
    })
    expect(screen.getByLabelText('읽기 권한 미보유')).toBeTruthy()
  })

  it('로딩 중에는 모든 chip이 미보유(보수적 디폴트)', async () => {
    // never-resolving promise to keep query pending
    getEffectivePermissionsMock.mockImplementation(
      () => new Promise(() => {}),
    )
    wrap(<PermissionsTab fileId="file_a" />)

    const list = await screen.findByLabelText('파일 권한 목록')
    const chips = list.querySelectorAll('li')
    expect(chips.length).toBe(9)
    chips.forEach((c) => {
      expect(c.getAttribute('data-held')).toBe('false')
    })
  })

  // ─── M8.1 list integration ───────────────────────────────────
  it('PERMISSION_ADMIN 미보유 → chip 만 렌더, 권한 부여 목록 섹션 미렌더', async () => {
    getEffectivePermissionsMock.mockResolvedValue(['READ', 'DOWNLOAD'])
    wrap(<PermissionsTab fileId="file_a" />)
    await waitFor(() => {
      expect(getEffectivePermissionsMock).toHaveBeenCalled()
    })
    // listResourcePermissions 가 fetch 되지 않아야 함 (admin 가드)
    await new Promise((r) => setTimeout(r, 30))
    expect(listResourcePermissionsMock).not.toHaveBeenCalled()
    expect(
      document.querySelector('section[aria-label="권한 부여 목록"]'),
    ).toBeNull()
    // chip 은 정상 렌더
    expect(screen.getByLabelText('파일 권한 목록')).toBeTruthy()
  })

  it('PERMISSION_ADMIN 보유 → chip + 권한 부여 목록 공존', async () => {
    getEffectivePermissionsMock.mockResolvedValue(['PERMISSION_ADMIN', 'READ'])
    listResourcePermissionsMock.mockResolvedValue([
      {
        id: 'p1',
        resourceType: 'file',
        resourceId: 'file_a',
        subjectType: 'user',
        subjectId: 'u1',
        preset: 'admin',
        grantedBy: 'admin1',
        expiresAt: null,
        createdAt: '2026-05-01T00:00:00Z',
        subjectName: 'Alice',
      },
    ])
    wrap(<PermissionsTab fileId="file_a" />)
    // chip 확인
    expect(await screen.findByLabelText('파일 권한 목록')).toBeTruthy()
    // list 확인 — grant row 의 subjectName 등장
    await waitFor(() => {
      expect(screen.getByText('Alice')).toBeTruthy()
    })
    expect(listResourcePermissionsMock).toHaveBeenCalledWith('file', 'file_a')
  })
})
