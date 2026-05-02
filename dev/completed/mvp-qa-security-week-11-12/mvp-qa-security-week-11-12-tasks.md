# MVP QA + 보안 점검 + 베타 (Week 11-12) — Tasks

Last Updated: 2026-05-02

## Phase 상태

| Phase | 상태 | 비고 |
|---|---|---|
| Phase 1 — 베이스라인 + Inventory | **완료 (G1 통과)** | 2026-05-02 |
| Phase 2 — STRIDE Gap Analysis + 핵심 원칙 검증 | **완료 (G2 통과 — A안 sign-off)** | 2026-05-02 |
| Phase 3 — Triage + Remediation | **완료 (G3 통과)** | 2026-05-02 |
| Phase 4 — Beta Release Readiness | **완료 (G4 대기 — 사용자 closure commit/PR sign-off)** | 2026-05-02 |

---

## Phase 1 — 베이스라인 + Inventory

목표: 변경 0, 보고서만. 다음 phase 진입 결정의 근거.

- [x] P1.1 backend `./gradlew test` 베이스라인 — 75 classes / 723 tests / 522 executed PASS / 201 skipped(no Docker) / 0 fail. UP-TO-DATE = 회귀 0
- [x] P1.2 frontend baseline — 563/563 GREEN, typecheck clean, lint clean, build success (43s)
- [x] P1.3 ADR 인덱스 → `findings/adr-index.md` (38 active + 2 superseded, 3 finding 후보 식별)
- [x] P1.4 빈 체크박스 inventory → `findings/empty-checkbox-inventory.md` (~12 MVP / ~20 운영 / ~50 v1.x, Phase 2 검증 후보 8개)
- [x] P1.5 git status 정리 — Phase 4 closure commit에 흡수 예정 (.gitignore 항목 추가는 closure 시점)

### P1.1 — backend test 베이스라인

**작업 전 필독**:
- `backend/build.gradle.kts` (test task 설정)
- `backend/src/test/java/com/ibizdrive/` 디렉터리 (12 패키지)
- 직전 트랙 baseline: A16 closure 시점 666 tests (progress.md `2026-05-02 A16` entry)
- storage-orphan-cleanup 추가분: +6 listOlderThan, +3 streamActiveStorageKeys, +8 service unit, +1 disabled IT, +2 IT = +20 → 추정 686 tests

**원본 코드 참조**:
- 빌드 파일: `backend/build.gradle.kts`
- 테스트 디렉터리: `backend/src/test/java/com/ibizdrive/`
- Testcontainers config: `disabledWithoutDocker=true` (Docker 미가용 시 skip)

**구현 대상**:
- `findings/baseline-report.md` 신설, `## Backend Tests` 섹션에 기록
  - 명령: `./gradlew test --console=plain` (로그 적당량 수집)
  - 기록: 종료 코드 / 실행 테스트 수 / 실패 수 / Testcontainers skip 수 / 소요 시간

**검증 참조**:
- 종료 코드 0 = GREEN
- 실패 발견 시 즉시 작업 중단 + Phase 1 내에서 별도 task로 분리 (회귀 fix는 본 phase의 첫 우선)

**문서 반영**: `findings/baseline-report.md` — Phase 1 산출

---

### P1.2 — frontend 베이스라인

**작업 전 필독**:
- `frontend/package.json` scripts (test/typecheck/lint/build)
- 직전 트랙 baseline: A16 closure 565/565, A15 closure 527/527
- storage-orphan-cleanup 변경: types/audit union 추가만 — frontend test 카운트 변화 0 추정

**원본 코드 참조**:
- `frontend/package.json`
- `frontend/vitest.config.ts`
- `frontend/tsconfig.json`
- `frontend/eslint.config.*`
- `frontend/next.config.ts`

