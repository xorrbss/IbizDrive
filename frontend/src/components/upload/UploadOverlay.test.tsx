import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { UploadOverlay } from './UploadOverlay'

describe('UploadOverlay', () => {
  it('visible=false 일 때 렌더 안됨', () => {
    const { container } = render(<UploadOverlay visible={false} />)
    expect(container.innerHTML).toBe('')
  })

  it('visible=true 일 때 안내 문구 + 서브 텍스트 표시', () => {
    const { container } = render(<UploadOverlay visible />)
    const overlay = container.querySelector('[role="presentation"]')
    expect(overlay).not.toBeNull()
    expect(container.textContent).toContain('여기에 놓아서 업로드')
    expect(container.textContent).toContain('현재 폴더에 파일이 추가됩니다')
  })
})
