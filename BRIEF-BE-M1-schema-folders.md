# 작업 브리프 — Backend M1: DB 스키마 + 폴더 엔드포인트

> **Claude Code에게 넘기는 세션 단위 작업 지시서 (백엔드 스트림).**
> 프론트 M1과 병렬로 진행 가능. 완료되면 프론트의 mock API를 실제 API로 교체 가능.

---

## 0. 이 브리프의 사용법

1. 이 파일 전체를 Claude Code 세션 시작 시 열어둡니다.
2. 지시된 문서 섹션만 읽고 구현.
3. §6의 완료 기준 전부 체크되어야 Backend M1 완료.
4. 세션 종료 시 §7의 회고 항목을 `docs/progress.md`에 기록.

---

## 1. 목표

문서관리 시스템의 **DB 기반 + 폴더 조회 API**를 완성합니다.

- PostgreSQL 스키마 (users, departments, folders, files, file_versions, permissions, shares, audit_log)
- 제약 조건 (unique constraint, check, FK)
- 정규화 함수 (프론트와 동일 로직)
- audit_log append-only 권한 설정
- 폴더 트리 조회 API
- 폴더 상세 + breadcrumb API
- 기본 인증 미들웨어 (JWT)
- 시드 데이터 (개발용)

**비범위** (이 마일스톤에서 하지 않음):
- 파일 업로드 API (Backend M2)
- 권한 검증 로직 (Backend M3) — 일단 인증만 하고 전부 허용
- 트랜잭션 로직 (Backend M2)
- 감사 로그 이벤트 기록 (Backend M3)
- 관리자 API (Backend M4 이후)

---

## 2. 기술 스택 결정

```text
Framework: NestJS (TypeScript)
ORM:       Prisma
DB:        PostgreSQL 15+
인증:      JWT (jose 라이브러리)
검증:      class-validator, zod
테스트:    Vitest + supertest
```

> NestJS를 선택한 이유: 의존성 주입, 가드/미들웨어 구조, OpenAPI 자동 생성이 권한/감사 로그 붙이기 좋음.

대안 고려: Spring Boot, FastAPI. 사용자 환경에 맞으면 교체 가능하되, **아래 구현 단계의 의미론은 동일하게 유지**.

---

## 3. 읽어야 할 설계 섹션

**필수 (이 순서대로):**

1. `docs/00-overview.md` §3 문서 간 계약, §6 용어집
2. `CLAUDE.md` §3 절대 깨지 않을 핵심 원칙
3. `docs/02-backend-data-model.md` §1 설계 원칙 (전체)
4. `docs/02-backend-data-model.md` §2.1~§2.8 DB 스키마 (전체)
5. `docs/02-backend-data-model.md` §3 정규화 함수
6. `docs/02-backend-data-model.md` §4 제약조건 요약
7. `docs/02-backend-data-model.md` §7.1, §7.2, §7.3의 폴더 부분만
8. `docs/02-backend-data-model.md` §8 에러 코드

**참고 (필요 시):**

- `docs/02-backend-data-model.md` §5 저장소 정책 (업로드는 Backend M2)
- `docs/02-backend-data-model.md` §10 마이그레이션 전략

**읽지 말 것** (이번 세션 범위 아님):

- §6 트랜잭션 (Backend M2)
- §9 성능 최적화 (여유 있을 때)
- 01 (프론트), 03, 04 전체

---

## 4. 작업 목록

### 4.1 프로젝트 셋업

```bash
# NestJS 프로젝트 생성
pnpm dlx @nestjs/cli new backend --package-manager pnpm
cd backend

# 핵심 의존성
pnpm add @nestjs/config @nestjs/jwt @prisma/client
pnpm add class-validator class-transformer zod
pnpm add bcrypt jose

# 개발 의존성
pnpm add -D prisma @types/bcrypt vitest supertest @types/supertest
```

프로젝트 구조:

```text
backend/
├─ prisma/
│  ├─ schema.prisma
│  ├─ migrations/
│  └─ seed.ts
├─ src/
│  ├─ main.ts
│  ├─ app.module.ts
│  ├─ common/
│  │  ├─ normalize.ts       ← 프론트와 동일
│  │  ├─ errors.ts          ← 에러 코드 상수
│  │  ├─ guards/
│  │  └─ filters/
│  ├─ auth/
│  │  ├─ auth.module.ts
│  │  ├─ auth.guard.ts
│  │  └─ auth.service.ts
│  ├─ folders/
│  │  ├─ folders.module.ts
│  │  ├─ folders.controller.ts
│  │  ├─ folders.service.ts
│  │  └─ dto/
│  └─ prisma/
│     └─ prisma.service.ts
├─ test/
└─ .env.example
```

### 4.2 환경 변수

`.env.example`:

```bash
DATABASE_URL="postgresql://app_user:dev@localhost:5432/docmgmt"
DATABASE_URL_ADMIN="postgresql://postgres:postgres@localhost:5432/docmgmt"
JWT_SECRET="dev-secret-change-in-production-at-least-32-chars"
JWT_EXPIRES_IN="1h"
PORT=3001
NODE_ENV=development
```

