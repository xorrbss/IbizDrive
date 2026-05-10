package com.ibizdrive.admin;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Admin team PATCH 요청 — T8 admin-teams.jsx TeamDetail "편집" + setLead/changeColor.
 *
 * <p>{@code PATCH /api/admin/teams/{id}} body. 모든 필드 nullable; 모두 null이면 controller에서
 * {@link AdminBadPatchException} → 400 VALIDATION_ERROR ({@link AdminDepartmentPatchRequest}와
 * 동일 pattern).
 *
 * <p><b>의미</b>:
 * <ul>
 *   <li>{@code name} != null/blank → rename</li>
 *   <li>{@code description} != null → updateDescription (blank → null로 정규화, 디자인 "설명 비움")</li>
 *   <li>{@code color} != null → changeColor (#RRGGBB hex 검증)</li>
 *   <li>{@code leadId} != null → assignLead (TeamMembership에 속한 user 검증은 service)</li>
 * </ul>
 *
 * @param name        새 팀 이름 (1~100자)
 * @param description 새 팀 설명 (최대 1000자, blank → null로 정규화)
 * @param color       새 색상 hex (#RRGGBB)
 * @param leadId      새 팀 lead user id
 */
public record AdminTeamPatchRequest(
    @Size(max = 100)
    String name,

    @Size(max = 1000)
    String description,

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must match #RRGGBB hex format")
    String color,

    UUID leadId
) {
    public boolean isEmpty() {
        return (name == null || name.isBlank())
            && description == null
            && (color == null || color.isBlank())
            && leadId == null;
    }
}
