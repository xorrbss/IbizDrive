'use client'
import { useRemoveTeamMember } from '@/hooks/useRemoveTeamMember'
import { TEAM_OWNER_REQUIRED } from '@/lib/errors'
import type { TeamMember } from '@/types/team'

export function RemoveMemberDialog({
  teamId, member, onClose,
}: { teamId: string; member: TeamMember; onClose: () => void }) {
  const remove = useRemoveTeamMember(teamId)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    try {
      await remove.mutateAsync({ userId: member.userId })
      onClose()
    } catch {
      // remove.isError handles inline display
    }
  }

  const errCode = (remove.error as Error & { code?: string } | undefined)?.code
  const errMsg = errCode === TEAM_OWNER_REQUIRED
    ? '팀에 최소 한 명의 OWNER가 필요합니다'
    : remove.isError ? `제거 실패: ${String(remove.error)}` : null

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="멤버 제거"
      onKeyDown={(e) => { if (e.key === 'Escape') onClose() }}
      className="fixed inset-0 flex items-center justify-center bg-black/40 z-50"
    >
      <form
        onSubmit={submit}
        className="bg-surface-1 border border-border rounded p-4 w-[420px] flex flex-col gap-3"
      >
        <h2 className="text-[14px] font-semibold">멤버 제거</h2>
        <p className="text-[13px]">
          <strong>{member.displayName}</strong> 을(를) 팀에서 제거합니다.
        </p>
        {errMsg && (
          <p role="alert" className="text-[12px] text-danger">
            {errMsg}
          </p>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="px-3 py-1 text-[12px]">
            취소
          </button>
          <button
            type="submit"
            disabled={remove.isPending}
            className="px-3 py-1 bg-danger text-white text-[12px] rounded disabled:opacity-50"
          >
            {remove.isPending ? '제거 중...' : '제거'}
          </button>
        </div>
      </form>
    </div>
  )
}
