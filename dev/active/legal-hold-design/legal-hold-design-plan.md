---
Last Updated: 2026-05-08
Status: design-only (코드 0줄). v2.x 진입 시 본 plan을 그대로 실행.
---

# Plan — legal-hold-design

## 요약

Legal Hold 정책 — **v2.x deferred** (docs/00 §4.3, ADR #46). 본 트랙은 활성화 작업이 아니라 **설계 명세 정합화**만 수행한다. 산출물:

1. ADR #46 (데이터 모델 결정) + ADR #31 forward-reference 정합화
2. docs/02 §2.10 `legal_holds` 테이블 + cache flag 스키마 reserve
3. docs/03 §6.3 본문화 (보안/컴플라이언스 명세)
4. docs/04 §10 본문화 (관리자 운영 명세)
5. docs/02 §8 신규 에러 코드 2종 (`LEGAL_HOLD_VIOLATION` 423, `LEGAL_HOLD_RECENTLY_RELEASED` 409)
6. 권한 enum `MANAGE_LEGAL_HOLD` 추가 (docs/03 §3.1)
7. Audit enum 신규 2종 (`admin.legal_hold.expired`, `admin.legal_hold.violation_blocked`) + 기존 placeholder 2종(`placed`/`released`) 활성화 명시 (docs/03 §4.1)

**v2.x 활성화 시점에 본 plan + tasks를 직접 실행** — 설계 변경 없이 task 분해 그대로 진행 가능.

## 핵심 결정 (ADR #46)

**데이터 모델 = 하이브리드 (메타 테이블 + cache flag)**

- `legal_holds` 메타 테이블: id/target_type/target_id/reason/placed_by/placed_at/released_*/expires_at/dual_approval_*
- `files.legal_hold BOOLEAN` + `folders.legal_hold BOOLEAN` cache flag (ADR #31 forward-reference 정합)
- 동기화 = 트랜잭션 + `SELECT FOR UPDATE` (CLAUDE.md §3 핵심 원칙 7)

**거부된 대안**:
- A. flag-only: 사유/만료/승인자 메타 부재 → 컴플라이언스 증거 부족
- B. 메타 테이블 only: 모든 mutation에서 join 필요 → ADR #31 WHERE 절 정합 깨짐, hot path 비용 ↑

## 차단 정책

→ docs/03 §6.3.3 매트릭스. 9개 mutation entry:
1. soft delete (file/folder)
2. restore
3. manual purge
4. cron purge (`HardPurgeService`)
5. 신규 버전 업로드
6. 버전 복원
7. rename
8. move
9. share/permission 변경

읽기/다운로드는 허용 — 보존 목적상 access trail 정상.

## 인증/승인

- **단일 admin** = MVP-of-v2.x default (`app.legal-hold.dual-approval.enabled=false`)
- **dual-approval** = config 게이트 (true 시 release 2단계: primary 요청 → secondary 승인)
- `Permission.MANAGE_LEGAL_HOLD` ROLE=ADMIN 전용. AUDITOR는 read-only 조회만.

## 30일 재지정 락 + 만료 cron

- 30일 cooldown: release 후 동일 target 재지정 거부 (`409 LEGAL_HOLD_RECENTLY_RELEASED`). config로 조정 가능 (`app.legal-hold.replace-cooldown-days`)
- expiration cron: `expires_at <= NOW()` 자동 release. share-expired/permission-expired-cron 동형, default `enabled=false`

## v2.x 진입 시 task 분해

→ `legal-hold-design-tasks.md` 참조. 8단계 (V_ 마이그레이션 → permission enum → guard → purge SQL → audit emission → controller/service → frontend → CLAUDE.md §3 정합 검증).

## 참조 문서

- docs/00 §4.3, §5 ADR #46, ADR #31 (보강)
- docs/02 §2.10 (스키마 reserve), §8 (에러 코드)
- docs/03 §3.1 (권한 enum), §3.2.5 (ROLE 매트릭스), §4.1 (audit enum), §6.3 (본문)
- docs/04 §10 (운영 명세), §15 (베타 운영 런북 — 진입 시점에 sub-section 추가)
- ADR #23/44 (LoginAttemptTracker 패턴 — violation_blocked sampling 후보)
- ADR #42/45 (EmailService — dual-approval 알림 재사용)
- ADR #34 (share-expired-cron), permissions-expired-cron (cron 패턴 답습)
