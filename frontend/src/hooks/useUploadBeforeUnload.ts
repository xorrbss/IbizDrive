'use client'
import { useEffect } from 'react'
import { useUploadStore } from '@/stores/upload'

export function useUploadBeforeUnload() {
  const pendingCount = useUploadStore((s) =>
    s.queue.filter(
      (t) => t.status === 'queued' || t.status === 'uploading' || t.status === 'conflict',
    ).length,
  )

  useEffect(() => {
    if (pendingCount === 0) return
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault()
      e.returnValue = ''
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [pendingCount])
}
