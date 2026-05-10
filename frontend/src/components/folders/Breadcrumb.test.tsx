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

  // 회귀 가드: workspace pivot 이후 backend breadcrumb은 workspace root부터 시작.
  // 기존 코드 `breadcrumb.slice(1)`은 root id를 두 번 포함시켜 React key 중복 warning을 일으켰다.
  // headCrumb.id 기반 filter로 root entry를 명시적으로 제거해야 한다.
  it('backend breadcrumb이 workspace root부터 시작 → root id 중복 없이 두 항목만 렌더', () => {
    ;(useCurrentFolder as ReturnType<typeof vi.fn>).mockReturnValue({
      folderId: 'fld-2',
      breadcrumb: [
        { id: 'root-dept', name: 'workspace root (server name)', slugPath: [] },
        { id: 'fld-2', name: '문서함', slugPath: ['mungseohan'] },
      ],
      isLoading: false,
    })
    ;(usePermission as ReturnType<typeof vi.fn>).mockReturnValue(ALL_FALSE)

    const qc = new QueryClient()
    const { container } = render(<Breadcrumb />, { wrapper: wrap(qc) })

    // displayCrumbs는 [headCrumb('영업부'), '문서함'] 두 항목. root id 중복 없음.
    const items = container.querySelectorAll('nav[aria-label="Breadcrumb"] > span')
    expect(items.length).toBe(2)
    // workspace head는 backend가 보내준 'workspace root (server name)'이 아닌 useWorkspaces.name으로 교체.
    expect(screen.getByText('영업부')).toBeTruthy()
    expect(screen.getByText('문서함')).toBeTruthy()
    expect(screen.queryByText('workspace root (server name)')).toBeNull()
  })
})
