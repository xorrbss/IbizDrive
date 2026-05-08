import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import AdminTrashAllPage from './page'
import * as apiModule from '@/lib/api'
import type { AdminTrashItem } from '@/types/trash'

/**
 * Wave 2 T9 — `/admin/trash/all` page (spec §5.2, plan §P6.1).
 *
 * <p>Hooks 자체는 src/hooks/useAdminTrash.test.ts 가 책임. 본 테스트는 page 레이어
 * (FilterBar + Table + ConfirmDialog 분기)만 가드.
 */
function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <AdminTrashAllPage />
    </QueryClientProvider>,
  )
}

const sample: AdminTrashItem = {
  id: 'f-1',
  name: 'spec.pdf',
  type: 'file',
  deletedAt: '2026-05-07T10:00:00Z',
  purgeAfter: '2026-06-06T10:00:00Z',
  ownerId: 'u-1',
  ownerEmail: 'alice@x',
  originalParentId: 'fd-1',
  originalParentName: 'Reports',
  originalParentPath: '/Workspace/Reports',
  sizeBytes: 12345,
  deletedById: null,
  deletedByEmail: null,
}

describe('/admin/trash/all', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('renders rows with name, ownerEmail, originalParentPath', async () => {
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({ items: [sample], nextCursor: null })
    renderPage()

    expect(await screen.findByText('spec.pdf')).toBeTruthy()
    expect(screen.getByText('alice@x')).toBeTruthy()
    // path가 있으면 단일 segment name이 아니라 절대 경로가 표시되어야 한다.
    expect(screen.getByText('/Workspace/Reports')).toBeTruthy()
    expect(screen.queryByText('Reports')).toBeNull()
  })

  it('falls back to originalParentName when originalParentPath is null', async () => {
    // 데이터 corruption / depth 초과 등으로 backend가 path를 채우지 못한 경우.
    const noPath: AdminTrashItem = { ...sample, originalParentPath: null }
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({ items: [noPath], nextCursor: null })
    renderPage()

    expect(await screen.findByText('Reports')).toBeTruthy()
    expect(screen.queryByText('/Workspace/Reports')).toBeNull()
  })

  it('renders (루트) marker when item has no parent', async () => {
    const root: AdminTrashItem = {
      ...sample,
      originalParentId: null,
      originalParentName: null,
      originalParentPath: null,
    }
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({ items: [root], nextCursor: null })
    renderPage()

    expect(await screen.findByText('(루트)')).toBeTruthy()
  })

  it('renders sizeBytes formatted via formatBytes (not raw bytes)', async () => {
    // 12345 B = 12 KB (formatBytes의 KB 정수 포맷). raw "12345 B"가 아니어야 한다.
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({ items: [sample], nextCursor: null })
    renderPage()

    expect(await screen.findByText('spec.pdf')).toBeTruthy()
    expect(screen.getByText('12 KB')).toBeTruthy()
    expect(screen.queryByText('12345 B')).toBeNull()
  })

  it('renders empty state when no items', async () => {
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({ items: [], nextCursor: null })
    renderPage()

    expect(await screen.findByText(/휴지통이 비어 있습니다/)).toBeTruthy()
  })

  it('opens ConfirmDialog on purge click', async () => {
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({ items: [sample], nextCursor: null })
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '영구 삭제' }))

    expect(await screen.findByText(/되돌릴 수 없습니다/)).toBeTruthy()
  })

  it('immediately calls restore api on restore click (no confirm)', async () => {
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({ items: [sample], nextCursor: null })
    const restoreSpy = vi
      .spyOn(apiModule.api, 'restoreFile')
      .mockResolvedValue(undefined as never)
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '복원' }))

    await waitFor(() => expect(restoreSpy).toHaveBeenCalledWith('f-1'))
  })

  // V10 — "삭제자" 컬럼 (cross-owner 추적)
  it('shows "삭제자" header column', async () => {
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({ items: [sample], nextCursor: null })
    renderPage()

    expect(await screen.findByRole('columnheader', { name: '삭제자' })).toBeTruthy()
  })

  it('renders deletedByEmail when present', async () => {
    const withDeleter: AdminTrashItem = {
      ...sample,
      deletedById: 'u-2',
      deletedByEmail: 'admin@x',
    }
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({ items: [withDeleter], nextCursor: null })
    renderPage()

    expect(await screen.findByText('admin@x')).toBeTruthy()
  })

  it('renders em dash when deletedByEmail is null', async () => {
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({ items: [sample], nextCursor: null })
    renderPage()

    // sample.deletedByEmail === null → em dash 표기
    expect(await screen.findByText('—')).toBeTruthy()
  })

  // ── Wave 2 T9 follow-up: bulk restore/purge (spec §3) ────────────────────

  const second: AdminTrashItem = {
    ...sample,
    id: 'f-2',
    name: 'plan.docx',
  }

  it('체크박스 다중 선택 시 BulkActionBar 노출', async () => {
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({
      items: [sample, second],
      nextCursor: null,
    })
    renderPage()

    // 초기에는 BulkActionBar 미노출
    expect(screen.queryByRole('toolbar', { name: '일괄 작업' })).toBeNull()

    // 행 1개 선택
    const cb = await screen.findByRole('checkbox', { name: 'spec.pdf 선택' })
    fireEvent.click(cb)
    expect(await screen.findByRole('toolbar', { name: '일괄 작업' })).toBeTruthy()
    expect(screen.getByTestId('admin-trash-bulk-count').textContent).toContain('1개')

    // 또 1개 선택
    fireEvent.click(screen.getByRole('checkbox', { name: 'plan.docx 선택' }))
    expect(screen.getByTestId('admin-trash-bulk-count').textContent).toContain('2개')
  })

  it('전체 선택 → 모든 행 체크 + BulkActionBar 카운트 = items.length', async () => {
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({
      items: [sample, second],
      nextCursor: null,
    })
    renderPage()

    fireEvent.click(await screen.findByRole('checkbox', { name: '전체 선택' }))
    expect(screen.getByTestId('admin-trash-bulk-count').textContent).toContain('2개')

    // 다시 클릭 → 전체 해제
    fireEvent.click(screen.getByRole('checkbox', { name: '전체 선택' }))
    expect(screen.queryByRole('toolbar', { name: '일괄 작업' })).toBeNull()
  })

  it('일괄 복원 → bulk api 호출(restore) + 결과 banner 노출', async () => {
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({
      items: [sample, second],
      nextCursor: null,
    })
    const bulkSpy = vi.spyOn(apiModule, 'adminBulkTrash').mockResolvedValue({
      succeeded: [{ type: 'file', id: 'f-1' }, { type: 'file', id: 'f-2' }],
      failed: [],
    })
    renderPage()

    fireEvent.click(await screen.findByRole('checkbox', { name: '전체 선택' }))
    fireEvent.click(screen.getByRole('button', { name: '일괄 복원' }))

    await waitFor(() => expect(bulkSpy).toHaveBeenCalledOnce())
    const [action, items] = bulkSpy.mock.calls[0]
    expect(action).toBe('restore')
    expect(items).toEqual(expect.arrayContaining([
      { type: 'file', id: 'f-1' },
      { type: 'file', id: 'f-2' },
    ]))
    expect(await screen.findByTestId('admin-trash-bulk-result')).toBeTruthy()
    expect(screen.getByTestId('admin-trash-bulk-result').textContent).toContain('성공 2개, 실패 0개')
  })

  it('일괄 영구삭제 → ConfirmDialog 후 bulk api 호출(purge)', async () => {
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({
      items: [sample],
      nextCursor: null,
    })
    const bulkSpy = vi.spyOn(apiModule, 'adminBulkTrash').mockResolvedValue({
      succeeded: [{ type: 'file', id: 'f-1' }],
      failed: [],
    })
    renderPage()

    fireEvent.click(await screen.findByRole('checkbox', { name: 'spec.pdf 선택' }))
    fireEvent.click(screen.getByRole('button', { name: '일괄 영구삭제' }))

    // ConfirmDialog 노출
    expect(await screen.findByText(/선택한 1개를 영구 삭제/)).toBeTruthy()
    expect(bulkSpy).not.toHaveBeenCalled()

    // 다이얼로그 내 "영구 삭제" 확인 버튼 클릭 (행의 단건 버튼과 구분)
    const dialog = screen.getByRole('dialog')
    fireEvent.click(within(dialog).getByRole('button', { name: '영구 삭제' }))

    await waitFor(() => expect(bulkSpy).toHaveBeenCalledOnce())
    expect(bulkSpy.mock.calls[0][0]).toBe('purge')
  })

  it('부분 실패 응답 → 자세히 펼치면 failed 항목 노출', async () => {
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({
      items: [sample, second],
      nextCursor: null,
    })
    vi.spyOn(apiModule, 'adminBulkTrash').mockResolvedValue({
      succeeded: [{ type: 'file', id: 'f-1' }],
      failed: [{ type: 'file', id: 'f-2', error: 'NAME_CONFLICT' }],
    })
    renderPage()

    fireEvent.click(await screen.findByRole('checkbox', { name: '전체 선택' }))
    fireEvent.click(screen.getByRole('button', { name: '일괄 복원' }))

    const banner = await screen.findByTestId('admin-trash-bulk-result')
    expect(banner.textContent).toContain('성공 1개, 실패 1개')

    // 자세히 펼치기
    fireEvent.click(screen.getByRole('button', { name: '자세히' }))
    expect(banner.textContent).toContain('NAME_CONFLICT')
    expect(banner.textContent).toContain('f-2')
  })

  it('필터 변경 시 선택 초기화', async () => {
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({
      items: [sample],
      nextCursor: null,
    })
    renderPage()

    fireEvent.click(await screen.findByRole('checkbox', { name: 'spec.pdf 선택' }))
    expect(await screen.findByRole('toolbar', { name: '일괄 작업' })).toBeTruthy()

    // 필터 변경
    fireEvent.change(screen.getByLabelText('이름 검색'), { target: { value: 'spec' } })

    // 선택 초기화 → BulkActionBar 사라짐
    await waitFor(() =>
      expect(screen.queryByRole('toolbar', { name: '일괄 작업' })).toBeNull(),
    )
  })
})
