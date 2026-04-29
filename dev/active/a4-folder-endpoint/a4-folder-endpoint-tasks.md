---
Last Updated: 2026-04-29
Status: 🔄 IMPLEMENTATION
---

# A4.7 — Tasks

## phase 1 — docs sync (1 commit)

- [ ] docs/00 §5 ADR #30 추가 — "A4.7 endpoint 권한 정책: root create/move(parentId/targetParentId=null) = ROLE ADMIN, 그 외 = 부모 EDIT 위임"
- [ ] docs/02 §7.5 POST /api/folders + POST /api/folders/:id/move Guard 셀에 root 케이스 한 줄 보강
- [ ] commit: `docs(A4.7): ADR #30 — root folder endpoint = ADMIN-only`

## phase 2 — DTO + handler (1 commit)

- [ ] backend/.../folder/dto/FolderDto.java — `record FolderDto(id, parentId, name, ownerId, auditLevel, createdAt, updatedAt)` + `static from(Folder)`
- [ ] backend/.../folder/dto/CreateFolderRequest.java — `record(parentId? UUID, name @NotBlank, auditLevel? String)`
- [ ] backend/.../folder/dto/RenameFolderRequest.java — `record(name @NotBlank)`
- [ ] backend/.../folder/dto/MoveFolderRequest.java — `record(targetParentId? UUID)`
- [ ] GlobalExceptionHandler에 `@ExceptionHandler(FolderNameConflictException)` 추가 → 409 RENAME_CONFLICT
- [ ] commit: `feat(A4.7): folder REST DTO + RENAME_CONFLICT mapping`

## phase 3 — controller + test (1 commit)

- [ ] backend/.../folder/FolderController.java
- [ ] backend/src/test/.../folder/FolderControllerTest.java (PermissionControllerTest 패턴, mock service)
- [ ] commit: `feat(A4.7): FolderController + 3 REST endpoint`

## phase 4 — verify + PR

- [ ] `./gradlew :backend:test --tests FolderControllerTest` GREEN
- [ ] `./gradlew test` 전체 회귀 0 (Testcontainers 슬라이스 환경 이슈 시 로컬 결과는 보고만, CI green이 진실)
- [ ] self-review (controller 분기, exception 매핑, SpEL 표기)
- [ ] PR open → CI green 대기 → 머지 OK 게이트 보고

## Acceptance Criteria

- [ ] 3 endpoint + 가드 정의, FolderControllerTest 7~8 케이스 GREEN
- [ ] FolderNameConflictException → 409 RENAME_CONFLICT envelope
- [ ] FolderMutationService 시그니처 0변경
- [ ] V5 schema / audit 정책 / A2/A3 회귀 0
- [ ] docs/00 ADR #30 + docs/02 §7.5 root 한 줄 동기화
