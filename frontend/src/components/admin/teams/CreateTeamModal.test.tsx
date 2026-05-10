import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'

// useMe 모킹 — creator 컨텍스트 주입 (myUserId 채워지면 멤버/리더 자동 설정)
vi.mock('@/hooks/useMe', () => ({
  useMe: () => ({
    data: {
      user: { id: 'u-me', email: 'me@x.io', name: 'Me', kind: 'human', mustChangePassword: false },
      departments: [],
      roles: ['ADMIN'],
      effectivePermissionsCacheKey: 'k',
    },
    isLoading: false,
    isError: false,
  }),
}))

// MemberPickerModal 격리 — 자체 테스트가 별도로 있고, 본 테스트는 CreateTeam 폼 로직 검증
vi.mock('./MemberPickerModal', () => ({
  MemberPickerModal: ({ onClose }: { onClose: () => void }) => (
    <div data-testid="member-picker-stub">
      <button onClick={onClose}>stub-close</button>
    </div>
  ),
}))

const createMutateMock = vi.fn()
vi.mock('@/hooks/useAdminTeams', () => ({
  useAdminCreateTeamWithMetadata: () => ({
    mutateAsync: createMutateMock,
    isPending: false,
  }),
}))

import { CreateTeamModal, TEAM_COLORS } from './CreateTeamModal'

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

describe('CreateTeamModal', () => {
  beforeEach(() => {
    createMutateMock.mockReset()
    createMutateMock.mockResolvedValue({
      id: 't-new',
      name: '디자인',
      description: null,
      visibility: 'PRIVATE',
      rootFolderId: 'f',
      createdAt: '2026-05-10T00:00:00Z',
      archivedAt: null,
    })
  })

  it('렌더링: 이름/설명/색상 swatches/리더 select', () => {
    wrap(<CreateTeamModal onClose={() => {}} />)
    expect(screen.getByLabelText('팀 이름')).toBeTruthy()
    expect(screen.getByLabelText('설명')).toBeTruthy()
    expect(screen.getByLabelText('팀 리더')).toBeTruthy()
    // 8색 swatch
    const swatches = screen.getAllByRole('radio')
    expect(swatches.length).toBe(TEAM_COLORS.length)
  })

  it('이름 빈 상태에서는 제출 버튼 disabled', () => {
    wrap(<CreateTeamModal onClose={() => {}} />)
    const submit = screen.getByRole('button', { name: '팀 등록' }) as HTMLButtonElement
    expect(submit.disabled).toBe(true)
  })

  it('이름 입력 후 제출 — useAdminCreateTeamWithMetadata 호출 + onClose', async () => {
    const onClose = vi.fn()
    wrap(<CreateTeamModal onClose={onClose} />)
    fireEvent.change(screen.getByLabelText('팀 이름'), { target: { value: '디자인' } })
    fireEvent.click(screen.getByRole('button', { name: '팀 등록' }))

    await waitFor(() => expect(createMutateMock).toHaveBeenCalled())
    expect(createMutateMock.mock.calls[0][0]).toMatchObject({
      name: '디자인',
      color: TEAM_COLORS[0],
      additionalMemberIds: [],
      leadIsCreator: true,
    })
    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })

  it('색상 swatch 클릭 — color 변경', async () => {
    wrap(<CreateTeamModal onClose={() => {}} />)
    fireEvent.change(screen.getByLabelText('팀 이름'), { target: { value: 'X' } })
    const swatches = screen.getAllByRole('radio')
    fireEvent.click(swatches[2]) // 5BA08A
    fireEvent.click(screen.getByRole('button', { name: '팀 등록' }))

    await waitFor(() => expect(createMutateMock).toHaveBeenCalled())
    expect(createMutateMock.mock.calls[0][0].color).toBe(TEAM_COLORS[2])
  })

  it('"+ 멤버 추가" 클릭 시 picker 모달 노출', () => {
    wrap(<CreateTeamModal onClose={() => {}} />)
    fireEvent.click(screen.getByText('+ 멤버 추가'))
    expect(screen.getByTestId('member-picker-stub')).toBeTruthy()
  })

  it('409 TEAM_CONFLICT — 인라인 에러 메시지', async () => {
    createMutateMock.mockRejectedValue(
      Object.assign(new Error('conflict'), {
        status: 409,
        code: 'TEAM_CONFLICT',
      }),
    )
    wrap(<CreateTeamModal onClose={() => {}} />)
    fireEvent.change(screen.getByLabelText('팀 이름'), { target: { value: '중복' } })
    fireEvent.click(screen.getByRole('button', { name: '팀 등록' }))

    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy())
    expect(screen.getByRole('alert').textContent).toMatch(/이미 존재/)
  })
})
