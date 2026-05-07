/**
 * 가상 root id — backend 대응물 없는 frontend-only navigation 노드 ('내 드라이브').
 *
 * <p>주의: 업로드/생성/이동 등 UUID를 요구하는 backend mutation에 그대로 보내면 400 (UUID 파싱 실패).
 * 호출부는 {@link isVirtualRoot}로 사전 차단해야 한다.
 */
export const VIRTUAL_ROOT_ID = 'root'

export function isVirtualRoot(folderId: string): boolean {
  return folderId === VIRTUAL_ROOT_ID
}

export function buildCanonicalPath(folderId: string, slugPath: string[]): string {
  const encoded = slugPath.map(encodeURIComponent).join('/')
  return encoded ? `/files/${folderId}/${encoded}` : `/files/${folderId}`
}

export function getFolderIdFromParts(parts: string[] | undefined): string {
  return parts?.[0] ?? VIRTUAL_ROOT_ID
}