> **왜 2개의 DATABASE_URL**: `app_user`는 audit_log에 UPDATE/DELETE 권한 없음 (§2.8). 마이그레이션은 `admin`으로만 실행.

### 4.3 Docker Compose (개발용 DB)

`docker-compose.yml`:

```yaml
version: '3.8'
services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: docmgmt
    ports:
      - '5432:5432'
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./scripts/init-db.sql:/docker-entrypoint-initdb.d/init.sql

volumes:
  pgdata:
```

`scripts/init-db.sql`:

```sql
-- app_user 생성 (제한된 권한)
CREATE USER app_user WITH PASSWORD 'dev';
GRANT CONNECT ON DATABASE docmgmt TO app_user;

-- LTREE 확장
-- (마이그레이션에서 실행하는 것이 프로덕션에서는 더 안전)
```

### 4.4 Prisma 스키마

`prisma/schema.prisma` — docs/02 §2의 DDL을 Prisma로 변환:

```prisma
generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL_ADMIN")  // 마이그레이션은 admin으로
  directUrl = env("DATABASE_URL_ADMIN")
}

model User {
  id            String       @id @default(uuid()) @db.Uuid
  email         String       @unique @db.VarChar(255)
  name          String       @db.VarChar(100)
  departmentId  String?      @map("department_id") @db.Uuid
  role          String       @default("member") @db.VarChar(50)
  storageQuota  BigInt       @default(10737418240) @map("storage_quota")
  storageUsed   BigInt       @default(0) @map("storage_used")
  isActive      Boolean      @default(true) @map("is_active")
  passwordHash  String       @map("password_hash") @db.VarChar(255)
  createdAt     DateTime     @default(now()) @map("created_at") @db.Timestamptz
  updatedAt     DateTime     @default(now()) @updatedAt @map("updated_at") @db.Timestamptz

  department    Department?  @relation(fields: [departmentId], references: [id])
  ownedFolders  Folder[]     @relation("FolderOwner")
  ownedFiles    File[]       @relation("FileOwner")

  @@index([departmentId])
  @@map("users")
}

model Department {
  id        String       @id @default(uuid()) @db.Uuid
  name      String       @db.VarChar(100)
  parentId  String?      @map("parent_id") @db.Uuid
  createdAt DateTime     @default(now()) @map("created_at") @db.Timestamptz

  parent    Department?  @relation("DepartmentTree", fields: [parentId], references: [id])
  children  Department[] @relation("DepartmentTree")
  users     User[]

  @@map("departments")
}

model Folder {
  id                String    @id @default(uuid()) @db.Uuid
  parentId          String?   @map("parent_id") @db.Uuid
  name              String    @db.VarChar(255)
  normalizedName    String    @map("normalized_name") @db.VarChar(255)
  slug              String    @db.VarChar(255)
  ownerId           String    @map("owner_id") @db.Uuid
  auditLevel        String    @default("standard") @map("audit_level") @db.VarChar(20)
  deletedAt         DateTime? @map("deleted_at") @db.Timestamptz
  purgeAfter        DateTime? @map("purge_after") @db.Timestamptz
  originalParentId  String?   @map("original_parent_id") @db.Uuid
  createdAt         DateTime  @default(now()) @map("created_at") @db.Timestamptz
  updatedAt         DateTime  @default(now()) @updatedAt @map("updated_at") @db.Timestamptz

  parent            Folder?   @relation("FolderTree", fields: [parentId], references: [id])
  children          Folder[]  @relation("FolderTree")
  owner             User      @relation("FolderOwner", fields: [ownerId], references: [id])
  files             File[]

  @@index([parentId])
  @@index([purgeAfter])
  @@map("folders")
}

model File {
  id                String        @id @default(uuid()) @db.Uuid
  folderId          String        @map("folder_id") @db.Uuid
  name              String        @db.VarChar(500)
  normalizedName    String        @map("normalized_name") @db.VarChar(500)
  currentVersionId  String?       @map("current_version_id") @db.Uuid
  ownerId           String        @map("owner_id") @db.Uuid
  sizeBytes         BigInt        @map("size_bytes")
  mimeType          String?       @map("mime_type") @db.VarChar(255)
  deletedAt         DateTime?     @map("deleted_at") @db.Timestamptz
  purgeAfter        DateTime?     @map("purge_after") @db.Timestamptz
  originalFolderId  String?       @map("original_folder_id") @db.Uuid
  createdAt         DateTime      @default(now()) @map("created_at") @db.Timestamptz
  updatedAt         DateTime      @default(now()) @updatedAt @map("updated_at") @db.Timestamptz

  folder            Folder        @relation(fields: [folderId], references: [id])
  owner             User          @relation("FileOwner", fields: [ownerId], references: [id])
  versions          FileVersion[] @relation("FileVersions")
  currentVersion    FileVersion?  @relation("CurrentVersion", fields: [currentVersionId], references: [id])

  @@index([folderId])
  @@index([ownerId])
  @@index([purgeAfter])
  @@map("files")
}

model FileVersion {
  id              String   @id @default(uuid()) @db.Uuid
  fileId          String   @map("file_id") @db.Uuid
  versionNumber   Int      @map("version_number")
  storageKey      String   @unique @map("storage_key") @db.Uuid
  sizeBytes       BigInt   @map("size_bytes")
  checksumSha256  String   @map("checksum_sha256") @db.Char(64)
  mimeType        String?  @map("mime_type") @db.VarChar(255)
  scanStatus      String   @default("pending") @map("scan_status") @db.VarChar(20)
  scanResult      Json?    @map("scan_result")
  uploadedBy      String   @map("uploaded_by") @db.Uuid
  uploadedAt      DateTime @default(now()) @map("uploaded_at") @db.Timestamptz
  comment         String?  @db.VarChar(500)

  file            File     @relation("FileVersions", fields: [fileId], references: [id])
  current         File[]   @relation("CurrentVersion")

  @@unique([fileId, versionNumber])
  @@index([fileId, versionNumber(sort: Desc)])
  @@map("file_versions")
}

model Permission {
  id           String    @id @default(uuid()) @db.Uuid
  resourceType String    @map("resource_type") @db.VarChar(20)
  resourceId   String    @map("resource_id") @db.Uuid
  subjectType  String    @map("subject_type") @db.VarChar(20)
  subjectId    String?   @map("subject_id") @db.Uuid
  preset       String    @db.VarChar(20)
  grantedBy    String    @map("granted_by") @db.Uuid
  expiresAt    DateTime? @map("expires_at") @db.Timestamptz
  createdAt    DateTime  @default(now()) @map("created_at") @db.Timestamptz

  @@index([subjectType, subjectId])
  @@index([resourceType, resourceId])
  @@index([expiresAt])
  @@map("permissions")
}

model AuditLog {
  id            BigInt   @id @default(autoincrement())
  timestamp     DateTime @default(now()) @db.Timestamptz
  actorId       String?  @map("actor_id") @db.Uuid
  actorIp       String?  @map("actor_ip") @db.Inet
  userAgent     String?  @map("user_agent") @db.Text
  eventType     String   @map("event_type") @db.VarChar(50)
  targetType    String   @map("target_type") @db.VarChar(20)
  targetId      String?  @map("target_id") @db.Uuid
  beforeState   Json?    @map("before_state")
  afterState    Json?    @map("after_state")
  metadata      Json?

  @@index([timestamp(sort: Desc)])
  @@index([actorId, timestamp(sort: Desc)])
  @@index([targetType, targetId, timestamp(sort: Desc)])
  @@index([eventType, timestamp(sort: Desc)])
  @@map("audit_log")
}
```

