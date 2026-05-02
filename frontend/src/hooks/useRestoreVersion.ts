'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

type Vars = {
  fileId: string
  versionId: string
  /** list view 정확 무효화용. 모르면 omit → `qk.files()` 보수 무효화. */
  parentFolderId?: string
}

type Options = {
  onSuccess?: (vars: Vars) => void
  onError?: (err: unknown, vars: Vars) => void
}

/**
 * 파일 버전 복원 mutation (M-RP.2.2, ADR #39).
 *
 * backend는 옵션 A: `current_version_id` 재지정 + denormalized 메타 동기화 (`files.size_bytes`,
 * `files.mime_type`를 target version의 값으로 갱신 — FileUploadService:214-217 invariant 보존).
 * 새 version row는 생성하지 않는다. 멱등 — 같은 versionId 재호출은 audit emit 없는 200 no-op.
 *
 * 무효화 매트릭스 (docs/01 §6.2):
 * - `qk.fileDetail(fileId)` — `currentVersionId`/sizeBytes/mimeType 신선화 (RightPanel detail 탭)
 * - `qk.fileVersions(fileId)` — `isCurrent` 표시가 다른 version으로 이동 (VersionsTab 행 갱신)
 * - `qk.filesListPrefix(parentFolderId)` — list view의 size 컬럼 신선화. 호출자가 parentFolderId를
 *   알면 정확한 prefix 무효화, 모르면 `qk.files()` 보수 무효화 (afterRestore 패턴 답습).
 *
 * 원칙 #3 — 낙관적 업데이트 없음 (파괴적 액션). pending UI는 호출부(VersionsTab) 책임.
 */

export function useRestoreVersion(options: Options = {}) {
  const qc = useQueryClient()

  return useMutation<void, Error, Vars>({
    mutationFn: ({ fileId, versionId }) => api.restoreVersion(fileId, versionId),

    onSuccess: async (_data, vars) => {
      const tasks = [
        qc.invalidateQueries({ queryKey: qk.fileDetail(vars.fileId) }),
        qc.invalidateQueries({ queryKey: qk.fileVersions(vars.fileId) }),
      ]
      if (vars.parentFolderId) {
        tasks.push(
          qc.invalidateQueries({ queryKey: qk.filesListPrefix(vars.parentFolderId) }),
        )
      } else {
        tasks.push(qc.invalidateQueries({ queryKey: qk.files() }))
      }
      await Promise.all(tasks)
      options.onSuccess?.(vars)
    },

    onError: (err, vars) => {
      options.onError?.(err, vars)
    },
  })
}
