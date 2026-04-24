# 회사 문서관리 시스템 — 설계 문서 세트

## 파일 구성

### 루트
- `CLAUDE.md` — Claude Code 라우팅 파일 (자동 로드됨)
- `BRIEF-M1-routing.md` — 프론트엔드 M1 세션 지시서
- `BRIEF-BE-M1-schema-folders.md` — 백엔드 M1 세션 지시서
- `README.md` — 이 파일

### docs/
- `00-overview.md` — 시스템 개요, 문서 간 계약, ADR
- `01-frontend-design.md` — 프론트엔드 아키텍처 (v3, 완성)
- `02-backend-data-model.md` — DB 스키마, 트랜잭션, API 계약 (완성)
- `03-security-compliance.md` — 보안/컴플라이언스 (스켈레톤)
- `04-admin-operations.md` — 관리자/운영 (스켈레톤)
- `progress.md` — 세션 진행 기록 (빈 상태)

## 시작하기

1. 이 전체를 프로젝트 루트에 복사
   ```bash
   mkdir docmgmt && cd docmgmt
   # 압축 해제한 파일들을 여기로 이동
   ```

2. Claude Code 설치 (미설치 시)
   ```bash
   # macOS / Linux
   curl -fsSL https://claude.ai/install.sh | bash
   # 또는 npm install -g @anthropic-ai/claude-code
   ```

3. 첫 세션 시작
   ```bash
   cd docmgmt
   claude
   ```

   첫 프롬프트:
   ```
   이 프로젝트의 CLAUDE.md를 먼저 읽고, 그다음 BRIEF-M1-routing.md를 읽어줘.
   이후 BRIEF에 정의된 프론트 M1을 frontend/ 디렉토리 아래에 구현해줘.

   진행 규칙:
   1. BRIEF §2의 "읽지 말 것" 목록은 로드하지 않기
   2. BRIEF §4 핵심 원칙 체크를 각 단계마다 자가 확인
   3. 모호한 부분은 구현 전에 먼저 물어보기
   4. 완료 후 §5 완료 기준 전부 체크
   5. 세션 종료 시 §6 양식대로 docs/progress.md에 기록
   ```

4. 백엔드는 별도 터미널 창에서 병렬로
   ```bash
   cd docmgmt
   claude
   ```
   → `BRIEF-BE-M1-schema-folders.md` 지정

## 문서 읽는 순서

| 독자 | 먼저 읽을 것 |
|---|---|
| 전체 파악 | `docs/00-overview.md` |
| 프론트 팀 | `CLAUDE.md` → `docs/01-frontend-design.md` |
| 백엔드 팀 | `CLAUDE.md` → `docs/02-backend-data-model.md` |
| 보안 팀 | `docs/03-security-compliance.md` |
| 운영 팀 | `docs/04-admin-operations.md` |

## 주의사항

- `docs/01-frontend-design.md`와 `docs/02-backend-data-model.md`는 각각 1300+, 800+ 줄입니다. **전체 로드 금지**, 섹션 번호로 접근 (`CLAUDE.md §2` 참조).
- `03`, `04`는 스켈레톤 (TODO 체크박스). 해당 영역 구현 전에 본문을 먼저 채우는 것을 권장.
- 설계 변경은 반드시 `docs/00-overview.md §5 ADR`에 기록.
