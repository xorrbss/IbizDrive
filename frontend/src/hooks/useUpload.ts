'use client'
import { useCallback, useEffect, useRef } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import { classifyError } from '@/lib/uploadErrors'
import {
  useUploadStore,
  type UploadConflictResolution,
  type UploadTask,
} from '@/stores/upload'

type XhrLike = XMLHttpRequest

/**
 * useUpload — store의 queued task를 감지해 transport(XMLHttpRequest)를 기동/취소한다.
 *
 * - queued → uploading (XHR 시작)
 * - onprogress → updateTask(progress)
 * - onload:
 *    - 200 → done + invalidate filesInFolder(targetFolderId)
 *    - 409 → applyToAll이면 자동 해결, 아니면 status: 'conflict'
 *    - 기타 → failed + classifyError
 * - onerror → failed + network
 * - cancel(id) → xhr.abort() + store.cancel(id)
 * - retry(id) → store.retry(id) → 상태 감시가 새 XHR 기동
 */
export function useUpload() {
  const queryClient = useQueryClient()
  const xhrMap = useRef<Map<string, XhrLike>>(new Map())
  const enqueue = useUploadStore((s) => s.enqueue)
  const updateTask = useUploadStore((s) => s.updateTask)
  const resolveConflictStore = useUploadStore((s) => s.resolveConflict)
  const retryStore = useUploadStore((s) => s.retry)
  const cancelStore = useUploadStore((s) => s.cancel)

  const startTask = useCallback(
    async (task: UploadTask) => {
      const prev = xhrMap.current.get(task.id)
      if (prev) prev.abort()

      const newName =
        task.conflictResolution === 'rename' ? renameFile(task.file.name) : undefined

      let xhr: XhrLike
      try {
        xhr = await api.uploadFile({
          file: task.file,
          folderId: task.targetFolderId,
          resolution:
            task.conflictResolution === 'new_version' ||
            task.conflictResolution === 'rename'
              ? task.conflictResolution
              : undefined,
          newName,
        })
      } catch {
        // ensureCsrfToken의 `/api/auth/csrf` 부트스트랩 fetch가 network error로 reject되는 경우.
        // xhr가 아직 만들어지지 않았으므로 onerror 경로를 탈 수 없음 → 직접 failed 마킹.
        updateTask(task.id, {
          status: 'failed',
          error: { kind: 'network', message: '네트워크 연결을 확인하세요' },
        })
        return
      }

      // csrf 부트스트랩 중 cancel 등으로 task가 'queued'를 벗어났을 수 있음 (#165 follow-up).
      // xhrMap 등록 전이라 store.cancel이 xhr를 abort하지 못한 상태 → 명시적 abort.
      const cur = useUploadStore.getState().queue.find((t) => t.id === task.id)
      if (!cur || cur.status !== 'queued') {
        xhr.abort()
        return
      }

      xhrMap.current.set(task.id, xhr)

      updateTask(task.id, { status: 'uploading' })

      xhr.upload.onprogress = (e) => {
        if (!e.lengthComputable) return
        updateTask(task.id, {
          progress: e.loaded / e.total,
          uploadedBytes: e.loaded,
        })
      }

      xhr.onload = () => {
        xhrMap.current.delete(task.id)
        // backend FileUploadController: 신규 파일 → 201 Created, 기존 파일에 새 version → 200 OK.
        // 둘 다 성공 — done + listing invalidate (sort/dir 변종 일괄 — qk.files() 'list' prefix).
        if (xhr.status === 200 || xhr.status === 201) {
          updateTask(task.id, { status: 'done', progress: 1 })
          queryClient.invalidateQueries({
            queryKey: [...qk.files(), 'list', task.targetFolderId],
          })
          return
        }
        if (xhr.status === 409) {
          // backend envelope: { error: { code: 'RENAME_CONFLICT', details? } }.
          // details에 충돌 파일 정보(fileId/fileName)가 있으면 surface — 없으면 conflictWith
          // undefined로 두면 UploadConflictDialog가 task.file.name으로 폴백.
          let conflictWith: UploadTask['conflictWith'] | undefined
          try {
            const body = JSON.parse(xhr.responseText) as {
              error?: { details?: { fileId?: string; fileName?: string } }
            }
            const d = body.error?.details
            if (d?.fileId && d?.fileName) {
              conflictWith = { fileId: d.fileId, fileName: d.fileName }
            }
          } catch {
            // ignore — body 부재/JSON 파싱 실패 시 conflictWith undefined 유지
          }
          const apply = useUploadStore.getState().applyToAll
          if (apply && apply !== 'skip') {
            updateTask(task.id, { status: 'conflict', conflictWith })
            resolveConflictStore(task.id, apply)
          } else if (apply === 'skip') {
            updateTask(task.id, { status: 'conflict', conflictWith })
            resolveConflictStore(task.id, 'skip')
          } else {
            updateTask(task.id, { status: 'conflict', conflictWith })
          }
          return
        }
        updateTask(task.id, { status: 'failed', error: classifyError(xhr) })
      }

      xhr.onerror = () => {
        xhrMap.current.delete(task.id)
        const cur = useUploadStore.getState().queue.find((t) => t.id === task.id)
        if (cur?.status === 'failed') return // cancel에 의한 onerror — 중복 업데이트 스킵
        updateTask(task.id, {
          status: 'failed',
          error: { kind: 'network', message: '네트워크 연결을 확인하세요' },
        })
      }
    },
    [queryClient, updateTask, resolveConflictStore],
  )

  // queued task 감지 → startTask
  useEffect(() => {
    const unsub = useUploadStore.subscribe((state, prev) => {
      for (const t of state.queue) {
        if (t.status !== 'queued') continue
        const prevTask = prev.queue.find((p) => p.id === t.id)
        if (prevTask?.status === 'queued') continue
        startTask(t)
      }
    })
    const xhrMapSnapshot = xhrMap.current
    return () => {
      unsub()
      for (const xhr of xhrMapSnapshot.values()) xhr.abort()
      xhrMapSnapshot.clear()
    }
  }, [startTask])

  const cancel = useCallback(
    (id: string) => {
      const xhr = xhrMap.current.get(id)
      if (xhr) {
        xhr.abort()
        xhrMap.current.delete(id)
      }
      cancelStore(id)
    },
    [cancelStore],
  )

  const retry = useCallback(
    (id: string) => {
      const xhr = xhrMap.current.get(id)
      if (xhr) {
        xhr.abort()
        xhrMap.current.delete(id)
      }
      retryStore(id)
    },
    [retryStore],
  )

  const resolveConflict = useCallback(
    (id: string, resolution: UploadConflictResolution, applyToAll?: boolean) => {
      resolveConflictStore(id, resolution, applyToAll)
    },
    [resolveConflictStore],
  )

  return {
    enqueue,
    cancel,
    retry,
    resolveConflict,
  }
}

function renameFile(name: string): string {
  const dot = name.lastIndexOf('.')
  if (dot === -1) return `${name} (2)`
  return `${name.slice(0, dot)} (2)${name.slice(dot)}`
}
