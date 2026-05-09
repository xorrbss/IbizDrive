import { describe, it, expect } from 'vitest'
import {
  buildWorkspacePath,
  parseWorkspaceUrl,
  type SidebarSectionKind,
} from './workspacePath'

describe('buildWorkspacePath', () => {
  it('builds /d/<deptId>/<folderId>/<...slug>', () => {
    expect(buildWorkspacePath({ kind: 'department', workspaceId: 'd1' }, 'f1', ['Q1', 'reports']))
      .toBe('/d/d1/f1/Q1/reports')
  })
  it('builds /t/<teamId>/<folderId>', () => {
    expect(buildWorkspacePath({ kind: 'team', workspaceId: 't1' }, 'f1', []))
      .toBe('/t/t1/f1')
  })
  it('builds /shared/<folderId>', () => {
    expect(buildWorkspacePath({ kind: 'shared' }, 'fX', ['nested']))
      .toBe('/shared/fX/nested')
  })
  it('encodes slug segments', () => {
    expect(buildWorkspacePath({ kind: 'department', workspaceId: 'd1' }, 'f1', ['보고서 A']))
      .toBe('/d/d1/f1/%EB%B3%B4%EA%B3%A0%EC%84%9C%20A')
  })
  it('builds workspace landing (no parts) — /d/<deptId>', () => {
    expect(buildWorkspacePath({ kind: 'department', workspaceId: 'd1' }, null, []))
      .toBe('/d/d1')
  })
})

describe('parseWorkspaceUrl', () => {
  it('parses /d/d1/f1/x/y', () => {
    expect(parseWorkspaceUrl('/d/d1/f1/x/y')).toEqual({
      section: 'department',
      workspaceId: 'd1',
      folderId: 'f1',
      slugPath: ['x', 'y'],
    })
  })
  it('parses /t/t1', () => {
    expect(parseWorkspaceUrl('/t/t1')).toEqual({
      section: 'team',
      workspaceId: 't1',
      folderId: null,
      slugPath: [],
    })
  })
  it('parses /shared/fX', () => {
    expect(parseWorkspaceUrl('/shared/fX')).toEqual({
      section: 'shared',
      workspaceId: null,
      folderId: 'fX',
      slugPath: [],
    })
  })
  it('returns null for unrelated paths', () => {
    expect(parseWorkspaceUrl('/admin/users')).toBeNull()
    expect(parseWorkspaceUrl('/login')).toBeNull()
  })
})

describe('SidebarSectionKind', () => {
  it('exports the expected union', () => {
    const valid: SidebarSectionKind[] = ['department', 'team', 'shared']
    expect(valid).toHaveLength(3)
  })
})
