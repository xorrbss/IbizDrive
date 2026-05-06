package com.ibizdrive.department;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Department} 도메인 메서드 단위 테스트 — admin-department-crud (Wave 2 T4).
 *
 * <p>{@link Department#rename(String)} / {@link Department#deactivate()} /
 * {@link Department#reactivate()} 상태 전이와 입력 검증을 다룬다.
 * 충돌 검출(UNIQUE 위반)은 service/repository 통합 테스트의 책임 — 본 클래스 범위 밖.
 */
class DepartmentTest {

    @Test
    void rename_trimsAndUpdatesName() {
        Department dept = activeDept("Old");

        dept.rename("  New Team  ");

        assertThat(dept.getName()).isEqualTo("New Team");
    }

    @Test
    void rename_rejectsNull() {
        Department dept = activeDept("Old");

        assertThatThrownBy(() -> dept.rename(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null");
    }

    @Test
    void rename_rejectsBlank() {
        Department dept = activeDept("Old");

        assertThatThrownBy(() -> dept.rename("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void rename_rejectsTooLong() {
        Department dept = activeDept("Old");
        String tooLong = "a".repeat(101);

        assertThatThrownBy(() -> dept.rename(tooLong))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("100");
    }

    @Test
    void rename_acceptsBoundaryLength() {
        Department dept = activeDept("Old");
        String exact100 = "a".repeat(100);

        dept.rename(exact100);

        assertThat(dept.getName()).isEqualTo(exact100);
    }

    @Test
    void deactivate_setsDeletedAt() {
        Department dept = activeDept("Eng");
        assertThat(dept.isActive()).isTrue();
        assertThat(dept.getDeletedAt()).isNull();

        dept.deactivate();

        assertThat(dept.isActive()).isFalse();
        assertThat(dept.isDeleted()).isTrue();
        assertThat(dept.getDeletedAt()).isNotNull();
    }

    @Test
    void deactivate_isIdempotent_preservesOriginalDeletedAt() {
        Department dept = activeDept("Eng");
        dept.deactivate();
        OffsetDateTime first = dept.getDeletedAt();

        // 도메인 시간 변경 가능성을 줄이기 위해 즉시 재호출
        dept.deactivate();

        // 두 번째 호출은 deletedAt을 새로 갱신하지 않는다 (최초 비활성 시각 보존).
        assertThat(dept.getDeletedAt()).isEqualTo(first);
    }

    @Test
    void reactivate_clearsDeletedAt() {
        Department dept = activeDept("Eng");
        dept.deactivate();
        assertThat(dept.isActive()).isFalse();

        dept.reactivate();

        assertThat(dept.isActive()).isTrue();
        assertThat(dept.getDeletedAt()).isNull();
    }

    @Test
    void reactivate_isIdempotent_whenAlreadyActive() {
        Department dept = activeDept("Eng");
        assertThat(dept.isActive()).isTrue();

        dept.reactivate();

        assertThat(dept.isActive()).isTrue();
        assertThat(dept.getDeletedAt()).isNull();
    }

    private static Department activeDept(String name) {
        return new Department(UUID.randomUUID(), name, OffsetDateTime.now());
    }
}
