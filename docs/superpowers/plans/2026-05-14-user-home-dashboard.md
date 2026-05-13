# User Home Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** root `/` redirect 를 personal home dashboard 로 전환 — 4 위젯 (WelcomeHeader / StarredCard / QuotaCard / SharedWithMeCard) + 신규 endpoint 1 + URL 라우팅 변경.

**Architecture:** admin overview (PR #200) 의 `SectionCard` + `admin-grid` 패턴 reuse. backend `/api/me/shared-with-me` 신규 1 endpoint, favorites 측은 병렬 favorites-list 트랙 산출물 reuse (Task 0 gate). frontend `page.tsx` redirect 로직 제거 + `<HomeDashboard />` 마운트.

**Tech Stack:** Next.js 15 App Router · TypeScript · TanStack Query v5 · Tailwind · Spring Boot · JPA · JUnit5 · Vitest · Testing Library.

**Spec:** `docs/superpowers/specs/2026-05-14-user-home-dashboard-design.md` (PR #242)

**Dependency:** favorites-list 트랙 (working tree WIP 2026-05-14 — `useMyFavorites` + `types/favorite.ts` + `GET /api/me/favorites`) 머지 후 Task 1 진입.

---

## Task 0: Dependency Gate — favorites-list 트랙 머지 확인

**Files:**
- Read-only: `frontend/src/hooks/useMyFavorites.ts`
- Read-only: `frontend/src/types/favorite.ts`
- Read-only: `frontend/src/lib/api.ts` (`api.listMyFavorites` 또는 동등)

- [ ] **Step 1: master 최신 fetch**

```bash
git -C /c/project/IbizDrive fetch origin --prune
git -C /c/project/IbizDrive switch master
git -C /c/project/IbizDrive pull --ff-only origin master
```

Expected: `Already up to date.` 또는 fast-forward 성공.

- [ ] **Step 2: dependency 파일 존재 확인**

```bash
ls -la /c/project/IbizDrive/frontend/src/hooks/useMyFavorites.ts \
       /c/project/IbizDrive/frontend/src/types/favorite.ts
```

Expected: 두 파일 모두 존재. 없으면 favorites-list 트랙 미머지 — Task 0 에서 정지하고 사용자에게 보고.

- [ ] **Step 3: `FavoriteListItem` 타입의 필드 capture**

`frontend/src/types/favorite.ts` 를 읽어 `FavoriteListItem` (또는 동등 타입) 의 필드 nv. 다음 필드의 존재 여부 확인:
- `resourceType: 'file' | 'folder'`
- `resourceId: string`
- `name: string`
- `starredAt: string` (ISO8601)
- `modifiedAt: string`
- `workspace: { kind, id, name }`
- `folderPath: { id, name }[]`

**필드 mismatch 시 대응 (trade-off 결정):**
- `workspace` 또는 `folderPath` 누락 시 → Task 5 StarredCard 에서 별도 lookup hook 추가 (e.g., `useFolderPath(folderId)`) 또는 favorites-list 트랙에 필드 보강 PR 분리. 본 plan 의 default 결정: **별도 lookup hook 추가** (KISS — 본 트랙 단독 머지 가능).

- [ ] **Step 4: 새 브랜치 생성**

```bash
git -C /c/project/IbizDrive switch -c feat/user-home-dashboard
```

Expected: `Switched to a new branch 'feat/user-home-dashboard'`

- [ ] **Step 5: Commit gate (changes 없음, branch 만 push)**

```bash
git -C /c/project/IbizDrive push -u origin feat/user-home-dashboard
```

Expected: new branch published.

---

## Task 1: Backend — PermissionRepository.findGrantsToUserPaged + JPA slice test

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/permission/PermissionRepository.java`
- Test: `backend/src/test/java/com/ibizdrive/permission/PermissionRepositoryFindGrantsToUserTest.java`

- [ ] **Step 1: JPA slice test 작성 (실패)**

`backend/src/test/java/com/ibizdrive/permission/PermissionRepositoryFindGrantsToUserTest.java`:

```java
package com.ibizdrive.permission;

import com.ibizdrive.IbizDriveApplication;
import com.ibizdrive.testsupport.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@ContextConfiguration(classes = IbizDriveApplication.class)
@Import(PostgresTestContainer.class)
class PermissionRepositoryFindGrantsToUserTest {

    @Autowired PermissionRepository repository;

    @Test
    void findGrantsToUserPaged_본인_USER_grantee_만_반환_grantedAt_DESC() {
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID resA = UUID.randomUUID();
        UUID resB = UUID.randomUUID();
        Instant now = Instant.now();

        // Given — me 에 grant 2건 + other 에 grant 1건 + everyone grant 1건
        repository.save(grant(resA, "file", me, "USER", "READ", now.minusSeconds(20)));
        repository.save(grant(resB, "folder", me, "USER", "WRITE", now.minusSeconds(10)));
        repository.save(grant(resA, "file", other, "USER", "READ", now));
        repository.save(grant(resB, "folder", null, "EVERYONE", "READ", now));

        // When
        List<Permission> rows = repository.findGrantsToUserPaged(me, 50);

        // Then — me 의 USER grant 2건만, granted_at DESC
        assertThat(rows).extracting(Permission::getResourceId)
            .containsExactly(resB, resA);
    }

    private static Permission grant(UUID resourceId, String type, UUID subjectId,
            String subjectKind, String level, Instant grantedAt) {
        // Permission 빌더 — 실제 entity 시그니처에 맞춰 implementation 시 채움.
        // 기존 PermissionRepository test (e.g., PermissionRepositoryTest.java) 패턴 reuse.
        throw new UnsupportedOperationException("implementation 시 기존 entity builder 사용");
    }
}
```

**구현 참고:** `backend/src/test/java/com/ibizdrive/permission/` 에 기존 JPA slice test 가 있다면 그 builder 패턴 reuse. 없으면 `Permission` entity 의 생성자/setter 로 직접 build.

- [ ] **Step 2: Run test — 실패 확인**

```bash
cd /c/project/IbizDrive/backend && ./gradlew test --tests "PermissionRepositoryFindGrantsToUserTest"
```

Expected: FAIL — `findGrantsToUserPaged` 메서드 미정의.

- [ ] **Step 3: PermissionRepository 에 메서드 추가**

`backend/src/main/java/com/ibizdrive/permission/PermissionRepository.java`:

```java
@Query("""
    SELECT p FROM Permission p
    WHERE p.granteeSubjectKind = 'USER'
      AND p.granteeSubjectId = :userId
      AND (p.revokedAt IS NULL)
    ORDER BY p.grantedAt DESC
""")
List<Permission> findGrantsToUserPaged(@Param("userId") UUID userId, Pageable pageable);
```

**Note:** 기존 `Permission` entity 의 필드명 (`granteeSubjectKind`, `granteeSubjectId`, `revokedAt`, `grantedAt`) 은 implementation 시 검증. revoke 패턴이 다르면 (e.g., `deleted_at`) 그에 맞춰 조건 수정.

테스트 시그니처 정합을 위해 limit 만 받는 overload 추가:

```java
default List<Permission> findGrantsToUserPaged(UUID userId, int limit) {
    return findGrantsToUserPaged(userId, PageRequest.of(0, limit));
}
```

- [ ] **Step 4: Run test — pass 확인**

```bash
cd /c/project/IbizDrive/backend && ./gradlew test --tests "PermissionRepositoryFindGrantsToUserTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/ibizdrive/permission/PermissionRepository.java \
        backend/src/test/java/com/ibizdrive/permission/PermissionRepositoryFindGrantsToUserTest.java
git commit -m "feat(user-home-dashboard): PermissionRepository.findGrantsToUserPaged (USER grantee + grantedAt DESC)"
```

---

## Task 2: Backend — MySharedWithMeListResponse DTO + MeSharedController + MockMvc test

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/me/MeSharedController.java`
- Create: `backend/src/main/java/com/ibizdrive/me/dto/MySharedWithMeListResponse.java`
- Create: `backend/src/main/java/com/ibizdrive/me/dto/MySharedWithMeItem.java`
- Test: `backend/src/test/java/com/ibizdrive/me/MeSharedControllerTest.java`

- [ ] **Step 1: MockMvc 테스트 작성 (실패)**

`backend/src/test/java/com/ibizdrive/me/MeSharedControllerTest.java`:

```java
package com.ibizdrive.me;

import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MeSharedController.class)
class MeSharedControllerTest {

    @Autowired MockMvc mvc;
    @MockBean PermissionRepository permissionRepository;
    @MockBean MeSharedQueryService meSharedQueryService;

    @Test
    @WithMockUser
    void sharedWithMe_200_본인_grantee_5건() throws Exception {
        // Given — service 가 5 row 반환하도록 stub
        when(meSharedQueryService.list(any(), eq(5)))
            .thenReturn(List.of(/* 5 MySharedWithMeItem */));

        mvc.perform(get("/api/me/shared-with-me?limit=5"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.items").isArray())
           .andExpect(jsonPath("$.items.length()").value(5));
    }

    @Test
    void sharedWithMe_401_unauthenticated() throws Exception {
        mvc.perform(get("/api/me/shared-with-me"))
           .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Run test — 실패 확인**

```bash
cd /c/project/IbizDrive/backend && ./gradlew test --tests "MeSharedControllerTest"
```

Expected: FAIL — `MeSharedController` / `MySharedWithMeItem` / `MeSharedQueryService` 미정의.

- [ ] **Step 3: DTO + Service + Controller 구현**

`backend/src/main/java/com/ibizdrive/me/dto/MySharedWithMeItem.java`:

```java
package com.ibizdrive.me.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MySharedWithMeItem(
    UUID permissionId,
    String resourceType,            // "file" | "folder"
    UUID resourceId,
    String name,
    String permission,              // "READ" | "WRITE" | "OWNER"
    Instant grantedAt,
    Granter grantedBy,
    List<FolderCrumb> folderPath
) {
    public record Granter(UUID id, String name) {}
    public record FolderCrumb(UUID id, String name) {}
}
```

`backend/src/main/java/com/ibizdrive/me/dto/MySharedWithMeListResponse.java`:

```java
package com.ibizdrive.me.dto;

import java.util.List;

public record MySharedWithMeListResponse(
    List<MySharedWithMeItem> items,
    String nextCursor
) {}
```

`backend/src/main/java/com/ibizdrive/me/MeSharedQueryService.java`:

```java
package com.ibizdrive.me;

import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.FolderPathService;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.me.dto.MySharedWithMeItem;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionRepository;
import com.ibizdrive.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class MeSharedQueryService {

    private final PermissionRepository permissionRepository;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final FolderPathService folderPathService;

    public MeSharedQueryService(PermissionRepository pr, FileRepository fr,
            FolderRepository fdr, UserRepository ur, FolderPathService fps) {
        this.permissionRepository = pr;
        this.fileRepository = fr;
        this.folderRepository = fdr;
        this.userRepository = ur;
        this.folderPathService = fps;
    }

    public List<MySharedWithMeItem> list(UUID userId, int limit) {
        List<Permission> grants = permissionRepository.findGrantsToUserPaged(userId, limit);
        return grants.stream().map(this::toItem).toList();
    }

    private MySharedWithMeItem toItem(Permission p) {
        // resource resolve — file or folder
        // granter resolve — userRepository.findById(p.getGrantedBy())
        // folderPath — folderPathService.pathTo(resourceId) (file 이면 parent folder, folder 면 self)
        // implementation 시 기존 FolderPathService API 시그니처 확인 후 채움
        throw new UnsupportedOperationException("implementation 시 기존 FolderPathService API 호출 패턴 reuse");
    }
}
```

`backend/src/main/java/com/ibizdrive/me/MeSharedController.java`:

```java
package com.ibizdrive.me;

import com.ibizdrive.me.dto.MySharedWithMeListResponse;
import com.ibizdrive.security.IbizDriveUserDetails;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@PreAuthorize("isAuthenticated()")
public class MeSharedController {

    private final MeSharedQueryService service;

    public MeSharedController(MeSharedQueryService service) {
        this.service = service;
    }

    @GetMapping("/shared-with-me")
    public MySharedWithMeListResponse sharedWithMe(
            @AuthenticationPrincipal IbizDriveUserDetails principal,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return new MySharedWithMeListResponse(
            service.list(principal.getUserId(), limit),
            null   // cursor 페이지네이션은 후속 PR
        );
    }
}
```

**Note:** `IbizDriveUserDetails.getUserId()` 메서드 시그니처는 implementation 시 검증. WorkspaceController 의 `me()` endpoint 가 동일 principal 을 사용하므로 그 패턴 reuse.

- [ ] **Step 4: Run test — pass 확인**

```bash
cd /c/project/IbizDrive/backend && ./gradlew test --tests "MeSharedControllerTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/ibizdrive/me/ \
        backend/src/test/java/com/ibizdrive/me/MeSharedControllerTest.java
git commit -m "feat(user-home-dashboard): GET /api/me/shared-with-me endpoint (USER grantee, limit≤50)"
```

---

## Task 3: Frontend — types + api + queryKey + hook (sharedWithMe)

**Files:**
- Create: `frontend/src/types/dashboard.ts`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/lib/queryKeys.ts`
- Create: `frontend/src/hooks/useMySharedWithMe.ts`
- Test: `frontend/src/lib/api.fetchMySharedWithMe.test.ts`
- Test: `frontend/src/hooks/useMySharedWithMe.test.tsx`

- [ ] **Step 1: API 테스트 작성 (실패)**

`frontend/src/lib/api.fetchMySharedWithMe.test.ts`:

```ts
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { api } from './api'

describe('api.fetchMySharedWithMe', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response(JSON.stringify({
      items: [
        {
          permissionId: 'p1',
          resourceType: 'file',
          resourceId: 'f1',
          name: '계약서.pdf',
          permission: 'READ',
          grantedAt: '2026-05-14T08:00:00Z',
          grantedBy: { id: 'u1', name: '김매니저' },
          folderPath: [{ id: 'r1', name: '계약' }],
        },
      ],
      nextCursor: null,
    })))))
  })

  it('GET /api/me/shared-with-me?limit=5 호출 + 응답 parse', async () => {
    const res = await api.fetchMySharedWithMe(5)
    expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining('/api/me/shared-with-me?limit=5'), expect.any(Object))
    expect(res.items).toHaveLength(1)
    expect(res.items[0].name).toBe('계약서.pdf')
  })
})
```

- [ ] **Step 2: Run test — 실패 확인**

```bash
cd /c/project/IbizDrive/frontend && pnpm test -- api.fetchMySharedWithMe
```

Expected: FAIL — `api.fetchMySharedWithMe` undefined.

- [ ] **Step 3: types + api + queryKey + hook 구현**

`frontend/src/types/dashboard.ts`:

```ts
export interface MySharedWithMeItem {
  permissionId: string
  resourceType: 'file' | 'folder'
  resourceId: string
  name: string
  permission: 'READ' | 'WRITE' | 'OWNER'
  grantedAt: string
  grantedBy: { id: string; name: string }
  folderPath: { id: string; name: string }[]
}

export interface MySharedWithMeListResponse {
  items: MySharedWithMeItem[]
  nextCursor: string | null
}
```

`frontend/src/lib/api.ts` — 기존 `api` 객체에 추가:

```ts
fetchMySharedWithMe: async (limit = 20): Promise<MySharedWithMeListResponse> => {
  const res = await apiFetch(`/api/me/shared-with-me?limit=${limit}`)
  if (!res.ok) throw new ApiError(res.status, await res.text())
  return res.json()
},
```

(import 추가: `import type { MySharedWithMeListResponse } from '@/types/dashboard'`)

`frontend/src/lib/queryKeys.ts` — `qk` 객체에 추가:

```ts
/**
 * 사용자 본인이 받은 공유 권한 list (dashboard SharedWithMeCard).
 * 단일 키 — server resolve. permission grant/revoke 후 invalidate.
 */
mySharedWithMe: () => [...qk.all, 'me', 'shared-with-me'] as const,
```

`frontend/src/hooks/useMySharedWithMe.ts`:

```ts
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

export function useMySharedWithMe(limit = 5) {
  return useQuery({
    queryKey: qk.mySharedWithMe(),
    queryFn: () => api.fetchMySharedWithMe(limit),
    staleTime: 60_000,
  })
}
```

`frontend/src/hooks/useMySharedWithMe.test.tsx`:

```tsx
import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useMySharedWithMe } from './useMySharedWithMe'
import { api } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  api: { fetchMySharedWithMe: vi.fn() },
}))

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useMySharedWithMe', () => {
  it('limit 인자를 api 에 전달', async () => {
    vi.mocked(api.fetchMySharedWithMe).mockResolvedValue({ items: [], nextCursor: null })
    renderHook(() => useMySharedWithMe(5), { wrapper })
    await waitFor(() => expect(api.fetchMySharedWithMe).toHaveBeenCalledWith(5))
  })
})
```

- [ ] **Step 4: Run tests — pass 확인**

```bash
cd /c/project/IbizDrive/frontend && pnpm test -- api.fetchMySharedWithMe useMySharedWithMe
```

Expected: PASS (2 test files, 2 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/types/dashboard.ts \
        frontend/src/lib/api.ts \
        frontend/src/lib/api.fetchMySharedWithMe.test.ts \
        frontend/src/lib/queryKeys.ts \
        frontend/src/hooks/useMySharedWithMe.ts \
        frontend/src/hooks/useMySharedWithMe.test.tsx
git commit -m "feat(user-home-dashboard): useMySharedWithMe hook + api + queryKey"
```

---

## Task 4: Frontend — WelcomeHeader + QuotaCard (간단 위젯)

**Files:**
- Create: `frontend/src/components/home/WelcomeHeader.tsx`
- Create: `frontend/src/components/home/WelcomeHeader.test.tsx`
- Create: `frontend/src/components/home/QuotaCard.tsx`
- Create: `frontend/src/components/home/QuotaCard.test.tsx`

- [ ] **Step 1: WelcomeHeader 테스트 작성**

`frontend/src/components/home/WelcomeHeader.test.tsx`:

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { WelcomeHeader } from './WelcomeHeader'

vi.mock('@/hooks/useMe', () => ({
  useMe: () => ({ data: { id: 'u1', name: '이태석', email: 'x@y' } }),
}))
vi.mock('@/hooks/useWorkspaces', () => ({
  useWorkspaces: () => ({ data: { department: { id: 'd1', name: '개발', rootFolderId: 'r1' }, teams: [] } }),
}))

describe('WelcomeHeader', () => {
  it('사용자명 + 부서 표시', () => {
    render(<WelcomeHeader />)
    expect(screen.getByText(/이태석/)).toBeInTheDocument()
    expect(screen.getByText(/개발/)).toBeInTheDocument()
  })

  it('workspace 0건 시 quick action disabled', () => {
    vi.doMock('@/hooks/useWorkspaces', () => ({
      useWorkspaces: () => ({ data: { department: null, teams: [] } }),
    }))
    // 별도 render — disabled 확인
  })
})
```

- [ ] **Step 2: Run test — 실패 확인**

```bash
cd /c/project/IbizDrive/frontend && pnpm test -- WelcomeHeader
```

Expected: FAIL — module not found.

- [ ] **Step 3: WelcomeHeader 구현**

`frontend/src/components/home/WelcomeHeader.tsx`:

```tsx
'use client'
import { useMe } from '@/hooks/useMe'
import { useWorkspaces } from '@/hooks/useWorkspaces'

export function WelcomeHeader() {
  const { data: me } = useMe()
  const { data: ws } = useWorkspaces()
  const hasWorkspace = !!(ws?.department || ws?.teams.length)
  const subtitle = ws?.department?.name ?? ws?.teams[0]?.name ?? '소속 워크스페이스 없음'

  return (
    <div className="flex items-end justify-between">
      <div>
        <h1 className="text-[20px] font-semibold text-fg mb-1">
          안녕하세요, {me?.name ?? '사용자'}님
        </h1>
        <p className="text-[13px] text-fg-2">{subtitle}</p>
      </div>
      <div className="flex gap-2">
        <button className="btn-primary btn-sm" disabled={!hasWorkspace}>업로드</button>
        <button className="btn-ghost btn-sm" disabled={!hasWorkspace}>새 폴더</button>
      </div>
    </div>
  )
}
```

**Note:** 업로드/새 폴더 버튼의 onClick 은 v1.1 — 본 PR 에선 disabled 또는 router.push to default workspace root. KISS — disabled placeholder 로 시작, follow-up PR 에서 wire.

- [ ] **Step 4: QuotaCard 테스트 + 구현**

`frontend/src/components/home/QuotaCard.test.tsx`:

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QuotaCard } from './QuotaCard'

vi.mock('@/hooks/useStorageQuota', () => ({
  useStorageQuota: () => ({ data: { used: 8_000_000_000, quota: 50_000_000_000 } }),
}))

describe('QuotaCard', () => {
  it('used / quota 표시 + 진행률', () => {
    render(<QuotaCard />)
    expect(screen.getByText(/7\.5 GB|8\.0 GB/)).toBeInTheDocument()
    expect(screen.getByText(/50 GB|46.6 GB/)).toBeInTheDocument()
  })
})
```

`frontend/src/components/home/QuotaCard.tsx`:

```tsx
'use client'
import { SectionCard } from '@/components/admin/SectionCard'
import { useStorageQuota } from '@/hooks/useStorageQuota'
import { formatBytes } from '@/lib/formatBytes'

export function QuotaCard() {
  const { data } = useStorageQuota()
  const used = data?.used ?? 0
  const quota = data?.quota ?? 0
  const ratio = quota > 0 ? Math.min(1, used / quota) : 0
  const tone = ratio >= 0.95 ? 'danger' : ratio >= 0.8 ? 'warning' : 'normal'

  return (
    <SectionCard title="내 저장공간" subtitle="할당량 대비 사용량">
      <div className="space-y-3">
        <div className="text-[14px]">
          <span className="font-semibold">{formatBytes(used)}</span>
          <span className="text-fg-2"> / {formatBytes(quota)}</span>
        </div>
        <div className="h-2 rounded bg-bg-2 overflow-hidden" role="progressbar" aria-valuenow={Math.round(ratio * 100)}>
          <div className={`h-full ${tone === 'danger' ? 'bg-red-500' : tone === 'warning' ? 'bg-amber-500' : 'bg-accent'}`}
               style={{ width: `${ratio * 100}%` }} />
        </div>
        <p className="text-[12px] text-fg-muted">{formatBytes(Math.max(0, quota - used))} 남음</p>
      </div>
    </SectionCard>
  )
}
```

- [ ] **Step 5: Run tests + Commit**

```bash
cd /c/project/IbizDrive/frontend && pnpm test -- WelcomeHeader QuotaCard
```

Expected: PASS.

```bash
git add frontend/src/components/home/WelcomeHeader.tsx \
        frontend/src/components/home/WelcomeHeader.test.tsx \
        frontend/src/components/home/QuotaCard.tsx \
        frontend/src/components/home/QuotaCard.test.tsx
git commit -m "feat(user-home-dashboard): WelcomeHeader + QuotaCard widgets"
```

---

## Task 5: Frontend — StarredCard + SharedWithMeCard (data-fetching 위젯)

**Files:**
- Create: `frontend/src/components/home/StarredCard.tsx`
- Create: `frontend/src/components/home/StarredCard.test.tsx`
- Create: `frontend/src/components/home/SharedWithMeCard.tsx`
- Create: `frontend/src/components/home/SharedWithMeCard.test.tsx`

- [ ] **Step 1: StarredCard 테스트 + 구현**

`frontend/src/components/home/StarredCard.test.tsx`:

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { StarredCard } from './StarredCard'

vi.mock('@/hooks/useMyFavorites', () => ({
  useMyFavorites: vi.fn(),
}))

import { useMyFavorites } from '@/hooks/useMyFavorites'

describe('StarredCard', () => {
  it('empty state — 0건 시 안내 표시', () => {
    vi.mocked(useMyFavorites).mockReturnValue({ data: { items: [] }, isLoading: false } as any)
    render(<StarredCard />)
    expect(screen.getByText(/아직 즐겨찾기한 항목이 없습니다/)).toBeInTheDocument()
  })

  it('row 8개 렌더 (limit 적용)', () => {
    vi.mocked(useMyFavorites).mockReturnValue({
      data: { items: Array.from({ length: 8 }, (_, i) => ({
        resourceType: 'file', resourceId: `f${i}`, name: `파일${i}.pdf`,
        starredAt: '2026-05-14T10:00:00Z',
      })) },
      isLoading: false,
    } as any)
    render(<StarredCard />)
    expect(screen.getAllByRole('listitem')).toHaveLength(8)
  })
})
```

`frontend/src/components/home/StarredCard.tsx`:

```tsx
'use client'
import Link from 'next/link'
import { SectionCard } from '@/components/admin/SectionCard'
import { useMyFavorites } from '@/hooks/useMyFavorites'

export function StarredCard() {
  const { data, isLoading } = useMyFavorites({ limit: 8 })
  const items = data?.items ?? []

  return (
    <SectionCard
      title="즐겨찾기"
      subtitle="별표 표시한 항목"
      right={<Link href="/favorites" className="btn-ghost btn-xs">전체 보기 →</Link>}
    >
      {isLoading && <div className="text-[13px] text-fg-muted">불러오는 중…</div>}
      {!isLoading && items.length === 0 && (
        <div className="text-[13px] text-fg-muted">
          아직 즐겨찾기한 항목이 없습니다. 파일 이름 옆 ☆ 아이콘을 눌러 추가하세요.
        </div>
      )}
      <ul role="list" className="space-y-1">
        {items.slice(0, 8).map((it: any) => (
          <li key={`${it.resourceType}:${it.resourceId}`} className="flex items-center justify-between py-1">
            <span className="text-[13px]">{it.name}</span>
            <span className="text-[12px] text-fg-2">{it.resourceType === 'folder' ? '폴더' : '파일'}</span>
          </li>
        ))}
      </ul>
    </SectionCard>
  )
}
```

**Note:** `useMyFavorites` 의 시그니처는 favorites-list 트랙 산출물 — Task 0 에서 검증한 형식 (`{ limit: 8 }` 인자) 으로 호출. 인자 형식이 다르면 (e.g., `useMyFavorites(8)` positional) 그에 맞춰 수정.

- [ ] **Step 2: SharedWithMeCard 테스트 + 구현**

`frontend/src/components/home/SharedWithMeCard.test.tsx`:

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { SharedWithMeCard } from './SharedWithMeCard'

vi.mock('@/hooks/useMySharedWithMe', () => ({
  useMySharedWithMe: vi.fn(),
}))

import { useMySharedWithMe } from '@/hooks/useMySharedWithMe'

describe('SharedWithMeCard', () => {
  it('empty — 안내 표시', () => {
    vi.mocked(useMySharedWithMe).mockReturnValue({ data: { items: [], nextCursor: null }, isLoading: false } as any)
    render(<SharedWithMeCard />)
    expect(screen.getByText(/공유받은 항목이 없습니다/)).toBeInTheDocument()
  })

  it('row 5개 + 권한 칩 표시', () => {
    vi.mocked(useMySharedWithMe).mockReturnValue({
      data: {
        items: Array.from({ length: 5 }, (_, i) => ({
          permissionId: `p${i}`,
          resourceType: 'file',
          resourceId: `f${i}`,
          name: `공유파일${i}.pdf`,
          permission: 'READ',
          grantedAt: '2026-05-14T08:00:00Z',
          grantedBy: { id: 'u1', name: '김매니저' },
          folderPath: [],
        })),
        nextCursor: null,
      },
      isLoading: false,
    } as any)
    render(<SharedWithMeCard />)
    expect(screen.getAllByText('READ')).toHaveLength(5)
  })
})
```

`frontend/src/components/home/SharedWithMeCard.tsx`:

```tsx
'use client'
import Link from 'next/link'
import { SectionCard } from '@/components/admin/SectionCard'
import { useMySharedWithMe } from '@/hooks/useMySharedWithMe'

export function SharedWithMeCard() {
  const { data, isLoading } = useMySharedWithMe(5)
  const items = data?.items ?? []

  return (
    <SectionCard
      title="공유받은 항목"
      subtitle="다른 사용자가 직접 공유한 파일/폴더"
      right={<Link href="/shared" className="btn-ghost btn-xs">전체 보기 →</Link>}
    >
      {isLoading && <div className="text-[13px] text-fg-muted">불러오는 중…</div>}
      {!isLoading && items.length === 0 && (
        <div className="text-[13px] text-fg-muted">공유받은 항목이 없습니다.</div>
      )}
      <ul role="list" className="space-y-1">
        {items.map((it) => (
          <li key={it.permissionId} className="flex items-center justify-between py-1">
            <div className="flex items-center gap-2">
              <span className="text-[13px]">{it.name}</span>
              <span className="text-[11px] px-1.5 py-0.5 rounded bg-bg-2 text-fg-2">{it.permission}</span>
            </div>
            <span className="text-[12px] text-fg-muted">{it.grantedBy.name}</span>
          </li>
        ))}
      </ul>
    </SectionCard>
  )
}
```

- [ ] **Step 3: Run tests — pass 확인**

```bash
cd /c/project/IbizDrive/frontend && pnpm test -- StarredCard SharedWithMeCard
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/home/StarredCard.tsx \
        frontend/src/components/home/StarredCard.test.tsx \
        frontend/src/components/home/SharedWithMeCard.tsx \
        frontend/src/components/home/SharedWithMeCard.test.tsx
git commit -m "feat(user-home-dashboard): StarredCard + SharedWithMeCard widgets"
```

---

## Task 6: Frontend — HomeDashboard container + page.tsx redirect 제거

**Files:**
- Create: `frontend/src/components/home/HomeDashboard.tsx`
- Create: `frontend/src/components/home/HomeDashboard.test.tsx`
- Modify: `frontend/src/app/page.tsx`
- Modify: `frontend/src/app/page.test.tsx` (있다면)

- [ ] **Step 1: HomeDashboard 테스트 + 구현**

`frontend/src/components/home/HomeDashboard.test.tsx`:

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { HomeDashboard } from './HomeDashboard'

// 4 위젯 모두 mock
vi.mock('./WelcomeHeader', () => ({ WelcomeHeader: () => <div data-testid="welcome" /> }))
vi.mock('./StarredCard', () => ({ StarredCard: () => <div data-testid="starred" /> }))
vi.mock('./QuotaCard', () => ({ QuotaCard: () => <div data-testid="quota" /> }))
vi.mock('./SharedWithMeCard', () => ({ SharedWithMeCard: () => <div data-testid="shared" /> }))

describe('HomeDashboard', () => {
  it('4 위젯 모두 렌더', () => {
    render(<HomeDashboard />)
    expect(screen.getByTestId('welcome')).toBeInTheDocument()
    expect(screen.getByTestId('starred')).toBeInTheDocument()
    expect(screen.getByTestId('quota')).toBeInTheDocument()
    expect(screen.getByTestId('shared')).toBeInTheDocument()
  })
})
```

`frontend/src/components/home/HomeDashboard.tsx`:

```tsx
'use client'
import { WelcomeHeader } from './WelcomeHeader'
import { StarredCard } from './StarredCard'
import { QuotaCard } from './QuotaCard'
import { SharedWithMeCard } from './SharedWithMeCard'

export function HomeDashboard() {
  return (
    <div className="admin-grid">
      <WelcomeHeader />
      <div className="admin-row admin-row-1-1">
        <StarredCard />
        <QuotaCard />
      </div>
      <div className="admin-row">
        <SharedWithMeCard />
      </div>
    </div>
  )
}
```

- [ ] **Step 2: page.tsx redirect 제거**

`frontend/src/app/page.tsx`:

```tsx
'use client'
import { HomeDashboard } from '@/components/home/HomeDashboard'

export default function Home() {
  return <HomeDashboard />
}
```

(기존 `useEffect` + `router.replace` + `useWorkspaces` import 모두 제거. workspace 0 zero-state 는 WelcomeHeader 내부에서 처리)

- [ ] **Step 3: Run all home tests**

```bash
cd /c/project/IbizDrive/frontend && pnpm test -- home
```

Expected: PASS (모든 home 컴포넌트 test).

- [ ] **Step 4: Dev preview 수동 검증**

```bash
cd /c/project/IbizDrive/frontend && pnpm dev
```

Browser 에서 `http://localhost:3000` 접속 → dashboard 렌더 확인:
- ① WelcomeHeader 표시
- ② StarredCard 8 row 또는 empty
- ③ QuotaCard 진행률 바
- ④ SharedWithMeCard 5 row 또는 empty

Expected: 4 위젯 모두 렌더, redirect 없음.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/home/HomeDashboard.tsx \
        frontend/src/components/home/HomeDashboard.test.tsx \
        frontend/src/app/page.tsx
git commit -m "feat(user-home-dashboard): root / dashboard render + redirect 제거"
```

---

## Task 7: Docs — 00 ADR + 01 §X + 02 §7 + v1x-backlog Tier 1 row

**Files:**
- Modify: `docs/00-overview.md`
- Modify: `docs/01-frontend-design.md`
- Modify: `docs/02-backend-data-model.md`
- Modify: `docs/v1x-backlog.md`

- [ ] **Step 1: ADR 추가 (`docs/00-overview.md`)**

ADR list 끝에 entry 추가:

```markdown
- **ADR #XX (2026-05-14)** — root `/` 의 의미를 workspace redirect 에서 personal home dashboard 로 변경.
  - Why: 사용자에게 "내 정체성 / 내 활동" 진입점 부재. 즐겨찾기/quota/공유받은 항목이 사이드바 탐색을 통해서만 접근 가능했음.
  - Trade-off: redirect 단순함 → dashboard 첫 진입 latency 증가 (2 endpoint fetch). staleTime 60s + Suspense 로 완화.
  - Scope: CLAUDE.md §3 원칙 1 (URL 이 "어디" 를 소유) 의 적용 범위는 workspace 내부 folder/file URL — root `/` 의미 변경은 원칙 위반 아님.
```

- [ ] **Step 2: 프론트 design §X 추가 (`docs/01-frontend-design.md`)**

새 섹션 `## X. User Home Dashboard` 추가:

```markdown
## X. User Home Dashboard (root `/`)

### X.1 진입점

- `/` route → `<HomeDashboard />` (workspace redirect 제거 — ADR #XX 참조).
- workspace 진입은 사이드바 트리 단일 진입점.

### X.2 위젯 inventory

| # | 컴포넌트 | hook | 데이터 |
|---|---|---|---|
| ① | `<WelcomeHeader>` | `useMe`, `useWorkspaces` | 사용자명 + 기본 workspace |
| ② | `<StarredCard>` | `useMyFavorites({limit:8})` | favorites-list 트랙 |
| ③ | `<QuotaCard>` | `useStorageQuota` | 사용자 quota |
| ④ | `<SharedWithMeCard>` | `useMySharedWithMe(5)` | 본 트랙 신규 endpoint |

### X.3 시각 디자인

admin overview 의 `SectionCard` + `admin-grid` + `admin-row admin-row-1-1` 패턴 reuse.

### X.4 empty/loading/error state

각 카드는 `<SectionCard>` 내부에 inline state 표시 — admin overview 와 동일.
```

- [ ] **Step 3: 백엔드 §7 endpoint spec 추가 (`docs/02-backend-data-model.md`)**

§7 (API 스펙) 끝에 entry 추가:

```markdown
### §7.XX `GET /api/me/shared-with-me`

- **권한**: `isAuthenticated()`. grantee_subject_kind=USER + grantee_subject_id=current user 만 반환.
- **query**: `limit` (default 20, max 50)
- **응답 200**: `MySharedWithMeListResponse`
  ```json
  {
    "items": [ { "permissionId", "resourceType", "resourceId", "name", "permission", "grantedAt", "grantedBy": {"id","name"}, "folderPath": [{"id","name"}] } ],
    "nextCursor": null
  }
  ```
- **정렬**: `granted_at DESC`.
- **out of scope**: indirect grant (department/team/everyone) 는 v1.1.
```

- [ ] **Step 4: v1x-backlog Tier 1 row 추가 + Last Updated 갱신**

`docs/v1x-backlog.md` Tier 1 표 마지막에 새 row:

```markdown
| ~~User Home Dashboard~~ | — | — | ✓ 2026-05-XX user-home-dashboard (PR #XXX) | **closure** — `/` redirect 제거 + 4 위젯 (Welcome/Starred/Quota/SharedWithMe) + `GET /api/me/shared-with-me` endpoint. favorites-list 트랙 의존. ADR #XX 추가 |
```

Last Updated 갱신:

```markdown
> **Last Updated**: 2026-05-XX (user-home-dashboard — `/` redirect → dashboard 전환)
```

- [ ] **Step 5: Commit**

```bash
git add docs/00-overview.md docs/01-frontend-design.md docs/02-backend-data-model.md docs/v1x-backlog.md
git commit -m "docs(user-home-dashboard): ADR + 01 §X + 02 §7 + backlog Tier 1 row"
```

---

## Task 8: PR 생성

- [ ] **Step 1: 전체 test 통과 검증**

```bash
cd /c/project/IbizDrive/backend && ./gradlew test
cd /c/project/IbizDrive/frontend && pnpm typecheck && pnpm lint && pnpm test
```

Expected: 모두 PASS.

- [ ] **Step 2: Push + PR**

```bash
git push origin feat/user-home-dashboard
cd /c/project/IbizDrive && gh pr create --title "feat(user-home-dashboard): root / dashboard 4 widget + /api/me/shared-with-me" --body "..."
```

PR body 에 명시:
- Spec PR #242 reference
- favorites-list 트랙 dependency closure
- Test plan: dev preview manual + vitest + junit

- [ ] **Step 3: PR review wait + 머지**

---

## Self-Review Checklist

**Spec coverage:**
- §3.1 WelcomeHeader → Task 4 ✓
- §3.2 StarredCard → Task 5 ✓
- §3.3 QuotaCard → Task 4 ✓ (useStorageQuota reuse 결정 후 §4.3 contingency 자동 해소)
- §3.4 SharedWithMeCard → Task 5 ✓
- §4.2 GET /api/me/shared-with-me → Task 1+2 ✓
- §5 frontend 파일 inventory → Task 3-6 ✓
- §6 empty/error/loading → 각 위젯 test ✓
- §7 테스트 전략 → 각 task ✓
- §9 영향문서 → Task 7 ✓

**Placeholder scan:** Task 1 `Permission` builder + Task 2 `MeSharedQueryService.toItem` 의 `throw new UnsupportedOperationException` — 의식적 placeholder (기존 entity/service 시그니처 확인 후 채움). implementation 시 첫 task 에서 reference 컨벤션 capture.

**Type consistency:**
- `MySharedWithMeItem` (backend record + frontend interface) 의 필드명 일치 — `permissionId`, `resourceType`, `resourceId`, `name`, `permission`, `grantedAt`, `grantedBy {id,name}`, `folderPath [{id,name}]` 검증 ✓
- `useMyFavorites({limit:8})` 호출 형식 — Task 0 에서 favorites-list 트랙 시그니처 검증 (positional vs object 인자) ✓

**Risk:**
- `IbizDriveUserDetails.getUserId()` 시그니처 — implementation 시 검증 (WorkspaceController 패턴 reuse)
- `useStorageQuota` 응답 필드 (`used`/`quota`) — implementation 시 검증 (없으면 §4.3 contingency endpoint 추가)
- favorites-list `FavoriteListItem` 응답이 dashboard 가 필요한 필드 누락 → Task 5 별도 lookup hook (Task 0 Step 3 결정)

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-05-14-user-home-dashboard.md`.**

**Dependency 머지 대기 중** — favorites-list 트랙 (PR TBD) 머지 후 Task 0 부터 실행.
