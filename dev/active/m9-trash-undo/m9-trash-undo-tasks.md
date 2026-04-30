---
Last Updated: 2026-04-29
Status: 📋 BOOTSTRAP — M9.1 진입 대기
---

# M9 — Tasks

## Phase

| Phase | 상태 | 설명 |
|---|---|---|
| M9.0 bootstrap | ⏳ in progress | dev-docs 3파일 |
| M9.1 mock api soft-delete | ⏳ pending | type 확장 + deleteBulk soft + listTrash/restore/purge + 필터 + qk |
| M9.2 hooks | ⏳ pending | useTrashList + useRestoreBulk + usePurgeBulk |
| M9.3 Toast Undo | ⏳ pending | BulkActionBar 토스트 action 추가 |
| M9.4 /trash 페이지 | ⏳ pending | 라우트 + Sidebar TrashLink |
| M9.5 closure | ⏳ pending | dev-docs-update + active→completed + progress.md + PR |

## Acceptance Criteria (전체)

- [ ] vitest GREEN (신규 + 회귀)
- [ ] typecheck + lint clean
- [ ] 휴지통 라우트 수동 시나리오 (단위 테스트로 대체):
  - 파일 삭제 → 토스트 5초 → "되돌리기" 클릭 → 원위치 복귀
  - `/trash` 진입 → 삭제된 항목 표시 → 복원/영구삭제 버튼 동작
