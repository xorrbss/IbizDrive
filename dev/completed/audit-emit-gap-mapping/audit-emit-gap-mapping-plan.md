# audit-emit-gap-mapping plan

Last Updated: 2026-05-05

## 요약

`AuditEventType` enum과 backend emit 사이의 gap을 정량화하고, 미emit 항목 각각이 v1.x/v2.x deferred(의도)인지 누락(버그)인지를 분류한다. 결과를 BETA-RELEASE.md §6의 stale 메트릭 정정 + cross-link 추가로 반영한다.

## 현재 상태 분석

`backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` 헤더 주석은 "총 42개". `BETA-RELEASE.md` §6 line 101도 "42 enum 중 35 emit (83%)"로 동일 수치. 그러나 실제 enum 카운트는 **44개**:

- 파일 8 + 버전 3 + 폴더 7 + 권한/공유 7 + **인증 8** + 관리자 7 + 시스템 3 + 감사 1 = **44**
- 인증 카테고리 주석은 `// 인증 (6)`이지만, A1.5 (`a1.5-email-infra`, ADR #43)에서 추가된 `USER_PASSWORD_CHANGED` / `USER_PASSWORD_FORGOT_REQUESTED` / `USER_PASSWORD_RESET` 3종이 카운트 반영 누락

emit grep 결과 (`AuditEventType.X` 호출, `@Audited` 어노테이션, listener emit) = **35건**. 미emit = **44 − 35 = 9개** (BETA-RELEASE.md가 적시한 7이 아님).

## 미emit 9개 분류 (모두 deferred — 누락 0)

| Enum | 분류 | 근거 |
|---|---|---|
| `FILE_VIEWED` | v1.x deferred | ADR #9, `FileVersionController:60` javadoc, BETA §7 |
| `FOLDER_AUDIT_LEVEL_CHANGED` | v1.x deferred | docs/04 §6 (line 269) "enum 정의됨, emit 미구현" |
| `USER_MFA_ENABLED` | v1.x deferred | ADR #18, BETA §5 |
| `ADMIN_USER_UPDATED` | v1.x deferred | BETA §7 line 113 "displayName 편집 v1.x" |
| `ADMIN_QUOTA_CHANGED` | v1.x deferred | BETA §7 line 113 "quota v1.x" |
| `ADMIN_LEGAL_HOLD_PLACED` | v2.x deferred | BETA §7 line 112 "Legal Hold v2.x" |
| `ADMIN_LEGAL_HOLD_RELEASED` | v2.x deferred | BETA §7 line 112 |
| `SYSTEM_BACKUP_COMPLETED` | v1.x deferred | docs/04 §13 line 368 "managed Postgres 자동 백업 — 별도 cron 미구현" |
| `AUDIT_EXPORTED` | v1.x deferred | docs/04 §7.2 line 203 "runtime emission v1.x deferred" |

→ **누락(버그) 0건**. docs-only 분기 확정.

## 목표 상태

`BETA-RELEASE.md` §6 audit emit 행이:
1. 정확한 분모(44) + 정확한 미emit 수(9) + 정확한 비율(~80%) 표기
2. 미emit 9개 각각의 deferred 근거를 cross-link
3. `AuditEventType.java` 헤더 주석 "총 42개"와 인증 카테고리 주석 "(6)" 정정

## phase별 실행 지도

- **P1 enum/emit 카운팅** — *완료*. enum 44, emit 35, 미emit 9 확정.
- **P2 deferred/누락 분류** — *완료*. 9개 모두 deferred (ADR/BETA/docs cross-ref).
- **P3 docs 정정** — *active*. BETA-RELEASE.md §6 + AuditEventType.java 헤더 주석.
  - P3.1 BETA-RELEASE.md §6 line 101 갱신 (35/42 → 35/44, 미emit cross-link 표 추가)
  - P3.2 `AuditEventType.java` 헤더 주석 "총 42개 → 총 44개", 인증 카테고리 "(6) → (8)"
- **P4 closure** — progress.md 엔트리 + dev-docs를 `dev/completed/`로 이동 + PR.

## acceptance criteria

- BETA-RELEASE.md §6에 "35 emit / 44 enum (~80%)" + 미emit 9개 deferred 표 또는 cross-link 존재
- `AuditEventType.java` 헤더 주석/카테고리 주석 정정 (코드 동작 영향 0)
- 9개 미emit이 v1.x/v2.x deferred로 명시 매핑됨
- 모든 변경은 docs/주석. 코드 동작 변경 0건 (compile-only).

## 검증 게이트

- docs-only 분기: 코드 검증 게이트 면제.
- `AuditEventType.java` 주석 변경은 javadoc/주석만이므로 컴파일 영향 없음. 그러나 baseline `mvn compile`로 한 번 확인하면 안전.
- 코드 emit 추가가 발생하면 STOP — 별도 트랙으로 분리.

## 리스크와 완화

- **R1** 사용자가 "7개"를 가정한 BETA 메트릭에 의존 중. 9로 정정 시 다른 문서 인용 충돌 가능성 → grep으로 "35/42" 인용 전수 확인.
- **R2** `AuditEventType.java` 주석 변경이 코드 일관성에 영향 → 컴파일 verify.
- **R3** 분류 중 한 건이라도 누락(버그)로 재해석되면 docs-only 분기 깨짐 → STOP 후 사용자 보고 (현재 평가: 0건).