### 4.5 마이그레이션 + 원시 SQL 보강

Prisma가 표현 못하는 부분은 **마이그레이션 파일에 raw SQL 추가**:

```bash
pnpm prisma migrate dev --name init --create-only
```

생성된 마이그레이션 파일 맨 아래에 추가:

```sql
-- docs/02 §2에서 Prisma로 표현 불가한 제약 조건

-- 1. Partial unique index (휴지통 제외)
CREATE UNIQUE INDEX idx_folders_unique_name
  ON folders (parent_id, normalized_name)
  WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX idx_files_unique_name
  ON files (folder_id, normalized_name)
  WHERE deleted_at IS NULL;

-- 2. Permission unique (subject_id NULL 포함)
CREATE UNIQUE INDEX idx_permissions_unique
  ON permissions (
    resource_type, resource_id, subject_type,
    COALESCE(subject_id, '00000000-0000-0000-0000-000000000000'::uuid)
  );

-- 3. CHECK 제약
ALTER TABLE folders ADD CONSTRAINT chk_folders_audit_level
  CHECK (audit_level IN ('standard', 'strict'));
ALTER TABLE folders ADD CONSTRAINT chk_folders_deleted_consistency
  CHECK ((deleted_at IS NULL) = (purge_after IS NULL));

ALTER TABLE files ADD CONSTRAINT chk_files_deleted_consistency
  CHECK ((deleted_at IS NULL) = (purge_after IS NULL));
ALTER TABLE files ADD CONSTRAINT chk_files_size_nonneg
  CHECK (size_bytes >= 0);

ALTER TABLE file_versions ADD CONSTRAINT chk_versions_scan_status
  CHECK (scan_status IN ('pending', 'clean', 'infected', 'error'));
ALTER TABLE file_versions ADD CONSTRAINT chk_versions_number
  CHECK (version_number > 0);

ALTER TABLE permissions ADD CONSTRAINT chk_permissions_resource_type
  CHECK (resource_type IN ('folder', 'file'));
ALTER TABLE permissions ADD CONSTRAINT chk_permissions_subject_type
  CHECK (subject_type IN ('user', 'department', 'role', 'everyone'));
ALTER TABLE permissions ADD CONSTRAINT chk_permissions_preset
  CHECK (preset IN ('read', 'upload', 'edit', 'admin'));
ALTER TABLE permissions ADD CONSTRAINT chk_permissions_subject_consistency
  CHECK ((subject_type = 'everyone') = (subject_id IS NULL));

ALTER TABLE audit_log ADD CONSTRAINT chk_audit_target_type
  CHECK (target_type IN ('file', 'folder', 'user', 'permission', 'share', 'system'));

-- 4. Deferrable FK (순환 참조용)
ALTER TABLE files
  ADD CONSTRAINT fk_files_current_version
  FOREIGN KEY (current_version_id) REFERENCES file_versions(id)
  DEFERRABLE INITIALLY DEFERRED;

-- 5. 정규화 함수
CREATE OR REPLACE FUNCTION normalize_name_for_dedup(input TEXT)
RETURNS TEXT IMMUTABLE LANGUAGE SQL AS $$
  SELECT LOWER(TRIM(NORMALIZE(input, NFC)))
$$;

CREATE OR REPLACE FUNCTION set_normalized_name()
RETURNS TRIGGER LANGUAGE PLPGSQL AS $$
BEGIN
  NEW.normalized_name := normalize_name_for_dedup(NEW.name);
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_files_normalize
  BEFORE INSERT OR UPDATE OF name ON files
  FOR EACH ROW EXECUTE FUNCTION set_normalized_name();

CREATE TRIGGER trg_folders_normalize
  BEFORE INSERT OR UPDATE OF name ON folders
  FOR EACH ROW EXECUTE FUNCTION set_normalized_name();

-- 6. audit_log 권한 제한 (핵심 원칙 #8)
GRANT SELECT, INSERT ON audit_log TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL OTHER TABLES IN SCHEMA public TO app_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_user;
REVOKE UPDATE, DELETE ON audit_log FROM app_user;
```

