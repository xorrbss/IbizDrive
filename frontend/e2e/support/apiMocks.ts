import type { Page } from '@playwright/test'

/**
 * E2E 공용 fake-fetch 하네스 (E2E 확장 트랙, 2026-07-03).
 *
 * auth.e2e.ts의 mock 컨벤션(backend :8080 불요, page.route 가로채기)을 explorer
 * 도메인 전체로 확장 — 로그인된 사용자 + 부서 workspace + in-memory 파일셋을 제공한다.
 * 응답 shape은 실 backend(2026-07-02 로컬 스택)에서 캡처한 wire와 1:1.
 *
 * Playwright 라우트는 LIFO 우선순위 — catch-all을 가장 먼저 등록하고 구체 라우트를
 * 나중에 등록한다 (auth.e2e.ts와 동일 규칙).
 *
 * 상태는 mutation 라우트(업로드/삭제/복원/공유)가 직접 갱신 — TanStack Query invalidate
 * 후 재fetch가 변경을 반영하는 실제 흐름을 그대로 검증할 수 있다.
 */

export const IDS = {
  user: '11111111-1111-4111-8111-111111111111',
  peer: '22222222-2222-4222-8222-222222222222',
  dept: '33333333-3333-4333-8333-333333333333',
  rootFolder: '44444444-4444-4444-8444-444444444444',
  subFolder: '55555555-5555-4555-8555-555555555555',
  filePng: '66666666-6666-4666-8666-666666666666',
  fileTxt: '77777777-7777-4777-8777-777777777777',
  versionV1: '88888888-8888-4888-8888-888888888888',
  versionV2: '99999999-9999-4999-8999-999999999999',
} as const

export const DEPT_NAME = '개발부'
/**
 * 부서 root 폴더의 canonical URL (workspacePath.ts §5.1 — /d/:deptId/:folderId/:slug).
 * slug 미포함 URL로 진입하면 ClientFilesPage의 canonical redirect가 발생해
 * 직전에 붙인 ?file= 같은 query param을 지울 수 있다 — 테스트는 canonical로 직행.
 */
export const DEPT_ROOT_URL = `/d/${IDS.dept}/${IDS.rootFolder}/root`

const NOW = '2026-07-03T00:00:00Z'

interface MockFile {
  id: string
  name: string
  mimeType: string
  size: number
  versions: Array<{
    id: string
    versionNumber: number
    sizeBytes: number
    isCurrent: boolean
  }>
}

interface MockTrashItem {
  id: string
  name: string
  type: 'file' | 'folder'
}

interface MockShare {
  id: string
  fileId: string
  subjectId: string
  subjectName: string
  preset: string
}

export interface UploadBehavior {
  /** 'ok' → 201 신규 파일. 'conflict-then-version' → resolution 없으면 409, new_version이면 200. */
  mode: 'ok' | 'conflict-then-version'
}

export class MockDrive {
  files: MockFile[] = [
    {
      id: IDS.filePng,
      name: 'pixel.png',
      mimeType: 'image/png',
      size: 70,
      versions: [
        { id: IDS.versionV1, versionNumber: 1, sizeBytes: 70, isCurrent: true },
      ],
    },
    {
      id: IDS.fileTxt,
      name: 'note.txt',
      mimeType: 'text/plain',
      size: 13,
      versions: [
        { id: IDS.versionV2, versionNumber: 1, sizeBytes: 13, isCurrent: true },
      ],
    },
  ]
  trash: MockTrashItem[] = []
  shares: MockShare[] = []
  subFolderName = '기획안'
  uploadBehavior: UploadBehavior = { mode: 'ok' }
  /** 마지막 업로드 POST의 multipart 원문 — resolution 파라미터 검증용. */
  lastUploadPostData: string | null = null
  /** 업로드 POST 횟수 — useUpload 다중 마운트 중복 기동 회귀 가드. */
  uploadPostCount = 0

  private seq = 0

  private nextId(): string {
    this.seq += 1
    const tail = String(this.seq).padStart(12, '0')
    return `aaaaaaaa-bbbb-4ccc-8ddd-${tail}`
  }

