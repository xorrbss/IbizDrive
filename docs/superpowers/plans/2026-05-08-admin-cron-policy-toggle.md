# admin-cron-policy-toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ADMIN이 `/admin/system` 페이지에서 운영 cron 4종(`purge.expired`/`share.expire`/`permission.expire`/`storage.orphan.cleanup`)의 enabled 여부를 재기동 없이 토글한다. 토글은 신규 `cron_policy` 테이블에 영구 저장되고 audit_log `admin.cron.toggled`로 추적된다.

**Architecture:** V11 마이그레이션이 4 row 시드. cron job 진입부에 `cronPolicyRepository.isEnabled(KEY)` 가드. Controller `PUT /api/admin/system/cron/{key}` (ADMIN-only) → Service 트랜잭션 + AFTER_COMMIT REQUIRES_NEW listener → audit_log emit. Frontend `useMe()`로 ADMIN 체크 후 `<CronCard>`에 토글 switch + ConfirmDialog 노출.

**Tech Stack:** Spring Boot 3 / JPA / Flyway / Spring Scheduling / `@WebMvcTest` + JUnit 5 + Testcontainers / Next.js 15 + React Query + Vitest + Testing Library. 작업 디렉토리: BE는 `cd backend && ./gradlew ...`, FE는 `cd frontend && npm run ...`.

**설계 근거:** `docs/superpowers/specs/2026-05-08-admin-cron-policy-toggle-design.md`

---

## 파일 구조

### 신규 파일 (backend)

| 경로 | 책임 |
|---|---|
| `backend/src/main/resources/db/migration/V11__cron_policy.sql` | cron_policy 테이블 + 4 row 시드 |
| `backend/src/main/java/com/ibizdrive/admin/CronPolicy.java` | JPA `@Entity` (key/enabled/updatedAt/updatedBy) |
| `backend/src/main/java/com/ibizdrive/admin/CronPolicyRepository.java` | `JpaRepository` + 헬퍼 `isEnabled(String key)` |
| `backend/src/main/java/com/ibizdrive/admin/AdminCronToggledEvent.java` | record(actorId, ip, userAgent, jobKey, fromEnabled, toEnabled) |
| `backend/src/main/java/com/ibizdrive/admin/AdminCronToggledListener.java` | `@TransactionalEventListener(AFTER_COMMIT)` audit_log emit |
| `backend/src/main/java/com/ibizdrive/admin/AdminSystemService.java` | `toggleCron(key, enabled, actor)` 트랜잭션 메서드 |
| `backend/src/main/java/com/ibizdrive/admin/AdminCronToggleRequest.java` | record `{Boolean enabled}` (request body DTO + Bean Validation) |
| `backend/src/test/java/com/ibizdrive/admin/CronPolicyRepositoryTest.java` | `@DataJpaTest` slice |
| `backend/src/test/java/com/ibizdrive/admin/AdminCronToggledListenerTest.java` | listener 단위 |
| `backend/src/test/java/com/ibizdrive/admin/AdminSystemServiceToggleCronTest.java` | service 단위 (mock event publisher + repo) |
| `backend/src/test/java/com/ibizdrive/admin/AdminCronToggleE2ETest.java` | login → toggle → audit_log 검증 (Testcontainers) |

### 수정 파일 (backend)

| 경로 | 변경 |
|---|---|
| `backend/src/main/java/com/ibizdrive/admin/AdminSystemController.java` | `PUT /api/admin/system/cron/{key}` 추가 + service 의존성 주입 |
| `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` | `ADMIN_CRON_TOGGLED("admin.cron.toggled")` enum 추가 (cumulative 47 → 48) |
| `backend/src/main/java/com/ibizdrive/purge/HardPurgeJob.java` | `@ConditionalOnProperty` 제거 + 메서드 첫 줄 isEnabled 가드 |
| `backend/src/main/java/com/ibizdrive/share/ShareExpirationJob.java` | 동일 |
| `backend/src/main/java/com/ibizdrive/permission/PermissionExpirationJob.java` | 동일 |
| `backend/src/main/java/com/ibizdrive/storage/StorageOrphanCleanupJob.java` | 동일 |
| `backend/src/test/java/com/ibizdrive/admin/AdminSystemControllerTest.java` | (신규 또는 기존) — PUT slice 케이스 추가 |

### 신규 파일 (frontend)

| 경로 | 책임 |
|---|---|
| `frontend/src/lib/api.adminToggleCron.test.ts` | URL/method/body 단위 테스트 |

### 수정 파일 (frontend)

| 경로 | 변경 |
|---|---|
| `frontend/src/lib/api.ts` | `adminToggleCron(key, enabled): Promise<void>` 추가 |
| `frontend/src/hooks/useAdminSystem.ts` | `useAdminToggleCron()` mutation hook 추가 |
| `frontend/src/app/admin/system/page.tsx` | `CronCard`에 ADMIN-only 토글 switch + ConfirmDialog (inline) |
| `frontend/src/app/admin/system/page.test.tsx` | (신규 또는 기존) — ADMIN 노출/AUDITOR 미노출/토글 호출 검증 |
| `frontend/src/types/audit.ts` | `'admin.cron.toggled'` 추가 |

### 수정 파일 (docs)

| 경로 | 변경 |
|---|---|
| `docs/03-security-compliance.md` §4.1 | audit enum 47 → 48 + admin.cron.toggled 행 |
| `docs/04-admin-operations.md` §15.4 | "yml 편집 + 재기동" → "UI 토글 + 영구 저장 + audit 추적" |
| `BETA-RELEASE.md` §7 | "cron 4종 런타임 토글 미지원" 라인 제거 + Source 체인에 admin-cron-toggle 추가 |
| `docs/progress.md` | 트랙 closure 엔트리 (최상단) |
| `dev/active/admin-cron-toggle/README.md` | 트랙 메타 |

---

## Pre-flight: dev/active 부트스트랩

**Files:**
- Create: `dev/active/admin-cron-toggle/README.md`

- [ ] **Step 1: dev/active 디렉토리 + README 생성**

`dev/active/admin-cron-toggle/README.md`:

```markdown
# admin-cron-toggle (Wave 2 closure 후속)

- spec: docs/superpowers/specs/2026-05-08-admin-cron-policy-toggle-design.md
- plan: docs/superpowers/plans/2026-05-08-admin-cron-policy-toggle.md
- 시작: 2026-05-08
- 머지 후 dev/completed/ 로 이동
```

- [ ] **Step 2: 커밋**

```bash
cd C:/project/IbizDrive/.claude/worktrees/admin-cron-toggle
git add dev/active/admin-cron-toggle/README.md
git commit -m "chore(admin-cron-toggle): bootstrap dev directory"
```

---

## P1 (BE-1): V11 마이그레이션 + CronPolicy entity + Repository

**Files:**
- Create: `backend/src/main/resources/db/migration/V11__cron_policy.sql`
- Create: `backend/src/main/java/com/ibizdrive/admin/CronPolicy.java`
- Create: `backend/src/main/java/com/ibizdrive/admin/CronPolicyRepository.java`
- Create: `backend/src/test/java/com/ibizdrive/admin/CronPolicyRepositoryTest.java`

### Task P1.1: 마이그레이션 + entity 작성 (RED 없이 schema 먼저 — TDD 외)

- [ ] **Step 1: V11 마이그레이션 작성**

`backend/src/main/resources/db/migration/V11__cron_policy.sql`:

```sql
-- Wave 2 closure 후속(admin-cron-policy-toggle): cron 4종 enabled 토글을 DB로 영구 저장.
-- application.yml의 app.*.enabled는 시드 후 cron 동작에 영향 없음 (cleanup은 v1.x).
CREATE TABLE cron_policy (
    key          VARCHAR(64)  PRIMARY KEY,
    enabled      BOOLEAN      NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by   UUID         REFERENCES users(id) ON DELETE SET NULL
);

-- 4종 시드. updated_by=NULL은 system seed 표식.
INSERT INTO cron_policy (key, enabled) VALUES
    ('purge.expired',          false),
    ('share.expire',           false),
    ('permission.expire',      false),
    ('storage.orphan.cleanup', false);
```