> **주의**: `GRANT ALL OTHER TABLES` 같은 문법은 없음. 실제로는 테이블별 GRANT 필요:

```sql
GRANT SELECT, INSERT, UPDATE, DELETE ON users TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON departments TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON folders TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON files TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON file_versions TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON permissions TO app_user;
-- audit_log: INSERT, SELECT만
GRANT SELECT, INSERT ON audit_log TO app_user;
GRANT USAGE, SELECT ON SEQUENCE audit_log_id_seq TO app_user;
```

마이그레이션 실행:

```bash
pnpm prisma migrate dev
```

### 4.6 정규화 라이브러리

`src/common/normalize.ts` — **프론트와 100% 동일**:

```ts
export function normalizeFileName(s: string): string {
  return s.normalize('NFC').trim()
}

export function normalizedNameForDedup(s: string): string {
  return s.normalize('NFC').toLowerCase().trim()
}

export function normalizeForSearch(s: string): string {
  return s.normalize('NFC').toLowerCase().trim().replace(/\s+/g, ' ')
}
```

**테스트 필수** (프론트와 동일 결과 보장):

`src/common/normalize.spec.ts`:

```ts
import { describe, it, expect } from 'vitest'
import {
  normalizeFileName,
  normalizedNameForDedup,
  normalizeForSearch,
} from './normalize'

describe('normalize', () => {
  it('NFC 정규화', () => {
    // macOS가 자주 쓰는 NFD 입력 → NFC로
    const nfd = '가'.normalize('NFD')
    expect(normalizeFileName(nfd)).toBe('가')
  })

  it('trim 적용', () => {
    expect(normalizeFileName('  test.pdf  ')).toBe('test.pdf')
  })

  it('중복 검사용은 lowercase', () => {
    expect(normalizedNameForDedup('Document.PDF')).toBe('document.pdf')
  })

  it('검색 정규화는 공백 압축', () => {
    expect(normalizeForSearch('  foo   bar  ')).toBe('foo bar')
  })

  it('한글 + 영문 혼합', () => {
    expect(normalizedNameForDedup('계약서 Final.docx')).toBe('계약서 final.docx')
  })
})
```

### 4.7 에러 코드

`src/common/errors.ts` — docs/02 §8:

```ts
export const ErrorCode = {
  VALIDATION_ERROR: { http: 400, code: 'VALIDATION_ERROR' },
  UNAUTHORIZED: { http: 401, code: 'UNAUTHORIZED' },
  PERMISSION_DENIED: { http: 403, code: 'PERMISSION_DENIED' },
  NOT_FOUND: { http: 404, code: 'NOT_FOUND' },
  UPLOAD_CONFLICT: { http: 409, code: 'UPLOAD_CONFLICT' },
  RENAME_CONFLICT: { http: 409, code: 'RENAME_CONFLICT' },
  RESTORE_CONFLICT: { http: 409, code: 'RESTORE_CONFLICT' },
  VERSION_CONFLICT: { http: 409, code: 'VERSION_CONFLICT' },
  QUOTA_EXCEEDED: { http: 413, code: 'QUOTA_EXCEEDED' },
  FILE_TOO_LARGE: { http: 413, code: 'FILE_TOO_LARGE' },
  UNSUPPORTED_MEDIA_TYPE: { http: 415, code: 'UNSUPPORTED_MEDIA_TYPE' },
  LOCKED: { http: 423, code: 'LOCKED' },
  RATE_LIMIT_EXCEEDED: { http: 429, code: 'RATE_LIMIT_EXCEEDED' },
  INTERNAL_ERROR: { http: 500, code: 'INTERNAL_ERROR' },
} as const

export class AppError extends Error {
  constructor(
    public readonly error: (typeof ErrorCode)[keyof typeof ErrorCode],
    public readonly details?: unknown,
    message?: string
  ) {
    super(message ?? error.code)
  }
}
```

