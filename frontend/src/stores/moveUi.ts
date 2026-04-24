import { create } from 'zustand'

type MoveUiState = {
  isMoveDialogOpen: boolean
  moveIds: string[]
  moveSourceFolderId: string | null
  openMoveDialog: (ids: string[], sourceFolderId: string) => void
  closeMoveDialog: () => void
}

export const useMoveUiStore = create<MoveUiState>((set) => ({
  isMoveDialogOpen: false,
  moveIds: [],
  moveSourceFolderId: null,
  openMoveDialog: (ids, sourceFolderId) =>
    set({ isMoveDialogOpen: true, moveIds: ids, moveSourceFolderId: sourceFolderId }),
  closeMoveDialog: () =>
    set({ isMoveDialogOpen: false, moveIds: [], moveSourceFolderId: null }),
}))
