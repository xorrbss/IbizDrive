package com.ibizdrive.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * {@link CronPolicy} JPA repository — cron tick guard와 admin toggle endpoint 양쪽에서 사용.
 *
 * <p>{@link #isEnabled(String)}은 cron tick(5분/자정/새벽 1시 단위)마다 호출되므로 single-row
 * lookup. 4-row 테이블 + PK 인덱스라 비용 미미.
 */
@Repository
public interface CronPolicyRepository extends JpaRepository<CronPolicy, String> {

    /**
     * 주어진 cron 키의 enabled 값. row가 없으면 false (방어 — V11 시드 후엔 발생 안 해야 함).
     */
    @Query("SELECT COALESCE(MAX(CASE WHEN c.enabled = true THEN 1 ELSE 0 END), 0) " +
           "FROM CronPolicy c WHERE c.key = :key")
    int isEnabledRaw(@Param("key") String key);

    default boolean isEnabled(String key) {
        return isEnabledRaw(key) == 1;
    }
}
