'use client'
import { useState } from 'react'
import { TeamCreateDialog } from './TeamCreateDialog'

export function TeamCreateButton() {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className="w-full text-left px-2 py-1 text-[12px] text-fg-muted hover:bg-surface-2 hover:text-fg rounded"
      >
        + 새 팀 만들기
      </button>
      {open && <TeamCreateDialog onClose={() => setOpen(false)} />}
    </>
  )
}
