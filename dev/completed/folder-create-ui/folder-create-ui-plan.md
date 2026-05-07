---
Last Updated: 2026-05-07
---

# folder-create-ui — 폴더 생성 UI 트랙 (frontend-only)

## 요약

Backend `POST /api/folders`와 frontend `api.createFolder()`는 이미 존재하나 호출 진입점이 없어 사용자가 폴더를 만들 수 없는 상태. 본 트랙은 **현재 폴더 안에 새 폴더를 만드는 핵심 흐름 1개**를 추가한다 — `FolderToolbar`에 "새 폴더" 버튼 + `CreateFolderDialog` + `useCreateFolder` mutation hook + 캐시 무효화 헬퍼. KISS — 컨텍스트 메뉴 / FolderTree 인라인 추가 / 키보드 단축키는 본 트랙 out (v1.x).

## 현재 상태 분석

### 이미 갖춰진 것
- **Backend**: `POST /api/folders` (FolderController:144) + `FolderMutationService.create(...)` + `CreateFolderRequest` DTO + `FolderNameConflictException` (409 `RENAME_CONFLICT` envelope) + `@PreAuthorize` (parentId null → ADMIN, 그 외 → 부모 EDIT — `docs/03 §3`).
- **Frontend api 래퍼**: `api.createFolder(parentId, name): Promise<{id, name, parentId}>` (`lib/api.ts:435`). `parentId === 'root'` → backend로 `null` 변환. trim 처리.
- **Query keys**: `qk.folderTree()`, `qk.folder(id)`, `qk.filesListPrefix(folderId)` 모두 존재 (`lib/queryKeys.ts`). `getFilesInFolder`가 폴더+파일 통합 listing 반환 — `filesListPrefix` 무효화로 자식 목록 재조회.
- **Invalidation 헬퍼**: `invalidations.afterRename`, `afterFilesMoved`, `afterDelete`, `afterRestore` 패턴 정착 (`lib/queryKeys.ts:182~`). 새 헬퍼 `afterFolderCreated`는 동일 패턴으로 추가.
- **정규화**: `lib/normalize.ts`의 `normalizeFileName` (NFC + trim) + `normalizedNameForDedup` + 에러 코드 (`ERR_EMPTY` / `ERR_TOO_LONG` / `ERR_FORBIDDEN_CHAR` / `ERR_RESERVED` / `ERR_TRAILING_DOT` / `ERR_NUL_CHAR`). 클라이언트 사전 검증에 그대로 재사용.
- **Toolbar 위치**: `components/upload/FolderToolbar.tsx` — 현재 `<UploadButton primary>` 하나. "새 폴더" 버튼은 옆 자리.
- **Mutation hook 패턴**: `useRestoreItem.ts` — `useMutation` + `onSuccess`에서 `invalidations.afterX(qc, opts)` 호출. 그대로 답습.

### 부재한 것 (본 트랙 scope)
- `useCreateFolder` mutation hook (`hooks/useCreateFolder.ts`).
- `CreateFolderDialog` (`components/explorer/CreateFolderDialog.tsx`) — 이름 입력 + 클라이언트 validation + 409 인라인 surface.
- `FolderToolbar`에 "새 폴더" 버튼 wiring (`components/upload/FolderToolbar.tsx`).
- `invalidations.afterFolderCreated(qc, { parentId })` 헬퍼.
- 테스트: `useCreateFolder.test.tsx`, `CreateFolderDialog.test.tsx`.

## 목표 상태

### 범위 안 (MVP)
1. **진입점**: `FolderToolbar`에 "새 폴더" 버튼 1개 (UploadButton 옆, secondary 스타일).
2. **현재 폴더(parentId)** 안에 새 폴더 생성. parentId는 `useCurrentFolder().folderId` (URL 소유 — `docs/01 §19` 원칙 1).
3. **다이얼로그**: 제어 컴포넌트. 이름 입력 + 빈 입력 / 길이 초과 / 금지문자 / 예약어 / 끝 점 클라이언트 validation (즉시 표기). 제출 시 trim + NFC.
4. **충돌 처리**: backend 409 `RENAME_CONFLICT` → 다이얼로그 안 인라인 에러 ("같은 이름의 폴더가 이미 있습니다"). 닫지 않음, 사용자가 이름 수정 후 재시도 가능.
5. **권한 에러**: backend 403 → 인라인 에러 ("폴더를 만들 권한이 없습니다"). 가상 root에서 비-ADMIN인 경우 동일 처리. **frontend gating 없음** (KISS — backend 권위).
6. **무효화**: `invalidations.afterFolderCreated(qc, { parentId })` — `qk.filesListPrefix(parentId)` + `qk.folderTree()` + `qk.folder(parentId)` 3개.
7. **성공 후**: 다이얼로그 닫기, 입력 초기화. (선택 / 네비게이션은 본 트랙 out — 단순 새로고침만.)

