---
Last Updated: 2026-04-30
Status: 📋 BOOTSTRAP — M11.1 진입 대기
---

# M11 — 검색 (useSearch + SearchBar)

## 목적

docs/01 §10 견고한 검색 구현. debounce + AbortController + normalize 일치 + 최소 2자 + placeholderData.

## 범위

- `qk.search(normalized, filters)` 캐시 키 팩토리
- `api.searchFiles({ q, filters }, { signal })` mock (MOCK_FILES 전체 normalize 매칭)
- `useDebounce<T>` 훅 (300ms)
- `useSearch(rawQuery, filters)` 훅
- `SearchBar` 컴포넌트 + TopBar 통합
- 결과 드롭다운 (클릭 → useOpenFile)
- `/` 글로벌 단축키 + Esc 닫기

## 비범위 (Out of Scope)

- 백엔드 실제 search endpoint 연결 (M5 이후 실서버 교체 시점)
- 고급 필터 UI (filters는 빈 객체로 시작, YAGNI)
- 검색 결과 별도 라우트 페이지 (`/search`) — 드롭다운만

## 의존

- `lib/normalize.ts` `normalizeForSearch` ✅ 존재
- `lib/queryKeys.ts` `qk` ✅ 존재 (확장 필요)
- `lib/api.ts` ✅ 존재 (확장 필요)
- `hooks/useOpenFile.ts` ✅ 존재 (결과 클릭 핸들러)
- `hooks/useGlobalShortcuts.ts` ✅ 존재 (`/` 단축키 추가)

## 핵심 설계 결정

- **결과 표시 = 드롭다운** (별도 페이지 X). docs/01 §10 placeholderData 의도 + KISS.
- **filters = `{}` MVP**. 추후 확장 시 qk.search 시그니처 호환.
- **AbortController**: TanStack Query queryFn signal 그대로 fetch/setTimeout abort에 연결.
- **Input focus 시 글로벌 단축키 무시**: useGlobalShortcuts 기존 패턴 유지.

## Phase 분할

| Phase | 산출물 |
|---|---|
| M11.0 | dev-docs 3파일 + commit |
| M11.1 | lib infra (`qk.search`, `api.searchFiles`, `useDebounce`) + 단위 테스트 |
| M11.2 | `useSearch` 훅 + 단위 테스트 |
| M11.3 | `SearchBar` 컴포넌트 + TopBar 통합 + `/` 단축키 + 결과 드롭다운 + RTL 테스트 |
| M11.4 | closure (dev-docs-update + dev/active→completed + progress.md + PR) |

## DoD

1. ✅ `useSearch('가', {})` → enabled false (1자)
2. ✅ `useSearch('계약', {})` → 300ms debounce 후 1회 호출, 추가 키 입력 시 이전 호출 abort
3. ✅ MOCK_FILES에서 normalizeForSearch 매칭 결과 반환
4. ✅ SearchBar `/` 키로 포커스, Esc로 결과 닫기
5. ✅ 결과 클릭 시 useOpenFile로 RightPanel 열림
6. ✅ vitest 새로 추가된 모든 케이스 GREEN
7. ✅ `npm run typecheck && npm run lint && npm run test` 전부 통과
