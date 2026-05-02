/**
 * 인증 도메인 타입 — backend `LoginResponse` (docs/02 §7.4) 1:1 mirror.
 *
 * `kind`는 ADR #21에 따라 A1 범위에서 항상 'human'. service 계정(A4)은 별도 트랙.
 * `effectivePermissionsCacheKey`는 ADR #26 SHA-256 hex prefix 16자.
 */
export interface UserInfo {
  id: string
  email: string
  name: string
  kind: 'human' | 'service'
  mustChangePassword: boolean
}

export interface DepartmentInfo {
  id: string
  name: string
  path: string
}

/**
 * `/api/auth/login`, `/api/auth/me`, `/api/auth/signup` 응답의 공용 shape.
 * 401(미인증) 시 `useMe`는 null 반환.
 */
export interface AuthSession {
  user: UserInfo
  departments: DepartmentInfo[]
  roles: string[]
  effectivePermissionsCacheKey: string
}

export interface LoginParams {
  email: string
  password: string
}

export interface SignupParams {
  email: string
  password: string
  displayName: string
}
