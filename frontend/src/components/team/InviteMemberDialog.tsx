'use client'
import { useId, useState } from 'react'
import { UserSearchCombobox } from '@/components/shares/UserSearchCombobox'
import { useInviteTeamMember } from '@/hooks/useInviteTeamMember'
import type { UserSummary } from '@/types/user'

export function InviteMemberDialog({
  teamId, onClose,
}: { teamId: string; onClose: () => void }) {
  const [selected, setSelected] = useState<UserSummary | null>(null)
  const invite = useInviteTeamMember(teamId)
  const inputId = useId()

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!selected) return
    try {
      await invite.mutateAsync({ userId: selected.id })
      onClose()
    } catch {
      // invite.isError handles inline display
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="멤버 초대"
      onKeyDown={(e) => { if (e.key === 'Escape') onClose() }}
      className="fixed inset-0 flex items-center justify-center bg-black/40 z-50"
    >
      <form
        onSubmit={submit}
        className="bg-surface-1 border border-border rounded p-4 w-[420px] flex flex-col gap-3"
      >
        <h2 className="text-[14px] font-semibold">멤버 초대</h2>
        <div className="flex flex-col gap-1 text-[12px]">
          <label htmlFor={inputId}>사용자 검색 *</label>
          <UserSearchCombobox
            value={selected}
            onChange={setSelected}
            inputId={inputId}
          />
        </div>
        {invite.isError && (
          <p role="alert" className="text-[12px] text-danger">
            초대 실패: {String(invite.error)}
          </p>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="px-3 py-1 text-[12px]">
            취소
          </button>
          <button
            type="submit"
            disabled={!selected || invite.isPending}
            className="px-3 py-1 bg-accent text-white text-[12px] rounded disabled:opacity-50"
          >
            {invite.isPending ? '초대 중...' : '초대'}
          </button>
        </div>
      </form>
    </div>
  )
}
