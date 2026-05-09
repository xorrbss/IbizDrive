package com.ibizdrive.team.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** {@code POST /api/teams/{id}/members} request body — Plan A Task 19. */
public record TeamMemberInviteRequest(@NotNull UUID userId) {}
