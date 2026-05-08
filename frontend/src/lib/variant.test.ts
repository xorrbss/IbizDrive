import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import {
  VARIANT_STORAGE_KEY,
  applyVariant,
  getInitialVariant,
  getStoredVariant,
  persistVariant,
} from './variant'

describe('lib/variant', () => {
  beforeEach(() => {
    window.localStorage.clear()
    document.documentElement.removeAttribute('data-variant')
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('getStoredVariant', () => {
    it('returns null when no value stored', () => {
      expect(getStoredVariant()).toBeNull()
    })
    it('returns one of 4 variants when valid', () => {
      const variants = ['default', 'notion', 'dropbox', 'terminal'] as const
      for (const v of variants) {
        window.localStorage.setItem(VARIANT_STORAGE_KEY, v)
        expect(getStoredVariant()).toBe(v)
      }
    })
    it('returns null for invalid stored value', () => {
      window.localStorage.setItem(VARIANT_STORAGE_KEY, 'sepia')
      expect(getStoredVariant()).toBeNull()
    })
  })

  describe('getInitialVariant', () => {
    it('prefers stored value', () => {
      window.localStorage.setItem(VARIANT_STORAGE_KEY, 'terminal')
      expect(getInitialVariant()).toBe('terminal')
    })
    it('falls back to default when nothing stored', () => {
      expect(getInitialVariant()).toBe('default')
    })
    it('falls back to default when stored value is invalid', () => {
      window.localStorage.setItem(VARIANT_STORAGE_KEY, 'sepia')
      expect(getInitialVariant()).toBe('default')
    })
  })

  describe('applyVariant', () => {
    it('sets data-variant on <html> for non-default', () => {
      applyVariant('notion')
      expect(document.documentElement.getAttribute('data-variant')).toBe('notion')
      applyVariant('terminal')
      expect(document.documentElement.getAttribute('data-variant')).toBe('terminal')
    })
    it('removes data-variant for default', () => {
      document.documentElement.setAttribute('data-variant', 'notion')
      applyVariant('default')
      expect(document.documentElement.getAttribute('data-variant')).toBeNull()
    })
  })

  describe('persistVariant', () => {
    it('writes to localStorage', () => {
      persistVariant('dropbox')
      expect(window.localStorage.getItem(VARIANT_STORAGE_KEY)).toBe('dropbox')
      persistVariant('default')
      expect(window.localStorage.getItem(VARIANT_STORAGE_KEY)).toBe('default')
    })
    it('swallows localStorage errors', () => {
      const setItem = vi
        .spyOn(window.localStorage.__proto__ as Storage, 'setItem')
        .mockImplementation(() => {
          throw new Error('quota')
        })
      expect(() => persistVariant('notion')).not.toThrow()
      setItem.mockRestore()
    })
  })
})
