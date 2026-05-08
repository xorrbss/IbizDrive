package com.ibizdrive.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuditExportProperties} record 검증 — 0/음수 입력 → default(10000) 보정.
 */
class AuditExportPropertiesTest {

    @Test
    void positiveValueIsKept() {
        assertThat(new AuditExportProperties(5_000).rowCap()).isEqualTo(5_000);
        assertThat(new AuditExportProperties(50_000).rowCap()).isEqualTo(50_000);
    }

    @Test
    void zeroIsCoercedToDefault() {
        assertThat(new AuditExportProperties(0).rowCap()).isEqualTo(10_000);
    }

    @Test
    void negativeIsCoercedToDefault() {
        assertThat(new AuditExportProperties(-1).rowCap()).isEqualTo(10_000);
        assertThat(new AuditExportProperties(-100).rowCap()).isEqualTo(10_000);
    }
}
