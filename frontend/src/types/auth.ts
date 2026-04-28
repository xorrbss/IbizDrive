export type AuthRole = 'MEMBER' | 'AUDITOR' | 'ADMIN'
export type AuthUserKind = 'human' | 'service'

export interface AuthUser {
  id: string
  email: string
  name: string
  kind: AuthUserKind
  mustChangePassword: boolean
}

export interface AuthDepartment {
  id: string
  name: string
  path?: string
}

export interface AuthSession {
  user: AuthUser
  departments: AuthDepartment[]
  roles: AuthRole[]
  effectivePermissionsCacheKey: string
}

export interface LoginCredentials {
  email: string
  password: string
}

export type AuthApiError = Error & {
  status: number
  code?: string
  reason?: string
  retryAfterSec?: number
}
