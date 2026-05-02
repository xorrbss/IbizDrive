# Empty Checkbox Inventory — docs/03 §5-§10 + docs/04 §3-§13

Last Updated: 2026-05-02

> 1차 분류는 *추천*. Phase 3 트리아지에서 사용자 sign-off 시 확정.
> 분류 기준: **MVP=베타 출시 전 본문화 또는 코드 fix 필요** / **운영=인프라/절차 책임(코드 영역 외)** / **v1.x=명시적 deferred**

## docs/03 보안

### §5 저장소 보안

| 항목 | 1차 분류 | 사유 |
|---|---|---|
| §5.1 TLS 1.2+ 강제 | 운영 | 인프라/리버스 프록시 책임 (Spring Boot HTTPS 직접 종단 안 함). 운영 절차 항목 |
| §5.1 HSTS 헤더 | 운영 | 동상 |
| §5.1 presigned URL 만료 ≤ 10분 | v1.x | 현재 MVP 다운로드 = direct GET (`/api/files/{id}/download`). presigned 0. ADR #36 multipart MVP에서 presigned 미사용 |
| §5.2 SSE-S3 또는 SSE-KMS | v1.x | LocalFsStorageClient만 구현. S3 v1.x 도입 시점에 활성 |
| §5.2 KMS 키 로테이션 1년 | v1.x | KMS 미도입 |
| §5.2 버킷 public access 차단 | v1.x | S3 미도입 |
| §5.3 확장자 화이트리스트 | **MVP 검증** | A15 업로드에 화이트리스트 적용 여부 불명 → Phase 2 검증. 미구현 시 정책 결정 |
| §5.3 MIME magic 검증 | v1.x | A15는 client-declared MIME만 사용. magic detection 라이브러리 추가 비용 ↑ |
| §5.3 바이러스 스캔 | v1.x | docs/00 §4.2 명시 deferred |
| §5.3 스캔 실패 시 다운로드 경고 | v1.x | 동상 |
| §5.4 Content-Disposition: attachment | **MVP 확인** | A15 download에 RFC 5987 적용 (ADR #36) — `attachment` disposition 명시 확인 필요 |
| §5.4 X-Content-Type-Options: nosniff | **MVP** | 단일 헤더 추가. Spring Security 기본 활성화 여부 검증 |
| §5.4 HTML/SVG 직접 렌더링 금지 | **MVP 결정** | MVP 미리보기 기능 0 → 다운로드만, attachment disposition으로 자동 보호. 결정 명시 |

### §6 데이터 보호

| 항목 | 1차 분류 | 사유 |
|---|---|---|
| §6.1 개인정보보호법 준수 | 운영 | 정책 문서 / 약관 책임. MVP는 사내 베타 가정 |
| §6.1 처리 목적/보존/제3자 명시 | 운영 | 동상 |
| §6.1 사용자 탈퇴 처리 | v1.x | 사용자 비활성화는 MVP. 탈퇴(완전 삭제) 시 파일 소유권 이관 UI = v1.x |
| §6.2 DB 일일 스냅샷 + PITR 7일 | 운영 | RDS/managed Postgres 책임 |
| §6.2 S3 Cross-region replication | v1.x | S3 도입 후 |
| §6.2 감사 로그 별도 버킷 + WORM | v1.x | 동상 |
| §6.3 Legal Hold (전체 §6.3) | v1.x | docs/00 §4.3 v2.x 명시. MVP 미구현 |

### §7 비밀번호·키 관리

| 항목 | 1차 분류 | 사유 |
|---|---|---|
| .env 관리, 커밋 금지 | **MVP** | `.gitignore` `.env.local` 확인. 본문화 |
| AWS Secrets Manager / Vault | 운영 | 운영 인프라 결정. MVP는 .env로 충분 |
| 키 로테이션 주기 | v1.x | KMS 미도입 |

### §8 규정 준수 (도메인별)

| 항목 | 1차 분류 | 사유 |
|---|---|---|
| §8.1 개인정보처리방침 | 운영 | 사내 베타 → 사내 정책으로 대체 |
| §8.1 이용약관 | 운영 | 동상 |
| §8.1 쿠키 정책 | 운영 | 사내 도메인 SameSite=Lax + HttpOnly로 자동 |
| §8.2 금융 / §8.3 의료 / §8.4 공공 | v1.x | MVP 도메인 적용 외 |

### §9 취약점 대응

| 항목 | 1차 분류 | 사유 |
|---|---|---|
| SAST / DAST 도구 | v1.x | CI 도구 도입 비용 ↑. MVP는 dependabot으로 충분 |
| 의존성 취약점 스캔 (Snyk/Dependabot) | **MVP** | GitHub Dependabot 활성화는 무료/단순. README 또는 운영 절차에 명시 |
| 연 1회 외부 모의해킹 | 운영 | 운영 절차 |
| 취약점 리포트 채널 | 운영 | 사내 베타 → 슬랙/이메일로 충분 |

### §10 인시던트 대응

| 항목 | 1차 분류 | 사유 |
|---|---|---|
| 인시던트 분류 (Severity 1~4) | 운영 | 운영 절차 문서 |
| 에스컬레이션 경로 | 운영 | 동상 |
| 데이터 유출 통지 72시간 | 운영 | 정책. MVP는 사내 베타 |
| Post-mortem 템플릿 | 운영 | 운영 절차 |

## docs/04 관리자/운영

### §3 대시보드

§3.1 실시간 지표 5개 / §3.2 알림 5개 = **모두 v1.x**

사유: 관리자 페이지 frontend 미구현 (audit logs UI만 활성, M12). 대시보드는 metrics 인프라 + 별도 페이지 필요.

### §4 사용자 관리

| 항목 | 1차 분류 | 사유 |
|---|---|---|
| §4.1 필터/정렬/일괄 작업 | v1.x | admin 사용자 페이지 frontend 미구현 |
| §4.2 사용자 상세 5항목 | v1.x | 동상 |
| §4.4 CSV 포맷 / SCIM | v1.x | docs/04 §4.4 자체에 SCIM = v1.x 명시 |

### §5 부서 관리

전체 4항목 = **v1.x**. department 도메인은 V7로 schema/API 활성, 관리자 UI는 미구현.

### §6 스토리지 관리

| 항목 | 1차 분류 | 사유 |
|---|---|---|
| §6.2 사용량 대시보드 4항목 | v1.x | 쿼터 시스템 미구현 (현재 quota 컬럼 0) |
| §6.3 고아 객체 정리 | **부분 구현** | `storage.orphan.cleanup` cron 활성 (ADR #38). UI는 미구현, 운영자 enable 절차만 운영 책임 |

### §7 감사 로그 UI

| 항목 | 1차 분류 | 사유 |
|---|---|---|
| §7.1 대상 리소스 / IP 필터 | v1.x | docs 본문에 이미 deferred 명시 |
| §7.2 server-side full export | v1.x | 동상 |
| §7.2 audit.exported runtime | v1.x | 동상 |
| §7.2 JSON 다운로드 | v1.x | 동상 |
| §7.3 diff 표시 / 관련 이벤트 | v1.x | 동상 |

(§7은 본문 자체가 이미 deferred 마커 보유 — 추가 작업 0)

### §8 휴지통 정책

| 항목 | 1차 분류 | 사유 |
|---|---|---|
| §8.2 매일 자정 purge cron | **구현됨** | A7 + ADR #31. docs 체크박스 → 본문 갱신 (이미 §13 표 + ADR에 반영, §8.2는 stale) |
| §8.2 S3 객체 + DB row 모두 삭제 | **부분** | A7 = DB-only, S3 = orphan-cleanup으로 흡수 (ADR #31/#38) |
| §8.2 audit_log file.purged | **구현됨** | A8 manual purge. cron purge는 SYSTEM_PURGE_EXECUTED summary |
| §8.2 Legal Hold 스킵 | v1.x | Legal Hold 미구현 |
| §8.3 전역 휴지통 뷰 | v1.x | admin 페이지 미구현 |

### §9 정책 관리

| 항목 | 1차 분류 | 사유 |
|---|---|---|
| §9.1 파일 크기 / 확장자 | **MVP 부분** | A15 multipart 100MB cap (`spring.servlet.multipart.max-file-size`). 확장자 정책 미적용 → §5.3과 함께 결정 |
| §9.2 보존 정책 | **부분** | A7 default 30일 (`app.purge.*` config 가능), 버전 보존은 v1.x |
| §9.3 감사 레벨 폴더별 | v1.x | ADR #9 audit_level 미구현 |

### §10 Legal Hold

전체 9항목 = **v1.x**. ADR + docs/00 §4.3 명시.

### §11 백업/복구

| 항목 | 1차 분류 | 사유 |
|---|---|---|
| §11.1 DB 일일 스냅샷 / S3 replication / WORM | 운영 | 인프라 책임 |
| §11.2 복구 시나리오 4종 | 운영 | 운영 절차 + audit_log 기반 롤백 스크립트는 v1.x 운영 절차 |
| §11.3 분기별 drill / RTO/RPO | 운영 | 동상 |

### §12 모니터링

§12.1~§12.3 전체 = **운영**. metrics 인프라 (Prometheus/Grafana 등) 외부 책임. MVP는 application logs + audit_log 조회로 대체.

### §13 배치 작업 (cron 활성화 상태)

| 작업 | 상태 | 분류 |
|---|---|---|
| `purge.expired` | 구현 (A7, default `enabled=false`) | MVP — 운영 enable |
| `cleanup.tmp` | 미구현 | v1.x (S3 도입 후) |
| `scan.pending` | 미구현 | v1.x (바이러스 스캔) |
| `storage.orphan.cleanup` | 구현 (ADR #38, default `enabled=false`) | MVP — 운영 enable |
| `quota.warning` | 미구현 | v1.x (quota 시스템 미구현) |
| `backup.snapshot` | 미구현 | 운영 (managed Postgres) |
| `audit.archive` | 미구현 | v1.x (월별 파티션 미적용) |
| `share.expire` | 구현 (ADR #34, default `enabled=false`) | MVP — 운영 enable |
| `permission.expire` | 구현 (ADR #34, default `enabled=false`) | MVP — 운영 enable |

### §14 관리자 액션의 감사

미구현 (admin 페이지 frontend 0 + `admin.*` audit enum 미정의) → **v1.x**

## 요약 통계

| 분류 | 개수 (대략) | 본 트랙 액션 |
|---|---|---|
| **MVP — 코드 검증 또는 본문화** | ~12 | Phase 2/3에서 실제 코드 검증, Phase 4에서 본문화 |
| **운영 — 절차 책임** | ~20 | Phase 4 BETA-RELEASE.md 또는 운영 절차 섹션에 inline 마커 |
| **v1.x — 명시 deferred** | ~50 | Phase 4에서 inline 마커 + (필요 시) `docs/05-v1x-backlog.md` 또는 docs/00 §4.2 확장 |

## Phase 2 검증 후보 (MVP 분류)

1. **§5.3 확장자 화이트리스트** — A15 업로드 코드에 화이트리스트 존재 여부
2. **§5.4 Content-Disposition: attachment** — `FileDownloadController` 헤더 검증
3. **§5.4 X-Content-Type-Options: nosniff** — Spring Security 기본 또는 명시 추가
4. **§7 .env 관리** — `.gitignore`에 `.env.local` 항목 (이미 확인됨, 본문화만 필요)
5. **§9 의존성 스캔** — GitHub Dependabot 활성화 여부 확인 또는 활성화
6. **§9.1 파일 크기 cap** — `application.yml` `spring.servlet.multipart.max-file-size` 값 확인
7. **§9.2 보존 정책** — `app.purge.cron` / 30일 default 확인
8. **§13 cron job 9개의 enabled 상태** — `application.yml` 종합 표

이 8개가 Phase 2/3에서 실제 코드 검증 + Phase 4 본문화 대상.
