# MVP QA + 보안 점검 + 베타 (Week 11-12) — Plan

Last Updated: 2026-05-02

## 요약

`docs/00 §4.1` 로드맵의 마지막 단계 (Week 11-12 = QA + 보안 점검 + 베타). A0~A16 + storage-orphan-cleanup 까지 백엔드/프론트 기능 구현은 완료(`master 90274c7`). 본 트랙은 **신규 기능 추가가 아니라**, (a) 구현된 시스템을 docs/03 위협 모델·감사·권한 매트릭스와 대조해 gap을 닫고, (b) docs/03 §5~§10 / docs/04 §2~§10 의 스켈레톤 항목을 MVP 베타 범위로 트리아지(채움 vs v1.x 명시 deferred), (c) 베타 출시 전 운영 readiness 체크리스트를 확정한다.

## 현재 상태 분석

### 코드 상태

- `master` HEAD: `90274c7` (storage-orphan-cleanup ADR #38 closure)
- 누적 ADR: #1 ~ #38, superseded 2건 (#7 → #13, #8 → #14)
- 백엔드 패키지 11종: `audit / auth / common / department / file / folder / permission / purge / search / share / storage / trash / user`
- 프론트엔드 컴포넌트 트리 11종: `audit / dnd / files / folders / shares / statusbar / storage / topbar / trash / upload / ...`
- 미해결 git status: `dev/process/a10-shares-2026-05-01.md` deleted (정리만 필요), `.gradle-user-home*` / `.g3~5/` / `.tmp-gradle-root-get/` untracked (gradle 임시, .gitignore 후보)

### docs/03 (보안) 본문 상태

| 섹션 | 상태 | 비고 |
|---|---|---|
| §1 위협 모델 (STRIDE) | 본문 활성 | 모든 행 `상태=설계` 또는 `부분 설계` — 본 트랙에서 구현 evidence 또는 v1.x 마커로 갱신 |
| §2 인증 | 본문 활성 (ADR #18~#23) | A1 / A1.5 closure 완료 |
| §3 권한 매트릭스 | 본문 활성 (ADR #17/#26/#28/#34/#37) | A1.5/A4/A10/A12/A13/A16 closure 완료 |
| §4 감사 정책 | 본문 활성 (ADR #24/#25) | A2 audit/append-only closure 완료 |
| §5 저장소 보안 | **체크박스 스켈레톤** | TLS/HSTS/SSE/MIME magic/Content-Disposition |
| §6 데이터 보호 | **체크박스 스켈레톤** | 개인정보/백업/Legal Hold |
| §7 비밀번호·키 관리 | **체크박스 스켈레톤** | .env/Secrets Manager/키 로테이션 |
| §8 규정 준수 | **체크박스 스켈레톤** | 도메인별 (금융/의료/공공) |
| §9 취약점 대응 | **체크박스 스켈레톤** | SAST/DAST/Dependabot |
| §10 인시던트 대응 | **체크박스 스켈레톤** | Severity/통지/post-mortem |

### docs/04 (관리자/운영) 본문 상태

전체 스켈레톤. §1 역할 / §2 페이지 트리 / §13 배치 작업 표는 채워짐, §3~§10 본문 미작성.

### 핵심 원칙 (CLAUDE.md §3) vs 코드 실제 일치도

본 트랙 Phase 2에서 11개 항목 grep+코드 검증. 현재 미검증.

## 목표 상태

베타 출시 (사내 베타 가정 — 외부 일반 출시 X) 시점에 다음을 만족:

1. **위협 모델 evidence 매핑**: docs/03 §1.3 STRIDE 매트릭스 모든 행이 (a) 구현 파일/테스트 evidence link, (b) 명시적 v1.x deferred + 사유, (c) 운영 절차 책임 중 하나로 분류됨. `상태=설계` 잔존 0.
2. **MVP 보안 게이트**: docs/03 §5 (저장소 보안) 항목 중 MVP 적용분 본문 작성, MVP 미적용분은 명시적 deferred 마커(이유+v1.x 트랙).
3. **프로덕션 readiness 분리 체크리스트**: §6/§7/§8/§9/§10 항목 중 (a) 코드/설정 변경 필요분, (b) 운영 절차/문서 책임분, (c) v1.x 이월분으로 분류.
4. **운영 페이지 (docs/04) MVP scope 확정**: §2 페이지 트리에서 MVP 출시 시 살아있는 화면과 v1.x deferred 화면 명시. 현재 frontend `app/(admin)` 구현 상태와 정합.
5. **회귀 안전망 GREEN**: backend `./gradlew test` GREEN, frontend `pnpm test --run` + `typecheck` + `lint` + `build` GREEN, 베이스라인 카운트 기록.
6. **베타 운영 체크리스트**: env config / DB migration order / secrets 주입 경로 / monitoring hook / rollback 절차가 단일 문서(또는 docs/04 §11 신설)에 정리.

비-목표: SSO/MFA/SAST/외부 모의해킹/Legal Hold 실 구현은 v1.x. 본 트랙은 deferred 결정의 정당화와 명시적 마커만 한다.

## Phase별 실행 지도

### Phase 1 — 베이스라인 + Inventory

목표: 코드/테스트/문서의 현재 상태를 단일 보고서로 고정. 실 변경 0.

작업 단위:
- P1.1 backend `./gradlew test` 실행, 카운트/실패 기록
- P1.2 frontend `pnpm test --run` + `pnpm typecheck` + `pnpm lint` + `pnpm build` 실행
- P1.3 ADR #1~#38 + superseded 2건을 한 표로 정리 (영향 받은 docs 섹션과 함께)
- P1.4 docs/03 § / docs/04 § 빈 칸 인벤토리 (체크박스 → MVP-필수 / 운영-책임 / v1.x-이월 1차 분류)
- P1.5 git status 정리 (`.gradle-user-home*` 등 untracked → .gitignore 또는 정리, dangling `dev/process/a10-shares-2026-05-01.md` 처리)

산출: `dev/active/mvp-qa-security-week-11-12/findings/baseline-report.md`

검증: 각 명령 종료 코드 0. 보고서가 다음 phase 진입 결정의 근거가 됨.

### Phase 2 — STRIDE Gap Analysis + 핵심 원칙 검증

목표: docs/03 §1.3 STRIDE 매트릭스 모든 행과 CLAUDE.md §3 핵심 원칙 11개를 코드 evidence와 1:1 매핑.

작업 단위:
- P2.1 STRIDE 매트릭스 6 카테고리 (Spoofing/Tampering/Repudiation/Info Disclosure/DoS/Elevation) 행별로 (a) 구현 파일:라인 또는 테스트, (b) 미구현 시 영향도, (c) MVP-blocker yes/no 판정
- P2.2 docs/03 §4.1 audit event enum vs 실 emission 사이트 grep 매칭 (정의됐지만 emit 사이트 0인 enum 식별)
- P2.3 docs/03 §3.5 endpoint × @PreAuthorize 매핑 (controller 표 누락 endpoint 검출)
- P2.4 CLAUDE.md §3 핵심 원칙 11개 검증
  - 1: URL folderId 중심 (`useCurrentFolder`/`folderPath` 라우팅 grep)
  - 2: RightPanel = query param
  - 3: 낙관적 업데이트 = 비파괴적만
  - 4: DnD 두 컨텍스트 분리
  - 5: 가상화 aria-rowcount/rowindex
  - 6: DB 제약 = 진실 출처 (`UNIQUE WHERE deleted_at IS NULL` partial index 검증)
  - 7: 업로드/이동/복원 = `@Transactional` + `SELECT FOR UPDATE`
  - 8: audit_log append-only (`REVOKE UPDATE, DELETE` migration 검증)
  - 9: storage_key = UUID
  - 10: 파괴적 액션 백엔드 재검증 (`@PreAuthorize` 모든 mutation endpoint)
  - 11: 정규화 함수 fixtures 공유

산출: `dev/active/mvp-qa-security-week-11-12/findings/stride-gap-analysis.md`, `principle-conformance.md`

검증: 각 행/원칙별 evidence 또는 deferred 마커 100% 채움. 미해결 = Phase 3 트리아지 후보.

### Phase 3 — Triage + Remediation

목표: Phase 2에서 발견된 gap을 (a) MVP 즉시 fix, (b) 운영 절차로 흡수, (c) v1.x deferred로 분류하고 (a) 트랙만 본 트랙에서 처리.

작업 단위:
- P3.1 모든 finding 트리아지 (severity x effort 매트릭스) — 사용자 sign-off 게이트
- P3.2 MVP-fix 트랙별 sub-task (각각은 작은 PR 또는 본 worktree commit)
- P3.3 v1.x deferred 항목은 docs/03/04 inline 마커 (`> v1.x deferred — 사유: ...`) + backlog index (`docs/00 §4.2` 또는 별도 `docs/05-v1x-backlog.md` 신설)
- P3.4 운영 절차 항목은 docs/04 본문 추가 (MVP 운영 가이드 섹션 신설)

산출: 변경된 docs + (있으면) 코드 fix commits

검증: P2 finding 100% 처리 상태. 미처리 = beta 출시 결정 별도 사유.

### Phase 4 — Beta Release Readiness

목표: 사내 베타 출시 가능성을 단일 체크리스트로 고정.

작업 단위:
- P4.1 docs/03 §5~§10 본문 작성 (또는 명시적 deferred 마커)
- P4.2 docs/04 §3~§10 MVP scope 본문 (또는 deferred)
- P4.3 베타 운영 체크리스트 신설 (docs/04 §11 또는 ROOT `BETA-RELEASE.md`):
  - env config 표 (`NEXT_PUBLIC_API_BASE_URL` / `app.storage.*` / `app.purge.*` / `app.share.expiration.*` / `app.permission.expiration.*` / `app.storage.orphan-cleanup.*` / DB 접속 / Spring Session)
  - DB migration 적용 순서 V1 → V7 + Flyway baseline 조건
  - 첫 admin 시드 절차 (ADR #21)
  - 모니터링 hook 위치 (현재 미구현 → audit_log query, application logs)
  - rollback 절차 (DB는 이전 V로 복원 불가 → 데이터 백업 우선)
- P4.4 본 트랙 PR + closure (`progress.md` entry + `dev/active → dev/completed`)

산출: 갱신된 docs/03·04, 신설 BETA-RELEASE.md (또는 §11 inline), progress entry

검증: 베타 출시 결정자(=사용자)가 체크리스트만 보고 GO/NO-GO 가능.

## Acceptance Criteria

- [ ] STRIDE 매트릭스 (docs/03 §1.3) 모든 행 status가 `구현 evidence` / `v1.x deferred + 사유` / `운영 책임` 중 하나로 갱신
- [ ] CLAUDE.md §3 핵심 원칙 11개 conformance 보고 (위반 0 또는 명시적 deferred)
- [ ] backend `./gradlew test` GREEN, frontend `pnpm test --run` + `typecheck` + `lint` + `build` GREEN
- [ ] docs/03 §5~§10 빈 체크박스 0 (모두 본문 또는 deferred 마커)
- [ ] docs/04 §3~§10 MVP scope 결정 완료 (본문 또는 deferred)
- [ ] 베타 운영 체크리스트 단일 위치 도달 (페이지 1장)
- [ ] 본 트랙 PR squash-merge + dev-docs archive

## 검증 게이트

| Gate | 조건 | 액션 |
|---|---|---|
| G1 (Phase 1 종료) | baseline-report.md 생성, 테스트/빌드 GREEN | Phase 2 진입 |
| G2 (Phase 2 종료) | gap-analysis 100% 채움 | 트리아지 sign-off 요청 (사용자 게이트) |
| G3 (Phase 3 종료) | MVP-fix 트랙 모두 머지, deferred 명시 | Phase 4 진입 |
| G4 (Phase 4 종료) | 베타 체크리스트 + 모든 docs sync | PR + closure 게이트 (사용자 게이트, ADR #38 closure 패턴) |

## 리스크와 완화

| Risk | Severity | Mitigation |
|---|---|---|
| 범위 폭발 — docs/03·04 빈 체크박스를 다 본문화하면 며칠 | High | Phase 1 inventory에서 "MVP 베타 적용 가능?" 기준으로 사전 트리아지 → Phase 4에서 deferred 명시는 1줄 마커만 |
| Phase 2 finding 중 critical bug 발견 시 본 트랙이 길어짐 | Medium | finding이 별도 트랙으로 분리되어야 할 규모면 본 트랙은 finding 보고 + open issue로 종료, 별도 worktree 분기 |
| ADR 신규 필요 (deferred 결정도 ADR 대상) | Low | deferred는 ADR 대신 inline 마커 + progress entry로 대체. 새 ADR은 본문 변경이 동반될 때만 |
| `.gradle-user-home*` / `.tmp-gradle-root-get/` 정리 시 실수로 active worktree 손상 | Low | 정리 전 `.gitignore` 추가 우선, 물리 삭제는 사용자 확인 후 |
| 베타 운영 체크리스트가 가설 기반 (실 운영 환경 부재) | Medium | 항목별 (a) 코드/설정 검증 가능, (b) 운영 시점 검증 명시. 본 트랙은 (a)만 GREEN 보장 |

## 참고 backlinks

- `docs/00-overview.md §4.1` — 본 phase 정의 출처
- `docs/03-security-compliance.md` — 위협 모델·인증·권한·감사·저장소·데이터·키·규정·취약점·인시던트
- `docs/04-admin-operations.md` — 관리자/운영 페이지·배치 작업
- `CLAUDE.md §3` — 핵심 원칙 11개
- `docs/progress.md` — A0~A16 + storage-orphan-cleanup closure 회고
- `dev/completed/` — 21개 closure 트랙 dev-docs (참조용)
