import { create } from 'zustand'
import type { ShareTarget } from '@/types/share'

/**
 * 공유 다이얼로그 UI 상태 — F5에서 file/folder 양립으로 generalize (M8 docs/01 §14, F4, F5).
 *
 * `target` discriminator로 file 또는 folder 진입을 표현. backend는 두 endpoint 분리(F4/A12)이고
 * `Share` row도 file_id/folder_id XOR이므로 store도 동일 형상이 자연스럽다.
 */
type ShareUiState = {
  isOpen: boolean
  /** open 상태에서만 non-null. close 시 다시 null. */
  target: ShareTarget | null
  open: (target: ShareTarget) => void
  close: () => void
}

export const useShareUiStore = create<ShareUiState>((set) => ({
  isOpen: false,
  target: null,
  open: (target) => set({ isOpen: true, target }),
  close: () => set({ isOpen: false, target: null }),
}))
