import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { UploadButton } from './UploadButton'

const enqueueMock = vi.fn()
const useCurrentFolderMock = vi.fn()

vi.mock('@/hooks/useUpload', () => ({
  useUpload: () => ({ enqueue: enqueueMock }),
}))
vi.mock('@/hooks/useCurrentFolder', () => ({
  useCurrentFolder: () => useCurrentFolderMock(),
}))
vi.mock('sonner', () => ({
  toast: {
    info: vi.fn(),
    error: vi.fn(),
  },
}))

describe('UploadButton — 가상 root 가드', () => {
  beforeEach(() => {
    enqueueMock.mockReset()
  })

  it('실제 폴더(folderId=UUID)에서는 버튼이 활성화되고 file 선택 시 enqueue 호출', () => {
    useCurrentFolderMock.mockReturnValue({ folderId: '3aa22143-8f37-4f40-9928-e569bccf6ece' })
    render(<UploadButton />)

    const btn = screen.getByRole('button', { name: '업로드' })
    expect((btn as HTMLButtonElement).disabled).toBe(false)

    const input = btn.parentElement!.querySelector('input[type="file"]') as HTMLInputElement
    const file = new File(['a'], 'a.txt', { type: 'text/plain' })
    Object.defineProperty(input, 'files', { value: [file], configurable: true })
    fireEvent.change(input)
    expect(enqueueMock).toHaveBeenCalledTimes(1)
  })

  it('가상 root(folderId="root")에서는 버튼이 disabled — backend는 root를 모르므로 사전 차단', () => {
    useCurrentFolderMock.mockReturnValue({ folderId: 'root' })
    render(<UploadButton />)

    const btn = screen.getByRole('button', { name: '업로드' })
    expect((btn as HTMLButtonElement).disabled).toBe(true)
    expect(btn.getAttribute('title')).toBeTruthy()
  })
})