### 범위 밖 (deferred)
- FolderTree 인라인 "+ 새 폴더" / 컨텍스트 메뉴.
- FileTable 빈 영역 우클릭.
- 키보드 단축키 (Ctrl+Shift+N).
- 생성 직후 새 폴더로 자동 네비게이션 / 자동 선택 / rename 인라인 모드.
- Bulk 생성 / 템플릿 폴더.

### Phase 지도

| Phase | 목표 | 산출물 | 의존 |
|---|---|---|---|
| **P0** | bootstrap | dev-docs 3파일 (코드 미변경 commit) | — |
| **P1** | invalidation 헬퍼 + hook | `invalidations.afterFolderCreated` + `useCreateFolder` + RED→GREEN 테스트 (성공 / 409 surface / 캐시 무효화 호출) | P0 |
| **P2** | dialog | `CreateFolderDialog` + RED→GREEN 테스트 (validation / 제출 / 인라인 에러 / 닫힘) | P1 |
| **P3** | toolbar wiring | `FolderToolbar`에 "새 폴더" 버튼 + 다이얼로그 mount + 통합 smoke 테스트 | P2 |
| **P4** | docs 동기화 | `docs/01 §6.2` 무효화 매트릭스 + `docs/progress.md` 항목 추가 | P3 |

### Acceptance Criteria

- [ ] `pnpm typecheck` + `pnpm lint` + `pnpm test` 모두 green
- [ ] `useCreateFolder` 성공 시 `qk.filesListPrefix(parentId)` / `qk.folderTree()` / `qk.folder(parentId)` 3개가 invalidate 됨 (테스트로 검증)
- [ ] `CreateFolderDialog`가 빈 입력 / 길이 / 금지문자 / 예약어 / 끝 점 5종 validation을 인라인으로 노출
- [ ] 409 `RENAME_CONFLICT` 응답 시 다이얼로그가 닫히지 않고 인라인 에러 노출
- [ ] 403 응답 시 동일 패턴으로 권한 에러 노출
- [ ] `FolderToolbar`에 "새 폴더" 버튼 노출, 클릭 시 다이얼로그 open
- [ ] §3 핵심 원칙 11개 위반 없음 (특히 1: URL folderId, 3: 파괴적 액션 낙관적 업데이트 금지 — 본 트랙은 신규 추가이므로 낙관적 업데이트 안 씀)

### 검증 게이트
- TDD: 모든 신규 파일에 RED→GREEN 사이클
- `frontend/src/lib/api.ts:435`의 `createFolder` 시그니처는 변경 없음 (신규 콜러만 추가)
- `lib/queryKeys.ts`에 `afterFolderCreated` 추가 시 기존 helper 패턴 1:1 답습 (Promise.all + invalidateQueries)

## 리스크와 완화

| 리스크 | 완화 |
|---|---|
| 가상 root(`'root'` literal)에서 생성 시 권한/무효화 키 충돌 | `api.createFolder`가 이미 `parentId === 'root'` → `null` 변환. 무효화 시 `qk.filesListPrefix('root')` 호출은 `getFilesInFolder('root')` 캐시(가상 root 트리 합성)도 함께 무효화 — 정합. `qk.folder('root')`는 캐시에 없을 수 있으나 invalidate 호출은 무해. |
| 클라이언트 정규화와 backend 정규화 불일치 (NFC vs NFKC, 정책 차이) | `normalizeFileName` + `normalizedNameForDedup`는 backend `NormalizeUtil`와 fixture 공유 (ADR #16, `docs/02 §3`). 본 트랙은 함수 그대로 호출만. 신규 정규화 규칙 추가 안 함. |
| 이중 클릭 / 빠른 재클릭으로 중복 생성 요청 | mutation 진행 중 제출 버튼 disable (`mutation.isPending`). |
| 다이얼로그가 escape/backdrop 클릭으로 닫혀 입력 손실 | 본 트랙은 "닫으면 초기화" 정책. 손실 방지는 v1.x. |
