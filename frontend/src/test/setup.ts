// frontend/src/test/setup.ts
import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

// globals: false 설정이라 @testing-library/react 자동 cleanup이 동작하지 않음.
// 각 test 후 DOM을 정리해 문서 레벨 리스너/렌더 잔재가 누적되지 않도록 한다.
afterEach(() => {
  cleanup()
})
