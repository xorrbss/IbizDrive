'use client'
import { useState } from 'react'
import { useFolderChildren } from '@/hooks/useFolderChildren'

interface Props {
  scopeType: 'department' | 'team'
  scopeId: string
  rootFolderId: string
  rootName: string
  /**
   * 선택 불가 폴더 ID 집합 — source folder + 이동되는 folder들.
   * backend가 self/descendant 거부하므로 보수적 체크.
   */
  invalidIds: string[]
  selectedTargetId: string | null
  onSelect: (id: string) => void
}

export function MoveFolderTree(props: Props) {
  return (
    <MoveFolderRadioNode
      {...props}
      folderId={props.rootFolderId}
      name={props.rootName}
      depth={0}
    />
  )
}

interface NodeProps extends Props {
  folderId: string
  name: string
  depth: number
}

function MoveFolderRadioNode({
  scopeType,
  scopeId,
  folderId,
  name,
  depth,
  invalidIds,
  selectedTargetId,
  onSelect,
  rootFolderId,
  rootName,
}: NodeProps) {
  const [expanded, setExpanded] = useState(depth === 0) // root expanded by default
  const children = useFolderChildren(scopeType, scopeId, folderId, { enabled: expanded })
  const invalid = invalidIds.includes(folderId)
  const inputId = `move-target-${folderId}`

  return (
    <div>
      <label
        htmlFor={inputId}
        className={`flex items-center gap-1.5 px-2 py-1 rounded transition-colors ${
          invalid ? 'opacity-50 cursor-not-allowed' : 'hover:bg-surface-2 cursor-pointer'
        }`}
        style={{ paddingLeft: depth * 12 + 8 }}
        title={
          invalid ? '자기 자신 또는 이동 중인 폴더로는 이동할 수 없습니다' : undefined
        }
      >
        <button
          type="button"
          onClick={(e) => {
            e.preventDefault()
            setExpanded(!expanded)
          }}
          aria-label={expanded ? '접기' : '펼치기'}
          aria-expanded={expanded}
          className="w-3.5 inline-flex items-center justify-center text-fg-muted text-[10px]"
        >
          {expanded ? '▾' : '▸'}
        </button>
        <input
          type="radio"
          id={inputId}
          name="move-target"
          value={folderId}
          disabled={invalid}
          aria-disabled={invalid || undefined}
          checked={selectedTargetId === folderId}
          onChange={() => onSelect(folderId)}
        />
        <span>{name}</span>
      </label>
      {expanded &&
        children.data?.map((c) => (
          <MoveFolderRadioNode
            key={c.id}
            scopeType={scopeType}
            scopeId={scopeId}
            rootFolderId={rootFolderId}
            rootName={rootName}
            invalidIds={invalidIds}
            selectedTargetId={selectedTargetId}
            onSelect={onSelect}
            folderId={c.id}
            name={c.name}
            depth={depth + 1}
          />
        ))}
      {expanded && children.isLoading && (
        <div
          className="px-2 py-0.5 text-[11px] text-fg-muted"
          style={{ paddingLeft: (depth + 1) * 12 + 8 }}
        >
          로딩…
        </div>
      )}
    </div>
  )
}
