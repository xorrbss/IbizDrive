---
Last Updated: 2026-05-29
---

# folder-upload — Plan

## 요약

폴더(디렉토리) 업로드를 정식 지원한다. 드래그&드롭(`webkitGetAsEntry` 재귀) 및 사이드바 버튼
(`<input webkitdirectory>`)으로 받은 폴더의 하위 구조를 그대로 재현하며 모든 파일을 업로드한다.
**프론트 오케스트레이션으로 구현하며 백엔드는 변경하지 않는다** — 기존 `POST /api/folders`,
`POST /api/files`, 업로드 store/`useUpload` 파이프라인을 그대로 재사용한다.

## 현재 상태 분석

- 업로드 파이프라인은 **파일 전용**. `useNativeFileDrop.ts:46`이 `e.dataTransfer.files`를
  그대로 `Array.from`해 넘기고, `SidebarNewButton.tsx:111`은 `<input type=file multiple>`만 둔다.
- 폴더를 드롭하면 디렉토리를 나타내는 가짜 `File`이 그대로 `enqueue`되어
  `FormData.append('file', dir)` → XHR 전송 시 body 읽기 실패(`NotReadableError`)/0바이트로
  `xhr.onerror` 경로 → "네트워크 연결을 확인하세요" 오해성 에러로 표면화 (`useUpload.ts:129`).
- 버튼 경로는 `webkitdirectory` 부재로 폴더 선택 자체가 불가.
- 설계 문서(`docs/01 §9`, m5 spec)에 폴더 업로드 항목 없음 → **회귀가 아니라 미구현 기능**.

### 이미 존재해 재사용하는 자산 (백엔드 무변경 근거)

- `api.getFolderChildren(parentId)` (`frontend/src/lib/api.ts:278`) — 하위 폴더를 name→id로 해석
  (병합 판단). `GET /api/folders/{id}/items` 후 `type==='folder'` 필터.
- `api.createFolder(parentId, name)` (`frontend/src/lib/api.ts:589`) — 없는 폴더 생성.
  scope 상속/`UNIQUE(parent, normalized_name) WHERE deleted_at IS NULL`/409 RENAME_CONFLICT 모두
  백엔드가 처리. (backend `FolderMutationService.create`)
- `useUpload().enqueue(files, targetFolderId)` (`frontend/src/hooks/useUpload.ts:31`,
  `stores/upload.ts:44`) — 파일별 XHR 기동 + 진행률 + 파일명 충돌 다이얼로그.
- `invalidations.afterFolderCreated(qc, {parentId})` (`frontend/src/lib/queryKeys.ts:304`) —
  신규 폴더 노출용 캐시 무효화 (folderChildren prefix + filesListPrefix + folder).

## 목표 상태

1. 폴더를 드롭/선택하면 하위 디렉토리 구조가 대상 폴더 아래 그대로 생성되고 모든 파일이 업로드된다.
2. 같은 이름의 폴더가 이미 있으면 **병합**(중복 폴더 생성 안 함). 파일명 충돌만 기존 충돌 다이얼로그로 처리.
3. 사이드바 "폴더 업로드" 메뉴 항목으로 드롭과 동일한 결과.
4. 단일/다중 **파일** 드롭·선택은 기존과 동일 (회귀 없음).
5. 백엔드/마이그레이션/업로드 store 변경 없음.

## Phase 지도

| Phase | 내용 | 게이트 | 상태 |
|---|---|---|---|
| P0 | bootstrap — plan/context/tasks 작성 | 문서 3파일 | ✅ 완료 |
| P1 | `docs/01 §9.6 폴더 업로드` 설계 섹션 + 병합 ADR 노트 | 문서 게이트 | ☐ |
| P2 | `lib/folderUpload.ts` 추출 유틸 + 단위 테스트 (RED→GREEN) | typecheck/test | ☐ |
| P3 | `useNativeFileDrop.ts` entry 동기 캡처 + 콜백 페이로드 변경 + 테스트 | typecheck/test | ☐ |
| P4 | `useFolderUpload.ts` materialize 오케스트레이터 + 테스트 | typecheck/test | ☐ |
| P5 | 진입점 와이어링 — `FileTable` 드롭 라우팅 + `SidebarNewButton` 폴더 메뉴 | typecheck/lint | ☐ |
| P6 | 통합 검증 + 수동 verify + progress.md + 문서 동기화 | full gate | ☐ |

