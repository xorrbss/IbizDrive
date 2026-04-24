# M4 설계안 — 선택 모델 & BulkActionBar

> **작성일**: 2026-04-25
> **마일스톤**: M4 (선택 모델 + BulkActionBar)
> **참조**: `docs/01-frontend-design.md` §5.1, §8, §12.1, §14.2
> **선행**: M1(라우팅), M3(FileTable 가상화 + focus)

---

## 0. 결정 요약 (브레인스토밍 결과)

| # | 쟁점 | 선택 | 대안 | 이유 |
|---|---|---|---|---|
| 1 | BulkActionBar 액션 범위 | **B**: 휴지통만 실제 mock mutation, 다운로드/이동은 스텁 | A(전부 스텁) / C(전부 mutation) | 원칙 #3 낙관적 업데이트 정책을 한 번 제대로 검증 (pendingIds 라이프사이클 + §6.2 무효화 매트릭스). 이동은 M6 DnD와 겹치고 다운로드는 별도 논의 필요. |
| 2 | `usePermission` 훅 | **B**: 스텁 훅 (모든 권한 true, §14.2 시그니처 준수) | A(훅 제거) / C(실제 훅) | BulkActionBar 코드가 §8.2 최종형으로 유지. M7에서 훅 내부만 교체. docs/03 §3 권한 매트릭스 미확정 리스크 회피. |
| 3 | 체크박스 컬럼 | **A**: 체크박스 없이 행 클릭 기반 | B(체크박스+행) / C(hover 체크박스) | §8 스펙 그대로, M3 컬럼 레이아웃 그대로. BulkActionBar 등장 자체가 다중선택 발견성 단서. |
| 4 | pending 시각·인터랙션 | **A1+B1+C1**: opacity 0.5 + 스피너 / aria-disabled + 클릭·키보드 스킵 + pending↔selected 상호배제 / 실패 시 롤백 코드만 M4, 실패 경로 검증은 M5 | A2/A3, B2/B3, C2 | skeleton은 로딩과 혼동. 차단 없이 시각만 두면 중복 요청 위험. 상호배제를 store가 강제해야 컴포넌트 실수 방지. |
| 5 | 테스트 범위 + Space 바인딩 | **B+B1**: Vitest 단위 + RTL 통합 6시나리오 + `useDeleteBulk` 훅 단위 / Space는 FileTable handleKeyDown에 통합 (preventDefault + focusedIndex 가드) | A(최소)/C(+Playwright), B2(FileRow 내부 핸들러) | Playwright는 M3 리듬 유지(수동 브라우저). RTL 6개면 회귀 방어 충분. Space를 FileRow에 두면 위임 패턴 깨짐. |

---

## 1. 범위

### 포함
- `frontend/src/stores/selection.ts` — §5.1 스펙 + 추가 규칙 (§2.1)
- 마우스 선택: 클릭(selectOnly) / Ctrl·Meta+click(toggle) / Shift+click(selectRange)
- 키보드: **Space**(toggle), Ctrl·Meta+A(selectAll), Esc(clear)
- `frontend/src/hooks/usePermission.ts` — 스텁 (모든 권한 true)
- `frontend/src/components/files/BulkActionBar.tsx` — §8.2 기반
- `frontend/src/hooks/useDeleteBulk.ts` — 실제 mock mutation
- `FileRow` 선택/pending 시각 상태 연결
- `FileTable`에 `aria-multiselectable="true"` 복원
- `deleteBulk(ids)` mock API 추가

### 비포함 (명시적 연기)
- **Shift+↑↓ 범위 확장, Ctrl+↑↓ 포커스만 이동** → M10
- F2(rename), Delete key, `/` 검색 포커스 → M10
- 실제 `usePermission` API 연결 → **M7 권한**
- 이동 실제 mutation → **M6 DnD**
- 다운로드 실제 구현 → 별도 논의
- 토스트 라이브러리 통합 → M4에서는 `console.warn` + TODO 주석으로 자리만 확보

---

## 2. 상태 모델

### 2.1 Selection store (§5.1 + 추가 규칙)

§5.1의 코드 shape는 그대로 유지. 다음 추가 규칙을 store 내부에서 강제:

