---
Last Updated: 2026-05-08
Status: design-only phase. v2.x 진입 시 §B 부터 실행.
---

# Tasks — legal-hold-design

## §A. 설계 정합화 (현재 트랙 — 코드 0줄)

- [x] A.1 ADR #46 추가 (docs/00 §5) — 데이터 모델 = 하이브리드
- [x] A.2 ADR #31 보강 — `WHERE legal_hold IS NOT TRUE` forward-reference 정합화 (cache flag 출처 = ADR #46)
- [x] A.3 docs/02 §2.10 `legal_holds` 스키마 reserve + 인덱스 4개 + 동기화 invariant 명시
- [x] A.4 docs/02 §2.11 ER 요약에 legal_holds edge 추가
- [x] A.5 docs/02 §8 에러 코드 2종 추가 — `LEGAL_HOLD_VIOLATION` 423, `LEGAL_HOLD_RECENTLY_RELEASED` 409
- [x] A.6 docs/03 §3.1 `MANAGE_LEGAL_HOLD` 권한 enum 추가
- [x] A.7 docs/03 §3.2.5 ROLE 매트릭스 — ADMIN에 `MANAGE_LEGAL_HOLD` 명시
- [x] A.8 docs/03 §4.1 audit enum 4종 정렬 — placeholder 2종 활성화 noted + 신규 2종 추가
- [x] A.9 docs/03 §6.3 본문화 — 정책/데이터 모델/차단 매트릭스/API/audit/권한/30일 락/만료 cron/dual-approval 10개 sub-section
- [x] A.10 docs/04 §10 본문화 — 대상 지정/Hold 동작/해제 워크플로/만료/관리자 페이지/운영 런북 7개 sub-section
- [x] A.11 dev/active/legal-hold-design/ plan/context/tasks 3파일 작성
- [x] A.12 docs/progress.md 갱신 — 본 세션 핵심 결정 기록 (2026-05-08 entry, 최상단)

## §B. v2.x 활성화 (현재 미실행 — 트리거: 외부 출시 + 컴플라이언스 도메인 진입)

### B.1 스키마 + 권한

- [ ] V_ 마이그레이션: `legal_holds` 테이블 + `files.legal_hold` + `folders.legal_hold` + 인덱스 4개 (docs/02 §2.10)
- [ ] `Permission.MANAGE_LEGAL_HOLD` enum 추가 + `IbizDrivePermissionEvaluator` ROLE=ADMIN 매핑
- [ ] `frontend/src/types/permission.ts` mirror 갱신
- [ ] `AuditEventType` 신규 2종 추가 (`admin.legal_hold.expired`, `admin.legal_hold.violation_blocked`)
- [ ] `frontend/src/types/audit.ts` mirror 갱신

### B.2 도메인 (place / release / dual-approval / expire)

- [ ] `LegalHold` 엔티티 + `LegalHoldRepository`
  - `findActiveByTarget(type, id)` — active hold 조회 (mutation 가드용 fast lookup)
  - `findRecentlyReleasedByTarget(type, id, since)` — 30일 락 검사
  - `findExpiredActiveIds(now, Pageable)` — expiration cron 후보 스캔
  - `lockById(holdId)` — pessimistic lock (cron/release 직렬화)
- [ ] `LegalHoldService`
  - `placeLegalHold(request)` — 사유/대상/만료 검증 + 30일 락 검사 + 메타 INSERT + cache flag cascade UPDATE + `LegalHoldPlacedEvent` publish
  - `releaseLegalHold(holdId, reason)` — 단일 admin 모드면 즉시 종료, dual-approval 모드면 status=pending
  - `approveLegalHold(holdId, decision, comment)` — secondary admin 승인/거부 (self-approval 검증)
  - `expireLegalHold(holdId)` — cron이 호출하는 시스템 release (actor=NULL)
  - cascade 헬퍼: `cascadeFlagFor(target)` — folder/user 대상 시 후손/소유 file/folder의 cache flag set/clear
- [ ] `LegalHoldExpirationJob` — `@Scheduled(cron)`, share-expired-cron 동형 (default enabled=false)
- [ ] `LegalHoldEvent` records: `Placed`, `Released`, `Expired`, `ViolationBlocked`
- [ ] `LegalHoldAuditListener` — `@TransactionalEventListener(phase=AFTER_COMMIT)`, `REQUIRES_NEW`로 audit_log INSERT

### B.3 가드 (mutation entry 9곳)

- [ ] `LegalHoldGuard.checkOrThrow(targetType, targetId)` — cache flag 검사, TRUE면 `LEGAL_HOLD_VIOLATION` 423 throw + `LegalHoldViolationBlockedEvent` publish
- [ ] 적용 지점:
  - [ ] `FileMutationService.softDelete` (휴지통 이동)
  - [ ] `FileMutationService.restore`
  - [ ] `FileMutationService.rename` (PATCH)
  - [ ] `FileMutationService.move`
  - [ ] `FileVersionMutationService.createVersion` (POST versions / NEW_VERSION 분기)
  - [ ] `FileVersionMutationService.restoreVersion`
  - [ ] `FolderMutationService.softDelete` / `restore` / `rename` / `move`
  - [ ] `TrashService.purge` (manual)
  - [ ] `ShareCommandService.create*` / `revoke*`
  - [ ] `PermissionService.grant` / `revoke`
  - [ ] `FileUploadService.upload` (folder 대상이 hold면 신규 file 거부)
