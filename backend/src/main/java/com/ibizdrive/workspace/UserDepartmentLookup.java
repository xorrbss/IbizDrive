package com.ibizdrive.workspace;

import java.util.Optional;
import java.util.UUID;

/**
 * 사용자의 소속 부서 조회 추상화 — Plan A Task 14.
 *
 * <p>{@link WorkspaceService}가 {@link com.ibizdrive.user.User}에 직접 의존하지 않도록 두는
 * indirection. 테스트 시 mock 대체 가능. production 구현은 {@link DefaultUserDepartmentLookup}.
 */
public interface UserDepartmentLookup {

    /**
     * 사용자의 소속 부서 id를 조회.
     *
     * @param userId 조회 대상 사용자
     * @return 소속 부서 id; user가 부서 미배정 또는 미존재면 empty
     */
    Optional<UUID> departmentIdOf(UUID userId);
}
