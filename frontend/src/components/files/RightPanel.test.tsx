import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

// Mock next/navigation
const replaceMock = vi.fn()
let mockQuery = ''
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock, push: vi.fn() }),
  usePathname: () => '/files/root',
  useSearchParams: () => new URLSearchParams(mockQuery),
}))

// Mock api.getFileDetail + api.listFileVersions (M-RP.1) + api.getEffectivePermissions (M-RP.3)
const getFileDetailMock = vi.fn()
const listFileVersionsMock = vi.fn()
const getEffectivePermissionsMock = vi.fn()
vi.mock('@/lib/api', () => ({
  api: {
    getFileDetail: (...args: unknown[]) => getFileDetailMock(...args),
    listFileVersions: (...args: unknown[]) => listFileVersionsMock(...args),
    getEffectivePermissions: (...args: unknown[]) =>
      getEffectivePermissionsMock(...args),
  },
}))

import { RightPanel } from './RightPanel'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>{node}</QueryClientProvider>
  )
}

describe('RightPanel', () => {
  beforeEach(() => {
    replaceMock.mockReset()
    getFileDetailMock.mockReset()
    listFileVersionsMock.mockReset()
    listFileVersionsMock.mockResolvedValue([])
    getEffectivePermissionsMock.mockReset()
    getEffectivePermissionsMock.mockResolvedValue([])
    mockQuery = ''
  })

  it('?file= 없으면 렌더되지 않음', () => {
    wrap(<RightPanel />)
    expect(screen.queryByRole('complementary')).toBeNull()
  })

  it('?file=<id> 일 때 상세 표시', async () => {
    mockQuery = 'file=file_abc'
    getFileDetailMock.mockResolvedValue({
      id: 'file_abc',
      name: '제안서.pdf',
      type: 'file',
      mimeType: 'application/pdf',
      size: 2400000,
      updatedAt: '2026-04-20T09:00:00Z',
      updatedBy: '김영수',
      parentId: 'root',
    })

    wrap(<RightPanel />)

    expect(screen.getByRole('complementary', { name: '파일 상세' })).toBeTruthy()
    await waitFor(() => {
      expect(screen.getAllByText('제안서.pdf').length).toBeGreaterThan(0)
    })
    expect(screen.getByText('김영수')).toBeTruthy()
  })

  it('닫기 버튼 클릭 시 ?file= 제거', async () => {
    mockQuery = 'file=file_abc'
    getFileDetailMock.mockResolvedValue({
      id: 'file_abc',
      name: 'x.pdf',
      type: 'file',
      mimeType: 'application/pdf',
      size: 100,
      updatedAt: '2026-04-20T09:00:00Z',
      updatedBy: 'u',
      parentId: 'root',
    })

    wrap(<RightPanel />)

    fireEvent.click(screen.getByRole('button', { name: '패널 닫기' }))
    expect(replaceMock).toHaveBeenCalledWith('/files/root', { scroll: false })
  })

  it('Esc 키 누르면 ?file= 제거', async () => {
    mockQuery = 'file=file_abc'
    getFileDetailMock.mockResolvedValue({
      id: 'file_abc',
      name: 'x.pdf',
      type: 'file',
      mimeType: 'application/pdf',
      size: 100,
      updatedAt: '2026-04-20T09:00:00Z',
      updatedBy: 'u',
      parentId: 'root',
    })

    wrap(<RightPanel />)

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(replaceMock).toHaveBeenCalledWith('/files/root', { scroll: false })
  })

  it('M15.4 — 4 탭 렌더 (세부정보/버전/활동/권한)', async () => {
    mockQuery = 'file=file_abc'
    getFileDetailMock.mockResolvedValue({
      id: 'file_abc',
      name: 'x.pdf',
      type: 'file',
      mimeType: 'application/pdf',
      size: 100,
      updatedAt: '2026-04-20T09:00:00Z',
      updatedBy: 'u',
      parentId: 'root',
    })
    wrap(<RightPanel />)
    const tablist = screen.getByRole('tablist', { name: '상세 탭' })
    expect(tablist).toBeTruthy()
    expect(screen.getByRole('tab', { name: '세부정보' }).getAttribute('aria-selected')).toBe('true')
    expect(screen.getByRole('tab', { name: '버전' }).getAttribute('aria-selected')).toBe('false')
    expect(screen.getByRole('tab', { name: '활동' }).getAttribute('aria-selected')).toBe('false')
    expect(screen.getByRole('tab', { name: '권한' }).getAttribute('aria-selected')).toBe('false')
  })

  it('M15.4 / M-RP.3 — 활동 탭 클릭 시 placeholder 유지 (M-RP.4에서 활성)', async () => {
    mockQuery = 'file=file_abc'
    getFileDetailMock.mockResolvedValue({
      id: 'file_abc',
      name: 'x.pdf',
      type: 'file',
      mimeType: 'application/pdf',
      size: 100,
      updatedAt: '2026-04-20T09:00:00Z',
      updatedBy: 'u',
      parentId: 'root',
    })
    wrap(<RightPanel />)
    fireEvent.click(screen.getByRole('tab', { name: '활동' }))
    expect(screen.getByRole('tab', { name: '활동' }).getAttribute('aria-selected')).toBe('true')
    expect(screen.getByText(/활동 타임라인.*준비 중/)).toBeTruthy()
  })

  it('M-RP.3 — 권한 탭 클릭 시 권한 chip 9개 렌더 + 보유 권한 강조', async () => {
    mockQuery = 'file=file_abc'
    getFileDetailMock.mockResolvedValue({
      id: 'file_abc',
      name: 'x.pdf',
      type: 'file',
      mimeType: 'application/pdf',
      size: 100,
      updatedAt: '2026-04-20T09:00:00Z',
      updatedBy: 'u',
      parentId: 'root',
    })
    getEffectivePermissionsMock.mockResolvedValue(['READ', 'DOWNLOAD'])
    wrap(<RightPanel />)
    fireEvent.click(screen.getByRole('tab', { name: '권한' }))
    expect(
      screen.getByRole('tab', { name: '권한' }).getAttribute('aria-selected'),
    ).toBe('true')
    const list = await screen.findByLabelText('파일 권한 목록')
    expect(list.querySelectorAll('li').length).toBe(9)
    await waitFor(() => {
      expect(
        document
          .querySelector('[data-permission="READ"]')
          ?.getAttribute('data-held'),
      ).toBe('true')
    })
    expect(
      document
        .querySelector('[data-permission="EDIT"]')
        ?.getAttribute('data-held'),
    ).toBe('false')
    expect(getEffectivePermissionsMock).toHaveBeenCalledWith('file_abc')
  })

  it('M-RP.3 — 비-permissions 탭에서는 getEffectivePermissions 호출 안 됨', async () => {
    mockQuery = 'file=file_abc'
    getFileDetailMock.mockResolvedValue({
      id: 'file_abc',
      name: 'x.pdf',
      type: 'file',
      mimeType: 'application/pdf',
      size: 100,
      updatedAt: '2026-04-20T09:00:00Z',
      updatedBy: 'u',
      parentId: 'root',
    })
    wrap(<RightPanel />)
    await waitFor(() => {
      expect(getFileDetailMock).toHaveBeenCalled()
    })
    expect(getEffectivePermissionsMock).not.toHaveBeenCalled()
  })

  it('M-RP.1 — 버전 탭 클릭 시 빈 상태 메시지 표시 (listFileVersions=[])', async () => {
    mockQuery = 'file=file_abc'
    getFileDetailMock.mockResolvedValue({
      id: 'file_abc',
      name: 'x.pdf',
      type: 'file',
      mimeType: 'application/pdf',
      size: 100,
      updatedAt: '2026-04-20T09:00:00Z',
      updatedBy: 'u',
      parentId: 'root',
    })
    listFileVersionsMock.mockResolvedValue([])
    wrap(<RightPanel />)
    fireEvent.click(screen.getByRole('tab', { name: '버전' }))
    expect(screen.getByRole('tab', { name: '버전' }).getAttribute('aria-selected')).toBe('true')
    await waitFor(() => {
      expect(screen.getByText('버전이 없습니다.')).toBeTruthy()
    })
    expect(listFileVersionsMock).toHaveBeenCalledWith('file_abc')
  })

  it('M-RP.1 — 버전 탭에서 list 렌더 + 현재 버전 badge', async () => {
    mockQuery = 'file=file_abc'
    getFileDetailMock.mockResolvedValue({
      id: 'file_abc',
      name: 'x.pdf',
      type: 'file',
      mimeType: 'application/pdf',
      size: 100,
      updatedAt: '2026-04-20T09:00:00Z',
      updatedBy: 'u',
      parentId: 'root',
    })
    listFileVersionsMock.mockResolvedValue([
      {
        id: 'v2',
        versionNumber: 2,
        sizeBytes: 2048,
        scanStatus: 'clean',
        uploadedBy: '김영수',
        uploadedAt: '2026-04-30T10:00:00Z',
        isCurrent: true,
      },
      {
        id: 'v1',
        versionNumber: 1,
        sizeBytes: 1024,
        scanStatus: 'clean',
        uploadedBy: '김영수',
        uploadedAt: '2026-04-29T10:00:00Z',
        isCurrent: false,
      },
    ])
    wrap(<RightPanel />)
    fireEvent.click(screen.getByRole('tab', { name: '버전' }))
    await waitFor(() => {
      expect(screen.getByLabelText('파일 버전 목록')).toBeTruthy()
    })
    expect(screen.getByText('v2')).toBeTruthy()
    expect(screen.getByText('v1')).toBeTruthy()
    expect(screen.getByLabelText('현재 버전')).toBeTruthy()
  })

  it('M-RP.1 — 비-versions 탭에서는 listFileVersions 호출 안 됨 (conditional render)', async () => {
    mockQuery = 'file=file_abc'
    getFileDetailMock.mockResolvedValue({
      id: 'file_abc',
      name: 'x.pdf',
      type: 'file',
      mimeType: 'application/pdf',
      size: 100,
      updatedAt: '2026-04-20T09:00:00Z',
      updatedBy: 'u',
      parentId: 'root',
    })
    wrap(<RightPanel />)
    // detail 탭이 default — versions API 미호출 보장
    await waitFor(() => {
      expect(getFileDetailMock).toHaveBeenCalled()
    })
    expect(listFileVersionsMock).not.toHaveBeenCalled()
  })

  it('에러 시 에러 메시지 표시', async () => {
    mockQuery = 'file=missing'
    getFileDetailMock.mockRejectedValue({ status: 404 })

    wrap(<RightPanel />)

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeTruthy()
    })
  })
})
