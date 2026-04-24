'use client'
import { useState } from 'react'
import { useUploadStore, type UploadTask } from '@/stores/upload'
import { useUpload } from '@/hooks/useUpload'

export function UploadQueueDock() {
  const queue = useUploadStore((s) => s.queue)
  const clearDone = useUploadStore((s) => s.clearDone)
  const [collapsed, setCollapsed] = useState(false)

  if (queue.length === 0) return null

  const doneCount = queue.filter((t) => t.status === 'done').length

  return (
    <aside
      role="region"
      aria-label="업로드 큐"
      className="fixed bottom-4 right-4 w-[360px] max-h-[60vh] z-40 bg-surface-1 border border-border rounded-lg shadow-lg flex flex-col overflow-hidden"
    >
      <header className="flex items-center justify-between px-3.5 py-2 border-b border-border">
        <span className="text-[12.5px] font-semibold text-fg">
          업로드 {doneCount} / {queue.length}
        </span>
        <button
          type="button"
          onClick={() => setCollapsed((c) => !c)}
          className="w-6 h-6 inline-flex items-center justify-center rounded text-fg-muted hover:bg-surface-2 hover:text-fg"
          aria-label={collapsed ? '펼치기' : '접기'}
        >
          {collapsed ? '▲' : '▼'}
        </button>
      </header>
      {!collapsed && (
        <>
          <ul className="flex-1 overflow-y-auto divide-y divide-border">
            {queue.map((t) => (
              <TaskRow key={t.id} task={t} />
            ))}
          </ul>
          <footer className="px-3.5 py-2 border-t border-border">
            <button
              type="button"
              onClick={clearDone}
              className="text-[12px] text-fg-muted hover:text-fg"
            >
              완료 항목 모두 지우기
            </button>
          </footer>
        </>
      )}
    </aside>
  )
}

function TaskRow({ task }: { task: UploadTask }) {
  const { cancel, retry } = useUpload()

  return (
    <li className="px-3.5 py-2">
      <div className="flex items-center justify-between gap-2">
        <span className="flex-1 truncate text-[12.5px] text-fg">{task.file.name}</span>
        <StatusBadge task={task} />
      </div>
      <div className="mt-1 h-1 bg-surface-2 rounded overflow-hidden">
        <div
          className={`h-full ${task.status === 'failed' ? 'bg-danger' : 'bg-accent'} transition-[width]`}
          style={{ width: `${Math.round(task.progress * 100)}%` }}
        />
      </div>
      <div className="mt-1 flex items-center justify-between">
        {task.status === 'failed' && task.error ? (
          <span className="text-[11.5px] text-danger">{task.error.message}</span>
        ) : (
          <span aria-hidden />
        )}
        <div className="flex gap-1">
          {task.status === 'uploading' && (
            <button
              type="button"
              onClick={() => cancel(task.id)}
              className="text-[11.5px] text-fg-muted hover:text-fg"
            >
              취소
            </button>
          )}
          {task.status === 'failed' && (
            <button
              type="button"
              onClick={() => retry(task.id)}
              className="text-[11.5px] text-accent hover:text-accent-hover"
            >
              재시도
            </button>
          )}
        </div>
      </div>
    </li>
  )
}

function StatusBadge({ task }: { task: UploadTask }) {
  const map: Record<UploadTask['status'], { label: string; className: string }> = {
    queued:    { label: '대기',      className: 'text-fg-muted' },
    uploading: { label: '업로드 중', className: 'text-accent' },
    done:      { label: '완료',      className: 'text-success' },
    failed:    { label: '실패',      className: 'text-danger' },
    conflict:  { label: '충돌',      className: 'text-warn' },
  }
  const { label, className } = map[task.status]
  return <span className={`text-[11.5px] font-medium ${className}`}>{label}</span>
}
