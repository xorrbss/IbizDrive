# 프로젝트: 회사 문서관리 시스템

> **Claude Code에게**: 이 파일은 작업 전 매번 확인하는 네비게이션 문서입니다.
> 전체 문서를 한 번에 로드하지 말고, 아래 라우팅 표에서 해당 작업에 필요한 섹션만 읽으세요.

---

## 1. 프로젝트 개요

사내 구성원이 문서를 안전하게 저장·공유·버전관리하는 웹 기반 파일 시스템.

### 스택

```text
Frontend: Next.js 15 (App Router) + TypeScript
          Zustand + TanStack Query v5 + dnd-kit + TanStack Virtual
Backend:  Node.js (NestJS 예정) 또는 Spring Boot
DB:       PostgreSQL 15+
Storage:  S3 호환 (AWS S3 / MinIO)
```

### 디렉토리

```text
docs/              설계 문서 (아래 §2 라우팅 참조)
src/               소스 코드
  components/      UI 컴포넌트
  stores/          Zustand 슬라이스
  hooks/           커스텀 훅
  lib/             유틸리티, API, 쿼리 키
  types/           TypeScript 타입
app/               Next.js App Router
  (explorer)/      탐색기 레이아웃
  api/             API 프록시 라우트
```

---

## 2. 문서 라우팅 (작업 시작 전 반드시 확인)

| 작업 유형 | 읽을 문서 / 섹션 |
|---|---|
| 전체 개요, 용어, ADR | `docs/00-overview.md` |
| URL 라우팅 (workspace prefix) | `docs/01-frontend-design.md` §2~§4, §17, spec `2026-05-09-team-centric-pivot-design.md` §5.1 |
| 사이드바 3-section 트리 | `docs/01-frontend-design.md` §2, spec `2026-05-09-team-centric-pivot-design.md` §4.5 |
| 프론트 컴포넌트, 라우팅 | `docs/01-frontend-design.md` §2~§4, §17 |
| Zustand 슬라이스 | `docs/01-frontend-design.md` §5 |
| TanStack Query 캐시/무효화 | `docs/01-frontend-design.md` §6 |
| DnD (드래그) 구현 | `docs/01-frontend-design.md` §7 |
| 선택 모델, BulkActionBar | `docs/01-frontend-design.md` §8 |
| 업로드 UI/로직 | `docs/01-frontend-design.md` §9, `docs/02-backend-data-model.md` §6.1 |
| 검색 | `docs/01-frontend-design.md` §10 |
| 키보드/접근성 | `docs/01-frontend-design.md` §12 |
| 휴지통 (workspace 분리: `/trash/d/*`, `/trash/t/*`) | `docs/01-frontend-design.md` §13, `docs/02-backend-data-model.md` §6.5 / §7.11, spec `2026-05-10-team-centric-pivot-plan-e-trash-workspace-split-design.md` |
| 권한 (프론트) | `docs/01-frontend-design.md` §14 |
| 실시간 동기화 | `docs/01-frontend-design.md` §15 |
| DB 스키마 | `docs/02-backend-data-model.md` §2 |
| 제약 조건, 정규화 | `docs/02-backend-data-model.md` §3, §4 |
| 저장소 정책 | `docs/02-backend-data-model.md` §5 |
| 트랜잭션, 동시성 제어 | `docs/02-backend-data-model.md` §6 |
| API 스펙 | `docs/02-backend-data-model.md` §7 |
| 에러 코드 | `docs/02-backend-data-model.md` §8 |
| 위협 모델, 인증 | `docs/03-security-compliance.md` §1, §2 (스켈레톤) |
| 권한 매트릭스 | `docs/03-security-compliance.md` §3 |
| 감사 이벤트 | `docs/03-security-compliance.md` §4 (스켈레톤) |
| 저장소 보안 | `docs/03-security-compliance.md` §5 |
| Legal Hold | `docs/03-security-compliance.md` §6.3 |
| 관리자 페이지 | `docs/04-admin-operations.md` §2~§10 (스켈레톤) |
| 배치 작업 | `docs/04-admin-operations.md` §13 |
| 사내 베타 운영 런북 (Wave 2 closure) | `docs/04-admin-operations.md` §15 |
| 로컬 dev preview 셋업 (frontend·backend·DB·시드·트러블슈팅) | `docs/local-dev.md` |

