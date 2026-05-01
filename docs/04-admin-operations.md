# 04 - 관리자 & 운영

> 관리자 페이지 UI, 운영 정책, 쿼터 관리, 백업/복구, 모니터링.
> **현재 상태**: 스켈레톤 (운영 요건 확정 후 본문 작성)

---

## 1. 관리자 역할 구분

| 역할 | 권한 |
|---|---|
| **Super Admin** | 전체 (사용자/부서/권한/감사) |
| **User Admin** | 사용자/부서 관리만 |
| **Auditor** | 감사 로그 읽기 전용 |
| **Storage Admin** | 쿼터/백업 관리만 |

> 역할 분리는 감사 원칙의 핵심: "한 명이 모든 것을 할 수 있으면 감사가 의미 없음"

---

## 2. 관리자 페이지 구조

```text
/admin
├─ /dashboard              운영 현황 요약
├─ /users
│  ├─ /list                사용자 목록
│  ├─ /:id                 사용자 상세 + 활동
│  └─ /import              CSV 일괄 import
├─ /departments
│  ├─ /tree                조직도
│  └─ /:id
├─ /permissions
│  ├─ /bulk                권한 일괄 변경
│  └─ /templates           권한 프리셋 템플릿
├─ /storage
│  ├─ /usage               전체 사용량
│  ├─ /quotas              쿼터 관리
│  └─ /cleanup             고아 객체 정리
├─ /audit
│  ├─ /logs                전체 감사 로그
│  ├─ /downloads           다운로드 이력
│  ├─ /permissions         권한 변경 이력
│  └─ /export              로그 내보내기 (CSV/JSON)
├─ /trash
│  ├─ /all                 전역 휴지통
│  └─ /policy              휴지통 정책 설정
├─ /legal-hold             법적 보존 관리
├─ /policies
│  ├─ /file-size           파일 크기/확장자 정책
│  ├─ /retention           보존 기간
│  └─ /audit-levels        감사 레벨 폴더 지정
└─ /system
   ├─ /health              시스템 상태
   ├─ /backups             백업 이력
   └─ /jobs                배치 작업 모니터링
```

---

## 3. 대시보드 (핵심 지표)

### 3.1 실시간 지표

- [ ] 활성 사용자 수
- [ ] 총 저장 용량 / 한도
- [ ] 오늘 업로드/다운로드 수
- [ ] 오늘 감사 이벤트 수
- [ ] 대기 중인 바이러스 스캔 수

### 3.2 알림

- [ ] 쿼터 80% 초과 사용자
- [ ] 바이러스 감지
- [ ] 비정상 다운로드 패턴 (한 사용자가 1시간 내 1000건+)
- [ ] 권한 변경 대량 발생
- [ ] 로그인 실패 급증

---

## 4. 사용자 관리

### 4.1 사용자 목록

- [ ] 필터: 부서, 역할, 활성 상태, 쿼터 사용률
- [ ] 정렬: 이름, 마지막 로그인, 저장 사용량
- [ ] 일괄 작업: 비활성화, 부서 변경, 쿼터 변경

### 4.2 사용자 상세

- [ ] 기본 정보 (이름, 이메일, 부서, 역할)
- [ ] 쿼터 / 사용량
- [ ] 최근 활동 (audit_log에서 조회)
- [ ] 소유 파일 수 / 공유 현황
- [ ] 로그인 이력

### 4.3 사용자 비활성화

```text
UX: "삭제"가 아니라 "비활성화"
  → 소유 파일은 유지
  → 로그인만 차단
  → 필요 시 파일 소유권 다른 사용자에게 이관 UI
```

### 4.4 사용자 Import

- [ ] CSV 포맷 명세
- [ ] SCIM 2.0 (v1.x)

---

## 5. 부서 관리

- [ ] 조직도 트리 편집 (드래그로 부서 이동)
- [ ] 부서 합병 / 분리
- [ ] 부서 기반 권한 일괄 부여
- [ ] 부서 해산 시 구성원 이관 flow

---

## 6. 스토리지 관리

### 6.1 쿼터 정책

```text
기본 쿼터: 사용자당 10GB (config)
부서 쿼터: 부서 전체 총합 제한 (optional)
쿼터 초과 시 동작: 업로드 차단 (413 응답)
```

### 6.2 사용량 대시보드

