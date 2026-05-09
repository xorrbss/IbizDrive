package com.ibizdrive.workspace;

import com.ibizdrive.department.Department;

import java.util.Objects;
import java.util.UUID;

/**
 * {@link Workspace} adapter for {@link Department}.
 *
 * <p>Thin passthrough record — all method calls delegate directly to the wrapped entity.
 * Caller must supply a non-null {@code Department} instance; a null argument will fail
 * fast at construction via {@link Objects#requireNonNull}.
 */
public record DepartmentWorkspace(Department dept) implements Workspace {

    /** Compact constructor: fail fast on null input. */
    public DepartmentWorkspace {
        Objects.requireNonNull(dept, "dept must not be null");
    }

    @Override
    public WorkspaceKind kind() { return WorkspaceKind.DEPARTMENT; }

    @Override
    public UUID id() { return dept.getId(); }

    @Override
    public String name() { return dept.getName(); }

    @Override
    public UUID rootFolderId() { return dept.getRootFolderId(); }

    @Override
    public boolean isActive() { return dept.isActive(); }
}
