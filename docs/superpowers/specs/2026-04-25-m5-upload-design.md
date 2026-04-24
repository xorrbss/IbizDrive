# M5 — 업로드 (multipart + 충돌 + 실패 분류) 설계

작성일: 2026-04-25
마일스톤: M5 (docs/01 §18의 #5)
범위 근거: docs/01 §5.3, §7, §9, docs/02 §6.1

---

## 1. 범위

### 포함 (M5)

- multipart 업로드 (XHR 기반, 진행률 0→100%)
- Upload store (Zustand, docs/01 §5.3 구현)
- 업로드 시작 경로:
  - (a) `FolderToolbar`의 **파일 선택 버튼**
  - (b) `FileTable` 중앙 컨테이너 **OS → 브라우저 DnD** (window 네이티브)
  - (c) `FileTable` **EmptyState CTA** (a)와 동일 컴포넌트 재사용
- `UploadConflictDialog` (새 버전 / 이름 변경 / 건너뛰기 + applyToAll)
- 실패 분류 5종: `network | permission | quota | server | conflict`
- `UploadQueueDock` (우하단 고정 진행률 목록, 접기/펼치기)
- `beforeunload` 페이지 이탈 경고
- done 시 TanStack Query cache invalidation (`qk.filesInFolder(folderId)`)
- `api.ts`에 **FakeXHR 기반 MOCK** + 매직 파일명 규약

### 제외 (M5.1 또는 별도)

- tus 재개 업로드 (docs/01 §9.4, v1.x)
- 동시성 슬롯 제한 (큐 queue-limit). 기본 동작: `enqueue` 호출 시 task들을 모두 즉시 시작. 브라우저 per-origin connection 제한(~6)에 맡김
- 드래그 타겟 확장(FolderTree 노드 drop으로 **다른 폴더에 업로드**) → M7 DnD 이동 마일스톤과 함께
- 업로드 항목에 대한 이동/삭제 액션
- 서버 측 실제 저장 (실제 백엔드 연결 작업 분리)
- i18n (현재 한국어 하드코딩)

---

## 2. 아키텍처

```
ClientFilesPage (기존 explorer shell)
├── Breadcrumb              (기존)
├── FolderToolbar           신규. "업로드" 버튼 단 하나. 미래 "새 폴더" 확장 자리
├── BulkActionBar           (기존, selection > 0)
├── FileTable               (기존) — 컨테이너에 native drop 리스너 (useNativeFileDrop)
│   └── EmptyState          (기존) — CTA를 UploadButton으로 교체
├── RightPanel              (기존)
├── UploadOverlay           신규. FileTable 컨테이너 위 absolute. drag 중에만 표시
├── UploadQueueDock         신규. position:fixed 우하단. queue 있을 때만
└── UploadConflictDialog    신규. 전역 단일 인스턴스. queue에 conflict task가 있고 applyToAll이 null일 때 open
```

### 드롭 타겟 결정

드롭 타겟 = **FileTable 중앙 컨테이너 하나**. 근거:

1. M5 범위 응집도 — 드롭 타겟 확장(트리 노드 → 다른 폴더)은 본질상 M7 DnD 이동과 같은 문제(타겟 = 다른 folderId)
2. 원칙 #7 "DnD 컨텍스트 두 개 섞지 않음" — 중앙 영역에만 native, 사이드바는 오직 dnd-kit(M7). 리스너 위치가 파일 경계로 갈라지므로 규칙이 코드 구조로 강제됨
3. UX — Overlay가 중앙에만 뜨면 "drop = 이 폴더로" 피드백이 단일
4. 확장 비용 — 전역 drop 필요 시 훅 `ref`만 window로 변경. 인터페이스 동일

---

## 3. 파일 구조

### 신규 파일

```
frontend/src/
  stores/upload.ts
  hooks/useUpload.ts
  hooks/useNativeFileDrop.ts
  hooks/useUploadBeforeUnload.ts
  lib/uploadErrors.ts
  components/upload/FolderToolbar.tsx
  components/upload/UploadButton.tsx           (파일 피커 공용)
  components/upload/UploadOverlay.tsx
  components/upload/UploadQueueDock.tsx
  components/upload/UploadConflictDialog.tsx
```

### 수정 파일

```
frontend/src/lib/api.ts                         (uploadFile → FakeXHR)
frontend/src/components/files/FileTable.tsx     (containerRef, EmptyState CTA 연결)
frontend/src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx
                                                (FolderToolbar / Overlay / Dock / Dialog 통합)
```

### 테스트 파일

모두 Vitest + @testing-library/react.

```
stores/upload.test.ts
hooks/useUpload.test.ts
hooks/useNativeFileDrop.test.ts
components/upload/UploadConflictDialog.test.tsx
components/upload/UploadQueueDock.test.tsx
components/upload/UploadOverlay.test.tsx
lib/api.uploadFile.test.ts                      (FakeXHR 매직 파일명 6종)
```

---

## 4. Upload Store

docs/01 §5.3 그대로 + MVP 보강.

```ts
// stores/upload.ts
export type UploadErrorKind =
  | 'network' | 'permission' | 'quota' | 'server' | 'conflict'

export type UploadTask = {
  id: string                            // crypto.randomUUID()
  file: File
  targetFolderId: string                // enqueue 시점 폴더 스냅샷
  status: 'queued' | 'uploading' | 'done' | 'failed' | 'conflict'
  progress: number                      // 0..1
  uploadedBytes: number
  error?: { kind: UploadErrorKind; message: string }
  conflictWith?: { fileId: string; fileName: string }
  conflictResolution?: 'new_version' | 'rename' | 'skip'
  enqueuedAt: number
}

export type UploadState = {
  queue: UploadTask[]
  applyToAll: UploadTask['conflictResolution'] | null
  enqueue: (files: File[], targetFolderId: string) => string[]   // 생성된 ids
  updateTask: (id: string, patch: Partial<UploadTask>) => void
  resolveConflict: (
    id: string,
    resolution: 'new_version' | 'rename' | 'skip',
    applyToAll?: boolean,
  ) => void
  retry: (id: string) => void
  cancel: (id: string) => void
  clearDone: () => void
  pendingCount: () => number            // status 'queued' | 'uploading' | 'conflict' (완료를 막는 모든 상태)
}
```

### 명세 보강

- `paused` 상태 제외 (tus M5.1)
- `applyToAll`은 **store 전역**으로 유지 (단순함 우선). 오직 `clearDone` 호출 시점에 null로 리셋 (queue 자연 비움 시점을 추적하지 않음 — 복잡도 절감)
- `pendingCount()`에 `conflict` 포함 이유: 충돌 대기는 사용자 결정 전까지 task가 살아있음. 페이지 이탈 시 손실되므로 beforeunload 경고 대상
- `cancel(id)`은 `failed`로 전환 + `error.kind = 'network'` + `message = '취소됨'` (status `'canceled'` 별도 도입하지 않음 — MVP 간결성)
- `retry(id)`은 `progress: 0, uploadedBytes: 0, error: undefined, status: 'queued'`로 리셋. `useUpload`가 상태 변화를 감지해 새 XHR을 기동

### 원칙 준수

- 원칙 #1 — `targetFolderId`는 enqueue 시점 **URL folderId 스냅샷**. 업로드 중 사용자가 폴더를 이동해도 해당 task는 원래 폴더로 감
- 원칙 #3 — 낙관적 append 안 함. `done` 시 invalidate로 서버 진실에서 재조회

---

## 5. useUpload 훅

```ts
// hooks/useUpload.ts
export function useUpload() {
  const enqueue  = (files: File[], folderId: string) => { ... }
  const retry    = (id: string) => { ... }
  const cancel   = (id: string) => { ... }
  const resolveConflict = (id, resolution, applyToAll?) => { ... }
  return { enqueue, retry, cancel, resolveConflict }
}
```

### 동작

1. `enqueue` → store `enqueue(files, folderId)` → 반환된 id마다 `startXHR(task)` 호출
2. `startXHR`:
   - `const xhr = api.uploadFile(task)` (XHR-like)
   - `xhr.upload.onprogress` → `updateTask(id, { progress, uploadedBytes, status: 'uploading' })`
   - `xhr.onload`:
     - `status === 200` → `updateTask({ status: 'done', progress: 1 })` + `queryClient.invalidateQueries(qk.filesInFolder(targetFolderId))`
     - `status === 409` → `responseText` JSON 파싱 → `updateTask({ status: 'conflict', conflictWith })`
     - 그 외 비-2xx → `updateTask({ status: 'failed', error: classifyError(xhr) })`
   - `xhr.onerror` → `updateTask({ status: 'failed', error: { kind: 'network', message: '네트워크 연결을 확인하세요' } })`
3. `cancel(id)` → 해당 task의 live XHR에 `abort()` 호출 + store 상태 전환
4. `retry(id)` → store `retry`로 상태 리셋 → 새 XHR 기동
5. `resolveConflict`:
   - `skip` → `updateTask({ status: 'done', progress: 1 })` (목록 변경 없음, invalidate 스킵)
   - `new_version` → 새 XHR with `?resolution=new_version`
   - `rename` → 파일명에 `(2)` 접미사 부여 후 새 XHR. **단일 시도**. 재차 409가 오면 다시 ConflictDialog 표시 (사용자가 다른 옵션 선택 또는 수동 이름 입력 — 수동 입력은 M5 밖, 현재는 skip/new_version만). 클라이언트가 `(2)`, `(3)`으로 자동 증가시키지 않음: 서버의 정규화 결과에 대한 추측을 피하고 진실 출처를 서버로 유지 (원칙 #6)
   - `applyToAll === true`면 store `applyToAll` 설정 → 이후 `conflict` 타입 onload는 다이얼로그 없이 자동 적용

### 라이브 XHR 추적

훅 내부 `const xhrMap = useRef<Map<string, FakeXHR | XMLHttpRequest>>`로 task id → xhr 매핑. `cancel`/`retry`에서 이전 xhr `abort` 후 map 갱신. 언마운트 시 일괄 abort (M5 범위 한정).

---

## 6. FakeXHR MOCK 구현

### 매직 파일명 규약

사용자 지정 (2026-04-25):

| 파일명 | 응답 | 근거 |
|---|---|---|
| `conflict.pdf` | 409 Conflict. `responseText = {"existing":{"fileId":"f_conflict","fileName":"conflict.pdf"}}` | 중복 이름 경로 검증 |
| `huge.bin` | 413 Payload Too Large | quota 분류 검증 |
| `deny.txt` | 403 Forbidden | permission 분류 검증 |
| `net_fail.any` | `onerror` 호출 (status 0) | network 분류 검증 |
| `srv_500.any` | 500 Internal Server Error | server 분류 검증 |
| 그 외 | 200 OK. 진행률 0→100% (약 1200ms) | 정상 경로 |

### FakeXHR 인터페이스

`XMLHttpRequest`의 업로드 사용 서브셋만 흉내:

```ts
export class FakeXHR {
  upload: { onprogress: ((e: { loaded: number; total: number; lengthComputable: boolean }) => void) | null } = { onprogress: null }
  onload: (() => void) | null = null
  onerror: (() => void) | null = null
  status = 0
  responseText = ''

  private intervalId: ReturnType<typeof setInterval> | null = null
  private aborted = false
  private filename: string = ''
  private totalBytes: number = 0

  open(_method: string, _url: string): void
  send(form: FormData): void
  abort(): void
}
```

`send(form)` 동작:
- form에서 `file` 추출 → `this.filename`, `this.totalBytes`
- `setInterval(tick, 50ms)` — 매 tick 진행률 ~4%씩 증가
- 매직 파일명 분기: 진행률이 40%에 도달한 시점에서 지정 결과 디스패치
  - `net_fail.any` → `clearInterval` + `onerror`
  - `huge.bin`, `deny.txt`, `srv_500.any`, `conflict.pdf` → `clearInterval` + status 설정 + `onload`
- 매직 아닌 파일 → 100% 도달 시 `status = 200` + `onload`

`abort()` 동작:
- `clearInterval(this.intervalId)`
- `this.aborted = true`
- `this.onerror?.()` 호출 (status 0 유지)

### 교체 경로

실제 백엔드 도입 시 `api.uploadFile`의 내부 구현만 실제 `XMLHttpRequest`로 교체. 소비자(`useUpload`)는 인터페이스 동일하므로 변경 없음.

---

## 7. UploadConflictDialog

### 트리거

`useUploadStore`에서 파생 selector:
```
open === queue.some(t => t.status === 'conflict') && applyToAll === null
currentTask === queue.find(t => t.status === 'conflict')
```

### 구조

- Radix Dialog (shadcn/ui) 사용. 없으면 네이티브 `<dialog>`.
- role="dialog", aria-modal, aria-labelledby, aria-describedby
- 포커스 트랩, 초기 포커스 = 첫 라디오
- 콘텐츠:
  ```
  "<filename>"이(가) 이미 존재합니다.
    ○ 새 버전으로 추가 (기존 파일 유지, 버전 히스토리에 추가)
    ○ 이름 변경하여 업로드 (<filename> (2).<ext>)
    ○ 건너뛰기
    [ ] 이후 충돌에 동일하게 적용
    [취소]  [적용]
  ```
- Esc → 해당 task `skip` 처리 (취소 버튼과 동일)
- Enter → 포커스된 버튼 실행 (기본 포커스 = 적용)

### 접근성 요구(DoD n)

- Tab 순환이 다이얼로그 내부에서만 일어남
- Esc로 닫힘 + currentTask `skip` 처리
- `aria-labelledby`/`aria-describedby` 검증

---

## 8. UploadQueueDock

- `position: fixed; bottom: 16px; right: 16px; width: 360px; max-height: 60vh; overflow-y: auto`
- `queue.length === 0` → null
- 헤더: 진행률 요약 (예: "업로드 3/5 완료 · 평균 62%") + 접기 토글
- 항목 행(각 task):
  - 파일명 (truncate)
  - 상태 배지 (대기/업로드 중/완료/실패/충돌)
  - 진행바 (`<progress>` 또는 div)
  - 액션:
    - `uploading` → 취소
    - `failed` → 재시도 + 제거
    - `conflict` → "해결" 버튼 (Dialog 트리거; applyToAll 설정 시 자동)
    - `done` → 제거
- 푸터: "완료 항목 모두 지우기" (`clearDone`)

### 원칙 체크

- 원칙 #8 — 가상화 없음 (MVP 범위의 동시 업로드는 적음)

---

## 9. UploadOverlay

```ts
// hooks/useNativeFileDrop.ts
function useNativeFileDrop(ref: RefObject<HTMLElement>, onDrop: (files: File[]) => void) { ... }
```

- dragenter counter 방식 (nested enter/leave에 안정적)
- `e.dataTransfer.types.includes('Files')`로 dnd-kit 이동과 구분 (원칙 #7)
- `isDragging` 상태 컴포넌트 local (Zustand에 넣지 않음 — 전역 관심사 아님)

UploadOverlay 컴포넌트:
- `absolute inset-0`, 반투명 점선 테두리, 중앙 "여기에 놓아 업로드" 문구
- `pointer-events: none` 자식, drop 이벤트는 컨테이너에서 받음

---

## 10. useUploadBeforeUnload

```ts
export function useUploadBeforeUnload() {
  const pendingCount = useUploadStore(s => s.pendingCount())
  useEffect(() => {
    if (pendingCount === 0) return
    const h = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = '' }
    window.addEventListener('beforeunload', h)
    return () => window.removeEventListener('beforeunload', h)
  }, [pendingCount])
}
```

`ClientFilesPage` 최상단에서 1회 호출.

---

## 11. EmptyState CTA 통합

- `FileTable`의 Empty 상태 변경:
  ```
  이 폴더는 비어 있습니다.
  [파일 업로드] 또는 파일을 이 영역에 끌어다 놓으세요.
  ```
- 버튼은 `<UploadButton />` — `FolderToolbar`의 것과 동일 컴포넌트
- 클릭 시 hidden `<input type="file" multiple>` 트리거 → `enqueue(files, currentFolderId)`

---

## 12. 에러 분류 유틸

```ts
// lib/uploadErrors.ts
export type UploadErrorKind =
  | 'network' | 'permission' | 'quota' | 'server' | 'conflict'

export function classifyError(xhr: { status: number }): {
  kind: UploadErrorKind; message: string
} {
  if (xhr.status === 0)                   return { kind: 'network',    message: '네트워크 연결을 확인하세요' }
  if (xhr.status === 403)                 return { kind: 'permission', message: '업로드 권한이 없습니다' }
  if (xhr.status === 413)                 return { kind: 'quota',      message: '용량 한도를 초과했습니다' }
  if (xhr.status === 409)                 return { kind: 'conflict',   message: '이미 존재하는 이름입니다' }
  if (xhr.status >= 500)                  return { kind: 'server',     message: '서버 오류가 발생했습니다' }
  return { kind: 'server', message: `오류 (${xhr.status})` }
}
```

docs/02 §8 에러 코드 표준과 매핑. 새 에러 추가 시 양쪽 동기화(원칙 #12).

---

## 13. Cache Invalidation

| 이벤트 | 무효화 대상 |
|---|---|
| task done (신규) | `qk.filesInFolder(targetFolderId)` |
| task done (new_version) | `qk.filesInFolder(targetFolderId)` + `qk.fileDetail(existingFileId)` |
| conflict skip | 없음 |
| 실패 | 없음 |

docs/01 §6.2 무효화 매트릭스 부합.

---

## 14. 원칙 체크리스트

| 원칙 | 준수 |
|---|---|
| #1 URL folderId canonical | ✅ task는 enqueue 시점 folderId 스냅샷 |
| #2 RightPanel query param | N/A |
| #3 낙관적 업데이트 비파괴적만 | ✅ 업로드는 낙관적 append 금지, done 시 invalidate |
| #4 정규화 NFC | 서버(FakeXHR) 측 처리. 프론트 pre-flight 없음 |
| #5 파일 동일성 | 서버 responseText의 existing 정보 신뢰 |
| #7 DnD 컨텍스트 분리 | ✅ native는 중앙 컨테이너만, dnd-kit은 사이드바 (M7) |
| #8 aria-rowcount/rowindex | N/A (FileTable 기존 유지) |
| #11 정규화 프론트/백엔드 동일 | 해당 없음 (충돌 판정 서버 전담) |
| #12 에러 코드 계약 | ✅ `lib/uploadErrors.ts` docs/02 §8과 매핑 |

---

## 15. DoD (수동/자동 검증 시나리오)

자동(Vitest) + 수동(브라우저) 교차. 모든 항목 통과 후 progress.md 기록.

### 기본 시나리오

a. 파일 선택 업로드 성공 → 진행바 → done → 목록 갱신
b. DnD 업로드 성공 (Overlay 표시 확인)
c. `conflict.pdf` → ConflictDialog → 3가지 해결 각각 동작 (new_version / rename / skip)
d. applyToAll 체크 → 배치의 나머지 자동 적용, Dialog 다시 뜨지 않음
e. `huge.bin` → quota 메시지
f. `deny.txt` → permission 메시지
g. `srv_500.any` → server 메시지
h. `net_fail.any` → network 메시지
i. 업로드 중 페이지 이동 시도 → beforeunload 경고

### 추가 시나리오 (사용자 지정 2026-04-25)

j. **멀티파일 배치 (5개 동시 선택/드롭)** — 동시 업로드 개수 제한 정책(M5는 무제한, 브라우저 per-origin 제한에 맡김)대로 동작. 모두 병렬로 queued → uploading 전환 확인
k. **혼합 배치 `[normal, conflict.pdf, huge.bin, normal, net_fail.any]`** — 성공 2개 / 충돌 대기 1개 / 실패 2개가 `UploadQueueDock`에 한 목록으로 혼재 표시. 각자 올바른 상태/액션 버튼으로 분기
l. **배치 중 일부 실패** — 성공 파일은 목록에 유지 + `retry` 비활성(또는 미표시), 실패한 것만 재시도 버튼 활성. 재시도 후 done 되면 해당 항목만 상태 전환
m. **`api.uploadFile` 단위 테스트 (Vitest)** — 6개 매직 파일명 각각 올바른 status/error 반환:
  - `conflict.pdf` → status 409 + `responseText.existing` 파싱 가능
  - `huge.bin` → status 413
  - `deny.txt` → status 403
  - `net_fail.any` → `onerror` 호출, status 0
  - `srv_500.any` → status 500
  - `normal.txt` → status 200, `onprogress` 여러 번 호출 후 `onload`
n. **ConflictDialog 접근성** — Tab 순환 / Enter = 포커스 버튼 실행 / Esc = 취소 / 포커스 트랩 동작. `aria-labelledby`, `aria-describedby` 존재 검증
o. **취소 시 리소스 정리** — 단위 테스트:
  - `cancel()` 호출 → `clearInterval`로 FakeXHR 내부 interval 정리
  - `retry()` 호출 → 이전 task의 interval이 추가 `onprogress`를 발생시키지 않음 (새 FakeXHR로 완전히 교체)

### DoD 공통

- typecheck / lint / 전체 Vitest 통과 (기존 30 + M5 신규)
- 수동 검증 a~o 전부 통과
- `docs/progress.md`에 세션 기록 (M5 완료, 원칙 체크, 다음 세션 컨텍스트)

---

## 16. 미래 영향 (M5 이후)

- **실제 백엔드 연결** — `api.uploadFile` 내부만 실제 XHR로 교체. `useUpload` 수정 불필요
- **tus 재개 (M5.1)** — `UploadTask`에 `tusUrl` 추가 (이미 §5.3에 slot 있음). `paused` status 도입. `useUpload`의 내부 transport만 교체. UI/store 인터페이스 유지
- **FolderTree 노드 drop 업로드 (M7과 합체)** — `useNativeFileDrop`를 사이드바 노드에도 연결. 단, dnd-kit 이동과 공존 시 `dataTransfer.types.includes('Files')` 구분 + 코드 레벨 주석/테스트 명시
- **동시성 슬롯 제한** — store에 `maxConcurrent` 필드, `queued` → `uploading` 전환을 훅이 관리. 대기 task는 `queued` 유지
- **ConflictDialog 이름 충돌 즉시 프리뷰** — docs/02 §3의 정규화 함수를 프론트에도 불러 같은 폴더 내 목록과 pre-flight 매칭 (정규화 일관성 원칙 #11 충족 시에만)

---

## 17. 리스크 및 결정

### 결정: FakeXHR 방식 채택

대안: Next.js Route Handler로 `/api/files/upload` 임시 구현.
각하 근거 (사용자 지정 2026-04-25):
- 실제 백엔드 도입 시 폐기될 임시 코드
- FakeXHR은 훅 경계 계약이 안정적 → 교체 비용 URL 한 줄
- 5개 실패 분류 전부 매직 파일명으로 시뮬레이션 가능

### 결정: 드롭 타겟 = 중앙 컨테이너 한정

대안: window 전역 drop / 트리 노드 포함.
각하 근거: 원칙 #7 코드 레벨 분리 + UX 단일 피드백. 확장은 M7과 합체가 응집도 높음.

### 결정: EmptyState CTA와 Toolbar 업로드 버튼을 같은 컴포넌트로

`<UploadButton />` 단일 컴포넌트. 하드코딩된 두 위치 대신 재사용.

### 결정: `applyToAll`은 store 전역, queue가 비워질 때 리셋

대안: 배치(enqueue 호출 단위)별 스코프.
각하 근거: 배치 추적용 필드(batchId) 추가 부담. MVP는 store 전역이 직관. 실제 사용자 시나리오에서 배치를 넘나들며 충돌하는 경우 드묾. 필요 시 후속 개선.

### 리스크: FakeXHR 매직 파일명이 프로덕션에 실수로 배포

완화: `api.uploadFile` 구현부 상단에 주석 `// TODO(M5 이후): 실제 XHR로 교체` + 매직 파일명은 개발자 전용 임을 주석 명시. 추후 환경 변수 분기 고려.

### 리스크: 진행률 setInterval이 jsdom에서 flaky

완화: 테스트는 `vi.useFakeTimers()` + `vi.advanceTimersByTime()`. 수동 tick 제어.

---

## 부록 A. docs/01 §5.3 대비 차이

| 항목 | §5.3 | M5 구현 |
|---|---|---|
| `status: 'paused'` | 있음 | 제외 (tus M5.1) |
| `tusUrl?: string` | 있음 | 제외 (M5.1) |
| `conflictResolution: 'overwrite'` | 있음 | 제외 — `new_version`로 대체 (덮어쓰기는 위험, 명시적 새 버전이 정책) |
| `pendingCount()` selector | 없음 | 추가 (beforeunload에 사용) |
| `enqueue` 반환값 | 없음 | `string[]` ids 반환 (훅이 task별 XHR 기동용) |

차이는 M5의 MVP 범위와 실용성 판단. docs/01 §5.3 하단에 "M5 구현 노트"로 추가 예정 (spec 승인 후).
