package com.ibizdrive.department;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Department#attachRootFolder(UUID)} 단위 테스트 — team-centric pivot Plan A Task 9.
 *
 * <p>Workspace pivot(spec §1.3): root folder는 부서 생성 트랜잭션에서 정확히 1회만 attach되며
 * 재할당은 root invariant 위반. detach/replace 메서드는 의도적으로 부재 (YAGNI).
 */
class DepartmentRootFolderTest {

    @Test
    void attachRootFolder_setsRootFolderId() {
        Department dept = activeDept("Eng");
        UUID folderId = UUID.randomUUID();

        dept.attachRootFolder(folderId);

        assertThat(dept.getRootFolderId()).isEqualTo(folderId);
    }

    @Test
    void rejectAttachIfAlreadySet() {
        Department dept = activeDept("Eng");
        dept.attachRootFolder(UUID.randomUUID());
        UUID second = UUID.randomUUID();

        assertThatThrownBy(() -> dept.attachRootFolder(second))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already attached");
    }

    @Test
    void rejectAttachNull() {
        Department dept = activeDept("Eng");

        assertThatThrownBy(() -> dept.attachRootFolder(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null");
    }

    private static Department activeDept(String name) {
        return new Department(UUID.randomUUID(), name, OffsetDateTime.now());
    }
}
