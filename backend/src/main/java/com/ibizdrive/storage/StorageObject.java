package com.ibizdrive.storage;

import java.time.Instant;

/**
 * Storage object 메타 — orphan cleanup walk 결과 항목.
 *
 * <p>{@link StorageClient#listOlderThan(java.time.Duration)} stream의 element. {@code key}는
 * 도메인 형식({@code {YYYY}/{MM}/{UUID}})이며 {@code lastModified}는 객체의 mtime (UTC).
 *
 * <p>cleanup service는 {@code lastModified}를 grace boundary 검증에 사용하지 않는다 — boundary는
 * {@link StorageClient#listOlderThan(java.time.Duration)} 내부에서 이미 적용. 본 record의 mtime은
 * 진단/audit metadata용.
 *
 * @param key          object key (도메인 = {@code {YYYY}/{MM}/{UUID}})
 * @param lastModified UTC mtime
 */
public record StorageObject(String key, Instant lastModified) {
}