- **`markPending(ids)` 호출 시 해당 id들을 `ids`(selected)에서 제거** — pending↔selected 상호 배제. 컴포넌트가 잊어도 store가 보장.
- `selectRange` 폴백 3케이스 (§2.2)
- 범위 내 pending은 선택에서 제외

### 2.2 Shift+click 앵커 폴백

```ts
selectRange: (to, orderedIds) => set((s) => {
  const { lastClickedId, ids, pendingIds } = s

  const anchorMissing = !lastClickedId
  const anchorPending = lastClickedId ? pendingIds.has(lastClickedId) : false
  const anchorNotInFolder = lastClickedId
    ? !orderedIds.includes(lastClickedId)
    : false

  if (anchorMissing || anchorPending || anchorNotInFolder) {
    return { ids: new Set([to]), lastClickedId: to }
  }

  const a = orderedIds.indexOf(lastClickedId)
  const b = orderedIds.indexOf(to)
  const [start, end] = a < b ? [a, b] : [b, a]
  const next = new Set(ids)
  orderedIds.slice(start, end + 1)
    .filter((id) => !pendingIds.has(id))
    .forEach((id) => next.add(id))
  return { ids: next }
})
```

### 2.3 Focus vs Selection 관계

| 동작 | focusedIndex | selection | lastClickedId |
|---|---|---|---|
| 행 클릭 | 해당 행 | `selectOnly` | 해당 id |
| Ctrl+click | 해당 행 | `toggle` | 해당 id |
| Shift+click | 해당 행 | `selectRange` | 유지 |
| ↑↓ | 이동 | 변경 없음 | 변경 없음 |
| Space | 변경 없음 | `toggle(focused.id)` | focused.id |
| Ctrl+A | 변경 없음 | `selectAll(nonPendingVisible)` | 유지 |
| Esc | -1 | `clear()` | null |

### 2.4 BulkActionBar 렌더링

`count === 0` → `return null`. §8.2 원본 그대로.

### 2.5 `useDeleteBulk` 훅 (폴더 이동 엣지 포함)

```ts
export function useDeleteBulk() {
  const qc = useQueryClient()
  const { markPending, unmarkPending, clear, selectAll } = useSelectionStore()
  const { folderId: currentFolderId } = useCurrentFolder()

  return useMutation({
    mutationFn: ({ ids }: { ids: string[]; folderIdAtStart: string }) =>
      api.deleteBulk(ids),

    onMutate: ({ ids }) => { markPending(ids) },

    onSuccess: async (_, { ids, folderIdAtStart }) => {
      await qc.invalidateQueries({ queryKey: qk.filesInFolder(folderIdAtStart) })
      unmarkPending(ids)
      clear()
    },

    onError: (_, { ids, folderIdAtStart }) => {
      unmarkPending(ids)
      if (folderIdAtStart === currentFolderId) {
        selectAll(ids)  // 같은 폴더 — selection 복원
      }
      // 다른 폴더로 이동 → 복원 스킵 (ghost 방지)
      // TODO(M5): 토스트 에러
    },
  })
}
```

### 2.6 Ctrl+A — 전체 pending no-op

```ts
case 'a':
case 'A': {
  if (e.ctrlKey || e.metaKey) {
    e.preventDefault()
    const selectable = items
      .filter(item => !pendingIds.has(item.id))
      .map(item => item.id)
    if (selectable.length === 0) return
    selectAll(selectable)
  }
  break
}
```

### 2.7 폴더 변경 시 reset

```ts
useEffect(() => {
  setFocusedIndex(-1)
  clear()  // clear()가 lastClickedId: null 포함 → 앵커도 리셋
  // pendingIds는 유지 — mutation은 폴더와 무관하게 완료되어야 함
}, [folderId])
```

### 2.8 markPending 시 focus 보정

```ts
useEffect(() => {
  if (focusedIndex < 0 || !items) return
  const focusedId = items[focusedIndex]?.id
  if (!focusedId || !pendingIds.has(focusedId)) return

  const findNonPending = (start: number, step: 1 | -1) => {
    for (let i = start; i >= 0 && i < items.length; i += step) {
      if (!pendingIds.has(items[i].id)) return i
    }
    return -1
  }

  const downIdx = findNonPending(focusedIndex + 1, 1)
  const next = downIdx !== -1 ? downIdx : findNonPending(focusedIndex - 1, -1)
  setFocusedIndex(next)
}, [pendingIds, focusedIndex, items])
```

