import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { TrashRowActions } from './TrashRowActions'
import { usePermission } from '@/hooks/usePermission'
import { useRestoreConflictUiStore } from '@/stores/restoreConflictUi'
import type { TrashItem } from '@/types/trash'

// Hook mocks — 본 컴포넌트는 mutation 호출 자체보다 disabled UX 분기에 초점.
const restoreMutate = vi.fn()
vi.mock('@/hooks/useRestoreItem', () => ({
  useRestoreItem: () => ({ mutate: restoreMutate, isPending: false }),
}))
vi.mock('@/hooks/usePurgeTrashItem', () => ({
  usePurgeTrashItem: () => ({ mutate: vi.fn(), isPending: false }),
}))
vi.mock('@/hooks/usePermission', () => ({ usePermission: vi.fn() }))

function wrap(node: ReactNode) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return <QueryClientProvider client={qc}>{node}</QueryClientProvider>
}

const item: TrashItem = {
  id: 'f1',
  name: '제안서.pdf',
  type: 'file',
  deletedAt: '2026-04-30T10:00:00Z',
  purgeAfter: '2026-05-30T10:00:00Z',
  originalParentId: 'p1',
}

describe('TrashRowActions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    ;(usePermission as ReturnType<typeof vi.fn>).mockReturnValue({ PURGE: true })
    useRestoreConflictUiStore.setState({
      isOpen: false,
      targetType: null,
      targetId: null,
      originalName: '',
      sourceFolderId: null,
      payload: null,
      error: null,
    })
  })

  it('disabled 미지정 (default) → 복원 버튼 활성', () => {
    render(wrap(<TrashRowActions item={item} />))
    const btn = screen.getByRole('button', { name: '복원' }) as HTMLButtonElement
    expect(btn.disabled).toBe(false)
    // archive 안내 툴팁은 미노출
    expect(btn.title).toBe('')
  })

  it('disabled=true → 복원 버튼 비활성 + archive 안내 툴팁', () => {
    render(wrap(<TrashRowActions item={item} disabled />))
    const btn = screen.getByRole('button', { name: '복원' }) as HTMLButtonElement
    expect(btn.disabled).toBe(true)
    expect(btn.title).toBe('archive된 팀의 콘텐츠는 복원할 수 없습니다')
  })

  it('disabled=false → 복원 버튼 활성 (명시적 false)', () => {
    render(wrap(<TrashRowActions item={item} disabled={false} />))
    const btn = screen.getByRole('button', { name: '복원' }) as HTMLButtonElement
    expect(btn.disabled).toBe(false)
    expect(btn.title).toBe('')
  })

  it('disabled=true 여도 영구 삭제 버튼은 권한 기반 (ADMIN → 표시)', () => {
    render(wrap(<TrashRowActions item={item} disabled />))
    expect(screen.getByRole('button', { name: '영구 삭제' })).toBeTruthy()
  })

  it('non-ADMIN → 영구 삭제 버튼 숨김 (복원만)', () => {
    ;(usePermission as ReturnType<typeof vi.fn>).mockReturnValue({ PURGE: false })
    render(wrap(<TrashRowActions item={item} />))
    expect(screen.queryByRole('button', { name: '영구 삭제' })).toBeNull()
    expect(screen.getByRole('button', { name: '복원' })).toBeTruthy()
  })
})
