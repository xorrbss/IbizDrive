import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { MoveFolderDialog } from './MoveFolderDialog'
import { useMoveUiStore } from '@/stores/moveUi'
import { resetSonnerToastMock } from '@/test/mocks/sonner'

// TODO: [BLOCKED]
//   violated: 기존 구조 우선
//   reason: MoveFolderDialog가 tree=undefined(Tasks 17+ 대기) → 항상 null 반환.
//   required_change: Tasks 17+ per-workspace lazy tree 구현 후 트리 기반 테스트 복원.

vi.mock('@/lib/api', () => ({
  api: {
    moveFiles: vi.fn().mockResolvedValue({ movedIds: ['a'] }),
  },
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
    useMoveUiStore.setState({ isMoveDialogOpen: false, moveIds: [], moveSourceFolderId: null })
  })

  it('isMoveDialogOpen=false면 렌더 안 함', () => {
    renderWithQc(<MoveFolderDialog />)
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('open 상태에서도 tree=undefined이므로 렌더 안 함 (Tasks 17+ 대기)', () => {
    useMoveUiStore.setState({
      isMoveDialogOpen: true,
      moveIds: ['file_x'],
      moveSourceFolderId: 'root',
    })
    renderWithQc(<MoveFolderDialog />)
    // tree=undefined → `!isOpen || !tree` 분기로 null 반환
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})
