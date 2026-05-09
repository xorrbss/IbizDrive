package com.ibizdrive.workspace;

import com.ibizdrive.team.Team;

import java.util.Objects;
import java.util.UUID;

/**
 * {@link Workspace} adapter for {@link Team}.
 *
 * <p>Thin passthrough record — all method calls delegate directly to the wrapped entity.
 * Caller must supply a non-null {@code Team} instance; a null argument will fail
 * fast at construction via {@link Objects#requireNonNull}.
 */
public record TeamWorkspace(Team team) implements Workspace {

    /** Compact constructor: fail fast on null input. */
    public TeamWorkspace {
        Objects.requireNonNull(team, "team must not be null");
    }

    @Override
    public WorkspaceKind kind() { return WorkspaceKind.TEAM; }

    @Override
    public UUID id() { return team.getId(); }

    @Override
    public String name() { return team.getName(); }

    @Override
    public UUID rootFolderId() { return team.getRootFolderId(); }

    @Override
    public boolean isActive() { return team.isActive(); }
}
