import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { AdminDepartmentPage } from '@/types/department'

/**
 * /admin/departments — 생성 폼 + 검색/목록/rename/(de)activate (admin-department-crud).
 *
 * <p>admin-user-mgmt page.test.tsx 매트릭스 mirror — invite를 create로, role을 rename으로 치환.
 * fetch wire는 hook.test에서 검증 — 본 테스트는 hook 모킹 후 UI 매트릭스만 가드.
 */

const createMutateAsyncMock = vi.fn()
const createIsPendingRef = { current: false }
vi.mock('@/hooks/useAdminDepartments', async () => {
  const actual = await vi.importActual<
    typeof import('@/hooks/useAdminDepartments')
  >('@/hooks/useAdminDepartments')
  return {
    ...actual,
    useAdminCreateDepartment: () => ({
      mutateAsync: createMutateAsyncMock,
      get isPending() {
        return createIsPendingRef.current
      },
    }),
    useAdminDepartments: (page: number, _size: number, q: string) => {
      lastQueryArgs = { page, q }
      return depQueryState
    },
    useAdminUpdateDepartment: () => ({
      mutateAsync: updateMutateAsyncMock,
      get isPending() {
        return updateIsPendingRef.current
      },
    }),
  }
})

let lastQueryArgs: { page: number; q: string } = { page: 0, q: '' }
let depQueryState: {
  data?: AdminDepartmentPage
  isLoading: boolean
  isError: boolean
} = { isLoading: false, isError: false }

const updateMutateAsyncMock = vi.fn()
const updateIsPendingRef = { current: false }

// AdminGuard 격리 — wave1.5-auditor-admin-ui-access로 페이지가 default
// `<AdminGuard>` 안쪽에 들어갔으므로 children이 렌더되도록 ADMIN role mock.
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), back: vi.fn() }),
}))
vi.mock('@/hooks/useMe', () => ({
  useMe: () => ({
    data: {
      user: { id: 'u1', email: 'a@b.com', name: 'A', kind: 'human', mustChangePassword: false },
      departments: [],
      roles: ['ADMIN'],
      effectivePermissionsCacheKey: 'k',
    },
    isLoading: false,
    isError: false,
  }),
}))

import AdminDepartmentsPage from './page'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

const PAGE_DATA: AdminDepartmentPage = {
  content: [
    {
      id: 'd1111111-1111-1111-1111-111111111111',
      name: '영업팀',
      isActive: true,
      createdAt: '2026-01-01T00:00:00Z',
    },
    {
      id: 'd2222222-2222-2222-2222-222222222222',
      name: '인사팀',
      isActive: false,
      createdAt: '2026-01-02T00:00:00Z',
    },
  ],
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 50,
}

describe('AdminDepartmentsPage — 생성 폼', () => {
  beforeEach(() => {
    vi.useRealTimers()
    createMutateAsyncMock.mockReset()
    createIsPendingRef.current = false
    updateMutateAsyncMock.mockReset()
    updateIsPendingRef.current = false
    depQueryState = { isLoading: false, isError: false, data: PAGE_DATA }
    lastQueryArgs = { page: 0, q: '' }
  })

  it('초기 렌더 — 부서 이름 input + 추가 버튼', () => {
    wrap(<AdminDepartmentsPage />)
    expect(screen.getByLabelText('부서 이름')).toBeTruthy()
    expect(screen.getByRole('button', { name: /^추가$/ })).toBeTruthy()
  })

  it('성공 — useAdminCreateDepartment({name}) + 안내 + 폼 리셋', async () => {
    createMutateAsyncMock.mockResolvedValue({
      id: 'd3333333-3333-3333-3333-333333333333',
      name: '재무팀',
      isActive: true,
      createdAt: '2026-01-03T00:00:00Z',
    })
    wrap(<AdminDepartmentsPage />)

    fireEvent.change(screen.getByLabelText('부서 이름'), {
      target: { value: '재무팀' },
    })
    fireEvent.click(screen.getByRole('button', { name: /^추가$/ }))

    await waitFor(() => {
      expect(createMutateAsyncMock).toHaveBeenCalledWith({ name: '재무팀' })
    })
    await waitFor(() => {
      expect(screen.getByRole('status').textContent ?? '').toMatch(/생성했습니다/)
    })
    expect((screen.getByLabelText('부서 이름') as HTMLInputElement).value).toBe('')
  })

  it('409 DEPARTMENT_CONFLICT — 인라인 에러 노출', async () => {
    const err = Object.assign(new Error('adminCreateDepartment failed: 409'), {
      status: 409,
      code: 'DEPARTMENT_CONFLICT',
    })
    createMutateAsyncMock.mockRejectedValue(err)
    wrap(<AdminDepartmentsPage />)

    fireEvent.change(screen.getByLabelText('부서 이름'), {
      target: { value: '중복' },
    })
    fireEvent.click(screen.getByRole('button', { name: /^추가$/ }))

    await waitFor(() => {
      const alerts = screen.getAllByRole('alert')
      expect(alerts.some((a) => /활성 부서가 이미 존재/.test(a.textContent ?? ''))).toBe(true)
    })
  })
})

