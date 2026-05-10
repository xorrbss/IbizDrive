import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { TeamsListPanel } from './TeamsListPanel'
import type { AdminTeamSummary } from '@/lib/api'

const TEAMS: AdminTeamSummary[] = [
  {
    id: 't-1',
    name: '디자인 시스템',
    description: '컴포넌트와 토큰 관리',
    color: '#5B7FCC',
    leadId: 'u-1',
    memberCount: 5,
    archived: false,
    createdAt: '2026-05-01T00:00:00Z',
  },
  {
    id: 't-2',
    name: '인프라',
    description: 'CI/CD + 모니터링',
    color: '#5BA08A',
    leadId: 'u-2',
    memberCount: 3,
    archived: false,
    createdAt: '2026-05-02T00:00:00Z',
  },
]

describe('TeamsListPanel', () => {
  it('팀 row와 카운트를 렌더링한다', () => {
    render(
      <TeamsListPanel
        teams={TEAMS}
        selectedId={null}
        onSelect={() => {}}
        query=""
        onQueryChange={() => {}}
      />,
    )
    expect(screen.getByText('디자인 시스템')).toBeTruthy()
    expect(screen.getByText('인프라')).toBeTruthy()
    expect(screen.getByText('5명')).toBeTruthy()
  })

  it('검색어로 필터링한다 (이름 매칭)', () => {
    render(
      <TeamsListPanel
        teams={TEAMS}
        selectedId={null}
        onSelect={() => {}}
        query="인프"
        onQueryChange={() => {}}
      />,
    )
    expect(screen.queryByText('디자인 시스템')).toBeNull()
    expect(screen.getByText('인프라')).toBeTruthy()
  })

  it('검색어로 필터링한다 (설명 매칭, 대소문자 무관)', () => {
    render(
      <TeamsListPanel
        teams={TEAMS}
        selectedId={null}
        onSelect={() => {}}
        query="CI/CD"
        onQueryChange={() => {}}
      />,
    )
    expect(screen.getByText('인프라')).toBeTruthy()
    expect(screen.queryByText('디자인 시스템')).toBeNull()
  })

  it('검색 결과 없음 메시지', () => {
    render(
      <TeamsListPanel
        teams={TEAMS}
        selectedId={null}
        onSelect={() => {}}
        query="없는팀"
        onQueryChange={() => {}}
      />,
    )
    expect(screen.getByText('검색 결과가 없습니다.')).toBeTruthy()
  })

  it('row 클릭 시 onSelect(id) 호출', () => {
    const onSelect = vi.fn()
    render(
      <TeamsListPanel
        teams={TEAMS}
        selectedId={null}
        onSelect={onSelect}
        query=""
        onQueryChange={() => {}}
      />,
    )
    fireEvent.click(screen.getByText('인프라'))
    expect(onSelect).toHaveBeenCalledWith('t-2')
  })

  it('selectedId 일치 row에 active class', () => {
    const { container } = render(
      <TeamsListPanel
        teams={TEAMS}
        selectedId="t-1"
        onSelect={() => {}}
        query=""
        onQueryChange={() => {}}
      />,
    )
    const active = container.querySelector('.team-row.active')
    expect(active?.textContent).toContain('디자인 시스템')
  })

  it('canCreate=false면 등록 버튼 숨김', () => {
    render(
      <TeamsListPanel
        teams={TEAMS}
        selectedId={null}
        onSelect={() => {}}
        query=""
        onQueryChange={() => {}}
        onCreate={() => {}}
        canCreate={false}
      />,
    )
    expect(screen.queryByLabelText('팀 등록')).toBeNull()
  })

  it('canCreate=true이고 onCreate 있으면 등록 버튼 노출 + 클릭 시 호출', () => {
    const onCreate = vi.fn()
    render(
      <TeamsListPanel
        teams={TEAMS}
        selectedId={null}
        onSelect={() => {}}
        query=""
        onQueryChange={() => {}}
        onCreate={onCreate}
      />,
    )
    fireEvent.click(screen.getByLabelText('팀 등록'))
    expect(onCreate).toHaveBeenCalled()
  })

  it('archived 팀에 (archived) 마커', () => {
    render(
      <TeamsListPanel
        teams={[{ ...TEAMS[0], archived: true }]}
        selectedId={null}
        onSelect={() => {}}
        query=""
        onQueryChange={() => {}}
      />,
    )
    expect(screen.getByText(/archived/)).toBeTruthy()
  })
})
