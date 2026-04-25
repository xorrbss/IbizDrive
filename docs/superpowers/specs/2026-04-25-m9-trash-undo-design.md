# M9 — 휴지통 + Undo (설계)

작성일: 2026-04-25 · 자율 모드 (사용자 승인 게이트 없음)

## 목표

`docs/01 §13` + `docs/02 §6.5` 기반으로 휴지통 라이프사이클을 프론트 mock으로 완결.

## 범위

| 기능 | MVP | v1.x |
|---|---|---|
| 휴지통으로 이동 (현 deleteBulk 재정의) | ✅ | — |
| 5초 Undo 토스트 | ✅ | — |
| `/trash` 페이지 + 목록 | ✅ | — |
| 복원 (원위치) | ✅ | — |
| 영구 삭제 (휴지통 페이지에서) | ✅ | — |
| 복원 충돌 (이름 충돌 시 409 RESTORE_CONFLICT + 이름 변경 후 복원) | error 분류만 | UX는 v1.x |
| 자동 영구 삭제 D-day 카운트다운 | ❌ | ✅ |
| 다중 선택 복원 / 영구 삭제 | ✅ | — |

## 아키텍처

### 데이터 모델 (frontend mock)

`FileItem` 확장 — optional 필드:
- `deletedAt?: string`
- `originalParentId?: string`

mock storage:
- `MOCK_FILES` (active)
- `MOCK_TRASH` (trashed) — 별도 배열

### API (mock)

| 함수 | 동작 |
|---|---|
| `deleteBulk(ids)` | MOCK_FILES → MOCK_TRASH (deletedAt 기록, originalParentId 보존) |
| `listTrash()` | MOCK_TRASH 반환 (deletedAt desc) |
| `restoreFiles(ids)` | MOCK_TRASH → MOCK_FILES, 원위치 polder에 동일 normalized_name 충돌 시 `409 RESTORE_CONFLICT` |
| `purgeFiles(ids)` | MOCK_TRASH 영구 삭제 |

### 훅

- `useTrashList()` — `useQuery({ queryKey: qk.trashList(), queryFn: api.listTrash })`
- `useRestoreFiles()` — mutation, onSuccess: invalidate trash + 원위치 폴더 파일 목록 + folderTree(폴더 복원 시)
- `usePurgeFiles()` — mutation, onSuccess: invalidate trash
- `useDeleteBulk()` — 기존 훅에 sonner toast + 5초 Undo 추가

### Toast: sonner

이유: shadcn 생태 표준, 5초 duration + action button + dismiss 기본 제공. action 콜백에 `restoreFiles.mutate(ids)` 연결.

`<Toaster />` 마운트 위치: `(explorer)/layout.tsx` 최상단 (DndProvider 내부 OK).

### 라우팅

`/trash` — `app/trash/page.tsx`. (explorer) layout 그룹 외부지만 사이드바/탑바는 공유 — explorer route group으로 이동: `app/(explorer)/trash/page.tsx`. 사이드바 `<TrashLink />` 추가.

### 컴포넌트

- `TrashTable` — files list와 별도. 컬럼: 이름 / 원위치 / 삭제일 / 액션[복원][영구삭제]. 가상화 동일 패턴.
- 사이드바 `<TrashLink />` — 휴지통 아이콘 + 라벨, /trash로 이동. StorageBar 위.

## 핵심 설계 결정

1. **deleteBulk는 그대로 — 의미만 "휴지통 이동"** — 호출부(FileTable Delete 키, BulkActionBar) 변경 없음. 내부 구현이 splice→push로 바뀔 뿐.
2. **Undo는 onSuccess에서 toast 띄움, action에 useRestoreFiles** — restore mutation을 hook 안에서 미리 호출해서 클로저로 캡처. 토스트 dismiss 시 자동 영구화 X (mock 단계 — 실제로는 백엔드가 30일 후 purge).
3. **충돌 정책 (복원)** — 동일 normalized_name 활성 파일이 있으면 409. 클라이언트는 RESTORE_CONFLICT 에러 받아 토스트로 알림(MVP). v1.x에서 이름 변경 후 복원 다이얼로그.
4. **권한 게이트** — TrashTable의 [영구삭제]는 `can.admin`만(생산적/파괴적 분류상 파괴적 → 숨김). M8 패턴 재사용.
5. **route 위치** — `(explorer)/trash`에 두어 layout(TopBar/Sidebar/Toaster) 공유.

## 검증

- typecheck / lint / 전체 테스트 PASS
- 신규 테스트:
  - `api.deleteBulk` — MOCK_TRASH로 이동 확인
  - `api.restoreFiles` — 성공 / 충돌 / NOT_FOUND
  - `api.listTrash` — 목록 + 정렬
  - `api.purgeFiles` — splice
  - `useDeleteBulk` — sonner mock 호출, action 콜백
  - `TrashTable` — 행 렌더 + 복원/영구삭제 버튼

## 영향 문서

- `docs/01 §18` M9 행 완료 마커
- `docs/02 §8` 에러 코드 — RESTORE_CONFLICT 추가
- `docs/progress.md` 세션 기록
