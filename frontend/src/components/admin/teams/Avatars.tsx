/**
 * Shared avatar primitives — admin-teams (T8 design-refresh-admin Phase 4).
 *
 * <p>디자인 admin-teams.jsx PAvatar / PAvatarStack 1:1 매핑. 디자인 원본은
 * `userById(uid)`의 `{name, color, initials}`에 의존하나, 프론트엔드 컴포넌트는
 * displayName + 결정론적 색상 + 1글자 이니셜을 자체 derive (backend가
 * 사용자 색상을 직렬화하지 않으므로).
 *
 * <p>색상 팔레트는 admin.css `--accent` 이외의 oklch 8색 — admin-teams.css의
 * TEAM_COLORS와 비슷한 톤이지만 user-id-hash 기반.
 */

const AVATAR_PALETTE = [
  '#5B7FCC',
  '#C16A8B',
  '#5BA08A',
  '#C9925A',
  '#7C6BB5',
  '#A56FB8',
  '#5C9B9B',
  '#B9824D',
] as const

/**
 * userId(UUID 또는 임의 문자열) → 8색 팔레트 인덱스. 같은 입력에 항상 같은 색상 반환.
 */
function colorForUser(userId: string): string {
  let hash = 0
  for (let i = 0; i < userId.length; i++) {
    hash = (hash * 31 + userId.charCodeAt(i)) | 0
  }
  const idx = Math.abs(hash) % AVATAR_PALETTE.length
  return AVATAR_PALETTE[idx]
}

function initialOf(name: string | null | undefined): string {
  if (!name) return '?'
  const trimmed = name.trim()
  if (trimmed.length === 0) return '?'
  // first grapheme — Korean/English 단일 문자 처리.
  return Array.from(trimmed)[0]
}

export interface PAvatarProps {
  userId: string
  name?: string | null
  size?: number
}

/**
 * 작은 원형 아바타 — userId가 색상 결정, name이 이니셜 결정.
 */
export function PAvatar({ userId, name, size = 24 }: PAvatarProps) {
  const color = colorForUser(userId)
  return (
    <span
      className="p-avatar"
      style={{
        background: color,
        width: size,
        height: size,
        fontSize: Math.round(size * 0.42),
      }}
      title={name ?? userId}
      aria-label={name ?? userId}
    >
      {initialOf(name)}
    </span>
  )
}

export interface PAvatarStackProps {
  /** users [{userId, name}]. 디자인 원본의 userIds + userById 분리 → 본 컴포넌트는 합친 형태로 받음. */
  users: { userId: string; name?: string | null }[]
  max?: number
  size?: number
}

export function PAvatarStack({ users, max = 5, size = 24 }: PAvatarStackProps) {
  const visible = users.slice(0, max)
  const rest = users.length - visible.length
  return (
    <span className="p-avatar-stack">
      {visible.map((u) => (
        <PAvatar key={u.userId} userId={u.userId} name={u.name} size={size} />
      ))}
      {rest > 0 && (
        <span
          className="p-avatar-more"
          style={{ width: size, height: size, fontSize: Math.round(size * 0.36) }}
        >
          +{rest}
        </span>
      )}
    </span>
  )
}

export interface RolePillProps {
  /** 'owner' | 'manager' | 'editor' | 'commenter' | 'viewer' — admin.css의 role-pill 변형들. */
  role: 'owner' | 'manager' | 'editor' | 'commenter' | 'viewer'
  label?: string
}

const ROLE_LABEL: Record<RolePillProps['role'], string> = {
  owner: '소유자',
  manager: '매니저',
  editor: '편집자',
  commenter: '댓글',
  viewer: '뷰어',
}

export function RolePill({ role, label }: RolePillProps) {
  return <span className={`role-pill role-${role}`}>{label ?? ROLE_LABEL[role]}</span>
}
