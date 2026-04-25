# M10 — 고급 키보드 + 접근성 마무리 (Design Spec)

작성일: 2026-04-25
관련 설계 섹션: docs/01 §12, §1 (원칙 #5/#8/#3/#12)
선행 마일스톤: M4 (selection slice + anchor 로직), M5 (useDeleteBulk), M7 (focus 보정 useEffect)

---

## 1. 목표

M4에서 deferred한 키보드/접근성 항목을 활성화하여 docs/01 §12.1 키맵을 완성한다.

| 키 | M4 상태 | M10 목표 |
|---|---|---|
| ↑ ↓ | ✅ focus + 단일 선택 | 유지 |
| Shift+↑↓ | ❌ deferred | ✅ 범위 확장 |
| Ctrl/Meta+↑↓ | ❌ deferred | ✅ focus-only 이동 |
| Space | ✅ 토글 | 유지 |
| Enter | ✅ 열기 | 유지 |
| Delete | ❌ deferred | ✅ 휴지통 (confirm 후) |
| F2 | ❌ deferred | ✅ rename 다이얼로그 |
| Ctrl/Meta+A | ✅ 전체 선택 | 유지 |
| Esc | ✅ 선택 해제 | 유지 |
| `/` | ❌ deferred | ✅ 전역 이벤트 디스패치 |

비목표:
- 복사/잘라내기 (Ctrl+C/X/V) — 별도 마일스톤
- FolderTree 내부 키보드 — FileTable 위주
- toast 시스템 — 충돌 시 `console.warn` 유지 (M5와 동일 패턴)

---

## 2. 핵심 설계 결정

### 2.1 Shift+↑↓ 범위 확장
M4의 `selection.selectRange(to, orderedIds)` 그대로 재사용. 핵심: **anchor (lastClickedId)는 Shift+↑↓ 동안 변하지 않는다**.

흐름:
1. Shift+↓ 입력 → `focusedIndex` 다음 non-pending row로 이동
2. 새 focus의 id를 to로 `selectRange(to, orderedIds)` 호출
3. `selectRange` 내부에서 anchor 폴백 검사 (M4 로직): null/pending/폴더 외 → 단일 선택만

상태 머신:
- 시작: focus=2, anchor=2, selected={2}
- Shift+↓ → focus=3, anchor=2, selected={2,3}
- Shift+↓ → focus=4, anchor=2, selected={2,3,4}
- Shift+↑ → focus=3, anchor=2, selected={2,3} (a<b 아니므로 swap)
- 중요: anchor=2 유지 → 진동 없음

원칙 준수: anchor 폴백 — `lastClickedId` null이거나 pending이면 새 focus 단일 선택으로 폴백. 폴더 변경 시 `selection.clear()`가 호출되어 anchor도 null이 되므로 자동 처리됨.

### 2.2 Ctrl/Meta+↑↓ focus-only
selection 상태 변경 없이 `focusedIndex`만 이동. pending 행 스킵 규칙은 일반 ↑↓와 동일.

기존 `case 'ArrowDown'` / `case 'ArrowUp'`을 modifier 분기로 확장:
- modifier 없음 → 현재 동작 (focus + selectOnly)
  - **단**: 현재 코드는 selectOnly를 호출하지 않음. ↑↓는 focus만 이동, 선택은 Space로 토글 — 기존 M4 동작 그대로 유지 (변경 없음)
- Shift → focus + selectRange
- Ctrl/Meta → focus만 (현재 modifier 없음 동작과 동일)

명료화: M4에서 ↑↓는 이미 focus-only였다. 따라서 Ctrl/Meta+↑↓는 §12.1 키맵 명세를 만족시키는 **별칭(alias)**일 뿐이며, 실질 동작은 ↑↓와 동일하게 처리한다. 향후 ↑↓의 동작을 "focus + selectOnly"로 변경해도 Ctrl/Meta+↑↓는 항상 focus-only로 남는다.

### 2.3 F2 Rename — 다이얼로그 방식
이유: 인라인 편집은 가상화 컨테이너에서 포커스 트랩이 어렵고, 행 리렌더링으로 입력 상태가 휘발될 위험이 있다. 다이얼로그는 M7 MoveFolderDialog 패턴 그대로 재사용.

**대상**:
- 우선: `focusedIndex`가 가리키는 행
- 폴백: 단일 선택된 행 (`selectedIds.size === 1` && `focusedIndex < 0`)
- 다중 선택 시: F2 무시 (BulkActionBar에 추후 추가 예정)
- pending 행: 무시

**충돌 처리** (원칙 #6 — 서버가 진실):
- 다이얼로그는 client-side validation을 하지 않음 (빈 값 외)
- mutation 실패 시 에러 코드 분기:
  - `NAME_CONFLICT` → 다이얼로그 내 inline 에러 메시지 ("같은 이름의 파일/폴더가 있습니다") + 입력 유지
  - `INVALID_NAME` → "이름은 비어있을 수 없습니다"
  - 기타 → "이름 변경에 실패했습니다" + console.warn

**API mock**: `api.renameFile(id: string, newName: string): Promise<FileItem>`
- MOCK_FILES/MOCK_TREE 양쪽에서 대상 노드의 name + normalized_name 갱신
- 같은 부모 내 중복 검사 (`normalized_name`, deleted_at IS NULL 시뮬)
- 폴더면 MOCK_TREE도 갱신, 파일이면 MOCK_FILES만
- 충돌 시 `{ code: 'NAME_CONFLICT', status: 409 }` throw
- 빈 이름은 `{ code: 'INVALID_NAME', status: 400 }` throw

**hook**: `useRenameFile()` — useMutation
- onMutate: pending 마크 (해당 id 1개)
- onSuccess: invalidate `filesInFolder(parentId)` + `fileDetail(id)` + (폴더면) `folderTree`
- onError: unmarkPending, 다이얼로그에 에러 전달
- onSettled: unmarkPending

### 2.4 Delete → 휴지통
flow:
1. Delete 키 (FileTable focused) →
2. selectedIds + focused row 결합:
   - `selectedIds.size > 0` → 그 ids 사용
   - 아니면 focused row id 단독 사용
3. `window.confirm(`${count}개 항목을 휴지통으로 이동할까요?`)` — MVP
4. 확인 시 `useDeleteBulk().mutate({ ids, folderIdAtStart: currentFolderId })` (M4 재사용)
5. pending 행은 자동 제외 (selection 자체가 pending과 상호 배제)

native `confirm()` 사용 이유: 브라우저 내장 포커스 트랩, focus 복귀, ESC 처리, 스크린리더 지원이 zero-cost. 디자인 일관성 vs 복잡도 트레이드오프에서 MVP는 native 선택. 토스트/커스텀 다이얼로그는 후속 마일스톤.

### 2.5 `/` 검색 포커스 — 전역 이벤트 배선
검색 입력 컴포넌트가 아직 없음 (M11/M14에서 마운트 예정). M10은 **트리거 인프라**만 제공:

- 전역 keydown 리스너 (`window`) 추가 — 단, 다음 조건일 때만 fire:
  - target이 `<input>`, `<textarea>`, `[contenteditable]`이 아닐 때
  - modifier 키 없을 때 (`/`만)
- 동작: `window.dispatchEvent(new CustomEvent('app:focus-search'))`
- M11/M14의 검색 입력 컴포넌트는 mount 시 이 이벤트를 listen하여 자기 자신에게 focus
- M10 시점에서는 listener 없음 → no-op (디버그 로그 X — 향후 noise)

위치: `src/hooks/useGlobalShortcuts.ts` — explorer layout에 마운트.

이유: ref forwarding으로 검색 input을 FileTable에서 직접 잡으면 컴포넌트 결합도 ↑. 이벤트 기반은 lazy attach + 다중 검색 입력 가능.

---

## 3. 컴포넌트 / 파일 변경

### 신규
| 파일 | 역할 |
|---|---|
| `src/hooks/useGlobalShortcuts.ts` | window keydown 리스너 (`/`만, 차후 확장 가능) |
| `src/hooks/useGlobalShortcuts.test.ts` | input 내부 무시, modifier 없을 때만 fire 검증 |
| `src/hooks/useRenameFile.ts` | rename mutation + invalidation |
| `src/hooks/useRenameFile.test.tsx` | 성공/충돌/무효화 검증 |
| `src/components/files/RenameDialog.tsx` | 이름 입력 + 에러 표시 + 포커스 트랩 |
| `src/components/files/RenameDialog.test.tsx` | open/close, Enter 제출, 충돌 에러, ESC |
| `src/stores/renameUi.ts` | `{ open, targetId, targetName, error }` + actions |
| `src/stores/renameUi.test.ts` | open/close/setError |

### 수정
| 파일 | 변경 |
|---|---|
| `src/lib/api.ts` | `renameFile(id, newName)` mock 추가 + 충돌 검사 |
| `src/lib/api.renameFile.test.ts` | (신규 테스트 파일) MOCK_FILES/MOCK_TREE 갱신, 에러 코드 throw |
| `src/lib/errors.ts` | `NAME_CONFLICT`, `INVALID_NAME` 추가 (이미 있으면 no-op) |
| `src/components/files/FileTable.tsx` | handleKeyDown에 Shift/Ctrl+↑↓, F2, Delete 분기 추가 |
| `src/components/files/RightPanel.tsx` 또는 새 mount | RenameDialog 마운트 (ClientFilesPage가 더 적절) |
| `src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx` | RenameDialog 추가 마운트 + useGlobalShortcuts() 호출 |
| `docs/02-backend-data-model.md` §8 | 에러 코드 표에 `NAME_CONFLICT`, `INVALID_NAME` (있으면 verify) |
| `docs/01-frontend-design.md` §18 | M10 완료 마커 |
| `docs/progress.md` | 세션 기록 추가 |

### 영향 X
- selection slice — 변경 없음, 그대로 재사용
- useDeleteBulk — 변경 없음, 그대로 재사용
- moveUi store — M10 범위 외

---

## 4. 데이터 흐름

### 4.1 Shift+↓ (focus=2, anchor=2, selected={2})
```
keydown Shift+ArrowDown
  → preventDefault
  → setFocusedIndex(prev => findNextNonPending(prev+1))
  → useEffect: scrollToIndex + 새 focused id 계산
  → selectRange(items[3].id, orderedIds)
    → anchor=2 유지, range [2..3] 추가
    → selected={2,3}, anchor=2
```

### 4.2 F2 on focused row
```
keydown F2
  → focusedId = items[focusedIndex].id (또는 single selected)
  → useRenameUiStore().open({ targetId, targetName: items[focusedIndex].name })
  → RenameDialog 마운트, input에 focus + select-all
  → user types "newname"
  → Enter
  → useRenameFile.mutate({ id, newName })
    → onMutate: markPending([id])
    → api.renameFile(id, "newname")
      ├ 성공: MOCK 갱신 → onSuccess invalidate → unmarkPending → close()
      └ 충돌: throw NAME_CONFLICT → onError: setError("같은 이름...") → 다이얼로그 유지, input 유지
```

### 4.3 Delete with selection={2,3,4}
```
keydown Delete
  → ids = [2,3,4] (selectedIds 우선)
  → confirm("3개 항목을 휴지통으로 이동할까요?")
    ├ 취소 → no-op
    └ 확인:
      → useDeleteBulk.mutate({ ids, folderIdAtStart: currentFolderId })
        → onMutate: markPending([2,3,4]) (selection 비움)
        → api.deleteBulk → onSuccess invalidate, unmarkPending, clear
```

### 4.4 `/` 키
```
window keydown
  → target이 input/textarea/[contenteditable]면 return
  → modifier 있으면 return
  → e.key === '/' → preventDefault + dispatchEvent('app:focus-search')
  → 현재 listener 없음 → no-op (M11 마운트 시 활성화)
```

---

## 5. 접근성 (원칙 #5, #8)

- FileTable의 `role="grid"` `aria-rowcount` `aria-multiselectable` — 변경 없음
- `aria-rowindex` — 변경 없음
- RenameDialog:
  - `role="dialog"` `aria-modal="true"` `aria-labelledby`
  - 마운트 시 input에 focus, select-all
  - ESC로 닫기, Enter로 제출
  - 닫힐 때 이전 focus 복귀 (FileTable의 focusedIndex 복귀) — `useRef<HTMLElement | null>` 패턴
  - 에러 메시지 `role="alert"` `aria-live="assertive"` (충돌 시 즉시 안내)
- Delete native confirm — 브라우저 내장 a11y
- `/` 트리거 — 검색 input 자체가 등장할 때 a11y는 거기 책임

---

## 6. 테스트 전략

기존 108 테스트 유지 + 신규:

| 영역 | 테스트 |
|---|---|
| `selection.selectRange` | (M4에서 이미 anchor 폴백 검증됨, 추가 X) |
| `api.renameFile` | 성공 케이스, 충돌 케이스, 빈 이름, 폴더 vs 파일, MOCK_TREE 갱신 |
| `useRenameFile` | onMutate pending, onSuccess invalidation, onError setError |
| `renameUi store` | open/close/setError |
| `RenameDialog` | render, Enter 제출, ESC 닫기, 에러 표시, 빈 입력 disabled |
| `useGlobalShortcuts` | `/` fire, input 내부 ignore, modifier 시 ignore |
| `FileTable handleKeyDown` (통합) | Shift+↓ 범위, Ctrl+↓ focus만, F2 다이얼로그 open, Delete confirm 모킹 |

목표: +20~25개 테스트 (총 128~133).

---

## 7. 원칙 체크

| # | 원칙 | M10 적용 |
|---|---|---|
| 3 | 낙관적 업데이트는 비파괴적만 | rename/delete는 pending 패턴 (M5/M7과 동일) |
| 5 | 가상화 aria 유지 | FileTable 변경은 onKeyDown 분기 추가만, role/aria 동일 |
| 6 | DB 제약이 진실의 출처 | rename 충돌은 mock에서 normalized_name 검사 후 throw, 클라이언트 사전 검사 X |
| 8 | aria-rowcount/rowindex | 그대로 |
| 12 | 에러 코드는 계약 | NAME_CONFLICT/INVALID_NAME — docs/02 §8 동기화 |

---

## 8. 마이그레이션 순서 (구현 단계)

1. errors + api.renameFile mock + tests
2. renameUi store + tests
3. useRenameFile hook + tests
4. RenameDialog component + tests
5. useGlobalShortcuts + tests
6. FileTable handleKeyDown 확장 (Shift+↑↓ → Ctrl+↑↓ → F2 → Delete)
7. ClientFilesPage 마운트 (RenameDialog + useGlobalShortcuts)
8. 통합 검증 (typecheck/lint/test 128+/회귀 0)
9. docs/01 §18, docs/02 §8, docs/progress.md 갱신
10. commit + push

---

## 9. Open Questions / 후속

- 다중 선택 + F2: BulkActionBar에 "이름 변경 (개별)" 버튼 추가는 v1.x
- 커스텀 ConfirmDialog: 현재 native confirm 사용. 디자인 일관성 필요해지면 일괄 교체 (M14/M15)
- 검색 입력 컴포넌트 (M11/M14)에서 `app:focus-search` listener 등록 잊지 말 것 — TODO 주석을 useGlobalShortcuts.ts에 명시
