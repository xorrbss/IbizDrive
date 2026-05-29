import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { SidebarNewButton } from './SidebarNewButton'

/**
 * folder-upload P5 — 사이드바 "폴더 업로드" 진입점.
 * 메뉴 항목 노출 + webkitdirectory input 설정 검증. 오케스트레이션 로직은 useFolderUpload.test가 책임.
 */

const { folderId } = vi.hoisted(() => ({ folderId: { value: 'folder1' } }))
vi.mock('@/hooks/useCurrentFolder', () => ({
  useCurrentFolder: () => ({ folderId: folderId.value }),
}))
vi.mock('@/hooks/useUpload', () => ({ useUpload: () => ({ enqueue: vi.fn() }) }))
vi.mock('@/hooks/useFolderUpload', () => ({
  useFolderUpload: () => ({ uploadFolder: vi.fn().mockResolvedValue({ errors: [] }) }),
}))
vi.mock('@/components/explorer/CreateFolderDialog', () => ({
  CreateFolderDialog: () => null,
}))

describe('SidebarNewButton — 폴더 업로드', () => {
  beforeEach(() => {
    folderId.value = 'folder1'
    vi.clearAllMocks()
  })

  it('메뉴에 "폴더 업로드" 항목 노출', () => {
    render(<SidebarNewButton />)
    fireEvent.click(screen.getByRole('button', { name: /새로 만들기/ }))
    expect(screen.getByRole('menuitem', { name: '폴더 업로드' })).toBeTruthy()
  })

  it('폴더 선택 input에 webkitdirectory 속성 설정', () => {
    const { container } = render(<SidebarNewButton />)
    const folderInput = container.querySelector('input[webkitdirectory]')
    expect(folderInput).not.toBeNull()
  })

  it('folderId 없으면(예: /trash) primary 버튼 비활성', () => {
    folderId.value = ''
    render(<SidebarNewButton />)
    const btn = screen.getByRole('button', { name: /새로 만들기/ }) as HTMLButtonElement
    expect(btn.disabled).toBe(true)
  })
})
