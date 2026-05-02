import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

// api 모킹 — listFileVersions(렌더 시) + downloadVersion + restoreVersion(클릭 시).
const listFileVersionsMock = vi.fn()
const downloadVersionMock = vi.fn()
const restoreVersionMock = vi.fn()
vi.mock('@/lib/api', () => ({
  api: {
    listFileVersions: (...args: unknown[]) => listFileVersionsMock(...args),
    downloadVersion: (...args: unknown[]) => downloadVersionMock(...args),
    restoreVersion: (...args: unknown[]) => restoreVersionMock(...args),
  },
}))

import { VersionsTab } from './VersionsTab'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>{node}</QueryClientProvider>,
  )
}

const SAMPLE = [
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
]

describe('VersionsTab — M-RP.2.3 download/restore actions', () => {
  beforeEach(() => {
    listFileVersionsMock.mockReset()
    downloadVersionMock.mockReset()
    restoreVersionMock.mockReset()
  })

  it('현재 버전(isCurrent=true)의 복원 버튼은 disabled, 비-current는 활성', async () => {
    listFileVersionsMock.mockResolvedValue(SAMPLE)
    wrap(<VersionsTab fileId="file_a" />)

    await waitFor(() => {
      expect(screen.getByLabelText('파일 버전 목록')).toBeTruthy()
    })

    const restoreV2 = screen.getByLabelText('v2 복원') as HTMLButtonElement
    const restoreV1 = screen.getByLabelText('v1 복원') as HTMLButtonElement
    expect(restoreV2.disabled).toBe(true)
    expect(restoreV1.disabled).toBe(false)
  })

  it('다운로드 버튼은 모든 row에서 활성 + 클릭 시 api.downloadVersion(fileId, versionId)', async () => {
    listFileVersionsMock.mockResolvedValue(SAMPLE)
    wrap(<VersionsTab fileId="file_a" />)

    await waitFor(() => {
      expect(screen.getByLabelText('파일 버전 목록')).toBeTruthy()
    })

    const dlV2 = screen.getByLabelText('v2 다운로드') as HTMLButtonElement
    const dlV1 = screen.getByLabelText('v1 다운로드') as HTMLButtonElement
    expect(dlV2.disabled).toBe(false)
    expect(dlV1.disabled).toBe(false)

    fireEvent.click(dlV1)
    expect(downloadVersionMock).toHaveBeenCalledWith('file_a', 'v1')
  })

  it('비-current 복원 버튼 클릭 시 api.restoreVersion(fileId, versionId) 호출', async () => {
    listFileVersionsMock.mockResolvedValue(SAMPLE)
    restoreVersionMock.mockResolvedValue(undefined)
    wrap(<VersionsTab fileId="file_a" />)

    await waitFor(() => {
      expect(screen.getByLabelText('파일 버전 목록')).toBeTruthy()
    })

    fireEvent.click(screen.getByLabelText('v1 복원'))
    await waitFor(() => {
      expect(restoreVersionMock).toHaveBeenCalledWith('file_a', 'v1')
    })
  })
})