### 큰 문서 접근 전략

`01-frontend-design.md`(1323줄), `02-backend-data-model.md`(815줄)는 **전체 로드 금지**. 항상 섹션 번호로 접근:

```bash
# 좋음
→ "docs/01-frontend-design.md의 §9 업로드 섹션만 읽어줘"

# 나쁨
→ "docs/01-frontend-design.md 읽고..."
```

`grep -n "## 9" docs/01-frontend-design.md`로 섹션 라인 번호 찾아 `view --view_range` 권장.

---

## 3. 절대 깨지 않을 핵심 원칙

작업 중 아래 원칙과 충돌하는 구현은 중단하고 사용자에게 확인 요청.

### 프론트 (docs/01 §1, §19)

1. **URL이 "어디"를 소유한다.** workspace + folderId 모두 URL이 진실 (`/d/:deptId/:folderId/...`, `/t/:teamId/:folderId/...`, `/shared/:folderId/...`). 사이드바 expand state만 localStorage(persist), workspace/folderId 절대 Zustand 복제 금지.
2. **RightPanel은 query param** (`?file=xxx`). parallel route 쓰지 않음.
3. **낙관적 업데이트는 비파괴적 액션만.** 이동/삭제/권한 변경은 pending 로딩 상태로 처리.
4. **DnD 컨텍스트 두 개는 섞지 않음.** 업로드 DnD(window 네이티브) ≠ 이동 DnD(dnd-kit).
5. **가상화 시 aria-rowcount/rowindex 필수.**

### 백엔드 (docs/02 §1)

6. **DB 제약이 진실의 출처.** `UNIQUE (folder_id, normalized_name) WHERE deleted_at IS NULL` 없이 충돌 검사만으로 구현 금지.
7. **업로드 완료/이동/복원은 반드시 트랜잭션 + SELECT FOR UPDATE.** race condition 방지.
8. **audit_log는 append-only.** DB 레벨 `REVOKE UPDATE, DELETE` 필수. 애플리케이션 레벨 보증만으로 부족.
9. **storage_key는 UUID.** 원본 파일명은 DB에만, S3 객체 키에 넣지 않음.
10. **파괴적 액션은 백엔드에서 재검증.** 프론트 권한은 UX용, 보안 아님.

### 공통

11. **정규화 함수는 프론트/백엔드 동일 로직** (`normalizeFileName`, `normalizeForSearch`). 테스트 케이스 공유.
12. **에러 코드 (docs/02 §8)는 계약이다.** 새 에러 추가 시 양쪽 동기화.
13. **데스크탑 메인, 모바일 미지원.** 사내 데스크탑 가정. `lg:` breakpoint 분기, `.mobile-view` 클래스, `useMediaQuery`/뷰포트 감지 hook, 사이드바 mobile overlay, RightPanel mobile auto-hide, FileTable 컬럼 축약 등 **모바일 UX 작업은 backlog에서 제외**. design 핸드오프 G7 항목도 폐기(2026-05-11 사용자 결정). 좁은 데스크탑 폭은 기존 `useSidebarChromeStore` 사용자 토글로 충분.

---

## 4. 자주 참조하는 계약 파일

구현 시 다음 파일들은 **설계 문서의 코드 표현**이므로 변경 시 설계 문서도 함께 업데이트:

