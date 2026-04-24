export function normalizeFileName(s: string): string {
  return s.normalize('NFC').trim()
}

export function normalizedNameForDedup(s: string): string {
  return s.normalize('NFC').toLowerCase().trim()
}

export function normalizeForSearch(s: string): string {
  return s.normalize('NFC').toLowerCase().trim().replace(/\s+/g, ' ')
}