- [ ] 전체 사용량 / 한도
- [ ] 부서별 사용량 차트
- [ ] 상위 사용자 리스트
- [ ] 월별 증가 추세

### 6.3 고아 객체 정리

```text
S3 객체 중 DB에 file_versions row가 없는 것 = orphan
  → 주 1회 배치로 tmp/ 이외 orphan 검출
  → 확인 후 삭제 (수동 승인 또는 자동)

DB row 중 S3 객체가 없는 것 = phantom
  → 알림만 발송 (복구 필요)
```

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

- [x] CSV 다운로드 — `toAuditCsvBlob` (RFC 4180 quoting + UTF-8 BOM, `text/csv` MIME)
- [x] 대상 기간 / 필터 조건 포함 — current-page 결과 기준
- [ ] **server-side full-result 스트리밍** — 현재는 client-side current-page만 export. 전체 결과 export는 별도 backend endpoint 필요 (v1.x deferred)
- [ ] **`audit.exported` runtime emission** — enum 정의 존재(`docs/03 §4.1`), runtime emission은 server export endpoint 도입 시점에 활성화 (v1.x deferred)
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

- [ ] 매일 자정 `purge_after < NOW()` 대상 영구 삭제
- [ ] S3 객체 + DB row 모두 삭제
- [ ] audit_log에 `file.purged` 기록
- [ ] Legal Hold 대상은 스킵

### 8.3 전역 휴지통 뷰

- [ ] 전체 사용자의 휴지통 파일 (관리자 전용)
- [ ] 긴급 복원 (사용자 요청 시)
- [ ] 즉시 영구 삭제 (승인 워크플로)

---

## 9. 정책 관리

### 9.1 파일 크기 / 확장자

```text
최대 파일 크기: 2GB (조정 가능)
허용 확장자: 화이트리스트 편집 UI
차단 확장자: 블랙리스트 편집 UI
```

### 9.2 보존 정책

```text
삭제된 파일 보존: 30일 (조정 가능)
버전 보존: 영구 / 최근 N개만 / 기간 제한 (선택)
감사 로그 보존: 3년 (도메인 규제 따라)
```

### 9.3 감사 레벨 지정

- [ ] 폴더별 `audit_level` 지정 UI
- [ ] strict 지정 시 하위 상속
- [ ] 변경 자체를 audit_log에 기록

---

## 10. Legal Hold (법적 보존)

### 10.1 대상 지정

- [ ] 개별 파일 / 폴더 / 사용자
- [ ] 태그 기반 (예: "소송 ABC")
- [ ] 지정 사유 기록

### 10.2 Hold 상태 동작

- [ ] 삭제 버튼 비활성 + "Legal Hold" 배지 표시
- [ ] 휴지통 이동 API 거부 (423 LOCKED)
- [ ] Purge 크론 스킵

### 10.3 해제 워크플로

- [ ] 관리자 2인 승인 (optional)
- [ ] 해제 사유 기록
- [ ] 해제 후 30일 내 재지정 불가 (실수 방지)

---

## 11. 백업 / 복구

### 11.1 백업 전략

```text
DB:
  - 일일 전체 스냅샷 → S3 (7일 보관)
  - PITR 활성화 (최대 7일)
  - 월 1회 cold storage 보관 (1년)

S3 객체:
  - Cross-region replication
  - 버전 관리 활성 (30일)
  - Lifecycle: tmp/ 24시간, 삭제 버전 90일

감사 로그:
  - 별도 버킷 + WORM 모드
  - 영구 보관
```

### 11.2 복구 시나리오

- [ ] 사용자 실수 (단일 파일): 휴지통 복원
- [ ] 관리자 실수 (대량 삭제): 감사 로그 기반 롤백 스크립트
- [ ] 데이터 손상 (일부): PITR + S3 버전 복원
- [ ] 재해 (전체): cross-region 재구축

### 11.3 복구 훈련

- [ ] 분기별 복구 drill (관리자)
- [ ] RTO / RPO 측정

---

## 12. 모니터링 / 알림

### 12.1 시스템 지표

- [ ] API 응답 시간 (p50/p95/p99)
- [ ] 에러율 (5xx)
- [ ] DB 커넥션 풀 사용률
- [ ] S3 업로드/다운로드 처리량

### 12.2 비즈니스 지표

