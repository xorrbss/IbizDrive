# Quick Action Dialog (Upload / New Folder) — Design

> **Date**: 2026-05-15
> **Trigger**: PR #253 closure follow-up (User Home Dashboard §3.1 의 두 quick action 보류).
> **Backlog ref**: `docs/v1x-backlog.md` Tier 1 → "업로드/새 폴더 quick action dialog 자동 오픈".
> **Predecessor spec**: `2026-05-14-user-home-dashboard-design.md` §3.1.

---

## 1. 목표 (Why)

User Home Dashboard `WelcomeHeader` 는 PR #246 spec §3.1 에서 **업로드** + **새 폴더** 두 quick action 을 정의했으나, PR #253 closure 결정으로 단일 navigation link (`내 워크스페이스 →`) 로 임시 통합되었음. 본 트랙은 두 quick action 을 정식 구현해 dashboard 진입 직후 1-click 으로 파일 업로드/폴더 생성을 시작할 수 있게 한다.

비목표:
- 다른 위젯(Starred/Quota/SharedWithMe) 변경 — scope crawl 회피.
- 키보드 단축키 / 우클릭 메뉴 등 다른 진입점 추가 — KISS.
- 일반화된 deep-link query convention — `?action=new-folder` 한 케이스만 도입, 향후 확장은 별도 트랙.

---

## 2. 핵심 결정

**두 액션을 다른 패턴으로 트리거 (분리)**:

| 액션 | 패턴 | 이유 |
|---|---|---|
| 업로드 | WelcomeHeader 내 in-place: 클릭 → file picker 즉시 open → 파일 선택 → `useUpload.enqueue` + `router.push(workspaceRoot)` | `<input type="file">.click()` 은 **user-gesture context** 안에서 호출되어야 브라우저 정책상 안전. navigate-후-자동-click 은 회색지대 |
| 새 폴더 | navigation + URL convention: `router.push(workspaceRoot?action=new-folder)` → explorer page 가 query 감지 → `CreateFolderDialog` mount → mount 시점에 `router.replace(workspaceRoot)` 로 query 1-shot consume | dialog 는 단순 React modal — gesture 무관. dialog 가 explorer 컨텍스트(`useCurrentFolder` folderId, sidebar tree) 안에서 열려야 의미 있어 navigation 필요 |

