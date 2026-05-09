package com.ibizdrive.team.dto;

import com.ibizdrive.team.Team;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/teams} request body — Plan A Task 19.
 *
 * <p>{@code visibility}는 nullable (controller가 {@link Team.Visibility#PRIVATE}로 default 처리).
 */
public record TeamCreateRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 1000) String description,
    Team.Visibility visibility
) {}
