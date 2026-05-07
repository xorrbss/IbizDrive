# Wave 1.5 — auditor-cron-readonly (plan)

## 목표

`GET /api/admin/system/cron` (Wave 1 — T3) 가드를 `ADMIN` 단독 → `ADMIN or AUDITOR`로 확장.
T3 closure에서 deferred로 명시한 "AUDITOR cron 설정 확인 read-only 권한" 회수.

## 범위 (in-scope)

- `AdminSystemController#getCronStatus` `@PreAuthorize` 변경
- 컨트롤러 클래스 javadoc(L23~24) "AUDITOR 미허용 — ADMIN 단독" 문구 갱신
- `AdminSystemControllerTest`의 `getCronStatus_auditor_returns403` → `getCronStatus_auditor_returns200`로 케이스 flip
- docs/04 §7.x 권한 표 갱신
- BETA-RELEASE.md / docs/audit-emit-gap-mapping.md 메트릭 정합 (해당 항목 있을 시)
- progress.md 트랙 closure 엔트리 추가

## 범위 외 (out-of-scope)

- **프론트엔드 가드**: `frontend/src/app/admin/layout.tsx`의 `<AdminGuard>`는 `/admin/**` 전체를 ADMIN 단독으로 차단. 이는 본 트랙(cron)뿐 아니라 T2 `/admin/audit/logs`(이미 backend AUDITOR 허용)도 동일하게 영향받는 broader 문제. 별도 트랙 "auditor-admin-ui-access"로 분리하여 처리.
  - 본 트랙은 백엔드 API 직접 호출(curl/스크립트/외부 모니터링)에서 AUDITOR가 401/403 받지 않도록 하는 것이 1차 목표.
- cron mutation UI / settings table — v1.x deferred 그대로 유지.

## 가드 정책 정합

T2 audit-export(`hasRole('AUDITOR') or hasRole('ADMIN')`)와 동일한 표현식 채택. 개별 메서드 단위 `@PreAuthorize`. 익명=401, MEMBER=403, AUDITOR/ADMIN=200.

## audit emit

read-only endpoint이므로 audit 이벤트 신규 emit 없음 (T3 정책 유지). AUDIT_QUERIED 같은 메타-이벤트는 v1.x deferred(별도 의사결정 필요).

## 리스크

- (없음) 가드 완화 방향이라 회귀 위험 낮음. 기존 ADMIN 케이스는 그대로 200.
- 단일 행 `@PreAuthorize` 변경 + 테스트 1건 flip + docs.

## 검증

- `cd backend && mvn -pl . test -Dtest='AdminSystemControllerTest'`
- `cd backend && mvn test` (회귀)
- (frontend 변경 없음 — pnpm 회귀 불필요하나 CI에서 통과 확인)
