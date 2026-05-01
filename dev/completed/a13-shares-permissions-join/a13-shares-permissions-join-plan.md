# A13 — Shares ShareDto ↔ permissions Join (plan)

Last Updated: 2026-05-01

## 요약

Backend `ShareDto`에 `subjectType`/`subjectId`/`preset` 3필드를 `permissions` join으로 복원하여 frontend `ShareDialog` 기존공유 행과 `SharesTable` preset 컬럼 표시를 살린다. F5 closure(2026-05-01)에서 명시된 backend backlog 단일 항목.

## 현재 상태 분석

- F5 closure에서 frontend types는 backend `ShareDto` record(10필드)와 wire 1:1 정합화 — `subjectType/subjectId/preset` 필드가 wire에서 누락(없는 정보).
- `SharesTable`은 preset 컬럼이 제거된 3컬럼 상태(`Receiver`/`Created`/`Expires`).
- `ShareDialog` 기존공유 행은 만료/해제 메타만 표시 (subject/preset 풀이 없음).
- DB 구조: `permissions` row가 `subject_type`/`subject_id`/`preset`을 보유. `shares.permission_id` FK로 1:1 매칭.
- `ShareCommandService`의 create 흐름은 `PermissionRow grant`를 이미 scope에 보유 → 추가 query 불필요.
- `ShareQueryService`의 by-me/with-me는 share 단일 쿼리 후 별도로 permissions를 fetch해야 함.

## 목표 상태

- `ShareDto` record가 13필드(현 10 + 3): `subjectType`(String), `subjectId`(UUID, nullable when 'everyone'), `preset`(String).
- `ShareCommandService.createShares/createFolderShares` 응답 DTO에 위 3필드가 채워진다.
- `ShareQueryService.listByMe/listWithMe` 응답 DTO에도 위 3필드가 채워진다 (batch fetch — N+1 회피).
- Frontend `ShareDto` interface가 backend wire와 1:1 정합 복원. `SharesTable`에 preset 컬럼 재도입(4컬럼). `ShareDialog` 기존공유 행에 preset 표시.
- `ShareControllerTest`에 새 3필드 wire JSON 검증 보강.

## Phase별 실행 지도

### Phase B1 — `ShareDto` 3필드 추가 + factory 갱신
- `ShareDto` record signature 확장 (`subjectType`, `subjectId`, `preset`).
- `from(Share, PermissionRow)` factory 도입(기존 `from(Share)` 제거 — 모든 caller가 grant를 보유).
- 변경 caller: `ShareCommandService` (2곳), `ShareQueryService` (toPage helper).

### Phase B2 — `ShareCommandService`: DTO 직접 반환
- `createShares` / `createFolderShares` 반환형을 `List<Share>` → `List<ShareDto>` 변경 (loop 안의 `grant`를 그대로 사용).
- `ShareController`의 두 POST 매핑은 `dtos`를 직접 envelope에 넣음.
- 호출자 변경: `ShareController` 두 메서드, `ShareCommandServiceTest`, `ShareControllerTest` (mock 반환형).

### Phase B3 — `ShareQueryService`: 배치 join
- repo 결과 `List<Share>`에서 `permissionId` 수집 → `permissionRepository.findAllById(ids)` 1회 호출 → `Map<UUID,PermissionRow>` 빌드.
- `toPage`가 share 별 매핑된 grant로 `ShareDto.from(share, grant)` 호출.
- 만약 grant가 없으면(이론상 race — share active이지만 permission cascade 미완료) 방어적 fallback: skip 또는 placeholder. → 결정: `IllegalStateException` (V6 FK 보증, 운영상 발생 불가).
- 의존성 추가: `PermissionRepository` 주입.
- `ShareQueryServiceTest`: 새 mock(PermissionRepository) + 검증 보강.

### Phase B4 — `ShareControllerTest` wire JSON 검증
- 신규 단위 테스트 또는 기존 케이스 강화: `ShareDto` 13필드가 응답 envelope에 포함되는지 직접 단언.
- `subjectType`/`subjectId`/`preset` 필드가 record로 직렬화되는지 jackson round-trip은 PR 내 별도 검증 — 단위 테스트는 ShareDto 인스턴스 필드 단언으로 충분.

### Phase B5 — Frontend wire 정합 + UI 복원
- `frontend/src/types/share.ts` `ShareDto` interface 3필드 추가, JSDoc backlink update.
- `SharesTable.tsx`: preset 컬럼 재도입, 컬럼 4개로 복귀. `presetLabel` helper.
- `ShareDialog.tsx` 기존공유 행: `${subjectType}` / `${preset}` 표시.
- 영향 받는 fixture/test: 검색 후 일괄 갱신.

### Phase B6 — docs sync
- `docs/02 §7.9` ShareDto 응답 스키마에 3필드 명시.
- `docs/01 §14.4` SharesTable 4컬럼 복원, ShareDialog 기존공유 행 풍부화 명시.
- `docs/00 §5` ADR/backlog: A13 closure marker.

### Phase B7 — PR + closure
- 단일 squash-merge PR.
- `master` closure 커밋 + dev-docs archive (active → completed).

## Acceptance Criteria

- [ ] `ShareDto.subjectType` is non-null wire field, `ShareDto.preset` is non-null wire field.
- [ ] `ShareDto.subjectId` is null when `subjectType=='everyone'`, UUID otherwise.
- [ ] by-me/with-me 응답이 share row 당 join된 grant 메타를 포함.
- [ ] 새 share 생성 응답이 동일 메타 포함.
- [ ] `SharesTable`에 preset 컬럼 표시.
- [ ] `ShareDialog` 기존공유 행에 subject/preset 표시.
- [ ] backend `./gradlew test` GREEN, frontend `pnpm test` GREEN.
- [ ] DB 스키마 변경 0.

## 검증 게이트

- backend: ShareCommandServiceTest, ShareQueryServiceTest, ShareControllerTest 전부 GREEN.
- frontend: vitest 전 케이스 GREEN. fixture 일괄 갱신 후 회귀 0.
- typecheck/lint 통과.

## 리스크와 완화

- **N+1**: query에서 permission fetch 분리 → batch fetch(`findAllById`)로 1쿼리.
- **Race**: share active인데 permission 부재 시 — V6 FK CASCADE로 정상상태 보증. fallback은 `IllegalStateException`(운영상 발생 불가, 발생 시 즉시 에러로 노출).
- **테스트 fixture 폭증**: backend ShareDto.from(share) 제거가 다수 테스트 영향. 컴파일 에러로 한 번에 수면 위로 — 일괄 수정.
