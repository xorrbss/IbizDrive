---
Last Updated: 2026-05-01
Status: ✅ CLOSED — PR #20 squash-merge `f9200dc`, dev-docs archived
---

# F1 — Frontend Search 실연결 — Tasks

## phase 상태

| Phase | Title | Status |
|---|---|---|
| F1.0 | dev-docs bootstrap | ✅ done |
| F1.1 | api.searchFiles 실연결 + 매핑 + test | ✅ done (`6475952`) |
| F1.2 | vitest GREEN + PR + closure | ✅ done (PR #20 → `f9200dc`) |

---

## F1.1 — api.searchFiles 실연결

### 작업 항목

- [ ] `api.search.test.ts` — fetch mock으로 갱신 (200 응답 + 매핑 검증, 401/403 status, abort)
- [ ] `api.ts` `searchFiles` 본체 교체 — `fetch('/api/search?q=...&type=...&cursor=...&limit=...', { credentials: 'include' })`
- [ ] `SearchPage` → `{ items: FileItem[] }` 매핑 inline helper
- [ ] file: `folderId`→`parentId`, `sizeBytes`→`size`, `mimeType` 그대로
- [ ] folder: `parentId`→`parentId`, `mimeType: null`, `size: null`
- [ ] 공통: `updatedBy: ''`, `deletedAt: undefined`, `originalParentId: undefined`
- [ ] 401/403/5xx → `Error & { status }` throw (audit 패턴 일관)
- [ ] AbortSignal → `fetch(..., { signal })`
- [ ] `pnpm test -- api.search` GREEN
- [ ] `pnpm test -- useSearch` GREEN (회귀 없음 검증)
- [ ] `pnpm test` 전체 회귀 0
- [ ] commit `feat(F1.1): api.searchFiles real /api/search connection + DTO mapping`

### 작업 전 필독

- `frontend/src/lib/api.ts` lines 415~447 (현 searchFiles mock)
- `frontend/src/lib/api.ts` lines 476~530 (getAuditLogs — fetch + Error{status} 패턴)
- `frontend/src/lib/api.search.test.ts` (기존 test — mock filter 검증)
- `frontend/src/hooks/useSearch.ts` (호출 시그니처 invariant)
- `frontend/src/types/file.ts` (FileItem 매핑 target)
- `dev/completed/a9-search-endpoint/a9-search-endpoint-plan.md` §7.8 (SearchPage / SearchResultDto schema)

### 원본 코드 참조

- `getAuditLogs` (api.ts:476) — `fetch + credentials: 'include' + Accept: 'application/json' + status throw`
- backend `SearchController.search` — `@RequestParam q, type, cursor, limit` mapping
- backend `SearchResultDto` — file: type/id/name/folderId/sizeBytes/mimeType/updatedAt, folder: type/id/name/parentId/updatedAt

### 구현 대상

```ts
// api.ts (개념)
async searchFiles(
  params: { q: string; filters: Record<string, unknown> },
  options: { signal?: AbortSignal } = {},
): Promise<{ items: FileItem[] }> {
  const { signal } = options
  const q = params.q
  if (!q || q.length < 2) return { items: [] }

  const qs = new URLSearchParams({ q })
  // filters 미지원 (backend 미구현). 향후 type/mime/owner 등 추가 시 여기서 매핑.

  const res = await fetch(`/api/search?${qs.toString()}`, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
    signal,
  })
  if (!res.ok) {
    const err = new Error(`search fetch failed: ${res.status}`) as Error & { status: number }
    err.status = res.status
    throw err
  }
  const page = (await res.json()) as SearchPage
  return { items: page.items.map(toFileItem) }
}

function toFileItem(dto: SearchResultDto): FileItem {
  if (dto.type === 'folder') {
    return {
      id: dto.id,
      name: dto.name,
      type: 'folder',
      mimeType: null,
      size: null,
      updatedAt: dto.updatedAt,
      updatedBy: '',
      parentId: dto.parentId ?? '',
    }
  }
  return {
    id: dto.id,
    name: dto.name,
    type: 'file',
    mimeType: dto.mimeType ?? null,
    size: dto.sizeBytes ?? null,
    updatedAt: dto.updatedAt,
    updatedBy: '',
    parentId: dto.folderId,
  }
}
```

### 검증 참조

- `api.search.test.ts` 케이스:
  - 1자 → 빈 결과 (호출 안 함)
  - 200 응답 file → mapped FileItem (folderId→parentId)
  - 200 응답 folder → mapped FileItem (parentId→parentId, size/mimeType null)
  - 200 응답 mixed → 순서 보존
  - 401 → Error{status:401}
  - 5xx → Error{status:500}
  - abort → DOMException AbortError
- `useSearch.test.tsx` 무수정 회귀 GREEN

### 문서 반영

- 변경 없음 (계약은 docs/02 §7.8 + docs/01 §10 그대로)

---

## F1.2 — Closure

### 작업 항목

- [ ] `pnpm typecheck && pnpm lint && pnpm test` 전체 GREEN
- [ ] PR 생성 (squash merge 대기) — 사용자 승인 게이트
- [ ] CI green (frontend vitest + backend junit)
- [ ] master squash-merge
- [ ] dev-docs archive `dev/active/frontend-search-realconnect/` → `dev/completed/frontend-search-realconnect/`
- [ ] `docs/progress.md` 최상단 F1 closure entry 추가
- [ ] commit `chore(F1): closure — F1 마일스톤 종료 + dev-docs archive`

### 작업 전 필독

- `dev/completed/a9-search-endpoint/a9-search-endpoint-tasks.md` A9.4 (closure 패턴)
- `docs/progress.md` 라인 1~30 (A9 closure entry 헤더 mirror)

### 검증 참조

- merge commit + CI history + archive diff

### 문서 반영

- `docs/progress.md` F1 closure entry (newest-first 최상단)
