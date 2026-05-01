---
Last Updated: 2026-05-01
Status: 🟡 ACTIVE — F5.0 bootstrap 완료. 게이트 0 통과 대기 중.
---

# F5 — Frontend Folder Share UI 확장 plan

## 요약

A12 backend(commit `e076a1b`, PR #26)가 `POST /api/folders/{folderId}/share`를 신설하면서 `Share` row 및 `ShareDto`가 `file_id`/`folder_id` XOR 양립으로 확장되었다. by-me/with-me 응답에는 folder share row가 자연 노출된다. 그러나 frontend 쪽은 F4(commit `d6ab9aa`, PR #27) 시점의 file-only 형상에 머물러 있다 — `ShareDto.fileId: string` 단일 필드 가정, store는 `fileId/fileName` 단수, `api.createShares(fileId, req)` 단일 메서드, `ShareDialog`/`SharesTable`도 file 라벨/아이콘 고정.

**F5.1.0 추가 발견 (2026-05-01, A안 채택)**: F4 시점부터 frontend `ShareDto`가 backend wire와 drift. backend record는 `{id, fileId, folderId, permissionId, sharedBy, message, expiresAt, createdAt, revokedAt, revokedBy}` 10필드만 직렬화 (Jackson 표준, `Share` entity 컬럼 1:1). frontend는 `subjectType/subjectId/preset` 가정 + `folderId/revokedAt/revokedBy` 누락. F4 mock fixture가 frontend types 자체 가정을 따라 GREEN. 실 backend hit 시 `existingShares.subjectType === 'everyone'` → `undefined === 'everyone'` → false → `s.subjectId`(undefined) 노출. SharesTable `preset` 컬럼도 동일하게 깨짐. `ShareControllerTest`도 wire JSON 필드 검증 부재(테스트 갭).

본 트랙은 wire drift 정정도 포함:

본 트랙은:

1. **wire 정합** — `types/share.ts`를 backend `ShareDto` 진실 형상으로 정렬. `subjectType/subjectId/preset` 제거(backend가 직렬화 안 함), `folderId` 추가 (XOR per row), `revokedAt/revokedBy` 추가 (active row에서는 항상 null, 향후 admin 화면 호환). `ShareCreateRequest.preset/subjects`는 그대로 유지(POST request body는 별도 wire — `ShareCreateRequest` record에 존재 확인).
2. **store/api/hook generalize** — `useShareUiStore` target을 discriminated union(`{kind:'file'|'folder', id, name}`)으로, `api.createShares` → file/folder 별 두 메서드, `useCreateShare`는 단일 hook + Vars discriminator.
3. **ShareDialog 위치 + 형상 generalize** — `components/files/ShareDialog.tsx` → `components/shares/ShareDialog.tsx`로 이동(소유 경계 정합), kind-aware 표시, existingShares 매칭 (`(s.fileId ?? s.folderId) === target.id`). 기존 공유 row 표기는 wire drift 제거에 맞춰 단순화: `{만료시각 || '무기한'} · 해제` (subject/preset 표기는 backlog — backend join 추가 후 복원).
4. **SharesTable kind-aware + preset 컬럼 제거** — 컬럼 `파일/공유한 사람/권한/만료` (4) → `항목/공유한 사람/만료` (3). icon 📄/📁 분기, id 표시 `fileId ?? folderId`. preset 컬럼은 wire에서 못 받으므로 제거.
5. **폴더 진입점 추가** — Breadcrumb 우측 작은 "공유" 액션 (현재 폴더 = URL `folderId` 기준, docs/01 §19 원칙 1 정합).
6. **docs/01 §6/§14/§17 동기화** + `docs/progress.md` F5 closure 행 (wire drift 정정 명시).
7. **새 backlog 등록** — backend ShareDto에 subject/preset join 필드 노출 또는 frontend가 permissions endpoint join (UI에서 subject/preset 표기 복원 시).

A10/F4 패턴(`mock → real`)이 아니라 **확장 + drift 정정** 트랙. backend 미터치(B 안 거부), 새 ADR 없음, 새 에러 코드 없음. invalidation prefix `qk.shares()` 단일 유지.

## 현재 상태 분석

### Backend 사실 (master `7c179d1` 기준)

- `Share` 엔티티: `file_id` UUID NULL · `folder_id` UUID NULL · `CHECK (file_id IS NULL) <> (folder_id IS NULL)` (XOR).
- `ShareDto` (record): `id, fileId, folderId, permissionId, sharedBy, message, expiresAt, createdAt, revokedAt, revokedBy` — record 필드명 wire 1:1 (Jackson). **subjectType/subjectId/preset 미노출** (Share entity 컬럼 자체에 없음, permissions 테이블 join 필요 → A13 backlog).
  - active row(by-me/with-me 응답)에서는 `revokedAt = null`, `revokedBy = null` 보장 (cursor 쿼리 active 한정).
  - `ShareCreateRequest`(POST body): `subjects/preset/expiresAt?/message?` — 그대로 유지.
- 라우트:
  - `POST /api/files/{fileId}/share` — file share 생성 (F4).
  - `POST /api/folders/{folderId}/share` — folder share 생성 (A12).
  - `GET /api/shares/by-me` / `/with-me` — file/folder 모두 자연 노출.
  - `DELETE /api/shares/{shareId}` — file/folder 공통 (canRevoke owner OR sharer OR ADMIN).

### Backend 검증 사항 — ✅ F5.1.0 완료

- `ShareDto.java` record + `Share.java` entity + `ShareController.java` 응답 매핑 확인 결과: backend가 직렬화하는 wire 필드는 위 10개 한정. `subjectType/subjectId/preset` 절대 미노출.
- `ShareControllerTest`도 JSON wire 필드 검증 부재(테스트 갭). `ShareCommandServiceTest`는 event payload만 검증.
- → **A안 채택**: frontend types를 backend wire에 정렬. 사용자 OK 받음 (2026-05-01).

### Frontend 현재 상태

- `types/share.ts:24-38`: `ShareDto.fileId: string` 단일. folder 모름. revoked 필드 미노출(YAGNI 합리).
- `stores/shareUi.ts`: `fileId/fileName/open(fileId, fileName)`. M8 시점 단순 형상 그대로.
- `lib/api.ts:659-700`:
  - `createShares(fileId, req)` — file 전용.
  - `revokeShare(shareId)` — kind-agnostic.
  - `listSharesByMe/WithMe` — kind-agnostic (응답 row만 kind 정보 보유).
- `hooks/useCreateShare.ts`: `Vars = {fileId, req}`.
- `components/files/ShareDialog.tsx`:
  - useShareUiStore에서 `fileId`/`fileName` 직접 의존.
  - `existingShares = ... .filter(s => s.fileId === fileId)` — folder share 미매칭.
  - 모든 라벨이 "파일".
- `components/shares/SharesTable.tsx`:
  - row 표시 `it.fileId` (folder share 시 빈 칸 또는 undefined 노출).
  - 컬럼 헤더 "파일", icon 📄 고정.
- 진입점:
  - `BulkActionBar.tsx` (file 다중 선택 시 공유) — file context 한정.
  - 폴더 공유 진입점 부재.

### docs 상태

- docs/01 §6.1/§6.2/§14.4/§17 모두 file share 기준으로 등재.
- docs/02 §7.9는 A12 closure 시 folder POST 추가 등재 완료.
- docs/03 §3 권한 매트릭스는 file/folder SHARE 모두 등재 완료.

## 목표 상태

### 신설/수정 파일 (예상)

| 종류 | 경로 | 변경 |
|---|---|---|
| 수정 | `frontend/src/types/share.ts` | wire 정렬: `subjectType/subjectId/preset` 제거 (POST body request에는 잔존), `fileId: string \| null` + `folderId: string \| null` (XOR) + `revokedAt/revokedBy` 추가, 헬퍼 타입 `ShareTarget` 추가. `ShareCreateRequest`는 변경 없음 |
| 수정 | `frontend/src/stores/shareUi.ts` | `target: ShareTarget \| null` + `open(target)` |
| 수정 | `frontend/src/lib/api.ts` | `createShares` → `createFileShares` + `createFolderShares` |
| 수정 | `frontend/src/hooks/useCreateShare.ts` | Vars `{target, req}` discriminated |
| 이동+수정 | `frontend/src/components/files/ShareDialog.tsx` → `frontend/src/components/shares/ShareDialog.tsx` | kind-aware. existingShares 매칭 generic화 |
| 수정 | `frontend/src/components/shares/SharesTable.tsx` | kind 컬럼/아이콘. id `fileId ?? folderId` |
| 수정 | `frontend/src/components/files/BulkActionBar.tsx` | `open({kind:'file', id, name})` |
| 수정 | `frontend/src/app/(explorer)/layout.tsx` | ShareDialog import 경로 갱신 |
| 신설 | `frontend/src/components/explorer/BreadcrumbShareAction.tsx` (또는 Breadcrumb 내부 인라인) | 폴더 공유 진입점 |
| 수정 | 위 컴포넌트 호출하는 Breadcrumb 호스트 | 진입점 마운트 |
| 수정 | 테스트: `ShareDialog.test.tsx`, `SharesTable.test.tsx`, `useCreateShare.test.tsx`, `api.shares.test.ts`, `shareUi.test.ts` | kind-aware 케이스 추가 |
| 신설 | `BreadcrumbShareAction.test.tsx` (또는 Breadcrumb 테스트 확장) | 폴더 공유 트리거 |
| 수정 | `docs/01-frontend-design.md` §6.1, §6.2, §14.4, §17 | folder share 등재 |
| 수정 | `docs/progress.md` | F5 closure 행 |

### 비-목표 (out-of-scope)

- subject picker UI(user/department/role) — `everyone` 고정 유지.
- with-me revoke — 보수 정책 유지.
- FolderTree row 우클릭 메뉴 — 별도 트랙 backlog.
- 폴더 다중 선택 BulkActionBar에 "공유" 추가 — 별도 트랙(현재 폴더 다중 선택 자체가 부재).
- backend 변경 일체 — A12 closure 그대로(B안 거부). subject/preset UI 복원은 별도 backend 트랙(가칭 A13 — ShareDto join 또는 별도 endpoint) backlog.
- ShareDialog 기존공유 row의 subject/preset 표기 — wire 부재로 본 트랙에서 노출 안 함. 만료 시각 + revoke 버튼만.
- SharesTable preset 컬럼 — 본 트랙에서 제거. A13 backlog.

## phase 실행 지도

| Phase | Title | 산출물 | 의존성 |
|---|---|---|---|
| F5.0 | dev-docs bootstrap + worktree | plan/context/tasks 3파일 + ownership 파일 | — |
| F5.1 | wire backbone (types + store + api 분리) + 회귀 GREEN | types/share.ts + shareUi.ts + api.ts (3 메서드) + 기존 테스트 갱신 | F5.0 |
| F5.2 | hook + ShareDialog generalize + 이동 | useCreateShare Vars 변경 + ShareDialog `components/shares/`로 이동 + kind-aware 폼/목록 + ShareDialog/useCreateShare 테스트 | F5.1 |
| F5.3 | SharesTable kind-aware + Breadcrumb 진입점 + 테스트 | SharesTable patch + BreadcrumbShareAction(또는 인라인) + 테스트 추가 | F5.2 |
| F5.4 | docs/01 §6/§14/§17 sync + PR + master merge + archive | docs patches + commits + PR + dev-docs `dev/completed/` 이관 + progress.md | F5.3 |

## acceptance criteria

- [ ] `frontend/src/types/share.ts`의 `ShareDto`가 backend record와 1:1 — `{id, fileId: string | null, folderId: string | null, permissionId, sharedBy, message, expiresAt, createdAt, revokedAt, revokedBy}` 10필드. `subjectType/subjectId/preset` 부재. XOR 인비ariant 주석.
- [ ] `useShareUiStore.open`이 `{kind:'file'|'folder', id, name}` discriminated target을 받는다.
- [ ] `api.createFileShares(fileId, req)` 와 `api.createFolderShares(folderId, req)` 두 메서드가 각각 backend 라우트로 fetch 한다.
- [ ] `ShareDialog`이 `frontend/src/components/shares/ShareDialog.tsx`에 위치하고, file/folder target에 대해 모두 동작 (라벨, existingShares 필터).
- [ ] `SharesTable`이 file row와 folder row를 시각적으로 구분 (📄/📁) 하고 row id 표시가 `fileId ?? folderId`. preset 컬럼 제거(컬럼 4→3).
- [ ] Breadcrumb(또는 동등한 폴더 액션 위치)에서 "공유" 클릭 시 folder ShareDialog가 열린다.
- [ ] BulkActionBar의 file 공유 동작은 회귀 0.
- [ ] `cd frontend && pnpm test` GREEN (신규 테스트 포함, 회귀 0).
- [ ] `cd frontend && pnpm typecheck && pnpm lint` GREEN.
- [ ] `cd frontend && pnpm build` `/shares` SSG 회귀 0.
- [ ] docs/01 §6.1(qk.shares 변경 없음 명시), §6.2(invalidation 매트릭스 변경 없음 명시), §14.4(ShareDialog kind-aware + Breadcrumb 진입점), §17(`/shares` 컬럼 변경) 갱신.
- [ ] `docs/progress.md`에 F5 closure 라인.

## 검증 게이트

| 게이트 | 조건 | 통과 시 |
|---|---|---|
| 0 | bootstrap 3파일 + worktree + 사용자 OK | F5.1 진입 |
| 1 | F5.1 wire backbone GREEN (`pnpm test` 회귀 0) | F5.2 진입 |
| 2 | F5.2 hook + ShareDialog 이동/generalize GREEN | F5.3 진입 |
| 3 | F5.3 SharesTable + Breadcrumb 진입점 GREEN | F5.4 진입 |
| 4 | F5.4 docs sync + PR + CI green + master merge | archive + progress.md |

## 리스크와 완화

1. ~~ShareDto wire 검증~~ — ✅ F5.1.0 완료. drift 확정 → A안으로 정정 포함.
2. **ShareDialog 이동(Edit이 아닌 Write 신설 + Delete 원본)** — 단일 PR 안에서 작업하므로 import 누락 시 build 깨짐. tasks.md F5.2 체크리스트에 `grep -r "from '@/components/files/ShareDialog'"` 강제.
3. **Breadcrumb 컴포넌트 구조 미상** — F5.3 진입 시 `frontend/src/components/explorer/Breadcrumb.tsx`(또는 등가) 먼저 read 하고 진입점 위치 확정. baseline에서 잘못된 가정 시 plan 수정하고 사용자에게 보고.
4. **store API breaking** — `open(fileId, fileName)` → `open(target)` 형상 변경. 호출자(현재 BulkActionBar 1곳 + ShareDialog 테스트) 모두 함께 갱신. typecheck가 모두 잡아냄.
5. **테스트 fixture 형상 변경** — 기존 mock에 `folderId: null` 추가 누락 시 type error. F5.1에서 fixture 일괄 갱신.
6. **m12-audit-ui-closure 트랙과 충돌** — 그쪽은 `frontend/src/app/admin/audit/logs/page.tsx`만 건드림. share 트랙 파일과 겹침 0. process 파일에 working_files 명시.

## 다음 세션 읽기 순서

1. 이 plan
2. `f5-frontend-folder-share-ui-context.md`
3. `f5-frontend-folder-share-ui-tasks.md`
4. `frontend/src/types/share.ts` (현재 fileId 단일 형상)
5. `backend/src/main/java/com/ibizdrive/share/ShareDto.java` (wire 진실)
6. `frontend/src/stores/shareUi.ts` + `frontend/src/components/files/ShareDialog.tsx` (현재 file-only)
7. `frontend/src/components/shares/SharesTable.tsx`
8. `frontend/src/lib/api.ts` (`createShares` 위치)
9. `docs/01-frontend-design.md` §6.1/§6.2/§14.4/§17 (folder share 부재 확인)
