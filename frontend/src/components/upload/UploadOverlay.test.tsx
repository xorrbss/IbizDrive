import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { UploadOverlay } from './UploadOverlay'

describe('UploadOverlay', () => {
  it('visible=false 일 때 렌더 안됨', () => {
    const { container } = render(<UploadOverlay visible={false} />)
    expect(container.innerHTML).toBe('')
  })

  it('visible=true + folderName 미주입 → "현재 폴더" fallback', () => {
    const { container } = render(<UploadOverlay visible />)
    const overlay = container.querySelector('[role="presentation"]')
    expect(overlay).not.toBeNull()
    expect(container.textContent).toContain('여기에 놓아서 업로드')
    expect(container.textContent).toContain('현재 폴더에 파일이 추가됩니다')
  })

  it('visible=true + folderName 주입 → 폴더명 + 안내 텍스트 (zip §DropOverlay)', () => {
    // design zip panels.jsx L356 `{folderName}에 파일이 추가됩니다` 충실. 폴더명은 강조(`font-medium`)
    // span 으로 감싸 시각 변별.
    const { container } = render(<UploadOverlay visible folderName="영업팀 자료" />)
    expect(container.textContent).toContain('영업팀 자료에 파일이 추가됩니다')
    expect(container.textContent).not.toContain('현재 폴더에 파일이 추가됩니다')
  })
})
