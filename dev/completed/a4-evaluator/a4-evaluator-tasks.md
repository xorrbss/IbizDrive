# tasks — a4-evaluator

## 단위 1: PermissionResolver 신설 ✅
- [x] `backend/src/main/java/com/ibizdrive/permission/PermissionResolver.java` 신규
  - `@Component` 의존: `PermissionRepository`
  - 메서드: `boolean isGranted(UUID userId, String resourceType, UUID resourceId, Permission required)`
  - 동작: `findEffective` 호출 → preset → permissions union → contains 검사
- [x] `backend/src/test/java/com/ibizdrive/permission/PermissionResolverTest.java` (Mockito 단위)
  - PermissionRepository mock으로 row 시나리오 4종

## 단위 2: IbizDrivePermissionEvaluator 분기
- [x] `IbizDrivePermissionEvaluator.java` 변경
  - 의존: `PermissionService` + `PermissionResolver` (신규 주입)
  - 평가 순서: ROLE → resourceType==folder/file & UUID 파싱 가능 → Resolver → 그 외 deny
  - PermissionDenyContext.record는 최종 deny 시 1회만

## 단위 3: 슬라이스/통합 테스트
- [x] `IbizDrivePermissionEvaluatorTest` (단위, Mockito) — 분기 표 검증:
  - ROLE ADMIN → grant
  - ROLE MEMBER + folder grant via Resolver → grant
  - ROLE MEMBER + Resolver false → deny + DenyContext 기록
  - Non-folder/file resourceType → ROLE 결과만
  - targetId가 UUID 파싱 실패 → ROLE 결과만
- [x] PermissionRepository 슬라이스 테스트는 A4-data 산출물에 이미 존재 (PermissionRepositoryTest) — 재실행 그린 확인

## 검증
- [x] `./gradlew test` 그린
- [x] A3 9개 통합 테스트 그린 (회귀 가드)
- [x] grep `hasPermission(` SpEL 호출처 변경 0건

## 배포
- [x] dev-docs-update
- [x] commit `feat(A4): IbizDrivePermissionEvaluator resource-level + inheritance`
- [x] push + PR (description: ADR #26 deferred close, A3 회귀 가드 결과)
- [x] dev/process/20260429-a4-evaluator.md 삭제 (보고 직전)