### 데이터 흐름 (P2~P5 합산)

```text
[drop: FileSystemEntry[] | input: File[].webkitRelativePath]
   → folderUpload.extract*()  →  UploadEntry[] { file, pathSegments[] }
   → useFolderUpload.materialize(baseFolderId, entries)
        · 디렉토리 경로 dedupe + 깊이순 정렬
        · per parent: getFolderChildren → 병합(있으면 id 재사용) / createFolder
        · pathKey → folderId 맵 구성 (409 race → 재조회)
   → 파일을 folderId별로 그룹핑 → enqueue(files, folderId)  (기존 파이프라인)
   → afterFolderCreated invalidate (신규 폴더 노출)
```

## Acceptance Criteria

- [ ] 중첩 폴더(예: `proj/src/a.ts`, `proj/README.md`) 드롭 시 `proj`, `proj/src` 생성 후 파일 2개 업로드.
- [ ] 대상 폴더에 동일 이름 폴더가 이미 있으면 그 폴더로 병합(신규 중복 폴더 미생성), 내부 파일 충돌은 기존 다이얼로그.
- [ ] 빈 폴더도 생성된다 (디렉토리 경로는 파일 유무와 무관하게 materialize).
- [ ] 사이드바 "폴더 업로드"로 동일 결과.
- [ ] 단일/다중 파일 드롭·선택 회귀 없음.
- [ ] 폴더 생성 실패(권한 403 / 이름 400)는 명확한 에러로 surface하고 해당 서브트리를 큐에 잘못된 task로 넣지 않는다.
- [ ] `webkitGetAsEntry` 미지원 브라우저는 flat 파일 업로드로 안전하게 폴백.

## 검증 게이트

- Phase별: 관련 단위 테스트 + `pnpm typecheck`.
- 최종(P6): `pnpm typecheck && pnpm lint && pnpm test` 전체 통과 + `verify` 스킬로 실제 폴더 드롭 동작 관찰.

## 리스크와 완화

| 리스크 | 완화 |
|---|---|
| `DataTransferItemList`는 drop 핸들러 반환 후 무효화 | `useNativeFileDrop` drop 시점에 `webkitGetAsEntry()`를 **동기 호출**해 entry 참조 캡처. 비동기 재귀는 entry 참조로 수행 |
| `directoryReader.readEntries()`는 한 번에 ≤100개만 반환 | empty 반환까지 while 루프 반복 호출 |
| 대형 트리 → getFolderChildren/createFolder N회 round-trip | parent별 children 1회 조회 캐시 + 깊이순 직렬 생성. 대용량은 MVP 한계로 문서 명시 (YAGNI) |
| 폴더명 정규화/예약어(CON 등) → createFolder 400 | 에러 surface + 해당 서브트리 스킵, 나머지 진행 |
| 동시 업로드/생성 race → createFolder 409 | 409 시 `getFolderChildren` 재조회로 기존 id 해석(병합) |
| `webkitGetAsEntry`/`webkitdirectory` 브라우저 편차 | 사내 데스크탑(Chromium) 가정(CLAUDE.md §3 #13). 미지원 시 flat files 폴백 |

## 원칙 정합성

- KISS / YAGNI / 기존 구조 우선 — 백엔드·store·upload 파이프라인 무변경, 신규 코드는 추출+오케스트레이션 2개 유닛.
- CLAUDE.md §3 #4 (DnD 컨텍스트 분리) — 폴더 드롭도 native(Files) DnD, dnd-kit 이동과 무관.
- CLAUDE.md §5.3 — 설계 문서에 없던 영역 → P1에서 `docs/01 §9.6` 먼저 추가 후 구현.
