import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { HomeDashboard } from './HomeDashboard'

vi.mock('./WelcomeHeader', () => ({ WelcomeHeader: () => <div data-testid="welcome">welcome</div> }))
vi.mock('./StarredCard', () => ({ StarredCard: () => <div data-testid="starred">starred</div> }))
vi.mock('./QuotaCard', () => ({ QuotaCard: () => <div data-testid="quota">quota</div> }))
vi.mock('./SharedWithMeCard', () => ({ SharedWithMeCard: () => <div data-testid="shared">shared</div> }))

describe('HomeDashboard', () => {
  it('4 위젯 모두 렌더', () => {
    render(<HomeDashboard />)
    expect(screen.getByTestId('welcome')).toBeTruthy()
    expect(screen.getByTestId('starred')).toBeTruthy()
    expect(screen.getByTestId('quota')).toBeTruthy()
    expect(screen.getByTestId('shared')).toBeTruthy()
  })
})
