'use client'
import { useEffect, useRef } from 'react'
import { useOpenFile } from './useOpenFile'

/**
 * 폴더가 바뀌면 RightPanel(`?file=`)을 자동으로 닫는다.
 *
 * - 이유: 파일은 특정 폴더 컨텍스트에 속함. 폴더가 바뀌면 패널은 의미 상실
 * - 대칭성: M3의 "folderId 변경 시 focus/selection reset"과 동일 정책
 * - 초기 마운트는 건너뜀 (딥링크 `/files/foo?file=xxx` 보존)
 */
export function useCloseFileOnFolderChange(folderId: string | null | undefined) {
  const { fileId, close } = useOpenFile()
  const prevRef = useRef<string | null>(null)

  useEffect(() => {
    if (!folderId) return
    const prev = prevRef.current
    if (prev !== null && prev !== folderId && fileId) {
      close()
    }
    prevRef.current = folderId
  }, [folderId, fileId, close])
}
