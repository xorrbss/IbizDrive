'use client'
import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { useShareUiStore } from '@/stores/shareUi'
import { useCreateShare } from '@/hooks/useCreateShare'
import { useRevokeShare } from '@/hooks/useRevokeShare'
import { useSharesByMe } from '@/hooks/useSharesByMe'
import type { SharePreset } from '@/types/share'

/**
 * 공유 다이얼로그 (F4 → F5.2, docs/01 §14, docs/02 §7.9, ADR #34).
 *
 * F5.2 변경 (F5.1 wire 정합 위에서):
 * - useCreateShare가 `{target, req}` discriminated를 받으므로 folder kind도 직접 mutate.
 *   target.kind === 'folder' → POST /api/folders/{id}/share, file → POST /api/files/{id}/share.
 * - 위치 이동: `components/files/` → `components/shares/` (file 전용 컴포넌트가 아니므로 소유 경계 정합).
 * - 라벨 kind-aware: 부제 "(파일|폴더) <name>" + NOT_FOUND toast 한국어 분기.
 *
 * F5.1에서 굳어진 정책:
 * - 기존 share 매칭은 `(s.fileId ?? s.folderId) === target.id` (wire XOR).
 * - 기존 share 행 표시는 만료 시각 + 해제 버튼만 (subject/preset wire에 없음, A13 backlog 시 복원).
 *
 * MVP 정책 (유지):
 * - subject = 'everyone' 고정 — frontend user/department/role 목록 endpoint 부재 (별도 트랙).
 * - preset 4값 (read|upload|edit|admin) — `Preset.SHARE`는 V5 CHECK 미지원 (ADR #34 backlog).
 * - expiresAt: HTML5 datetime-local 입력 → `new Date(value).toISOString()` 변환.
 * - message: optional, 1000자 제한은 backend가 검증 (400 BAD_REQUEST).
 *
 * 에러 envelope: api.create{File,Folder}Shares가 status/code surface → toast.error 분기.
 *   400 BAD_REQUEST / 403 PERMISSION_DENIED / 404 NOT_FOUND / 409 PERMISSION_CONFLICT
 *
 * focus trap: RenameDialog 패턴 동일. Esc 닫기 + 닫힐 때 이전 focus 복귀.
 */
