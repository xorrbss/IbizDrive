---
Last Updated: 2026-04-29
Status: 🔄 IMPLEMENTATION
---

# A4.7 — FolderController + REST endpoint — Plan

## 요약

A4.6에서 머지된 `FolderMutationService` (create/rename/move + audit emit)를 docs/02 §7.5 계약대로
3개 REST endpoint로 노출한다. service 시그니처 변경 0, V5/audit 정책 무영향. 응답 envelope은
`{ folder: FolderDto }` 단일(PermissionDto 패턴), breadcrumb·tree·delete·restore는 범위 외.

## endpoint 표 (docs/02 §7.5와 1:1)

| Method | Path | Guard | 본문 | 응답 |
|---|---|---|---|---|
| POST  | `/api/folders`              | `(req.parentId == null ? hasRole('ADMIN') : hasPermission(#req.parentId, 'folder', 'EDIT'))` | `{ parentId?, name, auditLevel? }` | 201 `{ folder: FolderDto }` |
| PATCH | `/api/folders/{id}`         | `hasPermission(#id, 'folder', 'EDIT')` | `{ name }` | 200 `{ folder }` |
| POST  | `/api/folders/{id}/move`    | `hasPermission(#id, 'folder', 'MOVE') and (#req.targetParentId == null ? hasRole('ADMIN') : hasPermission(#req.targetParentId, 'folder', 'EDIT'))` | `{ targetParentId? }` | 200 `{ folder }` |

## 시작 전 docs sync

- **docs/00 §5 ADR #30** 추가 — A4.7 endpoint 권한 정책: root create/move (`parentId == null`)는 ROLE `ADMIN` only, 그 외는 부모 EDIT 위임. SpEL 삼항으로 표기.
- **docs/02 §7.5** — POST `/api/folders` Guard 셀에 root 케이스 한 줄 보강 (`parentId == null`이면 ROLE ADMIN). POST `/api/folders/:id/move`도 동일.

## 단위 분할 (commits)

1. (sync) docs/00 ADR #30 + docs/02 §7.5 root 케이스 한 줄 — 1 commit
2. DTO 4종 (Folder/Create/Rename/Move) + GlobalExceptionHandler `FolderNameConflictException → 409 RENAME_CONFLICT` — 1 commit
3. FolderController + FolderControllerTest — 1 commit (논리적으로 묶임, PermissionController 머지와 동일 패턴)

## 절대 금지

- FolderMutationService 본체 변경
- delete/restore/audit_level change endpoint
- File CRUD endpoint
- V5 이후 schema
- Folder.java entity body 수정

## 검증

- `./gradlew :backend:test --tests FolderControllerTest` (단위)
- 전체 `./gradlew test` (회귀)
- CI green이 진실의 출처 (Testcontainers 슬라이스가 로컬에서 스킵될 수 있음)

## DoD

- [ ] 3 endpoint + 가드 GREEN, FolderControllerTest happy path + 검증 실패 + 충돌 매핑 7~8 케이스
- [ ] FolderNameConflictException → 409 RENAME_CONFLICT envelope
- [ ] docs/00 ADR #30 + docs/02 §7.5 root 정책 동기화
- [ ] PR open + CI green + 머지 OK 게이트 보고

## 리스크

- **R1: cycle exception 코드 분리 (MOVE_INTO_SELF/DESCENDANT)** — docs/02 §7.5는 별도 코드 명시이나 본 세션은 IllegalArgumentException → 400 BAD_REQUEST 일반 매핑으로 진행 (KISS). 코드 정련은 후속 세션 (frontend errors mirror 개입 시점에 분리).
- **R2: ownerId source** — Request body에서 받지 않고 principal.user.id로 통일 (보안 — 사용자 prompt의 "(parentId, name, ownerId, auditLevel)" 표기는 service 시그니처 인용으로 해석).
- **R3: SpEL 삼항** — `?:`는 SpEL 지원, `null` literal도 지원. 단 `#req.parentId`는 `@RequestBody`라 SpEL이 메서드 진입 시점 evaluate — `@Valid` 검증 통과 후라 NPE 없음.
