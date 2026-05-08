---
Last Updated: 2026-05-08
---

# Context — legal-hold-design

## 트리거

사용자가 "Legal Hold 정책 구현 계획"을 요청 (2026-05-08). docs/03 §6.3 / docs/04 §10이 v2.x deferred 스텁만 보유 → 본문화 누락 상태였음. 코드 변경 없이 설계만 정합화하여 v2.x 진입 시 즉시 실행 가능한 자산으로 만든다.

## 영향받는 트랙 인벤토리

### 활성 트랙과의 상호작용 (현재 영향 0, v2.x 활성화 시점에 정합 필요)

| 트랙 | 상호작용 | v2.x 영향 |
|---|---|---|
| **A7 hard purge** (ADR #31) | `HardPurgeService` 후보 SQL이 `WHERE deleted_at < NOW()-30d` — `legal_hold` 미참조 | `AND legal_hold IS NOT TRUE` 1줄 추가 |
| **Wave 2 T9 admin global trash** (ADR #36 인접) | 휴지통 row 복원/purge 버튼 — hold 검사 부재 | row.legal_hold 시 버튼 비활성 + 백엔드 423 거부 |
| **A10/A12 shares** (ADR #34) | 공유 생성/해제 시 hold 검사 부재 | `LegalHoldGuard` 추가 |
| **A11 permissions** | 권한 부여/회수 시 hold 검사 부재 | `LegalHoldGuard` 추가 |
| **share-expired-cron / permissions-expired-cron** | 시스템 expiration 패턴 | legal-hold-expiration-cron 동형 추가 (default enabled=false) |
| **a1.5 EmailService** (ADR #42/45) | 비동기 발송 | dual-approval 알림에 재사용 |
| **A14/A16 user/department search** | UserSearchCombobox / DepartmentSearchCombobox | place 폼의 target picker에 재사용 |
| **A15 file upload** (ADR #36) | `POST /api/files` NEW_VERSION 분기 | 신규 버전 업로드 차단 (folder 대상 hold면 신규 file 자체도 차단) |
| **M-RP RightPanel** (ADR #39/40) | file detail right panel | active hold 메타 카드 추가, activity 탭에 violation_blocked 이벤트 노출 |

### Frontend 영향 지점

- `AdminSideNav.tsx` — placeholder Legal Hold 항목 활성화
- `/admin/legal-holds` 페이지 신규 (목록, 상세, place/release 다이얼로그)
- File/Folder detail card — ⚖ 배지 (`LegalHoldBadge` 컴포넌트 신규)
- BulkActionBar — selection 중 hold 활성 row 있으면 mutation 액션 비활성
- `lib/queryKeys.ts` — `qk.legalHolds.*` 키 추가
- `lib/api.ts` — `placeLegalHold`/`releaseLegalHold`/`approveLegalHold`/`listLegalHolds` 등
- `types/permission.ts` — `MANAGE_LEGAL_HOLD` enum mirror
- `types/audit.ts` — 신규 audit enum 4종 mirror

### Backend 영향 지점 (v2.x 진입 시)

- V_ 마이그레이션: `legal_holds` 테이블 + cache flag 2개 + 인덱스 4개
- `LegalHold` 엔티티 + `LegalHoldRepository`
- `LegalHoldService` (place/release/approve/expire 비즈니스 로직)
- `LegalHoldController` (admin endpoints)
- `LegalHoldExpirationJob` (`@Scheduled`, share-expired-cron 동형)
- `LegalHoldGuard` (mutation entry 9곳에서 호출 — service-level 가드)
- `HardPurgeService` SQL 수정 (1줄)
- `Permission` enum 확장 + `IbizDrivePermissionEvaluator` 매핑
- `AuditEventType` enum 신규 2종 추가 + placeholder 2종 emission 활성화
- `LegalHoldEvent` records (`Placed`/`Released`/`Expired`/`ViolationBlocked`)
- `LegalHoldAuditListener` (`@TransactionalEventListener` AFTER_COMMIT, `REQUIRES_NEW`)

## 외부 제약

- **컴플라이언스 도메인 진입 트리거**: 외부 출시 + 법적 보존 의무 발생 시 활성화. 현재 사내 베타는 미해당.
- **Wave 2 closure와 무관**: Wave 2 T1~T9는 MVP 라인이며 Legal Hold는 v2.x. 본 트랙은 docs sync 한정.

## 결정 메모 (이전 세션 결과)

(본 세션이 초기 세션이라 prior decision 없음. 새 결정 = ADR #46.)

## 알려진 한계 / backlog

- **태그 기반 hold (예: "소송 ABC" 묶음)**: v2.x 1차 컷 미포함. metadata에 자유 텍스트 + reason LIKE 검색으로 임시 대체 가능. 정식 tag 도메인은 별도 트랙.
- **사용자 hold cascade와 user 비활성화의 관계**: 본 spec에서는 별개 도메인으로 분리 (legal hold ≠ account suspension). user hold가 활성이어도 user는 로그인 가능, 자기 hold된 자료 read는 정상 동작.
- **violation_blocked sampling 정책**: audit 폭증 회피 위해 60s 윈도우 dedup 권장(LoginAttemptTracker 패턴). v2.x 진입 시 결정.
- **Folder hold + 후손 신규 업로드 정책**: 본 spec은 신규 업로드 자체를 거부하는 보수적 정책 (KISS). 대안(자동 hold 상속)은 backlog.
- **Multi-instance lock**: cron 잡들은 single-instance 가정 (`@SchedulerLock` 미도입, ADR #31/share-expired/permission-expired 일관). 멀티화 시 별도 ADR.
- **Legal hold export**: hold 메타와 cascade 영향을 CSV/JSON으로 export하는 endpoint는 v2.x 1차 컷 미포함. audit log export(docs/04 §7.2)로 대체 가능.

## 진행 중 결정 (open questions)

(없음 — 본 트랙은 design-only이며 사용자 확인 후 진행. v2.x 활성화 시점의 추가 결정은 task 단계에서 별도 ADR로 처리.)
