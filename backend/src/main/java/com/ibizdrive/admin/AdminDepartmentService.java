package com.ibizdrive.admin;

import com.ibizdrive.department.Department;
import com.ibizdrive.department.DepartmentConflictException;
import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderMutationService;
import com.ibizdrive.folder.ScopeType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * admin-department-crud (Wave 2 T4) — 부서 admin CRUD 서비스.
 *
 * <p>{@link AdminUserService} 1:1 패턴. {@code list/create/rename/deactivate/reactivate} 5개 메서드.
 *
 * <p><b>이벤트 매핑</b>:
 * <ul>
 *   <li>create → {@link AdminDepartmentCreatedEvent}</li>
 *   <li>rename → {@link AdminDepartmentUpdatedEvent} (before/after {@code {name}})</li>
 *   <li>reactivate → {@link AdminDepartmentUpdatedEvent} (before/after {@code {isActive}})</li>
 *   <li>deactivate → {@link AdminDepartmentDeactivatedEvent}</li>
 * </ul>
 *
 * <p>모든 메서드는 멱등 — 같은 상태에서의 재호출은 no-op + event 미발행. {@link AdminDepartmentAuditListener}는
 * AFTER_COMMIT 트리거이므로 audit는 commit 이후 emit된다.
 */
@Service
public class AdminDepartmentService {

    private final DepartmentRepository departmentRepository;
    private final FolderMutationService folderMutationService;
    private final ApplicationEventPublisher eventPublisher;

