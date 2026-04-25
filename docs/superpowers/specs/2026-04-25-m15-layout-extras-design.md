# M15 Layout Extras — 설계 (2026-04-25)

## 1. 범위

design-reference의 보조 UI 4종을 M14 Visual Identity 위에 추가:

1. **SortChip** — FolderToolbar 우측. URL `?sort=&dir=` 양방향 동기화 dropdown
2. **ViewSwitch** — FolderToolbar 우측. `?view=list|grid` (M16 사전 인프라; M15는 토글 UI + URL만)
3. **StorageBar** — 사이드바 하단 영구 마운트. 사용량 % + 진척바
4. **RightPanel 탭** — 4 탭 (세부정보 / 버전 / 활동 / 권한). 세부정보 외 3개는 placeholder

## 2. 컴포넌트별 상세

### 2.1 SortChip

**위치**: `FolderToolbar` 오른쪽 (현재 정렬은 헤더 클릭으로만 가능 — 명시적 UI 추가)

**상태**: URL `?sort={name|updatedAt|size}&dir={asc|desc}` (기존 `useSortParams` 그대로)

**UI**:
```
[ 정렬 ▾ 이름 ] [↑]
```
- `<select>` 네이티브 사용 (이름/수정일/크기). 키보드 ↑↓ 자동 처리, ARIA 무료
- 방향 버튼 — 아이콘 ↑/↓ 토글 (Lucide ArrowUp/ArrowDown)

**훅**: 새 `useSetSortParams(sort, dir)` — URL replace로 다른 params 보존

**테스트** (3):
- mount 시 URL `?sort=updatedAt&dir=desc` → select/dir 표시
- select 변경 → router.replace에 `?sort=...` 포함
- 방향 토글 → asc ↔ desc

### 2.2 ViewSwitch

**위치**: FolderToolbar 우측 (SortChip 옆)

**상태**: URL `?view=list|grid` (default `list`)

**UI**: segmented (List/Grid 아이콘 토글, Lucide List/LayoutGrid)

**M15 범위**: 토글 UI + URL 동기화만. Grid 모드 본체는 M16
- FileTable/SearchResults는 `view='grid'`여도 현재는 list 그대로 렌더 (M16에서 분기)
- `useViewParam` 훅: `'list' | 'grid'` 반환, default `'list'`

**테스트** (3):
- default `?view=` 없으면 List active
- Grid 클릭 → `?view=grid`로 router.replace
- aria-pressed 양쪽 적절히

### 2.3 StorageBar

**위치**: 사이드바 하단 (`mt-auto`로 push down). `(explorer)/layout.tsx`에 마운트

**데이터**: 새 `api.getStorageQuota()` mock — `{ usedBytes: number, totalBytes: number }`. `useStorageQuota()` 훅 (TanStack Query, staleTime 5분)

**UI**:
```
사용량                65%
[████████░░░░]
65 GB / 100 GB
[용량 업그레이드]
```
- 진척바 4px high (`bg-surface-3` track, `bg-accent` fill)
- 90% 넘으면 fill 색을 `bg-warn`, 100% 시 `bg-danger`

**queryKey**: `['storage', 'quota']` (root level — `qk.all` 아래 구조 추가는 작아서 optional)

**테스트** (3):
- 65% 표시
- 95% → warn 색
- 로딩 시 placeholder

### 2.4 RightPanel 탭

**위치**: RightPanel 헤더 아래 탭 바

**탭**: 세부정보 / 버전 / 활동 / 권한

**상태**: local state `useState<TabKey>('details')`. URL 동기화 안 함 — 패널을 닫았다 열면 details로 리셋되는 게 자연
- `?file=` 진실 출처는 유지. tab은 일시적 view state

**UI**: 가로 탭 + role=tablist/tab/tabpanel + ↑↓/Home/End 키보드 (W3C ARIA APG)

**구현**:
- 세부정보: 현재 `PanelBody` 그대로
- 버전 / 활동 / 권한: placeholder ("준비 중") + 각각 M5/M11/M8 의존 안내

**테스트** (3):
- mount 시 details 탭 active
- 탭 클릭 → tabpanel 변경
- ←→ 키 → 탭 이동

## 3. 새 파일

```
src/components/files/
  SortChip.tsx + test
  ViewSwitch.tsx + test
src/components/layout/
  StorageBar.tsx + test
src/components/files/RightPanelTabs.tsx (RightPanel 내부 분리)
src/hooks/
  useSetSortParams.ts
  useViewParam.ts + test
  useStorageQuota.ts
src/lib/api.ts (getStorageQuota 메서드 추가)
```

## 4. 수정 파일

- `src/components/upload/FolderToolbar.tsx` — 우측에 SortChip + ViewSwitch 추가
- `src/components/files/RightPanel.tsx` — 헤더 아래 탭 바 + body 분기
- `src/app/(explorer)/layout.tsx` — 사이드바 하단에 `<StorageBar />`
- `src/lib/queryKeys.ts` — `qk.storage` 추가

## 5. 11개 핵심 원칙 충돌 검토

- **#1 URL canonical**: sort/dir/view 모두 URL에. file_tab은 ephemeral local state — RightPanel 닫고 열면 default
- 나머지 위반 없음

## 6. 검증

- typecheck / lint / test 통과
- 기존 177 tests + 신규 ~12 = ~189 tests
- docs/01 §18 M15 행 완료 마커
- progress.md 세션 기록

## 7. 비범위 (M16/v1.x로 이연)

- Grid View 본체 (M16)
- StorageBar 업그레이드 버튼 동작 (관리자 페이지 이연)
- RightPanel 버전/활동/권한 탭 본체 (해당 마일스톤 의존)
