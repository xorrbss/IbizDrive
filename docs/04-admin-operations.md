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

### 1.1 가드 분리 — UX 게이트 vs 보안 게이트 (m-admin-entry-rewrite, 2026-05-03)

ADR #21 잔여 closure로 `/admin` 진입을 두 계층으로 분리:

- **프론트 `AdminGuard` = UX 가드만**. `useMe`로 현재 역할을 확인해 비-ADMIN을 `/files`로 redirect. 사용자에게 "권한 없는 화면을 잠깐이라도 보지 않게" 하는 표면 보호. 보안 강제 없음.
- **백엔드 `@PreAuthorize("hasRole('ADMIN')")` = 보안의 진실**. 모든 `/api/admin/**` endpoint는 컨트롤러 메서드 단위에서 강제. 프론트 가드를 우회한 직접 호출은 401(미인증) 또는 403(역할 부족)으로 차단된다.
- **AuthGuard와 중첩 순서**: `<AuthGuard><AdminGuard>...</AdminGuard></AuthGuard>` — AuthGuard가 비로그인을 `/login?next=/admin`으로 먼저 처리한 뒤 AdminGuard가 role 검사. 두 가드의 책임은 분리(인증 보유 vs 역할).
- **중요 회귀 가드**: 프론트 게이트 강도를 보안 강도와 혼동하지 말 것. `/api/admin/**`는 `SecurityConfig` `permitAll` 목록에 포함되지 않아야 하며, 신규 admin 컨트롤러에는 항상 메서드 레벨 `@PreAuthorize` 또는 클래스 레벨 강제가 붙어야 한다.

---

## 2. 관리자 페이지 구조

> **활성 라우트** (Wave 1 T3 closure, 2026-05-07):
> - `/admin` — landing (가용 카드 4 + deferred 안내)
> - `/admin/audit/logs` — 감사 로그 (M12 closure)
> - `/admin/users` — 사용자 목록 + 초대 + 검색·재활성·displayName 편집 (Wave 1 T1 closure)
> - `/admin/departments` — 부서 CRUD(생성/검색/rename/(de)activate, Wave 2 T4)
> - `/admin/system` — 운영 cron 4종 read-only 노출 (Wave 1 T3, 변경은 application.yml + 재기동)
>
> 그 외 노드는 모두 **v1.x deferred**. 사이드바에는 disabled 항목으로 노출하되 라우트는 만들지 않음(YAGNI).

