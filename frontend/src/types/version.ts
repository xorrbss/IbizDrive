/**
 * 파일 버전 wire DTO — backend `com.ibizdrive.file.dto.FileVersionDto` (record) 1:1 미러.
 *
 * - JSON 키 표기: camelCase (backend record와 동일).
 * - `JsonInclude.NON_NULL`로 nullable 필드는 응답에서 누락 가능 → optional `?:` 표기.
 * - `scanStatus`는 V5 CHECK enum의 lowercase dbValue (`pending` | `clean` | `infected` | `unknown`).
 *   backend가 dbValue 문자열을 그대로 wire에 노출. 새 status 추가 시 enum 동기화는 backend가 책임.
 *
 * 트랙: M-RP.1 (`dev/active/m-rp-rightpanel-completion`).
 * 계약 origin: `backend/src/main/java/com/ibizdrive/file/dto/FileVersionDto.java`.
 */
export type VersionScanStatus = 'pending' | 'clean' | 'infected' | 'unknown'

export interface FileVersionDto {
  id: string
  versionNumber: number
  sizeBytes: number
  /** SHA-256 hex. nullable (legacy/보정 row 가능성) */
  checksumSha256?: string
  /** RFC 6838 mime. nullable (legacy/보정) */
  mimeType?: string
  /** V5 CHECK 위반 시 backend가 raw 값 노출하지 않으므로 known set이지만 forward compat을 위해 string */
  scanStatus?: VersionScanStatus | string
  uploadedBy: string
  /** ISO 8601 instant — `Instant.toString()` 직렬화 */
  uploadedAt: string
  comment?: string
  /** `file.currentVersionId === version.id` 평가 결과를 backend가 채워서 내려줌 */
  isCurrent: boolean
}
