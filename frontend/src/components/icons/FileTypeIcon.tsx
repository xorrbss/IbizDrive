/**
 * 파일 타입 아이콘 — 디자인 핸드오프 2026-05-10 icons.jsx §ICONS (L4~73) 1:1 매핑.
 *
 * <p>이전 `fileIconFor`(lucide-react 단색 5종) → 본 컴포넌트의 컬러 SVG 10종으로
 * 교체. 각 kind는 디자인 zip 고정 brand color (folder는 currentColor + accent 상속).
 *
 * <p>kind 매핑은 `lib/fileIcon.ts` `fileIconKind(item)` 담당. 본 컴포넌트는 단순
 * switch 분기 — fallback은 'doc'.
 */

export type FileTypeIconKind =
  | 'folder'
  | 'doc'
  | 'pdf'
  | 'sheet'
  | 'slides'
  | 'image'
  | 'video'
  | 'figma'
  | 'code'
  | 'archive'

export interface FileTypeIconProps {
  kind: FileTypeIconKind
  size?: number
  className?: string
  /** folder kind에만 적용 — 기본 currentColor (호출자 className `text-accent`로 색상 주입) */
  folderColor?: string
}

export function FileTypeIcon({
  kind,
  size = 16,
  className,
  folderColor,
}: FileTypeIconProps) {
  const common = {
    width: size,
    height: size,
    viewBox: '0 0 16 16',
    fill: 'none',
    'aria-hidden': true as const,
    className,
  }
  switch (kind) {
    case 'folder':
      return (
        <svg {...common}>
          <path
            d="M1.5 4.5C1.5 3.67157 2.17157 3 3 3H6.5L7.8 4.5H13C13.8284 4.5 14.5 5.17157 14.5 6V11.5C14.5 12.3284 13.8284 13 13 13H3C2.17157 13 1.5 12.3284 1.5 11.5V4.5Z"
            fill={folderColor || 'currentColor'}
            fillOpacity="0.22"
            stroke={folderColor || 'currentColor'}
            strokeWidth="1"
          />
        </svg>
      )
    case 'pdf':
      return (
        <svg {...common}>
          <path
            d="M3 1.5H9L13 5.5V13.5C13 14.3284 12.3284 15 11.5 15H3C2.17157 15 1.5 14.3284 1.5 13.5V3C1.5 2.17157 2.17157 1.5 3 1.5Z"
            fill="#E0564B"
            fillOpacity="0.14"
            stroke="#E0564B"
            strokeWidth="1"
          />
          <path d="M9 1.5V5.5H13" stroke="#E0564B" strokeWidth="1" />
          <text
            x="3.5"
            y="12.2"
            fontSize="4.2"
            fontWeight="700"
            fill="#E0564B"
            fontFamily="ui-monospace,monospace"
          >
            PDF
          </text>
        </svg>
      )
    case 'sheet':
      return (
        <svg {...common}>
          <path
            d="M3 1.5H9L13 5.5V13.5C13 14.3284 12.3284 15 11.5 15H3C2.17157 15 1.5 14.3284 1.5 13.5V3C1.5 2.17157 2.17157 1.5 3 1.5Z"
            fill="#3EA971"
            fillOpacity="0.14"
            stroke="#3EA971"
            strokeWidth="1"
          />
          <path d="M9 1.5V5.5H13" stroke="#3EA971" strokeWidth="1" />
          <path
            d="M3.5 8.5H11.5M3.5 10.5H11.5M3.5 12.5H11.5M6 8V13M9 8V13"
            stroke="#3EA971"
            strokeWidth="0.8"
          />
        </svg>
      )
    case 'slides':
      return (
        <svg {...common}>
          <path
            d="M3 1.5H9L13 5.5V13.5C13 14.3284 12.3284 15 11.5 15H3C2.17157 15 1.5 14.3284 1.5 13.5V3C1.5 2.17157 2.17157 1.5 3 1.5Z"
            fill="#E89B3C"
            fillOpacity="0.14"
            stroke="#E89B3C"
            strokeWidth="1"
          />
          <path d="M9 1.5V5.5H13" stroke="#E89B3C" strokeWidth="1" />
          <rect
            x="3.5"
            y="8"
            width="8"
            height="4"
            stroke="#E89B3C"
            strokeWidth="0.9"
            fill="none"
          />
        </svg>
      )
    case 'image':
      return (
        <svg {...common}>
          <rect
            x="1.5"
            y="2.5"
            width="13"
            height="11"
            rx="1.5"
            fill="#B06BCC"
            fillOpacity="0.14"
            stroke="#B06BCC"
            strokeWidth="1"
          />
          <circle cx="5" cy="6" r="1" fill="#B06BCC" />
          <path
            d="M1.5 11L5 8L8 10.5L11 7.5L14.5 11V12C14.5 12.8 13.8 13.5 13 13.5H3C2.2 13.5 1.5 12.8 1.5 12V11Z"
            fill="#B06BCC"
            fillOpacity="0.4"
          />
        </svg>
      )
    case 'video':
      return (
        <svg {...common}>
          <rect
            x="1.5"
            y="3"
            width="13"
            height="10"
            rx="1.5"
            fill="#C95A7B"
            fillOpacity="0.14"
            stroke="#C95A7B"
            strokeWidth="1"
          />
          <path d="M6.5 6L10 8L6.5 10V6Z" fill="#C95A7B" />
        </svg>
      )
    case 'figma':
      return (
        <svg {...common}>
          <rect
            x="2"
            y="2"
            width="12"
            height="12"
            rx="2"
            fill="#8B5CF6"
            fillOpacity="0.14"
            stroke="#8B5CF6"
            strokeWidth="1"
          />
          <circle cx="8" cy="8" r="2" fill="#8B5CF6" fillOpacity="0.5" />
          <rect x="5" y="4" width="3" height="2.5" fill="#8B5CF6" fillOpacity="0.5" />
          <rect x="8" y="4" width="3" height="2.5" fill="#8B5CF6" fillOpacity="0.3" />
        </svg>
      )
    case 'code':
      return (
        <svg {...common}>
          <path
            d="M3 1.5H9L13 5.5V13.5C13 14.3284 12.3284 15 11.5 15H3C2.17157 15 1.5 14.3284 1.5 13.5V3C1.5 2.17157 2.17157 1.5 3 1.5Z"
            fill="#5A7C9E"
            fillOpacity="0.14"
            stroke="#5A7C9E"
            strokeWidth="1"
          />
          <path d="M9 1.5V5.5H13" stroke="#5A7C9E" strokeWidth="1" />
          <path
            d="M5.5 9L4 10.5L5.5 12M9 9L10.5 10.5L9 12"
            stroke="#5A7C9E"
            strokeWidth="1"
            strokeLinecap="round"
            strokeLinejoin="round"
            fill="none"
          />
        </svg>
      )
    case 'archive':
      return (
        <svg {...common}>
          <rect
            x="1.5"
            y="2.5"
            width="13"
            height="4"
            fill="#8A8270"
            fillOpacity="0.2"
            stroke="#8A8270"
            strokeWidth="1"
          />
          <path
            d="M2.5 6.5H13.5V12.5C13.5 13.3284 12.8284 14 12 14H4C3.17157 14 2.5 13.3284 2.5 12.5V6.5Z"
            fill="#8A8270"
            fillOpacity="0.14"
            stroke="#8A8270"
            strokeWidth="1"
          />
          <rect x="7" y="8.5" width="2" height="2.5" fill="#8A8270" fillOpacity="0.6" />
        </svg>
      )
    case 'doc':
    default:
      return (
        <svg {...common}>
          <path
            d="M3 1.5H9L13 5.5V13.5C13 14.3284 12.3284 15 11.5 15H3C2.17157 15 1.5 14.3284 1.5 13.5V3C1.5 2.17157 2.17157 1.5 3 1.5Z"
            fill="#4A89DC"
            fillOpacity="0.14"
            stroke="#4A89DC"
            strokeWidth="1"
          />
          <path d="M9 1.5V5.5H13" stroke="#4A89DC" strokeWidth="1" />
          <path
            d="M4.5 8H10M4.5 10H10M4.5 12H8"
            stroke="#4A89DC"
            strokeWidth="1"
            strokeLinecap="round"
          />
        </svg>
      )
  }
}
