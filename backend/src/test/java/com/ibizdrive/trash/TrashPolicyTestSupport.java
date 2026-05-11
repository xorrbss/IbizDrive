package com.ibizdrive.trash;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 테스트용 헬퍼 — fixed retention days를 반환하는 {@link TrashPolicyService} mock 생성.
 *
 * <p>trash-retention-mutation Phase B 이후 {@link com.ibizdrive.file.FileMutationService}와
 * {@link com.ibizdrive.folder.FolderMutationService}가 yml({@link TrashRetentionProperties})
 * 대신 service에서 retention days를 받는다. slice 테스트는 DB-backed 실제 service 대신 mock 주입.
 */
public final class TrashPolicyTestSupport {

    private TrashPolicyTestSupport() {}

    public static TrashPolicyService stubReturning(int days) {
        TrashPolicyService stub = mock(TrashPolicyService.class);
        when(stub.getRetentionDays()).thenReturn(days);
        return stub;
    }
}