`src/common/filters/app-error.filter.ts` — 전역 예외 필터:

```ts
import { ExceptionFilter, Catch, ArgumentsHost } from '@nestjs/common'
import { Response, Request } from 'express'
import { AppError } from '../errors'

@Catch(AppError)
export class AppErrorFilter implements ExceptionFilter {
  catch(exception: AppError, host: ArgumentsHost) {
    const ctx = host.switchToHttp()
    const res = ctx.getResponse<Response>()
    const req = ctx.getRequest<Request>()

    res.status(exception.error.http).json({
      error: {
        code: exception.error.code,
        message: exception.message,
        details: exception.details,
      },
      requestId: req.header('x-request-id'),
    })
  }
}
```

### 4.8 인증 모듈 (최소 구현)

`src/auth/auth.guard.ts`:

```ts
import { CanActivate, ExecutionContext, Injectable } from '@nestjs/common'
import { JwtService } from '@nestjs/jwt'
import { AppError, ErrorCode } from '../common/errors'

@Injectable()
export class AuthGuard implements CanActivate {
  constructor(private jwt: JwtService) {}

  async canActivate(ctx: ExecutionContext): Promise<boolean> {
    const req = ctx.switchToHttp().getRequest()
    const header = req.headers.authorization
    if (!header?.startsWith('Bearer ')) {
      throw new AppError(ErrorCode.UNAUTHORIZED)
    }
    try {
      const token = header.slice(7)
      const payload = await this.jwt.verifyAsync(token)
      req.user = { id: payload.sub, email: payload.email, role: payload.role }
      return true
    } catch {
      throw new AppError(ErrorCode.UNAUTHORIZED)
    }
  }
}
```

`src/auth/auth.service.ts` — 로그인만:

```ts
import { Injectable } from '@nestjs/common'
import { JwtService } from '@nestjs/jwt'
import { PrismaService } from '../prisma/prisma.service'
import { AppError, ErrorCode } from '../common/errors'
import bcrypt from 'bcrypt'

@Injectable()
export class AuthService {
  constructor(
    private prisma: PrismaService,
    private jwt: JwtService,
  ) {}

  async login(email: string, password: string) {
    const user = await this.prisma.user.findUnique({ where: { email } })
    if (!user || !user.isActive) {
      throw new AppError(ErrorCode.UNAUTHORIZED, null, 'Invalid credentials')
    }
    const ok = await bcrypt.compare(password, user.passwordHash)
    if (!ok) {
      throw new AppError(ErrorCode.UNAUTHORIZED, null, 'Invalid credentials')
    }
    const token = await this.jwt.signAsync({
      sub: user.id,
      email: user.email,
      role: user.role,
    })
    return { token, user: { id: user.id, email: user.email, name: user.name } }
  }
}
```

`src/auth/auth.controller.ts`:

```ts
import { Body, Controller, Post } from '@nestjs/common'
import { AuthService } from './auth.service'
import { IsEmail, IsString, MinLength } from 'class-validator'

class LoginDto {
  @IsEmail() email: string
  @IsString() @MinLength(1) password: string
}

@Controller('api/auth')
export class AuthController {
  constructor(private auth: AuthService) {}

  @Post('login')
  login(@Body() dto: LoginDto) {
    return this.auth.login(dto.email, dto.password)
  }
}
```

### 4.9 폴더 서비스

`src/folders/folders.service.ts`:

