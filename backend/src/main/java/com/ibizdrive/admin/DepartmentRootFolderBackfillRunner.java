package com.ibizdrive.admin;

import com.ibizdrive.department.Department;
import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderMutationService;
import com.ibizdrive.folder.ScopeType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 부서 root folder backfill — Plan A Task 21 (dev/test 환경용 idempotent 도구).
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §6.1.
 * 본 runner는 production cutover에서 자동 실행되지 않는다 (green-field 정책). 활성 부서 중 root_folder_id
 * null인 row를 발견하면 root Folder를 생성하고 부착. dev/test 환경에서 V14 마이그레이션 후 기존 부서를
 * 보강하기 위한 1회성 도구.
 *
 * <p><b>idempotency</b>: 이미 root_folder_id가 set된 부서는 skip. 비활성 부서도 skip
 * (Plan A2 reactivate 시 재고).
 *
 * <p><b>실행 방법</b>: admin endpoint 또는 application startup runner (config-flag 게이트). 본 task에서는
 * runner 본체만 만든다 — 실행 수단은 별도 admin 작업 또는 SQL 도구로.
 */
@Component
public class DepartmentRootFolderBackfillRunner {

    private final DepartmentRepository deptRepo;
    private final FolderMutationService folderService;

    public DepartmentRootFolderBackfillRunner(DepartmentRepository deptRepo,
                                              FolderMutationService folderService) {
        this.deptRepo = deptRepo;
        this.folderService = folderService;
    }

    /**
     * 활성 부서 중 root_folder_id null인 부서를 모두 backfill.
     *
     * @param createdBy root folder의 owner로 기록할 user id (보통 admin)
     * @return 생성된 root folder 수 (skip된 부서는 카운트에서 제외)
     */
    @Transactional
    public int run(UUID createdBy) {
        int created = 0;
        for (Department d : deptRepo.findAll()) {
            if (!d.isActive() || d.getRootFolderId() != null) {
                continue;
            }
            Folder root = folderService.createRootForScope(
                ScopeType.DEPARTMENT, d.getId(), createdBy, d.getName());
            d.attachRootFolder(root.getId());
            created++;
        }
        return created;
    }
}
