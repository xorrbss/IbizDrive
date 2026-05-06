import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import {
  useAdminCreateDepartment,
  useAdminDeactivateDepartment,
  useAdminDepartments,
  useAdminUpdateDepartment,
} from './useAdminDepartments'
import { api } from '@/lib/api'
import type { AdminDepartmentPage, AdminDepartmentSummary } from '@/types/department'

vi.mock('@/lib/api', () => ({
  api: {
    adminListDepartments: vi.fn(),
    adminCreateDepartment: vi.fn(),
    adminUpdateDepartment: vi.fn(),
  },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const PAGE: AdminDepartmentPage = {
  content: [
    {
      id: 'd1111111-1111-1111-1111-111111111111',
      name: '영업팀',
      isActive: true,
      createdAt: '2026-01-01T00:00:00Z',
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 50,
}

const SUMMARY: AdminDepartmentSummary = {
  id: 'd2222222-2222-2222-2222-222222222222',
  name: '인사팀',
  isActive: true,
  createdAt: '2026-01-02T00:00:00Z',
}

describe('useAdminDepartments', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('데이터 로딩 성공 — api.adminListDepartments(page,size,q) 호출 + q 정규화', async () => {
    ;(api.adminListDepartments as ReturnType<typeof vi.fn>).mockResolvedValue(PAGE)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminDepartments(0, 50, '  YeongUp  '), {
      wrapper: wrap(qc),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.adminListDepartments).toHaveBeenCalledWith(0, 50, 'yeongup')
    expect(result.current.data).toEqual(PAGE)
  })

  it('빈 q — undefined로 위임 (전체 조회)', async () => {
    ;(api.adminListDepartments as ReturnType<typeof vi.fn>).mockResolvedValue(PAGE)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(() => useAdminDepartments(0, 50, ''), { wrapper: wrap(qc) })

    await waitFor(() =>
      expect(api.adminListDepartments).toHaveBeenCalledWith(0, 50, undefined),
    )
  })

  it('403 → isError + retry 비활성', async () => {
    const err = Object.assign(new Error('adminListDepartments failed: 403'), { status: 403 })
    const fn = api.adminListDepartments as ReturnType<typeof vi.fn>
    fn.mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminDepartments(), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(fn).toHaveBeenCalledTimes(1)
  })
})

describe('useAdminCreateDepartment', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('성공 — api.adminCreateDepartment 호출 + 결과 반환', async () => {
    ;(api.adminCreateDepartment as ReturnType<typeof vi.fn>).mockResolvedValue(SUMMARY)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminCreateDepartment(), { wrapper: wrap(qc) })

    const out = await result.current.mutateAsync({ name: '인사팀' })
    expect(out).toEqual(SUMMARY)
    expect(api.adminCreateDepartment).toHaveBeenCalledWith({ name: '인사팀' })
  })

  it('409 DEPARTMENT_CONFLICT — error로 surface (호출자 분기 가능)', async () => {
    const err = Object.assign(new Error('adminCreateDepartment failed: 409'), {
      status: 409,
      code: 'DEPARTMENT_CONFLICT',
    })
    ;(api.adminCreateDepartment as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminCreateDepartment(), { wrapper: wrap(qc) })

    await expect(result.current.mutateAsync({ name: '중복' })).rejects.toMatchObject({
      status: 409,
      code: 'DEPARTMENT_CONFLICT',
    })
  })
})

describe('useAdminUpdateDepartment', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('rename — api.adminUpdateDepartment(id, { name }) 호출', async () => {
    ;(api.adminUpdateDepartment as ReturnType<typeof vi.fn>).mockResolvedValue(SUMMARY)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminUpdateDepartment(), { wrapper: wrap(qc) })

    await result.current.mutateAsync({ id: SUMMARY.id, body: { name: 'NewName' } })
    expect(api.adminUpdateDepartment).toHaveBeenCalledWith(SUMMARY.id, { name: 'NewName' })
  })

  it('reactivate — body { isActive: true } 그대로 위임', async () => {
    ;(api.adminUpdateDepartment as ReturnType<typeof vi.fn>).mockResolvedValue(SUMMARY)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminUpdateDepartment(), { wrapper: wrap(qc) })

    await result.current.mutateAsync({ id: SUMMARY.id, body: { isActive: true } })
    expect(api.adminUpdateDepartment).toHaveBeenCalledWith(SUMMARY.id, { isActive: true })
  })
})

describe('useAdminDeactivateDepartment', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('비활성화 — body { isActive: false } 고정 위임', async () => {
    ;(api.adminUpdateDepartment as ReturnType<typeof vi.fn>).mockResolvedValue({
      ...SUMMARY,
      isActive: false,
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminDeactivateDepartment(), { wrapper: wrap(qc) })

    await result.current.mutateAsync({ id: SUMMARY.id })
    expect(api.adminUpdateDepartment).toHaveBeenCalledWith(SUMMARY.id, { isActive: false })
  })
})
