---
Last Updated: 2026-05-02
Status: 🏁 COMPLETE
---

# Context — M-Download

## SESSION PROGRESS

- 2026-05-02 — DL.0 worktree `feature/m-download-wire` from master `4e3da46` 생성.
  dev-docs 3파일 부트스트랩. baseline `pnpm test --run` GREEN 594/594 확인.

## Current Execution Contract

- **운영 모드**: 자율 실행 + 게이트는 closure(push/PR/squash-merge/worktree 제거)에서만 보고.
- **TDD**: phase 별 RED → GREEN. 회귀 0.
- **검증**: 각 phase 종료 시 `pnpm test --run` GREEN 유지. 마지막 closure 전 typecheck/lint/build clean.
- **파일 제한**: 500 line 이내. 새 추상화 금지(KISS).
- **불변 원칙**: docs CLAUDE.md ULTIMATE INVARIANTS 1~10. 특히 6 (구조 우선),
  7 (500라인), 9 (문제 은폐 금지).

## 현재 active task

DL.0(완료) → DL.1 RED 진입 직전.

## 다음 세션 읽기 순서

1. `dev/active/m-download-wire/m-download-wire-plan.md` (전체 계획)
2. `dev/active/m-download-wire/m-download-wire-tasks.md` (체크박스 + 참조 블록)
3. `frontend/src/components/files/BulkActionBar.tsx` (현재 stub `:63-66`)
4. `frontend/src/lib/api.ts:159-339` (api 객체 시그니처 + uploadFile 패턴)
5. `frontend/src/components/files/BulkActionBar.test.tsx` (rename/share describe 패턴)
6. `frontend/src/lib/api.upload.test.ts` (XHR mock 패턴 — anchor mock 참고)
7. `docs/02-backend-data-model.md:1096-1107` (download spec)

## 핵심 파일과 역할

| 파일 | 역할 |
|---|---|
| `frontend/src/lib/api.ts` | `downloadFile(id)` 신설 — anchor click |
| `frontend/src/lib/api.downloadFile.test.ts` | wire 테스트(신설) |
| `frontend/src/components/files/BulkActionBar.tsx` | `handleDownload` 와이어링 + file-only 가드 |
| `frontend/src/components/files/BulkActionBar.test.tsx` | 다운로드 버튼 describe 추가 |
| `docs/01-frontend-design.md` §9 | UX 한 줄 명시 |
| `docs/progress.md` | top entry |

## 중요한 의사결정

1. **anchor `<a>` 트릭 채택** (XHR/fetch + Blob 대신). 이유:
   - cookie 인증 same-origin 자동 동봉
   - RFC 5987 `Content-Disposition: attachment; filename*=UTF-8''...`을 backend가 이미 처리 → 브라우저가 제목 자동 적용
   - 메모리 효율: 파일이 100MB까지 갈 수 있음. fetch+Blob은 전체를 메모리에 적재. anchor click은 스트림 → 디스크.
   - 진행률은 브라우저 다운로드 매니저 책임 → UI 추가 0
   - KISS: 함수 5줄

2. **DOWNLOAD enum 미사용** — backend `hasPermission('file', 'READ')` 가드(ADR #36).
   frontend `usePermission().DOWNLOAD`는 UX 게이트(이미 BulkActionBar `can.DOWNLOAD`),
   실제 권한은 backend READ가 진실의 출처.

3. **file-only 가드** — BulkActionBar에서 폴더 선택 시 비활성. 폴더 다운로드(zip)는
   별도 트랙. 사용자 혼란 방지를 위해 tooltip 명시.

4. **fire-and-forget** — 다운로드 후 토스트 / 진행률 / 결과 처리 없음. 브라우저 매니저
   사용자 피드백 책임. 추가하면 YAGNI 위배.

## 빠른 재개 안내

```bash
cd /c/project/IbizDrive/.claude/worktrees/m-download-wire/frontend
pnpm test --run                       # baseline 594 + 본 트랙 추가 통과 확인
git status                            # untracked / modified 확인
cat ../dev/active/m-download-wire/m-download-wire-tasks.md  # 다음 task
```
