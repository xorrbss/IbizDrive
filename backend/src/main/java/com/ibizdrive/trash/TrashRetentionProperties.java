package com.ibizdrive.trash;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 휴지통 보존 기간 설정 (application.yml {@code app.trash.retention-days}).
 *
 * <p>{@code FileMutationService}/{@code FolderMutationService}가 soft-delete 시점에
 * {@code purge_after = deleted_at + days}로 설정하는 보존 기간(일). 이 기간이 지난 row는
 * A7 hard purge cron({@code purge.expired})의 hard delete 후보가 된다 (docs/02 §6.5,
 * docs/04 §13).
 *
 * <p>기존 {@code PURGE_DAYS=30} 상수가 두 mutation service에 중복 하드코딩되었던 것을
 * 외부화 — 운영자가 application.yml 수정 + 재기동만으로 보존 기간 조정 가능. 무중단 변경은
 * v1.x++ ({@code AuditExportProperties} 패턴 동형).
 *
 * @param days 보존 기간(일). 0 이하 입력은 default 30으로 보정 (실수로 즉시 삭제 방지).
 */
@ConfigurationProperties(prefix = "app.trash.retention")
public record TrashRetentionProperties(int days) {
    public TrashRetentionProperties {
        if (days <= 0) days = 30;
    }
}
