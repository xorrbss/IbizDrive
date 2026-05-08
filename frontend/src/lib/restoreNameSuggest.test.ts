import { describe, it, expect } from 'vitest'
import { suggestRestoreName } from './restoreNameSuggest'

describe('suggestRestoreName', () => {
  it('file with extension: inserts " (1)" before the last dot', () => {
    expect(suggestRestoreName('report.pdf', 'file')).toBe('report (1).pdf')
    expect(suggestRestoreName('Q1 plan.docx', 'file')).toBe('Q1 plan (1).docx')
  })

  it('file with multi-dot name: only the last dot is treated as ext', () => {
    expect(suggestRestoreName('archive.tar.gz', 'file')).toBe('archive.tar (1).gz')
  })

  it('file without extension: appends " (1)" to whole name', () => {
    expect(suggestRestoreName('Readme', 'file')).toBe('Readme (1)')
  })

  it('dotfile (leading dot, no other dot): treated as no-ext', () => {
    expect(suggestRestoreName('.gitignore', 'file')).toBe('.gitignore (1)')
  })

  it('folder: appends " (1)" without ext logic', () => {
    expect(suggestRestoreName('Reports', 'folder')).toBe('Reports (1)')
    expect(suggestRestoreName('Q1.archive', 'folder')).toBe('Q1.archive (1)')
  })
})
