# Wave 1 — T3: 시스템 정책 페이지 — cron 토글 read-only 노출 (plan)

Last Updated: 2026-05-07

> Branch: `wave1-t3-system-cron-readonly` (worktree `.claude/worktrees/wave1-t3-system-cron-readonly`, base `1a36615`)

## 요약

`/admin/system` 페이지를 활성화해 4개 운영 cron(`purge.expired`, `share.expire`, `permission.expire`, `storage.orphan.cleanup`)의 현재 설정값(enabled/cron/zone/batch 파라미터)을 **read-only**로 노출한다. backend는 신규 `GET /api/admin/system/cron`(ADMIN-only, audit emit 0)으로 4개 `@ConfigurationProperties` 빈을 묶어 반환. frontend는 단일 페이지에 4 카드. 변경/토글 UI는 v1.x deferred (실제 토글은 application.yml + 재기동 필요 — config server 도입 전까지 read-only가 유일한 정합).

## 현재 상태 분석

### backend
- 4 cron이 `application.yml app.{purge, share.expiration, permission.expiration, storage.orphan-cleanup}.*`로 설정됨. 각각 `@ConfigurationProperties` record (`HardPurgeProperties`, `ShareExpirationProperties`, `PermissionExpirationProperties`, `StorageOrphanCleanupProperties`).
- prod profile은 모두 `enabled=true`로 override (`mvp-prod-profile`). default(dev)는 모두 `false`.
- 운영자가 현재 설정을 확인할 admin UI는 부재. `application.yml` 직접 열람만 가능.
- 본 트랙은 read endpoint만 — 변경 endpoint 없음. (config 변경 시 재기동 필요라는 기존 제약 유지)

### frontend
- `/admin/system` 라우트 미존재. `AdminSideNav` `DEFERRED_ITEMS`에 '시스템' 항목 (line 33).
- `/admin` landing(`page.tsx`)의 deferred 안내 리스트(line 47)에 '시스템' 포함.
- pattern reference: `/admin/audit/logs` (Wave 1 T2 server-side export), `/admin/users`, `/admin/departments`. 단일 페이지/단일 endpoint 패턴은 `admin-department-crud`가 가장 가까움(KISS — 4 동작이 의미적 한 묶음 → 단일 hook).

### docs
- `docs/04 §13` cron 4개 활성화 옵션 표 존재 (line 393-396).
- `docs/04 §2` 라우트 트리 line 69-72에 `/system/{health,backups,jobs}` v1.x deferred 표기.
- `docs/02 §7` admin endpoint 표에 `/api/admin/system/*` 부재.
- `BETA-RELEASE.md §7` admin frontend deferred wording에 '시스템 페이지' 포함.

## 목표 상태

### backend (신규)
1. `GET /api/admin/system/cron` — `@PreAuthorize("hasRole('ADMIN')")`. 응답 본문:
   ```json
   {
     "jobs": [
       { "key": "purge.expired",      "label": "휴지통 hard purge",       "enabled": false, "cron": "0 0 0 * * *",  "zone": "Asia/Seoul", "maxPerRun": 10000 },
       { "key": "share.expire",       "label": "공유 만료 처리",          "enabled": false, "cron": "0 */5 * * * *", "zone": "Asia/Seoul", "batchSize": 200 },
       { "key": "permission.expire",  "label": "권한 만료 처리",          "enabled": false, "cron": "0 */5 * * * *", "zone": "Asia/Seoul", "batchSize": 200 },
       { "key": "storage.orphan.cleanup","label": "스토리지 고아 정리",   "enabled": false, "cron": "0 0 1 * * *",  "zone": "Asia/Seoul", "maxPerRun": 10000, "graceHours": 24 }
     ]
   }
   ```
2. SELECT-only — audit emit 0.
3. 401/403 매트릭스: 미인증 401, 비-ADMIN(MEMBER/AUDITOR) 403.

### frontend (신규)
4. `/admin/system` 페이지: 4 카드 grid.
   - 각 카드: 작업명(label) + ON/OFF 배지(enabled) + cron 표현식 + zone + 추가 파라미터(batch/grace/max).
   - 페이지 상단에 "현재 운영 설정 (read-only)" 헤더 + "변경은 application.yml + 재기동" 안내 문구.
5. `AdminSideNav` `DEFERRED_ITEMS` '시스템' 제거 + `ACTIVE_ITEMS`에 추가.
6. `/admin` landing deferred 리스트에서 '시스템' 제거 + 가용 카드 추가.

### docs (정렬)
7. `docs/04 §2` 라우트 트리 — `/system` 노드 활성 표기 + 본 트랙 cross-ref. 헤더 wording 갱신.
8. `docs/04 §13` 헤더에 `/admin/system` cross-link 추가.
9. `docs/02 §7` admin endpoint 표 + 본 endpoint 명세 행 추가.
10. `BETA-RELEASE.md §7` admin frontend wording — '시스템' 노드 v1.x 항목에서 제외 (cron 상태 노출만 활성, 변경은 v1.x 유지).

## phase별 실행 지도

각 phase가 끝나면 self-review + 다음 phase 자동 진입.

