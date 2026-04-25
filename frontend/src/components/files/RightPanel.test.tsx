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

// Mock api.getFileDetail
const getFileDetailMock = vi.fn()
vi.mock('@/lib/api', () => ({
  api: { getFileDetail: (...args: unknown[]) => getFileDetailMock(...args) },
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

  it('에러 시 에러 메시지 표시', async () => {
    mockQuery = 'file=missing'
    getFileDetailMock.mockRejectedValue({ status: 404 })

    wrap(<RightPanel />)

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeTruthy()
    })
  })

  it('탭바 mount 시 details 탭 active', () => {
    mockQuery = 'file=file_abc'
    getFileDetailMock.mockResolvedValue({
      id: 'file_abc', name: 'x', type: 'file', mimeType: 'application/pdf',
      size: 1, updatedAt: '2026-04-20T09:00:00Z', updatedBy: 'u', parentId: 'root',
    })
    wrap(<RightPanel />)
    const detailsTab = screen.getByRole('tab', { name: '세부정보' })
    expect(detailsTab.getAttribute('aria-selected')).toBe('true')
  })

  it('탭 클릭 → tabpanel 변경', () => {
    mockQuery = 'file=file_abc'
    getFileDetailMock.mockResolvedValue({
      id: 'file_abc', name: 'x', type: 'file', mimeType: 'application/pdf',
      size: 1, updatedAt: '2026-04-20T09:00:00Z', updatedBy: 'u', parentId: 'root',
    })
    wrap(<RightPanel />)
    fireEvent.click(screen.getByRole('tab', { name: '버전' }))
    expect(screen.getByRole('tab', { name: '버전' }).getAttribute('aria-selected')).toBe('true')
    expect(screen.getByText('버전 기록 — 준비 중입니다')).toBeTruthy()
  })

  it('ArrowRight 키 → 다음 탭으로 이동', () => {
    mockQuery = 'file=file_abc'
    getFileDetailMock.mockResolvedValue({
      id: 'file_abc', name: 'x', type: 'file', mimeType: 'application/pdf',
      size: 1, updatedAt: '2026-04-20T09:00:00Z', updatedBy: 'u', parentId: 'root',
    })
    wrap(<RightPanel />)
    const tablist = screen.getByRole('tablist')
    fireEvent.keyDown(tablist, { key: 'ArrowRight' })
    expect(screen.getByRole('tab', { name: '버전' }).getAttribute('aria-selected')).toBe('true')
  })
})