    public AdminDepartmentService(DepartmentRepository departmentRepository,
                                  FolderMutationService folderMutationService,
                                  ApplicationEventPublisher eventPublisher) {
        this.departmentRepository = departmentRepository;
        this.folderMutationService = folderMutationService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * admin 부서 목록 조회. q는 nullable — null/blank이면 전체.
     *
     * <p>q는 trim + lowercase + LIKE wildcard escape + {@code %...%} wrap 후 repository에 위임.
     * {@link com.ibizdrive.department.DepartmentSearchService#search} 1:1 답습 (단, minLen 검증 없음 — admin은 빈 q 허용).
     */
    @Transactional(readOnly = true)
    public Page<Department> list(Pageable pageable, String rawQuery) {
        String pattern = toLikePattern(rawQuery);
        return departmentRepository.findAllForAdminPageable(pattern, pageable);
    }

    /**
     * 부서 신규 생성. trim + 검증(도메인 메서드 재사용 X — entity 생성자는 trim 없으므로 service에서 trim).
     *
     * <p>충돌 검사: V9 partial unique를 진실로 두되, 사전 조회로 race window를 좁힘. 사전 검사 통과 후
     * INSERT가 unique 위반으로 실패하는 race는 MVP 허용 (auth signup과 동일 정책).
     *
     * @throws IllegalArgumentException     name이 null/blank/길이 위반
     * @throws DepartmentConflictException  활성 부서에 동일 name 존재
     */
    @Transactional
    public Department create(String rawName, UUID actorId) {
        String name = normalizeName(rawName);
        if (departmentRepository.findActiveByName(name).isPresent()) {
            throw new DepartmentConflictException();
        }

        Department dept = new Department(UUID.randomUUID(), name, OffsetDateTime.now());
        departmentRepository.save(dept);

        Folder root = folderMutationService.createRootForScope(
            ScopeType.DEPARTMENT, dept.getId(), actorId, name);
        dept.attachRootFolder(root.getId());

        eventPublisher.publishEvent(new AdminDepartmentCreatedEvent(dept.getId(), actorId, name));
        return dept;
    }

    /**
     * 부서 이름 변경. 같은 이름이면 no-op + event 미발행 (멱등).
     *
     * @throws ResourceNotFoundException    target 미존재
     * @throws IllegalArgumentException     newName 검증 실패 (도메인 메서드)
     * @throws DepartmentConflictException  다른 활성 부서에 동일 newName 존재
     */
    @Transactional
    public Department rename(UUID departmentId, String rawNewName, UUID actorId) {
        if (departmentId == null) throw new IllegalArgumentException("departmentId must not be null");
        Department dept = departmentRepository.findById(departmentId)
            .orElseThrow(() -> new ResourceNotFoundException("department not found: " + departmentId));

        String newName = normalizeName(rawNewName);
        String oldName = dept.getName();
        if (oldName.equals(newName)) {
            return dept; // 멱등 — same name.
        }

        // 다른 활성 부서가 동일 name을 들고 있으면 충돌. 자기 자신은 deletedAt 기준 active일 수도, 아닐 수도 있는데
        // active+자기자신은 위에서 같은 이름으로 걸러지므로 여기서 자기자신은 발견되지 않는다.
        departmentRepository.findActiveByName(newName).ifPresent(existing -> {
            if (!existing.getId().equals(departmentId)) {
                throw new DepartmentConflictException();
            }
        });

        dept.rename(newName);
        departmentRepository.save(dept);

        String beforeJson = "{\"name\":" + jsonString(oldName) + "}";
        String afterJson = "{\"name\":" + jsonString(newName) + "}";
        eventPublisher.publishEvent(new AdminDepartmentUpdatedEvent(departmentId, actorId, beforeJson, afterJson));
        return dept;
    }

    /**
     * 부서 비활성화 (soft-delete). 이미 비활성이면 멱등.
     *
     * @throws ResourceNotFoundException target 미존재
     */
    @Transactional
    public Department deactivate(UUID departmentId, UUID actorId) {
        if (departmentId == null) throw new IllegalArgumentException("departmentId must not be null");
        Department dept = departmentRepository.findById(departmentId)
            .orElseThrow(() -> new ResourceNotFoundException("department not found: " + departmentId));

        if (!dept.isActive()) {
            return dept; // 멱등 — 이미 비활성.
        }

        dept.deactivate();
        departmentRepository.save(dept);

        eventPublisher.publishEvent(new AdminDepartmentDeactivatedEvent(departmentId, actorId));
        return dept;
    }

    /**
     * 부서 재활성화. 이미 활성이면 멱등.
     *
     * <p>충돌 케이스: A 비활성 + B 활성(같은 이름) → A 재활성화 시 B와 unique 충돌. 사전 검사로 차단.
     *
     * @throws ResourceNotFoundException    target 미존재
     * @throws DepartmentConflictException  같은 name 활성 부서 존재
     */
    @Transactional
    public Department reactivate(UUID departmentId, UUID actorId) {
        if (departmentId == null) throw new IllegalArgumentException("departmentId must not be null");
        Department dept = departmentRepository.findById(departmentId)
            .orElseThrow(() -> new ResourceNotFoundException("department not found: " + departmentId));

        if (dept.isActive()) {
            return dept; // 멱등 — 이미 활성.
        }

        // reactivate 후 동일 name 활성 부서가 존재하면 V9 unique 위반 — 사전 차단.
        departmentRepository.findActiveByName(dept.getName()).ifPresent(other -> {
            throw new DepartmentConflictException();
        });

        dept.reactivate();
        departmentRepository.save(dept);

        String beforeJson = "{\"isActive\":false}";
        String afterJson = "{\"isActive\":true}";
        eventPublisher.publishEvent(new AdminDepartmentUpdatedEvent(departmentId, actorId, beforeJson, afterJson));
        return dept;
    }

    // ---------- helpers ----------

    private static String normalizeName(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("name must be at most 100 characters");
        }
        return trimmed;
    }

    /**
     * 검색용 LIKE 패턴 변환 — null/blank 허용 (admin list는 빈 q를 전체로 해석).
     * A14/{@link com.ibizdrive.department.DepartmentSearchService#escapeLike}와 동일 규칙.
     */
    private static String toLikePattern(String rawQuery) {
        if (rawQuery == null) return null;
        String trimmed = rawQuery.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return null;
        return "%" + escapeLike(trimmed) + "%";
    }

    private static String escapeLike(String input) {
        StringBuilder sb = new StringBuilder(input.length() + 8);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' || c == '%' || c == '_') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * audit before/after JSON 직렬화용 문자열 quote — Jackson 의존 회피.
     * {@code "}와 {@code \\}만 escape (부서 name은 100자 ASCII/Unicode 일반 문자열, 컨트롤 문자는 service 정규화 단계에서 사실상 trim).
     */
    private static String jsonString(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