- [ ] DAU / MAU
- [ ] 업로드/다운로드 건수 추이
- [ ] 부서별 사용 패턴

### 12.3 알림 채널

- [ ] 즉시 알림: 장애, 보안 이벤트
- [ ] 일일 리포트: 쿼터/사용량
- [ ] 주간 리포트: 트렌드

---

## 13. 배치 작업 (Jobs)

| 작업 | 주기 | 설명 |
|---|---|---|
| `purge.expired` | 매일 00:00 (KST) | `purge_after` 경과 folders/files DB hard delete (A7). **S3 객체 삭제는 별도 잡** (`orphan.detect`, ADR #31). [†] |
| `cleanup.tmp` | 매일 01:00 | S3 tmp/ 24시간 경과 객체 삭제 |
| `scan.pending` | 5분마다 | 바이러스 스캔 대기 파일 처리 (v1.x) |
| `orphan.detect` | 매주 일요일 | S3 orphan 객체 검출 (storage 모듈 도입 시 — A7 hard purge 후 잔존하는 storage_key 정리) |
| `quota.warning` | 매일 08:00 | 쿼터 80%+ 사용자에게 알림 |
| `backup.snapshot` | 매일 02:00 | DB 스냅샷 |
| `audit.archive` | 매월 1일 | 감사 로그 월별 파티션 아카이빙 |
| `share.expire` | default 5분 | `shares.expires_at <= NOW() AND revoked_at IS NULL` row를 자동 만료 (`share-expired-cron`, 2026-05-01). [‡] |
| `permission.expire` | default 5분 | `permissions.expires_at <= NOW()` row를 자동 cleanup (`permissions-expired-cron`, 2026-05-01). [‡‡] |

> [†] `purge.expired` (A7) 정책 상세: docs/02 §7.11.1. **DB-only** (storage 모듈 부재) — S3 객체는 orphan으로 잔존, audit `after_state.orphanStorageKeys` (cap=1000)에 기록. Properties: `app.purge.{enabled, max-per-run, cron, zone}`. Audit: `SYSTEM_PURGE_EXECUTED` summary-only 1건/run. ROLE 없음 (system actor).
>
> [‡] `share.expire` 정책 상세: docs/02 §7.9.1. ADR #34 backlog closure. Properties: `app.share.expiration.{enabled(default false), batch-size(200), cron("0 */5 * * * *"), zone("Asia/Seoul")}`. 단위 처리(per-row 트랜잭션) — `ShareCommandService.expireShare(shareId)`가 `revoked_by=NULL` 시스템 트리거로 `revoked_at` set + `permissions` row delete. Audit `share.expired`는 `actor_id=NULL`, `metadata.trigger='system.expiration'`. 다중 인스턴스 안전(V6 row-level pessimistic lock).
>
> [‡‡] `permission.expire` 정책 상세: docs/02 §7.10.1. ADR #34 backlog closure. Properties: `app.permission.expiration.{enabled(default false), batch-size(200), cron("0 */5 * * * *"), zone("Asia/Seoul")}`. 단위 처리(per-row 트랜잭션) — `PermissionService.expirePermission(permissionId)`가 `lockById` (PESSIMISTIC_WRITE) → snapshot → DELETE (permissions 테이블에 `revoked_at` 부재로 soft-delete 불가). Audit `permission.expired`는 `actor_id=NULL`, `metadata.trigger='system.expiration'`. `findEffective`가 이미 `expires_at > NOW()` 필터링하므로 cron 가치는 (a) DB cleanup, (b) audit trail. 다중 인스턴스 안전(row-level pessimistic lock).

---

## 14. 관리자 액션의 감사

```text
원칙: 관리자 액션일수록 감사 로그 강화

모든 관리자 UI 동작 = audit_log 기록
  예: admin.user.updated, admin.role.changed, admin.quota.changed,
      admin.legal_hold.placed, admin.policy.changed

Audit 뷰는 actor_role 필터로 관리자 액션만 조회 가능
```

---

## 작성 우선순위

1. §4 사용자 관리 (MVP 필수)
2. §7 감사 로그 UI (MVP 필수)
3. §8 휴지통 정책 (MVP 필수)
4. §6 스토리지 관리
5. §11 백업 (운영 시작 전)
6. §10 Legal Hold (컴플라이언스 요구 시)
7. 나머지 (v1.x 이후)