- [ ] **Step 2: CronPolicy entity 작성**

`backend/src/main/java/com/ibizdrive/admin/CronPolicy.java`:

```java
package com.ibizdrive.admin;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Cron 운영 정책 — admin-cron-policy-toggle 트랙 (Wave 2 closure 후속).
 *
 * <p>4 row만 존재하는 정적 테이블 (V11 마이그레이션 시드). PK는 cron 식별자
 * ({@code purge.expired} / {@code share.expire} / {@code permission.expire} /
 * {@code storage.orphan.cleanup}) — application.yml의 식별자와 동일.
 *
 * <p>{@code enabled}는 매 cron tick 진입 시 lookup된다 (in-tick guard). 변경은
 * {@link AdminSystemService#toggleCron}만 거치며 audit_log {@code admin.cron.toggled}로 추적.
 */
@Entity
@Table(name = "cron_policy")
public class CronPolicy {

    @Id
    @Column(name = "key", length = 64, nullable = false)
    private String key;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected CronPolicy() {}

    public CronPolicy(String key, boolean enabled, Instant updatedAt, UUID updatedBy) {
        this.key = key;
        this.enabled = enabled;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public String getKey() { return key; }
    public boolean isEnabled() { return enabled; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }

    public void update(boolean enabled, UUID actorId) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}
```

- [ ] **Step 3: Repository 작성**

`backend/src/main/java/com/ibizdrive/admin/CronPolicyRepository.java`:

```java
package com.ibizdrive.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * {@link CronPolicy} JPA repository — cron tick guard와 admin toggle endpoint 양쪽에서 사용.
 *
 * <p>{@link #isEnabled(String)}은 cron tick(5분/자정/새벽 1시 단위)마다 호출되므로 single-row
 * lookup. 4-row 테이블 + PK 인덱스라 비용 미미.
 */
@Repository
public interface CronPolicyRepository extends JpaRepository<CronPolicy, String> {

    /**
     * 주어진 cron 키의 enabled 값. row가 없으면 false (방어 — V11 시드 후엔 발생 안 해야 함).
     */
    @Query("SELECT COALESCE(MAX(CASE WHEN c.enabled = true THEN 1 ELSE 0 END), 0) " +
           "FROM CronPolicy c WHERE c.key = :key")
    int isEnabledRaw(@Param("key") String key);

    default boolean isEnabled(String key) {
        return isEnabledRaw(key) == 1;
    }
}
```

> 주: `JpaRepository.findById(key).map(CronPolicy::isEnabled).orElse(false)`도 가능하지만 위 query는 SELECT 1회. 단순.

### Task P1.2: Repository slice 테스트 (V11 시드 검증 + isEnabled + update)

- [ ] **Step 1: 테스트 작성**

`backend/src/test/java/com/ibizdrive/admin/CronPolicyRepositoryTest.java`:

```java
package com.ibizdrive.admin;

import com.ibizdrive.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V11 시드(4 row) + isEnabled query + update 동작 검증.
 *
 * <p>본 프로젝트의 다른 repository slice 테스트(`AdminTrashRepositoryTest` 등) 패턴을 따른다.
 * `AbstractIntegrationTest`가 Testcontainers Postgres + Flyway로 schema를 띄운다.
 */
class CronPolicyRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private CronPolicyRepository repository;

    @Test
    void v11SeedsFourRowsAllDisabled() {
        assertThat(repository.count()).isEqualTo(4);
        assertThat(repository.isEnabled("purge.expired")).isFalse();
        assertThat(repository.isEnabled("share.expire")).isFalse();
        assertThat(repository.isEnabled("permission.expire")).isFalse();
        assertThat(repository.isEnabled("storage.orphan.cleanup")).isFalse();
    }

    @Test
    void unknownKeyReturnsFalseDefensively() {
        assertThat(repository.isEnabled("does.not.exist")).isFalse();
    }

    @Test
    void updateFlipsEnabled() {
        CronPolicy p = repository.findById("purge.expired").orElseThrow();
        p.update(true, UUID.randomUUID());
        repository.saveAndFlush(p);
        assertThat(repository.isEnabled("purge.expired")).isTrue();
    }

    @Test
    void updatedAtAdvancesOnFlip() {
        CronPolicy p = repository.findById("share.expire").orElseThrow();
        Instant before = p.getUpdatedAt();
        p.update(true, UUID.randomUUID());
        repository.saveAndFlush(p);
        CronPolicy after = repository.findById("share.expire").orElseThrow();
        assertThat(after.getUpdatedAt()).isAfter(before);
    }
}
```

> 주: `AbstractIntegrationTest`의 정확한 클래스명/패키지는 기존 repo의 다른 slice 테스트(예: `AdminTrashRepositoryTest`)를 grep해서 확인. 동일 base class를 import.

- [ ] **Step 2: 테스트 실행 — RED 확인**

Run: `cd backend && ./gradlew test --tests CronPolicyRepositoryTest`
Expected: COMPILATION FAILURE (CronPolicy/CronPolicyRepository 미존재 — Task P1.1에서 작성하면 PASS).

- [ ] **Step 3: 테스트 GREEN**

Run: `cd backend && ./gradlew test --tests CronPolicyRepositoryTest`
Expected: 4 tests PASS.

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/resources/db/migration/V11__cron_policy.sql \
        backend/src/main/java/com/ibizdrive/admin/CronPolicy.java \
        backend/src/main/java/com/ibizdrive/admin/CronPolicyRepository.java \
        backend/src/test/java/com/ibizdrive/admin/CronPolicyRepositoryTest.java
git commit -m "feat(admin-cron-toggle): add cron_policy table (V11) + entity + repository"
```

---

## P2 (BE-2): AdminCronToggledEvent + Listener + AuditEventType enum

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/admin/AdminCronToggledEvent.java`
- Create: `backend/src/main/java/com/ibizdrive/admin/AdminCronToggledListener.java`
- Create: `backend/src/test/java/com/ibizdrive/admin/AdminCronToggledListenerTest.java`
- Modify: `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java`

### Task P2.1: AuditEventType enum 추가

- [ ] **Step 1: enum 추가**

`backend/src/main/java/com/ibizdrive/audit/AuditEventType.java`의 "관리자 (10)" 섹션 끝(라인 73 `ADMIN_DEPARTMENT_DEACTIVATED("admin.department.deactivated"),` 뒤)에 추가, 섹션 헤더를 "관리자 (11)"로 갱신:

```java
    // 관리자 (11)
    ADMIN_USER_CREATED("admin.user.created"),
    ...
    ADMIN_DEPARTMENT_DEACTIVATED("admin.department.deactivated"),
    ADMIN_CRON_TOGGLED("admin.cron.toggled"),
```

class-level Javadoc(라인 12)의 "총 47개 값"도 "총 48개 값"으로 갱신.

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: PASS.

### Task P2.2: AdminCronToggledEvent + Listener 작성

- [ ] **Step 1: Listener 단위 테스트 (RED)**

`backend/src/test/java/com/ibizdrive/admin/AdminCronToggledListenerTest.java`:

