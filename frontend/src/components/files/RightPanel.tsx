'use client'
import { useEffect } from 'react'
import { useOpenFile } from '@/hooks/useOpenFile'
import { useFileDetail } from '@/hooks/useFileDetail'
import type { FileItem } from '@/types/file'

/**
 * RightPanel: ?file=<id> 에 대응하는 파일 상세 패널.
 *
 * - URL query param이 진실 출처 (docs/01 §2.3)
 * - Esc 전역 리스너로 닫기 (§12.1)
 * - Parallel route 대신 query param 사용 (§19 원칙 2)
 *
 * 설계: docs/01 §11 (로딩/에러/빈 상태), §17.5 (useOpenFile)
 */
export function RightPanel() {
  const { fileId, close } = useOpenFile()
  const { data, isLoading, error } = useFileDetail(fileId)

  // Esc 전역 핸들러 — 포커스 위치와 무관하게 패널 닫기
  useEffect(() => {
    if (!fileId) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.stopPropagation()
        close()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [fileId, close])

  if (!fileId) return null

  return (
    <aside
      role="complementary"
      aria-label="파일 상세"
      className="w-80 border-l bg-white flex flex-col shrink-0"
    >
      <header className="flex items-center justify-between px-4 h-12 border-b">
        <h2 className="text-sm font-semibold text-gray-900 truncate">
          {isLoading ? '로딩…' : (data?.name ?? '파일')}
        </h2>
        <button
          type="button"
          onClick={close}
          aria-label="패널 닫기"
          className="text-gray-500 hover:text-gray-900 text-lg leading-none"
        >
          ×
        </button>
      </header>

      <div className="p-4 text-sm flex-1 overflow-y-auto">
        {isLoading && <PanelSkeleton />}
        {!isLoading && error && <PanelError />}
        {!isLoading && !error && data && <PanelBody file={data} />}
      </div>
    </aside>
  )
}

function PanelSkeleton() {
  return (
    <div className="space-y-2 animate-pulse" aria-hidden>
      <div className="h-4 bg-gray-200 rounded w-3/4" />
      <div className="h-4 bg-gray-200 rounded w-1/2" />
      <div className="h-4 bg-gray-200 rounded w-2/3" />
    </div>
  )
}

function PanelError() {
  return (
    <div role="alert" className="text-sm text-red-600">
      파일 정보를 불러오지 못했습니다.
    </div>
  )
}

function PanelBody({ file }: { file: FileItem }) {
  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-2 text-sm">
      <dt className="text-gray-500">이름</dt>
      <dd className="text-gray-900 break-all">{file.name}</dd>

      <dt className="text-gray-500">유형</dt>
      <dd className="text-gray-900">{file.type === 'folder' ? '폴더' : (file.mimeType ?? '파일')}</dd>

      <dt className="text-gray-500">크기</dt>
      <dd className="text-gray-900">{formatSize(file.size)}</dd>

      <dt className="text-gray-500">수정일</dt>
      <dd className="text-gray-900">{formatDate(file.updatedAt)}</dd>

      <dt className="text-gray-500">수정자</dt>
      <dd className="text-gray-900">{file.updatedBy}</dd>
    </dl>
  )
}

function formatSize(bytes: number | null): string {
  if (bytes == null) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
