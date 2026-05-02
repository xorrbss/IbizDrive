---
Last Updated: 2026-05-02
Status: 🏁 COMPLETE
---

# M-Download — BulkActionBar 다운로드 와이어링

## 요약

A15(2026-05-02 master 65e5cd3 → 4e3da46)에서 backend `GET /api/files/{id}/download`를
완전히 구현했지만 프론트 `BulkActionBar.handleDownload`는 `console.warn` 스텁이다.
사용자가 단일 파일 선택 후 "다운로드" 클릭 시 실제 브라우저 다운로드가 시작되도록
와이어링한다.

## 현재 상태 분석

- `frontend/src/components/files/BulkActionBar.tsx:63-66` — `console.warn` 스텁.
- `frontend/src/lib/api.ts` — `api.downloadFile`(단건 파일) 미정의. uploadFile은 XHR
  직접 다루지만 download는 RFC 5987 + 인증 쿠키만 있으면 브라우저가 알아서 처리하므로
  XHR/fetch 없이 `<a href>` 트릭으로 충분 (cookie auth + Content-Disposition).
- backend (docs/02 §7.6.1):
  - `GET /api/files/{id}/download`
  - Guard: `hasPermission(#id, 'file', 'READ')` (DOWNLOAD enum 미도입, ADR #36)
  - Headers: `Content-Type`, `Content-Length`, `ETag: "<versionId>"`,
    `Content-Disposition: attachment; filename="<ascii>"; filename*=UTF-8''<percent>`
  - audit: `FILE_DOWNLOADED`
  - errors: 404 / 403
- 정책 결정 (BulkActionBar):
  - **단일 파일** 선택 시만 활성. 폴더 선택 또는 다중 선택은 비활성 + tooltip.
  - 다중 zip 다운로드는 별도 트랙(out of scope).
  - 캐시 미스(useFilesInFolder data undefined) 시 비활성.
- 기존 패턴 일관성:
  - `count === 1 && !!singleItem` 가드는 rename / share에서 이미 검증됨.
  - 기능별 `handleX` 핸들러 + `xEnabled` boolean 유사.

## 목표 상태

- 단일 **파일** 선택 시 다운로드 버튼 활성 → 클릭하면 브라우저 다운로드 시작.
- 단일 **폴더** 선택 시 비활성 + tooltip("파일만 다운로드 가능").
- 다중 선택 또는 캐시 미스 시 비활성.
- backend audit `FILE_DOWNLOADED` 1회 emit (브라우저가 자동으로 GET 요청).

## phase별 실행 지도

### DL.0 — bootstrap (현재)

- worktree `feature/m-download-wire` from master `4e3da46` ✅
- dev-docs 3파일

### DL.1 — RED → GREEN

- **RED**:
  - `frontend/src/lib/api.downloadFile.test.ts` 신설 (anchor click + href 검증)
  - `frontend/src/components/files/BulkActionBar.test.tsx` 신규 describe
    "다운로드 버튼"
- **GREEN**:
  - `api.downloadFile(fileId)` — programmatic anchor (`document.createElement('a')`
    + `href = '/api/files/{id}/download'` + `click()`). 쿠키 인증은 same-origin 자동
    동봉. `target` 속성 없음(현재 탭에서 응답을 받으면 Content-Disposition으로 다운로드).
  - `BulkActionBar.handleDownload`:
    - file-only 가드: `count === 1 && singleItem?.type === 'file'`
    - 활성 시 `api.downloadFile(singleItem.id)` 호출
    - 비활성: 폴더면 tooltip "파일만 다운로드 가능", 다중 선택이면 기존 정책 따라 비활성

### DL.2 — closure

- `docs/01 §9` 다운로드 한 줄 명시 또는 §9 옆 §섹션. (KISS — UI 자체는 단순하므로
  기존 §9 끝에 짧은 다운로드 단락만 추가)
- `docs/progress.md` top entry — M-Download 트랙 종료
- `dev/active/m-download-wire/` → `dev/completed/m-download-wire/`
- PR + master squash-merge + worktree 정리

## acceptance criteria

- [ ] `api.downloadFile(fileId)`가 `/api/files/{fileId}/download`를 anchor click으로
      트리거 (URL encode 적용)
- [ ] `BulkActionBar` 다운로드 버튼은 단일 파일 선택 시만 활성, 폴더/다중/캐시미스는 비활성
- [ ] 클릭 시 `api.downloadFile`이 `singleItem.id`로 호출
- [ ] `pnpm test --run` GREEN (회귀 0, baseline 594 → 새 케이스 추가분 합산)
- [ ] `pnpm typecheck` clean
- [ ] `pnpm lint` clean
- [ ] `pnpm build` clean

## 검증 게이트

- 각 phase 종료 시:
  - DL.1: vitest GREEN + typecheck/lint
  - DL.2: build clean + dev-docs archive + PR 생성

## 리스크와 완화 전략

- **anchor click 트릭의 jsdom 차이**: jsdom은 click을 dispatch하지만 navigation은
  실제로 일어나지 않음. → 테스트는 `appendChild` + `click` spy로 검증, 실 브라우저
  동작은 RFC 5987 Content-Disposition을 backend가 보장하므로 통합 신뢰.
- **인증 쿠키**: same-origin GET이므로 브라우저가 자동 동봉. `withCredentials` 동치.
  cross-origin이 아니므로 별도 처리 불필요.
- **상태 머신 단순성**: 다운로드는 fire-and-forget. 진행률/실패 핸들링은 브라우저
  다운로드 매니저가 책임. 별도 상태 추적 불필요.

## 비범위

- 다중 zip 다운로드 (별도 트랙)
- 다운로드 진행률 UI (브라우저 매니저 책임)
- preview/inline 분기 (`/api/files/{id}/preview`는 별도 endpoint)
- DOWNLOAD audit 클라이언트 emit (backend 재검증 + audit이 진실의 출처)
