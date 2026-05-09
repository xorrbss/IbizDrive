package com.ibizdrive.share;

import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.permission.Preset;
import com.ibizdrive.permission.WorkspaceMembershipResolver;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShareGrantCapValidatorTest {

    private final WorkspaceMembershipResolver resolver = mock(WorkspaceMembershipResolver.class);
    private final ShareGrantCapValidator validator = new ShareGrantCapValidator(resolver);

    @Test
    void teamMember_canGrantEditPreset() {
        UUID sharer = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(resolver.resolve(sharer, ScopeType.TEAM, teamId))
            .thenReturn(Preset.EDIT.permissions());

        // EDIT preset ⊆ Preset.EDIT.permissions() → pass
        validator.validate(sharer, ScopeType.TEAM, teamId, Preset.EDIT);
        // throw 없음 = 통과
    }

    @Test
    void teamMember_cannotGrantAdminPreset() {
        UUID sharer = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(resolver.resolve(sharer, ScopeType.TEAM, teamId))
            .thenReturn(Preset.EDIT.permissions());

        // ADMIN preset includes PERMISSION_ADMIN which EDIT preset lacks → blocked
        assertThatThrownBy(() ->
            validator.validate(sharer, ScopeType.TEAM, teamId, Preset.ADMIN)
        ).isInstanceOf(ShareExceedsMembershipException.class);
    }

    /**
     * OWNER membership = Preset.ADMIN.permissions() = complement({PURGE}).
     * ADMIN preset = Preset.ADMIN.permissions() ⊆ OWNER cap → grant passes (spec §3.2).
     */
    @Test
    void teamOwner_canGrantAdminPreset() {
        UUID sharer = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(resolver.resolve(sharer, ScopeType.TEAM, teamId))
            .thenReturn(Preset.ADMIN.permissions());

        // ADMIN preset ⊆ Preset.ADMIN.permissions() → pass
        validator.validate(sharer, ScopeType.TEAM, teamId, Preset.ADMIN);
        // throw 없음 = 통과
    }

    @Test
    void departmentMember_grantUploadPreset_ok() {
        UUID sharer = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        when(resolver.resolve(sharer, ScopeType.DEPARTMENT, deptId))
            .thenReturn(Preset.UPLOAD.permissions());

        // UPLOAD preset ⊆ Preset.UPLOAD.permissions() → pass
        validator.validate(sharer, ScopeType.DEPARTMENT, deptId, Preset.UPLOAD);
    }

    @Test
    void departmentMember_grantEditPreset_blocked() {
        UUID sharer = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        when(resolver.resolve(sharer, ScopeType.DEPARTMENT, deptId))
            .thenReturn(Preset.UPLOAD.permissions());

        // EDIT preset includes EDIT, MOVE which UPLOAD preset lacks → blocked
        assertThatThrownBy(() ->
            validator.validate(sharer, ScopeType.DEPARTMENT, deptId, Preset.EDIT)
        ).isInstanceOf(ShareExceedsMembershipException.class);
    }
}
