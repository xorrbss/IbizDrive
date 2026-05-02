---
Last Updated: 2026-05-02
Status: 🚧 IN PROGRESS
---

# Tasks — M-Download

## phase 상태

| Phase | 상태 |
|---|---|
| DL.0 bootstrap | ✅ 완료 |
| DL.1 RED→GREEN | ⏳ 진행 |
| DL.2 closure | ⏳ 대기 |

## DL.0 — bootstrap

- [x] worktree `feature/m-download-wire` from `4e3da46` master
- [x] dev-docs 3파일 (`plan/context/tasks`)
- [x] baseline `pnpm test --run` GREEN 594/594 확인

## DL.1 — RED → GREEN: download wire

### 작업 전 필독

- `frontend/src/components/files/BulkActionBar.tsx:63-66` (현재 stub)
- `frontend/src/lib/api.ts:159-339` (api 객체 + uploadFile XHR 패턴)
- `frontend/src/lib/api.upload.test.ts` (XHR mock 패턴 참고)
- `frontend/src/components/files/BulkActionBar.test.tsx` (share/rename describe 패턴)
- `docs/02-backend-data-model.md:1096-1107` (download spec)

### 원본 코드 참조

- `BulkActionBar.handleShare` / `handleRename`: `count === 1 && !!singleItem` 가드
  + `singleItem.type` 분기 패턴 — 다운로드도 동일 형태로 작성.

### 구현 대상

#### RED 1: `frontend/src/lib/api.downloadFile.test.ts`

- [ ] anchor mock (`document.createElement` spy)
- [ ] 케이스:
  - `api.downloadFile('file_x')` → anchor `href`가 `/api/files/file_x/download`
  - `id`가 특수문자(예: `f / x`) → `encodeURIComponent` 적용 확인
  - anchor가 `appendChild` 후 `click()` 호출됨
  - `removeChild`로 cleanup

#### RED 2: `BulkActionBar.test.tsx` 다운로드 describe

- [ ] mock `@/lib/api`에 `downloadFile: vi.fn()` 추가 (기존 mock 유지)
- [ ] `mockReturnValue`로 useFilesInFolder ITEMS 제공
- [ ] 케이스:
  - 단일 file 선택 → 활성, 클릭 시 `api.downloadFile('f1')` 호출
  - 단일 folder 선택(`fo1`) → 비활성 + tooltip "파일만 다운로드 가능"
  - 다중 file 선택 → 비활성 + tooltip "단일 파일 선택 시 사용 가능"
  - 캐시 미스(items=undefined) + 단일 선택 → 비활성

#### GREEN 1: `api.ts`에 `downloadFile` 추가

- [ ] `api` object에 `downloadFile(id: string): void` 추가
  - `const a = document.createElement('a')`
  - `a.href = '/api/files/' + encodeURIComponent(id) + '/download'`
  - `document.body.appendChild(a); a.click(); document.body.removeChild(a)`
  - 주석: docs/02 §7.6.1 + ADR #36 + cookie auth same-origin

#### GREEN 2: `BulkActionBar.tsx` `handleDownload` 와이어링

- [ ] `downloadEnabled = count === 1 && singleItem?.type === 'file'`
- [ ] `handleDownload`: `if (!downloadEnabled || !singleItem) return; api.downloadFile(singleItem.id)`
- [ ] 버튼: `disabled={!downloadEnabled} title={...}` + `aria-disabled` (rename/share 패턴 동일)
- [ ] 기존 `console.warn` stub 제거

### 검증 참조

- [ ] `pnpm test --run` GREEN — baseline 594 → 새 케이스 추가 후 회귀 0
- [ ] `pnpm typecheck` clean
- [ ] `pnpm lint` clean
- [ ] `pnpm build` clean (DL.2 closure 직전 1회)

### 문서 반영

- DL.2에서 묶어 처리.

## DL.2 — closure

### 작업 전 필독

- `docs/01-frontend-design.md` §9 (업로드 섹션 — 옆에 다운로드 한 줄 추가)
- `docs/progress.md:1-50` (top entry 패턴)
- `dev/completed/m9-trash-undo/` (archive 패턴)

### 구현 대상

- [ ] `docs/01 §9` 끝에 "9.5 다운로드" 한 단락 추가 — single file fire-and-forget
      anchor click, backend RFC 5987 Content-Disposition 위임.
- [ ] `docs/progress.md` top entry: "🏁 M-Download 트랙 종료"
- [ ] `dev/active/m-download-wire/` → `git mv dev/completed/m-download-wire/`
- [ ] `git add` + commit + push
- [ ] `gh pr create`
- [ ] PR squash-merge
- [ ] worktree remove + branch 정리

### 검증 참조

- [ ] PR CI GREEN
- [ ] master에서 `pnpm test --run` GREEN
- [ ] dev-docs archive 정상 (active 비어있고 completed/m-download-wire 존재)

### 문서 반영

- `docs/01 §9.5` 신설 (위 참조)
- `docs/progress.md` top entry
