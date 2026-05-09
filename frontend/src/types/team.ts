/**
 * Team domain types — Plan B Task 25.
 * backend Team.Visibility enum 1:1 (Java enum.name() 직렬화).
 */

export type TeamVisibility = 'PRIVATE' | 'INTERNAL'

export interface TeamCreateRequest {
  name: string
  description?: string
  visibility?: TeamVisibility
}

export interface TeamResponse {
  id: string
  name: string
  description: string | null
  visibility: TeamVisibility
  rootFolderId: string
  createdAt: string
  archivedAt: string | null
}
