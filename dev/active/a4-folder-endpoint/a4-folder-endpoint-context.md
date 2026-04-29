---
Last Updated: 2026-04-29
---

# A4.7 — Context

## 직전 상태 (master 89d302d)

- A4.6 commit 2014199 (PR #10)이 `FolderMutationService` 머지 완료. create/rename/move + audit emit + V5 unique idx 가드 + cycle walk 모두 그린.
- A4.5 commit 1091339에서 `Folder` entity + `FolderRepository` 도입.
- A4.4 commit 675bc4c에서 `PermissionController` + `permission.granted/revoked` emit + ADR #26 close.
- `dev/completed/a4-mutation-service/`에 A4.6 dev-docs 아카이브 완료.

## 본 세션 결정

1. **endpoint shape = docs/02 §7.5 그대로** — 사용자 게이트(2026-04-29). PATCH `/{id}`(rename), POST `/{id}/move`(move). PATCH `/name`/`/parent` 분리 안 함.
2. **응답 envelope = `{ folder: FolderDto }` 단일** — breadcrumb 미포함 (FolderQueryService 부재). PermissionDto 머지 패턴.
3. **ownerId source = principal.user.id** — Request body에 ownerId 받지 않음. 사용자 ROLE에 무관하게 본인 소유로 생성.
4. **auditLevel = Request body 옵션, default 'standard'** — service의 ALLOWED_AUDIT_LEVELS와 일치.
5. **root create/move = ROLE.ADMIN** — parentId/targetParentId == null일 때 별도 가드. ADR #30로 기록.

## 핵심 의존성

- service `FolderMutationService.create(parentId, name, ownerId, auditLevel, actorId)` / `.rename(folderId, newName, actorId)` / `.move(folderId, newParentId, actorId)` — 호출만, 시그니처 변경 0
- `IbizDrivePermissionEvaluator` (A4.3 evaluator)가 SpEL `hasPermission(#id, 'folder', perm)` 평가
- `GlobalExceptionHandler`가 `ResourceNotFoundException`/`IllegalArgumentException`/`AccessDeniedException` 매핑 (`FolderNameConflictException` 신규)

## out of scope (다음 세션)

- folder delete/restore endpoint, file CRUD endpoint, breadcrumb/tree endpoint
- frontend errors mirror 신규 코드 분리 (MOVE_INTO_SELF/DESCENDANT)
- A4.6 통합 E2E 가드 (E2E 트랙은 별도 세션)
