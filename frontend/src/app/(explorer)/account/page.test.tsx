import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import AccountRoute from './page'

vi.mock('@/components/account/AccountPage', () => ({
  AccountPage: () => <div data-testid="account-page-stub">stub</div>,
}))

describe('/account route', () => {
  it('renders <AccountPage />', () => {
    render(<AccountRoute />)
    expect(screen.getByTestId('account-page-stub')).toBeTruthy()
  })
})
