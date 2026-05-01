---
Last Updated: 2026-05-02
---


# A15 Context — Storage 모듈 + 파일 업로드/다운로드 endpoint

## SESSION PROGRESS

| Phase | Status | Note |
|---|---|---|
| A15.0 bootstrap | ✅ done | dev-docs 3파일 commit `b98b044`. ADR #13 재정정 초안은 A15.7 closure에 포함. |
| A15.1 StorageClient + LocalFs | ✅ done | interface + LocalFs impl + properties + 9 RED→GREEN tests. `ibizdrive.storage.*` 네임스페이스 (existing convention). |
| A15.2 FileUploadService RED | ✅ done | UploadResolution + UploadResult + service skeleton(throws UOE) + 7 Testcontainers tests (Docker 미가용 시 skip). audit emission은 listener 미도입 — file/ 패키지 기존 convention(직접 emitAudit) 답습. |
| A15.3 FileUploadService GREEN | ✅ done | upload(...) 구현 (folder lock + conflict 이중 가드 + storage write + INSERT files+versions + current_version_id + emitAudit). FileVersionRepository.findMaxVersionNumberByFileId 추가. FileRepository.lockActiveByFolderAndNormalizedName 추가. RENAME suffix `(N)` 자동. |
| A15.4 POST /api/files | ✅ done | FileUploadController + UploadResponse DTO + multipart 활성화 (max 100MB). 8/8 controller tests GREEN. resolution wire format `new_version`/`rename`/null. |
| A15.5 GET /api/files/:id/download | ✅ done | FileDownloadService + FileDownloadController + DownloadHandle. RFC 5987 Content-Disposition (filename + filename*=UTF-8'') + ETag(versionId) + Content-Type fallback (octet-stream). audit FILE_DOWNLOADED. 7 service + 6 controller tests GREEN. 권한 = file READ. |
| A15.6 Frontend api.uploadFile 실 XHR | ✅ done | `api.uploadFile` → `new XMLHttpRequest()` + `POST /api/files` (multipart) + `withCredentials=true`. fakeXhr.ts/fakeXhr.test.ts 삭제. useUpload는 XMLHttpRequest 타입으로 전환 + 409 envelope `{error.details:{fileId,fileName}}` 파싱(폴백 conflictWith undefined). MOCK_FILES side-effect 제거(backend authoritative). 신규 api.upload.test.ts 4개 + useUpload.test.ts 9개(MockXHR global stub). 527/527 GREEN, typecheck/lint clean. |
| A15.7 closure | ✅ done | ADR #13 supersede + ADR #36 신규(`docs/00 §5`), `docs/02 §7.6` 표 + 신규 §7.6.1 multipart/download spec, §7.7 supersede 마커, `docs/progress.md` A15 closure entry, dev-docs archive. |

## Current Execution Contract

- **Mode**: 자율 실행 (autonomous). Destructive 액션만 게이트 (push, merge, force-push, hard reset, branch delete, 워크트리 remove). 일반 commit은 자동.
- **TDD**: 각 phase RED → GREEN → REFACTOR. 새 production 코드는 실패 테스트 선행.
- **Atomic commit**: phase 단위 1커밋 원칙. phase 내 전환 (RED→GREEN) 시 commit 분리.
- **Context budget**: 60% staging / 70% pause / 75% handoff. 본 작업은 dev-docs 부트스트랩까지 1 commit으로 닫고 A15.1 진입은 별 commit으로 분리.
- **검증**: backend `./gradlew test`, frontend `pnpm test --run` + `pnpm typecheck` + `pnpm lint`. phase 종료마다 양쪽 GREEN 확인.
- **Anti-hang**: 동일 명령 5회 반복 + 60분 무진전 시 self-kill + 보고. 도구 응답 무시 금지.

## 현재 active task

**전 phase 완료 — 트랙 종료.** 잔여 작업은 PR/squash-merge(게이트). dev-docs는 closure commit 후 `dev/completed/a15-file-upload-download/`로 이동.

## 다음 세션 읽기 순서

1. `dev/active/a15-file-upload-download/a15-file-upload-download-context.md` (본 파일) — SESSION PROGRESS / 다음 active phase 확인.
2. `dev/active/a15-file-upload-download/a15-file-upload-download-tasks.md` — 미완료 task의 참조 블록 (작업 전 필독 / 원본 코드 / 구현 대상 / 검증 / 문서 반영).
3. `dev/active/a15-file-upload-download/a15-file-upload-download-plan.md` — phase 별 acceptance criteria + 리스크.
4. `docs/00-overview.md §5` ADR — 특히 #5 (storage_key UUID), #7 (multipart MVP — superseded by #13), #13 (tus — 본 트랙에서 재정정 예정), #16 (정규화 fixtures), #24 (audit AOP+listener), #29 (POST /versions A6+ deferred).
5. `docs/02-backend-data-model.md §6.1` (업로드 트랜잭션) + `§7.6` (Files endpoint) + `§7.7` (tus — 재정정 대상).
6. `backend/src/main/java/com/ibizdrive/file/FileMutationService.java` — rename/move/delete 패턴 답습용 reference.

## 핵심 파일과 역할

