package com.ibizdrive.audit;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AuditEventTypeCrossWorkspaceTest {

    @Test
    void folderMovedCrossWorkspaceWire() {
        assertThat(AuditEventType.FOLDER_MOVED_CROSS_WORKSPACE.wire())
            .isEqualTo("folder.moved.cross_workspace");
    }

    @Test
    void fileMovedCrossWorkspaceWire() {
        assertThat(AuditEventType.FILE_MOVED_CROSS_WORKSPACE.wire())
            .isEqualTo("file.moved.cross_workspace");
    }

    @Test
    void roundTripFromWire() {
        assertThat(AuditEventType.from("folder.moved.cross_workspace"))
            .isEqualTo(AuditEventType.FOLDER_MOVED_CROSS_WORKSPACE);
        assertThat(AuditEventType.from("file.moved.cross_workspace"))
            .isEqualTo(AuditEventType.FILE_MOVED_CROSS_WORKSPACE);
    }
}