ArrowDown/Up 키 핸들러에도 pending 스킵 추가 — 일관성.

---

## 3. 상호작용 플로우

### 3.1 6가지 핵심 시퀀스

**① 단일 클릭**
```
FileRow onClick (no modifier)
  → selectOnly(id)
  → setFocusedIndex(idx)
```

**② Ctrl/Meta+click**
```
FileRow onClick (e.ctrlKey || e.metaKey)
  → toggle(id)
  → setFocusedIndex(idx)
```

**③ Shift+click**
```
FileRow onClick (e.shiftKey)
  → selectRange(id, orderedIds)  // 폴백 §2.2
  → setFocusedIndex(idx)
```

**④ Space (포커스된 행 토글)**
```
handleKeyDown (key === ' ', focusedIndex >= 0)
  → e.preventDefault()
  → 가드: pendingIds.has(focusedId) → return
  → toggle(focusedId)
```

**⑤ Ctrl+A**
```
handleKeyDown (key === 'a' && (ctrl||meta))
  → e.preventDefault()
  → selectable = items.filter(!pendingIds.has).map(id)
  → selectable.length === 0 → no-op
  → selectAll(selectable)
```

**⑥ 휴지통 버튼**
```
BulkActionBar.onClick(휴지통)
  → deleteBulkMutation.mutate({ ids, folderIdAtStart })
  → onMutate: markPending(ids)  [store가 selection에서 자동 제거]
  → UI: opacity 0.5 + 스피너 + aria-disabled
  → focus 보정 effect 트리거
  → mock API delay (~500ms)
  → onSuccess:
      invalidateQueries(qk.filesInFolder(folderIdAtStart))
      unmarkPending(ids)
      clear()
```

**Esc**: 기존 M3 Esc 플로우 + `clear()` 추가.

### 3.2 FileRow 이벤트 시그니처 변경

MouseEvent 전체 전달이 필요 (shift/ctrl/meta 판별):

```ts
// Before
onClick?: (id: string) => void

// After
onClick?: (item: FileItem, e: React.MouseEvent) => void
```

FileTable에서 modifier 분기, FileRow는 "얇은 프리젠터" 유지.

---

## 4. 계약 파일

### 신규

| 파일 | 역할 | 스펙 근거 |
|---|---|---|
| `frontend/src/stores/selection.ts` | selection slice | docs/01 §5.1 + 본 문서 §2.1, §2.2 |
| `frontend/src/hooks/usePermission.ts` | 스텁 훅 (M7에서 실제 교체) | docs/01 §14.2 |
| `frontend/src/hooks/useDeleteBulk.ts` | 삭제 mutation + pending 생명주기 | 본 문서 §2.5 |
| `frontend/src/components/files/BulkActionBar.tsx` | 선택 액션 툴바 | docs/01 §8.2 |

### 수정

| 파일 | 변경 내용 |
|---|---|
| `frontend/src/components/files/FileRow.tsx` | `aria-selected` 실제 연결, pending 시 opacity+스피너+aria-disabled, onClick 시그니처 변경(MouseEvent 전달) |
| `frontend/src/components/files/FileTable.tsx` | `aria-multiselectable="true"` 복원, 키보드 핸들러 확장(Space/Ctrl+A/Esc clear + pending skip), markPending focus 보정 useEffect, 폴더 변경 시 `clear()` 추가 |
| `frontend/src/lib/api.ts` | `deleteBulk(ids: string[])` mock 추가 — MOCK_FILES에서 id 제거 + 500ms delay |

### docs 업데이트
- `docs/01-frontend-design.md` §5.1 하단에 **구현 노트** 한 줄 추가: "pending 상호 배제, Shift+click 앵커 폴백 3케이스는 `docs/superpowers/specs/2026-04-25-m4-selection-bulkactionbar-design.md` §2.1, §2.2 참조"
- `docs/progress.md` — 세션 완료 시 기록

---

## 5. 접근성 & 시각 정책

