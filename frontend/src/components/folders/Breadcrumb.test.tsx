import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { Breadcrumb } from './Breadcrumb'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { usePermission } from '@/hooks/usePermission'
import { useShareUiStore } from '@/stores/shareUi'
import { useCurrentWorkspace } from '@/hooks/useCurrentWorkspace'
import { useWorkspaces } from '@/hooks/useWorkspaces'
import type { PermissionFlags } from '@/hooks/usePermission'

vi.mock('@/hooks/useCurrentFolder', () => ({ useCurrentFolder: vi.fn() }))
vi.mock('@/hooks/usePermission', () => ({ usePermission: vi.fn() }))
vi.mock('@/hooks/useCurrentWorkspace', () => ({ useCurrentWorkspace: vi.fn() }))
vi.mock('@/hooks/useWorkspaces', () => ({ useWorkspaces: vi.fn() }))
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

const DEPT_WORKSPACE = {
  section: 'department' as const,
  workspaceId: 'dept-1',
  folderId: 'root-dept',
  slugPath: [],
}

const WORKSPACES_DATA = {
  data: {
    department: { kind: 'department' as const, id: 'dept-1', name: '영업부', rootFolderId: 'root-dept' },
    teams: [],
  },
}

describe('Breadcrumb (F5.3 folder share entry)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    act(() => useShareUiStore.getState().close())
    // spec §4.5 §2: 기본적으로 부서 workspace 컨텍스트로 설정
    ;(useCurrentWorkspace as ReturnType<typeof vi.fn>).mockReturnValue(DEPT_WORKSPACE)
    ;(useWorkspaces as ReturnType<typeof vi.fn>).mockReturnValue(WORKSPACES_DATA)
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

    // workspace head crumb이 '영업부'로 표시되는지 확인
    expect(screen.getByText('영업부')).toBeTruthy()

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
    // workspace head crumb('영업부')가 유일한 항목으로 표시
    expect(screen.getByText('영업부')).toBeTruthy()
    expect(screen.queryByRole('button', { name: /폴더 공유/ })).toBeNull()
  })

  it('비workspace 라우트 (useCurrentWorkspace=null) → workspace head 없이 기존 breadcrumb 표시', () => {
    ;(useCurrentWorkspace as ReturnType<typeof vi.fn>).mockReturnValue(null)
    ;(useCurrentFolder as ReturnType<typeof vi.fn>).mockReturnValue({
      folderId: 'root',
      breadcrumb: [{ id: 'root', name: '내 드라이브', slugPath: [] }],
      isLoading: false,
    })
    ;(usePermission as ReturnType<typeof vi.fn>).mockReturnValue(ALL_FALSE)

    const qc = new QueryClient()
    render(<Breadcrumb />, { wrapper: wrap(qc) })
    expect(screen.getByText('내 드라이브')).toBeTruthy()
  })
})
