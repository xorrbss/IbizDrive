# Plan — file-badge-share-count (P2c)

## 목표

FileRow의 share 배지(`Users icon + count`)가 실제 데이터로 노출되도록 백엔드 wiring.

- 현재 상태: FileRow.tsx L188 `{typeof item.shareCount === 'number' && item.shareCount > 1 && ...}` — `shareCount`는 optional, 호출부 미전달 → 배지 항상 비표시 (PR #199 design-sweep-phase-2 placeholder)
- 목표 상태: `GET /api/folders/{id}/items` 응답 항목에 `shareCount: int` 포함, FileRow가 실데이터로 배지 노출

## 범위

- 백엔드: `FolderItemDto`에 `shareCount` 필드 추가, `FolderQueryService.loadItems()`에서 배치 계산
- 프론트: 변경 없음 (FileItem 타입 이미 optional, 임계값 `> 1` 유지)
- 테스트: 백엔드 mock + integration

## 비범위

- starred / restricted / itemsCount (별도 트랙 P2a/P2b/P2d)
- shareCount 변경 시 실시간 broadcast (v2.x)
- 상속 grant 카운트 (이 트랙은 direct grants only)

## shareCount 의미 (계약)

```text
shareCount = COUNT(*) FROM permissions
             WHERE resource_type = 'folder'|'file'
               AND resource_id = <item id>
               AND (expires_at IS NULL OR expires_at > NOW())
```

- **direct grants만** — 상속 grant 미포함 (UX 단순성: 모든 항목에 배지가 뜨는 폭증 방지)
- **소유자 제외** — `permissions` 테이블은 owner row 없음, 자연 제외
- **expired 제외** — `findEffective`와 동일 시점 정책
- **0인 경우 null** — `@JsonInclude(NON_NULL)`로 키 자체 omit (네트워크 절약)

FE 임계값 `> 1`은 유지 — "2건 이상의 explicit 공유 grant가 있는 경우만 배지". 1건 단발 공유는 시각적 노이즈로 간주 (PR #199 의도 답습).

## 단위 분할

1. **PermissionRepository** — `countActiveByResources(resourceType, ids)` 메서드 (단일 GROUP BY 쿼리, batch)
2. **FolderItemDto** — `Integer shareCount` 필드 + 기존 factory 시그니처 변경 (호출부 단일점)
3. **FolderQueryService.loadItems** — items 수집 후 folder/file id 그룹별 count 조회, DTO 조립 시 주입
4. **백엔드 테스트** — Mockito test 확장 (shareCount 누적), PermissionRepository integration test 추가
5. **docs/02-backend-data-model.md** §7 — `/api/folders/{id}/items` 응답 스펙에 `shareCount` 항목 추가
6. **frontend FileRow.test.tsx** (신규) — shareCount 배지 케이스만 (`< 2` 비표시, `>= 2` 표시)

## acceptance

- `./gradlew test` Mockito test PASS
- `./gradlew test` PermissionRepositoryTest PASS (Docker 가용 시 — 로컬 windows SKIP, CI Linux RUN)
- `pnpm typecheck` PASS
- `pnpm test FileRow` PASS
- docs §7 응답 스펙에 `shareCount` 명시
