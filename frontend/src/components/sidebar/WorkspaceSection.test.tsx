import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'

// WorkspaceFolderTree 의 react-query 의존을 회피 — 본 테스트의 관심사는 settings link 시각 가드
vi.mock('./WorkspaceFolderTree', () => ({
  WorkspaceFolderTree: ({ rootName }: { rootName: string }) => <div>{rootName}</div>,
}))

import { WorkspaceSection } from './WorkspaceSection'

describe('WorkspaceSection', () => {
  it('team workspace — settings link 가 Settings 아이콘 + icon-button 스타일', () => {
    render(
      <WorkspaceSection
        kind="team"
        workspaceId="t-1"
        title="ProjectAlpha"
        rootFolderId="r-1"
      />,
    )
    const link = screen.getByRole('link', { name: 'ProjectAlpha 팀 설정' })
    expect(link.getAttribute('href')).toBe('/t/t-1/settings/members')
    // icon-button 가드: bg-surface-2 hover + rounded + 정렬 + Settings 아이콘 svg
    expect(link.className).toContain('hover:bg-surface-2')
    expect(link.className).toContain('rounded')
    expect(link.className).toContain('group-hover:opacity-100')
    expect(link.querySelector('svg')).toBeTruthy()
    // 미완성 텍스트 회귀 차단
    expect(link.textContent).not.toContain('설정')
    expect(link.className).not.toContain('hover:underline')
  })

  it('department workspace — settings link 미렌더', () => {
    render(
      <WorkspaceSection
        kind="department"
        workspaceId="d-1"
        title="영업부"
        rootFolderId="r-d"
      />,
    )
    expect(screen.queryByRole('link', { name: /설정/ })).toBeNull()
  })

  it('archived team — settings link 미렌더 + [보관됨] 표시', () => {
    render(
      <WorkspaceSection
        kind="team"
        workspaceId="t-2"
        title="OldTeam"
        rootFolderId="r-2"
        archived
      />,
    )
    expect(screen.queryByRole('link', { name: /설정/ })).toBeNull()
    expect(screen.getByLabelText('보관됨').textContent).toBe('[보관됨]')
  })
})
