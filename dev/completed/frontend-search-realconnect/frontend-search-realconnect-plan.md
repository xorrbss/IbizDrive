---
Last Updated: 2026-05-01
Status: ✅ CLOSED — PR #20 squash-merge `f9200dc`, dev-docs archived
---

# F1 — Frontend Search 실연결 (mock → /api/search)

## 요약

M11(`PR #16` merged `f77f886`)에서 `api.searchFiles`는 MOCK_FILES 필터 mock으로 구현됐다.
A9(`PR #19` merged `73a8f01`)에서 backend `GET /api/search`가 신설됐다. 두 endpoint를
연결한다. 호출부(`useSearch`, `SearchBar`, `SearchResults`)는 무수정. `api.searchFiles` 본체만
fetch로 교체하고 `SearchPage` → `{ items: FileItem[] }` 매핑 helper를 추가한다.

## 현재 상태

- `frontend/src/hooks/useSearch.ts` — `api.searchFiles({ q, filters }, { signal })` 호출
- `frontend/src/lib/api.ts:415` — MOCK_FILES 기반 mock (200ms setTimeout + abort hookup)
- `frontend/src/components/topbar/SearchResults.tsx` — `FileItem[]` consume (file/folder 분기)
- backend `GET /api/search` — `q`(required, normalized minLen 2) `type` `cursor` `limit` 파라미터,
  `SearchPage { items: SearchResultDto[], nextCursor, totalEstimate }` 반환

## 목표 상태

- `api.searchFiles` 본체가 `fetch('/api/search?q=...', { credentials: 'include' })`로 호출
- `SearchResultDto` → `FileItem` 매핑: file의 `folderId`→`parentId`, folder의 `parentId`→`parentId`,
  `sizeBytes`→`size`, `mimeType` 그대로, `updatedAt` 그대로, `updatedBy`는 backend 미반환이므로 ''
- 401/403 → `Error & { status }` propagate (audit pattern 일관)
- `useSearch` / `SearchResults` 시그니처 무수정 — drift 0
- vitest GREEN (기존 useSearch.test/api.search.test 호환 유지 + 실연결 case 추가)

## phase 실행 지도

| Phase | Title | 산출물 |
|---|---|---|
| F1.0 | dev-docs bootstrap | plan/context/tasks 3 파일 |
| F1.1 | api.searchFiles 실연결 + 매핑 helper + test | api.ts 변경 + api.search.test.ts 갱신 |
| F1.2 | vitest GREEN + PR + closure | PR 생성 → CI green → master squash → archive + progress.md |

## acceptance criteria

- [ ] `api.searchFiles` 호출이 `fetch('/api/search?...')` 사용
- [ ] `SearchPage` → `{ items: FileItem[] }` 매핑 정확 (file/folder 둘 다)
- [ ] 401/403 → status 보존 Error (QueryCache.onError 일관)
- [ ] AbortSignal 전파 (fetch의 `signal` 옵션 사용)
- [ ] `useSearch` / `SearchBar` / `SearchResults` 시그니처/책임 무수정
- [ ] `pnpm typecheck && pnpm lint && pnpm test` GREEN

## 검증 게이트

- `pnpm test -- api.search` GREEN
- `pnpm test -- useSearch` GREEN
- 전체 `pnpm test` 회귀 0
- TypeScript 컴파일 OK

## 리스크와 완화

1. **`updatedBy` 누락** — backend SearchResultDto에 actor field 없음. mock UI에서는 `updatedBy`를 표시(아이콘 옆)하지만 검색 드롭다운에서는 secondary info. 빈 문자열로 매핑하고 SearchResults 표시는 기존 그대로 (빈 span).
   - 후속 트랙: backend에 `updatedByName` 필드 추가 시 매핑만 보강.
2. **filters 미사용** — 현재 useSearch는 `filters: {}` 빈 객체 전달. backend는 `type` 파라미터만 지원. F1에서는 filters 무시(향후 mime/owner 추가 시 controller param 신설).
3. **cursor 미사용** — F1는 첫 페이지만. infinite scroll은 후속 트랙(`SearchResults` UI 자체가 max-h-80 드롭다운). 시그니처는 `{ items }`로 유지.
4. **CORS / 세션** — audit 패턴 동일. `credentials: 'include'` + same-origin 가정.
