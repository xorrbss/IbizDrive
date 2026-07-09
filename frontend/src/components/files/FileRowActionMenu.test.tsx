import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, act } from '@testing-library/react'
import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { FileRowActionMenu } from './FileRowActionMenu'
import { useMoveUiStore } from '@/stores/moveUi'
import { useRenameUiStore } from '@/stores/renameUi'
import { useShareUiStore } from '@/stores/shareUi'
import type { FileItem } from '@/types/file'

vi.mock('@/lib/api', () => ({
  api: {
    downloadFile: vi.fn(),
    deleteBulk: vi.fn(() => Promise.resolve()),
    restoreFile: vi.fn(() => Promise.resolve()),
    restoreFolder: vi.fn(() => Promise.resolve()),
    getEffectivePermissions: vi.fn(() =>
      Promise.resolve([
        'READ',
        'UPLOAD',
        'EDIT',
        'MOVE',
        'DOWNLOAD',
        'DELETE',
        'SHARE',
        'PERMISSION_ADMIN',
        'PURGE',
      ]),
    ),
  },
}))

const FILE_ITEM: FileItem = {
  id: 'f1',
  name: 'a.pdf',
  type: 'file',
  mimeType: 'application/pdf',
  size: 1000,
  updatedAt: '2026-04-25T00:00:00Z',
  updatedBy: 'me',
  parentId: 'root',
}

const FOLDER_ITEM: FileItem = {
  id: 'd1',
  name: 'dir',
  type: 'folder',
  mimeType: null,
  size: null,
  updatedAt: '2026-04-25T00:00:00Z',
  updatedBy: 'me',
  parentId: 'root',
}

function wrap(node: ReactNode) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 } },
  })
  const allPermissions = [
    'READ',
    'UPLOAD',
    'EDIT',
    'MOVE',
    'DOWNLOAD',
    'DELETE',
    'SHARE',
    'PERMISSION_ADMIN',
    'PURGE',
  ]
  // 권한 query 결과를 미리 캐시에 주입 (모든 플래그 true) — 행 메뉴는 node별 권한을 본다.
  qc.setQueryData(['explorer', 'permissions', 'node', 'f1'], allPermissions)
  qc.setQueryData(['explorer', 'permissions', 'node', 'd1'], allPermissions)
  return <QueryClientProvider client={qc}>{node}</QueryClientProvider>
}

