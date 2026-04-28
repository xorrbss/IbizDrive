import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { AuthApiError, AuthRole } from '@/types/auth'

function statusOf(err: unknown): number | undefined {
  return (err as { status?: number })?.status
}

export function useAuth() {
  const query = useQuery({
    queryKey: qk.authMe(),
    queryFn: api.getMe,
    staleTime: 30_000,
    retry: (count, err: unknown) => {
      const status = statusOf(err)
      return status !== 401 && status !== 403 && count < 2
    },
  })

  const roles = query.data?.roles ?? []
  const error = query.error as AuthApiError | null

  return {
    ...query,
    session: query.data ?? null,
    user: query.data?.user ?? null,
    roles,
    error,
    isAuthenticated: query.isSuccess && !!query.data,
    isUnauthenticated: query.isError && statusOf(query.error) === 401,
    hasRole: (role: AuthRole) => roles.includes(role),
  }
}
