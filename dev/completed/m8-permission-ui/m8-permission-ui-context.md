---
Last Updated: 2026-04-29
---

# M8 — Context

## 현재 상태

- `src/types/permission.ts` 백엔드 미러 완성 (9 권한 + 5 preset).
- `src/hooks/usePermission.ts` TODO stub — lowercase 키 + 모두 true 하드코딩.
- `BulkActionBar`만 `usePermission()` 사용 (`can.download/move/edit/delete`).
- ShareDialog/useShareUiStore 미존재.
- `src/lib/queryKeys.ts` `qk.all + qk.files + qk.search + qk.trash` 패턴.
- 기존 hook test pattern: vi.mock + QueryClientProvider wrapper.

## 마이그레이션 영향

- `BulkActionBar` `can.download → can.DOWNLOAD` 등 4개 필드 변경. 동일 PR에서 처리.
- BulkActionBar.test.tsx는 usePermission을 직접 mock하지 않음 (real 훅 + QueryClient). api.getEffectivePermissions만 mock하면 통과.
- 다른 컴포넌트 사용처 0 — 회귀 위험 낮음.

## 결정

- usePermission 반환 타입: `Record<Permission, boolean>`. 권한 enum은 UPPER_SNAKE_CASE.
- 기본 mock 응답: admin preset (PURGE 제외 8 권한).
- ShareDialog 단순화: 링크 placeholder (`https://ibiz.example/share/{fileId}`) + 복사 + 닫기. 만료/권한 옵션은 비범위.
- ShareDialog 진입점: BulkActionBar 단일 파일 선택 시 "공유" 버튼만. FileRow 우클릭 등은 후속.
