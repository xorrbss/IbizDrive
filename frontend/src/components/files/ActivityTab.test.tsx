import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

// api 모킹 — listFileActivity만 사용. useFileActivity가 내부에서 호출.
const listFileActivityMock = vi.fn()
vi.mock('@/lib/api', () => ({
  api: {
    listFileActivity: (...args: unknown[]) => listFileActivityMock(...args),
  },
}))

import { ActivityTab } from './ActivityTab'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>{node}</QueryClientProvider>,
  )
}

describe('ActivityTab — M-RP.4', () => {
  beforeEach(() => {
    listFileActivityMock.mockReset()
  })

  it('빈 응답일 때 "활동 내역이 없습니다." 표시', async () => {
    listFileActivityMock.mockResolvedValue({
      entries: [],
      total: 0,
      page: 1,
      pageSize: 20,
    })
    wrap(<ActivityTab fileId="file_a" />)
    await waitFor(() => {
      expect(screen.getByText('활동 내역이 없습니다.')).toBeTruthy()
    })
    expect(listFileActivityMock).toHaveBeenCalledWith('file_a', 1, 20)
  })

  it('이벤트 행 렌더 — 한글 라벨 + actor 이름', async () => {
    listFileActivityMock.mockResolvedValue({
      entries: [
        {
          id: '101',
          occurredAt: '2026-04-30T10:00:00Z',
          eventType: 'file.uploaded',
          actorId: 'u1',
          actorName: '김영수',
          resourceType: 'file',
          resourceId: 'file_a',
          resourceName: null,
          ip: '203.0.113.10',
          metadata: null,
        },
        {
          id: '100',
          occurredAt: '2026-04-29T09:00:00Z',
          eventType: 'version.restored',
          actorId: 'u2',
          actorName: '이지은',
          resourceType: 'file',
          resourceId: 'file_a',
          resourceName: null,
          ip: '203.0.113.11',
          metadata: null,
        },
      ],
      total: 2,
      page: 1,
      pageSize: 20,
    })
    wrap(<ActivityTab fileId="file_a" />)

    await waitFor(() => {
      expect(screen.getByLabelText('파일 활동 타임라인')).toBeTruthy()
    })
    expect(screen.getByText('업로드')).toBeTruthy()
    expect(screen.getByText('버전 복원')).toBeTruthy()
    expect(screen.getByText('김영수')).toBeTruthy()
    expect(screen.getByText('이지은')).toBeTruthy()
  })

  it('알 수 없는 eventType은 raw 값으로 fallback 표시', async () => {
    listFileActivityMock.mockResolvedValue({
      entries: [
        {
          id: '200',
          occurredAt: '2026-04-30T10:00:00Z',
          eventType: 'audit.exported',
          actorId: 'u1',
          actorName: '관리자',
          resourceType: 'audit',
          resourceId: null,
          resourceName: null,
          ip: null,
          metadata: null,
        },
      ],
      total: 1,
      page: 1,
      pageSize: 20,
    })
    wrap(<ActivityTab fileId="file_a" />)
    await waitFor(() => {
      expect(screen.getByText('audit.exported')).toBeTruthy()
    })
  })

  it('데이터 행에 data-event-type 속성으로 원본 enum 노출', async () => {
    listFileActivityMock.mockResolvedValue({
      entries: [
        {
          id: '300',
          occurredAt: '2026-04-30T10:00:00Z',
          eventType: 'permission.granted',
          actorId: 'u1',
          actorName: '관리자',
          resourceType: 'file',
          resourceId: 'file_a',
          resourceName: null,
          ip: null,
          metadata: null,
        },
      ],
      total: 1,
      page: 1,
      pageSize: 20,
    })
    wrap(<ActivityTab fileId="file_a" />)
    await waitFor(() => {
      const li = document.querySelector('[data-event-type="permission.granted"]')
      expect(li).toBeTruthy()
    })
  })

  it('에러 시 alert role 메시지 표시', async () => {
    listFileActivityMock.mockRejectedValue({ status: 500 })
    wrap(<ActivityTab fileId="file_a" />)
    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeTruthy()
    })
  })

  it('로딩 중에는 로딩 메시지', async () => {
    listFileActivityMock.mockImplementation(() => new Promise(() => {}))
    wrap(<ActivityTab fileId="file_a" />)
    expect(await screen.findByText(/활동을 불러오는 중/)).toBeTruthy()
  })
})
