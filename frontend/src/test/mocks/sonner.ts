// frontend/src/test/mocks/sonner.ts
//
// sonner 모듈은 test/setup.ts에서 전역 mock 처리됨. 이 파일은 그 mock 인스턴스에
// 접근하는 헬퍼만 제공 — toast 호출 검증과 reset이 핵심 사용 케이스.
//
// 사용 예:
//   import { toast } from 'sonner'
//   import { resetSonnerToastMock, toastSpy } from '@/test/mocks/sonner'
//
//   beforeEach(() => resetSonnerToastMock())
//
//   it('성공 시 toast.success 호출', () => {
//     // ... mutate trigger ...
//     expect(toastSpy('success')).toHaveBeenCalledWith('완료')
//   })
//
// 왜 setup에서 전역 mock? — vi.mock의 factory가 다른 모듈의 import를 참조하면
// hoisting 이슈로 ReferenceError 발생. setup-level mock은 이를 회피하면서
// "모든 테스트에서 sonner는 no-op + spy" 정책을 일관되게 적용.
import { vi } from 'vitest'
import { toast } from 'sonner'
import type { Mock } from 'vitest'

type ToastMethod =
  | 'success'
  | 'error'
  | 'info'
  | 'warning'
  | 'message'
  | 'loading'
  | 'dismiss'
  | 'promise'
  | 'custom'

/** toast.<method>의 vi.Mock 인스턴스 반환. 호출 검증용. */
export function toastSpy(method: ToastMethod): Mock {
  return vi.mocked(toast)[method] as unknown as Mock
}

/** 모든 toast 메서드 mock의 호출 기록 초기화. beforeEach에서 사용. */
export function resetSonnerToastMock(): void {
  const t = vi.mocked(toast)
  ;(['success', 'error', 'info', 'warning', 'message', 'loading', 'dismiss', 'promise', 'custom'] as const).forEach(
    (m) => (t[m] as unknown as Mock).mockReset(),
  )
}