```ts
import { Injectable } from '@nestjs/common'
import { PrismaService } from '../prisma/prisma.service'
import { AppError, ErrorCode } from '../common/errors'

@Injectable()
export class FoldersService {
  constructor(private prisma: PrismaService) {}

  async getTree(userId: string) {
    // M1: 전체 반환 (권한 필터링은 Backend M3)
    // 단일 query로 모든 폴더 + 재귀 CTE로 트리 구조
    const folders = await this.prisma.folder.findMany({
      where: { deletedAt: null },
      orderBy: { name: 'asc' },
      select: {
        id: true,
        parentId: true,
        name: true,
        slug: true,
      },
    })

    return this.buildTree(folders)
  }

  private buildTree(flat: Array<{ id: string; parentId: string | null; name: string; slug: string }>) {
    const byId = new Map(flat.map((f) => [f.id, { ...f, children: [] as any[] }]))
    let root: any = null
    for (const f of byId.values()) {
      if (f.parentId === null) {
        root = f
      } else {
        byId.get(f.parentId)?.children.push(f)
      }
    }
    return root
  }

  async getDetail(folderId: string, userId: string) {
    const folder = await this.prisma.folder.findFirst({
      where: { id: folderId, deletedAt: null },
    })
    if (!folder) throw new AppError(ErrorCode.NOT_FOUND)

    // Breadcrumb: 재귀 CTE로 조상 체인 조회
    const chain = await this.prisma.$queryRaw<
      Array<{ id: string; name: string; slug: string; depth: number }>
    >`
      WITH RECURSIVE ancestors AS (
        SELECT id, parent_id, name, slug, 0 AS depth
          FROM folders
         WHERE id = ${folderId}::uuid
        UNION ALL
        SELECT f.id, f.parent_id, f.name, f.slug, a.depth + 1
          FROM folders f
          JOIN ancestors a ON a.parent_id = f.id
      )
      SELECT id, name, slug, depth FROM ancestors
       ORDER BY depth DESC
    `

    // slug path (root 제외)
    const slugPath = chain.slice(1).map((c) => c.slug)

    const breadcrumb = chain.map((c, i) => ({
      id: c.id,
      name: c.name,
      slugPath: chain.slice(1, i + 1).map((x) => x.slug),
    }))

    return {
      id: folder.id,
      name: folder.name,
      slugPath,
      breadcrumb,
      parentId: folder.parentId,
    }
  }
}
```

### 4.10 폴더 컨트롤러

`src/folders/folders.controller.ts`:

```ts
import { Controller, Get, Param, UseGuards, Req } from '@nestjs/common'
import { FoldersService } from './folders.service'
import { AuthGuard } from '../auth/auth.guard'

@Controller('api/folders')
@UseGuards(AuthGuard)
export class FoldersController {
  constructor(private folders: FoldersService) {}

  @Get('tree')
  tree(@Req() req: any) {
    return this.folders.getTree(req.user.id)
  }

  @Get(':id')
  detail(@Param('id') id: string, @Req() req: any) {
    return this.folders.getDetail(id, req.user.id)
  }
}
```

### 4.11 메인 앱

`src/app.module.ts`:

```ts
import { Module } from '@nestjs/common'
import { ConfigModule } from '@nestjs/config'
import { JwtModule } from '@nestjs/jwt'
import { PrismaService } from './prisma/prisma.service'
import { AuthController } from './auth/auth.controller'
import { AuthService } from './auth/auth.service'
import { FoldersController } from './folders/folders.controller'
import { FoldersService } from './folders/folders.service'

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    JwtModule.register({
      global: true,
      secret: process.env.JWT_SECRET,
      signOptions: { expiresIn: process.env.JWT_EXPIRES_IN ?? '1h' },
    }),
  ],
  controllers: [AuthController, FoldersController],
  providers: [PrismaService, AuthService, FoldersService],
})
export class AppModule {}
```

`src/main.ts`:

```ts
import { NestFactory } from '@nestjs/core'
import { ValidationPipe } from '@nestjs/common'
import { AppModule } from './app.module'
import { AppErrorFilter } from './common/filters/app-error.filter'

async function bootstrap() {
  const app = await NestFactory.create(AppModule)

  app.enableCors({
    origin: ['http://localhost:3000'],
    credentials: true,
  })

  app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }))
  app.useGlobalFilters(new AppErrorFilter())

  await app.listen(process.env.PORT ?? 3001)
}
bootstrap()
```

### 4.12 시드 데이터

`prisma/seed.ts`:

```ts
import { PrismaClient } from '@prisma/client'
import bcrypt from 'bcrypt'

const prisma = new PrismaClient()

async function main() {
  // 부서
  const hq = await prisma.department.upsert({
    where: { id: '00000000-0000-0000-0000-000000000001' },
    update: {},
    create: { id: '00000000-0000-0000-0000-000000000001', name: '본사' },
  })

  // 사용자
  const passwordHash = await bcrypt.hash('password123', 10)
  const alice = await prisma.user.upsert({
    where: { email: 'alice@example.com' },
    update: {},
    create: {
      email: 'alice@example.com',
      name: '앨리스',
      passwordHash,
      role: 'admin',
      departmentId: hq.id,
    },
  })

  // 루트 폴더
  const root = await prisma.folder.upsert({
    where: { id: '00000000-0000-0000-0000-000000000000' },
    update: {},
    create: {
      id: '00000000-0000-0000-0000-000000000000',
      parentId: null,
      name: '내 드라이브',
      slug: '',
      normalizedName: '내 드라이브',
      ownerId: alice.id,
    },
  })

  // 하위 폴더
  const sales = await prisma.folder.create({
    data: {
      parentId: root.id,
      name: '영업팀',
      slug: '영업팀',
      normalizedName: '영업팀',
      ownerId: alice.id,
    },
  })

  await prisma.folder.create({
    data: {
      parentId: sales.id,
      name: '계약서',
      slug: '계약서',
      normalizedName: '계약서',
      ownerId: alice.id,
    },
  })

  await prisma.folder.create({
    data: {
      parentId: root.id,
      name: '인사팀',
      slug: '인사팀',
      normalizedName: '인사팀',
      ownerId: alice.id,
    },
  })

  console.log('✅ Seed complete')
}

main().finally(() => prisma.$disconnect())
```

