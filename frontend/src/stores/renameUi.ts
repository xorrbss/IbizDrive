import { create } from 'zustand'

type RenameUiState = {
  isOpen: boolean
  targetId: string | null
  targetName: string
  error: string | null
  open: (id: string, name: string) => void
  close: () => void
  setError: (msg: string | null) => void
}

export const useRenameUiStore = create<RenameUiState>((set) => ({
  isOpen: false,
  targetId: null,
  targetName: '',
  error: null,
  open: (id, name) =>
    set({ isOpen: true, targetId: id, targetName: name, error: null }),
  close: () =>
    set({ isOpen: false, targetId: null, targetName: '', error: null }),
  setError: (msg) => set({ error: msg }),
}))
