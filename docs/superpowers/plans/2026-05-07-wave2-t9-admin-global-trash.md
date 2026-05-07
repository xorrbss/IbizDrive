# Wave 2 T9 — Admin Global Trash 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ADMIN이 시스템 전체 휴지통(`/admin/trash/all`)을 한 화면에서 보고 단건 복원/영구삭제 할 수 있도록 신규 listing endpoint(`GET /api/admin/trash`) 와 admin frontend 페이지를 추가한다. 기존 mutation은 재사용.

**Architecture:** 백엔드는 admin 전용 listing(`AdminTrashController` + `AdminTrashService` + `AdminTrashRepository`)만 신규로 추가하고, restore/purge는 기존 endpoint(`POST /api/files|folders/{id}/restore`, `DELETE /api/trash/{type}/{id}`) 를 그대로 호출한다(ADMIN ROLE이 SpEL 가드 통과). admin DTO는 owner/originalParent/size 메타를 추가로 노출. 프론트엔드는 cursor 페이지네이션 + filter(q/type/ownerId) 표 + ConfirmDialog + AdminSideNav 토글로 구성.

**Tech Stack:** Spring Boot 3 + JPA(native @Query) + Spring Security(SpEL `hasRole('ADMIN')`) / Next.js 15 App Router + TanStack Query v5 + Zustand / TypeScript strict / Vitest + jsdom (frontend) + JUnit 5 + `@WebMvcTest` (backend). 작업 디렉토리: backend는 `cd backend && ./gradlew ...`, frontend는 `cd frontend && npm run ...` (pnpm 아님).

**설계 근거:** `docs/superpowers/specs/2026-05-07-wave2-t9-admin-global-trash-design.md`

---

## 파일 구조

### 신규 파일 (backend)
| 경로 | 책임 |
|---|---|
| `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashController.java` | `GET /api/admin/trash` + `@PreAuthorize("hasRole('ADMIN')")` |
| `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashService.java` | `list(filters, cursorWire, limitOpt)` — repo 호출 + merge sort + cursor encode |
| `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashRepository.java` | native @Query 2종 (file/folder admin page) — JOIN users + JOIN folders(parent), LIKE escape |
| `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashItemDto.java` | record (10 fields) |
| `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashPage.java` | record (`items`, `nextCursor`) |
| `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashFilters.java` | record (`q`, `type`, `ownerId`) |
| `backend/src/test/java/com/ibizdrive/admin/trash/AdminTrashServiceTest.java` | service 단위 테스트 |
| `backend/src/test/java/com/ibizdrive/admin/trash/AdminTrashControllerTest.java` | `@WebMvcTest` |

### 수정 파일 (backend)
| 경로 | 변경 |
|---|---|
| `backend/src/main/java/com/ibizdrive/trash/TrashCursor.java` | package-private → `public` (admin/trash 패키지에서 재사용) |

### 신규 파일 (frontend)
| 경로 | 책임 |
|---|---|
| `frontend/src/hooks/useAdminTrash.ts` | `useAdminTrashList` + `useAdminRestoreTrashItem` + `useAdminPurgeTrashItem` |
| `frontend/src/hooks/useAdminTrash.test.tsx` | hooks 단위 테스트 |
| `frontend/src/app/admin/trash/all/page.tsx` | 페이지 (FilterBar + Table + ConfirmDialog + cursor 페이지네이션) |
| `frontend/src/app/admin/trash/all/page.test.tsx` | 페이지 테스트 |
| `frontend/src/lib/api.adminTrash.test.ts` | api 어댑터 테스트 |

### 수정 파일 (frontend)
| 경로 | 변경 |
|---|---|
| `frontend/src/types/trash.ts` | `AdminTrashItem`, `AdminTrashFilters`, `AdminTrashPage` 추가 |
| `frontend/src/lib/api.ts` | `adminListTrash(filters, cursor)` 추가 |
| `frontend/src/lib/queryKeys.ts` | `qk.adminTrash`, `qk.adminTrashList(...)` + `invalidations.afterAdminTrashChanged` |
| `frontend/src/components/admin/AdminSideNav.tsx` | '휴지통' DEFERRED → ACTIVE_ITEMS |
| `frontend/src/app/admin/page.tsx` | 랜딩 카드 '전역 휴지통' 추가 |

### 수정 파일 (docs)
| 경로 | 변경 |
|---|---|
| `docs/02-backend-data-model.md` §7.11 | `GET /api/admin/trash` 행 + 풀 스펙 |
| `docs/04-admin-operations.md` §2 / §8.3 | 활성 라우트 6, `/admin/trash/all` swap |
| `BETA-RELEASE.md` §7 | admin frontend wording 갱신 |
| `docs/progress.md` | 세션 기록 |

---

## Pre-flight: dev 디렉토리 + 브랜치

**Files:**
- Create: `dev/active/wave2-t9-admin-global-trash/README.md`

- [ ] **Step 1: 작업 브랜치 생성**

```bash
git checkout master && git pull
git checkout -b feat/wave2-t9-admin-global-trash
```

- [ ] **Step 2: dev/active 디렉토리 생성 + README**

```bash
mkdir -p dev/active/wave2-t9-admin-global-trash
```

`dev/active/wave2-t9-admin-global-trash/README.md`:

```markdown
# Wave 2 T9 — Admin Global Trash

- spec: docs/superpowers/specs/2026-05-07-wave2-t9-admin-global-trash-design.md
- plan: docs/superpowers/plans/2026-05-07-wave2-t9-admin-global-trash.md
- 시작: 2026-05-07
- 머지 후 dev/completed/ 로 이동
```

- [ ] **Step 3: 초기 commit**

```bash
git add dev/active/wave2-t9-admin-global-trash/README.md
git commit -m "chore(wave2-t9): bootstrap dev directory"
```

---

## P1 (BE-1): Repository — admin trash page native queries

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/trash/TrashCursor.java` (visibility)
- Create: `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashRepository.java`

### Task P1.1: TrashCursor를 public 으로

- [ ] **Step 1: visibility 변경**

`backend/src/main/java/com/ibizdrive/trash/TrashCursor.java`:

```java
// 17행: record TrashCursor(...)
public record TrashCursor(Instant deletedAt, UUID id) {

    public static String encode(Instant deletedAt, UUID id) {
        // 본문 변경 없음
    }

    public static TrashCursor decode(String wire) {
        // 본문 변경 없음
    }
}
```

`record` / `encode` / `decode` 모두 `public` 으로. 본문은 그대로.

- [ ] **Step 2: 빌드 확인**

```bash
cd backend && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: commit**

```bash
git add backend/src/main/java/com/ibizdrive/trash/TrashCursor.java
git commit -m "refactor(trash): make TrashCursor public for admin/trash reuse"
```

