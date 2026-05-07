import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { MoveFolderDialog } from './MoveFolderDialog'
import { useMoveUiStore } from '@/stores/moveUi'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'
import { toastSpy, resetSonnerToastMock } from '@/test/mocks/sonner'
import type { FolderNode } from '@/types/folder'

const tree: FolderNode = {
  id: 'root', parentId: null, name: '내 드라이브', slug: '',
  children: [
    { id: 'sales', parentId: 'root', name: '영업팀', slug: '영업팀',
      children: [{ id: 'contracts', parentId: 'sales', name: '계약서', slug: '계약서' }] },
    { id: 'hr', parentId: 'root', name: '인사팀', slug: '인사팀' },
  ],
}

vi.mock('@/lib/api', () => ({
  api: {
    moveFiles: vi.fn().mockResolvedValue({ movedIds: ['a'] }),
    // useFolderTree refetch가 invalidations.afterFilesMoved에서 트리거되므로
    // queryFn을 정의해줘야 한다 (없으면 default retry x 3 + backoff로 hang).
    getFolderTree: vi.fn(),
  },
}))

function renderWithQc(ui: ReactNode) {
  const qc = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  })
  qc.setQueryData(qk.folderTree(), tree)
  // refetch 시에도 안전하도록 mock 설정 (위 vi.mock과 별개로 런타임 구현 주입)
  ;(api.getFolderTree as ReturnType<typeof vi.fn>).mockResolvedValue(tree)
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

describe('MoveFolderDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetSonnerToastMock()
    useMoveUiStore.setState({ isMoveDialogOpen: false, moveIds: [], moveSourceFolderId: null })
  })

  it('isMoveDialogOpen=false면 렌더 안 함', () => {
    renderWithQc(<MoveFolderDialog />)
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('open 시 트리 라디오 표시', () => {
    useMoveUiStore.setState({
      isMoveDialogOpen: true,
      moveIds: ['file_x'],
      moveSourceFolderId: 'root',
    })
    renderWithQc(<MoveFolderDialog />)
    expect(screen.getByRole('dialog')).toBeTruthy()
    expect(screen.getByLabelText(/영업팀/)).toBeTruthy()
    expect(screen.getByLabelText(/계약서/)).toBeTruthy()
    expect(screen.getByLabelText(/인사팀/)).toBeTruthy()
  })

  it('source 폴더 라디오는 disabled', () => {
    useMoveUiStore.setState({
      isMoveDialogOpen: true,
      moveIds: ['file_x'],
      moveSourceFolderId: 'sales',
    })
    renderWithQc(<MoveFolderDialog />)
    const salesRadio = screen.getByLabelText(/영업팀/) as HTMLInputElement
    expect(salesRadio.disabled).toBe(true)
  })

  it('Esc로 닫힘', () => {
    useMoveUiStore.setState({
      isMoveDialogOpen: true,
      moveIds: ['file_x'],
      moveSourceFolderId: 'root',
    })
    renderWithQc(<MoveFolderDialog />)
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(useMoveUiStore.getState().isMoveDialogOpen).toBe(false)
  })

  it('이동 버튼 클릭 시 moveFiles 호출', async () => {
    useMoveUiStore.setState({
      isMoveDialogOpen: true,
      moveIds: ['file_x'],
      moveSourceFolderId: 'root',
    })
    renderWithQc(<MoveFolderDialog />)
    fireEvent.click(screen.getByLabelText(/인사팀/))
    fireEvent.click(screen.getByRole('button', { name: '이동' }))
    await waitFor(() => {
      // P3 — moveFiles signature 변경 (ids → items: {id,type}[]).
      // 캐시 미스(setQueryData 미설정) 항목은 type='file' 폴백.
      expect(api.moveFiles).toHaveBeenCalledWith(
        [{ id: 'file_x', type: 'file' }],
        'hr',
      )
    })
    expect(useMoveUiStore.getState().isMoveDialogOpen).toBe(false)
  })

  it('성공 시 toast.success 호출', async () => {
    useMoveUiStore.setState({
      isMoveDialogOpen: true,
      moveIds: ['file_x', 'file_y'],
      moveSourceFolderId: 'root',
    })
    renderWithQc(<MoveFolderDialog />)
    fireEvent.click(screen.getByLabelText(/인사팀/))
    fireEvent.click(screen.getByRole('button', { name: '이동' }))
    await waitFor(() => {
      expect(toastSpy('success')).toHaveBeenCalledWith('2개 항목을 이동했습니다')
    })
  })

  it('실패 시 toast.error 호출', async () => {
    ;(api.moveFiles as ReturnType<typeof vi.fn>).mockRejectedValueOnce({
      status: 400,
      code: 'MOVE_INTO_SELF',
    })
    useMoveUiStore.setState({
      isMoveDialogOpen: true,
      moveIds: ['file_x'],
      moveSourceFolderId: 'root',
    })
    renderWithQc(<MoveFolderDialog />)
    fireEvent.click(screen.getByLabelText(/인사팀/))
    fireEvent.click(screen.getByRole('button', { name: '이동' }))
    await waitFor(() => {
      expect(toastSpy('error')).toHaveBeenCalledWith(
        '이동에 실패했습니다. 다시 시도해 주세요.',
      )
    })
  })
})
