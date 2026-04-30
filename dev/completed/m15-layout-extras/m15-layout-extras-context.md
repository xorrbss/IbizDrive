---
Last Updated: 2026-04-29
---

# M15 — Context

## 관련 파일 (수정 대상)

| 파일 | 역할 | 변경 |
|---|---|---|
| `frontend/src/components/upload/FolderToolbar.tsx` | 폴더 액션 툴바 | SortChip + ViewSwitch 추가 |
| `frontend/src/hooks/useSortParams.ts` | 정렬 URL 읽기 | (변경 없음 — read-only) |
| `frontend/src/hooks/useViewParam.ts` | (신규) `?view=list\|grid` | 신규 |
| `frontend/src/components/files/SortChip.tsx` | (신규) 정렬 드롭다운 | 신규 |
| `frontend/src/components/files/ViewSwitch.tsx` | (신규) 뷰 토글 | 신규 |
| `frontend/src/components/storage/StorageBar.tsx` | (신규) 사이드바 하단 사용량 | 신규 디렉터리 |
| `frontend/src/hooks/useStorageQuota.ts` | (신규) quota fetch | 신규 |
| `frontend/src/lib/api.ts` | mock api | `getStorageQuota` 추가 |
| `frontend/src/lib/queryKeys.ts` | qk 팩토리 | `qk.storageQuota` 추가 |
| `frontend/src/app/(explorer)/layout.tsx` | 레이아웃 | StorageBar 마운트 (TrashLink 위) |
| `frontend/src/components/files/RightPanel.tsx` | 우측 패널 | 4-tab (세부정보/버전/활동/권한) |

## 관련 docs/01 섹션

- §1.1 진실 출처 규칙 — URL ↔ Zustand ↔ Query 분리 (SortChip/ViewSwitch는 URL)
- §4 컴포넌트 트리 — Sidebar / ContentArea / RightPanel 위치
- §17.5 useOpenFile — RightPanel 동작 패턴
- §18 row 15 — M15 정의

## 기존 패턴 재사용

- **URL search param 동기화**: `useSortParams` 읽기 + `router.replace(\`?\${new URLSearchParams(...)}\`)` 쓰기 (이미 다른 곳에서 사용 — useOpenFile 참고)
- **mock API + setTimeout latency**: `api.searchFiles`/`api.getEffectivePermissions` 패턴
- **TanStack Query + qk + staleTime**: `usePermission`/`useSearch` 패턴
- **focus trap dialog**: M15엔 신규 dialog 없음 (RightPanel은 dialog 아님)

## 테스트 계획 (Vitest)

| 컴포넌트 | 케이스 | 수 |
|---|---|---|
| SortChip | (1) 기본 name/asc 표시, (2) 옵션 변경 → router.replace 호출, (3) `aria-label` | 3 |
| ViewSwitch | (1) 기본 list, (2) Grid 클릭 → ?view=grid, (3) 토글 aria-pressed | 3 |
| useStorageQuota | (1) loading→data 천이, (2) 에러 시 null | 2 |
| StorageBar | (1) loading skeleton, (2) data 렌더 (used/total + %), (3) 99% 시 danger 색 | 3 |
| RightPanel | 기존 6 + 신규: (a) 4탭 렌더, (b) 탭 클릭 시 active 변경, (c) 세부정보 외 placeholder 표시 | +3 |

회귀 0 (StatusBar, BulkActionBar, FolderToolbar 등 영향 받는 곳).

## 위험 요소

1. **FolderToolbar 변경 → 기존 UploadButton 위치 보존** (좌측 첫 항목)
2. **`?view=grid` URL → ClientFilesPage가 무관심하게 통과 (M15.2 시점엔 FileTable이 view를 모르므로 무시)**: M16에서 FileTable이 읽음. M15.2엔 ViewSwitch UI/state만.
3. **RightPanel 탭 변경 — 기존 테스트 6개**: 탭 도입 후 기존 dl 셀렉터가 첫 탭 안에 있는지 확인. 호환성 유지하려면 dl을 첫 탭 default로.
4. **StorageBar mock 데이터 — react-query loading**: 처음 80ms 로딩 동안 skeleton, 이후 fixed mock 값.
