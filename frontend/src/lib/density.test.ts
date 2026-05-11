import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import {
  DENSITY_STORAGE_KEY,
  applyDensity,
  getInitialDensity,
  getStoredDensity,
  persistDensity,
} from './density'

describe('lib/density', () => {
  beforeEach(() => {
    window.localStorage.clear()
    document.documentElement.removeAttribute('data-density')
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('getStoredDensity', () => {
    it('returns null when no value stored', () => {
      expect(getStoredDensity()).toBeNull()
    })

    it('returns one of 3 densities when valid', () => {
      const densities = ['compact', 'default', 'comfortable'] as const
      for (const d of densities) {
        window.localStorage.setItem(DENSITY_STORAGE_KEY, d)
        expect(getStoredDensity()).toBe(d)
      }
    })

    it('returns null for invalid stored value', () => {
      window.localStorage.setItem(DENSITY_STORAGE_KEY, 'huge')
      expect(getStoredDensity()).toBeNull()
    })
  })

  describe('getInitialDensity', () => {
    it('prefers stored value', () => {
      window.localStorage.setItem(DENSITY_STORAGE_KEY, 'comfortable')
      expect(getInitialDensity()).toBe('comfortable')
    })

    it('falls back to default when nothing stored', () => {
      expect(getInitialDensity()).toBe('default')
    })

    it('falls back to default when stored value is invalid', () => {
      window.localStorage.setItem(DENSITY_STORAGE_KEY, 'huge')
      expect(getInitialDensity()).toBe('default')
    })
  })

  describe('applyDensity', () => {
    it('sets data-density on <html> for non-default', () => {
      applyDensity('compact')
      expect(document.documentElement.getAttribute('data-density')).toBe('compact')
      applyDensity('comfortable')
      expect(document.documentElement.getAttribute('data-density')).toBe('comfortable')
    })

    it('removes data-density for default', () => {
      document.documentElement.setAttribute('data-density', 'compact')
      applyDensity('default')
      expect(document.documentElement.getAttribute('data-density')).toBeNull()
    })
  })

  describe('persistDensity', () => {
    it('writes to localStorage', () => {
      persistDensity('comfortable')
      expect(window.localStorage.getItem(DENSITY_STORAGE_KEY)).toBe('comfortable')
      persistDensity('default')
      expect(window.localStorage.getItem(DENSITY_STORAGE_KEY)).toBe('default')
    })

    it('swallows localStorage errors', () => {
      const setItem = vi
        .spyOn(window.localStorage.__proto__ as Storage, 'setItem')
        .mockImplementation(() => {
          throw new Error('quota')
        })
      expect(() => persistDensity('compact')).not.toThrow()
      setItem.mockRestore()
    })
  })
})
