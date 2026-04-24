export function buildCanonicalPath(folderId: string, slugPath: string[]): string {
  const encoded = slugPath.map(encodeURIComponent).join('/')
  return encoded ? `/files/${folderId}/${encoded}` : `/files/${folderId}`
}

export function getFolderIdFromParts(parts: string[] | undefined): string {
  return parts?.[0] ?? 'root'
}
