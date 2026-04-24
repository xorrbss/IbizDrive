import { create } from 'zustand'
import type { UploadErrorKind } from '@/lib/uploadErrors'

export type UploadConflictResolution = 'new_version' | 'rename' | 'skip'

export type UploadTask = {
  id: string
  file: File
  targetFolderId: string
  status: 'queued' | 'uploading' | 'done' | 'failed' | 'conflict'
  progress: number
  uploadedBytes: number
  error?: { kind: UploadErrorKind; message: string }
  conflictWith?: { fileId: string; fileName: string }
  conflictResolution?: UploadConflictResolution
  enqueuedAt: number
}

export type UploadState = {
  queue: UploadTask[]
  applyToAll: UploadConflictResolution | null
  enqueue: (files: File[], targetFolderId: string) => string[]
  updateTask: (id: string, patch: Partial<UploadTask>) => void
  resolveConflict: (
    id: string,
    resolution: UploadConflictResolution,
    applyToAll?: boolean,
  ) => void
  retry: (id: string) => void
  cancel: (id: string) => void
  clearDone: () => void
  pendingCount: () => number
}

function uid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return `u_${Math.random().toString(36).slice(2)}_${Date.now()}`
}

export const useUploadStore = create<UploadState>((set, get) => ({
  queue: [],
  applyToAll: null,

  enqueue: (files, targetFolderId) => {
    const now = Date.now()
    const tasks: UploadTask[] = files.map((file) => ({
      id: uid(),
      file,
      targetFolderId,
      status: 'queued',
      progress: 0,
      uploadedBytes: 0,
      enqueuedAt: now,
    }))
    set((s) => ({ queue: [...s.queue, ...tasks] }))
    return tasks.map((t) => t.id)
  },

  updateTask: (id, patch) =>
    set((s) => ({
      queue: s.queue.map((t) => (t.id === id ? { ...t, ...patch } : t)),
    })),

  resolveConflict: (id, resolution, applyToAll) => {
    set((s) => ({
      applyToAll: applyToAll ? resolution : s.applyToAll,
      queue: s.queue.map((t) => {
        if (t.id !== id) return t
        if (resolution === 'skip') {
          return { ...t, status: 'done', progress: 1, conflictResolution: 'skip' }
        }
        return {
          ...t,
          status: 'queued',
          conflictResolution: resolution,
          progress: 0,
          uploadedBytes: 0,
        }
      }),
    }))
  },

  retry: (id) =>
    set((s) => ({
      queue: s.queue.map((t) =>
        t.id === id
          ? { ...t, status: 'queued', progress: 0, uploadedBytes: 0, error: undefined }
          : t,
      ),
    })),

  cancel: (id) =>
    set((s) => ({
      queue: s.queue.map((t) =>
        t.id === id
          ? { ...t, status: 'failed', error: { kind: 'network', message: '취소됨' } }
          : t,
      ),
    })),

  clearDone: () =>
    set((s) => ({
      queue: s.queue.filter((t) => t.status !== 'done'),
      applyToAll: null,
    })),

  pendingCount: () =>
    get().queue.filter(
      (t) => t.status === 'queued' || t.status === 'uploading' || t.status === 'conflict',
    ).length,
}))