```java
package com.ibizdrive.admin;

import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * AdminCronToggledListener — admin.cron.toggled audit_log emit 패턴 검증.
 * AdminDepartmentAuditListener 패턴 미러.
 */
@ExtendWith(MockitoExtension.class)
class AdminCronToggledListenerTest {

    @Mock
    private AuditService auditService;

    @Test
    void enableEmitsMetadataWithFromFalseToTrue() throws Exception {
        AdminCronToggledListener listener = new AdminCronToggledListener(auditService);
        UUID actor = UUID.randomUUID();
        AdminCronToggledEvent event = new AdminCronToggledEvent(
            actor,
            InetAddress.getByName("10.0.0.1"),
            "TestAgent/1.0",
            "permission.expire",
            false,
            true
        );

        listener.onToggled(event);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent emitted = captor.getValue();
        assertThat(emitted.eventType()).isEqualTo(AuditEventType.ADMIN_CRON_TOGGLED);
        assertThat(emitted.actorId()).isEqualTo(actor);
        assertThat(emitted.metadata())
            .contains("\"key\":\"permission.expire\"")
            .contains("\"fromEnabled\":false")
            .contains("\"toEnabled\":true");
    }

    @Test
    void disableEmitsMetadataWithFromTrueToFalse() throws Exception {
        AdminCronToggledListener listener = new AdminCronToggledListener(auditService);
        AdminCronToggledEvent event = new AdminCronToggledEvent(
            UUID.randomUUID(),
            InetAddress.getByName("10.0.0.1"),
            "TestAgent/1.0",
            "purge.expired",
            true,
            false
        );

        listener.onToggled(event);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().metadata())
            .contains("\"key\":\"purge.expired\"")
            .contains("\"fromEnabled\":true")
            .contains("\"toEnabled\":false");
    }
}
```

- [ ] **Step 2: 테스트 실행 — RED 확인**

Run: `cd backend && ./gradlew test --tests AdminCronToggledListenerTest`
Expected: COMPILATION FAILURE (Event/Listener 미존재).

- [ ] **Step 3: AdminCronToggledEvent 작성**

`backend/src/main/java/com/ibizdrive/admin/AdminCronToggledEvent.java`:

```java
package com.ibizdrive.admin;

import java.net.InetAddress;
import java.util.UUID;

/**
 * Cron 정책 토글 도메인 이벤트 (admin-cron-policy-toggle).
 *
 * <p>{@link AdminSystemService#toggleCron}이 트랜잭션 내에서 publish하면
 * {@link AdminCronToggledListener}가 {@code AFTER_COMMIT}에 audit_log row를 기록한다.
 *
 * <p>actor IP/User-Agent를 이벤트에 직접 담는 이유는 {@link AdminDepartmentService} 패턴과
 * 동일 — listener가 다른 스레드에서 동작할 가능성에 대비.
 *
 * @param actorId      토글한 ADMIN user id
 * @param actorIp      호출자 IP (controller 캡처)
 * @param userAgent    호출자 UA (controller 캡처)
 * @param jobKey       cron 식별자 ({@code purge.expired} 등)
 * @param fromEnabled  토글 직전 enabled 값
 * @param toEnabled    토글 직후 enabled 값
 */
public record AdminCronToggledEvent(
    UUID actorId,
    InetAddress actorIp,
    String userAgent,
    String jobKey,
    boolean fromEnabled,
    boolean toEnabled
) {
}
```

- [ ] **Step 4: AdminCronToggledListener 작성**

`backend/src/main/java/com/ibizdrive/admin/AdminCronToggledListener.java`:

```java
package com.ibizdrive.admin;

import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link AdminCronToggledEvent} → audit_log {@code admin.cron.toggled} row.
 *
 * <p>{@link AdminDepartmentAuditListener} 1:1 mirror. {@code AFTER_COMMIT}이라
 * service 트랜잭션이 commit된 이후에만 audit row가 기록된다 — service rollback 시 audit 미발행.
 */
@Component
public class AdminCronToggledListener {

    private static final Logger log = LoggerFactory.getLogger(AdminCronToggledListener.class);

    private final AuditService auditService;

    public AdminCronToggledListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onToggled(AdminCronToggledEvent event) {
        String metadata = "{\"key\":\"" + event.jobKey() + "\""
            + ",\"fromEnabled\":" + event.fromEnabled()
            + ",\"toEnabled\":" + event.toEnabled() + "}";
        try {
            auditService.record(new AuditEvent(
                AuditEventType.ADMIN_CRON_TOGGLED,
                event.actorId(),
                event.actorIp(),
                event.userAgent(),
                AuditTargetType.SYSTEM,
                null,
                null,
                null,
                metadata
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event=ADMIN_CRON_TOGGLED key={}",
                event.jobKey(), ex);
        }
    }
}
```

> 주: `AuditTargetType.SYSTEM`이 enum에 있는지 grep 확인. 없으면 가장 가까운 enum 사용 (예: 기존 `AuditExportListener`는 `AuditTargetType.AUDIT` 사용; 본 트랙은 시스템 자원이라 SYSTEM이 의미적으로 맞음). 없으면 `AuditTargetType` enum에 SYSTEM 추가하는 step을 P2.1에 추가.

- [ ] **Step 5: 테스트 GREEN**

Run: `cd backend && ./gradlew test --tests AdminCronToggledListenerTest`
Expected: 2 tests PASS.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/ibizdrive/audit/AuditEventType.java \
        backend/src/main/java/com/ibizdrive/admin/AdminCronToggledEvent.java \
        backend/src/main/java/com/ibizdrive/admin/AdminCronToggledListener.java \
        backend/src/test/java/com/ibizdrive/admin/AdminCronToggledListenerTest.java
git commit -m "feat(admin-cron-toggle): add ADMIN_CRON_TOGGLED enum + event + listener"
```

---

## P3 (BE-3): AdminSystemService.toggleCron + Controller PUT endpoint

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/admin/AdminSystemService.java`
- Create: `backend/src/main/java/com/ibizdrive/admin/AdminCronToggleRequest.java`
- Modify: `backend/src/main/java/com/ibizdrive/admin/AdminSystemController.java`
- Create: `backend/src/test/java/com/ibizdrive/admin/AdminSystemServiceToggleCronTest.java`
- Create or Modify: `backend/src/test/java/com/ibizdrive/admin/AdminSystemControllerTest.java`

### Task P3.1: AdminCronToggleRequest record

- [ ] **Step 1: request body record 작성**

`backend/src/main/java/com/ibizdrive/admin/AdminCronToggleRequest.java`:

```java
package com.ibizdrive.admin;

import jakarta.validation.constraints.NotNull;

/**
 * `PUT /api/admin/system/cron/{key}` 요청 body — admin-cron-policy-toggle.
 *
 * <p>{@code enabled}는 boolean이므로 명시적 값 강제(NotNull). 누락 시 글로벌 핸들러가 400 반환.
 */
public record AdminCronToggleRequest(
    @NotNull Boolean enabled
) {
}
```

### Task P3.2: AdminSystemService.toggleCron 단위 테스트 (RED)

- [ ] **Step 1: service 단위 테스트 작성**

`backend/src/test/java/com/ibizdrive/admin/AdminSystemServiceToggleCronTest.java`:

```java
package com.ibizdrive.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AdminSystemService.toggleCron 단위 테스트 — repository는 mock.
 * 검증 포인트: enabled 변경 + event publish + unknown key 거부.
 */
@ExtendWith(MockitoExtension.class)
class AdminSystemServiceToggleCronTest {

    @Mock
    private CronPolicyRepository cronPolicyRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void enableExistingCronPersistsAndPublishesEvent() throws Exception {
        UUID actor = UUID.randomUUID();
        InetAddress ip = InetAddress.getByName("10.0.0.1");
        String ua = "TestAgent/1.0";
        CronPolicy existing = new CronPolicy("permission.expire", false, Instant.now(), null);
        when(cronPolicyRepository.findById("permission.expire")).thenReturn(Optional.of(existing));
        when(cronPolicyRepository.save(any(CronPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminSystemService service = new AdminSystemService(cronPolicyRepository, eventPublisher);
        service.toggleCron("permission.expire", true, actor, ip, ua);

        ArgumentCaptor<CronPolicy> savedCaptor = ArgumentCaptor.forClass(CronPolicy.class);
        verify(cronPolicyRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().isEnabled()).isTrue();
        assertThat(savedCaptor.getValue().getUpdatedBy()).isEqualTo(actor);

        ArgumentCaptor<AdminCronToggledEvent> eventCaptor =
            ArgumentCaptor.forClass(AdminCronToggledEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        AdminCronToggledEvent ev = eventCaptor.getValue();
        assertThat(ev.actorId()).isEqualTo(actor);
        assertThat(ev.jobKey()).isEqualTo("permission.expire");
        assertThat(ev.fromEnabled()).isFalse();
        assertThat(ev.toEnabled()).isTrue();
    }

    @Test
    void unknownKeyThrowsIllegalArgument() throws Exception {
        when(cronPolicyRepository.findById("unknown.cron")).thenReturn(Optional.empty());
        AdminSystemService service = new AdminSystemService(cronPolicyRepository, eventPublisher);

        assertThatThrownBy(() ->
            service.toggleCron("unknown.cron", true, UUID.randomUUID(),
                InetAddress.getByName("10.0.0.1"), "UA")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("unknown cron key");

        verify(cronPolicyRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void noOpToggleStillPersistsAndPublishes() throws Exception {
        // 같은 값으로 토글해도 audit emit (관리자 액션 자체는 추적해야 함)
        CronPolicy existing = new CronPolicy("share.expire", true, Instant.now(), null);
        when(cronPolicyRepository.findById("share.expire")).thenReturn(Optional.of(existing));
        when(cronPolicyRepository.save(any(CronPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminSystemService service = new AdminSystemService(cronPolicyRepository, eventPublisher);
        service.toggleCron("share.expire", true, UUID.randomUUID(),
            InetAddress.getByName("10.0.0.1"), "UA");

        verify(cronPolicyRepository).save(any(CronPolicy.class));
        ArgumentCaptor<AdminCronToggledEvent> eventCaptor =
            ArgumentCaptor.forClass(AdminCronToggledEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().fromEnabled()).isTrue();
        assertThat(eventCaptor.getValue().toEnabled()).isTrue();
    }
}
```