```text
/admin                       (활성 — landing)
├─ /dashboard              운영 현황 요약                                 (v1.x deferred)
├─ /users                  사용자 초대 + 목록 (검색/편집/활성 토글)         (활성, 2026-05-06)
│  ├─ /:id                 사용자 상세 + 활동                              (v1.x deferred)
│  └─ /import              CSV 일괄 import                                (v1.x deferred)
├─ /departments              부서 CRUD (생성/검색/rename/(de)activate)     (활성, Wave 2 T4)
│  ├─ /tree                조직도 트리 편집                                 (v1.x deferred)
│  └─ /:id                 부서 상세                                       (v1.x deferred)
├─ /permissions
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
│  ├─ /all                 전역 휴지통                                     (v1.x deferred)
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

> **v1.x deferred (전체)**. metrics 인프라 + admin frontend `/admin/dashboard` 미구현. MVP는 `application logs` + `audit_log` 직접 조회로 대체.

### 3.1 실시간 지표

- [ ] 활성 사용자 수 — *v1.x deferred*
- [ ] 총 저장 용량 / 한도 — *v1.x deferred (quota 시스템 미구현)*
- [ ] 오늘 업로드/다운로드 수 — *v1.x deferred*
- [ ] 오늘 감사 이벤트 수 — *v1.x deferred (audit_log 직접 쿼리로 대체)*
- [ ] 대기 중인 바이러스 스캔 수 — *v1.x deferred (AV 미도입)*

### 3.2 알림

- [ ] 쿼터 80% 초과 사용자 — *v1.x deferred*
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

> **v1.x deferred**. quota 시스템 미구현 (DB `quota_bytes` 컬럼 0). MVP는 `spring.servlet.multipart.max-file-size=100MB` (per-request cap)로만 1차 보호.

```text
기본 쿼터: 사용자당 10GB (config)         ← v1.x deferred
부서 쿼터: 부서 전체 총합 제한 (optional)  ← v1.x deferred
쿼터 초과 시 동작: 업로드 차단 (413 응답)  ← v1.x deferred
```

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
- [ ] 사용량 대시보드 UI — *v1.x deferred (admin frontend 미구현)*

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
- [x] **server-side full-result 스트리밍** — Wave 1 T2: `GET /api/admin/audit/export` (`StreamingResponseBody`), 하드 캡 10,000행 + `LIMIT cap+1` truncation 감지, 초과 시 `X-Audit-Export-Truncated: true` 헤더
- [x] **`audit.exported` runtime emission** — Wave 1 T2: `AuditExportListener` (`@EventListener` + `@Transactional(REQUIRES_NEW)`), metadata에 `filters` / `rowCount` / `truncated` / `format=csv` (append-only 보존을 위해 export read 트랜잭션과 분리)
- [ ] JSON 다운로드 — v1.x deferred

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

- [ ] 전체 사용자의 휴지통 파일 (관리자 전용) — *v1.x deferred (admin frontend 미구현)*
- [ ] 긴급 복원 (사용자 요청 시) — *v1.x deferred (사용자 본인 복원은 구현됨, A6 closure)*
- [ ] 즉시 영구 삭제 (승인 워크플로) — *v1.x deferred*

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

> **MVP 부분 구현**: 휴지통 보존은 A7 cron + `app.purge.*` config로 운영자 제어. 버전 보존은 v1.x.

```text
삭제된 파일 보존: 30일 default — A7 `app.purge.cron`, `app.purge.max-per-run`
              ↳ 운영자가 application.yml에서 cron 변경 가능
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

> **v2.x deferred (전체)** (docs/00 §4.3 명시). 외부 출시 + 컴플라이언스 도메인 진입 시점에 별도 트랙.

### 10.1 대상 지정

- [ ] 개별 파일 / 폴더 / 사용자 — *v2.x deferred*
- [ ] 태그 기반 (예: "소송 ABC") — *v2.x deferred*
- [ ] 지정 사유 기록 — *v2.x deferred*

### 10.2 Hold 상태 동작

- [ ] 삭제 버튼 비활성 + "Legal Hold" 배지 표시 — *v2.x deferred*
- [ ] 휴지통 이동 API 거부 (423 LOCKED) — *v2.x deferred*
- [ ] Purge 크론 스킵 — *v2.x deferred*

### 10.3 해제 워크플로

- [ ] 관리자 2인 승인 (optional) — *v2.x deferred*
- [ ] 해제 사유 기록 — *v2.x deferred*
- [ ] 해제 후 30일 내 재지정 불가 (실수 방지) — *v2.x deferred*

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

## 작성 우선순위 (MVP closure 시점 갱신 — 2026-05-02)

1. ~~§4 사용자 관리~~ — **v1.x deferred** (admin frontend 미구현, MVP는 DB 직접 프로비저닝)
2. ~~§7 감사 로그 UI~~ — **MVP closure** (M12 wired + filter v1.x deferred 명시)
3. ~~§8 휴지통 정책~~ — **MVP closure** (A6/A7 cron + ADR #38 storage cleanup)
4. ~~§6 스토리지 관리~~ — **부분 closure** (orphan cleanup MVP, quota/대시보드 v1.x)
5. ~~§11 백업~~ — *운영 (인프라 책임, `BETA-RELEASE.md` 게이트)*
6. ~~§10 Legal Hold~~ — **v2.x deferred** (docs/00 §4.3)
7. 나머지 (§3·§5·§9·§12·§14) — **v1.x 또는 운영** 마커 완료
