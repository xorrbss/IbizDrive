---
Last Updated: 2026-05-01
---

# F5 — Frontend Folder Share UI 확장 TASKS

## phase별 상태

| Phase | 상태 |
|---|---|
| F5.0 bootstrap | ✅ 완료 |
| F5.1 wire backbone (types + store + api, drift 정정 포함) | 🟡 active (F5.1.0 ✅) |
| F5.2 hook + ShareDialog generalize/이동 | ⏳ pending |
| F5.3 SharesTable + Breadcrumb 진입점 | ⏳ pending |
| F5.4 docs sync + PR + archive | ⏳ pending |

---

## F5.0 — bootstrap

- [x] worktree `feature/f5-frontend-folder-share-ui` (master `7c179d1` base)
- [x] `dev/active/f5-frontend-folder-share-ui/` 3파일
- [x] `dev/process/f5-2026-05-01.md` ownership
- [x] 게이트 0 사용자 OK

---

## F5.1 — wire backbone (types + store + api)

### 작업 전 필독

- `f5-frontend-folder-share-ui-plan.md` §"신설/수정 파일"
- `frontend/src/types/share.ts` (현행 fileId 단일)
- `backend/src/main/java/com/ibizdrive/share/ShareDto.java` (wire 진실)
- `backend/src/main/java/com/ibizdrive/share/ShareController.java` (라우트 4종 직렬화 확인)
- `frontend/src/lib/api.ts:659-700` (`createShares`/`revokeShare`/`listSharesByMe`/`listSharesWithMe`)
- `frontend/src/lib/api.shares.test.ts` (현재 mock fixture 형상)
- `frontend/src/stores/shareUi.ts` + `frontend/src/stores/shareUi.test.ts`

### 원본 코드 참조

```ts
// types/share.ts:24 (현행)
export interface ShareDto {
  id: string
  fileId: string                      // ← string | null 로
  permissionId: string
  sharedBy: string
  subjectType: ShareSubjectType
  subjectId: string | null
  preset: SharePreset
  expiresAt: string | null
  message: string | null
  createdAt: string
}
```

```java
// ShareDto.java (현행 backend wire)
public record ShareDto(
    UUID id, UUID fileId, UUID folderId, UUID permissionId, UUID sharedBy,
    String message, Instant expiresAt, Instant createdAt,
    Instant revokedAt, UUID revokedBy
) {}
```

> 참고: backend record에 `subjectType`/`subjectId`/`preset` 미존재. frontend types가 backend와 drift 가능 — F5.1.0에서 grep으로 실제 wire 확정.

### 구현 대상

#### F5.1.0 — wire 형상 재확정 (read-only)

- [x] backend `ShareDto.java` record + `Share.java` entity + `ShareController.java` 응답 매핑 read
- [x] frontend `api.shares.test.ts` mock fixture 형상 grep
- [x] drift 확정 + A안 채택(사용자 OK)

#### F5.1.1 — types/share.ts wire 정렬 (drift 정정 + folder XOR)

- [ ] `ShareDto` 형상 정렬:
  - **제거**: `subjectType`, `subjectId`, `preset` (backend wire 부재)
  - **추가**: `folderId: string | null`, `revokedAt: string | null`, `revokedBy: string | null`
  - **변경**: `fileId: string` → `fileId: string | null`
  - 최종 10필드: `{id, fileId, folderId, permissionId, sharedBy, message, expiresAt, createdAt, revokedAt, revokedBy}`
  - XOR 인비ariant 주석 (한쪽만 non-null, V6 CHECK 동형)
  - active-only cursor 정책 주석 (revokedAt/revokedBy는 by-me/with-me에서 항상 null)
- [ ] `ShareCreateRequest`는 변경 없음 (POST body wire는 backend `ShareCreateRequest` record에 `subjects/preset/expiresAt?/message?` 그대로)
- [ ] 신규 헬퍼 타입 `ShareTarget = {kind:'file'|'folder', id: string, name: string}` (store/hook 공용)
- [ ] `ShareSubject`/`ShareSubjectType`/`SharePreset` 타입은 request body용으로만 유지

#### F5.1.2 — stores/shareUi.ts 갱신

- [ ] state: `target: ShareTarget | null` (기존 `fileId`/`fileName` 제거)
- [ ] `open(target: ShareTarget)` / `close()`
- [ ] `shareUi.test.ts` 테스트 case 갱신 (file/folder 각각 open/close)

#### F5.1.3 — lib/api.ts 갱신

- [ ] `createShares` 메서드 제거
- [ ] `createFileShares(fileId, req)` 추가 — POST `/api/files/:fileId/share`
- [ ] `createFolderShares(folderId, req)` 추가 — POST `/api/folders/:folderId/share`
- [ ] `api.shares.test.ts` mock fixture를 backend wire 진실로 갱신 (subjectType/subjectId/preset 제거 + folderId null + revokedAt/revokedBy null 추가)
- [ ] folder POST 케이스 신규 (호출 URL `/api/folders/:id/share` + 응답 fixture에 `fileId: null`, `folderId: 'fol-1'`)
- [ ] 기존 file POST 테스트 케이스는 메서드 이름 변경에 맞춰 갱신

