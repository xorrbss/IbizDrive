// frontend/src/test/setup.ts
import { afterEach, beforeEach, vi } from 'vitest'
import { cleanup } from '@testing-library/react'

// globals: false 설정이라 @testing-library/react 자동 cleanup이 동작하지 않음.
// 각 test 후 DOM을 정리해 문서 레벨 리스너/렌더 잔재가 누적되지 않도록 한다.
afterEach(() => {
  cleanup()
})

// ─── 전역 XSRF-TOKEN cookie 기본값 ──────────────────────────────────────────
//
// csrf-helper-sweep(2026-05-11) 이후 모든 mutation은 `await ensureCsrfToken()`을 호출한다.
// `ensureCsrfToken`은 cookie가 없으면 `/api/auth/csrf` GET으로 부트스트랩하므로 fetch
// mock의 응답 큐를 의도치 않게 소비할 수 있다. 테스트별로 cookie를 set하지 않은
// 기존 테스트의 회귀를 막기 위해 setup에서 기본 토큰을 깔아준다.
//
// 개별 테스트가 cookie 부재 동작을 검증해야 하면 beforeEach에서 직접 unset하면 된다.
beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-csrf-default; path=/'
})

// ─── 전역 sonner mock ───────────────────────────────────────────────────────
//
// 모든 테스트에서 sonner를 자동으로 mock 처리한다. 이유:
// 1) sonner의 Toaster는 DOM/브라우저 API에 의존 → jsdom에서 부수효과 우려
// 2) toast.success/error 호출만 검증하면 충분 (시각적 토스트 자체는 e2e에서)
// 3) vi.mock을 setup에 두면 각 테스트가 hoisting 이슈로 고민하지 않아도 됨
//
// 사용법 (테스트에서):
//   import { toast } from 'sonner'
//   import { vi } from 'vitest'
//   beforeEach(() => { resetSonnerToastMock() })
//   // ...
//   expect(vi.mocked(toast).success).toHaveBeenCalledWith('...')
//
// 또는 test/mocks/sonner.ts의 toastSpy / resetSonnerToastMock 헬퍼 사용.
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    warning: vi.fn(),
    message: vi.fn(),
    loading: vi.fn(),
    dismiss: vi.fn(),
    promise: vi.fn(),
    custom: vi.fn(),
  },
  Toaster: () => null,
}))
