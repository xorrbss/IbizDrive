# Context — file-badge-share-count

## 데이터 흐름

```
frontend FileRow.tsx (L188-196)
    consumes: FileItem.shareCount (frontend/src/types/file.ts L29)
        wire: FolderItemDto.shareCount (백엔드 신규)
            api: GET /api/folders/{id}/items
                handler: FolderController -> FolderQueryService.loadItems
                    source: FolderRepository.findByParentIdAndDeletedAtIsNull
                            FileRepository.findByFolderIdAndDeletedAtIsNull
                            PermissionRepository.<new> (batch share count)
```

## 핵심 파일

| 파일 | 역할 | 변경 |
|---|---|---|
| `backend/.../permission/PermissionRepository.java` | JPA repo | + countActiveByResources(type, ids) |
| `backend/.../folder/dto/FolderItemDto.java` | wire mirror of FileItem | + Integer shareCount |
| `backend/.../folder/FolderQueryService.java` | loadItems 어셈블 | 배치 count + DTO 조립 |
| `backend/.../folder/FolderQueryServiceItemsTest.java` | Mockito unit | shareCount mock + assert |
| `backend/.../permission/PermissionRepositoryTest.java` | Testcontainers integration | + count test |
| `docs/02-backend-data-model.md` §7 | 응답 스펙 | + shareCount 필드 |
| `frontend/src/components/files/FileRow.test.tsx` (신규) | FE 배지 가드 | shareCount 분기만 |

## 의존성

- PR #199 (design-sweep-phase-2-explorer) — FileRow.tsx에 share 배지 UI 이미 mount
- types/file.ts L23-31 — `shareCount?: number` optional 필드 이미 선언

## 제약 / 위험

- **Testcontainers Windows SKIP** — `feedback_local_skip_ci_gap`, `reference_docker_desktop_windows_testcontainers`. 로컬 그린 ≠ CI 그린. PR push 후 CI 결과 가드.
- **CLAUDE.md §3 원칙 6** — DB 제약이 진실. permissions 테이블의 `(resource_id, subject_type, subject_id)` UNIQUE 제약 검증 → COUNT(*) ≡ COUNT(DISTINCT subject) 보장.
- **N+1 회피** — items 수가 100+면 per-item count 쿼리는 폭증. 단일 GROUP BY로 처리.
- **scope creep** — starred/restricted/itemsCount는 별도 트랙. 본 트랙에서 함께 wiring하지 않음.

## 메모리 참조

- `feedback_subagent_workflow` — task별 implementer + reviewer 디스패치. 이 트랙은 사이즈가 작아 controller 직접 구현으로 진행.
- `feedback_state_check_first` — 시작 전 master sync, PR 상태 확인 완료 (PR #202/#203/#206/#207 merged).
- `feedback_no_redundant_tests` — co-session이 동일 트랙 작업 중인지 확인. v1x-backlog에 FileRow badge 항목 없음, co-session은 admin-grid-rebuild + audit-severity + p4-kpi-delta 중 → 중복 없음.
