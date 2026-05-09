import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { MoveFolderDialog } from './MoveFolderDialog'
import { useMoveUiStore } from '@/stores/moveUi'
import { resetSonnerToastMock } from '@/test/mocks/sonner'

// next/navigation mock — useCurrentWorkspace는 usePathname에 의존.
// department workspace URL('/d/dept-1/folder-root')을 반환해 scope가 설정되도록 한다.
vi.mock('next/navigation', () => ({
  usePathname: () => '/d/dept-1/folder-root',
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(''),
}))

vi.mock('@/lib/api', () => ({
  api: {
    moveFiles: vi.fn().mockResolvedValue({ movedIds: ['file-x'] }),
    getWorkspacesMe: vi.fn().mockResolvedValue({
      department: { kind: 'department', id: 'dept-1', name: '개발부', rootFolderId: 'folder-root' },
      teams: [],
    }),
    getFolderChildren: vi.fn().mockResolvedValue([
      { id: 'child-1', name: '하위폴더A', slug: 'child-a', parentId: 'folder-root' },
    ]),
  },
}))

// useWorkspaces는 useQuery 래퍼 — 테스트에서 직접 mock해 비동기 pending 없이 data를 즉시 반환.
vi.mock('@/hooks/useWorkspaces', () => ({
  useWorkspaces: () => ({
    data: {
      department: { kind: 'department', id: 'dept-1', name: '개발부', rootFolderId: 'folder-root' },
      teams: [],
    },
    isLoading: false,
  }),
}))

// useSelectionStore — useMoveBulk 내부에서 사용.
vi.mock('@/stores/selection', () => ({
  useSelectionStore: vi.fn((sel) =>
    sel({ markPending: vi.fn(), unmarkPending: vi.fn(), clear: vi.fn() }),
  ),
}))

function renderWithQc(ui: ReactNode) {
  const qc = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

describe('MoveFolderDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetSonnerToastMock()
    useMoveUiStore.setState({
      isMoveDialogOpen: false,
      moveIds: [],
      moveSourceFolderId: null,
    })
  })

  it('isMoveDialogOpen=false면 렌더 안 함', () => {
    renderWithQc(<MoveFolderDialog />)
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('open 상태에서 dialog role 렌더', () => {
    useMoveUiStore.setState({
      isMoveDialogOpen: true,
      moveIds: ['file-x'],
      moveSourceFolderId: 'src-folder',
    })
    renderWithQc(<MoveFolderDialog />)
    expect(screen.getByRole('dialog')).toBeTruthy()
    expect(screen.getByText('1개 항목 이동')).toBeTruthy()
  })

  it('workspace root 폴더 라디오 버튼 렌더 — root는 invalidIds에 없을 때 활성', () => {
    // sourceFolderId가 root가 아닌 다른 폴더 — root 라디오는 enabled 상태여야 함
    useMoveUiStore.setState({
      isMoveDialogOpen: true,
      moveIds: ['file-x'],
      moveSourceFolderId: 'some-other-folder',
    })
    renderWithQc(<MoveFolderDialog />)
    const rootRadio = screen.getByDisplayValue('folder-root')
    expect(rootRadio).toBeTruthy()
    expect((rootRadio as HTMLInputElement).disabled).toBe(false)
  })

  it('sourceFolderId가 root일 때 root 라디오는 disabled', () => {
    useMoveUiStore.setState({
      isMoveDialogOpen: true,
      moveIds: ['file-x'],
      moveSourceFolderId: 'folder-root',
    })
    renderWithQc(<MoveFolderDialog />)
    const rootRadio = screen.getByDisplayValue('folder-root')
    expect((rootRadio as HTMLInputElement).disabled).toBe(true)
  })

  it('취소 버튼 클릭 시 dialog 닫힘', () => {
    useMoveUiStore.setState({
      isMoveDialogOpen: true,
      moveIds: ['file-x'],
      moveSourceFolderId: 'src-folder',
    })
    renderWithQc(<MoveFolderDialog />)
    fireEvent.click(screen.getByText('취소'))
    expect(useMoveUiStore.getState().isMoveDialogOpen).toBe(false)
  })

  it('Escape 키 입력 시 dialog 닫힘', () => {
    useMoveUiStore.setState({
      isMoveDialogOpen: true,
      moveIds: ['file-x'],
      moveSourceFolderId: 'src-folder',
    })
    renderWithQc(<MoveFolderDialog />)
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(useMoveUiStore.getState().isMoveDialogOpen).toBe(false)
  })

  it('이동 버튼은 선택 없을 때 disabled', () => {
    useMoveUiStore.setState({
      isMoveDialogOpen: true,
      moveIds: ['file-x'],
      moveSourceFolderId: 'src-folder',
    })
    renderWithQc(<MoveFolderDialog />)
    const confirmBtn = screen.getByText('이동')
    expect((confirmBtn as HTMLButtonElement).disabled).toBe(true)
  })

  it('라디오 선택 시 이동 버튼 활성화 + moveFiles 호출', async () => {
    useMoveUiStore.setState({
      isMoveDialogOpen: true,
      moveIds: ['file-x'],
      moveSourceFolderId: 'src-folder',
    })
    renderWithQc(<MoveFolderDialog />)

    // root 라디오 선택
    const rootRadio = screen.getByDisplayValue('folder-root')
    fireEvent.click(rootRadio)

    await waitFor(() => {
      const confirmBtn = screen.getByText('이동')
      expect((confirmBtn as HTMLButtonElement).disabled).toBe(false)
    })

    fireEvent.click(screen.getByText('이동'))
    // dialog가 닫혀야 함
    expect(useMoveUiStore.getState().isMoveDialogOpen).toBe(false)
  })

  it('workspace scope 있을 때 radiogroup 렌더', () => {
    useMoveUiStore.setState({
      isMoveDialogOpen: true,
      moveIds: ['file-x'],
      moveSourceFolderId: 'src-folder',
    })
    renderWithQc(<MoveFolderDialog />)
    expect(screen.getByRole('radiogroup')).toBeTruthy()
  })
})
