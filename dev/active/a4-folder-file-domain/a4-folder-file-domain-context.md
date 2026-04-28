---
Last Updated: 2026-04-29
Status: 📋 BOOTSTRAP COMMITTED — A4-data 트랙 진입 대기 (다음 세션)
---

# A4 — Context

## SESSION PROGRESS

| 단계 | 상태 | 산출물 |
|---|---|---|
| 1. A3 archive 확인 (origin/master) | ✅ | `dev/completed/a3-permission-matrix/` 존재, master HEAD `6f0820d` |
| 2. A4 dev-docs bootstrap | ✅ | 본 3파일 |
| 3. **사용자 plan 리뷰 게이트** | ✅ | GO (옵션 B 분할 + ADR #28/#29 확정, 2026-04-29) |
| 4. ADR #27/#28/#29 등록 + docs/03 §3.4 마커 | ✅ | docs/00 §5 + docs/03 §3.4 patch (본 세션) |
| 5. **bootstrap commit + 게이트 1 보고 + 세션 종료** | 🏁 | 본 세션 종료 |
| --- A4-data 트랙 (다음 세션) --- | | |
| 6. A4.0 docs/02 §2.3/§2.6/§7.10 정합 patch (no-code) | ⏳ | — |
| 7. A4.1 V5 마이그레이션 | ⏳ | — |
| 8. A4.2 entity + repo + NormalizeUtil + frontend mirror | ⏳ | — |
| 9. **A4-data PR 머지** | ⏳ | — |
| 10. **게이트 2 (A4-controllers 진입 직전)** | ⏳ | — |
| --- A4-controllers 트랙 (A4-data 머지 후) --- | | |
| 11. A4.3 evaluator 내부 교체 | ⏳ | — |
| 12. A4.4 권한 endpoint + emit | ⏳ | — |
| 13. A4.5 폴더/파일 CRUD MVP | ⏳ | — |
| 14. A4.6 E2E + A2/A3 회귀 가드 | ⏳ | — |
| 15. **게이트 3 (A4-controllers PR 생성 직전)** | ⏳ | — |
| 16. A4.7 closure (PR + archive) | ⏳ | — |

## Current Execution Contract

- **현재 worktree**: `.claude/worktrees/a4-folder-file-domain` (bootstrap 세션 종료 후 다음 세션 동일 worktree에서 A4-data 트랙 시작)
- **branch**: `feature/a4-folder-file-domain` (A4-data 트랙). A4-controllers 트랙은 A4-data master 머지 후 새 worktree `.claude/worktrees/a4-controllers` (or 동급)에서 분기.
- **HEAD (bootstrap 후)**: 1 commit 추가됨 — `docs(a4): bootstrap dev-docs + ADR(s) for A4 scope decisions`
- **운영 모드**: 자율 실행 ON
- **컨텍스트 임계값**: 60/70/75/80% 진입 시 핸드오프 (`feedback_context_limit.md` 9줄 박스)
- **TDD**: RED → GREEN → REFACTOR (모든 phase)
- **DB 변경 절차**: V5__ 마이그레이션 (folders + files + file_versions + permissions). audit_log V4 REVOKE 정책 무충돌.
- **CLAUDE.md §3 11원칙**: 충돌 시 즉시 중단. 특히:
  - 원칙 6 (DB 제약이 진실): UNIQUE (parent_id|folder_id, normalized_name) WHERE deleted_at IS NULL + root는 COALESCE 보강 (ADR #27 D3는 ADR #27 본문에는 미포함, A4.1 commit에서 명시 — 갱신 본 plan에서는 보장사항으로 유지)
  - 원칙 7 (트랜잭션 + FOR UPDATE): 모든 mutation
  - 원칙 8 (audit append-only): REVOKE 정책 우회 금지
  - 원칙 9 (storage_key UUID): file_versions.storage_key는 UUID, S3 객체 키에 원본 파일명 절대 미포함
  - 원칙 10 (백엔드 재검증): 프론트 권한은 UX
  - 원칙 11 (정규화 동일 로직): NormalizeUtil ↔ src/lib/normalize.ts fixtures 공유
- **격리 정책**: `backup/codex-d99cd2a`, `stash@{0}`, local master, 다른 worktree 5개는 본/다음 세션에서 일절 변경 금지. backup의 V5/엔티티는 read-only 참고 (cherry-pick 금지).

## 현재 active task

**🏁 bootstrap 종료 후 → 다음 세션은 A4-data 트랙 A4.0 (docs/02 정합 patch) 진입.**

## 다음 세션 읽기 순서

1. `dev/active/a4-folder-file-domain/a4-folder-file-domain-plan.md` — 옵션 B 분할 + DoD 11항목 + R1~R7 위험 + ADR #27/#28/#29 명시
2. `dev/active/a4-folder-file-domain/a4-folder-file-domain-tasks.md` — A4-data / A4-controllers 두 트랙 체크리스트
3. `docs/00-overview.md` §5 ADR #27/#28/#29 (line 159~161 부근) — A4 분할/permissions/file_versions 결정
4. `docs/03-security-compliance.md` §3.4 (line 351~356) — `(v1 deferred — ADR #28)` 마커 확인
5. `docs/02-backend-data-model.md` §2.3 (line 90~118), §2.4 (120~150), §2.5 (152~180), §2.6 (182~209) — 4테이블 스키마
6. `docs/02-backend-data-model.md` §6.3 (611), §6.4 (637), §6.5 (660), §6.7 (720) — 트랜잭션 패턴
7. `docs/02-backend-data-model.md` §7.5 (880), §7.6 (923), §7.10 (1047) — endpoint Guard 표
8. `docs/03-security-compliance.md` §3.1 (305), §3.6 (381) — 권한 enum / 403 envelope
9. `dev/completed/a3-permission-matrix/a3-permission-matrix-context.md` line 54~64 (A4 진입점), line 96~104 (A3 사전 락 7항목)
10. `backend/src/main/java/com/ibizdrive/permission/IbizDrivePermissionEvaluator.java` — A4.3 내부 교체 대상 (A4-controllers 트랙 진입 시)

## 핵심 파일과 역할 (예정 / 기존)

| 파일 | 역할 | 트랙 |
|---|---|---|
| `backend/src/main/resources/db/migration/V5__folders_files_permissions.sql` | 4테이블 + UNIQUE/CHECK + COALESCE 보강 | A4-data / A4.1 |
| `backend/src/main/java/com/ibizdrive/folder/Folder.java` | JPA 엔티티 | A4-data / A4.2 |
| `backend/src/main/java/com/ibizdrive/folder/FolderRepository.java` | repo + lock query | A4-data / A4.2 |
| `backend/src/main/java/com/ibizdrive/file/FileItem.java` | JPA 엔티티 (이름 충돌 회피) | A4-data / A4.2 |
| `backend/src/main/java/com/ibizdrive/file/FileRepository.java` | repo + lock query | A4-data / A4.2 |
| ~~`backend/src/main/java/com/ibizdrive/file/FileVersion.java`~~ | A5 이월 (ADR #29) | A5 |
| `backend/src/main/java/com/ibizdrive/permission/PermissionRow.java` | DB row 엔티티 (enum `Permission`과 이름 분리) | A4-data / A4.2 |
| `backend/src/main/java/com/ibizdrive/permission/PermissionRepository.java` | repo + 재귀 CTE `findEffective(...)` | A4-data / A4.2 |
| `backend/src/main/java/com/ibizdrive/common/normalize/NormalizeUtil.java` | NFC + lowercase + collapse | A4-data / A4.2 |
| `frontend/src/lib/normalize.ts` | 프론트 정규화 — fixtures 공유 (ADR #16) | A4-data / A4.2 |
| `backend/src/main/java/com/ibizdrive/permission/IbizDrivePermissionEvaluator.java` | 내부 교체 (role bypass + grant 우선 + 상속) | A4-controllers / A4.3 |
| `backend/src/main/java/com/ibizdrive/permission/PermissionService.java` | `check()` 내부만 교체, `changeRole()` 보존 | A4-controllers / A4.3 |
| `backend/src/main/java/com/ibizdrive/permission/PermissionController.java` | 4 endpoint | A4-controllers / A4.4 |
| `backend/src/main/java/com/ibizdrive/audit/event/PermissionAuditListener.java` | `permission.granted/revoked` emit | A4-controllers / A4.4 |
| `backend/src/main/java/com/ibizdrive/folder/FolderController.java` | 7 endpoint | A4-controllers / A4.5 |
| `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java` | TX + FOR UPDATE + 충돌 검사 | A4-controllers / A4.5 |
| `backend/src/main/java/com/ibizdrive/file/FileController.java` | 5 endpoint + `/api/folders/:id/files` | A4-controllers / A4.5 |
| `backend/src/main/java/com/ibizdrive/file/FileMutationService.java` | TX + FOR UPDATE | A4-controllers / A4.5 |
| `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java` | RENAME/RESTORE/MOVE 충돌 envelope | A4-controllers / A4.5 |
| `frontend/src/lib/errors.ts` | 신규 에러 코드 1:1 mirror | A4-controllers / A4.5 |
| `docs/02-backend-data-model.md` §2.3/§2.6/§7.10 | 정합 patch | A4-data / A4.0 (별도 세션) |
| `docs/03-security-compliance.md` §3.4 | (v1 deferred) 마커 | ✅ 본 세션 완료 |
| `docs/00-overview.md` §5 ADR #27/#28/#29 | 신설 | ✅ 본 세션 완료 |

## 중요한 의사결정 (사전 락 — 확정)

1. **A4 단위 분할 = PR 2개 (ADR #27)** — A4-data + A4-controllers, 의존 단방향.
2. **permissions semantics = preset 단일 컬럼 (ADR #28)** — 명시 deny v1.x 이월, evaluator는 grant 우선 lookup.
3. **file_versions A5 이월 (ADR #29)** — V5 schema는 테이블만 도입, entity/repo/CRUD endpoint는 A5에서.
4. **LTREE 미도입** — folders는 parent_id 단일 컬럼. 후손 검사는 재귀 CTE. (사용자 prompt의 "LTREE 채택"은 docs/02 §2.3과 어긋나 거부됨, A4-data 트랙 A4.0 docs patch에 명시.)
5. **root folder UNIQUE = COALESCE(parent_id, ZERO_UUID) 보강** — backup V5에서 검증된 패턴. A4.1 V5 마이그레이션 + A4.0 docs §2.3 본문 보강 1줄.
6. **A3에서 보존**: `Permission` 9-value enum, `Preset` 5-value enum, `PermissionService.check` 시그니처, 403 envelope, `effectivePermissionsCacheKey` SHA-256 hex 16자 형식, `permission.changed` audit emit, ADMIN bypass / PURGE = ROLE only 가드.
7. **codex backup 격리**: `backup/codex-d99cd2a`의 V5는 구조 참고만. cherry-pick 금지. `com.ibizdrive.security.Permission` 절대 채택 금지 — `com.ibizdrive.permission.Permission` (A3 master) 단일 진실.
8. **out-of-scope (A5+)**: tus 업로드, 검색, 공유, 휴지통 페이지, 부서 LTREE, 권한 frontend UI, file_versions entity/CRUD.
9. **테스트 패턴**: A2/A3 확립한 `@SpringBootTest` + Testcontainers + HttpClient5 그대로 재사용.
10. **dev/process ownership 격리**: A4-data 트랙 진입 전 master 측 `20260428-a3-permission-a4`, `20260428-a3-folder-mutation-service` 정리 여부 재확인 (게이트 2).

## 빠른 재개 안내 (다음 세션용 — A4-data 트랙 A4.0)

```bash
# 1. 워크트리 진입
cd C:/project/IbizDrive/.claude/worktrees/a4-folder-file-domain

# 2. 상태 확인
git log -1 --oneline    # docs(a4): bootstrap dev-docs + ADR(s)... (본 세션 commit)
git status              # clean (또는 dev/process/ untracked만)

# 3. A4.0 docs 정합 patch (no-code) 진입
#    - docs/02 §2.3 folders 본문에 root parent UNIQUE = COALESCE 보강 1줄
#    - docs/02 §2.6 permissions 본문에 "preset 단일 (ADR #28)" 주석 1줄
#    - docs/02 §7.10 권한 Guard 컬럼 'ADMIN' → 'PERMISSION_ADMIN' alias 정합
#    - commit: docs(A4.0): folder/file 도메인 정합 (ADR #27/#28/#29 반영)
#    - 코드 변경 0줄 유지

# 4. A4.0 직후 → 게이트 1 보고는 본 세션에서 이미 완료.
#    A4.0 patch commit 직후 → A4.1 V5 마이그레이션 자율 진입 (게이트 없음, A4-data PR 머지 시 게이트 2)
```

## 게이트 보고 형식 (참고)

게이트 2 (A4-data PR 머지 직후 / A4-controllers 진입 직전):
- V5 + entity GREEN 검증
- A2 audit append-only 회귀 0
- A4-controllers worktree 분기 GO 여부
- master 측 active 세션 ownership 재확인

게이트 3 (A4.6 직후 / A4-controllers PR 생성 직전):
- backend / frontend test count
- A2/A3 회귀 가드 GREEN
- DoD 11/11 (CI 외 10개 로컬 GREEN)
- PR 생성 GO 여부
