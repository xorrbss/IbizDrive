'use client'
import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { useCreateTeam } from '@/hooks/useCreateTeam'
import { buildWorkspacePath } from '@/lib/workspacePath'

export function TeamCreateDialog({ onClose }: { onClose: () => void }) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const router = useRouter()
  const create = useCreateTeam()

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!name.trim()) return
    try {
      const team = await create.mutateAsync({
        name: name.trim(),
        description: description.trim() || undefined,
      })
      onClose()
      router.push(buildWorkspacePath({ kind: 'team', workspaceId: team.id }, team.rootFolderId, []))
    } catch {
      // create.isError handles inline display
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="새 팀 만들기"
      onKeyDown={(e) => { if (e.key === 'Escape') onClose() }}
      className="fixed inset-0 flex items-center justify-center bg-black/40 z-50"
    >
      <form
        onSubmit={submit}
        className="bg-surface-1 border border-border rounded p-4 w-[420px] flex flex-col gap-3"
      >
        <h2 className="text-[14px] font-semibold">새 팀 만들기</h2>
        <label className="flex flex-col gap-1 text-[12px]">
          이름 *
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            maxLength={100}
            className="border rounded px-2 py-1 text-[13px]"
          />
        </label>
        <label className="flex flex-col gap-1 text-[12px]">
          설명 (선택)
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={1000}
            className="border rounded px-2 py-1 text-[13px]"
          />
        </label>
        {create.isError && (
          <p role="alert" className="text-[12px] text-danger">
            생성 실패: {String(create.error)}
          </p>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="px-3 py-1 text-[12px]">
            취소
          </button>
          <button
            type="submit"
            disabled={create.isPending}
            className="px-3 py-1 bg-accent text-white text-[12px] rounded disabled:opacity-50"
          >
            {create.isPending ? '생성 중...' : '만들기'}
          </button>
        </div>
      </form>
    </div>
  )
}