#### F5.1.4 — 호출자 컴파일 보존 (BulkActionBar / useCreateShare / ShareDialog 임시 패치)

- [ ] BulkActionBar `openShare(fileId, fileName)` → `open({kind:'file', id: fileId, name: fileName})` (단일 호출 지점)
- [ ] useCreateShare `Vars`는 그대로 두고 `mutationFn`을 `api.createFileShares`로 wiring (file-only 보존). F5.2에서 Vars discriminated 전환
- [ ] ShareDialog: store 새 형상 `target.id`/`target.name` 직접 읽기 + `existingShares` 매칭 `s.fileId === target.id` 임시 (folder는 F5.2). subject/preset 표기 줄 즉시 제거(만료+해제만)
- [ ] ShareDialog/SharesTable이 `s.preset`/`s.subjectType`/`s.subjectId` 참조하는 모든 자리 컴파일 에러 → 단순화: ShareDialog `{만료} · 해제`, SharesTable preset 컬럼 자체는 F5.3에서 제거하므로 F5.1에서는 표시 함수만 죽지 않게 임시 처리(또는 F5.1에서 함께 컬럼 제거 — KISS 우선이므로 후자)
- [ ] **결정**: F5.1 시점에 SharesTable preset 컬럼도 함께 제거 (4→3 컬럼). 부분 동기화 회피

### 검증 참조

- [ ] `cd frontend && pnpm test` GREEN (회귀 0, fixture 갱신 포함)
- [ ] `cd frontend && pnpm typecheck` GREEN
- [ ] `cd frontend && pnpm lint` GREEN

### 문서 반영

- [ ] tasks.md `F5.1` 체크박스 + 결과 1줄 (`pnpm test` 결과 수치)
- [ ] context.md SESSION PROGRESS에 F5.1 라인 추가
- [ ] context.md `중요한 의사결정 1`에 F5.1.0 wire grep 결과 압축 1줄

---

## F5.2 — hook + ShareDialog generalize + 이동

### 작업 전 필독

- `f5-frontend-folder-share-ui-plan.md` §"신설/수정 파일"
- `frontend/src/hooks/useCreateShare.ts` + `useCreateShare.test.tsx`
- `frontend/src/components/files/ShareDialog.tsx` (현행) + `ShareDialog.test.tsx`
- `frontend/src/app/(explorer)/layout.tsx` (ShareDialog mount 위치)
- `grep -rn "from '@/components/files/ShareDialog'" frontend/src/` (이동 영향 범위)

### 구현 대상

#### F5.2.1 — useCreateShare Vars discriminated

- [ ] `Vars = {target: ShareTarget, req: ShareCreateRequest}`
- [ ] `mutationFn`: `target.kind === 'folder' ? api.createFolderShares(target.id, req) : api.createFileShares(target.id, req)`
- [ ] onSuccess: `invalidations.afterShareCreate(qc)` 그대로 (kind 무관 단일 prefix)
- [ ] `useCreateShare.test.tsx` file/folder 각각 케이스 추가 — fetch URL 분기 검증

#### F5.2.2 — ShareDialog 이동 + generalize

- [ ] `frontend/src/components/shares/ShareDialog.tsx` 신설 (전 파일 옮기면서 다음 변경 적용)
  - store에서 `target` 가져오기. `target.kind`/`target.id`/`target.name`
  - `existingShares = ... .filter(s => (s.fileId ?? s.folderId) === target.id)`
  - `useSharesByMe()` 사용은 그대로
  - `handleSubmit` → `createShare.mutate({target, req})`
  - 라벨: 제목 `공유` 그대로 + 본문 `<fileName>` → `<target.name>`
  - 라벨 fileset 같은 표현 유지 (target.kind 별 별도 카피는 미도입 — KISS, 사용자 메시지 차이 없음)
- [ ] `frontend/src/components/files/ShareDialog.tsx` 삭제
- [ ] `frontend/src/app/(explorer)/layout.tsx` import 갱신
- [ ] 모든 import 호출자 grep + 일괄 갱신

#### F5.2.3 — ShareDialog.test.tsx 이동 + 케이스 보강

- [ ] `frontend/src/components/shares/ShareDialog.test.tsx`로 이동
- [ ] 기존 케이스: `useShareUiStore.getState().open('f1', 'doc.pdf')` → `.open({kind:'file', id:'f1', name:'doc.pdf'})`
- [ ] 신규 케이스: folder target — `.open({kind:'folder', id:'fol1', name:'reports'})` → submit 시 `/api/folders/fol1/share` 호출 검증
- [ ] 신규 케이스: existingShares 매칭 — `folderId === target.id`인 share row 노출 검증

### 검증 참조

