# 00 - 시스템 개요

> 회사 문서관리 시스템 설계 문서 세트의 입구.
> 각 팀이 자기 영역부터 읽고, 계약(contract) 지점만 공유합니다.

---

## 1. 시스템 개요

### 1.1 목적
사내 구성원이 문서를 안전하게 저장·공유·버전관리하는 웹 기반 파일 시스템.

### 1.2 주요 사용자
- **일반 사용자**: 업로드, 탐색, 공유, 버전 관리
- **관리자**: 사용자/부서/권한 관리, 감사 로그 열람
- **감사자 (선택)**: 읽기 전용 감사 로그 접근

### 1.3 기술 스택
```text
Frontend: Next.js 15 (App Router) + TypeScript + Zustand + TanStack Query v5 + dnd-kit
Backend:  TBD (Node.js/NestJS 또는 Spring Boot 권장)
DB:       PostgreSQL 15+
Storage:  S3 호환 (AWS S3 / MinIO / Azure Blob)
Search:   Postgres tsvector (MVP) → OpenSearch (v1.x)
Realtime: Polling (MVP) → SSE (v1.x)
```

---

## 2. 문서 구조

| 문서 | 주요 독자 | 상태 |
|---|---|---|
| **00-overview.md** (본 문서) | 전체 | ✅ |
| **01-frontend-design.md** | 프론트엔드 팀 | ✅ (v3 완성) |
| **02-backend-data-model.md** | 백엔드 팀, DBA | ✅ |
| **03-security-compliance.md** | 보안 팀, 백엔드 팀 | 🔲 스켈레톤 |
| **04-admin-operations.md** | 운영 팀, 관리자 | 🔲 스켈레톤 |

---

## 3. 문서 간 계약(Contract) 지점

문서가 분리되어 있으므로 **팀 간 동기화가 필요한 지점**만 명시:

### 3.1 API 계약
- 모든 엔드포인트 스키마는 **02-backend-data-model.md §7**이 단일 진실 출처
- 프론트는 OpenAPI spec에서 타입 생성 (`openapi-typescript`)
- 변경 시: 02 문서 PR → 프론트 팀 리뷰 필수

### 3.2 권한 모델
- 권한 enum(`read/upload/edit/delete/download/move/share/admin`)은 **03-security-compliance.md §3**이 단일 진실 출처
- 프론트 `usePermission` 훅과 백엔드 `requirePermission` 미들웨어가 동일 enum 사용

### 3.3 정규화 함수
- `normalizeFileName`, `normalizeForSearch` 구현은 **프론트/백엔드 동일 로직**
- 02 문서 §2.4에 의사 코드 정의
- 테스트 케이스 공유 (양쪽 CI에서 동일 결과 검증)

### 3.4 감사 이벤트 타입
- `AuditEventType` enum은 **03-security-compliance.md §4**에서 정의
- 프론트/백엔드 모두 참조

### 3.5 에러 코드
- 403 (권한 없음) / 409 (충돌) / 413 (용량 초과) / 423 (잠김) 등
- **02-backend-data-model.md §8**에 전체 목록
- 프론트 전역 에러 핸들러와 매칭

---

## 4. 개발 마일스톤

### 4.1 MVP (8~12주)
```text
Week 1-2   : DB 스키마 + 기본 인증 + 업로드/다운로드 API
Week 3-4   : 폴더 트리 + 파일 목록 + FolderTree/Breadcrumb UI
Week 5-6   : 권한 시스템 + 공유 + BulkActionBar
Week 7-8   : 버전 관리 + 휴지통 + ConflictDialog
Week 9-10  : 감사 로그 + 관리자 기본 기능
Week 11-12 : QA + 보안 점검 + 베타
```

### 4.2 v1.x (MVP 후 3~6개월)
```text
- tus 재개 업로드
- SSE 실시간 동기화
- 전문 검색 (OpenSearch)
- 바이러스 스캔
- 외부 링크 공유
- 파일 잠금 (pessimistic)
```

### 4.3 v2.x (장기)
```text
- Co-authoring (Office Online 연동)
- Legal Hold
- 부서 조직도 고급 연동 (SCIM)
- DLP (Data Loss Prevention)
```

---

## 5. 의사결정 로그 (ADR)

| # | 결정 | 근거 | 문서 |
|---|---|---|---|
| 1 | URL folderId 중심 (slug는 표시용) | 폴더 rename/move 시 링크 안정성 | 01 §2 |
| 2 | RightPanel은 query param | parallel route 과잉, 단일 오버레이면 query param이 표준 | 01 §2.3 |
| 3 | 낙관적 업데이트 = 비파괴적만 | 403 롤백 시 UX 파괴 방지 | 01 §1.3 |
| 4 | unique constraint = partial index | 휴지통 파일은 이름 충돌 허용 | 02 §3.2 |
| 5 | storage_key = UUID | 경로 추측 공격 방지, 한글/특수문자 독립 | 02 §5 |
| 6 | 업로드 완료 시점 트랜잭션 체크 | 동시 업로드 race 방지 | 02 §6.1 |
| 7 | MVP 업로드 = multipart | tus 복잡도 대비 ROI, 파일 크기 분포가 소형 중심 | 01 §9 |
| 8 | MVP 실시간 = 폴링 | SSE 인프라 복잡도 회피 | 01 §15.1 |
| 9 | 뷰 로그 = 민감 폴더만 | 로그 폭증 방지 | 03 §4.2 |
| 10 | 문서 분리 | v3 단일 문서 2000줄 초과, 팀 병렬화 필요 | 00 본 문서 |

---

## 6. 용어집

| 용어 | 정의 |
|---|---|
| **folderId** | 폴더의 불변 식별자 (UUID). URL canonical key |
| **storage_key** | 저장소 객체 키 (UUID). 원본 파일명과 무관 |
| **normalized_name** | NFC + lowercase + trim 처리된 파일명. 중복 검사용 |
| **current_version_id** | 파일의 "현재 표시 버전" 참조 |
| **effective permission** | 사용자/부서/역할 권한을 합산한 최종 권한 |
| **tombstone** | 휴지통 상태 (deleted_at IS NOT NULL, purge_after 이전) |
| **purge** | 영구 삭제 (저장소 객체 + DB row 모두 제거) |
| **audit_level** | 폴더별 감사 레벨 (standard / strict). view 로그 여부 결정 |

---

## 7. 다음에 작성할 문서

- **03-security-compliance.md** 본문 (권한 매트릭스, 감사 대상, 저장소 보안)
- **04-admin-operations.md** 본문 (관리자 페이지, 쿼터, 백업)
- 각 도메인별 API OpenAPI spec
- DB 마이그레이션 스크립트
