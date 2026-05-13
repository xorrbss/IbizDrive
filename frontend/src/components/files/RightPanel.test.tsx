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

// Mock api.getFileDetail + listFileVersions (M-RP.1) + getEffectivePermissions (M-RP.3)
// + listFileActivity (M-RP.4) + listResourcePermissions (M8.1)
const getFileDetailMock = vi.fn()
const listFileVersionsMock = vi.fn()
const getEffectivePermissionsMock = vi.fn()
const listFileActivityMock = vi.fn()
const listResourcePermissionsMock = vi.fn()
vi.mock('@/lib/api', () => ({
  api: {
    getFileDetail: (...args: unknown[]) => getFileDetailMock(...args),
    listFileVersions: (...args: unknown[]) => listFileVersionsMock(...args),
    getEffectivePermissions: (...args: unknown[]) =>
      getEffectivePermissionsMock(...args),
    listFileActivity: (...args: unknown[]) => listFileActivityMock(...args),
    listResourcePermissions: (...args: unknown[]) =>
      listResourcePermissionsMock(...args),
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
    listFileActivityMock.mockReset()
    listFileActivityMock.mockResolvedValue({
      entries: [],
      total: 0,
      page: 1,
      pageSize: 20,
    })
    listResourcePermissionsMock.mockReset()
    listResourcePermissionsMock.mockResolvedValue([])
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

  it('M-RP.4 — 활동 탭 클릭 시 listFileActivity 호출 + 빈 상태 메시지', async () => {
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
    expect(
      screen.getByRole('tab', { name: '활동' }).getAttribute('aria-selected'),
    ).toBe('true')
    await waitFor(() => {
      expect(screen.getByText('활동 내역이 없습니다.')).toBeTruthy()
    })
    expect(listFileActivityMock).toHaveBeenCalledWith('file_abc', 1, 20)
  })

  it('M-RP.4 — 활동 탭에서 list 렌더 (eventType + actor 표시)', async () => {
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
    listFileActivityMock.mockResolvedValue({
      entries: [
        {
          id: '101',
          occurredAt: '2026-04-30T10:00:00Z',
          eventType: 'file.uploaded',
          actorId: 'u1',
          actorName: '김영수',
          resourceType: 'file',
          resourceId: 'file_abc',
          resourceName: null,
          ip: '203.0.113.10',
          metadata: null,
          severity: 'info',
        },
      ],
      total: 1,
      page: 1,
      pageSize: 20,
    })
    wrap(<RightPanel />)
    fireEvent.click(screen.getByRole('tab', { name: '활동' }))
    await waitFor(() => {
      expect(screen.getByLabelText('파일 활동 타임라인')).toBeTruthy()
    })
    expect(screen.getByText('업로드')).toBeTruthy()
    expect(screen.getByText('김영수')).toBeTruthy()
  })

  it('M-RP.4 — 비-activity 탭에서는 listFileActivity 호출 안 됨 (conditional render)', async () => {
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
    expect(listFileActivityMock).not.toHaveBeenCalled()
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

  // ─────────────────── P_panel-B: detail rows from FileDetailResponse ──────────

  it('P_panel-B — owner 정보가 있으면 소유자 row 표시', async () => {
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
      owner: { id: 'u1', displayName: '홍길동', email: 'h@test' },
    })
    wrap(<RightPanel />)
    await waitFor(() => {
      expect(screen.getByText('소유자')).toBeTruthy()
      expect(screen.getByText('홍길동')).toBeTruthy()
      expect(screen.getByText('h@test')).toBeTruthy()
    })
  })

  it('P_panel-B — owner 없으면 소유자 row 미렌더', async () => {
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
      // owner 필드 자체 없음 (soft-delete fallback)
    })
    wrap(<RightPanel />)
    await waitFor(() => {
      expect(screen.getAllByText('x.pdf').length).toBeGreaterThan(0)
    })
    expect(screen.queryByText('소유자')).toBeNull()
  })

  it('P_panel-B — sharedWith stack에 5명 이하 표시 + everyone "전체" 라벨', async () => {
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
      sharedWith: [
        { subjectType: 'user', subjectId: 'u1', subjectName: '김영업', preset: 'read' },
        { subjectType: 'user', subjectId: 'u2', subjectName: '이마케팅', preset: 'edit' },
        { subjectType: 'everyone', subjectId: null, subjectName: '전체', preset: 'read' },
      ],
    })
    wrap(<RightPanel />)
    await waitFor(() => {
      expect(screen.getByLabelText('공유 대상')).toBeTruthy()
    })
    // title attribute에서 subjectName + preset 확인
    const stack = screen.getByLabelText('공유 대상')
    expect(stack.querySelector('[title="김영업 · read"]')).toBeTruthy()
    expect(stack.querySelector('[title="전체 · read"]')).toBeTruthy()
  })

  it('P_panel-B — sharedWith 6명 이상이면 "+N" overflow 표시', async () => {
    mockQuery = 'file=file_abc'
    const many = Array.from({ length: 8 }).map((_, i) => ({
      subjectType: 'user' as const,
      subjectId: `u${i}`,
      subjectName: `사용자${i}`,
      preset: 'read',
    }))
    getFileDetailMock.mockResolvedValue({
      id: 'file_abc',
      name: 'x.pdf',
      type: 'file',
      mimeType: 'application/pdf',
      size: 100,
      updatedAt: '2026-04-20T09:00:00Z',
      updatedBy: 'u',
      parentId: 'root',
      sharedWith: many,
    })
    wrap(<RightPanel />)
    await waitFor(() => {
      // 8명 중 5명 visible → +3 overflow
      expect(screen.getByText('+3')).toBeTruthy()
    })
  })

  it('P_panel-B — sharedWith 빈 배열이면 공유 row 미렌더', async () => {
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
      sharedWith: [],
    })
    wrap(<RightPanel />)
    await waitFor(() => {
      expect(screen.getAllByText('x.pdf').length).toBeGreaterThan(0)
    })
    expect(screen.queryByLabelText('공유 대상')).toBeNull()
  })

  it('P_panel-B — folderPath가 있으면 경로 row 표시 (root → leaf 순)', async () => {
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
      folderPath: [
        { id: 'r1', name: '회사', slug: '회사' },
        { id: 'm1', name: '영업팀', slug: '영업팀' },
        { id: 'l1', name: '계약서', slug: '계약서' },
      ],
    })
    wrap(<RightPanel />)
    await waitFor(() => {
      expect(screen.getByLabelText('폴더 경로')).toBeTruthy()
    })
    const pathEl = screen.getByLabelText('폴더 경로')
    expect(pathEl.textContent).toContain('회사')
    expect(pathEl.textContent).toContain('영업팀')
    expect(pathEl.textContent).toContain('계약서')
  })
})
