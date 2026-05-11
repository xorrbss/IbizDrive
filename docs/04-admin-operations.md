# 04 - 관리자 & 운영

> 관리자 페이지 UI, 운영 정책, 쿼터 관리, 백업/복구, 모니터링.
> **MVP 상태 (mvp-qa-security closure, 2026-05-02)**: §7(감사 로그 UI) M12 closure, §13(배치 작업) 4개 cron 활성, 나머지 frontend admin UI는 v1.x. 본 문서 §3-§6, §8-§12, §14는 frontend admin 페이지 미구현 + v1.x deferred 마커.

---

## 1. 관리자 역할 구분

| 역할 | 권한 |
|---|---|
| **Super Admin** | 전체 (사용자/부서/권한/감사) |
| **User Admin** | 사용자/부서 관리만 |
| **Auditor** | 감사 로그 읽기 전용 |
| **Storage Admin** | 쿼터/백업 관리만 |

> 역할 분리는 감사 원칙의 핵심: "한 명이 모든 것을 할 수 있으면 감사가 의미 없음"

### 1.1 가드 분리 — UX 게이트 vs 보안 게이트 (m-admin-entry-rewrite, 2026-05-03; wave1.5-auditor-admin-ui-access, 2026-05-07)

ADR #21 잔여 closure로 `/admin` 진입을 두 계층으로 분리:

