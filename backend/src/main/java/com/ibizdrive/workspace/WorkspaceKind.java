package com.ibizdrive.workspace;

/**
 * Discriminator enum for the two workspace scopes in the team-centric-pivot design
 * (spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1).
 *
 * <p>Used by {@link Workspace#kind()} to allow permission evaluation and sidebar API
 * to branch on scope type without instanceof checks.
 */
public enum WorkspaceKind { DEPARTMENT, TEAM }