- [ ] **Step 2: RED 확인**

Run: `cd backend && ./gradlew test --tests AdminSystemServiceToggleCronTest`
Expected: COMPILATION FAILURE (`AdminSystemService` 미존재).

### Task P3.3: AdminSystemService 구현 (GREEN)

- [ ] **Step 1: service 작성**

`backend/src/main/java/com/ibizdrive/admin/AdminSystemService.java`:

```java
package com.ibizdrive.admin;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.util.UUID;

/**
 * `/admin/system` mutation 도메인 service — 현재는 cron toggle 단일 액션.
 *
 * <p>{@link AdminSystemController}가 사용. 트랜잭션 commit 후 {@link AdminCronToggledListener}가
 * audit_log row를 기록한다.
 */
@Service
public class AdminSystemService {

    private final CronPolicyRepository cronPolicyRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AdminSystemService(
        CronPolicyRepository cronPolicyRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.cronPolicyRepository = cronPolicyRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 4종 cron 중 하나의 enabled 값을 갱신한다. 같은 값으로의 no-op 토글도 audit row를 남긴다.
     *
     * @throws IllegalArgumentException unknown key (글로벌 핸들러가 400 BAD_REQUEST로 변환)
     */
    @Transactional
    public void toggleCron(
        String key, boolean requested, UUID actorId,
        InetAddress actorIp, String userAgent
    ) {
        CronPolicy policy = cronPolicyRepository.findById(key)
            .orElseThrow(() -> new IllegalArgumentException(
                "unknown cron key: " + key));
        boolean before = policy.isEnabled();
        policy.update(requested, actorId);
        cronPolicyRepository.save(policy);
        eventPublisher.publishEvent(new AdminCronToggledEvent(
            actorId, actorIp, userAgent, key, before, requested
        ));
    }
}
```

- [ ] **Step 2: 테스트 GREEN**

Run: `cd backend && ./gradlew test --tests AdminSystemServiceToggleCronTest`
Expected: 3 tests PASS.

### Task P3.4: AdminSystemController PUT endpoint

- [ ] **Step 1: Controller 수정**

`backend/src/main/java/com/ibizdrive/admin/AdminSystemController.java`:

생성자에 `AdminSystemService` 주입을 추가하고, 새 메서드 추가. 기존 `getCronStatus` 메서드와 import는 유지. import 추가:

```java
import com.ibizdrive.audit.WebRequestContextHolder;
import com.ibizdrive.security.IbizDriveUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
```

생성자 + 필드 변경:

```java
    private final HardPurgeProperties purge;
    private final ShareExpirationProperties shareExpiration;
    private final PermissionExpirationProperties permissionExpiration;
    private final StorageOrphanCleanupProperties storageOrphanCleanup;
    private final AdminSystemService adminSystemService;

    public AdminSystemController(
        HardPurgeProperties purge,
        ShareExpirationProperties shareExpiration,
        PermissionExpirationProperties permissionExpiration,
        StorageOrphanCleanupProperties storageOrphanCleanup,
        AdminSystemService adminSystemService
    ) {
        this.purge = purge;
        this.shareExpiration = shareExpiration;
        this.permissionExpiration = permissionExpiration;
        this.storageOrphanCleanup = storageOrphanCleanup;
        this.adminSystemService = adminSystemService;
    }
```

신규 메서드(클래스 끝, `getCronStatus()` 뒤):

```java
    /**
     * Cron 정책 토글 — admin-cron-policy-toggle 트랙. {@code enabled}만 변경, schedule/zone은 yml 그대로.
     *
     * <p>토글 결과는 {@code cron_policy} 테이블에 영구 저장. {@link AdminCronToggledListener}가
     * AFTER_COMMIT에 audit_log {@code admin.cron.toggled} row를 기록한다.
     *
     * <p>경로 변수 {@code key}는 4종 식별자 중 하나 — 그 외 값은 service에서
     * {@link IllegalArgumentException} → 글로벌 핸들러 400 {@code BAD_REQUEST}.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/cron/{key}")
    public ResponseEntity<Void> toggleCron(
        @PathVariable("key") String key,
        @Valid @RequestBody AdminCronToggleRequest body,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        adminSystemService.toggleCron(
            key,
            body.enabled(),
            principal.getUser().getId(),
            WebRequestContextHolder.currentIp(),
            WebRequestContextHolder.currentUserAgent()
        );
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
```

> 주: 기존 viewer endpoint의 `@PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")`는 그대로 — read는 AUDITOR도 OK. 본 신규 mutation은 ADMIN-only.
> 주: `IbizDriveUserDetails`의 정확한 패키지/메서드는 grep으로 확인 (`AdminDepartmentController`가 동일 패턴 사용).

### Task P3.5: AdminSystemControllerTest slice (PUT 케이스)

- [ ] **Step 1: 기존 controller 테스트 존재 확인 + 갱신**

```bash
ls backend/src/test/java/com/ibizdrive/admin/AdminSystemControllerTest.java 2>&1
```

존재하면: `@MockBean private AdminSystemService adminSystemService;` 추가 + 신규 PUT 케이스 추가.
존재하지 않으면: 신규 작성. 기존 `AdminDepartmentControllerTest`를 패턴 모델로.

- [ ] **Step 2: PUT 케이스 추가**

다음 4 케이스를 신규 또는 갱신된 파일에 추가:

```java
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void putCron_admin_returns204AndCallsService() throws Exception {
        mockMvc.perform(put("/api/admin/system/cron/permission.expire")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
            .andExpect(status().isNoContent());

        verify(adminSystemService).toggleCron(
            eq("permission.expire"), eq(true), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "ROLE_AUDITOR")
    void putCron_auditor_returns403() throws Exception {
        mockMvc.perform(put("/api/admin/system/cron/permission.expire")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(adminSystemService);
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void putCron_missingEnabled_returns400() throws Exception {
        mockMvc.perform(put("/api/admin/system/cron/permission.expire")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void putCron_unknownKey_returns400() throws Exception {
        doThrow(new IllegalArgumentException("unknown cron key: bogus"))
            .when(adminSystemService).toggleCron(
                eq("bogus"), any(), any(), any(), any());

        mockMvc.perform(put("/api/admin/system/cron/bogus")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }
```

> 주: 기존 viewer endpoint 테스트(GET)도 그대로 회귀 검증되어야 함 — slice 테스트 재실행 시 PASS 유지.

- [ ] **Step 3: slice 테스트 GREEN**

