# A13 — Shares ShareDto ↔ permissions Join (tasks)

Last Updated: 2026-05-01

## Phase 상태

- [done] B1 — `ShareDto` 3필드 추가 + factory 갱신
- [done] B2 — `ShareCommandService`: DTO 직접 반환
- [done] B3 — `ShareQueryService`: 배치 join
- [done] B4 — `ShareControllerTest` wire JSON 검증 보강
- [done] B5 — Frontend wire 정합 + UI 복원
- [done] B6 — docs sync (docs/00 §5 ADR #34, docs/01 §14.4, docs/02 §7.9 모두 갱신)
- [in_progress] B7 — PR + closure (rebase onto master 완료, PR 생성/머지는 사용자 승인 필요)

---

## B1 — ShareDto 3필드 추가 + factory 갱신

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/share/ShareDto.java`
- `backend/src/main/java/com/ibizdrive/permission/PermissionRow.java` (subject_type/subject_id/preset getter)

### 원본 코드 참조
- `ShareDto`: 10필드 record + `from(Share)` factory.
- `PermissionRow`: `getSubjectType()` String, `getSubjectId()` UUID nullable, `getPreset()` String.

### 구현 대상
- [ ] `ShareDto` record signature: `subjectType` String NOT NULL, `subjectId` UUID nullable, `preset` String NOT NULL 추가.
- [ ] `from(Share)` 제거.
- [ ] `from(Share, PermissionRow)` 신설 — share + grant로 13필드 채움.
- [ ] javadoc backlink 갱신 — A13 트랙 + ADR #34 참조.

### 검증 참조
- 컴파일 에러로 모든 caller 노출. 각 phase에서 일괄 수정.

### 문서 반영
- B6에서 docs/02 §7.9 ShareDto 응답 스키마 갱신.

---

## B2 — ShareCommandService: DTO 직접 반환

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/share/ShareCommandService.java`
- `backend/src/main/java/com/ibizdrive/share/ShareController.java`
- `backend/src/test/java/com/ibizdrive/share/ShareCommandServiceTest.java`
- `backend/src/test/java/com/ibizdrive/share/ShareControllerTest.java`

### 원본 코드 참조
- `createShares`/`createFolderShares`: 반환 `List<Share>`, loop 내 `PermissionRow grant` 보유.
- `ShareController.create`/`createFolderShare`: 반환된 `List<Share>`를 loop으로 ShareDto 매핑.

### 구현 대상
- [ ] `createShares` 반환형 `List<Share>` → `List<ShareDto>` (loop 내 `ShareDto.from(share, grant)` 직접 추가).
- [ ] `createFolderShares` 동일 변경.
- [ ] `ShareController.create`/`createFolderShare`: 반환된 dtos를 직접 envelope에 넣음.
- [ ] `ShareCommandServiceTest`: 모든 케이스 — 기존 `ShareCommandService`의 반환을 `Share` → `ShareDto` 형태로 단언 변경. 새 3필드 반영도 확인.
- [ ] `ShareControllerTest`: mock 반환을 `List<Share>` → `List<ShareDto>`로 변경.

### 검증 참조
- `cd backend && ./gradlew test --tests 'com.ibizdrive.share.ShareCommandServiceTest'`
- `cd backend && ./gradlew test --tests 'com.ibizdrive.share.ShareControllerTest'`

### 문서 반영
- B6에서 docs/02 §7.9 응답 스키마 일관 갱신.

---

## B3 — ShareQueryService: 배치 join

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/share/ShareQueryService.java`
- `backend/src/main/java/com/ibizdrive/permission/PermissionRepository.java` (findAllById 표준)
- `backend/src/test/java/com/ibizdrive/share/ShareQueryServiceTest.java`

### 원본 코드 참조
- `ShareQueryService`: `ShareRepository`만 의존. `toPage(rows, limit)`에서 `ShareDto.from(s)` 직접 호출.

### 구현 대상
- [ ] `ShareQueryService` 생성자에 `PermissionRepository` 주입 추가.
- [ ] `toPage`: page rows 결정 → `permissionId` set 수집 → `permissionRepository.findAllById(set)` 1회 호출 → `Map<UUID, PermissionRow>` 빌드 → 각 share에 대해 grant lookup 후 `ShareDto.from(share, grant)`.
- [ ] grant null 시 `IllegalStateException("share %s has dangling permission %s")` (V6 FK 보증 — 도달 불가).
- [ ] `ShareQueryServiceTest`: PermissionRepository 추가 mock + when stubbing. 새 3필드 검증 케이스 1건.

### 검증 참조
- `cd backend && ./gradlew test --tests 'com.ibizdrive.share.ShareQueryServiceTest'`

### 문서 반영
- B6에서 docs/02 §7.9에 batch fetch 정책 반영.

---

## B4 — ShareControllerTest wire JSON 검증 보강

### 작업 전 필독
- `backend/src/test/java/com/ibizdrive/share/ShareControllerTest.java`
- B2의 ShareController 변경 결과

### 원본 코드 참조
- 기존 controller test는 `ShareDto` 인스턴스의 `id()`만 단언.

### 구현 대상
- [ ] `create_returns201WithSharesEnvelope` 등 케이스에 `subjectType`/`subjectId`/`preset` 단언 추가.
- [ ] folder 변형도 동일 추가.
- [ ] makeShare/makeFolderShare 헬퍼 → makeDto 헬퍼로 시그니처 자연 정렬 (mock 반환이 ShareDto이므로).

### 검증 참조
- `cd backend && ./gradlew test --tests 'com.ibizdrive.share.ShareControllerTest'`

### 문서 반영
- B6에서 docs/02 §7.9 wire JSON 예시에 새 3필드 노출.

---

## B5 — Frontend wire 정합 + UI 복원

### 작업 전 필독
- `frontend/src/types/share.ts`
- `frontend/src/components/shares/SharesTable.tsx`
- `frontend/src/components/shares/ShareDialog.tsx`
- F5 closure에서 제거된 표시 위치 원복

### 원본 코드 참조
- `ShareDto` interface: 10필드. F5에서 subjectType/subjectId/preset 제거 + revokedAt/revokedBy 노출 + folderId 추가됨.
- `SharesTable`: 3컬럼(Receiver/Created/Expires). preset 컬럼 제거된 상태.
- `ShareDialog`: 기존공유 행 표시는 만료/해제 메타만.

### 구현 대상
- [ ] `frontend/src/types/share.ts` `ShareDto` interface — `subjectType`(`ShareSubjectType`), `subjectId`(`string|null`), `preset`(`SharePreset`) 3필드 추가. JSDoc — A13 join 적용 명시.
- [ ] `SharesTable.tsx` — preset 컬럼(4번째) 재도입. 컬럼 헤더 `'권한'` 라벨, presetLabel helper 도입(read→읽기 등 — 기존 패턴 따라).
- [ ] `ShareDialog.tsx` 기존공유 행 — subject + preset 표시 추가 (기존 만료/해제 표시 옆).
- [ ] 영향 받는 fixture/test (vitest) 일괄 갱신 — wire fixture에 3필드 추가.

### 검증 참조
- `cd frontend && pnpm typecheck && pnpm lint && pnpm test --run`

### 문서 반영
- B6에서 docs/01 §14.4 — SharesTable 4컬럼/ShareDialog 기존공유 표시 복원 명시.

---

## B6 — docs sync

### 작업 전 필독
- `docs/02-backend-data-model.md` §7.9
- `docs/01-frontend-design.md` §14.4
- `docs/00-overview.md` §5 ADR

### 구현 대상
- [ ] docs/02 §7.9 — ShareDto 응답 스키마에 `subjectType`/`subjectId`/`preset` 3필드 추가 + permissions join 메모.
- [ ] docs/01 §14.4 — SharesTable 4컬럼 복원, ShareDialog 기존공유 풍부화 명시. F5 closure에서 backlog로 표기된 항목을 closed 상태로 갱신.
- [ ] docs/00 §5 ADR(존재 시) — A13 closure marker.

### 검증 참조
- 본 단계는 코드 검증 없음. 본문/표 일관성만 확인.

---

## B7 — PR + closure

### 구현 대상
- [ ] PR 생성 (squash-merge 대상). 제목/본문은 F5 closure 패턴 따라 작성.
- [ ] CI green 확인 (backend junit + frontend vitest).
- [ ] master squash-merge.
- [ ] master에서 closure commit (`docs/progress.md` 최상단 entry + dev-docs archive: active → completed).
- [ ] dev-docs 디렉터리 이동: `dev/active/a13-shares-permissions-join/` → `dev/completed/a13-shares-permissions-join/`.

### 검증 참조
- `gh pr view <id>` CI 상태.
