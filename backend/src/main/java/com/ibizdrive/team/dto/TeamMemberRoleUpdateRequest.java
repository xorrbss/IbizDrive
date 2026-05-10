package com.ibizdrive.team.dto;

import com.ibizdrive.team.TeamMembership;
import jakarta.validation.constraints.NotNull;

/** {@code PATCH /api/teams/{teamId}/members/{userId}} request body — Plan F T1. */
public record TeamMemberRoleUpdateRequest(@NotNull TeamMembership.Role role) {}