export function ShareDialog() {
  const isOpen = useShareUiStore((s) => s.isOpen)
  const target = useShareUiStore((s) => s.target)
  const close = useShareUiStore((s) => s.close)

  const closeBtnRef = useRef<HTMLButtonElement>(null)
  const previousFocusRef = useRef<HTMLElement | null>(null)

  const [preset, setPreset] = useState<SharePreset>('read')
  const [expiresAtLocal, setExpiresAtLocal] = useState('')
  const [message, setMessage] = useState('')

  const createShare = useCreateShare()
  const revokeShare = useRevokeShare()
  const sharesQuery = useSharesByMe()

  useEffect(() => {
    if (!isOpen) return
    previousFocusRef.current = document.activeElement as HTMLElement | null
    queueMicrotask(() => closeBtnRef.current?.focus())
    // 다이얼로그 재오픈 시 폼 초기화
    setPreset('read')
    setExpiresAtLocal('')
    setMessage('')
  }, [isOpen])

  useEffect(() => {
    if (isOpen) return
    previousFocusRef.current?.focus?.()
  }, [isOpen])

  if (!isOpen || !target) return null

  // file 공유 row면 fileId, folder 공유 row면 folderId가 채워져 있다 (XOR). target.id와 매칭.
  const existingShares =
    sharesQuery.data?.pages
      .flatMap((p) => p.items)
      .filter((s) => (s.fileId ?? s.folderId) === target.id) ?? []

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    let expiresAt: string | undefined
    if (expiresAtLocal) {
      const d = new Date(expiresAtLocal)
      if (Number.isNaN(d.getTime())) {
        toast.error('만료 시각이 올바르지 않습니다')
        return
      }
      expiresAt = d.toISOString()
    }
    createShare.mutate(
      {
        target,
        req: {
          subjects: [{ type: 'everyone' }],
          preset,
          ...(expiresAt ? { expiresAt } : {}),
          ...(message.trim() ? { message: message.trim() } : {}),
        },
      },
      {
        onSuccess: () => {
          toast.success('공유했습니다')
          close()
        },
        onError: (err) => {
          const code = (err as Error & { code?: string }).code
          const subject = target.kind === 'folder' ? '폴더' : '파일'
          if (code === 'PERMISSION_CONFLICT') {
            toast.error('이미 같은 대상에게 공유되어 있습니다')
          } else if (code === 'PERMISSION_DENIED') {
            toast.error('공유 권한이 없습니다')
          } else if (code === 'NOT_FOUND') {
            toast.error(`${subject}을(를) 찾을 수 없습니다`)
          } else {
            toast.error('공유에 실패했습니다')
          }
        },
      },
    )
  }

  const handleRevoke = (shareId: string) => {
    revokeShare.mutate(shareId, {
      onSuccess: () => {
        toast.success('공유를 해제했습니다')
      },
      onError: (err) => {
        const code = (err as Error & { code?: string }).code
        if (code === 'PERMISSION_DENIED') {
          toast.error('공유 해제 권한이 없습니다')
        } else {
          toast.error('공유 해제에 실패했습니다')
        }
      },
    })
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
      <form
        onSubmit={handleSubmit}
        className="bg-surface-1 border border-border rounded-md w-[480px] flex flex-col p-4 gap-3 shadow-2xl"
      >
        <h2 id="share-dialog-title" className="text-[14px] font-semibold text-fg">
          공유
        </h2>
        <p className="text-[12.5px] text-fg-muted truncate">
          <span aria-hidden className="mr-1">{target.kind === 'folder' ? '📁' : '📄'}</span>
          <span className="text-fg">{target.name}</span> {target.kind === 'folder' ? '폴더' : '파일'} 공유 설정
        </p>

        <fieldset className="flex flex-col gap-1.5">
          <legend className="text-[11.5px] uppercase tracking-[0.04em] text-fg-muted">대상</legend>
          <p className="text-[12.5px] text-fg">
            모든 사용자 <span className="text-fg-muted">(everyone)</span>
          </p>
        </fieldset>

        <fieldset className="flex flex-col gap-1.5">
          <legend className="text-[11.5px] uppercase tracking-[0.04em] text-fg-muted">권한</legend>
          <div className="flex gap-3 text-[12.5px]" role="radiogroup" aria-label="공유 권한">
            {(['read', 'upload', 'edit', 'admin'] as const).map((p) => (
              <label key={p} className="flex items-center gap-1.5">
                <input
                  type="radio"
                  name="preset"
                  value={p}
                  checked={preset === p}
                  onChange={() => setPreset(p)}
                />
                {presetLabel(p)}
              </label>
            ))}
          </div>
        </fieldset>

        <label className="flex flex-col gap-1.5 text-[12.5px]">
          <span className="text-[11.5px] uppercase tracking-[0.04em] text-fg-muted">만료 (선택)</span>
          <input
            type="datetime-local"
            value={expiresAtLocal}
            onChange={(e) => setExpiresAtLocal(e.target.value)}
            className="h-8 px-2 rounded border border-border bg-bg text-fg focus:outline-none focus:border-accent"
          />
        </label>

        <label className="flex flex-col gap-1.5 text-[12.5px]">
          <span className="text-[11.5px] uppercase tracking-[0.04em] text-fg-muted">메시지 (선택)</span>
          <textarea
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            maxLength={1000}
            rows={2}
            className="px-2 py-1 rounded border border-border bg-bg text-fg focus:outline-none focus:border-accent resize-none"
          />
        </label>

        {existingShares.length > 0 && (
          <div className="flex flex-col gap-1.5 border-t border-border pt-3">
            <span className="text-[11.5px] uppercase tracking-[0.04em] text-fg-muted">
              기존 공유 ({existingShares.length})
            </span>
            <ul className="flex flex-col gap-1 max-h-[120px] overflow-auto">
              {existingShares.map((s) => (
                <li key={s.id} className="flex items-center justify-between text-[12.5px]">
                  <span className="text-fg-muted truncate">
                    {s.expiresAt ? `만료 ${formatExpires(s.expiresAt)}` : '무기한'}
                  </span>
                  <button
                    type="button"
                    onClick={() => handleRevoke(s.id)}
                    disabled={revokeShare.isPending}
                    className="px-2 h-6 rounded text-fg-muted text-[11.5px] hover:bg-surface-2 disabled:opacity-50"
                  >
                    해제
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="flex justify-end gap-2 mt-1">
          <button
            ref={closeBtnRef}
            type="button"
            onClick={close}
            className="h-8 px-3 rounded text-fg-2 text-[12.5px] hover:bg-surface-2"
          >
            닫기
          </button>
          <button
            type="submit"
            disabled={createShare.isPending}
            className="h-8 px-3 rounded bg-accent text-accent-fg text-[12.5px] font-medium hover:opacity-90 disabled:opacity-50"
          >
            {createShare.isPending ? '공유 중…' : '공유'}
          </button>
        </div>
      </form>
    </div>
  )
}

function presetLabel(p: SharePreset): string {
  switch (p) {
    case 'read':
      return '읽기'
    case 'upload':
      return '업로드'
    case 'edit':
      return '편집'
    case 'admin':
      return '관리'
  }
}

/** ISO 8601 → "YYYY-MM-DD HH:mm" (locale-agnostic, UTC 기준 표시). */
function formatExpires(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
