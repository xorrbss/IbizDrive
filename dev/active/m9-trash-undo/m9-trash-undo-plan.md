---
Last Updated: 2026-04-29
Status: 📋 BOOTSTRAP
---

# M9 — Trash + Undo

## 목표

`docs/01 §13` 휴지통 + 5초 Undo + `/trash` 페이지 (frontend-only, mock api 확장).

## 범위 (이번 PR)

1. soft-delete 데이터 모델 (FileItem.deletedAt + originalParentId)
2. mock api 확장: listTrash / restoreBulk / purgeBulk
3. listFiles / searchFiles는 deleted 제외
4. useTrashList / useRestoreBulk / usePurgeBulk 훅
5. delete 직후 Toast Undo (5초)
6. `/trash` 페이지 + Sidebar `<TrashLink/>`

## DoD

- [ ] FileItem 타입 확장 (deletedAt nullable + originalParentId)
- [ ] api.deleteBulk → soft delete (splice 제거, deletedAt 세팅)
- [ ] api.listTrash() — deletedAt NOT NULL 반환
- [ ] api.restoreBulk(ids) — deletedAt 해제 + parentId 복구
- [ ] api.purgeBulk(ids) — hard splice
- [ ] listFiles / searchFiles 필터 (deletedAt == null)
- [ ] qk.trash + invalidations.afterDelete가 trash까지 무효화 + afterRestore/afterPurge 헬퍼
- [ ] BulkActionBar Toast에 "되돌리기" 액션 (5초)
- [ ] `/trash` 라우트 페이지 + 행 액션 (복원 / 영구삭제)
- [ ] Sidebar 하단 TrashLink (active 라우트 강조)
- [ ] vitest 신규: api soft-delete cycle, listTrash, restore/purge, useTrashList, BulkActionBar Undo, TrashPage row actions

## phase 분할

| Phase | 내용 |
|---|---|
| M9.0 | bootstrap dev-docs |
| M9.1 | mock api soft-delete cycle + 타입 확장 + queryKeys/invalidations |
| M9.2 | useTrashList + useRestoreBulk + usePurgeBulk 훅 |
| M9.3 | Toast Undo (BulkActionBar + FileTable Delete shortcut) |
| M9.4 | /trash 페이지 + Sidebar TrashLink |
| M9.5 | closure (dev-docs-update + active→completed + progress.md + PR) |

## 비범위

- 백엔드 endpoint (별도 마일스톤). mock api로 계약만 시뮬레이션.
- folder soft-delete 휴지통 표시 (현재 파일만 — folder soft delete는 backend A6에서 cascade 처리, frontend는 현재 폴더 삭제 액션 미노출).
- 30일 자동 purge UI (백엔드 cron 책임).

## 위험

- BulkActionBar는 이미 toast.success 사용 중 → 기존 케이스 회귀 주의 (action 버튼 추가만 변경).
- listFiles 필터 추가로 기존 테스트가 deletedAt 없는 row 가정 → 영향 없음 (deletedAt null은 active 동의어).
