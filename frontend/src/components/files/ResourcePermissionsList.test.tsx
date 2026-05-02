import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

/**
 * M8.1 — ResourcePermissionsList. 본 테스트는:
 *  1) PERMISSION_ADMIN 미보유 시 컴포넌트 자체가 미렌더 (UX 가드)
 *  2) admin 보유 시 4상태 (loading/error/empty/data) 분기 렌더
 *  3) subjectName 우선 / everyone fallback / 미해결 fallback
 *  4) preset 라벨 + grid aria 구조
 *
 * usePermission 내부 fetch 와 useResourcePermissions 내부 fetch 가 모두 발생하므로
 * api 전체를 mock — 동일 파일에 PermissionsTab.test.tsx 패턴 답습.
 */

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

import { ResourcePermissionsList } from './ResourcePermissionsList'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>{node}</QueryClientProvider>,
  )
}

describe('ResourcePermissionsList (M8.1)', () => {
  beforeEach(() => {
    getEffectivePermissionsMock.mockReset()
    listResourcePermissionsMock.mockReset()
  })

  it('PERMISSION_ADMIN 미보유 → 컴포넌트 미렌더 + listResourcePermissions 호출 안 됨', async () => {
    getEffectivePermissionsMock.mockResolvedValue(['READ', 'DOWNLOAD'])
    const { container } = wrap(
      <ResourcePermissionsList resourceType="file" id="f1" />,
    )
    // usePermission 응답이 도착할 때까지 대기 — 그래도 아무 것도 렌더되지 않아야 함.
    await waitFor(() => {
      expect(getEffectivePermissionsMock).toHaveBeenCalled()
    })
    // 다음 microtask 까지 양보 후에도 listResourcePermissions 미호출.
    await new Promise((r) => setTimeout(r, 30))
    expect(listResourcePermissionsMock).not.toHaveBeenCalled()
    expect(container.querySelector('section[aria-label="권한 부여 목록"]')).toBeNull()
  })

  it('admin 보유 + 빈 응답 → empty 메시지 + 헤더 그리드 미렌더', async () => {
    getEffectivePermissionsMock.mockResolvedValue(['PERMISSION_ADMIN', 'READ'])
    listResourcePermissionsMock.mockResolvedValue([])
    wrap(<ResourcePermissionsList resourceType="file" id="f1" />)
    await waitFor(() => {
      expect(screen.getByText('부여된 권한이 없습니다.')).toBeTruthy()
    })
    // 빈 상태에서는 grid (role=grid) 자체가 mount 되지 않음 — header 도 없음.
    expect(document.querySelector('[role="grid"]')).toBeNull()
  })

  it('admin 보유 + 데이터 응답 → 행/컬럼/aria-rowcount 렌더', async () => {
    getEffectivePermissionsMock.mockResolvedValue(['PERMISSION_ADMIN'])
    listResourcePermissionsMock.mockResolvedValue([
      {
        id: 'p1',
        resourceType: 'file',
        resourceId: 'f1',
        subjectType: 'user',
        subjectId: 'u1',
        preset: 'admin',
        grantedBy: 'admin1',
        expiresAt: null,
        createdAt: '2026-05-01T00:00:00Z',
        subjectName: 'Alice',
      },
      {
        id: 'p2',
        resourceType: 'file',
        resourceId: 'f1',
        subjectType: 'everyone',
        subjectId: null,
        preset: 'read',
        grantedBy: 'admin1',
        expiresAt: '2026-12-31T00:00:00Z',
        createdAt: '2026-05-02T00:00:00Z',
        subjectName: null,
      },
      {
        id: 'p3',
        resourceType: 'file',
        resourceId: 'f1',
        subjectType: 'user',
        subjectId: 'u-deleted',
        preset: 'edit',
        grantedBy: 'admin1',
        expiresAt: null,
        createdAt: '2026-05-03T00:00:00Z',
        subjectName: null,
      },
    ])
    wrap(<ResourcePermissionsList resourceType="file" id="f1" />)

    const grid = await screen.findByRole('grid')
    // header(1) + 3 data rows = 4
    expect(grid.getAttribute('aria-rowcount')).toBe('4')

    // subject 표시 정책 — name 우선 → everyone fallback → 미해결 fallback
    expect(screen.getByText('Alice')).toBeTruthy()
    expect(screen.getByText('전체')).toBeTruthy()
    expect(screen.getByText(/\(미해결\) u-deleted/)).toBeTruthy()

    // preset 한국어 라벨
    expect(screen.getByText('관리')).toBeTruthy()
    expect(screen.getByText('읽기')).toBeTruthy()
    expect(screen.getByText('편집')).toBeTruthy()

    // 만료 — null → '없음', 값 → 포맷팅된 문자열 (정확한 로케일 출력은 환경 의존이라 '없음' 만 검증)
    expect(screen.getAllByText('없음').length).toBeGreaterThanOrEqual(2) // p1, p3 둘 다 null
  })

  it('admin 보유 + 에러 → role=alert 메시지', async () => {
    getEffectivePermissionsMock.mockResolvedValue(['PERMISSION_ADMIN'])
    const err = new Error('forbidden') as Error & { status: number }
    err.status = 403
    listResourcePermissionsMock.mockRejectedValue(err)
    wrap(<ResourcePermissionsList resourceType="file" id="f1" />)
    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toMatch(/불러올 수 없습니다/)
    })
  })

  it('admin 보유 + 로딩 중 → role=status 로딩 메시지', async () => {
    getEffectivePermissionsMock.mockResolvedValue(['PERMISSION_ADMIN'])
    listResourcePermissionsMock.mockImplementation(
      () => new Promise(() => {}),
    )
    wrap(<ResourcePermissionsList resourceType="file" id="f1" />)
    await waitFor(() => {
      expect(listResourcePermissionsMock).toHaveBeenCalled()
    })
    expect(screen.getByRole('status').textContent).toMatch(/로딩/)
  })

  it("resourceType='folder' 도 동일하게 동작 (api 인자에 'folder' 전달)", async () => {
    getEffectivePermissionsMock.mockResolvedValue(['PERMISSION_ADMIN'])
    listResourcePermissionsMock.mockResolvedValue([])
    wrap(<ResourcePermissionsList resourceType="folder" id="fld1" />)
    await waitFor(() => {
      expect(listResourcePermissionsMock).toHaveBeenCalledWith('folder', 'fld1')
    })
  })
})
