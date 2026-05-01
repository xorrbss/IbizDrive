/**
 * 공유(Share) 타입 — F4 + F5 (docs/02 §7.9, ADR #34 mirror).
 *
 * backend `ShareDto` record (com.ibizdrive.share.ShareDto) 와 wire 1:1.
 * - `Share` entity 컬럼 그대로 직렬화 — Jackson record 표준. 추가 필드(subject/preset 등)는 join이
 *   필요하므로 본 wire에 부재 (별도 backend 트랙 backlog).
 * - `fileId` / `folderId`: V6 `shares` 테이블 XOR CHECK 동형 — 한 row는 file 공유 또는 folder 공유,
 *   둘 다 NULL 또는 둘 다 NOT NULL은 backend 자체에서 거부.
 * - `revokedAt` / `revokedBy`: by-me/with-me cursor 쿼리는 active row만 반환 → 두 필드는 항상 null.
 *   향후 admin 화면(revoked 이력)이 동일 wire를 재사용할 수 있도록 노출 유지.
 * - `createdAt` / `expiresAt` / `revokedAt` 는 ISO 8601 (Jackson `Instant` → `"2026-04-30T12:34:56Z"`).
 *
 * `ShareCreateRequest`(POST body)는 별도 record (`subjects/preset/expiresAt?/message?`) — backend가
 * 받아서 `permissions` row 생성 시 subject_type/subject_id/preset로 사용. 따라서 request 측에는
 * `ShareSubject`/`ShareSubjectType`/`SharePreset` 타입이 살아 있다.
 */

// ─── Request body types (POST /api/files/:id/share, POST /api/folders/:id/share) ──────

export type ShareSubjectType = 'user' | 'department' | 'role' | 'everyone'

export interface ShareSubject {
  type: ShareSubjectType
  /** 'everyone' 일 때만 omit 가능. 그 외에는 UUID 필수 (V5 CHECK). */
  id?: string
}

export type SharePreset = 'read' | 'upload' | 'edit' | 'admin'

export interface ShareCreateRequest {
  subjects: ShareSubject[]
  preset: SharePreset
  /** ISO 8601 미래 시각. 미설정 시 omit. */
  expiresAt?: string
  /** 최대 1000자. 미설정 시 omit. */
  message?: string
}

// ─── Response wire (GET by-me / with-me, POST file/folder share) ──────────────────────

/**
 * Share row wire. backend `ShareDto` record 정합. 한 row는 file 공유 또는 folder 공유 — XOR.
 *
 * 새 share 발행 응답(POST 201)에서도 동일 형상으로 받음 — `{ shares: ShareDto[] }` envelope.
 */
export interface ShareDto {
  id: string
  /** file 공유 row면 string. folder 공유 row면 null. (XOR with folderId) */
  fileId: string | null
  /** folder 공유 row면 string. file 공유 row면 null. (XOR with fileId) */
  folderId: string | null
  permissionId: string
  sharedBy: string
  /** 최대 1000자, 미설정 시 null. */
  message: string | null
  /** ISO 8601, 무기한 시 null. */
  expiresAt: string | null
  createdAt: string
  /** active row(by-me/with-me)에서는 항상 null. revoked 이력 화면용 예약 필드. */
  revokedAt: string | null
  /** revokedAt와 pair-set (V6 CHECK). active row에서 항상 null. */
  revokedBy: string | null
}

export interface SharePage {
  items: ShareDto[]
  /** 다음 페이지가 있을 때만 backend가 echo. 없으면 null. */
  nextCursor: string | null
}

// ─── UI helper ────────────────────────────────────────────────────────────────────────

/**
 * ShareDialog 진입 대상 — file/folder 두 종류.
 *
 * `useShareUiStore.open(target)` / `useCreateShare.mutate({target, req})` 등에서 공용으로 쓴다.
 * `name`은 dialog 표시용 (UI only — backend 미전송).
 */
export type ShareTarget =
  | { kind: 'file'; id: string; name: string }
  | { kind: 'folder'; id: string; name: string }
