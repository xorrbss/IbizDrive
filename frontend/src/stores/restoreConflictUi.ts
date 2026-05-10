import { create } from 'zustand'
import type { RestoreConflictPayload, TrashItemType } from '@/types/trash'

/**
 * 휴지통 복원 시 RESTORE_CONFLICT 발생 시 띄우는 RestoreConflictDialog 의 상태.
 *
 * - `targetType` / `targetId` — 복원 대상 (file 또는 folder).
 * - `originalName` — 원본 이름 (사용자에게 표시 + 자동 제안 이름의 base).
 * - `sourceFolderId` — 원위치 부모 폴더 id (mutation 의 invalidate 정밀화용, nullable).
 * - `payload` — Plan E T13: backend RESTORE_CONFLICT envelope `details.*` (reason 분기용).
 *   `null`이면 v1.x 호환 동작(`name_conflict` 가정)을 유지.
 * - `error` — 다이얼로그 inline alert (NAME_CONFLICT / VALIDATION_ERROR 메시지).
 *
 * 패턴: `renameUi.ts` 미러.
 */
type RestoreConflictUiState = {
  isOpen: boolean
  targetType: TrashItemType | null
  targetId: string | null
  originalName: string
  sourceFolderId: string | null
  payload: RestoreConflictPayload | null
  error: string | null
  open: (params: {
    type: TrashItemType
    id: string
    originalName: string
    sourceFolderId: string | null
    payload?: RestoreConflictPayload | null
  }) => void
  close: () => void
  setError: (msg: string | null) => void
}

export const useRestoreConflictUiStore = create<RestoreConflictUiState>((set) => ({
  isOpen: false,
  targetType: null,
  targetId: null,
  originalName: '',
  sourceFolderId: null,
  payload: null,
  error: null,
  open: ({ type, id, originalName, sourceFolderId, payload }) =>
    set({
      isOpen: true,
      targetType: type,
      targetId: id,
      originalName,
      sourceFolderId,
      payload: payload ?? null,
      error: null,
    }),
  close: () =>
    set({
      isOpen: false,
      targetType: null,
      targetId: null,
      originalName: '',
      sourceFolderId: null,
      payload: null,
      error: null,
    }),
  setError: (msg) => set({ error: msg }),
}))