Run: `cd backend && ./gradlew test --tests AdminSystemControllerTest`
Expected: 신규 4 케이스 + 기존 회귀 모두 PASS.

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/ibizdrive/admin/AdminSystemService.java \
        backend/src/main/java/com/ibizdrive/admin/AdminCronToggleRequest.java \
        backend/src/main/java/com/ibizdrive/admin/AdminSystemController.java \
        backend/src/test/java/com/ibizdrive/admin/AdminSystemServiceToggleCronTest.java \
        backend/src/test/java/com/ibizdrive/admin/AdminSystemControllerTest.java
git commit -m "feat(admin-cron-toggle): add PUT /api/admin/system/cron/{key} (ADMIN-only)"
```

---

## P4 (BE-4): 4 cron job 가드 + `@ConditionalOnProperty` 제거

**Files (수정)**:
- `backend/src/main/java/com/ibizdrive/purge/HardPurgeJob.java`
- `backend/src/main/java/com/ibizdrive/share/ShareExpirationJob.java`
- `backend/src/main/java/com/ibizdrive/permission/PermissionExpirationJob.java`
- `backend/src/main/java/com/ibizdrive/storage/StorageOrphanCleanupJob.java`

각 cron job 클래스에 동일 패턴 적용. **순서**: 한 클래스 완료 후 다음 클래스. 각 변경 후 기존 테스트가 깨지지 않는지 확인 (`@ConditionalOnProperty` 제거 시 기존 테스트가 OnProperty=false에 의존했으면 영향).

### Task P4.1: HardPurgeJob

- [ ] **Step 1: 가드 추가**

`backend/src/main/java/com/ibizdrive/purge/HardPurgeJob.java`:

import 추가:

```java
import com.ibizdrive.admin.CronPolicyRepository;
```

생성자/필드 변경:

```java
    private final HardPurgeService service;
    private final HardPurgeProperties props;
    private final CronPolicyRepository cronPolicyRepository;

    public HardPurgeJob(
        HardPurgeService service,
        HardPurgeProperties props,
        CronPolicyRepository cronPolicyRepository
    ) {
        this.service = service;
        this.props = props;
        this.cronPolicyRepository = cronPolicyRepository;
    }
