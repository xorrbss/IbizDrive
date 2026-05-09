/**
 * Backend error envelope code constants (docs/02 §8 mirror).
 *
 * Currently includes only Plan D cross-workspace move codes. Existing string-literal
 * usage across the frontend (`'RENAME_CONFLICT'`, `'RESTORE_CONFLICT'`, etc.) will be
 * migrated to constants in a separate track.
 */

/** Plan D — same-scope move guard violation (allowCrossScope=false인데 cross 시도). HTTP 409. */
export const ERR_CROSS_SCOPE_MOVE = 'ERR_CROSS_SCOPE_MOVE' as const

/** Plan D — cross-workspace move 시 source `EDIT+SHARE` 또는 destination `UPLOAD` 부재. HTTP 403. */
export const ERR_DEST_WORKSPACE_DENIED = 'ERR_DEST_WORKSPACE_DENIED' as const

/** Plan D — destinationFolderId가 null(=root 직접 이동) 또는 자기 자신/후손. HTTP 400. */
export const ERR_INVALID_DESTINATION = 'ERR_INVALID_DESTINATION' as const

export type ErrorCode =
  | typeof ERR_CROSS_SCOPE_MOVE
  | typeof ERR_DEST_WORKSPACE_DENIED
  | typeof ERR_INVALID_DESTINATION
