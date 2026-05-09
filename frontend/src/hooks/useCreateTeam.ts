'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'
import type { TeamCreateRequest } from '@/types/team'

export function useCreateTeam() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: TeamCreateRequest) => api.createTeam(req),
    onSuccess: () => invalidations.afterTeamChanged(qc),
  })
}