### Task P1.2: AdminTrashRepository — file admin page native @Query

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashRepository.java`
- Reference: `backend/src/main/java/com/ibizdrive/file/FileRepository.java:172-181` (findTrashedPage 패턴)

- [ ] **Step 1: skeleton 작성 (file 쿼리만)**

```java
// backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashRepository.java
package com.ibizdrive.admin.trash;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.folder.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wave 2 T9 — admin global trash listing (docs/02 §7.11, ADR #32).
 *
 * <p>본 repo는 listing 전용. mutation은 기존 {@link com.ibizdrive.file.FileRepository}/
 * {@link com.ibizdrive.folder.FolderRepository}/{@code TrashPurgeService} 재사용.
 *
 * <p>두 native @Query는 {@code (:param IS NULL OR ...)} 패턴으로 q/ownerId 옵션 처리.
 * {@code q}는 호출자가 사전 escape + wildcard wrap. 정렬은 {@code deleted_at DESC, id DESC}
 * (TrashCursor key와 동일).
 */
@Repository
public interface AdminTrashRepository extends JpaRepository<FileItem, UUID> {

    @Query(value = """
        SELECT f.* FROM files f
        WHERE f.deleted_at IS NOT NULL
          AND (:q IS NULL OR LOWER(f.name) LIKE :q ESCAPE '\\')
          AND (CAST(:ownerId AS uuid) IS NULL OR f.owner_id = CAST(:ownerId AS uuid))
          AND (
            CAST(:cursorDeletedAt AS timestamptz) IS NULL
            OR f.deleted_at < CAST(:cursorDeletedAt AS timestamptz)
            OR (f.deleted_at = CAST(:cursorDeletedAt AS timestamptz) AND f.id < CAST(:cursorId AS uuid))
          )
        ORDER BY f.deleted_at DESC, f.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<FileItem> findTrashedFilesAdminPage(
        @Param("q") String q,
        @Param("ownerId") UUID ownerId,
        @Param("cursorDeletedAt") Instant cursorDeletedAt,
        @Param("cursorId") UUID cursorId,
        @Param("limit") int limit
    );

    @Query(value = """
        SELECT fd.* FROM folders fd
        WHERE fd.deleted_at IS NOT NULL
          AND (:q IS NULL OR LOWER(fd.name) LIKE :q ESCAPE '\\')
          AND (CAST(:ownerId AS uuid) IS NULL OR fd.owner_id = CAST(:ownerId AS uuid))
          AND (
            CAST(:cursorDeletedAt AS timestamptz) IS NULL
            OR fd.deleted_at < CAST(:cursorDeletedAt AS timestamptz)
            OR (fd.deleted_at = CAST(:cursorDeletedAt AS timestamptz) AND fd.id < CAST(:cursorId AS uuid))
          )
        ORDER BY fd.deleted_at DESC, fd.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Folder> findTrashedFoldersAdminPage(
        @Param("q") String q,
        @Param("ownerId") UUID ownerId,
        @Param("cursorDeletedAt") Instant cursorDeletedAt,
        @Param("cursorId") UUID cursorId,
        @Param("limit") int limit
    );
}
```

**중요:** owner email / originalParent name 은 **service 단에서 batch JOIN(N+1 회피, T5 패턴)** 으로 해소한다. SQL JOIN을 native에 박지 않는 이유: ① entity 매핑이 단일 root 가 아니라 result mapper 부담, ② FileItem/Folder 그대로 반환 → service에서 id 묶음으로 user/folder 일괄 조회. 이 결정은 §P2 service 작업에서 구현.

- [ ] **Step 2: 빌드 확인**

```bash
cd backend && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: commit**

```bash
git add backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashRepository.java
git commit -m "feat(wave2-t9): add AdminTrashRepository native queries"
```

---

## P2 (BE-2): Service — DTO records + AdminTrashService.list

**Files:**
- Create: `AdminTrashItemDto.java`, `AdminTrashPage.java`, `AdminTrashFilters.java`, `AdminTrashService.java`
- Create: `AdminTrashServiceTest.java`

### Task P2.1: DTO/Page/Filters records

- [ ] **Step 1: AdminTrashItemDto**

```java
// backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashItemDto.java
package com.ibizdrive.admin.trash;

import com.ibizdrive.trash.TrashItemType;

import java.time.Instant;
import java.util.UUID;

/**
 * Wave 2 T9 — admin global trash row DTO (spec §4.4).
 *
 * <p>user-facing {@code TrashItemDto}와 분리 — admin 전용 메타(owner, originalParent name,
 * sizeBytes) 노출. {@code originalParentId == null} = root.
 *
 * <p>{@code sizeBytes}는 file만 not-null. folder는 자기 자신 메타만 보여주므로 항상 null
 * (subtree size는 v1.x deferred — spec §4.4).
 */
public record AdminTrashItemDto(
    UUID id,
    String name,
    TrashItemType type,
    Instant deletedAt,
    Instant purgeAfter,
    UUID ownerId,
    String ownerEmail,
    UUID originalParentId,
    String originalParentName,
    Long sizeBytes
) {}
```

- [ ] **Step 2: AdminTrashPage**

```java
// backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashPage.java
package com.ibizdrive.admin.trash;

import java.util.List;

/**
 * Wave 2 T9 — admin trash listing 응답 envelope. {@code nextCursor=null}이면 마지막 페이지.
 */
public record AdminTrashPage(
    List<AdminTrashItemDto> items,
    String nextCursor
) {}
```

- [ ] **Step 3: AdminTrashFilters**

```java
// backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashFilters.java
package com.ibizdrive.admin.trash;

import com.ibizdrive.trash.TrashItemType;

import java.util.UUID;

/**
 * Wave 2 T9 — admin trash filters. 모든 필드 nullable (= 미적용).
 *
 * <p>{@code q}는 raw 입력 (서비스에서 정규화/escape/wrap). controller에서 길이 cap(예: 200)
 * 미준수 시 400. {@code type}/{@code ownerId}는 controller에서 파싱 실패 시 400.
 */
public record AdminTrashFilters(
    String q,
    TrashItemType type,
    UUID ownerId
) {}
```

- [ ] **Step 4: 빌드 확인 + commit**

```bash
cd backend && ./gradlew compileJava
git add backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashItemDto.java \
        backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashPage.java \
        backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashFilters.java
git commit -m "feat(wave2-t9): add AdminTrash DTO/Page/Filters records"
```

### Task P2.2: AdminTrashService — TDD

- [ ] **Step 1: 실패 테스트 작성 (empty + cursor + ownerId)**

```java
// backend/src/test/java/com/ibizdrive/admin/trash/AdminTrashServiceTest.java
package com.ibizdrive.admin.trash;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.trash.TrashItemType;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminTrashServiceTest {

    private AdminTrashRepository adminRepo;
    private UserRepository userRepo;
    private FolderRepository folderRepo;
    private AdminTrashService service;

    @BeforeEach
    void setUp() {
        adminRepo = mock(AdminTrashRepository.class);
        userRepo = mock(UserRepository.class);
        folderRepo = mock(FolderRepository.class);
        service = new AdminTrashService(adminRepo, userRepo, folderRepo);
    }

    @Test
    void list_returnsEmptyPage_whenBothSourcesEmpty() {
        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        AdminTrashPage page = service.list(new AdminTrashFilters(null, null, null), null, null);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void list_appliesQEscapeAndWildcardWrap() {
        // q = "10%" → escaped "10\\%" → wrapped "%10\\%%" → lowercase already
        when(adminRepo.findTrashedFilesAdminPage(eq("%10\\%%"), isNull(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(adminRepo.findTrashedFoldersAdminPage(eq("%10\\%%"), isNull(), any(), any(), anyInt()))
            .thenReturn(List.of());

        service.list(new AdminTrashFilters("10%", null, null), null, null);

        verify(adminRepo).findTrashedFilesAdminPage(eq("%10\\%%"), isNull(), any(), any(), anyInt());
        verify(adminRepo).findTrashedFoldersAdminPage(eq("%10\\%%"), isNull(), any(), any(), anyInt());
    }

    @Test
    void list_clampsLimitTo100() {
        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), eq(101))) // 100 + 1 over-fetch
            .thenReturn(List.of());
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), eq(101)))
            .thenReturn(List.of());

        service.list(new AdminTrashFilters(null, null, null), null, 9999);

        verify(adminRepo).findTrashedFilesAdminPage(any(), any(), any(), any(), eq(101));
    }

    @Test
    void list_rejectsQTooLong() {
        String longQ = "a".repeat(201);
        assertThatThrownBy(() ->
            service.list(new AdminTrashFilters(longQ, null, null), null, null)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    // 추가 케이스: type=FILE only / type=FOLDER only / merge sort / nextCursor encode /
    // batch user lookup / batch parent lookup — Step 4 에서 확장
}
```

```bash
cd backend && ./gradlew test --tests "com.ibizdrive.admin.trash.AdminTrashServiceTest"
```

Expected: FAIL — `AdminTrashService` 미존재.

- [ ] **Step 2: AdminTrashService 최소 구현**

```java
// backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashService.java
package com.ibizdrive.admin.trash;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.trash.TrashCursor;
import com.ibizdrive.trash.TrashItemType;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Wave 2 T9 — admin global trash listing service (spec §5.1, docs/02 §7.11).
 *
 * <p>repo가 반환한 raw entity 두 source를 in-memory merge sort + admin DTO 변환. owner/parent
 * batch lookup으로 N+1 회피 (T5 AdminPermissionService 패턴).
 *
 * <p>cursor 모델은 기존 user-facing {@link com.ibizdrive.trash.TrashQueryService}와 동일 키
 * (deletedAt DESC + id DESC tie-break) — frontend가 동일 cursor 디자인 가정.
 *
 * <p>권한 가드는 controller 단 {@code @PreAuthorize("hasRole('ADMIN')")} 단일 — service는
 * permission 후처리 없음 (admin은 시스템 전체 가시).
 */
@Service
public class AdminTrashService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_Q_LENGTH = 200;

    private final AdminTrashRepository adminRepo;
    private final UserRepository userRepository;
    private final FolderRepository folderRepository;

    public AdminTrashService(AdminTrashRepository adminRepo,
                             UserRepository userRepository,
                             FolderRepository folderRepository) {
        this.adminRepo = adminRepo;
        this.userRepository = userRepository;
        this.folderRepository = folderRepository;
    }

    @Transactional(readOnly = true)
    public AdminTrashPage list(AdminTrashFilters filters, String cursorWire, Integer limitOpt) {
        if (filters.q() != null && filters.q().length() > MAX_Q_LENGTH) {
            throw new IllegalArgumentException("q too long");
        }

        TrashCursor cursor = TrashCursor.decode(cursorWire);
        Instant cursorAt = cursor != null ? cursor.deletedAt() : null;
        UUID cursorId = cursor != null ? cursor.id() : null;
        int limit = clampLimit(limitOpt);
        int fetchSize = limit + 1;

        String qPattern = normalizeQ(filters.q());
        UUID ownerId = filters.ownerId();
        TrashItemType type = filters.type();

        List<FileItem> files = (type == null || type == TrashItemType.FILE)
            ? adminRepo.findTrashedFilesAdminPage(qPattern, ownerId, cursorAt, cursorId, fetchSize)
            : List.of();
        List<Folder> folders = (type == null || type == TrashItemType.FOLDER)
            ? adminRepo.findTrashedFoldersAdminPage(qPattern, ownerId, cursorAt, cursorId, fetchSize)
            : List.of();

        // batch lookup (N+1 회피)
        Set<UUID> userIds = new HashSet<>();
        Set<UUID> parentIds = new HashSet<>();
        for (FileItem f : files) { userIds.add(f.getOwnerId()); if (f.getOriginalFolderId() != null) parentIds.add(f.getOriginalFolderId()); }
        for (Folder fd : folders) { userIds.add(fd.getOwnerId()); if (fd.getOriginalParentId() != null) parentIds.add(fd.getOriginalParentId()); }

        Map<UUID, String> userEmailById = new HashMap<>();
        for (User u : userRepository.findAllById(userIds)) {
            userEmailById.put(u.getId(), u.getEmail());
        }
        Map<UUID, String> parentNameById = new HashMap<>();
        for (Folder p : folderRepository.findAllById(parentIds)) {
            parentNameById.put(p.getId(), p.getName());
        }

        List<AdminTrashItemDto> merged = new ArrayList<>(files.size() + folders.size());
        for (FileItem f : files) {
            merged.add(new AdminTrashItemDto(
                f.getId(), f.getName(), TrashItemType.FILE,
                f.getDeletedAt(), f.getPurgeAfter(),
                f.getOwnerId(), userEmailById.get(f.getOwnerId()),
                f.getOriginalFolderId(),
                f.getOriginalFolderId() != null ? parentNameById.get(f.getOriginalFolderId()) : null,
                f.getSizeBytes()
            ));
        }
        for (Folder fd : folders) {
            merged.add(new AdminTrashItemDto(
                fd.getId(), fd.getName(), TrashItemType.FOLDER,
                fd.getDeletedAt(), fd.getPurgeAfter(),
                fd.getOwnerId(), userEmailById.get(fd.getOwnerId()),
                fd.getOriginalParentId(),
                fd.getOriginalParentId() != null ? parentNameById.get(fd.getOriginalParentId()) : null,
                null
            ));
        }

        merged.sort(
            Comparator.comparing(AdminTrashItemDto::deletedAt, Comparator.reverseOrder())
                .thenComparing(AdminTrashItemDto::id, Comparator.reverseOrder())
        );

        boolean hasMore = merged.size() > limit;
        List<AdminTrashItemDto> page = hasMore ? merged.subList(0, limit) : merged;
        String nextCursor = null;
        if (hasMore) {
            AdminTrashItemDto last = page.get(page.size() - 1);
            nextCursor = TrashCursor.encode(last.deletedAt(), last.id());
        }
        return new AdminTrashPage(List.copyOf(page), nextCursor);
    }

    /** trim → lowercase → LIKE escape (\, %, _) → wildcard wrap. blank → null. */
    private static String normalizeQ(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        String escaped = trimmed.toLowerCase()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static int clampLimit(Integer requested) {
        if (requested == null || requested <= 0) return DEFAULT_LIMIT;
        return Math.min(requested, MAX_LIMIT);
    }
}
```

- [ ] **Step 3: 테스트 실행**

```bash
cd backend && ./gradlew test --tests "com.ibizdrive.admin.trash.AdminTrashServiceTest"
```

Expected: 4 tests passing (empty / qEscape / clamp / qTooLong).

- [ ] **Step 4: 추가 케이스 — merge sort + cursor + batch lookup**

`AdminTrashServiceTest`에 아래 메서드 추가:

```java
@Test
void list_mergesFilesAndFoldersByDeletedAtDesc() {
    UUID ownerA = UUID.randomUUID();
    User user = makeUser(ownerA, "a@x");
    when(userRepo.findAllById(any())).thenReturn(List.of(user));
    when(folderRepo.findAllById(any())).thenReturn(List.of());

    Instant t1 = Instant.parse("2026-05-07T10:00:00Z");
    Instant t2 = Instant.parse("2026-05-07T09:00:00Z");
    FileItem file = makeFile(UUID.randomUUID(), "a.pdf", ownerA, t1, null, 100L);
    Folder folder = makeFolder(UUID.randomUUID(), "F", ownerA, t2, null);

    when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of(file));
    when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of(folder));

    AdminTrashPage page = service.list(new AdminTrashFilters(null, null, null), null, null);

    assertThat(page.items()).hasSize(2);
    assertThat(page.items().get(0).type()).isEqualTo(TrashItemType.FILE); // t1 first
    assertThat(page.items().get(1).type()).isEqualTo(TrashItemType.FOLDER);
    assertThat(page.nextCursor()).isNull();
}

@Test
void list_encodesNextCursor_whenHasMore() {
    UUID ownerA = UUID.randomUUID();
    when(userRepo.findAllById(any())).thenReturn(List.of(makeUser(ownerA, "a@x")));
    when(folderRepo.findAllById(any())).thenReturn(List.of());

    // limit=1 with 2 results → hasMore=true
    Instant t1 = Instant.parse("2026-05-07T10:00:00Z");
    Instant t2 = Instant.parse("2026-05-07T09:00:00Z");
    FileItem f1 = makeFile(UUID.randomUUID(), "a", ownerA, t1, null, 1L);
    FileItem f2 = makeFile(UUID.randomUUID(), "b", ownerA, t2, null, 2L);
    when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of(f1, f2));
    when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of());

    AdminTrashPage page = service.list(new AdminTrashFilters(null, null, null), null, 1);

    assertThat(page.items()).hasSize(1);
    assertThat(page.nextCursor()).isNotNull();
}

@Test
void list_skipsFiles_whenTypeIsFolder() {
    when(userRepo.findAllById(any())).thenReturn(List.of());
    when(folderRepo.findAllById(any())).thenReturn(List.of());
    when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of());

    service.list(new AdminTrashFilters(null, TrashItemType.FOLDER, null), null, null);

    verify(adminRepo, never()).findTrashedFilesAdminPage(any(), any(), any(), any(), anyInt());
    verify(adminRepo).findTrashedFoldersAdminPage(any(), any(), any(), any(), anyInt());
}

@Test
void list_attachesOwnerEmailAndParentName() {
    UUID ownerA = UUID.randomUUID();
    UUID parentId = UUID.randomUUID();
    User u = makeUser(ownerA, "alice@x");
    Folder parent = makeFolder(parentId, "ParentFolder", ownerA, null, null);
    when(userRepo.findAllById(any())).thenReturn(List.of(u));
    when(folderRepo.findAllById(any())).thenReturn(List.of(parent));

    FileItem f = makeFile(UUID.randomUUID(), "x.pdf", ownerA, Instant.now(), parentId, 99L);
    when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of(f));
    when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of());

    AdminTrashPage page = service.list(new AdminTrashFilters(null, null, null), null, null);

    assertThat(page.items()).hasSize(1);
    AdminTrashItemDto item = page.items().get(0);
    assertThat(item.ownerEmail()).isEqualTo("alice@x");
    assertThat(item.originalParentName()).isEqualTo("ParentFolder");
    assertThat(item.sizeBytes()).isEqualTo(99L);
}

// helpers
private static User makeUser(UUID id, String email) {
    User u = new User();
    u.setId(id);
    u.setEmail(email);
    return u;
}

private static FileItem makeFile(UUID id, String name, UUID ownerId, Instant deletedAt, UUID parentId, Long size) {
    FileItem f = new FileItem();
    f.setId(id);
    f.setName(name);
    f.setOwnerId(ownerId);
    f.setDeletedAt(deletedAt);
    f.setOriginalFolderId(parentId);
    f.setSizeBytes(size != null ? size : 0L);
    return f;
}

private static Folder makeFolder(UUID id, String name, UUID ownerId, Instant deletedAt, UUID parentId) {
    Folder fd = new Folder();
    fd.setId(id);
    fd.setName(name);
    fd.setOwnerId(ownerId);
    fd.setDeletedAt(deletedAt);
    fd.setOriginalParentId(parentId);
    return fd;
}
```

> **주의:** `User`, `FileItem`, `Folder` 의 setter/생성자 방식은 기존 entity 코드를 확인 후 helper 본문을 보정한다. 만약 entity가 setter 미공개라면 builder 또는 reflection을 쓰지 말고 protected/public 생성자를 추가하지 말 것 — 대신 `@DataJpaTest` + 실제 persist로 전환 (별도 task).

```bash
cd backend && ./gradlew test --tests "com.ibizdrive.admin.trash.AdminTrashServiceTest"
```

Expected: 8 tests passing.

- [ ] **Step 5: commit**

```bash
git add backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashService.java \
        backend/src/test/java/com/ibizdrive/admin/trash/AdminTrashServiceTest.java
git commit -m "feat(wave2-t9): add AdminTrashService.list with merge sort + batch lookup"
```

---

## P3 (BE-3): Controller — `GET /api/admin/trash` + WebMvcTest

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashController.java`
- Create: `backend/src/test/java/com/ibizdrive/admin/trash/AdminTrashControllerTest.java`
- Reference: `AdminPermissionController.java` (admin namespace 패턴), `AdminPermissionControllerTest.java` (WebMvcTest 패턴)

### Task P3.1: Controller TDD

- [ ] **Step 1: 실패 테스트 작성**

```java
// backend/src/test/java/com/ibizdrive/admin/trash/AdminTrashControllerTest.java
package com.ibizdrive.admin.trash;

import com.ibizdrive.trash.TrashItemType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminTrashController.class)
class AdminTrashControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AdminTrashService service;

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_200_forAdmin() throws Exception {
        when(service.list(any(), any(), any())).thenReturn(new AdminTrashPage(List.of(), null));

        mockMvc.perform(get("/api/admin/trash").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void list_401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/trash"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void list_403_forMember() throws Exception {
        mockMvc.perform(get("/api/admin/trash"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void list_403_forAuditor() throws Exception {
        mockMvc.perform(get("/api/admin/trash"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_passesFiltersAndCursorThrough() throws Exception {
        UUID ownerId = UUID.randomUUID();
        when(service.list(any(), any(), any())).thenReturn(new AdminTrashPage(List.of(), null));

        mockMvc.perform(get("/api/admin/trash")
                .param("q", "report")
                .param("type", "file")
                .param("ownerId", ownerId.toString())
                .param("cursor", "abc")
                .param("limit", "20"))
            .andExpect(status().isOk());

        verify(service).list(
            argThat(f -> "report".equals(f.q())
                && f.type() == TrashItemType.FILE
                && ownerId.equals(f.ownerId())),
            eq("abc"),
            eq(20)
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_400_whenInvalidType() throws Exception {
        mockMvc.perform(get("/api/admin/trash").param("type", "bogus"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_400_whenInvalidOwnerId() throws Exception {
        mockMvc.perform(get("/api/admin/trash").param("ownerId", "not-a-uuid"))
            .andExpect(status().isBadRequest());
    }
}
```

```bash
cd backend && ./gradlew test --tests "com.ibizdrive.admin.trash.AdminTrashControllerTest"
```

Expected: FAIL — `AdminTrashController` 미존재.

- [ ] **Step 2: Controller 구현**

```java
// backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashController.java
package com.ibizdrive.admin.trash;

import com.ibizdrive.trash.TrashItemType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Wave 2 T9 — admin global trash listing controller (spec §5.1, docs/02 §7.11).
 *
 * <p>{@code GET /api/admin/trash} 단일 endpoint. mutation은 본 트랙에서 신규 endpoint 0:
 * 기존 {@code POST /api/files|folders/{id}/restore} + {@code DELETE /api/trash/{type}/{id}}
 * 재사용 (ADMIN ROLE이 SpEL 가드 통과 — spec §3 표).
 *
 * <p>{@code IllegalArgumentException}은 글로벌 핸들러에서 400으로 매핑된다고 가정 — 본 트랙
 * 신규 핸들러 추가 없음 (T1/T4 패턴).
 */
@RestController
@RequestMapping("/api/admin/trash")
public class AdminTrashController {

    private final AdminTrashService service;

    public AdminTrashController(AdminTrashService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminTrashPage> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String ownerId,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        TrashItemType parsedType = type != null ? TrashItemType.from(type) : null;
        UUID parsedOwner = ownerId != null ? UUID.fromString(ownerId) : null;

        AdminTrashFilters filters = new AdminTrashFilters(q, parsedType, parsedOwner);
        return ResponseEntity.ok(service.list(filters, cursor, limit));
    }
}
```

`UUID.fromString` 실패 시 `IllegalArgumentException` → 글로벌 핸들러 400. `TrashItemType.from` 동상.

- [ ] **Step 3: 테스트 실행**

```bash
cd backend && ./gradlew test --tests "com.ibizdrive.admin.trash.AdminTrashControllerTest"
```

Expected: 7 tests passing.

> **글로벌 400 매핑 점검:** 만약 `IllegalArgumentException` 핸들러가 미등록이라면(grep `IllegalArgumentException` in `*ExceptionHandler*.java`) — `AdminPermissionController`에서 동일 에러 매핑이 동작하므로 같은 핸들러 재사용. 별도 추가 작업 없음.

- [ ] **Step 4: 전체 backend 테스트 회귀 점검**

```bash
cd backend && ./gradlew test
```

Expected: BUILD SUCCESSFUL (전체 GREEN, 신규 케이스 ~15 추가).

- [ ] **Step 5: commit**

```bash
git add backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashController.java \
        backend/src/test/java/com/ibizdrive/admin/trash/AdminTrashControllerTest.java
git commit -m "feat(wave2-t9): add GET /api/admin/trash + WebMvcTest"
```

---

## P4 (FE-1): types + api.ts + queryKeys.ts

**Files:**
- Modify: `frontend/src/types/trash.ts`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/lib/queryKeys.ts`
- Create: `frontend/src/lib/api.adminTrash.test.ts`

### Task P4.1: types 확장

- [ ] **Step 1: AdminTrashItem / AdminTrashFilters / AdminTrashPage 추가**

`frontend/src/types/trash.ts` 파일 끝에 append (기존 `TrashItem` 등은 유지):

```ts
// Wave 2 T9 — admin global trash (spec §4.4)
export interface AdminTrashItem {
  id: string
  name: string
  type: 'file' | 'folder'
  deletedAt: string  // ISO-8601
  purgeAfter: string
  ownerId: string
  ownerEmail: string
  originalParentId: string | null
  originalParentName: string | null
  sizeBytes: number | null
}

export interface AdminTrashFilters {
  q: string
  type: 'file' | 'folder' | null
  ownerId: string | null
}

export interface AdminTrashPage {
  items: AdminTrashItem[]
  nextCursor: string | null
}
```

- [ ] **Step 2: typecheck**

```bash
cd frontend && npm run typecheck
```

Expected: 0 errors.

### Task P4.2: api.adminListTrash + 테스트

- [ ] **Step 1: 실패 테스트 작성**

```ts
// frontend/src/lib/api.adminTrash.test.ts
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { adminListTrash } from './api'
import type { AdminTrashFilters } from '@/types/trash'

describe('adminListTrash', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    globalThis.fetch = fetchMock as never
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  function mockOk(body: unknown) {
    fetchMock.mockResolvedValue({
      ok: true, status: 200, json: async () => body, headers: new Headers(),
    } as Response)
  }

  function mockErr(status: number, code: string) {
    fetchMock.mockResolvedValue({
      ok: false, status, json: async () => ({ code, message: code }),
      headers: new Headers(),
    } as Response)
  }

  it('omits blank filters from query string', async () => {
    mockOk({ items: [], nextCursor: null })
    const filters: AdminTrashFilters = { q: '', type: null, ownerId: null }

    await adminListTrash(filters, null)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const url = fetchMock.mock.calls[0][0] as string
    expect(url).toContain('/api/admin/trash')
    expect(url).not.toContain('q=')
    expect(url).not.toContain('type=')
    expect(url).not.toContain('ownerId=')
    expect(url).not.toContain('cursor=')
  })

  it('encodes q + type + ownerId + cursor', async () => {
    mockOk({ items: [], nextCursor: null })
    const filters: AdminTrashFilters = { q: 'foo bar', type: 'file', ownerId: 'u-1' }

    await adminListTrash(filters, 'CURSOR123')

    const url = fetchMock.mock.calls[0][0] as string
    expect(url).toContain('q=foo+bar')
    expect(url).toContain('type=file')
    expect(url).toContain('ownerId=u-1')
    expect(url).toContain('cursor=CURSOR123')
  })

  it('throws envelope on 401', async () => {
    mockErr(401, 'UNAUTHORIZED')
    await expect(adminListTrash({ q: '', type: null, ownerId: null }, null))
      .rejects.toMatchObject({ status: 401, code: 'UNAUTHORIZED' })
  })

  it('throws envelope on 403', async () => {
    mockErr(403, 'FORBIDDEN')
    await expect(adminListTrash({ q: '', type: null, ownerId: null }, null))
      .rejects.toMatchObject({ status: 403, code: 'FORBIDDEN' })
  })

  it('throws envelope on 400 VALIDATION_ERROR', async () => {
    mockErr(400, 'VALIDATION_ERROR')
    await expect(adminListTrash({ q: 'x', type: null, ownerId: null }, null))
      .rejects.toMatchObject({ status: 400, code: 'VALIDATION_ERROR' })
  })
})
```

```bash
cd frontend && npm run test -- src/lib/api.adminTrash.test.ts
```

Expected: FAIL — `adminListTrash` export 미존재.

- [ ] **Step 2: api.ts에 adminListTrash 추가**

`frontend/src/lib/api.ts` 의 기존 admin 어댑터(`adminGetStorageOverview` 등) 근처에 추가. **`buildApiError`** 가 이미 있으므로 그 헬퍼 재사용.

```ts
import type { AdminTrashFilters, AdminTrashPage } from '@/types/trash'

/**
 * Wave 2 T9 — admin global trash listing.
 *
 * <p>빈 filter 값은 query string에서 제거. 401/403/400 envelope을 `buildApiError` 로 throw.
 * cursor 는 backend opaque base64 — 그대로 echo back.
 */
export async function adminListTrash(
  filters: AdminTrashFilters,
  cursor: string | null,
): Promise<AdminTrashPage> {
  const params = new URLSearchParams()
  if (filters.q && filters.q.trim()) params.set('q', filters.q.trim())
  if (filters.type) params.set('type', filters.type)
  if (filters.ownerId) params.set('ownerId', filters.ownerId)
  if (cursor) params.set('cursor', cursor)

  const qs = params.toString()
  const url = `/api/admin/trash${qs ? `?${qs}` : ''}`

  const res = await fetch(url, { method: 'GET', credentials: 'include' })
  if (!res.ok) throw await buildApiError(res)
  return (await res.json()) as AdminTrashPage
}
```

- [ ] **Step 3: 테스트 GREEN 확인**

```bash
cd frontend && npm run test -- src/lib/api.adminTrash.test.ts
```

Expected: 5 tests passing.

### Task P4.3: queryKeys + invalidations

- [ ] **Step 1: qk.adminTrash 추가**

`frontend/src/lib/queryKeys.ts` 에 추가:

```ts
import type { AdminTrashFilters } from '@/types/trash'

// 기존 qk 객체 안에 추가
export const qk = {
  // ... 기존 키들
  adminTrash: ['adminTrash'] as const,
  adminTrashList: (filters: AdminTrashFilters, cursor: string | null) =>
    ['adminTrash', 'list', filters.q ?? '', filters.type ?? null, filters.ownerId ?? null, cursor ?? null] as const,
} as const

export const invalidations = {
  // ... 기존
  afterAdminTrashChanged: (qc: QueryClient) => {
    qc.invalidateQueries({ queryKey: qk.adminTrash })
  },
}
```

> **주의:** 기존 `qk` 객체의 정확한 형태(읽기 전용 vs spread)는 파일 첫줄을 확인해 동일 패턴으로 추가. `QueryClient` import도 기존 `invalidations` 헬퍼 import를 그대로 따라간다.

- [ ] **Step 2: typecheck**

```bash
cd frontend && npm run typecheck
```

Expected: 0 errors.

- [ ] **Step 3: commit P4 전체**

```bash
git add frontend/src/types/trash.ts \
        frontend/src/lib/api.ts \
        frontend/src/lib/api.adminTrash.test.ts \
        frontend/src/lib/queryKeys.ts
git commit -m "feat(wave2-t9): add adminListTrash api + types + query keys"
```

---

## P5 (FE-2): hooks — useAdminTrashList / Restore / Purge

**Files:**
- Create: `frontend/src/hooks/useAdminTrash.ts`
- Create: `frontend/src/hooks/useAdminTrash.test.tsx`

### Task P5.1: 실패 테스트 작성

- [ ] **Step 1: 테스트 골격**

```tsx
// frontend/src/hooks/useAdminTrash.test.tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAdminTrashList, useAdminRestoreTrashItem, useAdminPurgeTrashItem } from './useAdminTrash'
import * as api from '@/lib/api'
import { qk } from '@/lib/queryKeys'

function wrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  return { qc, Wrapper }
}

describe('useAdminTrashList', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('fetches trash list with filters + cursor', async () => {
    const spy = vi.spyOn(api, 'adminListTrash').mockResolvedValue({ items: [], nextCursor: null })
    const { Wrapper } = wrapper()

    const { result } = renderHook(
      () => useAdminTrashList({ q: 'foo', type: 'file', ownerId: null }, null),
      { wrapper: Wrapper },
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(spy).toHaveBeenCalledWith({ q: 'foo', type: 'file', ownerId: null }, null)
  })
})

describe('useAdminRestoreTrashItem', () => {
  it('invalidates adminTrash queries on file restore success', async () => {
    vi.spyOn(api, 'restoreFile').mockResolvedValue(undefined as never)
    const { qc, Wrapper } = wrapper()
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')

    const { result } = renderHook(() => useAdminRestoreTrashItem(qc), { wrapper: Wrapper })

    await act(async () => {
      await result.current.mutateAsync({ id: 'f-1', type: 'file' })
    })

    expect(invalidateSpy).toHaveBeenCalledWith(expect.objectContaining({ queryKey: qk.adminTrash }))
  })
})

describe('useAdminPurgeTrashItem', () => {
  it('invalidates adminTrash on purge success', async () => {
    vi.spyOn(api, 'purgeTrashItem').mockResolvedValue(undefined as never)
    const { qc, Wrapper } = wrapper()
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')

    const { result } = renderHook(() => useAdminPurgeTrashItem(qc), { wrapper: Wrapper })

    await act(async () => {
      await result.current.mutateAsync({ id: 'fd-1', type: 'folder' })
    })

    expect(invalidateSpy).toHaveBeenCalledWith(expect.objectContaining({ queryKey: qk.adminTrash }))
  })
})
```

```bash
cd frontend && npm run test -- src/hooks/useAdminTrash.test.tsx
```

Expected: FAIL — hooks 미존재.

### Task P5.2: 구현

- [ ] **Step 1: useAdminTrash.ts 작성**

```ts
// frontend/src/hooks/useAdminTrash.ts
'use client'
import { useQuery, useMutation, useQueryClient, keepPreviousData, type QueryClient } from '@tanstack/react-query'
import { adminListTrash, restoreFile, restoreFolder, purgeTrashItem } from '@/lib/api'
import type { AdminTrashFilters, AdminTrashPage } from '@/types/trash'
import { qk, invalidations } from '@/lib/queryKeys'

/**
 * Wave 2 T9 — admin trash listing (spec §5.2).
 *
 * <p>cursor 상태는 caller(page)가 보유. 본 훅은 인자 그대로 fetch.
 * `placeholderData: keepPreviousData`로 페이지 전환 깜빡임 완화.
 */
export function useAdminTrashList(filters: AdminTrashFilters, cursor: string | null) {
  return useQuery<AdminTrashPage>({
    queryKey: qk.adminTrashList(filters, cursor),
    queryFn: () => adminListTrash(filters, cursor),
    placeholderData: keepPreviousData,
  })
}

export interface AdminTrashTarget {
  id: string
  type: 'file' | 'folder'
}

/**
 * 단건 복원 — type에 따라 기존 file/folder restore endpoint 재사용 (spec §4.2).
 * 성공 시 admin trash list invalidate.
 */
export function useAdminRestoreTrashItem(qcInjected?: QueryClient) {
  const qc = qcInjected ?? useQueryClient()
  return useMutation({
    mutationFn: async ({ id, type }: AdminTrashTarget) => {
      if (type === 'file') return restoreFile(id)
      return restoreFolder(id)
    },
    onSuccess: () => invalidations.afterAdminTrashChanged(qc),
  })
}

/**
 * 단건 영구삭제 — 기존 {@code DELETE /api/trash/{type}/{id}} 재사용.
 */
export function useAdminPurgeTrashItem(qcInjected?: QueryClient) {
  const qc = qcInjected ?? useQueryClient()
  return useMutation({
    mutationFn: ({ id, type }: AdminTrashTarget) => purgeTrashItem(type, id),
    onSuccess: () => invalidations.afterAdminTrashChanged(qc),
  })
}
```

> **주의 — 기존 api 헬퍼:** `restoreFile(id)` / `restoreFolder(id)` / `purgeTrashItem(type, id)` 가 이미 `lib/api.ts` 에 존재해야 한다(M9 frontend trash). grep으로 사전 확인:
>
> ```bash
> grep -n "restoreFile\|restoreFolder\|purgeTrashItem" frontend/src/lib/api.ts
> ```
>
> 만약 어느 하나라도 미존재라면, **TODO [BLOCKED]** 로 중단하고 별도 트랙(M9 보강)으로 처리. 본 트랙 spec §4.2의 "mutation 재사용" 전제가 깨진 것이므로 임시 wrapper를 만들지 말 것 (CLAUDE.md §3 원칙 8).

- [ ] **Step 2: 테스트 실행**

```bash
cd frontend && npm run test -- src/hooks/useAdminTrash.test.tsx
```

Expected: 3 tests passing.

- [ ] **Step 3: commit**

```bash
git add frontend/src/hooks/useAdminTrash.ts frontend/src/hooks/useAdminTrash.test.tsx
git commit -m "feat(wave2-t9): add useAdminTrash hooks (list/restore/purge)"
```

---

## P6 (FE-3): page + AdminSideNav 토글 + landing 카드

**Files:**
- Create: `frontend/src/app/admin/trash/all/page.tsx`
- Create: `frontend/src/app/admin/trash/all/page.test.tsx`
- Modify: `frontend/src/components/admin/AdminSideNav.tsx`
- Modify: `frontend/src/app/admin/page.tsx`

### Task P6.1: page 골격 + 실패 테스트

- [ ] **Step 1: page test 작성 (failing)**

```tsx
// frontend/src/app/admin/trash/all/page.test.tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminTrashAllPage from './page'
import * as api from '@/lib/api'
import type { AdminTrashItem } from '@/types/trash'

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <AdminTrashAllPage />
    </QueryClientProvider>,
  )
}

const sample: AdminTrashItem = {
  id: 'f-1', name: 'spec.pdf', type: 'file',
  deletedAt: '2026-05-07T10:00:00Z', purgeAfter: '2026-06-06T10:00:00Z',
  ownerId: 'u-1', ownerEmail: 'alice@x',
  originalParentId: 'fd-1', originalParentName: 'Reports', sizeBytes: 12345,
}

describe('/admin/trash/all', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('renders rows with 8 column values', async () => {
    vi.spyOn(api, 'adminListTrash').mockResolvedValue({ items: [sample], nextCursor: null })
    renderPage()

    expect(await screen.findByText('spec.pdf')).toBeInTheDocument()
    expect(screen.getByText('alice@x')).toBeInTheDocument()
    expect(screen.getByText('Reports')).toBeInTheDocument()
  })

  it('renders empty state when no items', async () => {
    vi.spyOn(api, 'adminListTrash').mockResolvedValue({ items: [], nextCursor: null })
    renderPage()

    expect(await screen.findByText(/휴지통이 비어 있습니다/)).toBeInTheDocument()
  })

  it('opens ConfirmDialog on purge click', async () => {
    vi.spyOn(api, 'adminListTrash').mockResolvedValue({ items: [sample], nextCursor: null })
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '영구 삭제' }))

    expect(await screen.findByText(/되돌릴 수 없습니다/)).toBeInTheDocument()
  })

  it('immediately calls restore api on restore click (no confirm)', async () => {
    vi.spyOn(api, 'adminListTrash').mockResolvedValue({ items: [sample], nextCursor: null })
    const restoreSpy = vi.spyOn(api, 'restoreFile').mockResolvedValue(undefined as never)
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '복원' }))

    await waitFor(() => expect(restoreSpy).toHaveBeenCalledWith('f-1'))
  })
})
```

```bash
cd frontend && npm run test -- src/app/admin/trash/all/page.test.tsx
```

Expected: FAIL — page 미존재.

- [ ] **Step 2: page 구현**

```tsx
// frontend/src/app/admin/trash/all/page.tsx
'use client'
import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import {
  useAdminTrashList,
  useAdminRestoreTrashItem,
  useAdminPurgeTrashItem,
  type AdminTrashTarget,
} from '@/hooks/useAdminTrash'
import type { AdminTrashFilters, AdminTrashItem } from '@/types/trash'

/**
 * Wave 2 T9 — `/admin/trash/all` (spec §5.2).
 *
 * <p>FilterBar(q debounce 300 / type / ownerId) + Table(8 cols) + cursor 페이지네이션 +
 * 단건 [복원]/[영구삭제] (purge만 ConfirmDialog).
 */
export default function AdminTrashAllPage() {
  const qc = useQueryClient()
  const [filters, setFilters] = useState<AdminTrashFilters>({ q: '', type: null, ownerId: null })
  const [cursor, setCursor] = useState<string | null>(null)
  const [confirmTarget, setConfirmTarget] = useState<AdminTrashTarget | null>(null)

  const list = useAdminTrashList(filters, cursor)
  const restore = useAdminRestoreTrashItem(qc)
  const purge = useAdminPurgeTrashItem(qc)

  function updateFilter<K extends keyof AdminTrashFilters>(key: K, value: AdminTrashFilters[K]) {
    setFilters((prev) => ({ ...prev, [key]: value }))
    setCursor(null)
  }

  function onRestore(item: AdminTrashItem) {
    restore.mutate({ id: item.id, type: item.type })
  }

  function onConfirmPurge() {
    if (!confirmTarget) return
    purge.mutate(confirmTarget)
    setConfirmTarget(null)
  }

  return (
    <main className="flex flex-col gap-3 p-4">
      <h1 className="text-[16px] font-semibold">전역 휴지통</h1>

      <div className="flex gap-2 items-center text-[13px]">
        <input
          aria-label="이름 검색"
          placeholder="이름 검색"
          value={filters.q}
          onChange={(e) => updateFilter('q', e.target.value)}
          className="px-2 py-1 border border-border rounded"
        />
        <select
          aria-label="타입"
          value={filters.type ?? ''}
          onChange={(e) => updateFilter('type', (e.target.value || null) as AdminTrashFilters['type'])}
          className="px-2 py-1 border border-border rounded"
        >
          <option value="">전체</option>
          <option value="file">파일</option>
          <option value="folder">폴더</option>
        </select>
        <input
          aria-label="소유자 ID"
          placeholder="소유자 UUID"
          value={filters.ownerId ?? ''}
          onChange={(e) => updateFilter('ownerId', e.target.value || null)}
          className="px-2 py-1 border border-border rounded w-[280px]"
        />
      </div>

      {list.isLoading && <div>로딩중...</div>}
      {list.isError && <div className="text-red-600">오류: {(list.error as Error).message}</div>}

      {list.data && list.data.items.length === 0 && (
        <div className="text-fg-muted py-8 text-center">휴지통이 비어 있습니다</div>
      )}

      {list.data && list.data.items.length > 0 && (
        <table className="w-full text-[13px]">
          <thead>
            <tr className="text-left text-fg-muted">
              <th>이름</th><th>타입</th><th>소유자</th><th>원위치</th>
              <th>크기</th><th>삭제일</th><th>영구삭제 예정</th><th>작업</th>
            </tr>
          </thead>
          <tbody>
            {list.data.items.map((it) => (
              <tr key={`${it.type}:${it.id}`} className="border-t border-border">
                <td>{it.name}</td>
                <td>{it.type === 'file' ? '파일' : '폴더'}</td>
                <td>{it.ownerEmail}</td>
                <td>{it.originalParentName ?? '(루트)'}</td>
                <td>{it.sizeBytes != null ? `${it.sizeBytes} B` : '-'}</td>
                <td>{it.deletedAt}</td>
                <td>{it.purgeAfter}</td>
                <td className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => onRestore(it)}
                    className="px-2 py-0.5 border border-border rounded"
                  >
                    복원
                  </button>
                  <button
                    type="button"
                    onClick={() => setConfirmTarget({ id: it.id, type: it.type })}
                    className="px-2 py-0.5 border border-border rounded text-red-600"
                  >
                    영구 삭제
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {list.data?.nextCursor && (
        <div>
          <button
            type="button"
            onClick={() => setCursor(list.data!.nextCursor)}
            className="px-3 py-1 border border-border rounded"
          >
            다음 페이지
          </button>
        </div>
      )}

      {confirmTarget && (
        <div role="dialog" aria-modal="true" className="fixed inset-0 flex items-center justify-center bg-black/40">
          <div className="bg-surface-1 border border-border rounded p-4 flex flex-col gap-3 w-[360px]">
            <h2 className="text-[14px] font-semibold">영구 삭제하시겠습니까?</h2>
            <p className="text-[12px] text-fg-muted">이 동작은 되돌릴 수 없습니다.</p>
            <div className="flex gap-2 justify-end">
              <button type="button" onClick={() => setConfirmTarget(null)} className="px-3 py-1 border border-border rounded">
                취소
              </button>
              <button type="button" onClick={onConfirmPurge} className="px-3 py-1 bg-red-600 text-white rounded">
                영구 삭제
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  )
}
```

> **q debounce 300ms:** 본 step에서는 단순 controlled input. 별도 useDebouncedValue 훅 도입은 spec 변경 없이 가능하지만 spec §4.5는 "변경 시 cursor=null 리셋"만 강제. debounce 없이도 입력마다 fetch + keepPreviousData 로 UX 충분 — debounce는 v1.x 보강(추후 트랙) 으로 미루고, **본 트랙은 입력 즉시 cursor 리셋 + query**.

- [ ] **Step 3: 테스트 GREEN**

```bash
cd frontend && npm run test -- src/app/admin/trash/all/page.test.tsx
```

Expected: 4 tests passing.

### Task P6.2: AdminSideNav 토글

- [ ] **Step 1: '휴지통' DEFERRED → ACTIVE**

`frontend/src/components/admin/AdminSideNav.tsx`:

```diff
 const ACTIVE_ITEMS = [
   { label: '감사 로그', href: '/admin/audit/logs', match: 'exact' as const },
   { label: '사용자 초대', href: '/admin/users', match: 'prefix' as const },
   { label: '부서', href: '/admin/departments', match: 'prefix' as const },
   { label: '권한', href: '/admin/permissions', match: 'prefix' as const },
   { label: '시스템', href: '/admin/system', match: 'prefix' as const },
   { label: '스토리지', href: '/admin/storage', match: 'prefix' as const },
+  { label: '휴지통', href: '/admin/trash/all', match: 'prefix' as const },
 ]

 const DEFERRED_ITEMS = [
   '대시보드',
-  '휴지통',
   'Legal Hold',
   '정책',
 ]
```

- [ ] **Step 2: doc-comment의 "활성 항목(누적)" 섹션 갱신**

같은 파일 상단 JSDoc:

```diff
- * <p>활성 항목(누적): `/admin/audit/logs` (m-audit), `/admin/users`
- * (m-admin-entry-rewrite + admin-user-mgmt + admin-user-search-update Wave 1 T1),
- * `/admin/departments` (admin-department-crud Wave 2 T4), `/admin/system`
- * (Wave 1 T3 read-only cron 노출). 나머지 §2 트리 노드는 v1.x deferred —
+ * <p>활성 항목(누적): `/admin/audit/logs` (m-audit), `/admin/users`
+ * (m-admin-entry-rewrite + admin-user-mgmt + admin-user-search-update Wave 1 T1),
+ * `/admin/departments` (admin-department-crud Wave 2 T4), `/admin/permissions`
+ * (admin-permission-matrix Wave 2 T5), `/admin/system` (Wave 1 T3 read-only cron),
+ * `/admin/storage` (admin-storage-overview Wave 2 T8), `/admin/trash/all`
+ * (admin-global-trash Wave 2 T9). 나머지 §2 트리 노드는 v1.x deferred —
```

### Task P6.3: landing 카드 추가

- [ ] **Step 1: `app/admin/page.tsx` 의 카드 리스트에 항목 추가**

먼저 현재 파일을 읽어 카드 패턴을 확인:

```bash
grep -n "전역\|deferred\|v1.x\|admin/storage" frontend/src/app/admin/page.tsx
```

기존 카드 패턴(예: `'/admin/storage'`)와 동일한 형태로 `'전역 휴지통'` → `/admin/trash/all` 카드를 추가하고, 만약 deferred 리스트에 '휴지통' 라벨이 있다면 제거.

> **주의:** 카드 컴포넌트 시그니처 / props 는 기존 storage 카드를 그대로 복제. 임의 신규 prop 도입 금지(YAGNI).

- [ ] **Step 2: typecheck + 전체 frontend 테스트**

```bash
cd frontend && npm run typecheck && npm run lint && npm run test --run
```

Expected: 0 typecheck errors, lint clean, 모든 테스트 GREEN.

- [ ] **Step 3: build 점검**

```bash
cd frontend && npm run build
```

Expected: exit 0. `/admin/trash/all` 정적/SSR 빌드에 누락 없음.

- [ ] **Step 4: commit P6 전체**

```bash
git add frontend/src/app/admin/trash/all/page.tsx \
        frontend/src/app/admin/trash/all/page.test.tsx \
        frontend/src/components/admin/AdminSideNav.tsx \
        frontend/src/app/admin/page.tsx
git commit -m "feat(wave2-t9): add /admin/trash/all page + nav toggle + landing card"
```

---

## P7 (Docs): docs/02 §7.11 + docs/04 §2 §8.3 + BETA-RELEASE §7 + progress.md

**Files:**
- Modify: `docs/02-backend-data-model.md` §7.11
- Modify: `docs/04-admin-operations.md` §2, §8.3
- Modify: `BETA-RELEASE.md` §7
- Modify: `docs/progress.md`

### Task P7.1: docs/02 §7.11 — `GET /api/admin/trash` 추가

- [ ] **Step 1: 섹션 라인 찾기**

```bash
grep -n "§7.11\|## 7.11\|GET /api/trash" docs/02-backend-data-model.md | head -20
```

- [ ] **Step 2: 기존 §7.11 GET /api/trash 블록 바로 아래에 admin endpoint 행 + 풀 스펙 추가**

추가할 내용 (실제 표/코드블록 형식은 §7.11 기존 패턴을 따름):

```text
GET /api/admin/trash
  Auth:    ROLE.ADMIN (PreAuthorize hasRole)
  Query:
    q          string?    이름 LIKE 검색 (case-insensitive, max 200)
    type       enum?      "file" | "folder" | (omit = both)
    ownerId    UUID?
    cursor     string?    base64 opaque (deletedAt|id) — TrashCursor 동일
    limit      int?       default 50, max 100
  Response 200:
    {
      "items": [{
        "id":"...", "name":"...", "type":"file"|"folder",
        "deletedAt":"<ISO>", "purgeAfter":"<ISO>",
        "ownerId":"...", "ownerEmail":"...",
        "originalParentId": "..."|null, "originalParentName":"..."|null,
        "sizeBytes": <long>|null
      }, ...],
      "nextCursor": "..."|null
    }
  Response 401:  UNAUTHORIZED
  Response 403:  FORBIDDEN  (MEMBER / AUDITOR)
  Response 400:  VALIDATION_ERROR (q too long / invalid type / invalid ownerId / invalid cursor)
  Audit emit: 0 (listing only)
  Mutation:  reuse POST /api/files|folders/{id}/restore + DELETE /api/trash/{type}/{id}
```

### Task P7.2: docs/04 §2 + §8.3 갱신

- [ ] **Step 1: §2 활성 라우트에 `/admin/trash/all` 추가, deferred 트리에서 제거**

```bash
grep -n "§2\|/admin/trash" docs/04-admin-operations.md | head -20
```

활성 라우트 목록(§2)에 `/admin/trash/all` 추가 (T8 `/admin/storage` 추가 패턴 그대로). 트리 표기에서 `/admin/trash/all`을 활성 swap. `/admin/trash/policy` 는 v1.x 유지 코멘트.

- [ ] **Step 2: §8.3 closure 항목 ✓ 표시**

```diff
- - [ ] 전체 사용자의 휴지통 파일 (관리자 전용) — *v1.x deferred (admin frontend 미구현)*
+ - [x] 전체 사용자의 휴지통 파일 (관리자 전용) — `/admin/trash/all` (Wave 2 T9, 2026-05-07)
+   - 목록: `GET /api/admin/trash` (admin DTO: owner/originalParent/size 노출)
+   - 단건 복원/영구삭제: 기존 endpoint 재사용
+   - 정책 조정 UI(`/admin/trash/policy`) / bulk / 2인 승인 / deletedBy 컬럼: v1.x deferred
```

### Task P7.3: BETA-RELEASE §7 wording

- [ ] **Step 1: header `Source` 트랙 추가 + §7 admin frontend 갱신**

```bash
grep -n "wave2-t6\|wave2-t8\|admin-storage-overview\|## 7\|§7" BETA-RELEASE.md | head -10
```

header 의 Source 라인에 `wave2-t9-admin-global-trash` 추가. §7 (admin frontend) 의 `/admin/trash/all` 관련 wording을 "활성"으로 swap. 정확한 wording은 기존 §7 표현(예: T8 wording)에 맞춰 동형 갱신.

### Task P7.4: progress.md 세션 기록

- [ ] **Step 1: `docs/progress.md` 최상단에 새 세션 블록 prepend**

```markdown
## 2026-05-07 세션 — Wave 2 T9: admin-global-trash

### 완료
- [Wave 2 T9] `GET /api/admin/trash` admin listing endpoint (DTO: owner/originalParent/size)
- [Wave 2 T9] `/admin/trash/all` 페이지 (filter q/type/ownerId + cursor pagination + ConfirmDialog purge + AdminSideNav 토글 + landing 카드)
- [Wave 2 T9] mutation 0 신규 — 기존 file/folder restore + trash purge endpoint 재사용 (ADMIN ROLE이 SpEL 가드 통과)
- [Wave 2 T9] audit emit 변경 0 (47 enum 유지)
- [Wave 2 T9] DB schema 변경 0 (deletedBy 컬럼 미도입, audit_log lookup이 차선)
- [Wave 2 T9] docs/02 §7.11, docs/04 §2 + §8.3, BETA-RELEASE §7 갱신

### 다음 세션 컨텍스트
- T7 admin-dashboard PR #70 (CONFLICTING) 해소 — `lib/api.ts` / `queryKeys.ts` / `AdminSideNav.tsx` / `app/admin/page.tsx` 4개 add-only 머지(자명).
- v1.x backlog: `deletedBy` 컬럼(V10) / 휴지통 정책 UI(/admin/trash/policy) / bulk restore·purge / 2인 승인 워크플로 / 날짜 범위 필터 / full path resolve / folder subtree size.
- A/B/C 운영 시나리오 검증: 사내 베타 시 ADMIN이 cross-owner 복원 시 audit_log 의 actor_id 경로 운영 매뉴얼 작성 필요(별도 트랙).

### 블로커
- 없음
```

- [ ] **Step 2: 전체 docs 변경 grep으로 wording drift 0 확인**

```bash
grep -n "/admin/trash" docs/04-admin-operations.md docs/02-backend-data-model.md BETA-RELEASE.md
grep -n "휴지통.*v1.x\|deferred.*휴지통" docs/04-admin-operations.md
```

Expected: deferred로 남는 것은 `/admin/trash/policy` / 2인 승인 / bulk 만. `/admin/trash/all`은 활성으로만 표기.

- [ ] **Step 3: 최종 commit + dev/active → 머지 후 dev/completed 이동 안내**

```bash
git add docs/02-backend-data-model.md docs/04-admin-operations.md \
        BETA-RELEASE.md docs/progress.md
git commit -m "docs(wave2-t9): document /api/admin/trash + /admin/trash/all activation"
```

---

## 최종 게이트

- [ ] **Step 1: backend 전체 테스트**

```bash
cd backend && ./gradlew clean test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: frontend 전체 게이트**

```bash
cd frontend && npm run typecheck && npm run lint && npm run test --run && npm run build
```

Expected: 모두 exit 0. 신규 테스트 ~24 케이스 추가(spec §10 기준), 전체 GREEN.

- [ ] **Step 3: T7 conflict 사전 점검**

```bash
git fetch origin
git diff --stat origin/master..HEAD
git log --oneline origin/master..HEAD
```

T7 PR #70(`feat/admin-dashboard`) 과의 conflict 영역(`lib/api.ts` / `lib/queryKeys.ts` / `AdminSideNav.tsx` / `app/admin/page.tsx`)이 모두 add-only 인지 확인. 충돌 시 자명한 add-only 머지로 해결.

- [ ] **Step 4: PR 생성**

```bash
git push -u origin feat/wave2-t9-admin-global-trash
gh pr create --title "feat(wave2-t9): admin global trash (/admin/trash/all)" --body "$(cat <<'EOF'
## Summary
- Wave 2 T9 closure — `/admin/trash/all` admin frontend + new `GET /api/admin/trash` listing endpoint.
- Mutation 0 신규: 기존 `POST /api/files|folders/{id}/restore` + `DELETE /api/trash/{type}/{id}` 재사용 (ADMIN ROLE이 SpEL 가드 통과).
- audit emit 0 / DB schema 0 / 권한 enum 0 변경.
- spec: `docs/superpowers/specs/2026-05-07-wave2-t9-admin-global-trash-design.md`
- plan: `docs/superpowers/plans/2026-05-07-wave2-t9-admin-global-trash.md`

## Test plan
- [x] `cd backend && ./gradlew test` — BUILD SUCCESSFUL (신규 ~15 케이스)
- [x] `cd frontend && npm run typecheck && npm run lint && npm run test --run && npm run build` — exit 0 (신규 ~9 케이스)
- [x] `/admin/trash/all` 수동 점검: ADMIN 200 / MEMBER redirect / AUDITOR redirect
- [x] T7 PR #70 conflict 영역(api.ts / queryKeys.ts / AdminSideNav / admin landing) add-only 자명 머지

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: 머지 후 dev/active → dev/completed 이동**

머지 완료 시 후속 정리(별도 PR):

```bash
git mv dev/active/wave2-t9-admin-global-trash dev/completed/wave2-t9-admin-global-trash
git commit -m "docs(wave2-t9): archive dev-docs to dev/completed/"
```

---

## 자기 점검 체크 (스펙 커버리지 + 일관성)

- spec §3 mutation 재사용 → P5 useAdminRestore/Purge가 `restoreFile/restoreFolder/purgeTrashItem` 재호출 ✅
- spec §4.1 신규 `GET /api/admin/trash` → P3 controller ✅
- spec §4.4 DTO 10 fields → P2.1 record 10 fields 일치 ✅
- spec §4.5 ConfirmDialog purge / 즉시 restore / cursor 리셋 → P6 page 처리 ✅
- spec §4.6 AUDITOR 403 → P3.1 테스트 케이스 ✅
- spec §5.1 batch lookup (N+1 회피) → P2.2 service `userRepository.findAllById` + `folderRepository.findAllById` ✅
- spec §5.2 AdminSideNav swap → P6.2 ✅
- spec §5.3 docs 4 파일 → P7.1~P7.3 ✅
- spec §10 머지 게이트 → 최종 게이트 step 1~2 ✅
- spec §11 단일 PR 스코프 → P1~P7 단일 브랜치 / 단일 PR ✅
- spec §13 DB/audit/enum 변경 0 → 모든 task 비파괴 add-only ✅

타입/이름 일관성:
- backend record/class 이름: `AdminTrashController`, `AdminTrashService`, `AdminTrashRepository`, `AdminTrashItemDto`, `AdminTrashPage`, `AdminTrashFilters` (spec §5.1과 1:1 일치) ✅
- frontend export 이름: `adminListTrash`, `qk.adminTrashList`, `useAdminTrashList`, `useAdminRestoreTrashItem`, `useAdminPurgeTrashItem`, `invalidations.afterAdminTrashChanged` (spec §5.2와 1:1 일치) ✅
- TrashItemType wire 값(`"file"`/`"folder"`) backend/frontend 동일 ✅
- TrashCursor 형식 backend/frontend 동일 (opaque base64) ✅