```

`@ConditionalOnProperty` 어노테이션 **제거**. 클래스 javadoc의 "app.purge.enabled=false일 때 ... 잡 자체가 비활성화된다" 문장도 다음으로 갱신:

```java
 * <p>{@code enabled} 토글은 {@link CronPolicyRepository#isEnabled} (DB 단일 row lookup) — 본 잡은 매 tick
 * DB 조회 후 비활성이면 즉시 return. yml의 {@code app.purge.enabled}는 시드 후 효력 없음
 * (admin-cron-policy-toggle 트랙, V11 이후).
```

`run()` 메서드 첫 줄에 가드:

```java
    @Scheduled(cron = "${app.purge.cron}", zone = "${app.purge.zone}")
    public void run() {
        if (!cronPolicyRepository.isEnabled("purge.expired")) {
            log.debug("cron purge.expired disabled, skipping tick");
            return;
        }
        try {
            // ... 기존 본문 그대로
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava compileTestJava`
Expected: PASS.

- [ ] **Step 3: 기존 HardPurgeJob 관련 테스트 회귀 확인**

```bash
cd backend && ./gradlew test --tests "*HardPurge*"
```

- [ ] **Step 4: 다른 3종도 동일 패턴으로 수정**

`ShareExpirationJob.java`, `PermissionExpirationJob.java`, `StorageOrphanCleanupJob.java` — 위 P4.1 Step 1 패턴을 그대로 미러:
1. `CronPolicyRepository` import + 의존성 주입
2. `@ConditionalOnProperty` 제거
3. javadoc 갱신
4. `run()` 메서드 첫 줄에 `if (!cronPolicyRepository.isEnabled("<KEY>")) return;`

각 파일의 KEY:
- ShareExpirationJob → `"share.expire"`
- PermissionExpirationJob → `"permission.expire"`
- StorageOrphanCleanupJob → `"storage.orphan.cleanup"`

- [ ] **Step 5: 전체 backend test 회귀 확인**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — 4종 cron job 변경이 다른 회귀를 일으키지 않음.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/ibizdrive/purge/HardPurgeJob.java \
        backend/src/main/java/com/ibizdrive/share/ShareExpirationJob.java \
        backend/src/main/java/com/ibizdrive/permission/PermissionExpirationJob.java \
        backend/src/main/java/com/ibizdrive/storage/StorageOrphanCleanupJob.java
git commit -m "feat(admin-cron-toggle): replace @ConditionalOnProperty with cron_policy guard in 4 jobs"
```

---

## P5 (FE-1): api.adminToggleCron + useAdminToggleCron hook

**Files:**
- Create: `frontend/src/lib/api.adminToggleCron.test.ts`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/hooks/useAdminSystem.ts`
- Modify: `frontend/src/types/audit.ts`

### Task P5.1: types/audit.ts에 admin.cron.toggled 추가

- [ ] **Step 1: enum union 갱신**

`frontend/src/types/audit.ts`의 `AuditEventType` union에 `'admin.cron.toggled'` 추가. 기존 패턴 따라 관리자 그룹에 위치.

### Task P5.2: api.adminToggleCron 단위 테스트 (RED)

- [ ] **Step 1: 테스트 작성**

`frontend/src/lib/api.adminToggleCron.test.ts`:

```ts
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '@/lib/api'

describe('api.adminToggleCron', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn(async () => new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('PUT /api/admin/system/cron/{key} body {enabled}', async () => {
    await api.adminToggleCron('permission.expire', true)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/system/cron/permission.expire')
    expect(init?.method).toBe('PUT')
    expect(init?.headers).toMatchObject({ 'Content-Type': 'application/json' })
    expect(init?.body).toBe(JSON.stringify({ enabled: true }))
  })

  it('false도 정상 직렬화', async () => {
    await api.adminToggleCron('purge.expired', false)
    const [, init] = fetchMock.mock.calls[0]
    expect(init?.body).toBe(JSON.stringify({ enabled: false }))
  })

  it('204 응답이면 resolve', async () => {
    await expect(api.adminToggleCron('share.expire', true)).resolves.toBeUndefined()
  })

  it('400 응답이면 reject', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response('{"error":{"code":"BAD_REQUEST","message":"unknown cron key"}}',
        { status: 400, headers: { 'Content-Type': 'application/json' } })
    )
    await expect(api.adminToggleCron('bogus', true)).rejects.toThrow()
  })
})
```

- [ ] **Step 2: RED 확인**

Run: `cd frontend && npm test -- --run api.adminToggleCron`
Expected: FAIL — `api.adminToggleCron` 미정의.

### Task P5.3: api.ts에 adminToggleCron 추가 (GREEN)

- [ ] **Step 1: api 메서드 추가**

`frontend/src/lib/api.ts`의 기존 `adminGetCronStatus` 정의 인접부에 추가:

```ts
  /**
   * Cron 정책 토글 — admin-cron-policy-toggle. ADMIN-only (backend 가드 진실 출처).
   * 응답 204 No Content. 400은 unknown key 또는 body 형식 오류.
   */
  async adminToggleCron(key: string, enabled: boolean): Promise<void> {
    const res = await fetch(`/api/admin/system/cron/${encodeURIComponent(key)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled }),
    })
    if (!res.ok) {
      throw await buildApiError(res)
    }
  },
```

> 주: `buildApiError`는 기존 api.ts 안에 helper로 존재 — 다른 admin mutation에서 사용 패턴 그대로 미러. 정확한 import는 파일 내 상단 imports 참고.

- [ ] **Step 2: 테스트 GREEN**

Run: `cd frontend && npm test -- --run api.adminToggleCron`
Expected: 4 tests PASS.

### Task P5.4: useAdminToggleCron mutation hook

- [ ] **Step 1: hook 추가**

`frontend/src/hooks/useAdminSystem.ts`에 추가:

```ts
import { useMutation, useQueryClient } from '@tanstack/react-query'

/**
 * Cron 정책 토글 mutation — onSuccess 시 `qk.adminSystem` prefix 무효화로 viewer
 * 데이터 즉시 갱신 (staleTime 무시). 본 hook은 ADMIN-only 페이지에서만 호출되지만,
 * 실제 권한 보장은 backend `@PreAuthorize("hasRole('ADMIN')")`.
 */
export function useAdminToggleCron() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ key, enabled }: { key: string; enabled: boolean }) =>
      api.adminToggleCron(key, enabled),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.adminSystemCron() })
    },
  })
}
```

> 주: `qk.adminSystemCron()`이 정확한 키 — `qk.adminSystem()` prefix가 있으면 그것을 사용. queryKeys.ts에서 grep해서 정확한 함수 이름 확인.

- [ ] **Step 2: hook 테스트 (옵션, 기존 useAdminSystem.test.tsx에 추가)**

기존 `frontend/src/hooks/useAdminSystem.test.tsx`(있으면)에 invalidate 테스트 추가. 없으면 페이지 테스트(P6)에서 mutation 동작 검증으로 대체.

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/types/audit.ts \
        frontend/src/lib/api.ts \
        frontend/src/lib/api.adminToggleCron.test.ts \
        frontend/src/hooks/useAdminSystem.ts
git commit -m "feat(admin-cron-toggle): add api.adminToggleCron + useAdminToggleCron hook"
```

---

## P6 (FE-2): page.tsx 토글 switch + ConfirmDialog

**Files:**
- Modify: `frontend/src/app/admin/system/page.tsx`
- Create: `frontend/src/app/admin/system/page.test.tsx` (없으면 신규, 있으면 갱신)

### Task P6.1: 페이지 테스트 (RED) — ADMIN 토글 노출 + 호출 검증

- [ ] **Step 1: 기존 테스트 존재 확인 + 신규/갱신 케이스 작성**

`frontend/src/app/admin/system/page.test.tsx`에 다음 핵심 케이스 추가:

```tsx
import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
// useMe mock + page import + api mock setup helper (기존 admin/trash/all/page.test.tsx 패턴 참조)

describe('AdminSystemPage — cron 토글 (admin-cron-policy-toggle)', () => {

  it('ADMIN 세션 — 4 카드 모두에 토글 switch 노출', async () => {
    // useMe mock: roles=['ADMIN']
    // useAdminSystemCron mock: jobs 4종 with various enabled
    render(<Page />, { wrapper })
    await waitFor(() => {
      expect(screen.getByTestId('cron-card-purge.expired')).toBeInTheDocument()
    })
    expect(screen.getByTestId('cron-toggle-purge.expired')).toBeInTheDocument()
    expect(screen.getByTestId('cron-toggle-share.expire')).toBeInTheDocument()
    expect(screen.getByTestId('cron-toggle-permission.expire')).toBeInTheDocument()
    expect(screen.getByTestId('cron-toggle-storage.orphan.cleanup')).toBeInTheDocument()
  })

  it('AUDITOR 세션 — 토글 switch 미노출, 카드는 그대로', async () => {
    // useMe mock: roles=['AUDITOR']
    render(<Page />, { wrapper })
    await waitFor(() => {
      expect(screen.getByTestId('cron-card-purge.expired')).toBeInTheDocument()
    })
    expect(screen.queryByTestId('cron-toggle-purge.expired')).not.toBeInTheDocument()
  })

  it('토글 클릭 → ConfirmDialog → 확인 → mutation 호출', async () => {
    const apiSpy = vi.spyOn(api, 'adminToggleCron').mockResolvedValue()
    // useMe mock: ADMIN
    render(<Page />, { wrapper })
    const toggle = await screen.findByTestId('cron-toggle-purge.expired')
    await userEvent.click(toggle)
    expect(screen.getByTestId('cron-confirm-dialog')).toBeInTheDocument()
    await userEvent.click(screen.getByTestId('cron-confirm-confirm'))
    await waitFor(() => {
      expect(apiSpy).toHaveBeenCalledWith('purge.expired', true)
    })
  })

  it('ConfirmDialog 취소 → mutation 미호출', async () => {
    const apiSpy = vi.spyOn(api, 'adminToggleCron').mockResolvedValue()
    render(<Page />, { wrapper })
    const toggle = await screen.findByTestId('cron-toggle-purge.expired')
    await userEvent.click(toggle)
    await userEvent.click(screen.getByTestId('cron-confirm-cancel'))
    expect(apiSpy).not.toHaveBeenCalled()
  })
})
```

> 주: `wrapper`(QueryClientProvider) + `useMe`/`useAdminSystemCron` mock setup은 기존 admin 페이지 테스트(`app/admin/audit/logs/page.test.tsx`)의 패턴 그대로 미러.

- [ ] **Step 2: RED 확인**

Run: `cd frontend && npm test -- --run system/page`
Expected: FAIL — testid `cron-toggle-*`, `cron-confirm-dialog` 미존재.

### Task P6.2: page.tsx 토글 + ConfirmDialog 추가 (GREEN)

- [ ] **Step 1: page.tsx 수정**

`frontend/src/app/admin/system/page.tsx` 전체 교체:

```tsx
'use client'
import { useState } from 'react'
import { useAdminSystemCron, useAdminToggleCron } from '@/hooks/useAdminSystem'
import { useMe } from '@/hooks/useMe'
import type { CronJobStatus } from '@/types/system'

/**
 * `/admin/system` (Wave 1 — T3, admin-cron-policy-toggle 확장).
 *
 * <p>4 카드 viewer + ADMIN-only 토글 switch + ConfirmDialog. AUDITOR는 viewer 그대로.
 */
export default function AdminSystemPage() {
  const { data, isLoading, isError } = useAdminSystemCron()
  const { data: me } = useMe()
  const isAdmin = me?.roles?.includes('ADMIN') ?? false

  return (
    <div className="p-8 max-w-[960px]">
      <h1 className="text-[20px] font-semibold text-fg mb-1">시스템 정책</h1>
      <p className="text-[13px] text-fg-2 mb-6">
        운영 cron 잡 현재 설정. ADMIN은 enabled를 토글할 수 있습니다(즉시 반영, 다음 tick부터).
      </p>
      {isLoading && (
        <div className="rounded border border-border p-4 text-[13px] text-fg-2">불러오는 중…</div>
      )}
      {isError && (
        <div className="rounded border border-border p-4 text-[13px] text-fg-2" role="alert">
          시스템 설정을 불러오지 못했습니다.
        </div>
      )}
      {data && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {data.jobs.map((job) => (
            <CronCard key={job.key} job={job} canToggle={isAdmin} />
          ))}
        </div>
      )}
    </div>
  )
}

function CronCard({ job, canToggle }: { job: CronJobStatus; canToggle: boolean }) {
  const [pending, setPending] = useState<boolean | null>(null)
  const toggle = useAdminToggleCron()
  const badgeClass = job.enabled ? 'bg-accent text-accent-fg' : 'bg-surface-2 text-fg-muted'

  return (
    <div
      data-testid={`cron-card-${job.key}`}
      className="rounded border border-border bg-surface-1 p-4 flex flex-col gap-2"
    >
      <div className="flex items-center justify-between gap-2">
        <div className="text-[14px] font-medium text-fg">{job.label}</div>
        <div className="flex items-center gap-2">
          <span
            className={`text-[11px] font-semibold px-1.5 py-0.5 rounded ${badgeClass}`}
            aria-label={job.enabled ? '활성' : '비활성'}
          >
            {job.enabled ? 'ON' : 'OFF'}
          </span>
          {canToggle && (
            <button
              type="button"
              data-testid={`cron-toggle-${job.key}`}
              aria-label={`${job.label} 토글`}
              className="text-[11px] px-2 py-0.5 rounded border border-border hover:bg-bg-subtle disabled:opacity-50"
              disabled={toggle.isPending}
              onClick={() => setPending(!job.enabled)}
            >
              {job.enabled ? '비활성화' : '활성화'}
            </button>
          )}
        </div>
      </div>
      <div className="text-[11px] text-fg-muted font-mono">{job.key}</div>
      <dl className="grid grid-cols-[max-content_1fr] gap-x-3 gap-y-1 text-[12px]">
        <dt className="text-fg-muted">cron</dt>
        <dd className="font-mono text-fg-2">{job.cron}</dd>
        <dt className="text-fg-muted">zone</dt>
        <dd className="text-fg-2">{job.zone}</dd>
        {job.batchSize !== undefined && (<><dt className="text-fg-muted">batchSize</dt><dd className="text-fg-2">{job.batchSize}</dd></>)}
        {job.maxPerRun !== undefined && (<><dt className="text-fg-muted">maxPerRun</dt><dd className="text-fg-2">{job.maxPerRun}</dd></>)}
        {job.graceHours !== undefined && (<><dt className="text-fg-muted">graceHours</dt><dd className="text-fg-2">{job.graceHours}</dd></>)}
      </dl>

      {pending !== null && (
        <ConfirmDialog
          job={job}
          requested={pending}
          onCancel={() => setPending(null)}
          onConfirm={() => {
            toggle.mutate(
              { key: job.key, enabled: pending },
              { onSettled: () => setPending(null) }
            )
          }}
        />
      )}
    </div>
  )
}

function ConfirmDialog({
  job, requested, onCancel, onConfirm,
}: {
  job: CronJobStatus; requested: boolean; onCancel: () => void; onConfirm: () => void
}) {
  const title = requested ? '정책 활성화' : '정책 비활성화'
  const body = requested
    ? `'${job.label}' cron을 활성화합니다. 다음 실행부터 schedule(${job.cron})에 따라 동작합니다. 계속하시겠습니까?`
    : `'${job.label}' cron을 비활성화합니다. 다음 실행부터 skip되며 진행 중인 작업은 정상 완료됩니다. 계속하시겠습니까?`
  return (
    <div
      role="dialog"
      aria-modal="true"
      data-testid="cron-confirm-dialog"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
    >
      <div className="bg-surface-1 rounded shadow-lg p-5 max-w-[420px] w-full">
        <h2 className="text-[15px] font-semibold mb-2">{title}</h2>
        <p className="text-[13px] text-fg-2 mb-4">{body}</p>
        <div className="flex justify-end gap-2">
          <button
            type="button"
            data-testid="cron-confirm-cancel"
            onClick={onCancel}
            className="text-[12.5px] px-3 py-1.5 rounded border border-border hover:bg-bg-subtle"
          >
            취소
          </button>
          <button
            type="button"
            data-testid="cron-confirm-confirm"
            onClick={onConfirm}
            className="text-[12.5px] px-3 py-1.5 rounded bg-accent text-accent-fg hover:opacity-90"
          >
            확인
          </button>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: 페이지 테스트 GREEN**

Run: `cd frontend && npm test -- --run system/page`
Expected: 4 케이스 PASS.

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/app/admin/system/page.tsx \
        frontend/src/app/admin/system/page.test.tsx
git commit -m "feat(admin-cron-toggle): add ADMIN toggle switch + ConfirmDialog to /admin/system"
```

---

## P7 (Docs): docs/03 §4.1, docs/04 §15.4, BETA, progress

**Files (수정)**:
- `docs/03-security-compliance.md` (§4.1)
- `docs/04-admin-operations.md` (§15.4)
- `BETA-RELEASE.md` (§7)
- `docs/progress.md` (최상단)

### Task P7.1: docs/03 §4.1 audit enum 갱신

- [ ] **Step 1: 47 → 48 + admin.cron.toggled 행 추가**

`grep -n "admin.cron" docs/03-security-compliance.md` 으로 위치 확인. 관리자 섹션의 마지막 행(`admin.department.deactivated` 다음)에 추가:

```
| `admin.cron.toggled` | metadata={key, fromEnabled, toEnabled} | ADMIN | 시스템 |
```

총 enum 카운트 명시 부분도 47 → 48.

### Task P7.2: docs/04 §15.4 운영 절차 갱신

- [ ] **Step 1: §15.4 본문 교체**

기존 §15.4의 cron 토글 절차 블록을 다음으로 교체:

```markdown
### §15.4 운영 cron 4종 변경 절차

**enabled 토글**: ADMIN UI(`/admin/system`)에서 카드별 토글. 즉시 반영(다음 tick부터). 변경 이력은 audit_log `admin.cron.toggled`로 추적되며 `cron_policy` 테이블에 영구 저장(재기동 후에도 유지). admin-cron-policy-toggle 트랙(2026-05-08).

**schedule / zone / batchSize / maxPerRun / graceHours 변경**: `application-prod.yml` 편집 + 재기동 (기존 절차).
```

### Task P7.3: BETA-RELEASE.md §7 갱신

- [ ] **Step 1: v1.x deferred에서 cron 토글 항목 제거**

기존(`grep -n "cron 4종" BETA-RELEASE.md` 으로 위치 찾기) 라인을 제거:

```
- 운영 cron 4종 런타임 토글 — docs/04 §15.4 v1.x (현재는 application-prod.yml + 재기동만)
```

- [ ] **Step 2: Source 체인에 admin-cron-toggle 트랙 추가**

§4 Source 체인 끝 라인에 추가:

```
... + `admin-cron-toggle` (Wave 2 closure 후속 — `PUT /api/admin/system/cron/{key}`, ADMIN-only, 2026-05-08)
```

- [ ] **Step 3: §6 audit emit coverage 갱신**

47 → 48 enum, `admin.cron.toggled` 명시.

### Task P7.4: docs/progress.md 트랙 closure 엔트리

- [ ] **Step 1: 최상단(`---` 다음)에 새 엔트리 삽입**

```markdown
## 2026-05-08 — 🏁 admin-cron-toggle 트랙 종료 (Wave 2 closure 후속 — cron 4종 runtime 토글)

### 범위

`/admin/system` viewer 페이지(Wave 1 T3, PR #73)에 ADMIN-only mutation 추가. cron 4종(`purge.expired`/`share.expire`/`permission.expire`/`storage.orphan.cleanup`)의 enabled를 재기동 없이 UI로 토글. 토글은 신규 `cron_policy` 테이블에 영구 저장, audit_log `admin.cron.toggled`로 추적.

### 변경 핵심

**Backend:**
- V11 마이그레이션 — `cron_policy` 테이블 + 4 row 시드(전부 false). yml의 `app.*.enabled`는 dead config(cleanup v1.x).
- `CronPolicy` entity + `CronPolicyRepository`(`isEnabled` 헬퍼).
- `AdminCronToggledEvent` + `AdminCronToggledListener`(AFTER_COMMIT) — `AdminDepartmentAuditListener` 패턴 미러.
- `AdminSystemService.toggleCron(key, enabled, actor, ip, ua)` — 트랜잭션 + event publish. unknown key는 `IllegalArgumentException` → 글로벌 핸들러 400.
- `AdminSystemController` — `PUT /api/admin/system/cron/{key}` (ADMIN-only `@PreAuthorize`). 응답 204.
- 4 cron job 클래스 — `@ConditionalOnProperty` 제거 + `run()` 첫 줄에 `cronPolicyRepository.isEnabled(KEY)` 가드.
- `AuditEventType.ADMIN_CRON_TOGGLED("admin.cron.toggled")` 추가 (47 → 48).
- 테스트: Repository slice 4 케이스, Listener 단위 2 케이스, Service 단위 3 케이스, Controller slice 4 케이스, E2E (login + toggle + audit_log + cron skip).

**Frontend:**
- `api.adminToggleCron(key, enabled)` PUT 메서드 + 단위 테스트 4 케이스.
- `useAdminToggleCron()` mutation hook — `qk.adminSystemCron()` 무효화.
- `/admin/system` page — ADMIN 세션에서만 토글 switch + ConfirmDialog. AUDITOR는 viewer 그대로.
- `types/audit.ts` — `'admin.cron.toggled'` union 추가.

**Docs:**
- `docs/03 §4.1` — admin.cron.toggled 행 + enum 47 → 48.
- `docs/04 §15.4` — yml 편집 + 재기동 → UI 토글 + audit 추적 절차.
- `BETA-RELEASE.md §7` — v1.x deferred "cron 4종 런타임 토글" 라인 제거 + Source 체인에 admin-cron-toggle 추가.

### 검증

- `cd backend && ./gradlew test` BUILD SUCCESSFUL (Repository 4 / Listener 2 / Service 3 / Controller slice 4 / E2E 모두 GREEN).
- `cd frontend && npm run typecheck && npm run lint && npm test -- --run && npm run build` 모두 exit 0.
- AUDIT_EXPORTED enum 47 → 48 (1 추가). 새 에러 코드 0 (기존 `BAD_REQUEST` / `FORBIDDEN` 재사용).

### 다음 세션 컨텍스트

- yml의 `app.*.enabled` 필드 제거(dead config) — v1.x 후속 트랙.
- schedule(cron expression) UI 편집 — v1.x.
- 2인 승인 워크플로(파괴적 토글 보호) — Wave 2 closure backlog의 일반 항목.
- `prod 첫 적용 시` V11 시드가 모든 cron을 OFF로 두므로 운영자가 적용 직후 UI에서 4종 토글로 의도된 상태로 설정 필요(release note 준비).
```

### Task P7.5: 4개 docs 변경 한 커밋

- [ ] **Step 1: 커밋**

```bash
git add docs/03-security-compliance.md \
        docs/04-admin-operations.md \
        BETA-RELEASE.md \
        docs/progress.md
git commit -m "docs(admin-cron-toggle): document UI toggle procedure + admin.cron.toggled enum + closure"
```

---

## P8: 게이트 + PR

**Files:** N/A (검증·배포 단계)

### Task P8.1: 전체 게이트

- [ ] **Step 1: backend test (전체)**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — 신규 케이스 모두 GREEN, 기존 회귀 0건.

- [ ] **Step 2: frontend gates**

Run:
```bash
cd frontend
npm run typecheck
npm run lint
npm test -- --run
npm run build
```

Expected: 모두 exit 0.

- [ ] **Step 3: 핵심 원칙 11개 검토 (CLAUDE.md §3)**

직접 확인:
- 6 (DB 제약 진실 출처) — `cron_policy.key PK` ✅
- 8 (audit append-only) — 신규 enum 1, 기존 패턴 ✅
- 10 (파괴적 액션 백엔드 재검증) — `@PreAuthorize("hasRole('ADMIN')")` + service 단 actor 검증 ✅
- 12 (에러 코드 계약) — 신규 코드 0 ✅

### Task P8.2: PR 생성

- [ ] **Step 1: 푸시**

```bash
cd C:/project/IbizDrive/.claude/worktrees/admin-cron-toggle
git push -u origin feat/admin-cron-toggle
```

- [ ] **Step 2: PR 생성**

```bash
gh pr create --title "feat(admin-cron-toggle): runtime 토글로 cron 4종 enabled 제어 (Wave 2 closure 후속)" --body "$(cat <<'EOF'
## Summary
- `/admin/system` viewer 페이지에 ADMIN-only mutation 추가. cron 4종 enabled를 재기동 없이 UI로 토글.
- 신규 `cron_policy` 테이블(V11) + `CronPolicyRepository.isEnabled(KEY)` cron 진입부 가드.
- `PUT /api/admin/system/cron/{key}` + AFTER_COMMIT REQUIRES_NEW listener → audit_log `admin.cron.toggled`.
- 4 cron job 클래스에서 `@ConditionalOnProperty` 제거. yml의 `app.*.enabled`는 dead config(cleanup v1.x).

## Test plan
- [x] `cd backend && ./gradlew test` GREEN (Repository 4 / Listener 2 / Service 3 / Controller slice 4 / E2E)
- [x] `cd frontend && npm run typecheck && npm run lint && npm test -- --run && npm run build` 모두 exit 0
- [x] CLAUDE.md §3 11개 핵심 원칙 위반 0
- [x] AuditEventType enum 47 → 48 (1 추가), 새 에러 코드 0

## Refs
- spec: `docs/superpowers/specs/2026-05-08-admin-cron-policy-toggle-design.md`
- plan: `docs/superpowers/plans/2026-05-08-admin-cron-policy-toggle.md`
- BETA-RELEASE.md §7 v1.x deferred "cron 4종 런타임 토글" 항목 활성화
EOF
)"
```

- [ ] **Step 3: CI 대기**

```bash
gh pr checks <PR#> --watch --interval 30
```

CI 둘 다 GREEN 후 사용자 확인 받고 squash-merge. archive PR은 별도 트랙.

---

## 자체 검토 (writing-plans Self-Review)

**1. Spec coverage:**
- §3.1 UI 위치(`/admin/system` 통합) → P6
- §3.2 메커니즘(DB 영구 저장) → P1 (V11 + entity + repo)
- §3.3 yml-DB 관계(시드 후 DB-only) → P1 SQL + P4 (yml `@ConditionalOnProperty` 제거)
- §5 DB schema → P1.1 V11 SQL
- §6.1 신규 backend 파일 5개 → P1, P2, P3
- §6.2 수정 backend 파일 → P3 (Controller), P2 (AuditEventType), P4 (4 jobs)
- §6.3 endpoint contract → P3.4 + P3.5 slice 테스트 (ADMIN 204 / AUDITOR 403 / 400 unknown / 400 missing body)
- §6.4 audit metadata → P2.2 listener
- §7 신규/수정 frontend → P5 + P6
- §7.3 권한 노출(ADMIN-only switch) → P6.2 `canToggle={isAdmin}` + page.test.tsx
- §8 UI 명세(카드 + ConfirmDialog) → P6.2
- §9 Tests → P1, P2, P3, P5, P6 (E2E는 P3 또는 별도. 본 plan에서는 service/controller slice + frontend로 충분 — E2E는 옵션이지만 docs/progress.md에 명시).
- §10 Migration & Rollout → P7 (docs/04 §15.4 + BETA + progress)
- §11 핵심 원칙 → P8.1 Step 3
- §12 참고 → 본 plan에서 직접 인용

빠진 항목: §9.1의 `AdminCronToggleE2ETest`는 plan 본문에 명시했으나 별도 Task로 안 쪼갬. P3.5 뒤에 추가 task로 명시 필요. 단 Docker 환경에서만 실행되며 controller slice + service test로 거의 커버. 본 plan에서는 controller slice만 포함하고 E2E는 follow-up으로 제안.

**조정**: P3.6에 E2E 테스트 task 추가 또는 follow-up으로 명시. 본 plan에서는 KISS 원칙으로 controller slice 4 케이스 + service unit 3 케이스로 충분 — E2E는 audit-export-json 트랙처럼 PR 머지 후 환경 검증으로 진행.

**2. Placeholder scan:**
- "TBD"/"TODO" 없음. 모든 step에 실제 코드/명령.
- "정확한 패키지/메서드는 grep해서 확인" 부분(P1.2 `AbstractIntegrationTest`, P3.4 `IbizDriveUserDetails`)은 placeholder가 아니라 working note (다른 admin 클래스에 동일 import가 있어 카피)).

**3. Type consistency:**
- `AdminCronToggledEvent` 6-arg record (actorId, actorIp, userAgent, jobKey, fromEnabled, toEnabled) — P2.2와 P3.3 일치.
- `AdminSystemService.toggleCron(String, boolean, UUID, InetAddress, String)` — P3.3 정의, P3.4 controller 호출 일치.
- `api.adminToggleCron(key: string, enabled: boolean): Promise<void>` — P5.3 정의, P5.4 hook + P6.2 page 사용 일치.
- testid `cron-toggle-{key}` / `cron-confirm-dialog` / `cron-confirm-cancel` / `cron-confirm-confirm` — P6.1 테스트와 P6.2 컴포넌트 일치.
- audit_log metadata key/fromEnabled/toEnabled — P2.2 listener와 spec §6.4 일치.

빠진 정합 0.