실행:

```bash
pnpm prisma db seed
```

### 4.13 통합 테스트

`test/folders.e2e.spec.ts`:

```ts
import { describe, it, beforeAll, afterAll, expect } from 'vitest'
import { Test } from '@nestjs/testing'
import { INestApplication } from '@nestjs/common'
import request from 'supertest'
import { AppModule } from '../src/app.module'

describe('Folders API', () => {
  let app: INestApplication
  let token: string

  beforeAll(async () => {
    const mod = await Test.createTestingModule({ imports: [AppModule] }).compile()
    app = mod.createNestApplication()
    await app.init()

    const res = await request(app.getHttpServer())
      .post('/api/auth/login')
      .send({ email: 'alice@example.com', password: 'password123' })
    token = res.body.token
  })

  afterAll(async () => app.close())

  it('GET /api/folders/tree — 인증 없이 401', async () => {
    await request(app.getHttpServer()).get('/api/folders/tree').expect(401)
  })

  it('GET /api/folders/tree — 루트 반환', async () => {
    const res = await request(app.getHttpServer())
      .get('/api/folders/tree')
      .set('Authorization', `Bearer ${token}`)
      .expect(200)

    expect(res.body.name).toBe('내 드라이브')
    expect(res.body.children).toBeInstanceOf(Array)
    expect(res.body.children.length).toBeGreaterThan(0)
  })

  it('GET /api/folders/:id — breadcrumb 포함', async () => {
    const tree = await request(app.getHttpServer())
      .get('/api/folders/tree')
      .set('Authorization', `Bearer ${token}`)

    const sales = tree.body.children.find((c: any) => c.name === '영업팀')
    const contracts = sales.children[0]

    const res = await request(app.getHttpServer())
      .get(`/api/folders/${contracts.id}`)
      .set('Authorization', `Bearer ${token}`)
      .expect(200)

    expect(res.body.name).toBe('계약서')
    expect(res.body.slugPath).toEqual(['영업팀', '계약서'])
    expect(res.body.breadcrumb.length).toBe(3)
    expect(res.body.breadcrumb[0].name).toBe('내 드라이브')
  })

  it('GET /api/folders/:id — 존재하지 않는 id → 404', async () => {
    await request(app.getHttpServer())
      .get('/api/folders/00000000-0000-0000-0000-999999999999')
      .set('Authorization', `Bearer ${token}`)
      .expect(404)
  })
})
```

### 4.14 audit_log 권한 검증 테스트

**이건 꼭 자동화해야 합니다.** 원칙 #8이 실제로 작동하는지 확인:

`test/audit-immutable.spec.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { PrismaClient } from '@prisma/client'

describe('audit_log immutability (core principle #8)', () => {
  const appClient = new PrismaClient({
    datasources: { db: { url: process.env.DATABASE_URL } }, // app_user
  })

  it('INSERT는 성공', async () => {
    const r = await appClient.auditLog.create({
      data: {
        eventType: 'test.insert',
        targetType: 'system',
      },
    })
    expect(r.id).toBeDefined()
  })

  it('UPDATE는 실패해야 함', async () => {
    const r = await appClient.auditLog.create({
      data: { eventType: 'test.update', targetType: 'system' },
    })
    await expect(
      appClient.auditLog.update({
        where: { id: r.id },
        data: { eventType: 'modified' },
      })
    ).rejects.toThrow(/permission denied/i)
  })

  it('DELETE는 실패해야 함', async () => {
    const r = await appClient.auditLog.create({
      data: { eventType: 'test.delete', targetType: 'system' },
    })
    await expect(
      appClient.auditLog.delete({ where: { id: r.id } })
    ).rejects.toThrow(/permission denied/i)
  })
})
```

---

## 5. 핵심 원칙 체크 (구현 중 지속 확인)

- [ ] 마이그레이션에 `UNIQUE ... WHERE deleted_at IS NULL` partial index 포함?
- [ ] `REVOKE UPDATE, DELETE ON audit_log FROM app_user` 실행됐고, 테스트로 검증됐는가?
- [ ] `normalize_name_for_dedup` 함수와 트리거가 설치됐는가?
- [ ] `files.current_version_id` FK가 DEFERRABLE인가?
- [ ] `src/common/normalize.ts`가 프론트 `src/lib/normalize.ts`와 **완전 동일**한가?
- [ ] `DATABASE_URL`(app_user)과 `DATABASE_URL_ADMIN`이 분리돼 있는가?
- [ ] 에러 응답이 docs/02 §8.2 포맷을 따르는가? (`{error: {code, message, details}, requestId}`)

---

