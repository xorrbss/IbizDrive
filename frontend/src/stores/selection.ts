import { create } from 'zustand'

type SelectionState = {
  ids: Set<string>
  lastClickedId: string | null
  pendingIds: Set<string>
  toggle: (id: string) => void
  selectRange: (to: string, orderedIds: string[]) => void
  selectOnly: (id: string) => void
  clear: () => void
  selectAll: (ids: string[]) => void
  markPending: (ids: string[]) => void
  unmarkPending: (ids: string[]) => void
}

export const useSelectionStore = create<SelectionState>((set, get) => ({
  ids: new Set(),
  lastClickedId: null,
  pendingIds: new Set(),

  toggle: (id) =>
    set((s) => {
      const next = new Set(s.ids)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return { ids: next, lastClickedId: id }
    }),

  selectRange: (to, orderedIds) => {
    const { lastClickedId, ids, pendingIds } = get()

    const anchorMissing = !lastClickedId
    const anchorPending = lastClickedId ? pendingIds.has(lastClickedId) : false
    const anchorNotInFolder = lastClickedId
      ? !orderedIds.includes(lastClickedId)
      : false

    if (anchorMissing || anchorPending || anchorNotInFolder) {
      set({ ids: new Set([to]), lastClickedId: to })
      return
    }

    const a = orderedIds.indexOf(lastClickedId!)
    const b = orderedIds.indexOf(to)
    const [start, end] = a < b ? [a, b] : [b, a]
    const next = new Set(ids)
    orderedIds
      .slice(start, end + 1)
      .filter((id) => !pendingIds.has(id))
      .forEach((id) => next.add(id))
    set({ ids: next })
  },

  selectOnly: (id) => set({ ids: new Set([id]), lastClickedId: id }),

  clear: () => set({ ids: new Set(), lastClickedId: null }),

  selectAll: (ids) => set({ ids: new Set(ids) }),

  markPending: (idsToMark) =>
    set((s) => {
      const nextPending = new Set(s.pendingIds)
      const nextSelected = new Set(s.ids)
      idsToMark.forEach((id) => {
        nextPending.add(id)
        nextSelected.delete(id) // 상호 배제
      })
      return { pendingIds: nextPending, ids: nextSelected }
    }),

  unmarkPending: (idsToUnmark) =>
    set((s) => {
      const next = new Set(s.pendingIds)
      idsToUnmark.forEach((id) => next.delete(id))
      return { pendingIds: next }
    }),
}))