- [ ] `HardPurgeService.findExpiredCandidates` SQL에 `AND legal_hold IS NOT TRUE` 1줄 추가 (ADR #31 close)

### B.4 Controller / endpoint

- [ ] `LegalHoldController` (`/api/admin/legal-holds`)
  - `POST` (place) — `@PreAuthorize("hasAuthority('MANAGE_LEGAL_HOLD')")`
  - `DELETE /:holdId` (release)
  - `POST /:holdId/approve` (dual-approval secondary)
  - `GET` (목록 + 필터)
  - `GET /:holdId` (상세)
  - `GET /by-target` (mutation 가드용 fast lookup, 또는 frontend badge용)
- [ ] `LegalHoldDto` / `LegalHoldRequest` / `LegalHoldApproveRequest`
- [ ] envelope: `LEGAL_HOLD_VIOLATION`/`LEGAL_HOLD_RECENTLY_RELEASED` 에러 응답 details(`{holdId, reason, placedAt, placedBy, replaceableAt}`)

### B.5 Frontend

- [ ] `lib/queryKeys.ts` — `qk.legalHolds.list()`, `.detail(id)`, `.byTarget(type, id)`
- [ ] `lib/api.ts` — list/get/place/release/approve wrapper
- [ ] `/admin/legal-holds/page.tsx` — 목록 + 필터 + place 다이얼로그
- [ ] `/admin/legal-holds/[holdId]/page.tsx` — 상세 + cascade 영향 + audit timeline + release/approve 액션
- [ ] `components/admin/LegalHoldBadge.tsx` — ⚖ 배지 + 권한별 detail tooltip
- [ ] `components/files/FileTable.tsx` / `RightPanel.tsx` / `BulkActionBar.tsx` — hold 활성 row의 mutation 액션 비활성 + 배지 노출
- [ ] `components/admin/AdminSideNav.tsx` — placeholder Legal Hold 항목 활성화
- [ ] `app/admin/trash/page.tsx` (Wave 2 T9) — row.legalHold 시 복원/purge 버튼 비활성

### B.6 검증

- [ ] 단위 테스트: `LegalHoldServiceTest` (place/release/approve/expire/30일 락/cascade)
- [ ] 단위 테스트: `LegalHoldGuardTest` (각 mutation 진입 시 423 throw 검증)
- [ ] 통합 테스트: `LegalHoldControllerIntegrationTest` (REST entry → service → DB 한 번에)
- [ ] 통합 테스트: `HardPurgeServiceLegalHoldSkipTest` (hold 활성 row가 purge candidate에 미포함)
- [ ] 통합 테스트: `LegalHoldExpirationJobIntegrationTest` (cron이 expired row 자동 release)
- [ ] e2e (Playwright): place → mutation 차단 토스트 확인 → release → mutation 정상 흐름 복구
- [ ] CLAUDE.md §3 핵심 원칙 6/7 정합 검증 — DB 제약 + 트랜잭션 + `SELECT FOR UPDATE`로 cache flag 동기화 invariant 강제

### B.7 운영 / 문서

- [ ] docs/04 §15 베타 운영 런북에 Legal Hold sub-section 추가 (외부 요청 도착 → place → 활성 동안 → release 절차)
- [ ] docs/00 §4.3 v2.x 항목에서 Legal Hold remove (활성화 closure)
- [ ] docs/03 §6.3 / docs/04 §10 "v2.x deferred" 표식 제거 + 활성화 일자 기록
- [ ] ADR #46 Status: Active (현재 deferred → active로 변경)
- [ ] ADR #31 close + 본문 봐서 `legal_hold IS NOT TRUE` 활성화 명시

## §C. v2.x 후반 (선택)

- [ ] 태그 기반 hold (`legal_hold_tags` 테이블) — 별도 ADR
- [ ] Hold export endpoint (CSV/JSON) — 별도 ADR
- [ ] violation_blocked dedup 정책 결정 (LoginAttemptTracker 패턴 vs WARN 로그만)
- [ ] dept hierarchy(LTREE) 기반 cascade 추가 — folder/user 외 dept-level hold

## §D. 산출물 위치

- ADR: docs/00 §5
- 스키마: docs/02 §2.10 (reserve), V_ 마이그레이션 (B.1 활성화 시)
- 에러 코드: docs/02 §8
- 보안/컴플라이언스: docs/03 §6.3
- 운영 명세: docs/04 §10, §15 (활성화 시 sub-section 추가)
- dev-docs: 본 디렉터리 — v2.x closure 시 dev/completed/legal-hold-design/ 으로 이동