## 6. 완료 기준

모두 체크되면 Backend M1 완료:

- [ ] `docker-compose up` → PostgreSQL 기동
- [ ] `pnpm prisma migrate dev` → 모든 테이블, 제약, 트리거, 권한 설정 완료
- [ ] `pnpm prisma db seed` → 개발 데이터 생성
- [ ] `POST /api/auth/login` → JWT 토큰 반환
- [ ] `GET /api/folders/tree` → 트리 반환 (인증 필요)
- [ ] `GET /api/folders/:id` → breadcrumb + slugPath 포함한 상세
- [ ] `GET /api/folders/not-exist-id` → 404 + `{error: {code: 'NOT_FOUND'}}`
- [ ] 한글 폴더명 정상 저장/조회
- [ ] `pnpm test` — 모든 테스트 통과 (normalize, folders, audit immutability)
- [ ] `pnpm typecheck` 통과
- [ ] audit_log UPDATE/DELETE 시도 → `permission denied` 에러 (DB 레벨)
- [ ] 동일 parent_id에 동일 normalized_name 폴더 INSERT 시도 → unique violation

---

## 7. 세션 종료 시 회고 (progress.md에 기록)

```markdown
## YYYY-MM-DD — Backend M1 완료

### 완료
- [BE-M1] Prisma 스키마 + 마이그레이션 (8개 테이블)
- [BE-M1] 제약 조건 (partial unique, check, deferrable FK)
- [BE-M1] 정규화 함수 + 트리거
- [BE-M1] audit_log 권한 제한 (DB 레벨 append-only)
- [BE-M1] JWT 인증 + AuthGuard
- [BE-M1] GET /api/folders/tree, GET /api/folders/:id
- [BE-M1] 에러 응답 표준 포맷
- [BE-M1] 시드 데이터
- [BE-M1] 통합 테스트 (folders, audit immutability)

### 계약 파일
- src/common/normalize.ts  (프론트와 동일, docs/02 §3)
- src/common/errors.ts      (docs/02 §8)
- prisma/schema.prisma      (docs/02 §2)

### 다음 세션 (Backend M2: 파일 업로드 + 트랜잭션)
- multipart 업로드 엔드포인트
- 트랜잭션 기반 동시성 제어 (docs/02 §6.1)
- 409 UPLOAD_CONFLICT 응답
- S3 클라이언트 설정 (또는 MinIO)

### 프론트 팀에게 전달
- Base URL: http://localhost:3001
- 로그인: POST /api/auth/login { email, password } → { token, user }
- 헤더: Authorization: Bearer <token>
- 프론트 mock API를 실제 API로 교체 가능. src/lib/api.ts 수정

### 블로커
- 없음
```

---

## 8. 자주 발생하는 함정

1. **Prisma가 partial index 미지원**: `@@unique`로 표현 못함. raw SQL 마이그레이션 필수.
2. **DEFERRABLE FK**: files ↔ file_versions 순환 참조. 트랜잭션 안에서만 일시적 위반 허용. 마이그레이션에 DEFERRABLE 붙이지 않으면 시드 데이터조차 안 들어감.
3. **audit_log 권한 테스트 실수**: `postgres` 유저로 테스트하면 당연히 통과. 반드시 `app_user`로 연결한 PrismaClient로 테스트.
4. **BigInt 직렬화**: NestJS는 기본 `BigInt` → JSON 직렬화 실패. `app.module.ts`에 `BigInt.prototype.toJSON = function() { return this.toString() }` 추가.
5. **한글 slug의 trim 문제**: DB normalize 함수와 애플리케이션 normalize 함수가 미묘하게 달라 트리거가 다른 값을 만들 수 있음. 마이그레이션 후 `SELECT name, normalized_name FROM folders` 로 실제 값 확인.
6. **CORS**: 프론트(3000) ↔ 백엔드(3001). `enableCors` 설정 필수. `credentials: true`까지.

---

## 9. 참고: 다음 백엔드 마일스톤 미리보기

Backend M2+ 후보:

- **BE-M2**: 파일 업로드 (multipart + 트랜잭션 + 409 처리)
- **BE-M3**: 권한 시스템 (매트릭스 + requirePermission 미들웨어 + 감사 이벤트 기록)
- **BE-M4**: 이동/이름변경/삭제 (트랜잭션)
- **BE-M5**: 버전 관리 + optimistic concurrency
- **BE-M6**: 검색 (tsvector)
- **BE-M7**: 관리자 API

프론트/백엔드 마일스톤 동기화:

```text
프론트 M1 ↔ 백엔드 M1  (라우팅 ↔ 폴더 API)     ← 이 세션
프론트 M2 ↔ 백엔드 M1  (Sidebar, 백엔드 변경 없음)
프론트 M3 ↔ 백엔드 M3  (FileTable ↔ 권한 + 파일 조회)
프론트 M4 ↔ 백엔드 -    (선택 모델)
프론트 M5 ↔ 백엔드 M2  (업로드 UI ↔ 업로드 API)
...
```
