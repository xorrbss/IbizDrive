import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { Breadcrumb } from './Breadcrumb'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { usePermission } from '@/hooks/usePermission'
import { useShareUiStore } from '@/stores/shareUi'
import type { PermissionFlags } from '@/hooks/usePermission'

vi.mock('@/hooks/useCurrentFolder', () => ({ useCurrentFolder: vi.fn() }))
vi.mock('@/hooks/usePermission', () => ({ usePermission: vi.fn() }))
vi.mock('@/components/dnd/useFolderDroppable', () => ({
  useFolderDroppable: () => ({
    setNodeRef: () => undefined,
    isOver: false,
    isInvalid: false,
    isDragging: false,
    isSameFolder: false,
  }),
}))

const ALL_FALSE: PermissionFlags = {
  READ: false,
  UPLOAD: false,
  EDIT: false,
  MOVE: false,
  DOWNLOAD: false,
  DELETE: false,
  SHARE: false,
  PERMISSION_ADMIN: false,
  PURGE: false,
}

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('Breadcrumb (F5.3 folder share entry)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    act(() => useShareUiStore.getState().close())
  })

  it('SHARE 권한 + 비루트 폴더 → 공유 버튼 노출 + 클릭 시 shareUi.open(folder target)', () => {
    ;(useCurrentFolder as ReturnType<typeof vi.fn>).mockReturnValue({
      folderId: 'fld-2',
      breadcrumb: [
        { id: 'root', name: '내 드라이브', slugPath: [] },
        { id: 'fld-2', name: '문서함', slugPath: ['mungseohan'] },
      ],
      isLoading: false,
    })
    ;(usePermission as ReturnType<typeof vi.fn>).mockReturnValue({
      ...ALL_FALSE,
      SHARE: true,
    })

    const qc = new QueryClient()
    render(<Breadcrumb />, { wrapper: wrap(qc) })

    const shareBtn = screen.getByRole('button', { name: /문서함 폴더 공유/ })
    fireEvent.click(shareBtn)

    const state = useShareUiStore.getState()
    expect(state.isOpen).toBe(true)
    expect(state.target).toEqual({ kind: 'folder', id: 'fld-2', name: '문서함' })
  })

  it('SHARE 권한 없음 → 공유 버튼 미노출', () => {
    ;(useCurrentFolder as ReturnType<typeof vi.fn>).mockReturnValue({
      folderId: 'fld-2',
      breadcrumb: [
        { id: 'root', name: '내 드라이브', slugPath: [] },
        { id: 'fld-2', name: '문서함', slugPath: ['mungseohan'] },
      ],
      isLoading: false,
    })
    ;(usePermission as ReturnType<typeof vi.fn>).mockReturnValue(ALL_FALSE)

    const qc = new QueryClient()
    render(<Breadcrumb />, { wrapper: wrap(qc) })
    expect(screen.queryByRole('button', { name: /폴더 공유/ })).toBeNull()
  })

  it('루트(breadcrumb 1개) → 공유 버튼 미노출 (정책: 시스템 루트 공유 차단)', () => {
    ;(useCurrentFolder as ReturnType<typeof vi.fn>).mockReturnValue({
      folderId: 'root',
      breadcrumb: [{ id: 'root', name: '내 드라이브', slugPath: [] }],
      isLoading: false,
    })
    ;(usePermission as ReturnType<typeof vi.fn>).mockReturnValue({
      ...ALL_FALSE,
      SHARE: true,
    })

    const qc = new QueryClient()
    render(<Breadcrumb />, { wrapper: wrap(qc) })
    expect(screen.queryByRole('button', { name: /폴더 공유/ })).toBeNull()
  })
})
