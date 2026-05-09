package com.ibizdrive.share;

import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.Preset;
import com.ibizdrive.permission.WorkspaceMembershipResolver;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
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
            .thenReturn(EnumSet.of(Permission.READ, Permission.UPLOAD, Permission.EDIT,
                Permission.MOVE, Permission.DOWNLOAD, Permission.DELETE));

        // EDIT preset = {READ, UPLOAD, EDIT, MOVE, DOWNLOAD, DELETE} ⊆ MEMBER+ cap → pass
        validator.validate(sharer, ScopeType.TEAM, teamId, Preset.EDIT);
        // throw 없음 = 통과
    }

    @Test
    void teamMember_cannotGrantAdminPreset() {
        UUID sharer = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(resolver.resolve(sharer, ScopeType.TEAM, teamId))
            .thenReturn(EnumSet.of(Permission.READ, Permission.UPLOAD, Permission.EDIT));

        assertThatThrownBy(() ->
            validator.validate(sharer, ScopeType.TEAM, teamId, Preset.ADMIN)
        ).isInstanceOf(ShareExceedsMembershipException.class);
    }

    /**
     * ADMIN preset = complement({PURGE}) = {READ, UPLOAD, EDIT, MOVE, DOWNLOAD, DELETE, SHARE, PERMISSION_ADMIN}.
     * OWNER membership = {READ, UPLOAD, EDIT, DELETE, SHARE} — excludes MOVE, DOWNLOAD, PERMISSION_ADMIN.
     * Therefore OWNER ⊉ ADMIN.permissions() → grant is blocked (spec-correct: no one grants
     * above their own cap; ADMIN preset exceeds any membership-derived cap).
     */
    @Test
    void teamOwner_cannotGrantAdminPreset_becauseAdminIncludesPermissionAdmin() {
        UUID sharer = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(resolver.resolve(sharer, ScopeType.TEAM, teamId))
            .thenReturn(EnumSet.of(
                Permission.READ, Permission.UPLOAD, Permission.EDIT,
                Permission.DELETE, Permission.SHARE));

        // ADMIN preset includes PERMISSION_ADMIN (and MOVE, DOWNLOAD) which OWNER membership does not grant.
        assertThatThrownBy(() ->
            validator.validate(sharer, ScopeType.TEAM, teamId, Preset.ADMIN)
        ).isInstanceOf(ShareExceedsMembershipException.class);
    }

    @Test
    void departmentMember_grantUploadPreset_ok() {
        UUID sharer = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        when(resolver.resolve(sharer, ScopeType.DEPARTMENT, deptId))
            .thenReturn(EnumSet.of(Permission.READ, Permission.UPLOAD, Permission.DOWNLOAD));

        // UPLOAD preset = {READ, UPLOAD, DOWNLOAD} ⊆ dept membership → pass
        validator.validate(sharer, ScopeType.DEPARTMENT, deptId, Preset.UPLOAD);
    }

    @Test
    void departmentMember_grantEditPreset_blocked() {
        UUID sharer = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        when(resolver.resolve(sharer, ScopeType.DEPARTMENT, deptId))
            .thenReturn(EnumSet.of(Permission.READ, Permission.UPLOAD));

        assertThatThrownBy(() ->
            validator.validate(sharer, ScopeType.DEPARTMENT, deptId, Preset.EDIT)
        ).isInstanceOf(ShareExceedsMembershipException.class);
    }
}
