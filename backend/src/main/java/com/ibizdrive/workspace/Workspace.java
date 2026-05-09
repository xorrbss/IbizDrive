package com.ibizdrive.workspace;

import java.util.UUID;

/**
 * Polymorphic facade over {@code Department} and {@code Team} for permission evaluation
 * and sidebar API (spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1).
 *
 * <p>Callers obtain a {@code Workspace} via {@link DepartmentWorkspace} or {@link TeamWorkspace}
 * adapter records. No factory method is provided — adapters are constructed directly (YAGNI).
 *
 * <p>This interface is intentionally non-sealed. The two known implementations cover the current
 * scope; a sealed hierarchy would require a {@code permits} clause that couples this interface to
 * concrete adapters (KISS).
 */
public interface Workspace {

    /**
     * Discriminator for permission evaluation and routing.
     *
     * @return {@link WorkspaceKind#DEPARTMENT} or {@link WorkspaceKind#TEAM} — never null
     */
    WorkspaceKind kind();

    /**
     * Scope identifier used in {@code folders.scope_id} and {@code permissions.subject_id}
     * (spec §1). Matches the underlying entity's primary key.
     *
     * @return non-null UUID identifying this workspace scope
     */
    UUID id();

    /**
     * Display name of the workspace as stored on the underlying entity.
     *
     * @return non-null, non-blank display name
     */
    String name();

    /**
     * Root folder UUID for this workspace scope. May be {@code null} for departments whose
     * root folder has not yet been created (Plan A2 backfill).
     *
     * @return root folder UUID, or {@code null} if not yet attached
     */
    UUID rootFolderId();

    /**
     * Whether the workspace is currently active (not soft-deleted or archived).
     * For departments this mirrors {@code deletedAt == null}; for teams it mirrors
     * {@code archivedAt == null}.
     *
     * @return {@code true} if the workspace is active
     */
    boolean isActive();
}
