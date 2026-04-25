import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { RenameDialog } from './RenameDialog'
import { useRenameUiStore } from '@/stores/renameUi'
import { useFilesInFolder } from '@/hooks/useFilesInFolder'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { useSortParams } from '@/hooks/useSortParams'
import { api } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  api: { renameFile: vi.fn() },
}))

vi.mock('@/hooks/useFilesInFolder', () => ({
  useFilesInFolder: vi.fn(),
}))

vi.mock('@/hooks/useCurrentFolder', () => ({
  useCurrentFolder: vi.fn(),
}))

vi.mock('@/hooks/useSortParams', () => ({
  useSortParams: vi.fn(),
}))

function makeWrapper(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const MOCK_ITEM = {
  id: 'file_x',
  name: 'old.txt',
  parentId: 'root',
  type: 'file' as const,
  size: 1000,
  mimeType: 'text/plain',
  updatedAt: '2026-04-25T00:00:00Z',
  updatedBy: 'me',
}

function renderDialog() {
  const qc = new QueryClient()
  const Wrapper = makeWrapper(qc)
  return render(
    <Wrapper>
      <RenameDialog />
    </Wrapper>,
  )
}

describe('RenameDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useRenameUiStore.setState({
      isOpen: false,
      targetId: null,
      targetName: '',
      error: null,
    })
    ;(useFilesInFolder as ReturnType<typeof vi.fn>).mockReturnValue({
      data: [MOCK_ITEM],
    })
    ;(useCurrentFolder as ReturnType<typeof vi.fn>).mockReturnValue({
      folderId: 'root',
    })
    ;(useSortParams as ReturnType<typeof vi.fn>).mockReturnValue({
      sort: 'name',
      dir: 'asc',
    })
  })

  it('isOpen=false → 렌더 X', () => {
    const { container } = renderDialog()
    expect(container.querySelector('[role="dialog"]')).toBeNull()
  })

  it('isOpen=true → role=dialog 렌더, input에 기존 이름 채워짐', () => {
    useRenameUiStore.setState({
      isOpen: true,
      targetId: 'file_x',
      targetName: 'old.txt',
      error: null,
    })
    renderDialog()
    expect(screen.queryByRole('dialog')).not.toBeNull()
    const input = screen.getByLabelText(/새 이름/) as HTMLInputElement
    expect(input.value).toBe('old.txt')
  })

  it('Enter 제출 → renameFile 호출', async () => {
    useRenameUiStore.setState({
      isOpen: true,
      targetId: 'file_x',
      targetName: 'old.txt',
      error: null,
    })
    ;(api.renameFile as ReturnType<typeof vi.fn>).mockResolvedValue({
      ...MOCK_ITEM,
      name: 'new.txt',
    })
    renderDialog()
    const input = screen.getByLabelText(/새 이름/) as HTMLInputElement
    fireEvent.change(input, { target: { value: 'new.txt' } })
    fireEvent.submit(input.closest('form')!)
    await waitFor(() => {
      expect(api.renameFile).toHaveBeenCalledWith('file_x', 'new.txt')
    })
  })

  it('ESC → close()', () => {
    useRenameUiStore.setState({
      isOpen: true,
      targetId: 'file_x',
      targetName: 'old.txt',
      error: null,
    })
    renderDialog()
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(useRenameUiStore.getState().isOpen).toBe(false)
  })

  it('error 있으면 role=alert 메시지 표시', () => {
    useRenameUiStore.setState({
      isOpen: true,
      targetId: 'file_x',
      targetName: 'old.txt',
      error: '같은 이름의 파일/폴더가 있습니다',
    })
    renderDialog()
    const alert = screen.getByRole('alert')
    expect(alert.textContent).toContain('같은 이름')
  })

  it('빈 입력일 때 제출 버튼 disabled', () => {
    useRenameUiStore.setState({
      isOpen: true,
      targetId: 'file_x',
      targetName: 'old.txt',
      error: null,
    })
    renderDialog()
    const input = screen.getByLabelText(/새 이름/) as HTMLInputElement
    fireEvent.change(input, { target: { value: '   ' } })
    const btn = screen.getByRole('button', { name: '확인' }) as HTMLButtonElement
    expect(btn.disabled).toBe(true)
  })

  it('동일 이름이면 제출 버튼 disabled (no-op 방지)', () => {
    useRenameUiStore.setState({
      isOpen: true,
      targetId: 'file_x',
      targetName: 'old.txt',
      error: null,
    })
    renderDialog()
    const btn = screen.getByRole('button', { name: '확인' }) as HTMLButtonElement
    expect(btn.disabled).toBe(true)
  })
})
