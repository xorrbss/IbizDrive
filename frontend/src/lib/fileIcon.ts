import type { FileItem } from '@/types/file'
import type { FileTypeIconKind } from '@/components/icons/FileTypeIcon'

/**
 * mime → 디자인 zip icons.jsx 의 kind 매핑. FileRow / FileCard 양쪽 공통 helper.
 *
 * <p>backend 가 mimeType 을 노출하지 않는 경우(폴더, 또는 mime unknown) folder/doc
 * 으로 fallback. 매핑 순서는 비교적 명확한 시그니처부터 (image/video → archive →
 * 문서류 → code → 기본 doc).
 */
export function fileIconKind(item: FileItem): FileTypeIconKind {
  if (item.type === 'folder') return 'folder'
  const mime = (item.mimeType || '').toLowerCase()
  const name = item.name.toLowerCase()

  if (mime.startsWith('image/')) return 'image'
  if (mime.startsWith('video/')) return 'video'

  if (
    mime === 'application/zip' ||
    mime === 'application/x-zip-compressed' ||
    mime === 'application/x-tar' ||
    mime === 'application/x-rar-compressed' ||
    mime === 'application/x-7z-compressed' ||
    mime === 'application/gzip' ||
    /\.(zip|tar|gz|tgz|rar|7z|bz2)$/i.test(name)
  ) {
    return 'archive'
  }

  if (mime === 'application/pdf' || /\.pdf$/i.test(name)) return 'pdf'

  if (
    mime.includes('spreadsheet') ||
    mime.includes('excel') ||
    /\.(xls|xlsx|csv|ods)$/i.test(name)
  ) {
    return 'sheet'
  }

  if (
    mime.includes('presentation') ||
    mime.includes('powerpoint') ||
    /\.(ppt|pptx|odp|key)$/i.test(name)
  ) {
    return 'slides'
  }

  if (mime === 'application/vnd.figma' || /\.fig$/i.test(name)) return 'figma'

  if (
    mime.startsWith('text/') ||
    mime === 'application/json' ||
    mime === 'application/javascript' ||
    mime === 'application/xml' ||
    /\.(js|jsx|ts|tsx|json|html|css|scss|sass|less|xml|yaml|yml|md|sh|py|rb|java|go|rs|c|h|cpp|cs|php|swift|kt|sql|toml)$/i.test(
      name,
    )
  ) {
    return 'code'
  }

  return 'doc'
}

/**
 * kind → 한국어 라벨 (RightPanel detail "종류" row). 디자인 panels.jsx L211~215 1:1.
 */
const KIND_LABEL: Record<FileTypeIconKind, string> = {
  folder: '폴더',
  doc: '문서',
  pdf: 'PDF 문서',
  sheet: '스프레드시트',
  slides: '프레젠테이션',
  image: '이미지',
  video: '비디오',
  figma: 'Figma 파일',
  code: '코드',
  archive: '압축파일',
}

export function kindLabel(kind: FileTypeIconKind): string {
  return KIND_LABEL[kind] ?? kind
}
