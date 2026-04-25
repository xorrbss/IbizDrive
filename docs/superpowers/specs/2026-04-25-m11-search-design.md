# M11 Search — 설계

> 마일스톤: docs/01 §18 #11 / 견고성 명세: §10
> 진실 출처: URL `?q=...` (원칙 #1)
> 활용 인프라: M10 `app:focus-search` 이벤트 + M14 TopBar 검색 입력

---

## 1. 범위

| 항목 | 추가/수정 |
|---|---|
| `?q=` URL 파라미터 (search canonical) | 신규 |
| `hooks/useDebounce.ts` | 신규 |
| `hooks/useSearch.ts` | 신규 |
| `api.searchFiles({ q, filters }, { signal })` mock | 신규 |
| `qk.search(q, filters)` 쿼리 키 | 신규 |
| `components/files/SearchResults.tsx` | 신규 |
| TopBar — URL `?q=` ↔ input 양방향 동기화 | 수정 |
| ClientFilesPage — q 있을 때 SearchResults 렌더 | 수정 |

**범위 외:**
- 검색 필터(타입/소유자/날짜) — §10의 `Filters` 스키마 미정. v1.x.
- Recent searches / autocomplete — v1.x.
- 검색 결과에서의 DnD 이동 — folder context가 모호하므로 검색 모드에서는 비활성. 키보드/다이얼로그 경로(M7)는 유지.

---

## 2. 선행 결정

### 2.1 검색 모드 표시 방식 — **FileTable 대체 (inline)**
- 옵션:
  - (A) `?q=` 있으면 FileTable → SearchResults 교체 (folder URL은 그대로)
  - (B) 별도 `/search?q=` 라우트
  - (C) Dropdown/popover
- **(A) 채택**: 기존 selection / RightPanel / StatusBar / BulkActionBar 인프라 그대로 재사용. URL은 `/files/<folderId>?q=foo` 형태 — 폴더 컨텍스트 보존(검색 종료 시 즉시 복귀).
- Drive/Finder UX 패턴과 일치.

### 2.2 검색 범위 — **전역(모든 폴더)**
- v1.x에서 "현재 폴더 내" 토글 추가 검토.
- Mock에서는 MOCK_FILES 전체 스캔.

### 2.3 q 입력 ↔ URL 동기화 — **로컬 state 우선, debounced URL replace**
- 입력 즉시 표시는 컴포넌트 로컬 state.
- 300ms debounce → `router.replace(?q=...)`.
- URL 변화(navigation/back/forward) → useEffect로 로컬 state 재시드.
- 빈 문자열 또는 trim 후 < 2자 → URL `?q=` 제거 (folder view 복귀).

### 2.4 결과 정렬 — **이름 가나다 + 일치 위치 가산점**
- MVP: name.localeCompare. prefix match 우선 정렬은 v1.x.
- 검색 결과는 "이름 / 경로 / 수정일 / 수정자" 표시. 경로(부모 폴더 표시)는 SearchResults에서 새 컬럼.

### 2.5 폴더 클릭 시 동작 — **폴더로 이동 + 검색 종료**
- folder row click → `router.push(/files/<folderId>)` → 새 pathname이라 `?q=` 자연 소실.
- 파일 클릭 → `useOpenFile().open(id)` → `?file=` 추가, `?q=` 보존 (M6 useOpenFile은 다른 param 보존).

### 2.6 BulkActionBar / DnD — **다이얼로그 경로 유지, DnD는 비활성**
- selection store는 ids 기반 — 검색 결과에서 선택해도 모든 액션 동작 (서버는 어차피 id로 처리).
- DnD: 검색 결과에는 폴더 행도 섞여 있어 직관적 drop 타겟이 모호 → SearchResults는 dnd-kit `useDraggable` 호출 안 함 (FileRow 그대로 쓰면 DnD가 따라옴; SearchRow는 별도 컴포넌트로 만들거나 prop으로 disable).
- **결정**: 별도 `SearchRow` 컴포넌트 신설 (FileRow에서 DnD/droppable 제거한 슬림 버전). 가상화는 동일하게 적용.

### 2.7 빈 결과 / 로딩 / 에러 — **공통 상태 컴포넌트 재사용 + Empty variant**
- 로딩: 기존 `FileTableSkeleton` 재사용.
- 에러: 기존 `FileTableError` 재사용.
- 빈 결과: 신규 `SearchEmpty` ("'{q}'에 대한 결과가 없습니다").

---

## 3. 컴포넌트 / 훅 설계

### 3.1 `hooks/useDebounce.ts`
```ts
export function useDebounce<T>(value: T, ms: number): T
```
- generic, leading false / trailing true. 단위 테스트 3종.

### 3.2 `hooks/useSearch.ts`
```ts
export type SearchFilters = Record<string, never> // MVP — 빈 객체

export function useSearch(rawQuery: string, filters: SearchFilters = {}) {
  const debounced = useDebounce(rawQuery.trim(), 300)
  const normalized = normalizeForSearch(debounced)
  return useQuery({
    queryKey: qk.search(normalized, filters),
    queryFn: ({ signal }) => api.searchFiles({ q: normalized, filters }, { signal }),
    enabled: normalized.length >= 2,
    placeholderData: (prev) => prev,
    staleTime: 30_000,
  })
}
```

### 3.3 `lib/api.ts` — `searchFiles`
```ts
async searchFiles(
  { q, filters }: { q: string; filters: SearchFilters },
  opts?: { signal?: AbortSignal },
): Promise<FileItem[]> {
  // 200ms 시뮬 + AbortSignal 즉시 처리
  if (q.length < 2) return []
  const norm = normalizeForSearch(q)
  await new Promise<void>((resolve, reject) => {
    const t = setTimeout(resolve, 200)
    opts?.signal?.addEventListener('abort', () => {
      clearTimeout(t)
      reject(new DOMException('Aborted', 'AbortError'))
    }, { once: true })
  })
  return MOCK_FILES.filter((f) => normalizeForSearch(f.name).includes(norm))
                   .sort((a, b) => a.name.localeCompare(b.name, 'ko'))
}
```

### 3.4 `lib/queryKeys.ts`
```ts
search: (q: string, filters: SearchFilters) =>
  [...qk.all, 'search', q, filters] as const,
```

### 3.5 `components/files/SearchResults.tsx`
- props: `query: string` (URL `?q=`)
- `useSearch(query)` 구독
- 가상화 (TanStack Virtual, ROW_HEIGHT 36)
- 헤더: "이름 / 위치 / 수정일 / 수정자" (4열 grid). "위치"는 `parentId` → folder name 매핑(MOCK_TREE 검색)
- 상태 분기: pending → FileTableSkeleton, error → FileTableError, empty → SearchEmpty, has data → 가상화 행
- `aria-rowcount/rowindex` 유지 (원칙 #5)
- 키보드 네비: ↑↓ Enter Esc 동일 (FileTable 핸들러 재사용 — 단순화 위해 SearchResults 내부에 구현)

### 3.6 `components/files/SearchRow.tsx`
- FileRow의 슬림 버전 — DnD/droppable 제거
- "위치" 셀: parent folder 이름 + 클릭 시 폴더 이동 (linkable)
- 더블클릭 / Enter:
  - folder → router.push(`/files/<id>`)
  - file → useOpenFile().open(id)

### 3.7 `components/files/SearchEmpty.tsx`
- "'{q}'에 대한 결과가 없습니다." + 부가 안내

### 3.8 TopBar 수정
- 로컬 `query` state (즉시 표시) + URL `?q=` 양방향 동기화
- on mount: URL → local seed
- on URL change (`useSearchParams` deps): local !== url 일 때 local 갱신
- on local change (debounced 300ms): URL replace (q.length < 2 면 q 파라미터 삭제)
- clear 버튼: local 즉시 비움 + URL replace 즉시 (debounce 우회)

### 3.9 ClientFilesPage 수정
- `useSearchParams().get('q')` 읽음
- `q` 가 빈 문자열 또는 < 2자 → 기존 흐름
- 그 외 → Breadcrumb를 "검색 결과" 표시로 교체 (또는 Breadcrumb 그대로 + 그 아래 SearchHeader). FileTable → SearchResults 교체

---

## 4. 원칙 체크

| 원칙 | 확인 |
|---|---|
| #1 URL canonical | `?q=` URL 진실 출처. local state는 즉시 표시용 거울. ✅ |
| #2 RightPanel query param | `?q=` + `?file=` 공존 — useOpenFile은 다른 param 보존. ✅ |
| #3 낙관적 업데이트 비파괴적만 | 검색은 read-only. ✅ |
| #5 가상화 aria | SearchResults에 aria-rowcount/rowindex 유지. ✅ |
| #6 DB 제약 | 백엔드 normalized 컬럼 정합 필요. mock도 normalizeForSearch 사용. ✅ |
| #7 DnD 컨텍스트 분리 | 검색 모드에서 DnD 비활성 — SearchRow에 useDraggable 없음. ✅ |
| #11 정규화 일관 | `normalizeForSearch` 동일. ✅ |
| #12 에러 코드 | 신규 에러 없음. mock은 200/abort만. ✅ |

---

## 5. 테스트 전략

| 대상 | 테스트 |
|---|---|
| `useDebounce` | (a) 즉시 반영 안 함, (b) 300ms 후 반영, (c) 빠른 변경 시 마지막만 반영 |
| `api.searchFiles` | (a) <2자 → 빈 배열, (b) match by normalize, (c) 한글 NFC, (d) abort throws |
| `useSearch` | (a) <2자 disabled, (b) >=2자 enabled + 결과 반환, (c) placeholderData 유지 |
| `SearchResults` | (a) loading skeleton, (b) empty 메시지, (c) 결과 행 렌더 |
| TopBar URL sync | (a) URL → input seed, (b) input → URL replace (debounced), (c) clear → URL 즉시 제거 |

---

## 6. 구현 순서

1. `qk.search` 추가 + `SearchFilters` 타입
2. `lib/api.ts#searchFiles` mock 구현 + 단위 테스트
3. `hooks/useDebounce.ts` + 테스트
4. `hooks/useSearch.ts` + 테스트
5. `components/files/SearchEmpty.tsx`
6. `components/files/SearchRow.tsx`
7. `components/files/SearchResults.tsx` + 테스트
8. `TopBar` URL 동기화 리팩터링 + 테스트 갱신
9. `ClientFilesPage` 검색 모드 분기
10. 검증 (typecheck/lint/test)
11. docs/01 §18 마커 + progress.md

---

## 7. DoD

- typecheck / lint / test 통과 (157 → ~178)
- `/` 키 → TopBar 검색 input focus (M10+M14 결합 그대로)
- 입력 → 300ms 후 URL `?q=` 반영
- URL `/files/root?q=계약` 직접 접근 시 결과 표시
- < 2자 또는 비어있을 때 결과 영역 비활성, 폴더 view 복귀
- 한글 NFC + 영문 case-insensitive 일치
- 검색 결과에서 폴더 클릭 → 폴더로 이동 + `?q=` 사라짐
- 검색 결과에서 파일 클릭 → `?file=` + `?q=` 공존, RightPanel 표시
- BulkActionBar 동작 유지 (selection 기반 → 검색 결과에서도 vlid)