  private fileItemWire(f: MockFile) {
    return {
      id: f.id,
      type: 'file',
      name: f.name,
      mimeType: f.mimeType,
      size: f.size,
      updatedAt: NOW,
      updatedBy: IDS.user,
      parentId: IDS.rootFolder,
      scope: { type: 'department', id: IDS.dept },
    }
  }

  private folderItemWire(id: string, name: string) {
    return {
      id,
      type: 'folder',
      name,
      mimeType: null,
      size: null,
      updatedAt: NOW,
      updatedBy: IDS.user,
      parentId: IDS.rootFolder,
      scope: { type: 'department', id: IDS.dept },
    }
  }

  private shareWire(s: MockShare) {
    return {
      id: s.id,
      fileId: s.fileId,
      folderId: null,
      permissionId: this.nextId(),
      sharedBy: IDS.user,
      message: null,
      expiresAt: null,
      createdAt: NOW,
      revokedAt: null,
      revokedBy: null,
      subjectType: 'user',
      subjectId: s.subjectId,
      subjectName: s.subjectName,
      preset: s.preset,
    }
  }

  /** 모든 라우트를 설치한다. 등록 순서 = LIFO 우선순위(뒤가 우선). */
  async install(page: Page): Promise<void> {
    // ── (lowest) catch-all — 목록에 없는 부수 호출은 빈 응답으로 흘림 ──
    await page.route('**/api/**', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: '{}' }),
    )

    await page.route('**/api/auth/csrf', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { 'Set-Cookie': 'XSRF-TOKEN=e2e-csrf; Path=/; SameSite=Lax' },
        body: JSON.stringify({ csrfToken: 'e2e-csrf' }),
      }),
    )

    await page.route('**/api/auth/me', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          user: {
            id: IDS.user,
            email: 'e2e@example.com',
            name: 'E2E User',
            kind: 'human',
            mustChangePassword: false,
          },
          departments: [],
          roles: ['ADMIN'],
          effectivePermissionsCacheKey: 'e2e-cache-key',
        }),
      }),
    )

    await page.route('**/api/workspaces/me', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          department: {
            kind: 'department',
            id: IDS.dept,
            name: DEPT_NAME,
            rootFolderId: IDS.rootFolder,
          },
          teams: [],
        }),
      }),
    )

    await page.route('**/api/me/favorites**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [] }),
      }),
    )
    await page.route('**/api/me/shared-with-me**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [] }),
      }),
    )

    await page.route('**/api/me/effective-permissions**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          permissions: [
            'READ', 'UPLOAD', 'EDIT', 'MOVE', 'DOWNLOAD',
            'DELETE', 'SHARE', 'PERMISSION_ADMIN', 'PURGE',
          ],
        }),
      }),
    )

    // ── 폴더 상세 (useCurrentFolder — Breadcrumb/SidebarNewButton 의존) ──
    await page.route('**/api/folders/*', (route) => {
      const req = route.request()
      const m = new URL(req.url()).pathname.match(/^\/api\/folders\/([^/]+)$/)
      if (!m || req.method() !== 'GET') return route.fallback()
      const id = decodeURIComponent(m[1])
      const rootCrumb = { id: IDS.rootFolder, name: DEPT_NAME, slug: 'root' }
      if (id === IDS.subFolder) {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            folder: { id, name: this.subFolderName },
            breadcrumb: [rootCrumb, { id, name: this.subFolderName, slug: 'sub' }],
            starred: false,
          }),
        })
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          folder: { id: IDS.rootFolder, name: DEPT_NAME },
          breadcrumb: [rootCrumb],
          starred: false,
        }),
      })
    })

    // ── 이동 (MoveFolderDialog — useMoveBulk 단건 fanout) ──
    await page.route('**/api/files/*/move', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: '{}' }),
    )
    await page.route('**/api/folders/*/move', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: '{}' }),
    )

    // ── 폴더 items (FileTable 목록) ──
    await page.route(`**/api/folders/${IDS.rootFolder}/items**`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            this.folderItemWire(IDS.subFolder, this.subFolderName),
            ...this.files.map((f) => this.fileItemWire(f)),
          ],
        }),
      }),
    )
    await page.route(`**/api/folders/${IDS.subFolder}/items**`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [] }),
      }),
    )

    // ── 파일 상세 (RightPanel) + 버전 + 버전 복원 ──
    await page.route('**/api/files/*', async (route) => {
      const req = route.request()
      const url = new URL(req.url())
      const m = url.pathname.match(/^\/api\/files\/([^/]+)$/)
      if (!m) return route.fallback()
      const id = decodeURIComponent(m[1])
      const f = this.files.find((x) => x.id === id)

      if (req.method() === 'DELETE') {
        // soft delete → 휴지통 이동
        if (f) {
          this.files = this.files.filter((x) => x.id !== id)
          this.trash.push({ id: f.id, name: f.name, type: 'file' })
        }
        return route.fulfill({ status: 204, body: '' })
      }

      if (req.method() === 'GET') {
        if (!f) return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
        const current = f.versions.find((v) => v.isCurrent) ?? f.versions[0]
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            file: {
              id: f.id,
              folderId: IDS.rootFolder,
              name: f.name,
              ownerId: IDS.user,
              sizeBytes: f.size,
              mimeType: f.mimeType,
              currentVersionId: current.id,
              scope: { type: 'department', id: IDS.dept },
              createdAt: NOW,
              updatedAt: NOW,
            },
            owner: { id: IDS.user, displayName: 'E2E User', email: 'e2e@example.com' },
            sharedWith: [],
            folderPath: [{ id: IDS.rootFolder, name: DEPT_NAME, slug: 'root' }],
          }),
        })
      }
      return route.fallback()
    })

    await page.route('**/api/files/*/versions', (route) => {
      const m = new URL(route.request().url()).pathname.match(/^\/api\/files\/([^/]+)\/versions$/)
      const f = m ? this.files.find((x) => x.id === decodeURIComponent(m[1])) : undefined
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          versions: (f?.versions ?? []).map((v) => ({
            id: v.id,
            versionNumber: v.versionNumber,
            sizeBytes: v.sizeBytes,
            checksumSha256: '0'.repeat(64),
            mimeType: f?.mimeType ?? 'application/octet-stream',
            scanStatus: 'pending',
            uploadedBy: IDS.user,
            uploadedAt: NOW,
            isCurrent: v.isCurrent,
          })),
        }),
      })
    })

    await page.route('**/api/files/*/versions/*/restore', (route) => {
      const m = new URL(route.request().url()).pathname.match(
        /^\/api\/files\/([^/]+)\/versions\/([^/]+)\/restore$/,
      )
      if (!m) return route.fallback()
      const f = this.files.find((x) => x.id === decodeURIComponent(m[1]))
      const targetVersionId = decodeURIComponent(m[2])
      if (f) {
        // 옵션 A(ADR #39): 과거 버전 내용으로 새 current 버전을 만든다.
        const maxNo = Math.max(...f.versions.map((v) => v.versionNumber))
        f.versions = f.versions.map((v) => ({ ...v, isCurrent: false }))
        const src = f.versions.find((v) => v.id === targetVersionId)
        f.versions.push({
          id: this.nextId(),
          versionNumber: maxNo + 1,
          sizeBytes: src?.sizeBytes ?? f.size,
          isCurrent: true,
        })
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ file: { id: f?.id ?? '', name: f?.name ?? '' } }),
      })
    })

    // ── 업로드 (XHR POST /api/files) ──
    await page.route('**/api/files', async (route) => {
      const req = route.request()
      if (req.method() !== 'POST') return route.fallback()
      const post = req.postData() ?? ''
      this.lastUploadPostData = post
      this.uploadPostCount += 1

      const nameMatch = post.match(/filename="([^"]+)"/)
      const uploadName = nameMatch ? nameMatch[1] : 'uploaded.bin'
      const hasNewVersion = post.includes('new_version')

      if (this.uploadBehavior.mode === 'conflict-then-version' && !hasNewVersion) {
        const existing = this.files.find((x) => x.name === uploadName)
        return route.fulfill({
          status: 409,
          contentType: 'application/json',
          body: JSON.stringify({
            error: {
              code: 'RENAME_CONFLICT',
              message: '이미 존재하는 이름입니다',
              details: existing
                ? { fileId: existing.id, fileName: existing.name }
                : {},
            },
          }),
        })
      }

      if (hasNewVersion) {
        const f = this.files.find((x) => x.name === uploadName)
        if (f) {
          const maxNo = Math.max(...f.versions.map((v) => v.versionNumber))
          f.versions = f.versions.map((v) => ({ ...v, isCurrent: false }))
          f.versions.push({
            id: this.nextId(),
            versionNumber: maxNo + 1,
            sizeBytes: 1,
            isCurrent: true,
          })
        }
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ file: f ? { id: f.id, name: f.name } : {} }),
        })
      }

      const created: MockFile = {
        id: this.nextId(),
        name: uploadName,
        mimeType: 'application/octet-stream',
        size: 1,
        versions: [
          { id: this.nextId(), versionNumber: 1, sizeBytes: 1, isCurrent: true },
        ],
      }
      this.files.push(created)
      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({ file: this.fileItemWire(created) }),
      })
    })

    // ── 휴지통 ──
    await page.route('**/api/trash?**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: this.trash.map((t) => ({
            id: t.id,
            name: t.name,
            type: t.type,
            deletedAt: NOW,
            purgeAfter: '2026-08-02T00:00:00Z',
            originalParentId: IDS.rootFolder,
            originalParentPath: `/${DEPT_NAME}`,
          })),
          nextCursor: null,
        }),
      }),
    )

    await page.route('**/api/files/*/restore', (route) => {
      const m = new URL(route.request().url()).pathname.match(/^\/api\/files\/([^/]+)\/restore$/)
      if (!m) return route.fallback()
      const id = decodeURIComponent(m[1])
      const t = this.trash.find((x) => x.id === id)
      if (t) {
        this.trash = this.trash.filter((x) => x.id !== id)
        this.files.push({
          id: t.id,
          name: t.name,
          mimeType: 'text/plain',
          size: 1,
          versions: [
            { id: this.nextId(), versionNumber: 1, sizeBytes: 1, isCurrent: true },
          ],
        })
      }
      return route.fulfill({ status: 204, body: '' })
    })

    await page.route('**/api/trash/*/*', (route) => {
      if (route.request().method() !== 'DELETE') return route.fallback()
      const m = new URL(route.request().url()).pathname.match(/^\/api\/trash\/[^/]+\/([^/]+)$/)
      if (m) this.trash = this.trash.filter((x) => x.id !== decodeURIComponent(m[1]))
      return route.fulfill({ status: 204, body: '' })
    })

    // ── 공유 ──
    await page.route('**/api/users/search**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            { id: IDS.peer, displayName: '박동료', email: 'peer@example.com' },
          ],
        }),
      }),
    )

    await page.route('**/api/files/*/share', async (route) => {
      const m = new URL(route.request().url()).pathname.match(/^\/api\/files\/([^/]+)\/share$/)
      if (!m || route.request().method() !== 'POST') return route.fallback()
      const fileId = decodeURIComponent(m[1])
      const body = route.request().postDataJSON() as {
        subjects: Array<{ type: string; id?: string }>
        preset: string
      }
      const created = body.subjects.map((s) => {
        const share: MockShare = {
          id: this.nextId(),
          fileId,
          subjectId: s.id ?? '',
          subjectName: '박동료',
          preset: body.preset,
        }
        this.shares.push(share)
        return this.shareWire(share)
      })
      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({ shares: created }),
      })
    })

    await page.route('**/api/shares/by-me**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: this.shares.map((s) => this.shareWire(s)),
          nextCursor: null,
        }),
      }),
    )

    // 사이드바 SharedWithMeSection이 첫 렌더에 호출 — 누락 시 catch-all {}로 크래시 주의
    await page.route('**/api/shares/with-me**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [], nextCursor: null }),
      }),
    )
  }
}

/** 하네스 설치 + 부서 root로 진입까지 한 번에. */
export async function gotoDeptRoot(page: Page): Promise<MockDrive> {
  const mock = new MockDrive()
  await mock.install(page)
  await page.goto(DEPT_ROOT_URL)
  return mock
}