- **프론트 `AdminGuard` = UX 가드만**. `useMe`로 현재 역할을 확인해 허용 role 외에는 `/files`로 redirect. 사용자에게 "권한 없는 화면을 잠깐이라도 보지 않게" 하는 표면 보호. 보안 강제 없음.
- **백엔드 `@PreAuthorize` = 보안의 진실**. 모든 `/api/admin/**` endpoint는 컨트롤러 메서드 단위에서 강제. 프론트 가드를 우회한 직접 호출은 401(미인증) 또는 403(역할 부족)으로 차단된다. read-only 엔드포인트(audit/*, audit/export, system/cron)는 `hasAnyRole('ADMIN','AUDITOR')`로 확장.
- **AuthGuard와 중첩 순서**: `<AuthGuard><AdminGuard allowedRoles=...>...</AdminGuard></AuthGuard>` — AuthGuard가 비로그인을 `/login?next=/admin`으로 먼저 처리한 뒤 AdminGuard가 role 검사. 두 가드의 책임은 분리(인증 보유 vs 역할).
- **이중 가드(layout + page)**: layout `<AdminGuard allowedRoles={['ADMIN','AUDITOR']}>`로 read-only 영역 진입을 허용하고, ADMIN-only mutation 페이지는 페이지 단에서 default `<AdminGuard>` (allowedRoles 기본값 `['ADMIN']`)로 다시 좁힌다. 같은 `useMe` 캐시(staleTime 60s)를 공유해 추가 fetch 없음.
- **중요 회귀 가드**: 프론트 게이트 강도를 보안 강도와 혼동하지 말 것. `/api/admin/**`는 `SecurityConfig` `permitAll` 목록에 포함되지 않아야 하며, 신규 admin 컨트롤러에는 항상 메서드 레벨 `@PreAuthorize` 또는 클래스 레벨 강제가 붙어야 한다.

### 1.2 페이지별 진입 허용 role (wave1.5-auditor-admin-ui-access, 2026-05-07)

| 페이지 | 진입 허용 (UI 가드) | 백엔드 가드 |
|---|---|---|
| `/admin` (대시보드) | ADMIN | `hasRole('ADMIN')` |
| `/admin/audit/logs` | ADMIN, AUDITOR | `hasAnyRole('ADMIN','AUDITOR')` |
| `/admin/system` | ADMIN, AUDITOR | `hasAnyRole('ADMIN','AUDITOR')` |
| `/admin/users` | ADMIN | `hasRole('ADMIN')` |
| `/admin/departments` | ADMIN | `hasRole('ADMIN')` |
| `/admin/permissions` | ADMIN | `hasRole('ADMIN')` |
| `/admin/storage` | ADMIN | `hasRole('ADMIN')` |

`AdminSideNav`는 AUDITOR에게 `감사 로그` + `시스템`만 노출하고 deferred 섹션도 hide(화면 단순화). ADMIN에게는 7 active + 3 deferred 항목 모두 노출 — 회귀.

---

## 2. 관리자 페이지 구조

> **활성 라우트** (admin-dashboard 트랙 closure, 2026-05-07):
> - `/admin` — KPI 대시보드 (admin-dashboard 트랙 closure, 2026-05-07)
> - `/admin/audit/logs` — 감사 로그 (M12 closure)
> - `/admin/users` — 사용자 목록 + 초대 + 검색·재활성·displayName 편집 (Wave 1 T1 closure)
> - `/admin/departments` — 부서 CRUD(생성/검색/rename/(de)activate, Wave 2 T4)
> - `/admin/permissions` — 권한 매트릭스 viewer + 단일 row 철회 (subject/resource/preset/q 필터 + 만료 배지 + 행별 "철회" 버튼, Wave 2 T5 + admin-permission-revoke follow-up 2026-05-09; grant 다이얼로그는 v1.x deferred)
> - `/admin/system` — 운영 cron 4종 read-only 노출 (Wave 1 T3, 변경은 application.yml + 재기동)
> - `/admin/storage` — 시스템 합계 + 정리 기록 overview (admin-storage-overview, Wave 2 T8, 2026-05-07)
> - `/admin/trash/all` — 전역 휴지통 viewer (q/type/ownerId 필터 + cursor pagination + 단건 복원/영구삭제, Wave 2 T9, 2026-05-07)
>
> 그 외 노드는 모두 **v1.x deferred**. 사이드바에는 disabled 항목으로 노출하되 라우트는 만들지 않음(YAGNI).

```text
/admin                       (활성 — KPI 대시보드, admin-dashboard 트랙 2026-05-07)
├─ /users                  사용자 초대 + 목록 (검색/편집/활성 토글)         (활성, 2026-05-06)
│  ├─ /:id                 사용자 상세 + 활동                              (v1.x deferred)
│  └─ /import              CSV 일괄 import                                (v1.x deferred)
├─ /departments              부서 CRUD (생성/검색/rename/(de)activate)     (활성, Wave 2 T4)
│  ├─ /tree                조직도 트리 편집                                 (v1.x deferred)
│  └─ /:id                 부서 상세                                       (v1.x deferred)
├─ /permissions              권한 매트릭스 viewer + 단일 row 철회 (필터+만료배지+철회) (활성, Wave 2 T5 + admin-permission-revoke 2026-05-09; grant UI는 v1.x deferred)
│  ├─ /bulk                권한 일괄 변경                                  (v1.x deferred)
│  └─ /templates           권한 프리셋 템플릿                              (v1.x deferred)
├─ /storage
│  ├─ /usage               전체 사용량                                    (v1.x deferred)
│  ├─ /quotas              쿼터 관리                                       (v1.x deferred)
│  └─ /cleanup             고아 객체 정리                                  (v1.x deferred)
├─ /audit
│  ├─ /logs                전체 감사 로그                                  (활성, M12)
│  ├─ /downloads           다운로드 이력                                   (v1.x deferred)
│  ├─ /permissions         권한 변경 이력                                  (v1.x deferred)
│  └─ /export              로그 내보내기 (CSV/JSON)                        (v1.x deferred)
├─ /trash
│  ├─ /all                 전역 휴지통 viewer (q/type/ownerId 필터)        (활성, Wave 2 T9, 2026-05-07)
│  └─ /policy              휴지통 정책 설정                                (v1.x deferred)
├─ /legal-hold             법적 보존 관리                                  (v1.x deferred)
├─ /policies
│  ├─ /file-size           파일 크기/확장자 정책                            (v1.x deferred)
│  ├─ /retention           보존 기간                                       (v1.x deferred)
│  └─ /audit-levels        감사 레벨 폴더 지정                             (v1.x deferred)
└─ /system                  운영 cron 4종 read-only 노출 (Wave 1 T3, 2026-05-07)  (활성)
   ├─ /health              시스템 상태                                     (v1.x deferred)
   ├─ /backups             백업 이력                                       (v1.x deferred)
   └─ /jobs                배치 작업 모니터링                               (v1.x deferred — `/system` 본문에서 4 cron 설정만 노출)
```

---

## 3. 대시보드 (핵심 지표)

> **MVP closure (admin-dashboard 트랙, 2026-05-07)**. 단일 endpoint `GET /api/admin/dashboard/summary`(`@PreAuthorize hasRole('ADMIN')`)가 8개 KPI를 envelope `{ summary: {...} }`로 반환. 프론트 `/admin` 페이지가 KPI 그리드를 렌더한다. 메트릭스 인프라는 도입하지 않음 — 기존 repository count + audit_log 24h native COUNT로 충분.

### 3.1 KPI (현재)

- [x] 등록 사용자 / 활성 사용자 — `users.total` / `users.active` (`User.deletedAt IS NULL`, `+ isActive=TRUE`)
- [x] 부서(전체/활성) — `departments.total` / `departments.active` (Department는 `is_active` 컬럼 부재 — 둘 다 `deletedAt IS NULL`)
- [x] 활성 폴더 수 — `folders.active` (`Folder.deletedAt IS NULL`)
- [x] 활성 / 휴지통 파일 수 — `files.active` / `files.trashed` (`File.deletedAt IS NULL` vs `IS NOT NULL`)
- [x] 24시간 감사 이벤트 수 — `audit.last24h` (`audit_log` JdbcTemplate `COUNT(*) WHERE occurred_at >= now()-24h`, `Clock` 주입)
- [x] 스토리지 사용량 — `storage.usedBytes` (`SUM(file_versions.size_bytes)` 모든 버전 누적, current/older 미구분)

### 3.2 v1.x deferred (실시간 지표/알림)

- [ ] 오늘 업로드/다운로드 수 — *v1.x deferred (별도 카운터 미구현)*
- [ ] 대기 중인 바이러스 스캔 수 — *v1.x deferred (AV 미도입)*
- [ ] 쿼터 80% 초과 사용자 — *v1.x deferred (quota 시스템 미구현)*
- [ ] 바이러스 감지 — *v1.x deferred*
- [ ] 비정상 다운로드 패턴 (한 사용자가 1시간 내 1000건+) — *v1.x deferred*
- [ ] 권한 변경 대량 발생 — *v1.x deferred*
- [ ] 로그인 실패 급증 — *v1.x deferred (LoginAttemptTracker는 단일-사용자 lockout만)*

---

## 4. 사용자 관리

> **MVP closure (admin-user-mgmt + admin-user-search-update, 2026-05-06)**. backend `User` 도메인은 v0 (A14/A16 closure)이고, admin frontend `/admin/users` 페이지는 초대 + 목록(검색·역할·활성/비활성·표시 이름) 운영 가능. 부서/쿼터/일괄작업은 v1.x.

### 4.1 사용자 목록

- [x] **검색** — 이메일/표시 이름 부분 매칭(case-insensitive, LIKE escape) — admin-user-search-update.
- [x] **페이지네이션** — page/size 50 기본, max 200.
- [x] **활성/비활성 모두 표시** — soft-delete 사용자만 제외.
- [ ] 필터: 부서, 역할, 활성 상태, 쿼터 사용률 — *v1.x deferred*
- [ ] 정렬: 이름, 마지막 로그인, 저장 사용량 — *v1.x deferred*
- [ ] 일괄 작업: 비활성화, 부서 변경, 쿼터 변경 — *v1.x deferred*

### 4.2 사용자 상세

- [ ] 기본 정보 (이름, 이메일, 부서, 역할) — *v1.x deferred*
- [ ] 쿼터 / 사용량 — *v1.x deferred (quota 시스템 미구현)*
- [ ] 최근 활동 (audit_log에서 조회) — *v1.x deferred (admin UI 미구현)*
- [ ] 소유 파일 수 / 공유 현황 — *v1.x deferred*
- [ ] 로그인 이력 — *v1.x deferred*

### 4.3 사용자 비활성화 / 재활성화 / 표시 이름 편집

```text
UX 의미 분리:
  - 비활성화(deactivate, 제재)  → admin.user.deactivated
    → 로그인만 차단, 소유 파일 유지, self-deactivate 차단(403 SELF_PROTECTION)
  - 재활성화(reactivate)         → admin.user.updated (isActive false→true)
  - 표시 이름 편집               → admin.user.updated (displayName 변경)
  - 역할 변경                    → admin.role.changed (self-demote 차단)

audit 분리 이유: deactivated는 제재 의미를 가지므로 별도 enum으로 시각적 구분.
나머지 일반 속성 변경은 admin.user.updated로 흡수(reactivate 포함).
```

> **MVP 상태**: 모두 `/admin/users`에서 운영 가능 (admin-user-mgmt + admin-user-search-update, 2026-05-06). 파일 소유권 이관 UI는 v1.x.

### 4.4 사용자 Import

- [ ] CSV 포맷 명세 — *v1.x deferred*
- [ ] SCIM 2.0 — *v1.x deferred*

---

## 5. 부서 관리

> **MVP CRUD wired (Wave 2 T4 admin-department-crud, 2026-05-06)** — flat list 기반 생성·이름변경·비활성/재활성. 조직도 트리(LTREE 기반 이동/합병/분리)는 v1.x deferred.

### 5.1 활성 — `/admin/departments` (Wave 2 T4)

- 라우트: `/admin/departments` (AdminSideNav '부서' 활성).
- 가드: `@PreAuthorize("hasRole('ADMIN')")` (backend 진실, frontend는 UX용).
- 화면 구성:
  - **상단 생성 폼** — 이름 입력(1~100자, trim) + "추가" 버튼. 409 인라인 에러("같은 이름의 활성 부서가 이미 존재합니다").
  - **검색 input** — 300ms debounce, `LOWER(name) LIKE` 부분 일치(`q.trim().toLowerCase()`). 검색어 변경 시 page=0 리셋.
  - **목록 표 + pagination** — 활성/비활성 모두 표시(reactivate UX), createdAt DESC 정렬, page=50.
  - **rename inline form** — 행 단위 "이름 변경" → input + 저장/취소. 동일 이름 저장은 no-op.
  - **(de)activate toggle** — 활성 → "비활성화", 비활성 → "재활성화". soft-delete 의미 등가(`deletedAt = NOW()` / `null`).
- API: `GET/POST/PATCH /api/admin/departments` (docs/02 §7.12).
- Audit: `admin.department.created/updated/deactivated` 3종(AFTER_COMMIT, docs/03 §4.1).
- DB 제약: V9 `idx_departments_name_active` partial unique(`WHERE deleted_at IS NULL`) — 활성 이름 충돌 차단(CLAUDE.md §3 원칙 6).
- 에러 매핑: 409 DEPARTMENT_CONFLICT(create/rename/reactivate) / 404 NOT_FOUND(target dept) / 400 VALIDATION_ERROR(empty body, length) / 403(ADMIN 아님).

### 5.2 v1.x deferred

- [ ] 조직도 트리 편집 (드래그로 부서 이동, LTREE path 활용) — *v1.x deferred (V7 schema 도입만)*
- [ ] 부서 합병 / 분리 — *v1.x deferred*
- [ ] 부서 기반 권한 일괄 부여 — *v1.x deferred (현재 권한 부여는 subject_type=department + 단건 API 가능, A11/A16 closure)*
- [ ] 부서 해산 시 구성원 이관 flow — *v1.x deferred (현재 deactivate 시 `users.department_id` 정리 미수행 — 비활성 dept 참조 row는 권한 평가 미스매치로 자연 제외)*

---

## 6. 스토리지 관리

### 6.1 쿼터 정책

> **v1.x deferred → quota mutation 5-phase track으로 분할 진행 (Phase 1: 본 PR — spec drift 정정)**.
>
> 현재 impl 상태:
> - `users.storage_quota` / `users.storage_used` 컬럼은 `docs/02 §2.1` schema에 정의돼 있으나 **V2~V17 어디에도 ALTER 미적용**. `V2__users_auth.sql` 주석 "후속 phase에서 추가"가 ground truth — Phase 2 V18에서 도입.
> - upload path의 `413 QUOTA_EXCEEDED` 분기(`docs/02 §7.6` POST `/api/files`·`/api/files/upload`·`/api/files/:id/versions` + `docs/02 §8` 에러 contract)는 표에 정의돼 있으나 **enforcement 미구현 — Phase 5 도입**. MVP는 `spring.servlet.multipart.max-file-size=100MB` (per-request cap)로만 1차 보호.
> - frontend `api.getStorageQuota` (`frontend/src/lib/api.ts`)는 mock placeholder (75% 사용 고정). Phase 4 frontend 트랙에서 실 endpoint로 교체.

```text
기본 쿼터: 사용자당 10GB (config)         ← v1.x deferred (V18 도입 시 활성)
부서 쿼터: 부서 전체 총합 제한 (optional)  ← v1.x deferred (Phase 2+ scope 외)
쿼터 초과 시 동작: 업로드 차단 (413 응답)  ← v1.x deferred (Phase 5 enforcement)
```

**5-phase plan**:

1. **Phase 1 (본 PR)** — spec drift 정정. `docs/02 §2.1` schema 두 라인에 V18 미도입 callout, `docs/04 §6.1` 잘못된 "quota_bytes" 컬럼명 정정 + 단계 plan 통일.
2. **Phase 2** — `V18__user_storage_quota.sql`: `ALTER TABLE users ADD storage_quota BIGINT NOT NULL DEFAULT 10737418240, storage_used BIGINT NOT NULL DEFAULT 0`. 기존 row backfill 자동 (DEFAULT).
3. **Phase 3** — backend: `AdminUserQuotaService` + `PUT /api/admin/users/{id}/quota` (admin only) + `AuditEventType.USER_QUOTA_UPDATED` + listener. slice test + service test.
4. **Phase 4** — frontend: `/admin/members` quota 컬럼 또는 별도 `/admin/quota` 페이지 — single-row inline editor (trash-retention-mutation `RetentionPolicyEditor` 패턴). `useUpdateAdminUserQuota` hook + `api.getStorageQuota` mock 제거 + 실 endpoint 호출.
5. **Phase 5** — enforcement: upload path (`FileUploadController.create`, `FileVersionService.create`, tus init) 진입에서 `users.storage_used + payload.size > storage_quota` 검증 → `413 QUOTA_EXCEEDED`. 성공 시 `UPDATE users SET storage_used = storage_used + size WHERE id = ?` 트랜잭션 (CLAUDE.md §3 원칙 7 `FOR UPDATE`).

### 6.2 사용량 대시보드

- [ ] 전체 사용량 / 한도 — *v1.x deferred (quota 시스템 미구현)*
- [ ] 부서별 사용량 차트 — *v1.x deferred*
- [ ] 상위 사용자 리스트 — *v1.x deferred*
- [ ] 월별 증가 추세 — *v1.x deferred*

### 6.3 고아 객체 정리

> **MVP 부분 구현 (ADR #38, 2026-05-02)**: `storage.orphan.cleanup` cron 활성. `application.yml`의 `app.storage.orphan-cleanup.enabled=false` default — 운영자가 staging/prod에서 명시 enable. 알고리즘과 audit는 §13 [§] footnote 참조.

```text
storage 객체 중 DB에 file_versions row가 없는 것 = orphan
  → MVP cron `storage.orphan.cleanup` 매일 01:00 (KST), enabled=false default
  → grace=24h 이전 객체만 후보 (in-flight 업로드 race 회피)
  → STORAGE_ORPHAN_CLEANED audit summary 1건/run

DB row 중 storage 객체가 없는 것 = phantom
  → v1.x deferred (감지 + 알림 잡 미구현)
```

- [x] orphan 정리 cron 구현 — `storage.orphan.cleanup` (ADR #38, `app.storage.orphan-cleanup.*`)
- [ ] 관리자 트리거 endpoint (HTTP `/api/admin/storage/orphan/run`) — *v1.x deferred (cron으로 1차 충분)*
- [ ] phantom 감지 + 알림 — *v1.x deferred*
- [x] 시스템 합계 + 정리 기록 overview UI — `/admin/storage` (admin-storage-overview, 2026-05-07)

### 6.4 시스템 합계 overview (`/admin/storage`)

> **MVP 구현 (admin-storage-overview, 2026-05-07)**: 읽기 전용 단일 페이지. quota/부서별 차트(§6.2)는 v1.x deferred 유지.

- 진입점: `AdminSideNav` "스토리지" → `/admin/storage` (ADMIN role only)
- API: `GET /api/admin/storage/overview` (`@PreAuthorize("hasRole('ADMIN')")`, 읽기 전용)
- 응답 envelope: `{ overview: { totalFiles, totalVersions, totalBytes, trashedFiles, trashedBytes, orphanCleanup?: { lastRunAt, lastDeletedCount } } }`
- 데이터 의미:
  - `totalFiles` — `files.deleted_at IS NULL` (활성 파일 수)
  - `totalVersions` — `file_versions` 전체 row 수 (휴지통 포함, 버전 히스토리 보존)
  - `totalBytes` — 모든 `file_versions.size_bytes` 합 (실제 storage 점유의 가장 가까운 근사)
  - `trashedFiles` / `trashedBytes` — `files.deleted_at IS NOT NULL` count + 그 파일들의 `size_bytes` 합
  - `orphanCleanup` — `audit_log` 마지막 `storage.orphan.cleaned` 이벤트 (없으면 `null`)
- TanStack Query: `qk.adminStorageOverview()`, `staleTime=30s`, `retry: false` (401/403 즉시 노출)

---

## 7. 감사 로그 UI

> **Status**: M12 wired (2026-05-01 closure, `m12-audit-ui-closure`). frontend `/admin/audit/logs`(M12 트랙, 2026-04-25 mock 도입) → A2.6에서 `api.getAuditLogs`를 backend `GET /api/admin/audit`(docs/02 §7.12) 실연결로 교체. M12 closure는 `page.tsx` stale docblock 정정 + 본 §7 상태 표기 동기화로 마감.

### 7.1 필터

- [x] 시간 범위 — `dateFrom`/`dateTo` (datetime-local)
- [x] 행위자 (`actorId`)
- [x] 이벤트 타입 (`eventType` enum mirror, docs/03 §4.1)
- [ ] 대상 리소스 — frontend filter 미수용 (`AuditLogFilters` 신규 필드 + backend query param 추가 필요, v1.x deferred)
- [ ] IP 주소 — 동상 (frontend filter 미수용, v1.x deferred)

### 7.2 내보내기

- [x] CSV 다운로드 — `AuditCsvWriter` (RFC 4180 quoting + UTF-8 BOM + `\r\n`, `text/csv; charset=utf-8` MIME)
- [x] 대상 기간 / 필터 조건 포함 — `AuditQueryFilters` 전체 (eventType / actorId / targetType / targetId / dateFrom / dateTo) query string 그대로 전달
- [x] **server-side full-result 스트리밍** — Wave 1 T2: `GET /api/admin/audit/export` (`StreamingResponseBody`), 하드 캡 10,000행 + `LIMIT cap+1` truncation 감지, 초과 시 `X-Audit-Export-Truncated: true` 헤더. `format=csv|json` 분기(audit-export-json 트랙, 2026-05-08) — default csv, json은 plain array(metadata는 nested object).
- [x] **`audit.exported` runtime emission** — Wave 1 T2: `AuditExportListener` (`@EventListener` + `@Transactional(REQUIRES_NEW)`), metadata에 `filters` / `rowCount` / `truncated` / `format`(`"csv"` 또는 `"json"`, audit-export-json 트랙) — append-only 보존을 위해 export read 트랜잭션과 분리.
- [x] JSON 다운로드 — audit-export-json 트랙(2026-05-08): `GET /api/admin/audit/export?format=json` 응답으로 `application/json` plain array. `AuditJsonWriter`(`ObjectMapper.writeValue`) 단일 호출, BOM 미부착. 진정한 SQL → JSON streaming은 v1.x deferred.

### 7.3 상세 뷰

- [ ] before/after 상태 diff 표시 (v1.x deferred — 현재는 raw JSON 표기만)
- [ ] 관련 이벤트 연결 (같은 세션 내 이벤트, v1.x deferred)

---

## 8. 휴지통 정책

### 8.1 전역 설정

```text
기본 보존 기간: 30일
관리자 조정 가능: 7~90일
Legal Hold 대상: 영구 보존 (정책과 무관)
```

### 8.2 크론 작업

> **MVP 구현됨 (A7 + ADR #31/#38)**. 상세는 §13 [†][§] footnote 참조.

- [x] 매일 자정 `purge_after < NOW()` 대상 영구 삭제 — A7 (`app.purge.*`, default `enabled=false`, `cron=0 0 0 * * *`)
- [x] storage 객체 + DB row 모두 삭제 — DB는 A7 hard purge, storage 객체는 별도 cron `storage.orphan.cleanup` (ADR #38, 매일 01:00)
- [x] audit_log에 `file.purged` 기록 — `SYSTEM_PURGE_EXECUTED` summary 1건/run + 개별 manual purge는 `FILE_PURGED`
- [ ] Legal Hold 대상은 스킵 — *v2.x deferred (Legal Hold 미구현)*

### 8.3 전역 휴지통 뷰

- [x] 전체 사용자의 휴지통 파일 (관리자 전용) — `/admin/trash/all` (Wave 2 T9, 2026-05-07)
  - 목록: `GET /api/admin/trash` (admin DTO: owner/originalParent/size + V10 `deletedById`/`deletedByEmail` 노출 — docs/02 §7.11, §6.5.1)
  - 단건 복원/영구삭제: 기존 endpoint 재사용 (`POST /api/files|folders/{id}/restore` + `DELETE /api/trash/{type}/{id}` — ADMIN ROLE이 SpEL 가드 통과)
  - 정책 viewer (`/admin/retention`, T7-P2 rename) — wave2-trash-policy-viewer (Wave 2 T9 follow-up, 2026-05-09): 현재 보존 일수 + 변경 절차 + cron cross-link.
  - **정책 mutation UI** — trash-retention-mutation (Phase A spec 2026-05-11, Phase B/C 별도 PR): V17 `trash_policy` single-row 테이블 + `PUT /api/admin/trash/policy` + frontend mutation editor + `RETENTION_POLICY_CHANGED` audit. **단일-approver MVP** — 단일 ADMIN 즉시 적용. 변경은 신규 soft-delete만 적용 (기존 `purge_after` 재계산 안 함, hard purge 폭증 회피).
  - 2인 승인 framework: v1.x deferred (§15.4 — `app.dual-approval.retention-change.enabled`. 도입 시 본 endpoint hook point로 사용).
- [x] 일괄 복원·영구삭제 (admin-trash-bulk, Wave 2 T9 follow-up, 2026-05-08)
  - endpoint: `POST /api/admin/trash/bulk` (`action: 'restore' | 'purge'` + `items: 1..200`, docs/02 §7.11)
  - UI: 행 좌측 체크박스 + 헤더 select-all (페이지 한정) + BulkActionBar (선택 N개 / 전체 해제 / 일괄 복원 / 일괄 영구삭제). 일괄 영구삭제는 ConfirmDialog 거침.
  - 결과: "성공 N개, 실패 M개" banner + 부분 실패 시 자세히 펼치기(failed 항목 type/id/error 노출).
  - 부분 실패 모델: 한 항목 NAME_CONFLICT가 다른 199개를 막지 않음 — 30일 만료 직전 일괄 정리, 대량 오삭제 일괄 복원 시나리오 정상 흡수.
  - audit 영향 0 — per-item 기존 emit 그대로 (FILE_RESTORED / FOLDER_RESTORED / FILE_PURGED / FOLDER_PURGED). 동일 actor + 근접 timestamp로 묶음 식별 가능.
- [x] 폴더 subtree size (Wave 2 T9 follow-up, 2026-05-08 — folder-subtree-size)
  - `AdminTrashItemDto.sizeBytes`가 folder에서도 not-null로 채워진다 — 자기 자신 + 모든 하위 폴더의 file size 합. 빈 폴더는 0.
  - 단일 재귀 CTE batch lookup (`AdminTrashRepository.findFolderSubtreeSizes`) — 페이지의 trashed folder ids 전체를 한 번에 처리. depth cap 100 (cycle 방지).
  - 운영 가치: 휴지통에서 큰 폴더를 식별해 우선 복원/영구삭제 결정 가능.
- [x] 원위치 절대 경로 (Wave 2 T9 follow-up, 2026-05-09 — full-path-resolve)
  - `AdminTrashItemDto.originalParentPath`가 부모 폴더의 절대 경로(leading `/`, trailing slash 없음 — 예: `/회사/팀A/문서`)로 채워진다. 부모 row가 없으면 null이며, UI는 `originalParentName` 단일 segment를 fallback으로 표시.
  - 단일 재귀 CTE batch lookup (`AdminTrashRepository.findFolderAncestorPaths`) — 페이지에 노출된 모든 부모 ids를 한 번에 처리. 살아있는/삭제된 부모 모두 chain 추적(휴지통 부모 row 보존 정책 활용). depth cap 100.
  - UI: 행의 "원위치" cell이 path를 우선 표시하고 `title` tooltip으로 hover 전체 경로 노출. column max-w + truncate로 긴 경로 처리.
  - 운영 가치: 같은 이름의 폴더가 여러 위치에 존재하는 환경에서 운영자가 항목 식별을 즉시 할 수 있다 (이전에는 단일 segment name만 노출).
- [x] 삭제일 범위 필터 (Wave 2 T9 follow-up, 2026-05-08 — `deletedFrom`/`deletedTo`)
  - wire: `?deletedFrom=YYYY-MM-DD&deletedTo=YYYY-MM-DD` (date-only)
  - 경계: KST(`Asia/Seoul`) 기준 — `deletedFrom`은 해당일 KST 0시(inclusive), `deletedTo`는 입력일+1의 KST 0시(exclusive, 즉 입력일 KST 종일 포함). 운영자 wall-clock과 일치.
  - 양쪽 모두 적용 시 `deletedFrom < deletedTo`여야 함 — 위반 시 400.
- [x] `deletedBy` 컬럼 (Wave 2 T9 follow-up, 2026-05-08, V10)
  - 응답 예시:
    ```json
    {
      "id": "11111111-...", "name": "report.pdf", "type": "file",
      "deletedAt": "2026-05-08T03:14:00Z", "purgeAfter": "2026-06-07T03:14:00Z",
      "ownerId": "aaaa-...", "ownerEmail": "alice@example.com",
      "originalParentId": "ffff-...", "originalParentName": "Reports", "originalParentPath": "/회사/팀A/Reports",
      "sizeBytes": 12345,
      "deletedById": "bbbb-...", "deletedByEmail": "admin@example.com"
    }
    ```
  - UI 테이블에 "삭제자" 컬럼 (크기 ↔ 삭제일 사이). NULL은 em dash "—".
  - **Backfill cutoff**: V10 적용(2026-05-08) 이전에 휴지통에 들어간 row는 `deletedBy IS NULL`로 영구 유지 (backfill 미실시). UI는 컷오프 이전을 "—"로 렌더한다.
  - NULL 의미: (a) V10 이전 삭제분, (b) deleter 계정 hard-delete (FK ON DELETE SET NULL), (c) 시스템/cron 자동 삭제 — 모두 동일 표기.
- [ ] 긴급 복원 (사용자 요청 시) — *v1.x deferred (사용자 본인 복원은 구현됨, A6 closure; cross-owner 관리자 복원은 Wave 2 T9 closure)*
- [ ] 즉시 영구 삭제 (승인 워크플로) — *v1.x deferred (단건 영구 삭제는 Wave 2 T9 closure, 승인 워크플로는 v1.x)*

---

## 9. 정책 관리

### 9.1 파일 크기 / 확장자

> **MVP 부분 구현**: 파일 크기는 `application.yml` 단일 정책. 확장자 정책은 §5.3 v1.x deferred (사내 베타 + Content-Disposition: attachment 1차 방어).

```text
최대 파일 크기: 100MB (`spring.servlet.multipart.max-file-size`, ADR #36 단일-POST multipart cap)
              ↳ MVP: per-request cap만 적용 (사용자 quota 미구현)
허용 확장자: v1.x deferred (외부 출시 시점에 화이트리스트 편집 UI 도입)
차단 확장자: v1.x deferred
```

### 9.2 보존 정책

> **MVP 거의 완전 구현**: 휴지통 보존은 A7 cron + DB-backed `trash_policy` 테이블(V17, trash-retention-mutation)로 운영자 무중단 제어. 버전 보존은 v1.x.

```text
삭제된 파일 보존: 30일 default → 운영자 무중단 변경 (DB-backed runtime mutation)
              ↳ Phase A: yml `app.trash.retention.days` (외부화, wave2-trash-retention-config)
              ↳ Phase B/C: V17 single-row 테이블 `trash_policy` (id=1, retention_days CHECK 7..90)
              ↳ 변경 endpoint: PUT /api/admin/trash/policy (단일-approver MVP)
              ↳ 변경 영향: 신규 soft-delete만 새 일수 적용. 기존 trash row의 `purge_after`는 재계산 안 함.
              ↳ 0/음수 입력은 backend 사전 검증으로 400 (CHECK 제약과 동기, docs/02 §2.12)
              ↳ hard delete는 별개 cron — A7 `app.purge.cron`, `app.purge.max-per-run`
              ↳ 2인 승인은 v1.x++ deferred (§15.4)
버전 보존: v1.x deferred (현재 모든 버전 영구 보관, A5 closure)
감사 로그 보존: 영구 — DB-level append-only (`V4__audit_log_revoke.sql`).
              ↳ 파티션/아카이빙은 v1.x (ADR #9)
```

### 9.3 감사 레벨 지정

- [ ] 폴더별 `audit_level` 지정 UI — *v1.x deferred (ADR #9 audit_level 컬럼 미구현)*
- [ ] strict 지정 시 하위 상속 — *v1.x deferred*
- [ ] 변경 자체를 audit_log에 기록 — *v1.x deferred (`FOLDER_AUDIT_LEVEL_CHANGED` enum 정의됨, emit 미구현)*

---

## 10. Legal Hold (법적 보존)

> **Status: v2.x deferred** (docs/00 §4.3, ADR #46). 본 절은 **운영 명세 — 활성화 = v2.x 진입**. 보안/컴플라이언스 명세는 docs/03 §6.3, 데이터 모델은 docs/02 §2.10.

### 10.1 대상 지정

#### 10.1.1 대상 유형

- **개별 파일** (`target_type='file'`): 단일 file row
- **폴더** (`target_type='folder'`): folder + 모든 후손 folder/file 시점 스냅샷 cascade. hold 후 신규 업로드 file은 ancestor folder hold 검사 후 `legal_hold=TRUE`로 자동 INSERT
- **사용자** (`target_type='user'`): 해당 user 소유의 모든 file/folder (owner_id 매칭)

#### 10.1.2 입력 필드 (place 폼)

| 필드 | 필수 | 검증 |
|---|---|---|
| 대상 유형 (file/folder/user) | ✅ | 라디오 |
| 대상 선택 | ✅ | 검색 콤보 — 파일/폴더는 path 기반, 사용자는 displayName 기반 (UserSearchCombobox 재사용) |
| 사유 | ✅ | textarea, 최소 10자, 최대 1000자 — 사건번호/케이스 ID 등 추적 가능한 정보 권장 |
| 만료일 (optional) | ❌ | datetime-local, 미입력=무기한 |

> **태그 기반 hold (예: "소송 ABC" 묶음)**: v2.x 1차 컷 미포함. `metadata.caseId` 자유 텍스트 검색은 admin 페이지 필터에서 reason LIKE로 대체 가능. 정식 tag 도메인은 별도 트랙(backlog).

### 10.2 Hold 상태 동작

#### 10.2.1 UI 표현 (모든 사용자에 노출)

- 파일/폴더 detail 카드에 **"⚖ Legal Hold"** 배지 (붉은 외곽선) + 호버 시 사유/지정자/지정일 노출 (사유 본문은 `MANAGE_LEGAL_HOLD` 권한자만, 그 외에는 "법적 보존 중" 단순 메시지)
- 휴지통/이동/이름변경/삭제 버튼 **비활성** + 호버 툴팁 "법적 보존으로 인해 작업할 수 없습니다"
- RightPanel(file detail)에 active hold 메타 카드 (지정자/지정일/만료일/사유 — 권한자 한정)
- 검색 결과/리스트 row에도 ⚖ 아이콘 prefix

#### 10.2.2 백엔드 차단 (진실의 출처)

→ docs/03 §6.3.3 차단 액션 매트릭스. 9개 mutation entry 모두 423 `LEGAL_HOLD_VIOLATION`.

> **CLAUDE.md §3 핵심 원칙 10**: 프론트 비활성은 UX 게이트, 백엔드 423이 보안 게이트. 직접 fetch 또는 curl 호출도 동일하게 거부.

#### 10.2.3 Hard purge 스킵

ADR #31 `HardPurgeService` 후보 SQL에 `AND legal_hold IS NOT TRUE` 1줄 추가. 휴지통 30일 경과한 row라도 hold 활성이면 purge 보류.

> **30일 휴지통 + hold**: 휴지통 이동 자체가 차단되므로 정상 흐름에서 휴지통+hold 동시 상태는 발생하지 않음. 단, hold place **이전에 이미 휴지통 상태**였던 row에 user-target hold가 cascade된 케이스는 가능 — 이 row는 hold 동안 30일 카운트가 멈춘 효과 (purge 보류). release 후 만료 카운트 재개.

#### 10.2.4 Trash UI

전역 휴지통(`/admin/trash`, Wave 2 T9) 결과 row에 hold 활성이면 ⚖ 배지 + 복원/영구삭제 버튼 비활성.

### 10.3 해제 워크플로

#### 10.3.1 단일 admin 모드 (default — `app.legal-hold.dual-approval.enabled=false`)

1. 관리자가 hold 상세 페이지에서 "해제" 클릭
2. release 사유 입력 dialog → `DELETE /api/admin/legal-holds/:holdId` (`releaseReason`)
3. 트랜잭션 내: `legal_holds.released_at/by/reason` set + cache flag clear (cascade) + `admin.legal_hold.released` audit 1건
4. 즉시 토스트 "해제 완료 (영향 N개)"

#### 10.3.2 Dual-approval 모드 (`enabled=true`)

| 단계 | 액션 | 결과 |
|---|---|---|
| 1 | primary admin이 release 요청 (`DELETE /api/admin/legal-holds/:holdId`) | `dual_approval_status = 'pending'` + secondary 후보 admin들에게 이메일 알림 (`EmailService` 재사용 — ADR #42/45) |
| 2 | secondary admin이 승인 (`POST .../approve` decision=approve) | `released_at` set + cache flag clear + `admin.legal_hold.released` audit (`metadata.dualApproval=true, secondaryApproverId=...`) |
| 2' | secondary admin이 거부 (decision=reject) | `dual_approval_status = NULL` 복귀, hold 유지, primary에게 거부 알림 |

**self-approval 차단**: secondary admin은 primary와 다른 user여야 함 (`secondary_approver_id != placed_by AND != primary_release_actor`). 동일 인물이 approve 시도 시 403.

#### 10.3.3 30일 재지정 락

release 직후 동일 (target_type, target_id)에 대한 place 시도는 거부 (`409 LEGAL_HOLD_RECENTLY_RELEASED`). UI는 토스트 + "재지정 가능 일자: YYYY-MM-DD" 표시.

회피 방법:
- 의도적 즉시 재지정 = `app.legal-hold.replace-cooldown-days=0`으로 환경별 설정
- 또는 다른 reason/target으로 새 hold 생성

### 10.4 만료 cron

`app.legal-hold.expiration.{enabled, cron, batch-size, zone}` properties (default `enabled=false`).

`expires_at <= NOW()` row 자동 release + `admin.legal_hold.expired` audit (`actor_id=NULL`, `metadata.trigger='system.expiration'`). share-expired-cron / permission-expired-cron 동형 운영.

운영 권장:
- staging/prod에서 expiration 활용 시 `enabled=true` + `cron='0 */5 * * * *'` (5분 주기)
- 만료 임박 hold는 admin 대시보드에서 별도 카드로 노출 (KPI §3.1 추가 후보)

### 10.5 관리자 페이지 (`/admin/legal-holds`)

#### 10.5.1 진입 권한

`hasRole('ADMIN')` (보안 게이트) + sideNav 항목은 `ROLE=ADMIN`만 표시 (`AdminSideNav.tsx` 기존 placeholder 활성화). AUDITOR는 read-only로 active hold **목록 조회만** 가능 (별도 view 페이지, place/release 버튼 비활성).

#### 10.5.2 목록 (`/admin/legal-holds`)

| 컬럼 | 비고 |
|---|---|
| 대상 | type 아이콘 + path/displayName |
| 사유 | truncate 80자, hover tooltip 전체 |
| 지정자 / 지정일 | placedBy.displayName · placedAt |
| 만료일 | expiresAt or "무기한", 임박(7일 이내)은 노란 배지 |
| 상태 | active / pending(dual-approval) / released(필터로만) |
| 액션 | 상세 / 해제(권한자) / 승인(pending이고 본인 승인 가능 시) |

필터: 대상 유형, 지정자, reason 키워드, 만료 범위, 상태(active/pending/released).

#### 10.5.3 상세 페이지 (`/admin/legal-holds/:holdId`)

- hold 메타 (전체 필드)
- cascade 영향 목록 (folders/files 트리 — 스크롤 가능, 200건 cap)
- 관련 audit timeline (place, mutation_blocked 시도들, release 등)
- 액션 버튼: 해제 / 승인 / 거부 (상태/권한별)

### 10.6 운영 런북 진입 (Legal Hold 발동 절차)

> **베타 운영 런북** (§15) 미반영 항목 — Legal Hold 활성화 시 §15에 sub-section 추가 권장.

#### 10.6.1 외부 요청 도착 시 (e.g. 법무팀 요청)

1. 사건번호/요청 출처 기록 (티켓 ID 또는 메일 archive)
2. 대상 식별 — 사용자/폴더/파일 명확히 (모호 시 법무팀과 재확인)
3. `/admin/legal-holds` → place — reason에 사건번호 + 요청 출처 명시
4. `cascadeAffected` 확인 후 영향 사용자에게 사내 공지 (cascade가 활성 사용자 작업을 막을 경우)
5. audit log export (CSV/JSON, docs/04 §7.2) — 법무팀 인계용

#### 10.6.2 hold 활성 동안

- 해당 자료에 대한 사용자 문의는 운영팀 → 법무팀으로 라우팅
- 만료일 1주 전 자동 알림 (cron이 enable되어 있으면 expired audit으로 자동 처리, 그 외 admin 수동 release)
- `admin.legal_hold.violation_blocked` audit 추적 — 빈도 높으면 추가 cascade가 필요한 신호일 수 있음

#### 10.6.3 release

- 법무팀 release 승인 문서 archive
- dual-approval 모드면 primary/secondary admin 사전 합의 + 2단계 승인
- release 후 30일 락 — 재지정 필요하면 `app.legal-hold.replace-cooldown-days=0`로 일시 조정 (config 변경 + audit log에 운영 노트)

### 10.7 v2.x 진입 시 작업 분해

→ `dev/active/legal-hold-design/` plan/tasks 참조. docs/03 §6.3.10 8단계와 동일.

---

## 11. 백업 / 복구

> **MVP 결정**: 백업/복구는 인프라 운영 책임 (managed Postgres + 운영자 절차). 본 §11은 v1.x 시점 본문화 대상. 베타 출시 게이트는 `BETA-RELEASE.md`.

### 11.1 백업 전략

```text
DB:
  - 일일 전체 스냅샷 → managed Postgres / RDS 자동 (운영 책임)
  - PITR 활성화 (managed 옵션)
  - cold storage 보관: v1.x 운영 절차

storage 객체 (LocalFs):
  - MVP: 파일시스템 백업 (rsync/snapshot)은 운영 책임
  - S3 도입 후: Cross-region replication / 버전 관리 / Lifecycle (v1.x)

감사 로그:
  - DB 내 append-only (`V4__audit_log_revoke.sql`)
  - 별도 버킷 + WORM은 v1.x (S3 도입 후)
```

### 11.2 복구 시나리오

- [x] 사용자 실수 (단일 파일): 휴지통 복원 — A6 closure (`/api/files/{id}/restore`, `/api/folders/{id}/restore`)
- [ ] 관리자 실수 (대량 삭제): 감사 로그 기반 롤백 스크립트 — *v1.x deferred (audit_log에 before_state 보유, 스크립트는 별도)*
- [ ] 데이터 손상 (일부): PITR + 객체 버전 복원 — *운영 (managed Postgres + S3 도입 후)*
- [ ] 재해 (전체): cross-region 재구축 — *운영 (인프라 책임, 외부 출시 시점)*

### 11.3 복구 훈련

- [ ] 분기별 복구 drill (관리자) — *운영 (외부 출시 시점)*
- [ ] RTO / RPO 측정 — *운영*

---

## 12. 모니터링 / 알림

> **운영 (전체)**. metrics 인프라(Prometheus/Grafana 등) 외부 책임. MVP는 application logs(`com.ibizdrive` INFO) + audit_log 직접 조회로 대체. 외부 출시 시점에 metrics export endpoint(`/actuator/prometheus`) 도입 검토.

### 12.1 시스템 지표

- [ ] API 응답 시간 (p50/p95/p99) — *운영 (Spring Boot Actuator + Micrometer 도입 시점)*
- [ ] 에러율 (5xx) — *운영*
- [ ] DB 커넥션 풀 사용률 — *운영 (HikariCP 메트릭 expose)*
- [ ] storage 업로드/다운로드 처리량 — *운영*

### 12.2 비즈니스 지표

- [ ] DAU / MAU — *v1.x deferred (audit_log 집계로 도출 가능)*
- [ ] 업로드/다운로드 건수 추이 — *v1.x deferred (audit_log 집계)*
- [ ] 부서별 사용 패턴 — *v1.x deferred*

### 12.3 알림 채널

- [ ] 즉시 알림: 장애, 보안 이벤트 — *운영 (사내 베타 → 슬랙 webhook으로 충분)*
- [ ] 일일 리포트: 쿼터/사용량 — *v1.x deferred (quota 시스템 미구현)*
- [ ] 주간 리포트: 트렌드 — *v1.x deferred*

---

## 13. 배치 작업 (Jobs)

> **MVP 상태 (mvp-qa-security closure, 2026-05-02)**: 4개 cron 활성 (`purge.expired`, `share.expire`, `permission.expire`, `storage.orphan.cleanup`). 모두 default `enabled=false` — 운영자가 staging/prod에서 명시 enable. 5개는 v1.x.
>
> **현재 설정 노출** (Wave 1 T3, 2026-05-07): admin 페이지 `/admin/system`에서 4개 잡의 `enabled/cron/zone/batchSize/maxPerRun/graceHours`를 read-only 카드로 노출 (docs/02 §7.12 `GET /api/admin/system/cron`). 변경은 application.yml + 재기동 — mutation endpoint는 v1.x deferred.
>
> **읽기 권한 확장** (Wave 1.5 `auditor-cron-readonly`, 2026-05-07): 백엔드 `GET /api/admin/system/cron` 가드를 `hasRole('ADMIN') OR hasRole('AUDITOR')`로 확장. 감사자가 외부 모니터링/스크립트로 cron 설정을 직접 확인 가능.
>
> **UI 진입 확장** (Wave 1.5 `auditor-admin-ui-access`, 2026-05-07): 프론트 `<AdminGuard>`에 `allowedRoles` prop을 도입하고 admin layout을 `['ADMIN','AUDITOR']`로 완화. AUDITOR는 `/admin/audit/logs`, `/admin/system`에 직접 진입 가능. 그 외 ADMIN-only 페이지는 페이지 단에서 default 가드로 다시 좁힌다(§1.1, §1.2). `AdminSideNav`도 AUDITOR에게는 두 항목만 노출.

| 작업 | 주기 | 상태 | 설명 |
|---|---|---|---|
| `purge.expired` | 매일 00:00 (KST) | **MVP** (`enabled=false` default) | `purge_after` 경과 folders/files DB hard delete (A7). **storage 객체 삭제는 별도 잡** (`storage.orphan.cleanup`, ADR #31/#38). [†] |
| `cleanup.tmp` | 매일 01:00 | *v1.x deferred* | storage tmp/ 24시간 경과 객체 삭제. MVP는 multipart spill threshold(`file-size-threshold=1MB`)로 OS temp가 자동 회수. S3 도입 시 별도 정책. |
| `scan.pending` | 5분마다 | *v1.x deferred* | 바이러스 스캔 대기 파일 처리. AV 미도입. |
| `storage.orphan.cleanup` | 매일 01:00 (KST) | **MVP** (`enabled=false` default) | storage 객체와 `file_versions.storage_key` 비교 후 `storage_key`에 없는 객체 hard delete. A7 cascade orphan + A15 트랜잭션 실패 잔존 객체 회수 (`storage-orphan-cleanup`, 2026-05-02). [§] |
| `quota.warning` | 매일 08:00 | *v1.x deferred* | 쿼터 80%+ 사용자에게 알림. quota 시스템 미구현. |
| `backup.snapshot` | 매일 02:00 | *운영* | DB 스냅샷. managed Postgres / RDS 자동 백업으로 대체 — 별도 cron 미구현. |
| `audit.archive` | 매월 1일 | *v1.x deferred* | 감사 로그 월별 파티션 아카이빙. ADR #9 audit_level/파티션 미구현. |
| `share.expire` | default 5분 | **MVP** (`enabled=false` default) | `shares.expires_at <= NOW() AND revoked_at IS NULL` row를 자동 만료 (`share-expired-cron`, 2026-05-01). [‡] |
| `permission.expire` | default 5분 | **MVP** (`enabled=false` default) | `permissions.expires_at <= NOW()` row를 자동 cleanup (`permissions-expired-cron`, 2026-05-01). [‡‡] |

> [†] `purge.expired` (A7) 정책 상세: docs/02 §7.11.1. **DB-only** (storage 모듈 부재) — S3 객체는 orphan으로 잔존, audit `after_state.orphanStorageKeys` (cap=1000)에 기록. Properties: `app.purge.{enabled, max-per-run, cron, zone}`. Audit: `SYSTEM_PURGE_EXECUTED` summary-only 1건/run. ROLE 없음 (system actor).
>
> [‡] `share.expire` 정책 상세: docs/02 §7.9.1. ADR #34 backlog closure. Properties: `app.share.expiration.{enabled(default false), batch-size(200), cron("0 */5 * * * *"), zone("Asia/Seoul")}`. 단위 처리(per-row 트랜잭션) — `ShareCommandService.expireShare(shareId)`가 `revoked_by=NULL` 시스템 트리거로 `revoked_at` set + `permissions` row delete. Audit `share.expired`는 `actor_id=NULL`, `metadata.trigger='system.expiration'`. 다중 인스턴스 안전(V6 row-level pessimistic lock).
>
> [‡‡] `permission.expire` 정책 상세: docs/02 §7.10.1. ADR #34 backlog closure. Properties: `app.permission.expiration.{enabled(default false), batch-size(200), cron("0 */5 * * * *"), zone("Asia/Seoul")}`. 단위 처리(per-row 트랜잭션) — `PermissionService.expirePermission(permissionId)`가 `lockById` (PESSIMISTIC_WRITE) → snapshot → DELETE (permissions 테이블에 `revoked_at` 부재로 soft-delete 불가). Audit `permission.expired`는 `actor_id=NULL`, `metadata.trigger='system.expiration'`. `findEffective`가 이미 `expires_at > NOW()` 필터링하므로 cron 가치는 (a) DB cleanup, (b) audit trail. 다중 인스턴스 안전(row-level pessimistic lock).
>
> [§] `storage.orphan.cleanup` 정책 상세: docs/02 §5.6. ADR #38. Properties: `app.storage.orphan-cleanup.{enabled(default false), cron("0 0 1 * * *"), zone("Asia/Seoul"), max-per-run(10000), grace-hours(24), batch-size(200)}`. 알고리즘: liveSet=`file_versions.storage_key` 전체 stream(NO `deleted_at` 필터, trash 보호) → `StorageClient.listOlderThan(grace=24h)` walk → diff → per-row delete(IOException isolation) → cap 도달 시 truncated=true. Audit: `STORAGE_ORPHAN_CLEANED` summary-only 1건/run, `actor_id=NULL`, target_type=`system`, `metadata={runId,scanned,candidates,deleted,failed,truncated,durationMs}`. MVP single-instance 가정 (`@SchedulerLock` 미도입). HTTP 운영 트리거 endpoint 미도입(backlog).

---

## 14. 관리자 액션의 감사

> **v1.x deferred**. admin frontend `/admin/{users,departments,permissions,storage,policies,system}` 페이지가 모두 미구현이라 emit 시점이 0. `AuditEventType.ADMIN_*` 7 enum 정의됨 (mvp-qa-security Phase 2 P2.2 검증), runtime emission은 admin frontend 트랙 도입 시점에 활성화.

```text
원칙: 관리자 액션일수록 감사 로그 강화

모든 관리자 UI 동작 = audit_log 기록
  예: admin.user.updated, admin.role.changed, admin.quota.changed,
      admin.legal_hold.placed, admin.policy.changed
   ↳ MVP: enum 정의만. emit은 v1.x admin frontend 트랙에서 활성화.

Audit 뷰는 actor_role 필터로 관리자 액션만 조회 가능
   ↳ MVP: M12 audit logs UI에 actor_role 필터 미구현 (v1.x)
```

---

## 15. 사내 베타 운영 런북 (Wave 2 closure, 2026-05-07)

> Wave 2 (T4~T9) closure 직후 사내 베타 출시를 위한 **운영 절차 매뉴얼**. 코드 변경 없이 admin frontend (`/admin/*`) + 운영 cron + audit_log 조회로 처리 가능한 5개 시나리오를 다룬다. 향후 v1.x에서 자동화/UI 개선 시 본 런북은 그 차선 경로의 fallback 으로 유지.
>
> 적용 시점: backend `application-prod.yml` cron 활성화 + ADMIN/AUDITOR 계정 프로비저닝 완료 후.

### 15.1 ADMIN cross-owner 복원/영구삭제 추적

`/admin/trash/all` (T9)은 ADMIN이 **타 사용자 소유** 휴지통 항목을 복원/영구삭제할 수 있게 한다. `deletedBy` 컬럼은 v1.x deferred (docs/02 §7.11) 이므로 "누가 누구의 항목을 처리했는가" 추적은 **`audit_log` 단일 진실의 출처**로 운영한다.

**조회 절차** (사내 베타 운영자):

1. `/admin/audit/logs` 접속 (M12 wired) — actor_role 필터는 v1.x deferred 라 SQL 직접 사용 권장.
2. 또는 DB 직접 쿼리:

   ```sql
   -- 최근 24h 동안 ADMIN actor 가 수행한 휴지통 액션
   SELECT
     occurred_at,
     actor_id,
     event_type,        -- file.restored / file.purged / folder.restored / folder.purged
     target_id,         -- 복원·영구삭제된 file/folder UUID
     after_state->>'name' AS name,
     metadata->>'ip'    AS ip
   FROM audit_log
   WHERE event_type IN ('file.restored','file.purged','folder.restored','folder.purged')
     AND actor_id IS NOT NULL
     AND occurred_at >= NOW() - INTERVAL '24 hours'
   ORDER BY occurred_at DESC;
   ```

3. `actor_id` → `users` 테이블 join 으로 actor 식별, `target_id` → `files`/`folders` (soft-delete row 포함) join 으로 owner 식별. **owner ≠ actor** 인 row 가 cross-owner 액션.

**제약**:

- A6/A7 cron (`app.purge.expired`, `app.share.expiration`) 은 `actor_id=NULL` + `metadata.trigger='system.expiration'` 으로 emit (docs/02 §7.10.1, §1561) → 위 쿼리에서 자연 배제됨.
- soft-delete 된 file/folder 도 row 자체는 보존되므로 `target_id` join 가능 (T9 design rationale).

### 15.2 휴지통 일일 운영

운영자가 `/admin/trash/all` 만으로 처리 가능한 일상 작업과 cron 분담:

| 작업 | UI 경로 | 백엔드 처리 |
|---|---|---|
| 휴지통 전수 조회 | `/admin/trash/all` (T9, q/type/ownerId 필터) | `GET /api/admin/trash` |
| 단건 복원 | `/admin/trash/all` 행 `복원` 버튼 | `POST /api/files\|folders/{id}/restore` |
| 단건 영구삭제 | `/admin/trash/all` 행 `영구삭제` 버튼 + ConfirmDialog | `DELETE /api/trash/{type}/{id}` |
| 자동 hard purge | (UI 없음, cron) | `app.purge` cron — `deleted_at + retention < NOW()` 일괄 hard delete |

**운영 권장**:

- 베타 초기 1주는 `app.purge.enabled: false` 유지하고 수동 영구삭제만 (ADMIN 검토 후) → audit trail 완전 확보.
- 안정화 후 `enabled: true` 로 전환 (재기동 필요, §15.4 참조).
- bulk restore/purge 는 v1.x deferred (T9 backlog) — 단건 처리만 가능.

### 15.3 권한 만료 모니터링

`/admin/permissions` (T5) 는 viewer + 만료 배지 + 단일 row 철회를 제공한다. 만료된 권한 자체는 `permissions-expired-cron` 이 평가에서 무시 + DB 정리하지만 (docs/02 §7.10), **운영 알림은 자동화되지 않았다**.

**일일 점검** (사내 베타):

1. `/admin/permissions?preset=expiring` (만료 임박 필터, 7일 이내) → 갱신 필요 항목 list-up.
2. 갱신은 v1.x deferred — 베타 기간엔 직접 `permissions` row UPDATE 또는 `POST /api/folders|files/{id}/share` 재부여로 처리. 잘못 부여되었거나 만료 임박 이전 즉시 회수가 필요한 경우 viewer 행의 "철회" 버튼으로 처리(admin-permission-revoke, Wave 2 T5 follow-up, 2026-05-09 — 기존 `DELETE /api/permissions/{id}` 재사용, ROLE.ADMIN 통과). 단일 자원 grant 다이얼로그는 **docs/01 §14.5 spec 작성** (2026-05-09, spec-permission-grant-dialog) — `RightPanel` 권한 탭에서 부여, 실 구현은 v1.x phase B/C/D. admin 페이지 전역 grant (resource picker)는 v2.x.
3. cron 동작 검증:

   ```sql
   -- 최근 1h 동안 만료 처리된 permission/share
   SELECT event_type, COUNT(*) FROM audit_log
   WHERE event_type IN ('permission.expired','share.expired')
     AND occurred_at >= NOW() - INTERVAL '1 hour'
   GROUP BY event_type;
   ```

### 15.4 운영 cron 4종 변경 절차

**enabled 토글**: ADMIN UI(`/admin/system`)에서 카드별 토글. 즉시 반영(다음 tick부터). 변경 이력은 audit_log `admin.cron.toggled`로 추적되며 `cron_policy` 테이블에 영구 저장(재기동 후에도 유지). admin-cron-policy-toggle 트랙(2026-05-08).

**schedule / zone / batchSize / maxPerRun / graceHours 변경**: `application-prod.yml` 편집 + 재기동 (기존 절차).

**운영 cron 4종** (`backend/src/main/resources/application.yml`):

| 키 | 정의 | 역할 |
|---|---|---|
| `app.purge` | `cron:"0 0 0 * * *"` (매일 자정) | A7 hard purge — `deleted_at` 만료 file/folder 영구삭제 |
| `app.share.expiration` | `cron:"0 */5 * * * *"` (매 5분) | SHARE_EXPIRED — 만료 share row 자동 정리 + audit |
| `app.permission.expiration` | `cron:"0 */5 * * * *"` (매 5분) | PERMISSION_EXPIRED — 만료 permission row hard delete + audit |
| `app.storage.orphan-cleanup` | `cron:"0 0 1 * * *"` (매일 새벽 1시) | A15 backlog — 고아 객체 정리 |

> enabled 토글은 yml이 아닌 `cron_policy` DB 테이블 단일 source(admin-cron-toggle, 2026-05-08; yml-enabled-cleanup, 2026-05-09에서 yml `app.*.enabled` 필드 + 4 `*Properties.enabled` record param 제거 완료). yml은 schedule/zone/batchSize/maxPerRun/graceHours 정의만 보유 — 변경 시 재기동 필요.

**조회 / 권한**:

- AUDITOR 계정으로도 `/admin/system` 접근 가능 (Wave 1.5 auditor-cron-readonly closure, 2026-05-07) — 토글 mutation은 ADMIN-only.

### 15.5 스토리지 KPI 해석 가이드

`/admin` (T7 admin-dashboard) 8 KPI + `/admin/storage` (T8 admin-storage-overview) 시스템 합계 + 정리 기록 overview 를 운영자가 일일 모니터링.

**T7 대시보드 KPI 8종 (`GET /api/admin/dashboard/summary`)**:

| KPI | 정의 | 베타 베이스라인 (예상) | 알림 임계 |
|---|---|---|---|
| 등록 사용자 | `users` count | 사내 인원 N | (변동 시 §15.1 audit 조회) |
| 활성 사용자 | `users WHERE is_active=true` | ≈ 등록 ± 비활성 | 활성 < 등록 × 0.8 검토 |
| 전체 부서 | `departments` count | 조직도 부서 수 | 변경시 audit `admin.dept.*` 추적 |
| 활성 부서 | (현재 전체 동치 — `is_active` 컬럼 부재, T7 closure note) | = 전체 부서 | — |
| 활성 폴더 | `folders WHERE deleted_at IS NULL` | 운영 중 폴더 수 | 급증 시 §15.2 휴지통 검토 |
| 활성 파일 | `files WHERE deleted_at IS NULL` | 운영 중 파일 수 | — |
| 휴지통 파일 | `files WHERE deleted_at IS NOT NULL` | A7 cron 동작 시 ≤ retention period 내 삭제 누적 | retention 초과 잔존 = cron 미동작 — §15.4 검토 |
| 24h 감사 이벤트 | `audit_log WHERE occurred_at >= NOW() - 24h` | 활동량 비례 | 0 = audit emit pipeline 점검 |

**T8 `/admin/storage` overview**:

- 시스템 합계 (`SUM(file_versions.size_bytes)`) — 쿼터·과금 베이스 (v1.x quota 트랙 의존).
- 정리 기록 (`storage.orphan.cleaned` audit 24h tail) — orphan-cleanup cron 동작 가시화.

**일일 점검 권장**:

1. T7 KPI 8종 스냅샷 — 전일 대비 ±20% 변동 시 §15.1~15.4 분기.
2. T8 storage trend — 일주일 단위 sum 증가율 모니터링 (rapid growth = quota 트랙 우선순위 ↑).
3. 24h audit count = 0 → audit emit/DB 연결 즉시 점검 (severity high).

### 15.6 Wave 2 backlog → v1.x 전환

본 런북이 차선 경로로 운영하는 동안 v1.x 트랙 후보 (T9 closure note 인용):

- `deletedBy` 컬럼 V10 마이그레이션 → §15.1 SQL 의존 제거.
- ~~`/admin/trash/policy` UI → cron 런타임 토글 (§15.4 의 재기동 의존 해소).~~ → **trash-retention-mutation (Phase A spec 2026-05-11, Phase B/C 별도 PR)**: V17 single-row 테이블 + PUT endpoint + frontend mutation editor로 무중단 변경 도입. 단일-approver MVP, dual-approval은 별도 framework 트랙.
- 휴지통 bulk restore/purge → §15.2 단건 제약 해소.
- 휴지통 날짜 범위 필터 + full path resolve → §15.1 운영자 식별 공수 축소.
- T7 KPI 확장 (오늘 업로드/다운로드, 쿼터 알림, 비정상 패턴) → §15.5 일일 점검 자동화.

### 15.7 Multi-session 자율 작업 트러블슈팅 (2026-05-09 추가)

자율 모드 (다중 Claude/codex 세션이 동일 repo에 병렬 작업) 운영 시 자주 마주치는 충돌 + 복구 패턴. 본 세션 trajectory에서 검증됐고 운영 가드로 명시한다.

#### 15.7.1 PR 자동 머지 백그라운드 — `mergeStateStatus` 5단계 분기

`gh pr view <PR> --json mergeStateStatus,state --jq '"\(.state) \(.mergeStateStatus)"'`로 폴링. 각 분기별 처리:

| state · mergeStateStatus | 의미 | 처리 |
|---|---|---|
| `OPEN UNSTABLE` | CI 일부 진행 중 또는 fail | 폴링 지속 (30s 간격) |
| `OPEN UNKNOWN` | GitHub 평가 중 | 일시적, 다음 폴링 시 보통 해결 |
| `OPEN DIRTY` | master 새 push로 충돌 | 즉시 ABORT + rebase + `--force-with-lease` 재시도 |
| `OPEN CLEAN` | 머지 가능 | `gh pr merge --squash --delete-branch` |
| `MERGED *` | 이미 머지됨 | 폴링 break (다른 세션이 끝낸 PR 처리 시 무한 polling 방지) |

폴링 간격은 30초 권장 — GitHub API rate limit + cache miss 회피.

#### 15.7.2 충돌 복구 — rebase + force-push

`OPEN DIRTY` 발견 시 워크트리에서:

```bash
git fetch origin master
git rebase origin/master
# 충돌 해소 (보통 docs/progress.md — 양쪽 entry 보존 + 시간순 배치)
git add <resolved files>
GIT_EDITOR=true git rebase --continue
git push --force-with-lease origin <branch>
```

`--force-with-lease`는 다른 세션이 우리 브랜치에 직접 push한 경우 force-push를 차단한다 — 보호 가드 (메모리 `feedback_co_session_collab`).

복구 단계에서 워크트리 인덱스가 stale state로 빠지면 (`docs/progress.md: needs merge` 등):

```bash
git reset --hard origin/<branch>      # 우리 브랜치 origin 최신으로 복구
git rebase origin/master              # 깨끗한 base로 재시도
```

**주의**: 본 sequence 전 `git reflog -10`으로 우리 작업 SHA를 백업해 둔다 — `reset --hard`가 미푸시 commit을 삭제하기 때문.

#### 15.7.3 다른 세션 영역 회피

다른 세션이 작업 중인 worktree/branch는 **본 세션이 손대지 않는다**:

- `git worktree list`로 활성 worktree 확인. 다른 worktree 안의 파일은 read-only도 가급적 회피 (lock 위험).
- `git branch -a`에서 다른 세션 작업 브랜치는 정리 대상에서 제외 (`backup/*`, 다른 세션 active branch).
- `gh pr list --state open`으로 open PR 확인 — 다른 세션 PR은 영역 점유로 간주.

#### 15.7.4 다른 세션이 `master` 브랜치에 직접 commit한 경우

자율 모드에서 다른 세션이 실수로 master 로컬에 commit하고 push 안 한 상태 (`master ahead 1`, origin GONE) 발견 시:

- **본 세션은 절대 reset/discard 하지 않는다** — 작업 손실 위험.
- 메인 worktree가 `master [ahead N]` 상태면 다른 세션이 정리할 때까지 보존.
- 본 세션이 master fast-forward 필요하면 별도 worktree를 `origin/master` 직접 base로 생성:

```bash
git worktree add .claude/worktrees/<task> -b <branch> origin/master
```

archive PR 등 master 위 작업도 본 패턴으로 분리한다 (메인 worktree 잘못 commit과 격리).

#### 15.7.5 cleanup 잔여 (Windows lock)

머지 + cleanup 백그라운드 종료 후 worktree 디렉터리가 `Permission denied` / `Device or resource busy`로 남는 경우:

- `git worktree list`에서 사라졌으면 git 메타데이터는 정리됨 (디스크 공간만 잠식, 무해).
- 다음 세션 시작 시 또는 codex/IDE 프로세스 종료 후 수동 `rm -rf` 가능.
- 동일 이름 worktree 재생성은 git 에러 없음 (메타데이터 free 상태).

---

## 작성 우선순위 (MVP closure 시점 갱신 — 2026-05-02)

1. ~~§4 사용자 관리~~ — **v1.x deferred** (admin frontend 미구현, MVP는 DB 직접 프로비저닝)
2. ~~§7 감사 로그 UI~~ — **MVP closure** (M12 wired + filter v1.x deferred 명시)
3. ~~§8 휴지통 정책~~ — **MVP closure** (A6/A7 cron + ADR #38 storage cleanup)
4. ~~§6 스토리지 관리~~ — **부분 closure** (orphan cleanup MVP, quota/대시보드 v1.x)
5. ~~§11 백업~~ — *운영 (인프라 책임, `BETA-RELEASE.md` 게이트)*
6. ~~§10 Legal Hold~~ — **v2.x deferred** (docs/00 §4.3)
7. 나머지 (§3·§5·§9·§12·§14) — **v1.x 또는 운영** 마커 완료

---

## 16. Dual-Approval 운영 (관리자 파괴적 액션 보호)

> **Status: v1.x deferred** (docs/00 §5 ADR #47, docs/03 §6.4). 본 절은 **운영 명세 — 활성화 = v1.x 진입**. 보안/컴플라이언스 명세는 docs/03 §6.4, 데이터 모델은 docs/02 §2.11.

### 16.1 Tier 0 적용 액션 (1차)

| action_type | 진입점 | 위험 | 게이트 properties |
|---|---|---|---|
| `role_change` | `PATCH /api/admin/users/:id` (role 필드 변경) | ADMIN 권한 부여 = 보안 critical | `app.dual-approval.role-change.enabled` |
| `trash_purge` | `DELETE /api/admin/trash/:type/:id`, `POST /api/admin/trash/bulk` (action='purge') | 회복 불가 영구 삭제 | `app.dual-approval.trash-purge.enabled` |
| `retention_change` | `PUT /api/admin/trash/policy` (deferred — wave2-trash-policy-viewer mutation 후속) | 일수 감소 시 hard purge 폭증 | `app.dual-approval.retention-change.enabled` |

> Tier 1 (cron_toggle, user_deactivate)는 v1.x 후속. Legal Hold release는 ADR #46 본 framework 이관 (v1.x V_ 마이그레이션 + payload_json='legal_hold_release').

### 16.2 활성화 정책

게이트는 **per-action**, default `false`. 환경별 점진 활성화 권장:

| 환경 | 권장 |
|---|---|
| dev | 모두 false (테스트 시점 개별 활성화) |
| staging | `role-change` 우선 활성화 (보안 critical) |
| prod | Tier 0 3종 모두 활성화. `ttl-days=7` (default) 권장 |

회피 (긴급 운영): 게이트를 일시 false 복귀 + 운영 노트에 사유 기록 + audit_log에 변경 이력.

### 16.3 운영 흐름 (게이트 활성 시)

```text
[ requested_by admin ]                         [ secondary admin ]
        │                                              │
        │ 1. 기존 admin 페이지에서 액션 시도                │
        │    (e.g. /admin/users/:id role 변경)         │
        │                                              │
        │ 2. 백엔드: 게이트 ON → framework INSERT       │
        │    202 Accepted + APPROVAL_REQUIRED          │
        │                                              │
        │ 3. Frontend: toast + /admin/approvals redirect │
        │                                              │
        │ 4. EmailService.send(secondary 후보 ADMIN)   │
        │    제목: "[승인 요청] role_change …"           │
        │                                              │
        │                                              │ 5. 이메일 또는 /admin/approvals
        │                                              │    pending 배지 → 상세 페이지
        │                                              │
        │                                              │ 6. payload + reason 검토
        │                                              │
        │                                              │ 7a. POST /approve {decisionReason?}
        │                                              │     → action 실행 + status=APPROVED
        │                                              │
        │                                              │ 7b. POST /reject {decisionReason}
        │                                              │     → action 미실행 + status=REJECTED
        │                                              │
        │ 8. 결과 알림 이메일 (granted/rejected)         │
        │                                              │
        ▼                                              ▼
    [ /admin/approvals 결과 row ]              [ /admin/approvals 결정 history ]
```

**취소 (requested_by 본인)**: `DELETE /api/admin/approvals/:id` → status=CANCELLED, audit row 미발행 (KISS, §6.4.6).

**만료 (system)**: `expires_at <= NOW()` 자동 EXPIRED + `admin.approval.expired` audit. requested_by에게 알림.

### 16.4 `/admin/approvals` 페이지

#### 16.4.1 진입 권한

`hasRole('ADMIN')` (보안 게이트). AUDITOR는 read-only로 목록 조회 가능 (Legal Hold 일관 — `/admin/legal-holds` AUDITOR 정책).

#### 16.4.2 목록 (`/admin/approvals`)

| 컬럼 | 비고 |
|---|---|
| 액션 | action_type 라벨 (`사용자 권한 변경` / `휴지통 영구 삭제` / `보존 기간 변경` 등) |
| 요약 | payload_json에서 추출 (e.g. `userId → toRole`, `N개 항목 영구삭제`, `30일 → 14일`) |
| 요청자 / 요청일 | requested_by.displayName · requested_at |
| 만료 | expires_at, 임박(1일 이내) 노란 배지 |
| 상태 | REQUESTED / APPROVED / REJECTED / CANCELLED / EXPIRED 필터 |
| 액션 | 상세 / 승인 (secondary 가능) / 거부 / 취소 (requested_by 본인) |

필터: action_type, 요청자, 상태, 만료 임박.

#### 16.4.3 상세 페이지 (`/admin/approvals/:id`)

- 메타 (전체 필드)
- payload_json 풀이 (action_type별 친화적 표시)
- decision_reason 입력 textarea (승인/거부)
- 결정 버튼: 승인 / 거부 / 취소 (상태/권한별)
- 관련 audit timeline (request → expired/granted/rejected)

#### 16.4.4 알림 (EmailService 재사용)

| 트리거 | 수신자 | 내용 |
|---|---|---|
| status=REQUESTED 진입 | 모든 ADMIN (requested_by 제외) | "[승인 요청] {action_type}" + payload 요약 + 만료일 |
| status=APPROVED | requested_by | "[승인됨] {action_type}" + secondary + decision_reason |
| status=REJECTED | requested_by | "[거부됨] {action_type}" + secondary + decision_reason (필수) |
| status=EXPIRED | requested_by | "[만료] {action_type}" + 재요청 안내 |

EmailService 재사용 — ADR #42/45 패턴, async fire-and-forget. SMTP 실패는 ERROR 로그만 (audit_log에 발송 실패 기록 안 함, 별도 운영 모니터링 영역).

### 16.5 운영 런북 진입 (긴급 액션 절차)

> **베타 운영 런북** (§15) 미반영 항목 — Dual-approval 활성화 시 §15에 sub-section 추가 권장.

#### 16.5.1 일반 흐름

1. 운영 액션 요청 발생 (e.g. 사용자 `xxx`를 ADMIN으로 승격)
2. 운영자 A가 `/admin/users/:id`에서 role 변경 시도 → 202 + 토스트 → `/admin/approvals` redirect
3. 운영자 A가 사내 채널/메일로 운영자 B에게 검토 요청 (자동 알림 외 명시 communication 권장)
4. 운영자 B가 payload + reason 검증 (티켓 ID 또는 사내 정책 부합 여부) → 승인 또는 거부
5. APPROVED → 즉시 action 실행 + 양쪽에 알림 → 종료

#### 16.5.2 긴급 우회 (게이트 일시 OFF)

운영 책임자 결정 하에:
1. `application-prod.yml`에서 해당 action_type 게이트 false로 변경
2. 운영 노트에 사유 + 시점 기록 (사내 위키 또는 운영 채널 archive)
3. action 즉시 실행 (기존 흐름)
4. 게이트 true로 복원
5. audit_log에 game-changer 이벤트 흔적 (운영 cleanup 정책 변경 audit — 별도 enum 미존재, application 시작 시 `app.dual-approval.*` 값을 INFO 로그로 출력하므로 그것이 운영 trail 시작점)

> 빈번한 우회는 게이트 자체의 정책 적합성 재검토 신호.

#### 16.5.3 만료 누적 모니터링

`/admin/approvals?status=EXPIRED` 정기 점검. 만료가 빈번하면:
- TTL이 너무 짧음 → `app.dual-approval.ttl-days` 조정
- secondary 알림이 누락됨 → EmailService 헬스체크
- 운영자 N명이 너무 적음 → ADMIN 운영자 추가

### 16.6 v1.x 진입 시 작업 분해

→ `dev/active/v1x-confirm-2admin-design/` plan/tasks 참조. docs/03 §6.4.9 14단계와 동일.
