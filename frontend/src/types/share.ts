/**
 * 공유(Share) 타입 — F4 (docs/02 §7.9, ADR #34 mirror).
 *
 * backend `ShareDto` / `SharePage` (com.ibizdrive.share.*) 의 wire 1:1.
 * - subject 4종 wire = lower-case ('user' | 'department' | 'role' | 'everyone'),
 *   id는 'everyone' 일 때만 null (V5 idx_permissions_unique CHECK 동형).
 * - preset wire = V5 4값 ('read' | 'upload' | 'edit' | 'admin').
 *   `Preset.SHARE` 는 backend enum 정의는 있으나 V5 CHECK 미지원 → 본 클라이언트 미노출 (ADR #34 backlog).
 * - `expiresAt` / `createdAt` 는 ISO 8601 문자열 (Jackson `Instant` → `"2026-04-30T12:34:56Z"`).
 *
 * 본 파일은 backend 응답을 그대로 표현하므로 형태 drift는 backend wire 변경 ADR을 동반해야 한다.
 */

export type ShareSubjectType = 'user' | 'department' | 'role' | 'everyone'

export interface ShareSubject {
  type: ShareSubjectType
  /** 'everyone' 일 때만 omit 가능. 그 외에는 UUID 필수 (V5 CHECK). */
  id?: string
}

export type SharePreset = 'read' | 'upload' | 'edit' | 'admin'

export interface ShareDto {
  id: string
  fileId: string
  permissionId: string
  sharedBy: string
  subjectType: ShareSubjectType
  /** 'everyone' 일 때 null. */
  subjectId: string | null
  preset: SharePreset
  /** ISO 8601, 미설정 시 null. */
  expiresAt: string | null
  /** 최대 1000자, 미설정 시 null. */
  message: string | null
  createdAt: string
}

export interface ShareCreateRequest {
  subjects: ShareSubject[]
  preset: SharePreset
  /** ISO 8601 미래 시각. 미설정 시 omit. */
  expiresAt?: string
  /** 최대 1000자. 미설정 시 omit. */
  message?: string
}

export interface SharePage {
  items: ShareDto[]
  /** 다음 페이지가 있을 때만 backend가 echo. 없으면 null. */
  nextCursor: string | null
}
