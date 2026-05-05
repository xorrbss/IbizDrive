# audit-emit-gap-mapping tasks

Last Updated: 2026-05-05

## phase별 상태

- P1 enum/emit 카운팅 — ✓ 완료
- P2 deferred/누락 분류 — ✓ 완료 (9건 모두 deferred)
- P3 docs 정정 — ✓ 완료
- P4 closure — ✓ 완료

## 작업 항목

- [x] P1.1 `AuditEventType.java` enum 전체 추출 (44개 확정)
- [x] P1.2 backend src grep으로 emit caller 35건 식별
- [x] P1.3 미emit 9개 결정
- [x] P2.1 9개 각각의 ADR/docs/BETA cross-ref 수집
- [x] P2.2 deferred(의도) vs 누락(버그) 분류 (9 deferred / 0 missing)
- [x] P3.1 BETA-RELEASE.md §6 line 101 갱신 (42 → 44, 미emit cross-link 추가)
- [x] P3.2 `AuditEventType.java` 헤더 주석 "총 42개 → 총 44개", 인증 카테고리 주석 "(6) → (8)"
- [x] P3.3 baseline 빌드 면제 (주석/문서 only — 컴파일 의미 변경 0)
- [x] P4.1 `docs/progress.md` closure 엔트리
- [x] P4.2 `dev/active/audit-emit-gap-mapping/` → `dev/completed/audit-emit-gap-mapping/` 이동
- [x] P4.3 commit + PR

## 미완료 task 참조 블록

### P3.1 BETA-RELEASE.md §6 갱신

**작업 전 필독**
- `BETA-RELEASE.md` §6 line 96~104 (현재 audit emit coverage 행)
- `BETA-RELEASE.md` §7 line 106~113 (v1.x deferred 명시 — 미emit cross-ref 위치)

**원본 코드 참조**
- `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` (enum 44 ground truth)

**구현 대상**
- §6 line 101 audit emit coverage 셀의 "42 enum 중 35 emit (83%)" → "44 enum 중 35 emit (~80%) — 미emit 9개는 §7 deferred 매핑"
- 미emit 9개 각각 deferred 근거를 §7 행에 cross-link하거나 §6에 인라인 표 추가 (간결성 우선: §7 기존 항목과 1:1 매핑되는 형태로 inline 표)

**검증 참조**
- 갱신 후 `BETA-RELEASE.md`의 다른 "42" / "35/42" 언급 grep으로 0건 확인
- §6 ✓ 항목 변동 없는지 확인 (audit emit coverage 행만 수정)

**문서 반영**
- `docs/progress.md`에 closure 엔트리

### P3.2 AuditEventType.java 헤더 주석 정정

**작업 전 필독**
- `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` line 9~17 (헤더 javadoc), line 53 (인증 카테고리 주석)

**원본 코드 참조**
- 동일 파일

**구현 대상**
- line 12 javadoc "총 42개 값" → "총 44개 값"
- line 53 `// 인증 (6)` → `// 인증 (8)`

**검증 참조**
- `mvn compile -pl backend -DskipTests` (주석만 변경이므로 PASS 자명, 그러나 보장)

**문서 반영**
- 변경 없음 (주석 정정만)

### P4.1 progress.md closure 엔트리

**작업 전 필독**
- `docs/progress.md` 마지막 엔트리 형식

**구현 대상**
- 2026-05-05 세션 entry: "audit-emit-gap-mapping closure — enum 44/emit 35/미emit 9, 9개 모두 deferred 매핑, BETA §6 메트릭 정정 + AuditEventType.java 주석 정정"

**검증 참조**
- 없음 (docs)

**문서 반영**
- (자기 자신)

### P4.2 dev-docs 이동

**구현 대상**
- `dev/active/audit-emit-gap-mapping/` → `dev/completed/audit-emit-gap-mapping/` 디렉터리 이동

### P4.3 commit + PR

**구현 대상**
- 변경 파일: BETA-RELEASE.md, AuditEventType.java, docs/progress.md, dev/{active→completed}/audit-emit-gap-mapping/*
- 커밋 메시지: `docs(audit-emit-gap): BETA §6 메트릭 정정 (35/42→35/44, 미emit 9 모두 deferred 매핑)`
- PR 생성 후 URL 보고