### Backend (수정/신설 예정)
- **신설**: `backend/src/main/java/com/ibizdrive/storage/StorageClient.java` (interface)
- **신설**: `backend/src/main/java/com/ibizdrive/storage/LocalFsStorageClient.java` (impl)
- **신설**: `backend/src/main/java/com/ibizdrive/storage/StorageProperties.java` (`@ConfigurationProperties("app.storage")`)
- **신설**: `backend/src/main/java/com/ibizdrive/file/FileUploadService.java`
- **신설**: `backend/src/main/java/com/ibizdrive/file/FileUploadController.java` (POST /api/files)
- **신설**: `backend/src/main/java/com/ibizdrive/file/FileDownloadController.java` (GET /api/files/:id/download) — 또는 `FileController` 안에 메서드 추가 (단일 controller 응집)
- **취소(2026-05-01)**: `FileUploadedEvent.java` / `FileDownloadedEvent.java` / `FileAuditListener.java` — file/ 패키지의 기존 emitAudit convention 답습으로 대체.
- **신설(A15.2 done)**: `backend/src/main/java/com/ibizdrive/file/UploadResolution.java` enum (NEW_VERSION, RENAME)
- **신설(A15.2 done)**: `backend/src/main/java/com/ibizdrive/file/UploadResult.java` record (FileItem file, FileVersion version, boolean newFile)
- **수정**: `backend/src/main/java/com/ibizdrive/file/FileRepository.java` — `lockActiveFolderById`, `existsActiveByFolderAndNormalizedName` (이미 있음), `findMaxVersionNumberByFileId` (file_versions repository 활용 가능)
- **수정**: `backend/src/main/resources/application.yml` — `app.storage.{type, root}` 추가 + `spring.servlet.multipart.{max-file-size, max-request-size}` 명시
- **신설**: `backend/src/test/java/com/ibizdrive/storage/LocalFsStorageClientTest.java`
- **신설**: `backend/src/test/java/com/ibizdrive/file/FileUploadServiceTest.java`
- **신설**: `backend/src/test/java/com/ibizdrive/file/FileUploadControllerTest.java`
- **신설**: `backend/src/test/java/com/ibizdrive/file/FileDownloadControllerTest.java`

### Frontend (수정 예정)
- **수정**: `frontend/src/lib/api.ts` — uploadFile 분기 교체 (FakeXHR → XMLHttpRequest)
- **삭제 검토**: `frontend/src/lib/fakeXhr.ts` — 영향 grep 후 결정
- **검증**: `frontend/src/components/files/UploadDock.tsx`, `frontend/src/stores/upload.ts`, `frontend/src/components/files/ConflictDialog.tsx` — 인터페이스 변경 0 확인
- **신설/수정**: `frontend/src/lib/api.upload.test.ts` (신규) 또는 기존 api 테스트 파일 확장

### Docs
- **수정**: `docs/00-overview.md §5` — ADR #13 재정정 메모 (closure marker), 신규 ADR (가칭 #36) — A15 결정 (storage abstraction + multipart MVP).
- **수정**: `docs/02-backend-data-model.md §6.1`, `§7.6`, `§7.7` (multipart 본문 spec)
- **수정**: `docs/progress.md` — A15 closure entry 최상단

## 중요한 의사결정

1. **MVP = 단일-POST multipart** (tus 프로토콜 v1.x 재이월). ADR #13 재정정. KISS+YAGNI.
2. **Storage abstraction = `StorageClient` interface + `LocalFsStorageClient` MVP impl**. S3 impl은 v1.x. AWS SDK v2 의존성 추가 안 함.
3. **resolution = `new_version` | `rename` | unset(409)**. M5 frontend 인터페이스 1:1.
4. **Audit emission = file/ 패키지 기존 convention(직접 `auditService.record()` via `emitAudit` helper)** 답습 (FileMutationService:298-315). 2026-05-01 결정: share/permission/auth는 listener 패턴이지만 file/ 패키지 내 일관성을 위해 직접 호출 채택. KISS+§3 원칙 — 동일 패키지 안에 두 패턴 혼재 회피.
5. **권한**: 업로드 = `hasPermission(#req.folderId, 'folder', 'UPLOAD')`, 다운로드 = `hasPermission(#id, 'file', 'READ')` (DOWNLOAD enum 별도 — docs/03 §3 매트릭스 재확인 후 결정. MVP는 READ로 단순화 강한 후보).
6. **storage orphan**: MVP 한정 알려진 한계. cleanup job 별도 트랙.
7. **인증**: 기존 `@AuthenticationPrincipal` 패턴 그대로.
8. **객체 키**: `{YYYY}/{MM}/{storage_key UUID}` (ADR #5 정합).
9. **Frontend FakeXHR**: A15.6에서 import 의존 grep 후 삭제 또는 dev-only stub으로 격리.

## 빠른 재개 안내

```bash
# 1. 워크트리 이동
cd C:/project/IbizDrive/.claude/worktrees/a15-file-upload-download

# 2. 현재 phase 확인
cat dev/active/a15-file-upload-download/a15-file-upload-download-context.md

# 3. 다음 phase 진입 (예: A15.1)
#   tasks.md의 A15.1 참조 블록 → RED 테스트부터 작성

# 4. 검증
cd backend && ./gradlew test
cd ../frontend && pnpm test --run && pnpm typecheck && pnpm lint
```

## Worktree 정보

- 경로: `.claude/worktrees/a15-file-upload-download`
- 브랜치: `feature/a15-file-upload-download`
- Base: `master c576bb4` (F6 closure)
- 원격: `origin` (push는 게이트)