**구현 대상**:
- `findings/baseline-report.md` `## Frontend` 섹션에 4개 명령 결과 기록
  - `pnpm test --run` — 카운트 / 실패
  - `pnpm typecheck` — 종료 코드 / 에러 수
  - `pnpm lint` — 종료 코드 / warning/error 수
  - `pnpm build` — 종료 코드 / 번들 사이즈 (있으면)

**검증 참조**: 4개 모두 종료 코드 0

**문서 반영**: `findings/baseline-report.md`

---

### P1.3 — ADR 인덱스

**작업 전 필독**:
- `docs/00-overview.md` §5 ADR 표 (#1~#38)
- `docs/00-overview.md` §5.1 Superseded ADRs (#7, #8)

**원본 코드 참조**: 없음 (docs 분석)

**구현 대상**:
- `findings/adr-index.md` 신설
  - 컬럼: # / 제목 / status (active / superseded / deferred-marker) / 영향 docs 섹션 / 영향 코드 모듈 / 관련 closure 트랙
  - 표 형식 (38행 + superseded 2행)

**검증 참조**: 모든 ADR이 실 코드 모듈 또는 deferred 마커에 연결되는지 확인. 연결 끊김 = Phase 2 finding 후보

**문서 반영**: `findings/adr-index.md`

---

### P1.4 — 빈 체크박스 inventory

**작업 전 필독**:
- `docs/03-security-compliance.md` §5/§6/§7/§8/§9/§10
- `docs/04-admin-operations.md` §3~§10

**원본 코드 참조**: 없음 (docs 분석)

**구현 대상**:
- `findings/empty-checkbox-inventory.md` 신설
  - 표: docs § / 항목 / 1차 분류 (MVP / 운영 / v1.x) / 사유
  - 1차 분류는 *추천*이며 Phase 3 트리아지에서 사용자 sign-off 시 확정

**검증 참조**: 모든 빈 체크박스가 분류됨

**문서 반영**: `findings/empty-checkbox-inventory.md`

---

### P1.5 — git status 정리

**작업 전 필독**:
- `git status` 현재 출력
- `.gitignore` 현재 내용

**원본 코드 참조**:
- `.gitignore`

**구현 대상**:
- `.gradle-user-home*` / `.g3-5/` / `.tmp-gradle-root-get/` → `.gitignore` 항목 추가 (이미 있는지 먼저 확인)
- `dev/process/a10-shares-2026-05-01.md` deletion staging
- 물리 디렉터리 삭제는 본 task에서 하지 않음 (사용자 확인 후 별도)

**검증 참조**: `git status --short` 출력이 본 트랙 작업 파일 + closure에 사용할 staging만 남음

**문서 반영**: 없음 (cleanup task)

---

## Phase 2 — STRIDE Gap Analysis + 핵심 원칙 검증

목표: 모든 위협/원칙에 evidence 매핑.

- [x] P2.1 STRIDE 매트릭스 6 카테고리 행별 매핑 → `findings/stride-gap-analysis.md` (28행: 18 구현됨 / 3 부분 / 5 v1.x deferred / 2 운영 책임). MVP-blocker 후보 3건 식별
- [x] P2.2 audit event enum vs emit 사이트 매칭 — 41 enum 중 26 emit (63%). 미사용 15건 모두 deferred 항목 (ADMIN_* 7개 + SYSTEM_BACKUP_COMPLETED + USER_PASSWORD_CHANGED + USER_MFA_ENABLED + FILE_VIEWED + FOLDER_AUDIT_LEVEL_CHANGED + VERSION_RESTORED + VERSION_DOWNLOADED + AUDIT_EXPORTED — ADR #9/#18/admin frontend 미구현)
- [x] P2.3 endpoint × @PreAuthorize 매핑 → `findings/principle-conformance.md` 원칙 10. 모든 mutation 컨트롤러 (File/Folder/Permission/Trash/Share) 0 미보호
- [x] P2.4 CLAUDE.md §3 핵심 원칙 11개 conformance → `findings/principle-conformance.md` (8 PASS / 3 PARTIAL / 0 FAIL). 백엔드 원칙 6-11 모두 PASS, 프론트 3-5 수동 검증 필요

### Phase 2 G2 게이트

| 게이트 항목 | 상태 |
|---|---|
| STRIDE 28행 evidence 매핑 100% | ✓ |
| 핵심 원칙 11개 conformance | ✓ (백엔드 PASS, 프론트 PARTIAL = grep 한계) |
| audit enum 미사용 15건 deferred 매핑 | ✓ |
| MVP-blocker 후보 식별 | ✓ (3건) |
| 사용자 sign-off (트리아지) | **대기** |

---

## Phase 3 — Triage + Remediation

목표: gap 분류 + MVP-fix 트랙 처리.

- [x] P3.1 finding 트리아지 + 사용자 sign-off (2026-05-02 A안: #1=defer, #2=fix, #3=fix)
- [x] P3.2 MVP-fix 트랙 sub-task — fix #2 (`application.yml` server.error 3줄), fix #3 (`SecurityConfig.headers()` chain)
- [x] P3.3 v1.x deferred 마커 — `docs/03 §5.3` 4개 항목 + §5.4 마지막 항목. §5.4 첫 두 항목 [x] 체크
- [ ] P3.4 운영 절차 docs/04 본문 추가 — Phase 4 P4.2와 통합 (운영/v1.x 마커 동시 처리)

→ G3 게이트 통과 (backend test GREEN — 723 tests / 522 PASS / 0 fail). P3.4는 docs/04 §3-§10 통합 작업으로 Phase 4에서 처리.

산출:
- `findings/triage-decisions.md` — sign-off 기록 + Fix 변경 내역 + 검증 결과

---

## Phase 4 — Beta Release Readiness

목표: 베타 출시 GO/NO-GO 체크리스트.

- [x] P4.1 docs/03 §5-§10 본문 또는 deferred 마커 — §5.1·§5.2(운영/v1.x), §5.3·§5.4(Phase 3 결정), §6.1·§6.2(운영/v1.x), §6.3(v2.x), §7(.env [x] + 시크릿 매니저 운영), §8(공통 운영, 도메인 v1.x), §9(Dependabot MVP 게이트, 나머지 v1.x/운영), §10(운영) + §"작성 우선순위" 갱신
- [x] P4.2 docs/04 §3-§14 마커 — §3(전체 v1.x), §4(전체 v1.x + §4.3 MVP 상태 박스), §5(전체 v1.x), §6(orphan cleanup [x] MVP, quota v1.x), §8.2([x] + Legal Hold v2.x), §9(파일크기 100MB 본문, 확장자/audit_level v1.x), §10(전체 v2.x), §11(복구 시나리오 [x] 휴지통, 나머지 운영), §12(전체 운영/v1.x), §13(cron 표 상태 컬럼 추가), §14(v1.x) + §"작성 우선순위" 갱신
- [x] P4.3 BETA-RELEASE.md 신설 — 10 섹션(전제, 코드 게이트, 인프라 게이트, cron, 보안 헤더, 인증, 감사, v1.x deferred 명시, 모니터링, GO 결정)
- [x] P4.4 closure 준비 — `.gitignore` 5개 패턴 추가, `docs/progress.md` 트랙 closure entry 추가, dev-docs sync. PR/squash-merge/dev-docs archive는 G4 사용자 sign-off 후

→ G4 게이트 대기: closure commit + PR/squash-merge 사용자 승인 필요

---

## 참조 블록 템플릿 (Phase 2 진입 시 활용)

각 미완료 task는 다음 5섹션을 가진다:
- 작업 전 필독
- 원본 코드 참조
- 구현 대상
- 검증 참조
- 문서 반영

Phase 2/3/4 각 task는 진입 시점에 본 5섹션을 채워서 다음 세션이 즉시 재개 가능하도록 한다.