### 5.1 ARIA
- grid: `aria-multiselectable="true"` 복원
- 선택된 행: `aria-selected="true"`
- pending 행: `aria-disabled="true"` (aria-selected는 false로 강제 — 상호 배제)
- BulkActionBar: `role="toolbar"` + `aria-label="선택 항목 액션"`

### 5.2 시각 톤
| 상태 | 스타일 |
|---|---|
| hover | `hover:bg-gray-50` |
| focused | `bg-blue-50 outline outline-2 outline-blue-400` |
| selected | `bg-blue-100` |
| selected + focused | `bg-blue-100 outline outline-2 outline-blue-400` |
| pending | `opacity-50` + 우측 스피너 (selected/focused 스타일 위에 덮어씀) |

### 5.3 우선순위 (동시 적용)
`pending > selected > focused > hover`

### 5.4 스크린리더
- BulkActionBar의 "N개 선택" 텍스트는 자체적으로 `aria-live="polite"` 적용 → 선택 개수 변경 시 낭독

---

## 6. 리스크 & 대응

| ID | 리스크 | 대응 |
|---|---|---|
| R1 | Zustand Set 참조 미갱신 | 모든 액션이 `new Set(...)` 새 참조 반환 — 기존 §5.1 패턴 유지 |
| R2 | Ctrl+A가 브라우저 텍스트 전체 선택과 충돌 | grid 포커스일 때만 `preventDefault()`, 외부 포커스는 기본 동작 |
| R3 | Space 페이지 스크롤 | `preventDefault()` + `focusedIndex >= 0` 가드 |
| R4 | Shift+click 네이티브 텍스트 선택 | FileRow `select-none` 이미 적용 ✔ |
| R5 | virtualizer가 선택된 행 언마운트 | `aria-selected`는 store 기반 → 재마운트 시 자연스러운 복원 ✔ |
| R6 | deleteBulk 중 폴더 이동 | 훅이 `folderIdAtStart` 캡처, onError에서 비교 후 복원 여부 결정 |
| R7 | markPending 후 focus가 사라진 행에 남음 | useEffect에서 non-pending 최근접 행으로 자동 보정 |

---

## 7. 검증 기준 (DoD)

### 7.1 자동 테스트
- `selection.test.ts` — store 단위: toggle/selectOnly/selectRange(폴백 3케이스 포함)/clear/selectAll/markPending(selection 제거 부작용)/unmarkPending
- `useDeleteBulk.test.ts` — 성공 순서 / 실패+같은폴더+복원 / 실패+다른폴더+복원스킵
- `FileTable.selection.test.tsx` — RTL 6시나리오:
  1. 클릭 → selectOnly, BulkActionBar 노출
  2. Ctrl+click → toggle
  3. Shift+click → range (앵커 유지)
  4. Space → 포커스 행 toggle
  5. Ctrl+A → 전체, Esc → clear
  6. 휴지통 → pending → invalidate → clear
  + pending 행 ArrowDown 스킵
  + Ctrl+A 전체 pending no-op

### 7.2 수동 브라우저 검증 (M3 리듬 유지)
- 클릭/Ctrl+click/Shift+click/Space/Ctrl+A/Esc 경로
- 휴지통 버튼 → pending 시각 확인 → 500ms 후 리스트에서 제거

### 7.3 품질 게이트
- `pnpm typecheck && pnpm lint` 통과
- 원칙 #3 (낙관적 업데이트 정책) 준수: 파괴적 액션은 pending 로딩으로
- 원칙 #1 (URL=진실) 침해 없음: folderId 여전히 URL 소유

---

## 8. 롤아웃 순서

1. `stores/selection.ts` 작성 + 단위 테스트
2. `hooks/usePermission.ts` 스텁 작성
3. `lib/api.ts`에 `deleteBulk` mock 추가
4. `hooks/useDeleteBulk.ts` + 단위 테스트
5. `components/files/BulkActionBar.tsx` 신규
6. `FileRow.tsx` 수정 (onClick 시그니처, selected/pending 시각)
7. `FileTable.tsx` 수정 (키보드, pending skip, focus 보정, clear)
8. RTL 통합 테스트
9. 수동 브라우저 검증
10. `docs/01 §5.1` 구현 노트 추가, `docs/progress.md` 기록
