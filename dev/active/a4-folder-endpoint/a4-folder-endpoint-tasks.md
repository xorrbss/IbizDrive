---
Last Updated: 2026-04-29
Status: 🟢 IMPLEMENTATION COMPLETE — PR open 단계
---

# A4.7 — Tasks

## phase 1 — docs sync ✅ commit `e040656`

- [x] docs/00 §5 ADR #30 추가
- [x] docs/02 §7.5 root 케이스 한 줄 보강
- [x] dev/active/a4-folder-endpoint/{plan,context,tasks} 부트스트랩

## phase 2 — DTO + handler ✅

- [x] FolderDto / CreateFolderRequest / RenameFolderRequest / MoveFolderRequest
- [x] GlobalExceptionHandler `FolderNameConflictException → 409 RENAME_CONFLICT`

## phase 3 — controller + test ✅ commit `fd8f2d6`

- [x] FolderController (POST /, PATCH /{id}, POST /{id}/move + SpEL 삼항)
- [x] FolderControllerTest 9 케이스 (PermissionControllerTest 패턴)

## phase 4 — verify ✅

- [x] `./gradlew test` 357 tests / 0 failures / 90 skipped (Docker 슬라이스 — CI에서 실행)
- [x] self-review (SpEL `-parameters` 의존성 OK — Spring Boot 3.2+ 기본 활성)

## phase 5 — PR + 머지 게이트 ⏳

- [ ] push feature/a4-folder-endpoint
- [ ] PR open
- [ ] CI green 대기
- [ ] 머지 OK 게이트 보고 (사용자 승인)

## Acceptance Criteria

- [x] 3 endpoint + 가드 정의, FolderControllerTest 9 케이스 GREEN
- [x] FolderNameConflictException → 409 RENAME_CONFLICT envelope
- [x] FolderMutationService 시그니처 0변경
- [x] V5 schema / audit 정책 / A2/A3 회귀 0 (기존 357 tests 통과)
- [x] docs/00 ADR #30 + docs/02 §7.5 root 한 줄 동기화