describe('AdminDepartmentsPage — 목록 + 검색', () => {
  beforeEach(() => {
    vi.useRealTimers()
    createMutateAsyncMock.mockReset()
    updateMutateAsyncMock.mockReset()
    updateIsPendingRef.current = false
    depQueryState = { isLoading: false, isError: false, data: PAGE_DATA }
    lastQueryArgs = { page: 0, q: '' }
  })

  it('목록 렌더 — 활성/비활성 상태 + 동작 버튼', () => {
    wrap(<AdminDepartmentsPage />)
    expect(screen.getByText('영업팀')).toBeTruthy()
    expect(screen.getByText('인사팀')).toBeTruthy()
    expect(screen.getByLabelText('영업팀 비활성화')).toBeTruthy()
    expect(screen.getByLabelText('인사팀 재활성화')).toBeTruthy()
  })

  it('검색 입력 — 300ms debounce 후 q 갱신', async () => {
    vi.useFakeTimers()
    wrap(<AdminDepartmentsPage />)

    fireEvent.change(screen.getByLabelText('부서 검색'), {
      target: { value: '영업' },
    })

    // 300ms 이전에는 갱신 없음 (이전 마운트 시점의 q='')
    expect(lastQueryArgs.q).toBe('')

    act(() => {
      vi.advanceTimersByTime(300)
    })
    expect(lastQueryArgs.q).toBe('영업')
    vi.useRealTimers()
  })

  it('이름 변경 — 편집 모드 → 저장 → useAdminUpdateDepartment({id, body:{name}})', async () => {
    updateMutateAsyncMock.mockResolvedValue({
      ...PAGE_DATA.content[0],
      name: '신영업팀',
    })
    wrap(<AdminDepartmentsPage />)

    fireEvent.click(screen.getByLabelText('영업팀 이름 변경'))
    const input = screen.getByLabelText('영업팀 이름 편집') as HTMLInputElement
    fireEvent.change(input, { target: { value: '신영업팀' } })
    fireEvent.click(screen.getByRole('button', { name: /^저장$/ }))

    await waitFor(() => {
      expect(updateMutateAsyncMock).toHaveBeenCalledWith({
        id: 'd1111111-1111-1111-1111-111111111111',
        body: { name: '신영업팀' },
      })
    })
  })

  it('비활성화 — useAdminUpdateDepartment({id, body:{isActive:false}})', async () => {
    updateMutateAsyncMock.mockResolvedValue({})
    wrap(<AdminDepartmentsPage />)

    fireEvent.click(screen.getByLabelText('영업팀 비활성화'))

    await waitFor(() => {
      expect(updateMutateAsyncMock).toHaveBeenCalledWith({
        id: 'd1111111-1111-1111-1111-111111111111',
        body: { isActive: false },
      })
    })
  })

  it('재활성화 — useAdminUpdateDepartment({id, body:{isActive:true}})', async () => {
    updateMutateAsyncMock.mockResolvedValue({})
    wrap(<AdminDepartmentsPage />)

    fireEvent.click(screen.getByLabelText('인사팀 재활성화'))

    await waitFor(() => {
      expect(updateMutateAsyncMock).toHaveBeenCalledWith({
        id: 'd2222222-2222-2222-2222-222222222222',
        body: { isActive: true },
      })
    })
  })

  it('409 DEPARTMENT_CONFLICT (rename) — 인라인 에러 노출', async () => {
    const err = Object.assign(new Error('adminUpdateDepartment failed: 409'), {
      status: 409,
      code: 'DEPARTMENT_CONFLICT',
    })
    updateMutateAsyncMock.mockRejectedValue(err)
    wrap(<AdminDepartmentsPage />)

    fireEvent.click(screen.getByLabelText('영업팀 이름 변경'))
    fireEvent.change(screen.getByLabelText('영업팀 이름 편집'), {
      target: { value: '인사팀' },
    })
    fireEvent.click(screen.getByRole('button', { name: /^저장$/ }))

    await waitFor(() => {
      const alerts = screen.getAllByRole('alert')
      expect(alerts.some((a) => /활성 부서가 이미 존재/.test(a.textContent ?? ''))).toBe(true)
    })
  })

  it('빈 목록 — 안내 메시지', () => {
    depQueryState = {
      isLoading: false,
      isError: false,
      data: { ...PAGE_DATA, content: [], totalElements: 0 },
    }
    wrap(<AdminDepartmentsPage />)
    expect(screen.getByText('부서가 없습니다.')).toBeTruthy()
  })

  it('로딩 상태', () => {
    depQueryState = { isLoading: true, isError: false }
    wrap(<AdminDepartmentsPage />)
    expect(screen.getByText('불러오는 중…')).toBeTruthy()
  })

  it('에러 상태 — alert', () => {
    depQueryState = { isLoading: false, isError: true }
    wrap(<AdminDepartmentsPage />)
    const alerts = screen.getAllByRole('alert')
    expect(alerts.some((a) => /목록을 불러오지 못했습니다/.test(a.textContent ?? ''))).toBe(true)
  })
})
