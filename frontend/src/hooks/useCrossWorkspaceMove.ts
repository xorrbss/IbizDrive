'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { crossWorkspaceMoveFolder, crossWorkspaceMoveFile } from '@/lib/api.move'
import type {
  CrossWorkspaceMoveFolderBody,
  CrossWorkspaceMoveFileBody,
} from '@/lib/api.move'
import { qk } from '@/lib/queryKeys'
import type { FolderDetail } from '@/types/folder'
import type { FileItem } from '@/types/file'

/**
 * Plan D Task 24 — cross-workspace 이동 실행 hooks.
 *
 * 원칙 #3 — 낙관적 업데이트 없음. 이동은 파괴적 액션.
 *
 * 무효화 매트릭스 (same-scope보다 넓음):
 *   - folderChildren 전체 prefix (source + destination 워크스페이스 트리 갱신)
 *   - fileDetail (file 전용 — 새 parentId 반영)
 *   - permissions — 이동 후 permissions이 변경되므로 source + dest 노드 무효화
 *   - shares() prefix — revokedShares가 발생하므로 by-me/with-me 모두 갱신
 *   - workspaces.me() — 팀/부서 워크스페이스 사이드바 갱신
 */

// ─── Folder Cross-Workspace Move ──────────────────────────────────────────────

export type CrossWorkspaceMoveFolderVars = {
  folderId: string
  /** 이동 전 현재 상위 폴더 ID (source 트리 무효화용). null이면 prefix 전체 보수 무효화. */
  sourceFolderId: string | null
  /** 이동 전 워크스페이스 scopeType (source folderChildren 키 구성). */
  sourceScopeType: 'department' | 'team'
  /** 이동 전 워크스페이스 scopeId (source folderChildren 키 구성). */
  sourceScopeId: string
  body: CrossWorkspaceMoveFolderBody
  /** 이동 후 워크스페이스 scopeType (destination folderChildren 키 구성). */
  destinationScopeType: 'department' | 'team'
  /** 이동 후 워크스페이스 scopeId (destination folderChildren 키 구성). */
  destinationScopeId: string
}

/** POST /api/folders/{id}/move { allowCrossScope: true } → { folder } */
export function useCrossWorkspaceMoveFolder() {
  const qc = useQueryClient()
  return useMutation<{ folder: FolderDetail }, Error, CrossWorkspaceMoveFolderVars>({
    mutationFn: ({ folderId, body }: CrossWorkspaceMoveFolderVars) =>
      crossWorkspaceMoveFolder(folderId, body),

    onSuccess: (_data, vars: CrossWorkspaceMoveFolderVars) => {
      const {
        folderId,
        sourceFolderId,
        sourceScopeType,
        sourceScopeId,
        body,
        destinationScopeType,
        destinationScopeId,
      } = vars

      // folderChildren 전체 prefix 무효화 (source + dest 트리 모두 포함)
      // 두 워크스페이스 전체를 prefix 매칭으로 처리. 세밀하게 분리해도 되지만
      // cross-scope 이동은 트리 전체가 갱신 대상 → 보수적으로 children prefix 전체.
      qc.invalidateQueries({ queryKey: [...qk.all, 'folders', 'children'] })

      // Source workspace root folderChildren (정밀 무효화)
      if (sourceFolderId) {
        qc.invalidateQueries({
          queryKey: qk.folderChildren(sourceScopeType, sourceScopeId, sourceFolderId),
        })
      }

      // Destination parent folderChildren (정밀 무효화)
      qc.invalidateQueries({
        queryKey: qk.folderChildren(
          destinationScopeType,
          destinationScopeId,
          body.targetParentId,
        ),
      })

      // Source + destination folder 노드 permissions 무효화
      if (sourceFolderId) {
        qc.invalidateQueries({ queryKey: qk.permissions(sourceFolderId) })
      }
      qc.invalidateQueries({ queryKey: qk.permissions(folderId) })

      // shares: revokedShares가 발생했으므로 by-me / with-me 모두 갱신
      qc.invalidateQueries({ queryKey: qk.shares() })

      // workspaces.me: 사이드바 트리 갱신
      qc.invalidateQueries({ queryKey: qk.workspaces.me() })
    },
  })
}

// ─── File Cross-Workspace Move ────────────────────────────────────────────────

export type CrossWorkspaceMoveFileVars = {
  fileId: string
  /** 이동 전 현재 폴더 ID (source 트리 무효화용). */
  sourceFolderId: string
  /** 이동 전 워크스페이스 scopeType. */
  sourceScopeType: 'department' | 'team'
  /** 이동 전 워크스페이스 scopeId. */
  sourceScopeId: string
  body: CrossWorkspaceMoveFileBody
  /** 이동 후 워크스페이스 scopeType. */
  destinationScopeType: 'department' | 'team'
  /** 이동 후 워크스페이스 scopeId. */
  destinationScopeId: string
}

/** POST /api/files/{id}/move { allowCrossScope: true } → { file } */
export function useCrossWorkspaceMoveFile() {
  const qc = useQueryClient()
  return useMutation<{ file: FileItem }, Error, CrossWorkspaceMoveFileVars>({
    mutationFn: ({ fileId, body }: CrossWorkspaceMoveFileVars) =>
      crossWorkspaceMoveFile(fileId, body),

    onSuccess: (_data, vars: CrossWorkspaceMoveFileVars) => {
      const {
        fileId,
        sourceFolderId,
        sourceScopeType,
        sourceScopeId,
        body,
        destinationScopeType,
        destinationScopeId,
      } = vars

      // folderChildren 전체 prefix 무효화 (트리에서 이 파일은 보이지 않지만
      // 부모 폴더 카운트/정렬이 반영되도록 보수 무효화)
      qc.invalidateQueries({ queryKey: [...qk.all, 'folders', 'children'] })

      // Source 폴더 children (정밀 무효화)
      qc.invalidateQueries({
        queryKey: qk.folderChildren(sourceScopeType, sourceScopeId, sourceFolderId),
      })

      // Destination 폴더 children (정밀 무효화)
      qc.invalidateQueries({
        queryKey: qk.folderChildren(
          destinationScopeType,
          destinationScopeId,
          body.targetFolderId,
        ),
      })

      // fileDetail: 새 parentId / scope 반영
      qc.invalidateQueries({ queryKey: qk.fileDetail(fileId) })

      // filesListPrefix: source + destination 폴더 파일 목록 갱신
      qc.invalidateQueries({ queryKey: qk.filesListPrefix(sourceFolderId) })
      qc.invalidateQueries({ queryKey: qk.filesListPrefix(body.targetFolderId) })

      // permissions: 이동 후 파일 permissions 변경
      qc.invalidateQueries({ queryKey: qk.permissions(fileId) })

      // shares: revokedShares 발생 → by-me / with-me 갱신
      qc.invalidateQueries({ queryKey: qk.shares() })

      // workspaces.me: 사이드바 갱신
      qc.invalidateQueries({ queryKey: qk.workspaces.me() })
    },
  })
}
