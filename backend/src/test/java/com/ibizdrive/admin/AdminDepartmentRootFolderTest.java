package com.ibizdrive.admin;

import com.ibizdrive.department.Department;
import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderMutationService;
import com.ibizdrive.folder.ScopeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plan A Task 20: AdminDepartmentService.create attaches a root folder via FolderMutationService.
 * peer = TeamServiceCreateTest. Mockito unit test (no DB).
 */
class AdminDepartmentRootFolderTest {

    private DepartmentRepository deptRepo;
    private FolderMutationService folderService;
    private ApplicationEventPublisher events;
    private AdminDepartmentService svc;

    @BeforeEach
    void setUp() {
        deptRepo = Mockito.mock(DepartmentRepository.class);
        folderService = Mockito.mock(FolderMutationService.class);
        events = Mockito.mock(ApplicationEventPublisher.class);
        svc = new AdminDepartmentService(deptRepo, folderService, events);

        when(deptRepo.findActiveByName(anyString())).thenReturn(Optional.empty());
        // folderService stub — return a mocked Folder with random id
        when(folderService.createRootForScope(any(), any(), any(), anyString()))
            .thenAnswer(inv -> {
                Folder f = Mockito.mock(Folder.class);
                when(f.getId()).thenReturn(UUID.randomUUID());
                return f;
            });
    }

    @Test
    void create_attachesRootFolder_withDepartmentScope() {
        UUID admin = UUID.randomUUID();

        Department result = svc.create("Sales", admin);

        // department saved
        ArgumentCaptor<Department> deptCaptor = ArgumentCaptor.forClass(Department.class);
        verify(deptRepo).save(deptCaptor.capture());
        Department savedDept = deptCaptor.getValue();
        assertThat(savedDept.getName()).isEqualTo("Sales");

        // root folder created via FolderMutationService.createRootForScope
        ArgumentCaptor<UUID> scopeIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(folderService).createRootForScope(
            eq(ScopeType.DEPARTMENT), scopeIdCaptor.capture(), eq(admin), nameCaptor.capture());
        assertThat(scopeIdCaptor.getValue()).isEqualTo(savedDept.getId());
        assertThat(nameCaptor.getValue()).isEqualTo("Sales");

        // attachRootFolder called on dept (dept.getRootFolderId() not null on returned Department)
        assertThat(result.getRootFolderId()).isNotNull();

        // event published
        ArgumentCaptor<AdminDepartmentCreatedEvent> eventCaptor =
            ArgumentCaptor.forClass(AdminDepartmentCreatedEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        AdminDepartmentCreatedEvent published = eventCaptor.getValue();
        assertThat(published.departmentId()).isEqualTo(savedDept.getId());
        assertThat(published.actorId()).isEqualTo(admin);
        assertThat(published.name()).isEqualTo("Sales");
    }
}
