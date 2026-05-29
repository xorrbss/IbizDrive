---
Last Updated: 2026-05-29
---

# folder-upload — Session Context

## SESSION PROGRESS

- 2026-05-29: bootstrap (P0). plan/context/tasks 3파일 작성. 코드 변경 없음.
  - 원인 진단 완료: 폴더 업로드 미구현 → 디렉토리 가짜 File이 XHR 본문 읽기 실패로 오해성 에러.
  - 아키텍처 결정: **프론트 오케스트레이션 / 백엔드 무변경** (기존 getFolderChildren + createFolder + enqueue 재사용).

## Current Execution Contract

- **자율 실행 모드**: phase별 RED→GREEN→commit. phase 완료 시 보고.
- **PR 단위**: 본 트랙 1개 PR. P1~P6 누적 후 최종 push + PR open.
- **테스트 게이트**: phase별 관련 테스트 + `pnpm typecheck`. 최종 P6에서 `typecheck && lint && test` 전체.
- **충돌 시 우선순위**: CLAUDE.md §3 핵심 원칙 → 운영 규칙 → 본 plan.

## 현재 active phase / task

- **active phase**: P0 (bootstrap) — 완료, commit만 남음
- **next**: P1 (`docs/01 §9.6 폴더 업로드` 설계 섹션 추가 + 병합 ADR 노트)

## 다음 세션 읽기 순서

1. `dev/active/folder-upload/folder-upload-plan.md` — 범위 / phase 지도 / acceptance / 데이터 흐름
2. `dev/active/folder-upload/folder-upload-tasks.md` — 체크박스 진행 상태 + 참조 블록
3. `frontend/src/hooks/useNativeFileDrop.ts` — 드롭 감지 (entry 캡처 대상, P3)
4. `frontend/src/hooks/useUpload.ts:31` + `frontend/src/stores/upload.ts:44` — `enqueue` 재사용 계약
5. `frontend/src/lib/api.ts:278` (`getFolderChildren`), `:589` (`createFolder`) — 오케스트레이션 API
6. `frontend/src/lib/queryKeys.ts:304` — `invalidations.afterFolderCreated`
7. `frontend/src/components/files/FileTable.tsx:72` + `components/sidebar/SidebarNewButton.tsx` — 진입점 (P5)
8. `frontend/src/lib/normalize.ts` — `normalizeFileName` (폴더명 매칭/병합 비교)

## 핵심 파일과 역할

| 파일 | 역할 | 본 트랙 변경 |
|---|---|---|
| `docs/01-frontend-design.md` | §9 업로드 | **§9.6 신규** (P1) |
| `lib/folderUpload.ts` | 드롭/입력 → `UploadEntry[]` 추출 | **신규** (P2) |
| `hooks/useNativeFileDrop.ts` | native 드롭 감지 | 콜백 페이로드에 entries 추가 (P3) |
| `hooks/useFolderUpload.ts` | 폴더 트리 materialize + 그룹 enqueue | **신규** (P4) |
| `components/files/FileTable.tsx` | 드롭 핸들러 | 폴더/파일 라우팅 (P5) |
| `components/sidebar/SidebarNewButton.tsx` | 새로 만들기 메뉴 | "폴더 업로드" 항목 + `webkitdirectory` input (P5) |
| `lib/api.ts` | getFolderChildren / createFolder | 변경 없음 (호출만) |
| `stores/upload.ts`, `hooks/useUpload.ts` | 업로드 파이프라인 | **변경 없음** |
| backend 전체 | — | **변경 없음** |

## 중요한 의사결정

1. **백엔드 무변경 / 프론트 오케스트레이션** — `getFolderChildren` + `createFolder`가 이미 존재해
   relativePath 백엔드 확장 없이 가능. KISS·기존 구조 우선. (대안인 upload 엔드포인트 relativePath 확장은 거부)
2. **폴더 이름 충돌 = 병합(merge)** — 같은 이름 폴더 존재 시 그 폴더 id를 재사용(중복 폴더 생성 안 함).
   파일명 충돌만 기존 `UploadConflictDialog`로 처리. 재업로드 시 자연스러움. P1에서 ADR로 명기.
3. **store 무변경** — 폴더를 먼저 전부 materialize(생성/해석) 한 뒤, 해석된 folderId로 기존
   `enqueue(files, folderId)` 호출. `UploadTask`에 path 필드 추가 안 함 (파이프라인 무변경).
4. **빈 폴더도 생성** — 디렉토리 경로를 파일 유무와 무관하게 materialize.
5. **폴더 생성은 깊이순 직렬** — parent가 먼저 존재해야 child 생성 가능. parent별 children 1회 캐시.
6. **drop entry 동기 캡처** — `DataTransferItemList` 수명 때문에 `webkitGetAsEntry()`를 drop 핸들러에서
   즉시 호출. 비동기 재귀(`file()`/`readEntries`)는 캡처한 entry 참조로 수행.
7. **미지원 폴백** — `webkitGetAsEntry` 부재 시 flat `files` 업로드 (안전 폴백).

## 빠른 재개 안내

```text
1. dev/active/folder-upload/folder-upload-tasks.md 열기
2. 첫 미완료 task의 "원본 코드 참조" → "구현 대상" → "검증 참조" 순으로 진행
3. (코드 phase) RED 테스트 작성 → 실패 확인 → GREEN 구현 → 통과 → commit
4. phase 끝나면 plan §"Phase 지도" 표 갱신 + 본 파일 SESSION PROGRESS 추가
```
