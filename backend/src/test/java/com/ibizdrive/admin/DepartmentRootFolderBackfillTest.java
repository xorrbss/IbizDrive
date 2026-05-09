package com.ibizdrive.admin;

import com.ibizdrive.department.Department;
import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderMutationService;
import com.ibizdrive.folder.ScopeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plan A Task 21: DepartmentRootFolderBackfillRunner — Mockito unit test.
 * peer = AdminDepartmentRootFolderTest.
 */
class DepartmentRootFolderBackfillTest {

    private DepartmentRepository deptRepo;
    private FolderMutationService folderService;
    private DepartmentRootFolderBackfillRunner runner;

    @BeforeEach
    void setUp() {
        deptRepo = Mockito.mock(DepartmentRepository.class);
        folderService = Mockito.mock(FolderMutationService.class);
        runner = new DepartmentRootFolderBackfillRunner(deptRepo, folderService);

        when(folderService.createRootForScope(any(), any(), any(), anyString()))
            .thenAnswer(inv -> {
                Folder f = Mockito.mock(Folder.class);
                when(f.getId()).thenReturn(UUID.randomUUID());
                return f;
            });
    }

    @Test
    void run_createsRoot_forActiveDeptWithoutRoot() {
        UUID admin = UUID.randomUUID();
        Department legacy = new Department(UUID.randomUUID(), "Legacy", OffsetDateTime.now());
        when(deptRepo.findAll()).thenReturn(List.of(legacy));

        int created = runner.run(admin);

        assertThat(created).isEqualTo(1);
        verify(folderService).createRootForScope(
            eq(ScopeType.DEPARTMENT), eq(legacy.getId()), eq(admin), eq("Legacy"));
        assertThat(legacy.getRootFolderId()).isNotNull();
    }

    @Test
    void run_skipsDept_whenRootFolderAlreadyAttached() {
        UUID admin = UUID.randomUUID();
        Department alreadyHasRoot = new Department(UUID.randomUUID(), "Modern", OffsetDateTime.now());
        alreadyHasRoot.attachRootFolder(UUID.randomUUID());
        when(deptRepo.findAll()).thenReturn(List.of(alreadyHasRoot));

        int created = runner.run(admin);

        assertThat(created).isZero();
        verify(folderService, never()).createRootForScope(any(), any(), any(), anyString());
    }

    @Test
    void run_skipsInactiveDept() {
        UUID admin = UUID.randomUUID();
        Department inactive = new Department(UUID.randomUUID(), "Sunset", OffsetDateTime.now());
        inactive.deactivate();
        when(deptRepo.findAll()).thenReturn(List.of(inactive));

        int created = runner.run(admin);

        assertThat(created).isZero();
        verify(folderService, never()).createRootForScope(any(), any(), any(), anyString());
    }

    @Test
    void run_processesMultipleDepartments_independently() {
        UUID admin = UUID.randomUUID();
        Department a = new Department(UUID.randomUUID(), "A", OffsetDateTime.now());
        Department b = new Department(UUID.randomUUID(), "B", OffsetDateTime.now());
        Department c = new Department(UUID.randomUUID(), "C", OffsetDateTime.now());
        c.attachRootFolder(UUID.randomUUID()); // already has root — should skip
        when(deptRepo.findAll()).thenReturn(List.of(a, b, c));

        int created = runner.run(admin);

        assertThat(created).isEqualTo(2);
        verify(folderService, times(2))
            .createRootForScope(eq(ScopeType.DEPARTMENT), any(), eq(admin), anyString());
    }
}
