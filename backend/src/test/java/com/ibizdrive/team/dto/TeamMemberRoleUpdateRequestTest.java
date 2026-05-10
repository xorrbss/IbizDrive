package com.ibizdrive.team.dto;

import com.ibizdrive.team.TeamMembership;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TeamMemberRoleUpdateRequestTest {

    private final Validator validator;

    TeamMemberRoleUpdateRequestTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            this.validator = factory.getValidator();
        }
    }

    @Test
    void roleNotNull_isRequired() {
        TeamMemberRoleUpdateRequest req = new TeamMemberRoleUpdateRequest(null);
        Set<ConstraintViolation<TeamMemberRoleUpdateRequest>> v = validator.validate(req);
        assertThat(v).hasSize(1);
        assertThat(v.iterator().next().getPropertyPath().toString()).isEqualTo("role");
    }

    @Test
    void validRole_passes() {
        TeamMemberRoleUpdateRequest req = new TeamMemberRoleUpdateRequest(TeamMembership.Role.OWNER);
        Set<ConstraintViolation<TeamMemberRoleUpdateRequest>> v = validator.validate(req);
        assertThat(v).isEmpty();
    }
}
