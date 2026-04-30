import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Avatar } from './Avatar'

describe('Avatar (M14)', () => {
  it('기본 — "U" + 사용자 라벨', () => {
    render(<Avatar />)
    const el = screen.getByLabelText('사용자')
    expect(el.textContent).toBe('U')
    expect(el.getAttribute('title')).toBe('사용자')
  })

  it('initial은 첫 글자 + uppercase', () => {
    render(<Avatar initial="kim" displayName="김영수" />)
    const el = screen.getByLabelText('김영수')
    expect(el.textContent).toBe('K')
  })
})