- [ ] `cd frontend && pnpm test` GREEN
- [ ] `cd frontend && pnpm typecheck && pnpm lint` GREEN
- [ ] `grep -rn "from '@/components/files/ShareDialog'" frontend/src/` → 0건

### 문서 반영

- [ ] tasks/context phase 갱신

---

## F5.3 — SharesTable + Breadcrumb 진입점

### 작업 전 필독

- `frontend/src/components/shares/SharesTable.tsx` + `SharesTable.test.tsx`
- `frontend/src/components/explorer/Breadcrumb.tsx` (또는 현재 위치 — F5.3 진입 시 첫 read)
- `frontend/src/app/(explorer)/layout.tsx` (Breadcrumb mount 확인)
- docs/01 §17.x (Breadcrumb 설계 — 액션 슬롯 가능 여부)

### 구현 대상

#### F5.3.1 — SharesTable kind-aware (preset 컬럼은 F5.1에서 이미 제거됨)

- [ ] `<span aria-hidden className="mr-1.5">{it.fileId ? '📄' : '📁'}</span>`
- [ ] `{it.fileId ?? it.folderId}` 표시
- [ ] 컬럼 헤더 `파일` → `항목` (aria-label 동기화 — `받은 공유 항목` 그대로)
- [ ] `SharesTable.test.tsx` 신규 케이스: folder share row가 📁 + folderId로 노출 + preset 컬럼 부재 회귀 확인

#### F5.3.2 — Breadcrumb 폴더 공유 진입점

- [ ] Breadcrumb 컴포넌트 baseline read 후 진입점 위치 확정
- [ ] 두 후보 중 택1:
  - (a) Breadcrumb 우측 fixed action `[공유]` 버튼 인라인 추가
  - (b) `BreadcrumbShareAction.tsx` 별도 컴포넌트로 추출 (Breadcrumb이 props slot 패턴이면)
- [ ] 클릭 시 `useShareUiStore.open({kind:'folder', id: currentFolderId, name: currentFolderName})`
- [ ] 루트 폴더(`/files`)에서는 비활성 또는 미노출 (currentFolderId === null)
- [ ] 권한 가드: useEffectivePermissions 또는 동등한 hook으로 `share` 권한 없으면 비활성/미노출 (F2 패턴 따름)
- [ ] 테스트: 액션 클릭 시 store.open이 folder target으로 호출되는지

### 검증 참조

- [ ] `cd frontend && pnpm test` GREEN
- [ ] `cd frontend && pnpm typecheck && pnpm lint` GREEN
- [ ] manual: dev 서버에서 폴더 진입 → Breadcrumb action 클릭 → ShareDialog folder kind로 열림 (가능 시)

### 문서 반영

- [ ] tasks/context phase 갱신
- [ ] context.md `중요한 의사결정 5`에 최종 진입점 위치 한 줄 확정

---

## F5.4 — docs sync + PR + master merge + archive

### 작업 전 필독

- `docs/01-frontend-design.md` §6.1, §6.2, §14.4, §17 (현행)
- `docs/progress.md` (F4 closure 행 — 같은 형식 미러)
- F4 closure commit `7c179d1` (PR 본문 + dev-docs archive 패턴)

### 구현 대상

#### F5.4.1 — docs/01 sync

- [ ] §6.1: qk.shares 트리/invalidation 변경 없음 명시 (folder share도 동일 prefix)
- [ ] §6.2: invalidation 매트릭스에서 share 행이 file/folder 모두 커버함 명시
- [ ] §14.4: ShareDialog 위치(`components/shares/`)와 kind-aware 동작 명시 + Breadcrumb 폴더 진입점
- [ ] §17: `/shares` 컬럼 라벨 `파일` → `항목` 변경 + folder share row 표기

#### F5.4.2 — commit + PR

- [ ] 모든 변경 staged
- [ ] commit message: `feat(F5): Frontend Folder Share UI 확장 (file/folder XOR + Breadcrumb 진입점)`
- [ ] `gh pr create` (제목 + 본문 — F4 PR 미러)
- [ ] CI green 대기

#### F5.4.3 — master squash-merge + archive

- [ ] PR squash-merge
- [ ] master pull
- [ ] `dev/active/f5-frontend-folder-share-ui/` → `dev/completed/f5-frontend-folder-share-ui/`
- [ ] `dev/process/f5-2026-05-01.md` 삭제
- [ ] `docs/progress.md`에 F5 closure 섹션 추가 (F4 미러 — 핵심 사실, 변경 파일, 핵심 결정, 다음 트랙)
- [ ] closure commit + 즉시 master push (PR 별도 없이 또는 함께 — F4 패턴 확인)

### 검증 참조

- [ ] CI green
- [ ] `pnpm typecheck && pnpm lint && pnpm test && pnpm build` 최종 GREEN

### 문서 반영

- [ ] tasks/context FINAL: phase 모두 ✅
- [ ] context.md SESSION PROGRESS에 F5 closure 라인
