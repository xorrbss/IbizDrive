package com.ibizdrive.trash;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TrashRetentionProperties} record 검증 — 0/음수 입력 → default(30) 보정.
 *
 * <p>운영자가 application.yml에서 실수로 {@code 0} 또는 음수를 입력해도 30일 default로 fallback해
 * 즉시 hard purge 후보가 되는 사고를 방지 ({@link com.ibizdrive.audit.AuditExportProperties} 패턴 동형).
 */
class TrashRetentionPropertiesTest {

    @Test
    void positiveValueIsKept() {
        assertThat(new TrashRetentionProperties(7).days()).isEqualTo(7);
        assertThat(new TrashRetentionProperties(30).days()).isEqualTo(30);
        assertThat(new TrashRetentionProperties(365).days()).isEqualTo(365);
    }

    @Test
    void zeroIsCoercedToDefault() {
        assertThat(new TrashRetentionProperties(0).days()).isEqualTo(30);
    }

    @Test
    void negativeIsCoercedToDefault() {
        assertThat(new TrashRetentionProperties(-1).days()).isEqualTo(30);
        assertThat(new TrashRetentionProperties(-30).days()).isEqualTo(30);
    }
}
