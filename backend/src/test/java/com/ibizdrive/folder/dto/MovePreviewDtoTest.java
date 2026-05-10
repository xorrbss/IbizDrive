package com.ibizdrive.folder.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.permission.Permission;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MovePreviewDtoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requestRoundTrip() throws Exception {
        UUID dest = UUID.randomUUID();
        String json = mapper.writeValueAsString(new MovePreviewRequest(dest));
        assertThat(json).contains("destinationFolderId");
        MovePreviewRequest parsed = mapper.readValue(json, MovePreviewRequest.class);
        assertThat(parsed.destinationFolderId()).isEqualTo(dest);
    }

    @Test
    void responseShapeIncludesAllFields() throws Exception {
        UUID permId = UUID.randomUUID();
        UUID shareId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID sharedBy = UUID.randomUUID();
        MovePreviewResponse resp = new MovePreviewResponse(
            5,
            List.of(new PermissionRef(permId, "user", subjectId, "EDIT")),
            List.of(new ShareRef(shareId, "folder", resourceId, sharedBy)),
            List.of(Permission.READ, Permission.UPLOAD),
            null
        );
        String json = mapper.writeValueAsString(resp);
        assertThat(json).contains("itemCount");
        assertThat(json).contains("removedPermissions");
        assertThat(json).contains("revokedShares");
        assertThat(json).contains("targetMembershipDefaults");
    }
}
