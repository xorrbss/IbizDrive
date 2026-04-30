import { create } from 'zustand'

/**
 * 공유 다이얼로그 UI 상태 (M8 docs/01 §14).
 *
 * 단일 파일 공유 진입점. 폴더/다중 파일 공유는 v1.x.
 * 백엔드 endpoint(`POST /api/files/:id/share`, docs/03 §3.1)는 미구현 — 본 store는 UI만.
 */
type ShareUiState = {
  isOpen: boolean
  fileId: string | null
  fileName: string
  open: (fileId: string, fileName: string) => void
  close: () => void
}

export const useShareUiStore = create<ShareUiState>((set) => ({
  isOpen: false,
  fileId: null,
  fileName: '',
  open: (fileId, fileName) => set({ isOpen: true, fileId, fileName }),
  close: () => set({ isOpen: false, fileId: null, fileName: '' }),
}))