근거 1 — **user gesture 보존**: 업로드는 `<input>.click()` 의존. 브라우저 정책(Chromium/Firefox/Safari 공통)은 navigation 직후 1~5초 내 `.click()` 호출을 허용하지만 정확한 timeout 은 unspecified. dashboard 가 사내 데스크탑 메인(Chromium/Edge 주류, CLAUDE.md §3 #13) 이라 실패 가능성 낮으나 회귀 가드 비용 ↑.

근거 2 — **URL convention 최소화**: `?action=` 을 한 액션에만 도입. 향후 두 번째 케이스가 등장하면 확장하되 YAGNI.

근거 3 — **scope 작음**: WelcomeHeader 1 파일 + 두 explorer ClientFilesPage 2 파일 (department + team) 만 수정. 신규 컴포넌트 없음.

---

## 3. URL Contract

### 3.1 새 폴더 query convention

- **shape**: `?action=new-folder` (단일 key, 단일 value).
- **scope**: workspace folder 페이지(`/d/{deptId}/{...parts}`, `/t/{teamId}/{...parts}`) 에서만 처리. 다른 위치(/trash, /shared, /favorites, /admin) 에서는 무시.
- **수명**: 1-shot. ClientFilesPage 가 mount 시점에 감지 → dialog open + `router.replace` 로 query 제거 → 같은 URL 재방문(예: 사용자가 dialog 를 X 로 닫고 history.back) 으로 dialog 재오픈 없음.
- **유효한 값**: `new-folder` 한 가지. 알 수 없는 값은 무시 + console.warn 없음(silent).
- **다른 query 와의 공존**: `?file=xxx` (RightPanel) 와 동시 존재 가능. `router.replace` 시 `?action` 만 제거하고 다른 query 는 보존.

### 3.2 업로드 — URL convention 없음

업로드는 WelcomeHeader 안에서 모두 처리 후 단순 navigation 만 발생. URL 에 흔적 남기지 않음.

---

## 4. WelcomeHeader 변경

### 4.1 현재 상태

`components/home/WelcomeHeader.tsx:43-69` — 우측에 단일 `<Link>` "내 워크스페이스 →" 만 존재.

### 4.2 변경 후

우측 영역을 **버튼 2개**로 교체:

```
[ ⬆ 업로드 ]  [ + 새 폴더 ]
```

- 두 버튼 모두 `defaultWorkspaceLink` 가 null 이면 `disabled` + `aria-disabled="true"` + visual dim. tooltip 없음(spec §3.1 zero-state 메시지가 이미 좌측 안내문에 있음).
- 기존 "내 워크스페이스 →" link 는 **삭제**. quick action 이 workspace 진입을 포함하므로 중복.
- 버튼 styling: explorer `FolderToolbar.tsx:22-29` 의 패턴 답습 — `h-8 px-3 rounded border border-border text-fg-2 text-[12.5px]`. 업로드는 primary tone (`bg-accent text-accent-fg`) 한 단계 강조.

### 4.3 업로드 클릭 핸들러

```typescript
// hidden <input type="file" multiple ref={fileInputRef} />
const { enqueue } = useUpload()
const router = useRouter()

const handleUploadClick = () => {
  if (!defaultWorkspace) return // disabled 와 이중 가드
  fileInputRef.current?.click() // user gesture 안에서 즉시 호출
}

const handleFileChange = (e) => {
  const files = e.target.files
  if (!files || files.length === 0) return
  enqueue(Array.from(files), defaultWorkspace.rootFolderId)
  e.target.value = ''
  router.push(defaultWorkspaceLink) // 업로드 진행 overlay 가 explorer 에 mount 된 후 보이도록 navigate
}
```

- `useUpload.enqueue` 는 이미 비동기 큐 기반 — navigation 도중 task 손실 없음 (store 가 React-tree 외부, `feedback_mock_transport.md` 패턴 참고).
- `enqueue` 후 `router.push` 까지의 1-frame 간격 동안 `UploadQueueDock` 이 dashboard 에 mount 되어 있지 않아 사용자가 진행 표시를 잠깐 못 봄 — 수용 가능 trade-off. dashboard 에 `UploadQueueDock` mount 는 scope crawl.

### 4.4 새 폴더 클릭 핸들러

```typescript
const handleNewFolderClick = () => {
  if (!defaultWorkspace) return
  router.push(`${defaultWorkspaceLink}?action=new-folder`)
}
```

- 단순 navigation. dialog 의 mount/open 은 explorer 측 책임.

### 4.5 0-workspace 동작

- 두 버튼 모두 `disabled`.
- 좌측 안내문(spec §3.1)이 zero-state 메시지를 이미 표시 — 별도 tooltip 불필요.
- `defaultWorkspaceLink` 가 null 인 시점(department + team 모두 0개)이 disabled 의 단일 trigger.

---

## 5. Explorer Page Query Handler

### 5.1 적용 위치

- `app/(explorer)/d/[deptId]/[[...parts]]/ClientFilesPage.tsx`
- `app/(explorer)/t/[teamId]/[[...parts]]/ClientFilesPage.tsx`

두 파일에 동일 로직을 inline 으로 추가하거나, 공통 hook `useQuickActionParam()` 로 추출. **DRY 우선 — hook 추출 채택**:

- 위치: `hooks/useQuickActionParam.ts`
- 시그니처: `function useQuickActionParam(folderId: string): { newFolderOpen: boolean; closeNewFolder: () => void }`
- 동작: `useSearchParams().get('action')` 감지 → `action === 'new-folder' && folderId.length > 0` 일 때 `newFolderOpen=true` + 첫 render 에서 `router.replace` 로 `?action` 제거. `closeNewFolder` 는 dialog 측 onClose hook (state set false + 이미 query 가 제거됐으니 추가 navigation 없음).

### 5.2 hook 내부 구현

```typescript
'use client'
import { useEffect, useState } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'

export function useQuickActionParam(folderId: string) {
  const router = useRouter()
  const pathname = usePathname()
  const params = useSearchParams()
  const action = params.get('action')

  const [newFolderOpen, setNewFolderOpen] = useState(false)

  useEffect(() => {
    if (action === 'new-folder' && folderId.length > 0) {
      setNewFolderOpen(true)
      // ?action 만 제거, 다른 query (e.g. ?file=) 보존
      const next = new URLSearchParams(params)
      next.delete('action')
      const qs = next.toString()
      router.replace(qs ? `${pathname}?${qs}` : pathname)
    }
    // action 이 다른 값으로 바뀌면 dialog 자동 닫기 (사용자가 history.back 으로 돌아온 케이스 보호)
    // - 단, ?action 제거 후 router.replace 가 발생하면 이 effect 가 재호출되어 action===null 분기로 빠짐
    //   → setNewFolderOpen(false) 는 명시적으로 호출하지 않음 (dialog 사용자 close 와 자동 close 의도 분리)
  }, [action, folderId, pathname, params, router])

  return {
    newFolderOpen,
    closeNewFolder: () => setNewFolderOpen(false),
  }
}
```

### 5.3 ClientFilesPage 통합

각 ClientFilesPage 의 `MoveFolderDialog` 등 옆에 `CreateFolderDialog` mount 추가:

```typescript
const { newFolderOpen, closeNewFolder } = useQuickActionParam(folderId)

// ... 기존 코드 ...

<CreateFolderDialog
  parentId={folderId}
  open={newFolderOpen}
  onClose={closeNewFolder}
/>
```

- `folderId` 가 비어있는 동안(workspace landing → root redirect 중) 은 hook 자체가 무동작. redirect 완료 후 catch-all `[[...parts]]` 가 채워지면 effect 재실행.
- 기존 `SidebarNewButton` / `FolderToolbar` 의 `CreateFolderDialog` mount 와 **공존**. 같은 페이지에 두 개의 modal 이 있지만 한 번에 하나만 `open=true` — 충돌 없음.

### 5.4 다른 workspace 의 적용 여부

- `/shared`, `/trash`, `/favorites`, `/admin/*` 등은 처리 X.
- 이유: dashboard quick action 의 destination 이 항상 default workspace (부서 또는 첫 팀) root 폴더로 고정. 다른 진입점이 `?action=` 을 발급하면 그 진입점이 자체 처리.

---

## 6. Empty / Error / Loading State

- **0-workspace**: WelcomeHeader 두 버튼 disabled. 좌측 zero-state 메시지가 안내 (재사용).
- **default workspace 가 로딩 중** (`useWorkspaces` pending): 두 버튼 disabled (`defaultWorkspaceLink === null`). 로딩 끝나면 enabled 로 전환.
- **navigation 후 explorer 진입 실패** (예: workspace API 가 stale 한 rootFolderId 반환 → 404): explorer 의 기존 error 경로가 처리. quick action 트랙 책임 없음.
- **업로드 file picker 취소** (사용자가 파일 0개 선택): `handleFileChange` early-return — navigation 도 발생하지 않음. dashboard 그대로.
- **새 폴더 dialog 사용자가 X 로 close**: `closeNewFolder` 호출 → `newFolderOpen=false`. URL 은 이미 cleansed 라 재오픈 트리거 없음. 정상.

---

## 7. 테스트 전략

### 7.1 WelcomeHeader 단위테스트

`components/home/WelcomeHeader.test.tsx` 확장:

- **enabled 상태**: department 있음 → 두 버튼 enabled. 업로드 click → `fileInputRef.click()` 호출 verify(spy). 새 폴더 click → `router.push` 가 `workspaceLink + '?action=new-folder'` 호출 verify.
- **disabled 상태**: workspace 0개 → 두 버튼 모두 `disabled` attribute + `aria-disabled="true"` + click 시 핸들러 no-op.
- **업로드 flow**: `handleFileChange` 가 `enqueue` 호출 후 `router.push(workspaceLink)` 호출 verify (file picker 자체는 jsdom 한계로 mock).
- **첫 팀 fallback**: department 0개 + teams[0] 있음 → 버튼 enabled + workspace link 가 team rootFolderId 사용.

### 7.2 `useQuickActionParam` 단위테스트

`hooks/useQuickActionParam.test.ts` (신규):

- `?action=new-folder` + folderId 있음 → `newFolderOpen=true` + `router.replace` 가 `?action` 제거된 URL 호출.
- `?action=new-folder` + folderId='' (workspace landing 중) → `newFolderOpen=false` + `router.replace` 미호출.
- `?action=foo` (알 수 없는 값) → `newFolderOpen=false` + `router.replace` 미호출.
- `?action=new-folder&file=xxx` → replace 가 `?file=xxx` 보존 verify.
- `closeNewFolder()` 호출 → `newFolderOpen=false`.

### 7.3 ClientFilesPage 통합 (department + team 2종)

현재 두 `ClientFilesPage` 의 단위테스트는 부재(`(explorer)/d/**/ClientFilesPage.test.tsx`, `(explorer)/t/**/ClientFilesPage.test.tsx` 모두 미존재 — `useQuickActionParam` hook 단위테스트가 logic 핵심을 가드하므로 두 페이지에 대한 신규 통합 테스트 작성은 **임의(implementation 시 결정)**. 작성한다면:

- `?action=new-folder` 진입 → `CreateFolderDialog` mount + dialog input focus verify.
- dialog close 후 URL 에 `?action` 없음 verify.

본 spec 은 §7.1 + §7.2 단위 테스트만을 **필수** 회귀 가드로 정의한다. ClientFilesPage 측 통합 테스트가 부재해도 closure 조건 충족.

### 7.4 회귀 가드 — 기존 SidebarNewButton / FolderToolbar 의 CreateFolderDialog

기존 entry-point 의 dialog 동작은 변경 없음. 기존 unit test 그대로 PASS 가드.

---

## 8. 영향받는 문서

- `docs/01-frontend-design.md` — quick action URL convention 짧은 메모 추가 (어느 섹션에 넣을지는 implementation 시 §9 / §17 후보 검토; 추정 §9 업로드 또는 §17 URL/라우팅).
- `docs/v1x-backlog.md` Tier 1 — 본 항목 closure 행으로 전환 (`✓ PR #xxx` + 단문).
- `docs/progress.md` — closure entry.
- `docs/superpowers/specs/2026-05-14-user-home-dashboard-design.md` §3.1 — quick action 동작 "follow-up 트랙" 메모를 본 spec 으로 cross-link.
- 본 spec 자체 — `docs/superpowers/specs/2026-05-15-quick-action-dialog-design.md`.

---

## 9. 영향받는 코드 파일 (사전 inventory)

| 파일 | 변경 종류 | 비고 |
|---|---|---|
| `components/home/WelcomeHeader.tsx` | 수정 | 버튼 2개 + 업로드 핸들러 + hidden file input |
| `components/home/WelcomeHeader.test.tsx` | 수정 | 테스트 케이스 4개 추가 |
| `hooks/useQuickActionParam.ts` | 신규 | query 감지 + 1-shot consume |
| `hooks/useQuickActionParam.test.ts` | 신규 | 5 케이스 |
| `app/(explorer)/d/[deptId]/[[...parts]]/ClientFilesPage.tsx` | 수정 | hook 호출 + dialog mount |
| `app/(explorer)/t/[teamId]/[[...parts]]/ClientFilesPage.tsx` | 수정 | 동일 |
| (각 ClientFilesPage 의 테스트 파일이 있다면) | 수정 | 통합 케이스 추가 |

backend 변경 0. 신규 endpoint 0.

---

## 10. Open Question / Risk

| 항목 | 위험도 | 완화책 |
|---|---|---|
| `router.push` 직후 `enqueue` 의 store update 가 navigation 동안 React 18 batch 에서 누락? | 낮음 | `useUploadStore` 는 React-tree 외부 Zustand store — navigation 무관. 기존 `SidebarNewButton` 패턴도 같은 store 사용 + 회귀 0 |
| `useSearchParams` 가 첫 render 에서 stale? (Next.js 15 App Router 알려진 race) | 낮음 | `useEffect` 안에서 읽으므로 client navigation 완료 후 안정. PR #248 `SharedWithMeCard` row click 패턴과 동일 |
| 두 ClientFilesPage 가 미세하게 분기 (department vs team workspace 메타) → hook 만으로 충분한가 | 낮음 | hook 은 folderId 만 받음. workspace 분기 무관. 검증 시 두 page test 양쪽 가드 |
| dialog open 중 사용자가 다른 폴더로 navigate → folderId 변경 → dialog 의 parentId stale | 낮음 | `CreateFolderDialog` 의 `parentId` 가 props 라 re-mount 시 새 folderId 반영. dialog open 상태에서 sidebar 클릭으로 folder 변경하는 케이스는 드물고, sleeping bug 가 발생해도 데이터 손상 없음 (backend 가 parent folder READ 가드) |
| 향후 `?action=<other>` 추가 필요성 발생 시 hook 확장 비용 | 낮음 | `useQuickActionParam` 의 분기를 switch 로 확장 + return shape 에 새 필드 추가. 본 spec §1 비목표대로 현재 트랙에서는 도입하지 않으며, 새 action 케이스가 등장하면 별도 spec 으로 분리 |

---

## 11. Reference

- `docs/superpowers/specs/2026-05-14-user-home-dashboard-design.md` §3.1 — WelcomeHeader 의 quick action 본 spec.
- `docs/v1x-backlog.md` Tier 1 — "업로드/새 폴더 quick action dialog 자동 오픈" 행.
- `docs/progress.md` 2026-05-14 PR #253 closure entry — 본 트랙 분리 결정 history.
- `components/sidebar/SidebarNewButton.tsx:48-65` — file picker 즉시 open + `useUpload.enqueue` 답습 패턴.
- `components/explorer/CreateFolderDialog.tsx` — props 시그니처 (`parentId` / `open` / `onClose`).
- `hooks/useOpenFile.ts` — `useSearchParams` 기반 1-shot consume 패턴 reference.
