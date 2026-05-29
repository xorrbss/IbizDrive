'use client'
import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'
import { normalizedNameForDedup } from '@/lib/normalize'
import { useUpload } from '@/hooks/useUpload'
import type { FolderUploadPlan } from '@/lib/folderUpload'

export type FolderUploadResult = {
  /** enqueue 호출 그룹 수 (= 파일이 들어간 distinct 폴더 수) */
  uploadedGroups: number
  /** 새로 생성된 폴더 수 (병합된 기존 폴더 제외) */
  createdFolders: number
  /** 서브트리별 생성 실패 (권한/이름) — 나머지는 계속 진행 */
  errors: Array<{ path: string; message: string }>
}

const ROOT_KEY = JSON.stringify([] as string[])
const keyOf = (segments: string[]): string => JSON.stringify(segments)

/** 정규화 실패(예약어/금지문자) 시 null — 매칭 불가로 처리하고 createFolder가 400을 surface. */
function safeNorm(name: string): string | null {
  try {
    return normalizedNameForDedup(name)
  } catch {
    return null
  }
}

function statusOf(e: unknown): number | undefined {
  return (e as { status?: number } | null)?.status
}

function messageOf(e: unknown): string {
  const status = statusOf(e)
  if (status === 403) return '폴더 생성 권한이 없습니다'
  if (status === 400) return '폴더 이름이 올바르지 않습니다'
  return e instanceof Error ? e.message : '폴더 생성에 실패했습니다'
}

/**
 * 폴더 업로드 오케스트레이터 (docs/01 §9.6).
 *
 * `FolderUploadPlan`의 디렉토리 경로를 깊이순으로 materialize한다 — parent별 자식 목록을 1회 조회해
 * (`getFolderChildren`) 동일 이름이면 **병합**(id 재사용), 없으면 `createFolder`. 그 후 파일을 해석된
 * folderId별로 그룹핑해 기존 `useUpload.enqueue`에 넘긴다. 백엔드/업로드 store는 변경하지 않는다.
 *
 * - 폴더 이름 충돌 = 병합 (ADR §9.6). 파일명 충돌만 기존 UploadConflictDialog가 처리.
 * - createFolder 409(race) → 자식 목록 재조회로 id 해석.
 * - 403/400 등 → 해당 서브트리 스킵 + errors 수집, 나머지는 계속.
 */
export function useFolderUpload() {
  const qc = useQueryClient()
  const { enqueue } = useUpload()

  const uploadFolder = useCallback(
    async (
      plan: FolderUploadPlan,
      baseFolderId: string,
    ): Promise<FolderUploadResult> => {
      const resolved = new Map<string, string>([[ROOT_KEY, baseFolderId]])
      const childrenCache = new Map<string, Array<{ name: string; id: string }>>()
      const createdParents = new Set<string>()
      const errors: FolderUploadResult['errors'] = []
      let createdFolders = 0

      const getChildren = async (parentId: string) => {
        let cached = childrenCache.get(parentId)
        if (!cached) {
          cached = (await api.getFolderChildren(parentId)).map((f) => ({
            name: f.name,
            id: f.id,
          }))
          childrenCache.set(parentId, cached)
        }
        return cached
      }

      const findChild = (children: Array<{ name: string; id: string }>, name: string) => {
        const target = safeNorm(name)
        if (target === null) return undefined
        return children.find((c) => safeNorm(c.name) === target)
      }

      const resolveOrCreate = async (parentId: string, name: string): Promise<string> => {
        const existing = findChild(await getChildren(parentId), name)
        if (existing) return existing.id
        try {
          const created = await api.createFolder(parentId, name)
          childrenCache.get(parentId)?.push({ name: created.name, id: created.id })
          createdParents.add(parentId)
          createdFolders++
          return created.id
        } catch (e) {
          if (statusOf(e) === 409) {
            // race — 다른 클라이언트/요청이 먼저 생성. 재조회로 병합.
            childrenCache.delete(parentId)
            const again = findChild(await getChildren(parentId), name)
            if (again) return again.id
          }
          throw e
        }
      }

      // 디렉토리: 깊이 오름차순 — parent가 child보다 먼저 해석되도록.
      const dirPaths = [...plan.dirPaths].sort((a, b) => a.length - b.length)
      for (const dirPath of dirPaths) {
        const parentSegs = dirPath.slice(0, -1)
        const segName = dirPath[dirPath.length - 1]
        const parentId = resolved.get(keyOf(parentSegs))
        if (!parentId) {
          errors.push({ path: dirPath.join('/'), message: '상위 폴더 생성 실패로 건너뜀' })
          continue
        }
        try {
          resolved.set(keyOf(dirPath), await resolveOrCreate(parentId, segName))
        } catch (e) {
          errors.push({ path: dirPath.join('/'), message: messageOf(e) })
        }
      }

      // 파일을 해석된 folderId별 그룹으로 묶어 기존 파이프라인에 enqueue.
      const groups = new Map<string, File[]>()
      for (const entry of plan.entries) {
        const folderId = resolved.get(keyOf(entry.pathSegments))
        if (!folderId) {
          errors.push({
            path: [...entry.pathSegments, entry.file.name].join('/'),
            message: '폴더 생성 실패로 건너뜀',
          })
          continue
        }
        const arr = groups.get(folderId) ?? []
        arr.push(entry.file)
        groups.set(folderId, arr)
      }
      for (const [folderId, files] of groups) enqueue(files, folderId)

      // 새 폴더가 부모 목록에 즉시 보이도록 무효화 (업로드 파일 listing은 useUpload가 별도 무효화).
      await Promise.all(
        [...createdParents].map((parentId) =>
          invalidations.afterFolderCreated(qc, { parentId }),
        ),
      )

      return { uploadedGroups: groups.size, createdFolders, errors }
    },
    [qc, enqueue],
  )

  return { uploadFolder }
}
