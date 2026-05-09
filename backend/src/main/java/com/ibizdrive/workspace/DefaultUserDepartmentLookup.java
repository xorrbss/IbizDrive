package com.ibizdrive.workspace;

import com.ibizdrive.user.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * production {@link UserDepartmentLookup} — V7 {@code users.department_id} 컬럼 직접 read.
 *
 * <p>{@link UserRepository#findById}로 user를 fetch하고 {@link com.ibizdrive.user.User#getDepartmentId()}
 * 결과를 노출. user가 없거나 department_id가 null이면 empty.
 */
@Component
public class DefaultUserDepartmentLookup implements UserDepartmentLookup {

    private final UserRepository userRepo;

    public DefaultUserDepartmentLookup(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public Optional<UUID> departmentIdOf(UUID userId) {
        return userRepo.findById(userId).map(u -> u.getDepartmentId());
    }
}