beforeEach(() => {
  useMoveUiStore.setState({
    isMoveDialogOpen: false,
    moveIds: [],
    moveSourceFolderId: null,
  })
  useRenameUiStore.setState({
    isOpen: false,
    targetId: null,
    targetName: '',
    error: null,
  })
  useShareUiStore.setState({ isOpen: false, target: null })
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('FileRowActionMenu', () => {
  it('초기 상태 — 메뉴 닫힘, "더 보기" 버튼만 노출', () => {
    render(wrap(<FileRowActionMenu item={FILE_ITEM} folderId="root" isPending={false} />))
    expect(screen.getByRole('button', { name: '더 보기' })).toBeTruthy()
    expect(screen.queryByRole('menu')).toBeNull()
  })

  it('"더 보기" 클릭 시 메뉴 열림 + aria-expanded=true', () => {
    render(wrap(<FileRowActionMenu item={FILE_ITEM} folderId="root" isPending={false} />))
    const trigger = screen.getByRole('button', { name: '더 보기' })
    act(() => fireEvent.click(trigger))
    expect(trigger.getAttribute('aria-expanded')).toBe('true')
    expect(screen.getByRole('menu', { name: /a\.pdf/ })).toBeTruthy()
  })

  it('파일 — 5개 액션 (다운로드/이동/이름 변경/공유/휴지통)', () => {
    render(wrap(<FileRowActionMenu item={FILE_ITEM} folderId="root" isPending={false} />))
    act(() => fireEvent.click(screen.getByRole('button', { name: '더 보기' })))
    expect(screen.getByRole('menuitem', { name: /다운로드/ })).toBeTruthy()
    expect(screen.getByRole('menuitem', { name: /이동/ })).toBeTruthy()
    expect(screen.getByRole('menuitem', { name: /이름 변경/ })).toBeTruthy()
    expect(screen.getByRole('menuitem', { name: /공유/ })).toBeTruthy()
    expect(screen.getByRole('menuitem', { name: /휴지통/ })).toBeTruthy()
  })

  it('폴더 — 다운로드 비활성 (파일만 지원)', () => {
    render(wrap(<FileRowActionMenu item={FOLDER_ITEM} folderId="root" isPending={false} />))
    act(() => fireEvent.click(screen.getByRole('button', { name: '더 보기' })))
    const dl = screen.getByRole('menuitem', { name: /다운로드/ }) as HTMLButtonElement
    expect(dl.disabled).toBe(true)
    expect(dl.getAttribute('title')).toMatch(/파일만/)
  })

  it('이동 클릭 → moveUi.openMoveDialog([id], folderId) 호출 + 메뉴 닫힘', () => {
    render(wrap(<FileRowActionMenu item={FILE_ITEM} folderId="root" isPending={false} />))
    act(() => fireEvent.click(screen.getByRole('button', { name: '더 보기' })))
    act(() => fireEvent.click(screen.getByRole('menuitem', { name: /이동/ })))
    expect(useMoveUiStore.getState().isMoveDialogOpen).toBe(true)
    expect(useMoveUiStore.getState().moveIds).toEqual(['f1'])
    expect(useMoveUiStore.getState().moveSourceFolderId).toBe('root')
    expect(screen.queryByRole('menu')).toBeNull()
  })

  it('이름 변경 클릭 → renameUi.open(id, name) 호출 + 메뉴 닫힘', () => {
    render(wrap(<FileRowActionMenu item={FILE_ITEM} folderId="root" isPending={false} />))
    act(() => fireEvent.click(screen.getByRole('button', { name: '더 보기' })))
    act(() => fireEvent.click(screen.getByRole('menuitem', { name: /이름 변경/ })))
    expect(useRenameUiStore.getState().isOpen).toBe(true)
    expect(useRenameUiStore.getState().targetId).toBe('f1')
    expect(useRenameUiStore.getState().targetName).toBe('a.pdf')
  })

  it('공유 클릭 → shareUi.open({kind, id, name}) 호출', () => {
    render(wrap(<FileRowActionMenu item={FILE_ITEM} folderId="root" isPending={false} />))
    act(() => fireEvent.click(screen.getByRole('button', { name: '더 보기' })))
    act(() => fireEvent.click(screen.getByRole('menuitem', { name: /공유/ })))
    expect(useShareUiStore.getState().isOpen).toBe(true)
    expect(useShareUiStore.getState().target).toEqual({
      kind: 'file',
      id: 'f1',
      name: 'a.pdf',
    })
  })

  it('Esc 키 → 메뉴 닫힘', () => {
    render(wrap(<FileRowActionMenu item={FILE_ITEM} folderId="root" isPending={false} />))
    act(() => fireEvent.click(screen.getByRole('button', { name: '더 보기' })))
    expect(screen.queryByRole('menu')).not.toBeNull()
    act(() => fireEvent.keyDown(document, { key: 'Escape' }))
    expect(screen.queryByRole('menu')).toBeNull()
  })

  it('outside mousedown → 메뉴 닫힘', () => {
    render(
      wrap(
        <div>
          <FileRowActionMenu item={FILE_ITEM} folderId="root" isPending={false} />
          <button data-testid="outside">outside</button>
        </div>,
      ),
    )
    act(() => fireEvent.click(screen.getByRole('button', { name: '더 보기' })))
    expect(screen.queryByRole('menu')).not.toBeNull()
    act(() => fireEvent.mouseDown(screen.getByTestId('outside')))
    expect(screen.queryByRole('menu')).toBeNull()
  })

  it('isPending=true → 트리거 disabled, 클릭 시 메뉴 안 열림', () => {
    render(wrap(<FileRowActionMenu item={FILE_ITEM} folderId="root" isPending={true} />))
    const trigger = screen.getByRole('button', { name: '더 보기' }) as HTMLButtonElement
    expect(trigger.disabled).toBe(true)
    act(() => fireEvent.click(trigger))
    expect(screen.queryByRole('menu')).toBeNull()
  })
})
