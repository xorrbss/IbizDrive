---
Last Updated: 2026-04-29
Status: ⏳ in progress
Owner: frontend-m4-m10
---

# M8 — 권한 UI + 조건부 렌더링 + ShareDialog

## 목적

`docs/01 §14` 권한 훅 + 조건부 렌더링 + ShareDialog (단일 파일) 구현. 백엔드 미존재 시 mock api로 admin preset 응답.

## 범위

- `src/types/permission.ts` Permission enum(이미 존재, UPPER_SNAKE_CASE) 활용
- `usePermission(nodeId?)` — useQuery 기반, `Record<Permission, boolean>` 반환
- `api.getEffectivePermissions(nodeId?)` mock — 기본 admin preset (READ/UPLOAD/EDIT/MOVE/DOWNLOAD/DELETE/SHARE/PERMISSION_ADMIN)
- `qk.permissions(nodeId)` / `qk.effectivePermissions()`
- BulkActionBar 필드 마이그레이션 (lowercase → UPPER_SNAKE_CASE)
- ShareDialog (단일 파일) — 링크 mock + 복사 + 닫기. 만료/권한 옵션 UI placeholder.
- BulkActionBar 단일 선택 시 "공유" 버튼 (SHARE 권한 게이트)

## 비범위 (후속)

- 403 글로벌 핸들러 (toast + qk.permissions invalidate) — 별도 PR
- ShareDialog 만료/권한 옵션 실제 처리 — 백엔드 endpoint 신설 후
- 폴더 공유 — file 우선
- FileRow 우클릭/단축키 공유 진입점 — KISS, BulkActionBar 진입만

## 참조

- docs/01 §14 권한 훅
- docs/01 §18 #8
- docs/03 §3.1~§3.2 권한 enum + preset 매트릭스
- src/types/permission.ts (계약)
