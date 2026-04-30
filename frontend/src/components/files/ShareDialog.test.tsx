import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, act, waitFor } from '@testing-library/react'
import { ShareDialog } from './ShareDialog'
import { useShareUiStore } from '@/stores/shareUi'
import { toastSpy, resetSonnerToastMock } from '@/test/mocks/sonner'

describe('ShareDialog (M8)', () => {
  beforeEach(() => {
    resetSonnerToastMock()
    act(() => useShareUiStore.getState().close())
  })

  it('isOpen=false 일 때 렌더링 안 됨', () => {
    const { container } = render(<ShareDialog />)
    expect(container.children.length).toBe(0)
  })

  it('open 시 dialog + 파일명 + 링크 표시', () => {
    act(() => useShareUiStore.getState().open('f1', 'doc.pdf'))
    render(<ShareDialog />)
    expect(screen.getByRole('dialog')).toBeTruthy()
    expect(screen.getByText('doc.pdf')).toBeTruthy()
    const input = screen.getByLabelText('공유 링크') as HTMLInputElement
    expect(input.value).toBe('https://ibiz.example/share/f1')
    expect(input.readOnly).toBe(true)
  })

  it('Esc → close', () => {
    act(() => useShareUiStore.getState().open('f1', 'doc.pdf'))
    render(<ShareDialog />)
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(useShareUiStore.getState().isOpen).toBe(false)
  })

  it('닫기 버튼 → close', () => {
    act(() => useShareUiStore.getState().open('f1', 'doc.pdf'))
    render(<ShareDialog />)
    fireEvent.click(screen.getByRole('button', { name: '닫기' }))
    expect(useShareUiStore.getState().isOpen).toBe(false)
  })

  describe('복사', () => {
    let writeTextSpy: ReturnType<typeof vi.fn>

    beforeEach(() => {
      writeTextSpy = vi.fn().mockResolvedValue(undefined)
      Object.defineProperty(navigator, 'clipboard', {
        configurable: true,
        value: { writeText: writeTextSpy },
      })
    })

    afterEach(() => {
      delete (navigator as { clipboard?: unknown }).clipboard
    })

    it('복사 버튼 → clipboard.writeText + toast.success', async () => {
      act(() => useShareUiStore.getState().open('f1', 'doc.pdf'))
      render(<ShareDialog />)
      fireEvent.click(screen.getByRole('button', { name: '복사' }))
      await waitFor(() => expect(writeTextSpy).toHaveBeenCalledWith('https://ibiz.example/share/f1'))
      expect(toastSpy('success')).toHaveBeenCalledWith('링크를 복사했습니다')
    })

    it('clipboard 미지원 → toast.error', async () => {
      delete (navigator as { clipboard?: unknown }).clipboard
      act(() => useShareUiStore.getState().open('f1', 'doc.pdf'))
      render(<ShareDialog />)
      fireEvent.click(screen.getByRole('button', { name: '복사' }))
      await waitFor(() => expect(toastSpy('error')).toHaveBeenCalledWith('복사에 실패했습니다'))
    })
  })
})
