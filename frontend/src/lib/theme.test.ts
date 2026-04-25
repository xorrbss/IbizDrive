import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import {
  THEME_STORAGE_KEY,
  applyTheme,
  getInitialTheme,
  getStoredTheme,
  getSystemTheme,
  persistTheme,
} from './theme'

describe('lib/theme', () => {
  beforeEach(() => {
    window.localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('getStoredTheme', () => {
    it('returns null when no value stored', () => {
      expect(getStoredTheme()).toBeNull()
    })
    it('returns light/dark when valid', () => {
      window.localStorage.setItem(THEME_STORAGE_KEY, 'dark')
      expect(getStoredTheme()).toBe('dark')
      window.localStorage.setItem(THEME_STORAGE_KEY, 'light')
      expect(getStoredTheme()).toBe('light')
    })
    it('returns null for invalid stored value', () => {
      window.localStorage.setItem(THEME_STORAGE_KEY, 'sepia')
      expect(getStoredTheme()).toBeNull()
    })
  })

  describe('getSystemTheme', () => {
    it('returns dark when prefers-color-scheme matches', () => {
      vi.spyOn(window, 'matchMedia').mockImplementation((q) => ({
        matches: q === '(prefers-color-scheme: dark)',
        media: q,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        onchange: null,
        dispatchEvent: vi.fn(),
      }))
      expect(getSystemTheme()).toBe('dark')
    })
    it('returns light otherwise', () => {
      vi.spyOn(window, 'matchMedia').mockImplementation((q) => ({
        matches: false,
        media: q,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        onchange: null,
        dispatchEvent: vi.fn(),
      }))
      expect(getSystemTheme()).toBe('light')
    })
  })

  describe('getInitialTheme', () => {
    it('prefers stored value over system', () => {
      window.localStorage.setItem(THEME_STORAGE_KEY, 'light')
      vi.spyOn(window, 'matchMedia').mockImplementation((q) => ({
        matches: true,
        media: q,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        onchange: null,
        dispatchEvent: vi.fn(),
      }))
      expect(getInitialTheme()).toBe('light')
    })
    it('falls back to system when nothing stored', () => {
      vi.spyOn(window, 'matchMedia').mockImplementation((q) => ({
        matches: true,
        media: q,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        onchange: null,
        dispatchEvent: vi.fn(),
      }))
      expect(getInitialTheme()).toBe('dark')
    })
  })

  describe('applyTheme', () => {
    it('sets data-theme=dark on <html> for dark', () => {
      applyTheme('dark')
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    })
    it('removes data-theme for light', () => {
      document.documentElement.setAttribute('data-theme', 'dark')
      applyTheme('light')
      expect(document.documentElement.getAttribute('data-theme')).toBeNull()
    })
  })

  describe('persistTheme', () => {
    it('writes to localStorage', () => {
      persistTheme('dark')
      expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark')
      persistTheme('light')
      expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('light')
    })
    it('swallows localStorage errors', () => {
      const setItem = vi
        .spyOn(window.localStorage.__proto__ as Storage, 'setItem')
        .mockImplementation(() => {
          throw new Error('quota')
        })
      expect(() => persistTheme('dark')).not.toThrow()
      setItem.mockRestore()
    })
  })
})
