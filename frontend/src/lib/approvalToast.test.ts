import { describe, it, expect, beforeEach } from 'vitest'
import { showApprovalRequiredToast } from './approvalToast'
import { ApprovalRequiredError } from './errors'
import { toastSpy, resetSonnerToastMock } from '@/test/mocks/sonner'

/**
 * `showApprovalRequiredToast` — dual-approval framework Phase 4 FE follow-up
 * (ADR #47, docs/02 §2.11).
 *
 * <p>호출 시 sonner `toast.info` 1회 + 메시지에 actionLabel 포함 + action 버튼이
 * `/admin/approvals/:approvalId`로 location 이동.
 */
describe('approvalToast — showApprovalRequiredToast', () => {
  beforeEach(() => {
    resetSonnerToastMock()
  })

  it('toast.info 1회 호출 + 메시지에 actionLabel 포함', () => {
    const err = new ApprovalRequiredError('xxx', '2026-05-15T00:00:00Z')
    showApprovalRequiredToast(err, '사용자 역할 변경')
    expect(toastSpy('info')).toHaveBeenCalledOnce()
    const [msg] = toastSpy('info').mock.calls[0]
    expect(msg).toMatch(/승인 요청이 등록되었습니다/)
    expect(msg).toMatch(/사용자 역할 변경/)
    expect(msg).toMatch(/두 번째 관리자/)
  })

  it('action 버튼 label="승인 페이지" + onClick은 /admin/approvals/:id 이동', () => {
    const err = new ApprovalRequiredError('aaaa-bbbb', '2026-05-15T00:00:00Z')
    showApprovalRequiredToast(err, '휴지통 보존 정책 변경')

    const opts = toastSpy('info').mock.calls[0][1] as {
      action?: { label: string; onClick: () => void }
    }
    expect(opts.action?.label).toBe('승인 페이지')

    // jsdom의 window.location.href 할당은 실제 navigation 시도(stderr 노이즈) → location 객체를
    // 임시 plain object로 교체해 setter만 가드. afterEach에서 원본 복원.
    const originalLocation = window.location
    const hrefStub = { value: '' }
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: {
        get href() {
          return hrefStub.value
        },
        set href(v: string) {
          hrefStub.value = v
        },
      },
    })
    try {
      opts.action?.onClick()
      expect(hrefStub.value).toBe('/admin/approvals/aaaa-bbbb')
    } finally {
      Object.defineProperty(window, 'location', {
        configurable: true,
        writable: true,
        value: originalLocation,
      })
    }
  })

  it('duration: 8000ms (일반 5초보다 길게)', () => {
    const err = new ApprovalRequiredError('id', 'iso')
    showApprovalRequiredToast(err, '휴지통 영구 삭제')
    const opts = toastSpy('info').mock.calls[0][1] as { duration?: number }
    expect(opts.duration).toBe(8000)
  })
})
