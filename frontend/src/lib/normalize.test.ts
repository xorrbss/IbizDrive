import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import {
  normalizeFileName,
  normalizedNameForDedup,
  normalizeForSearch,
  NormalizationError,
} from './normalize'

/**
 * Loads the shared corpus `docs/normalize-fixtures.json` and verifies that
 * each TS function matches the `expected` block. Backend JUnit runs the same
 * fixtures — drift between the two halves fails CI (ADR #16).
 */

interface ExpectedOk {
  ok: true
  value: string
}
interface ExpectedErr {
  ok: false
  error: string
}
type Expected = ExpectedOk | ExpectedErr

interface Fixture {
  id: string
  category: string
  description: string
  input: string
  expected: {
    normalizeFileName: Expected
    normalizedNameForDedup: Expected
    normalizeForSearch: Expected
  }
}

interface FixturesFile {
  fixtures: Fixture[]
}

const fixturesPath = resolve(__dirname, '../../../docs/normalize-fixtures.json')
const data: FixturesFile = JSON.parse(readFileSync(fixturesPath, 'utf-8'))

function check(input: string, expected: Expected, fn: (s: string) => string) {
  if (expected.ok) {
    expect(fn(input)).toBe(expected.value)
  } else {
    let thrown: unknown
    try {
      fn(input)
    } catch (e) {
      thrown = e
    }
    expect(thrown).toBeInstanceOf(NormalizationError)
    expect((thrown as NormalizationError).code).toBe(expected.error)
  }
}

describe('normalize fixtures', () => {
  for (const fx of data.fixtures) {
    describe(`${fx.id} — ${fx.description}`, () => {
      it('normalizeFileName', () => {
        check(fx.input, fx.expected.normalizeFileName, normalizeFileName)
      })
      it('normalizedNameForDedup', () => {
        check(fx.input, fx.expected.normalizedNameForDedup, normalizedNameForDedup)
      })
      it('normalizeForSearch', () => {
        check(fx.input, fx.expected.normalizeForSearch, normalizeForSearch)
      })
    })
  }
})