| 파일 | 설계 문서 | 역할 |
|---|---|---|
| `src/lib/queryKeys.ts` | docs/01 §6.1 | TanStack Query 키 팩토리 |
| `src/lib/normalize.ts` | docs/02 §3 | NFC 정규화 함수 |
| `src/lib/folderPath.ts` | docs/01 §17.3 | URL canonical 빌더 |
| `src/types/permission.ts` | docs/03 §3 | 권한 enum |
| `src/types/audit.ts` | docs/03 §4 | 감사 이벤트 enum |
| `src/lib/errors.ts` | docs/02 §8 | 에러 코드 상수 |
| `src/lib/keyboardShortcuts.ts` | docs/01 §12.1 | 키보드 단축키 cheat sheet 데이터 |

---

## 5. 작업 지시 해석 가이드

사용자가 작업을 지시할 때:

### 5.1 모호한 지시를 받은 경우

"파일 목록 만들어줘" 같은 지시 → **바로 구현하지 말고** 아래 순서로 확인:

1. `docs/01 §18` 로드맵에서 해당 마일스톤이 무엇인지 확인
2. 연관된 설계 섹션 읽고 **의존성 확인** (예: FileTable은 라우팅 + useCurrentFolder 선행)
3. 범위를 사용자에게 제안: "마일스톤 3 (FileTable)을 구현하려면 마일스톤 1, 2가 먼저 필요합니다. 어느 단계부터 시작할까요?"

### 5.2 설계 문서와 다른 방향 요청을 받은 경우

사용자가 설계와 다르게 구현하라고 할 때 (예: "그냥 Zustand에 folderId 넣자"):

1. 충돌하는 원칙을 인용하며 확인: "§19 원칙 1과 충돌합니다 (URL folderId 중심). 의도적 변경이면 진행하고 ADR 업데이트하겠습니다. 아니면 기존 원칙대로 갈까요?"
2. 의도적 변경이면 `docs/00-overview.md §5 ADR`에 기록 + 영향받는 문서 섹션 함께 업데이트.

### 5.3 문서에 없는 영역을 요청받은 경우

설계에 아직 없는 기능 (예: 즐겨찾기 상세 구현):

1. **먼저 설계 문서에 해당 섹션을 추가**한 뒤 구현
2. 관련 문서 선택:
   - UI/상태 → 01
   - API/DB → 02
   - 권한/감사 → 03
   - 관리자 기능 → 04

---

## 6. 명령어 (프로젝트 설정 후 갱신)

```bash
# 개발
pnpm dev              # Next.js 개발 서버
pnpm db:migrate       # DB 마이그레이션 (Prisma 또는 Flyway)
pnpm db:seed          # 시드 데이터

# 품질
pnpm lint
pnpm typecheck
pnpm test             # Vitest
pnpm test:e2e         # Playwright

# 빌드
pnpm build
```

---

## 7. 진행 상황 추적

각 세션 종료 시 `docs/progress.md`에 아래 형식으로 기록:

```markdown
## YYYY-MM-DD 세션
### 완료
- [M1] folderId 중심 라우팅 (catch-all + canonical redirect)
- [M1] FolderTree, Breadcrumb 연결

### 다음 세션 컨텍스트
- 현재 FolderTree는 tree API가 전체 반환 가정. 10k+ 폴더 시 lazy 로딩 필요 (docs/02 §9.2)
- useCurrentFolder가 folderPath API 호출 2번. 서버가 breadcrumb 포함해 내려주도록 §7.3 수정 필요

### 블로커
- docs/03 §3 권한 매트릭스 미완성 → 백엔드 미들웨어 구현 불가
```

---

## 8. 체크리스트 (모든 PR 전)

- [ ] 관련 설계 문서 섹션을 읽었는가
- [ ] §3의 핵심 원칙 11개와 충돌하지 않는가
- [ ] 새 에러 코드 / 쿼리 키 / 권한 enum 추가 시 설계 문서도 업데이트했는가
- [ ] `pnpm typecheck && pnpm lint && pnpm test` 통과
- [ ] DB 스키마 변경 시 마이그레이션 포함
- [ ] `docs/progress.md`에 진행 기록
