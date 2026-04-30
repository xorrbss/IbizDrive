'use client'
import { useEffect, useRef } from 'react'
import { toast } from 'sonner'
import { useShareUiStore } from '@/stores/shareUi'

/**
 * 공유 다이얼로그 (M8 docs/01 §14, 단일 파일).
 *
 * 백엔드 `POST /api/files/:id/share` 미구현 — 본 다이얼로그는 링크 placeholder를
 * `https://ibiz.example/share/{fileId}` 형식으로 표시 + 클립보드 복사만 제공.
 * 만료/권한 옵션은 v1.x — 백엔드 endpoint 신설 후 동일 store/Dialog에 추가.
 *
 * focus trap: RenameDialog 패턴 동일. Esc 닫기 + 닫힐 때 이전 focus 복귀.
 */
export function ShareDialog() {
  const isOpen = useShareUiStore((s) => s.isOpen)
  const fileId = useShareUiStore((s) => s.fileId)
  const fileName = useShareUiStore((s) => s.fileName)
  const close = useShareUiStore((s) => s.close)

  const closeBtnRef = useRef<HTMLButtonElement>(null)
  const previousFocusRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    if (!isOpen) return
    previousFocusRef.current = document.activeElement as HTMLElement | null
    queueMicrotask(() => closeBtnRef.current?.focus())
  }, [isOpen])

  useEffect(() => {
    if (isOpen) return
    previousFocusRef.current?.focus?.()
  }, [isOpen])

  if (!isOpen || !fileId) return null

  // mock 링크 — 백엔드 endpoint 신설 시 응답 URL로 교체
  const shareUrl = `https://ibiz.example/share/${fileId}`

  const handleCopy = async () => {
    try {
      // jsdom은 navigator.clipboard가 없을 수 있음 — try/catch 후 fallback
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(shareUrl)
      } else {
        // jsdom fallback — 실 브라우저에서는 도달 안 함
        throw new Error('clipboard unavailable')
      }
      toast.success('링크를 복사했습니다')
    } catch {
      toast.error('복사에 실패했습니다')
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="share-dialog-title"
      tabIndex={-1}
      onKeyDown={(e) => {
        if (e.key === 'Escape') close()
      }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
    >
      <div className="bg-surface-1 border border-border rounded-md w-[440px] flex flex-col p-4 gap-3 shadow-2xl">
        <h2 id="share-dialog-title" className="text-[14px] font-semibold text-fg">
          공유
        </h2>
        <p className="text-[12.5px] text-fg-muted truncate">
          <span className="text-fg">{fileName}</span>의 공유 링크
        </p>
        <div className="flex items-center gap-2">
          <input
            type="text"
            readOnly
            value={shareUrl}
            aria-label="공유 링크"
            className="flex-1 h-8 px-2 rounded border border-border bg-bg text-fg text-[12.5px] focus:outline-none focus:border-accent"
          />
          <button
            type="button"
            onClick={handleCopy}
            className="h-8 px-3 rounded bg-accent text-accent-fg text-[12.5px] font-medium hover:opacity-90"
          >
            복사
          </button>
        </div>
        <p className="text-[11.5px] text-fg-muted">
          만료/권한 옵션은 곧 제공됩니다 (백엔드 연동 후).
        </p>
        <div className="flex justify-end gap-2 mt-1">
          <button
            ref={closeBtnRef}
            type="button"
            onClick={close}
            className="h-8 px-3 rounded text-fg-2 text-[12.5px] hover:bg-surface-2"
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  )
}
