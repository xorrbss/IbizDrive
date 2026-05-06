package com.ibizdrive.admin;

import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.department.Department;
import com.ibizdrive.department.DepartmentConflictException;
import com.ibizdrive.department.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminDepartmentService} 단위 테스트 — admin-department-crud (Wave 2 T4).
 *
 * <p>{@link AdminUserServiceTest} 패턴 mirror — repository/publisher mock + ArgumentCaptor로 publish payload 검증.
 * Repository는 `@DataJpaTest` 통합 테스트({@link com.ibizdrive.department.DepartmentRepositoryTest})와 분리.
 */
class AdminDepartmentServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private DepartmentRepository departmentRepository;
    private ApplicationEventPublisher eventPublisher;
    private AdminDepartmentService service;

    @BeforeEach
    void setUp() {
        departmentRepository = mock(DepartmentRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new AdminDepartmentService(departmentRepository, eventPublisher);
    }

    // -------- list --------

    @Test
    void list_passesNullPatternForBlankQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        when(departmentRepository.findAllForAdminPageable(eq(null), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of()));

        Page<Department> result = service.list(pageable, "  ");

        assertThat(result.getContent()).isEmpty();
        verify(departmentRepository).findAllForAdminPageable(eq(null), eq(pageable));
    }

    @Test
    void list_lowercasesAndEscapesQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        when(departmentRepository.findAllForAdminPageable(any(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        service.list(pageable, "  Eng_Dev  ");

        ArgumentCaptor<String> patternCaptor = ArgumentCaptor.forClass(String.class);
        verify(departmentRepository).findAllForAdminPageable(patternCaptor.capture(), eq(pageable));
        // trim → lowercase → '_' escape → wildcard wrap.
        assertThat(patternCaptor.getValue()).isEqualTo("%eng\\_dev%");
    }

    // -------- create --------

    @Test
    void create_savesDepartmentAndPublishesEvent() {
        when(departmentRepository.findActiveByName("Eng")).thenReturn(Optional.empty());

        Department saved = service.create("  Eng  ", ACTOR_ID);

        assertThat(saved.getName()).isEqualTo("Eng");
        assertThat(saved.isActive()).isTrue();
        verify(departmentRepository).save(saved);

        ArgumentCaptor<AdminDepartmentCreatedEvent> ev = ArgumentCaptor.forClass(AdminDepartmentCreatedEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue().departmentId()).isEqualTo(saved.getId());
        assertThat(ev.getValue().actorId()).isEqualTo(ACTOR_ID);
        assertThat(ev.getValue().name()).isEqualTo("Eng");
    }

    @Test
    void create_throwsConflict_whenSameActiveNameExists() {
        Department existing = active("Eng");
        when(departmentRepository.findActiveByName("Eng")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create("Eng", ACTOR_ID))
            .isInstanceOf(DepartmentConflictException.class);

        verify(departmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void create_rejectsBlankName() {
        assertThatThrownBy(() -> service.create("   ", ACTOR_ID))
            .isInstanceOf(IllegalArgumentException.class);

        verify(departmentRepository, never()).save(any());
    }

    // -------- rename --------

    @Test
    void rename_updatesNameAndPublishesUpdatedEvent() {
        Department dept = active("Old");
        when(departmentRepository.findById(dept.getId())).thenReturn(Optional.of(dept));
        when(departmentRepository.findActiveByName("New")).thenReturn(Optional.empty());

        service.rename(dept.getId(), "New", ACTOR_ID);

        assertThat(dept.getName()).isEqualTo("New");
        verify(departmentRepository).save(dept);

        ArgumentCaptor<AdminDepartmentUpdatedEvent> ev = ArgumentCaptor.forClass(AdminDepartmentUpdatedEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue().departmentId()).isEqualTo(dept.getId());
        assertThat(ev.getValue().beforeJson()).isEqualTo("{\"name\":\"Old\"}");
        assertThat(ev.getValue().afterJson()).isEqualTo("{\"name\":\"New\"}");
    }

    @Test
    void rename_isIdempotent_whenSameName() {
        Department dept = active("Same");
        when(departmentRepository.findById(dept.getId())).thenReturn(Optional.of(dept));

        service.rename(dept.getId(), "Same", ACTOR_ID);

        verify(departmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void rename_throwsConflict_whenAnotherActiveHasSameName() {
        Department dept = active("Old");
        Department other = active("New");
        when(departmentRepository.findById(dept.getId())).thenReturn(Optional.of(dept));
        when(departmentRepository.findActiveByName("New")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.rename(dept.getId(), "New", ACTOR_ID))
            .isInstanceOf(DepartmentConflictException.class);

        verify(departmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void rename_throwsNotFound_whenMissing() {
        UUID missing = UUID.randomUUID();
        when(departmentRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rename(missing, "New", ACTOR_ID))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rename_acceptsTrimmedNewName() {
        Department dept = active("Old");
        when(departmentRepository.findById(dept.getId())).thenReturn(Optional.of(dept));
        when(departmentRepository.findActiveByName("Trimmed")).thenReturn(Optional.empty());

        service.rename(dept.getId(), "  Trimmed  ", ACTOR_ID);

        assertThat(dept.getName()).isEqualTo("Trimmed");
    }

    // -------- deactivate --------

    @Test
    void deactivate_softDeletesAndPublishesEvent() {
        Department dept = active("Eng");
        when(departmentRepository.findById(dept.getId())).thenReturn(Optional.of(dept));

        service.deactivate(dept.getId(), ACTOR_ID);

        assertThat(dept.isActive()).isFalse();
        verify(departmentRepository).save(dept);
        ArgumentCaptor<AdminDepartmentDeactivatedEvent> ev =
            ArgumentCaptor.forClass(AdminDepartmentDeactivatedEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue().departmentId()).isEqualTo(dept.getId());
        assertThat(ev.getValue().actorId()).isEqualTo(ACTOR_ID);
    }

    @Test
    void deactivate_isIdempotent_whenAlreadyInactive() {
        Department dept = active("Eng");
        dept.deactivate(); // already inactive
        when(departmentRepository.findById(dept.getId())).thenReturn(Optional.of(dept));

        service.deactivate(dept.getId(), ACTOR_ID);

        verify(departmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void deactivate_throwsNotFound_whenMissing() {
        UUID missing = UUID.randomUUID();
        when(departmentRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(missing, ACTOR_ID))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------- reactivate --------

    @Test
    void reactivate_clearsDeletedAtAndPublishesUpdatedEvent() {
        Department dept = active("Eng");
        dept.deactivate();
        when(departmentRepository.findById(dept.getId())).thenReturn(Optional.of(dept));
        when(departmentRepository.findActiveByName("Eng")).thenReturn(Optional.empty());

        service.reactivate(dept.getId(), ACTOR_ID);

        assertThat(dept.isActive()).isTrue();
        verify(departmentRepository).save(dept);
        ArgumentCaptor<AdminDepartmentUpdatedEvent> ev =
            ArgumentCaptor.forClass(AdminDepartmentUpdatedEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue().beforeJson()).isEqualTo("{\"isActive\":false}");
        assertThat(ev.getValue().afterJson()).isEqualTo("{\"isActive\":true}");
    }

    @Test
    void reactivate_isIdempotent_whenAlreadyActive() {
        Department dept = active("Eng");
        when(departmentRepository.findById(dept.getId())).thenReturn(Optional.of(dept));

        service.reactivate(dept.getId(), ACTOR_ID);

        verify(departmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void reactivate_throwsConflict_whenSameActiveNameExists() {
        Department dept = active("Eng");
        dept.deactivate();
        Department other = active("Eng");
        when(departmentRepository.findById(dept.getId())).thenReturn(Optional.of(dept));
        when(departmentRepository.findActiveByName("Eng")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.reactivate(dept.getId(), ACTOR_ID))
            .isInstanceOf(DepartmentConflictException.class);

        verify(departmentRepository, never()).save(any());
    }

    // -------- helpers --------

    private static Department active(String name) {
        return new Department(UUID.randomUUID(), name, OffsetDateTime.now());
    }
}