### P1 — backend 도메인/응답 DTO (TDD)
- P1.1 `CronJobStatusResponse` record 4종 (또는 단일 `key`-discriminator) 설계.
  - 결정: **단일 record + 옵셔널 필드**(`batchSize`/`maxPerRun`/`graceHours` `@JsonInclude(NON_NULL)`). 4종 분할 시 클라이언트가 union 타입 처리해야 해 frontend 부담 증가 (KISS).
- P1.2 `AdminSystemControllerTest` (Spring `@WebMvcTest`):
  - `GET /api/admin/system/cron` 200 + 4 jobs 순서 고정.
  - 각 job 페이로드 필드 셰입(필요시 키별 옵셔널 검증).
  - 401/403 매트릭스.
- P1.3 `AdminSystemController` 구현 — 4 properties 빈 주입 → DTO 변환.

### P2 — frontend 타입/api/hook (TDD)
- P2.1 `types/system.ts` — `CronJobStatus`, `CronJobsResponse`. backend DTO mirror.
- P2.2 `api.adminSystem.test.ts` — `adminGetCronStatus()` happy path + 403/401 throw.
- P2.3 `lib/api.ts` 확장 — `adminGetCronStatus()`.
- P2.4 `lib/queryKeys.ts` 확장 — `adminSystemCron` 키.
- P2.5 `hooks/useAdminSystem.ts` (단일 hook 통합 — admin-department-crud 패턴).

### P3 — frontend 페이지 (TDD)
- P3.1 `/admin/system/page.test.tsx` — render(loading/error/4 cards).
- P3.2 `/admin/system/page.tsx`.
- P3.3 `AdminSideNav.tsx` ACTIVE에 '시스템' 추가 + DEFERRED에서 제거.
- P3.4 `/admin/page.tsx` — 가용 카드 + deferred 리스트 정리.

### P4 — docs 정렬
- P4.1 `docs/02 §7` 신규 endpoint 명세.
- P4.2 `docs/04 §2 §13` route tree + cross-link.
- P4.3 `BETA-RELEASE.md §7` wording.

### P5 — 검증 + dev-docs sync + closure
- P5.1 backend `./gradlew test` GREEN.
- P5.2 frontend `pnpm test --run` + `pnpm typecheck` + `pnpm lint` + `pnpm build` 모두 GREEN.
- P5.3 `docs/progress.md` closure entry (최상단).
- P5.4 `dev/active/.../*` → `dev/completed/`로 archive (PR 머지 후 별도 commit).
- P5.5 `dev/process/wave1-t3-2026-05-07.md` 삭제.

## acceptance criteria

- `GET /api/admin/system/cron` 200 응답이 위 4 jobs 페이로드와 일치(JSON 셰입 + 키 순서 고정).
- 비-ADMIN actor가 호출 시 403 (audit emit 0).
- `/admin/system`이 이 응답을 4 카드로 렌더 — enabled 배지/cron/zone/batch가 화면에 노출.
- `AdminSideNav` 사이드바에서 '시스템' 활성 링크.
- `/admin` landing에서 '시스템' deferred 표기 제거 + 가용 카드 추가.
- `docs/02 §7` + `docs/04 §2 §13` + `BETA-RELEASE.md §7` 정렬 완료.
- backend test + frontend test/typecheck/lint/build 모두 exit 0.

## 검증 게이트

- backend: `./gradlew test --tests "com.ibizdrive.admin.AdminSystemControllerTest"`로 단위, 그리고 전체 test로 회귀 가드.
- frontend: api 단위 + page 통합 테스트 신설. `pnpm test --run`/`typecheck`/`lint`/`build` 풀세트.
- docs grep — `시스템` v1.x 잔존 확인 (페이지가 활성화된 만큼 wording 정렬).
- audit emit 카운트(40)는 본 트랙에서 변동 없음 — `BETA-RELEASE.md §6` 메트릭 변경 0.

## 리스크와 완화

- **R1 토글 가능 오해**: 사용자가 '시스템 정책'이라는 어휘에서 변경 가능 UI를 기대. → 페이지 헤더 + 카드에 read-only 명시 + "변경은 application.yml + 재기동" 안내.
- **R2 prod 노출 민감도**: cron 표현식 자체는 비밀이 아님(application.yml은 코드와 함께 관리). password/secret은 별도. → DTO에 cron/zone/배치만 포함, secret 미노출.
- **R3 4종 DTO 일관성**: 옵셔널 필드(batchSize/maxPerRun/graceHours) 처리 → `@JsonInclude(NON_NULL)` + frontend `?` 옵셔널 타입.
- **R4 ADR 발번 가능성**: read-only/변경 v1.x 결정은 ADR 신규 발번이 아니라 기존 `mvp-prod-profile` + ADR #38 정책 자연 확장 — 신규 ADR 0건 목표.
- **R5 docs/04 §2 트리에서 `/system/{health,backups,jobs}` 자식 노드 처리**: 본 트랙은 부모 `/system` landing만 활성. 자식은 v1.x 유지. → 트리 wording에서 부모만 활성, 자식 deferred 명시.
