'use client'
import { useMutation } from '@tanstack/react-query'
import { previewFolderMove, previewFileMove } from '@/lib/api.move'
import type { MovePreviewRequest, MovePreviewResponse } from '@/lib/api.move'

/**
 * Plan D Task 24 — cross-workspace 이동 미리보기 hooks.
 *
 * 두 별개 hook으로 분리 (useMoveBulk 패턴 답습, kind 디스크리미네이터 불필요):
 *   - useMoveFolderPreview: folders 미리보기
 *   - useMoveFilePreview:   files 미리보기
 *
 * 원칙 #3 — 낙관적 업데이트 없음. preview는 읽기 전 확인 단계이므로 side effect 없음.
 */

// ─── Folder Preview ───────────────────────────────────────────────────────────

type FolderPreviewVars = {
  folderId: string
  body: MovePreviewRequest
}

/** POST /api/folders/{id}/move/preview → MovePreviewResponse */
export function useMoveFolderPreview() {
  return useMutation<MovePreviewResponse, Error, FolderPreviewVars>({
    mutationFn: ({ folderId, body }: FolderPreviewVars) =>
      previewFolderMove(folderId, body),
  })
}

// ─── File Preview ─────────────────────────────────────────────────────────────

type FilePreviewVars = {
  fileId: string
  body: MovePreviewRequest
}

/** POST /api/files/{id}/move/preview → MovePreviewResponse */
export function useMoveFilePreview() {
  return useMutation<MovePreviewResponse, Error, FilePreviewVars>({
    mutationFn: ({ fileId, body }: FilePreviewVars) =>
      previewFileMove(fileId, body),
  })
}
