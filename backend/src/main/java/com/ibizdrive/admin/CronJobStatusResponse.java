package com.ibizdrive.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Wave 1 — T3: {@code /admin/system} 페이지가 노출하는 단일 cron 잡 설정 스냅샷.
 *
 * <p>4종 잡(`purge.expired`, `share.expire`, `permission.expire`, `storage.orphan.cleanup`)을
 * 단일 record로 표현하기 위해 잡-specific 필드(`batchSize`/`maxPerRun`/`graceHours`)는 옵셔널 +
 * {@link JsonInclude#NON_NULL}로 직렬화 — 4종 union 분리 시 클라이언트가 discriminator 분기를
 * 해야 해 KISS 위배. 본 트랙은 read-only이므로 setter/builder 미제공.
 *
 * @param key        cron 식별자 (application.yml 키와 동형, e.g. {@code "purge.expired"}).
 * @param label      한국어 사람이 읽는 명칭 — `/admin/system` 카드에 그대로 노출.
 * @param enabled    현재 profile에서의 활성화 여부 (config-bean 시점 스냅샷).
 * @param cron       Spring cron 표현식 (6필드).
 * @param zone       cron 평가 시간대.
 * @param batchSize  share/permission 잡의 단일 run 처리 상한. 그 외 null.
 * @param maxPerRun  purge/storage-orphan 잡의 단일 run 처리 상한. 그 외 null.
 * @param graceHours storage-orphan 전용 — mtime grace. 그 외 null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CronJobStatusResponse(
    String key,
    String label,
    boolean enabled,
    String cron,
    String zone,
    Integer batchSize,
    Integer maxPerRun,
    Integer graceHours
) {
}
