package com.ibizdrive.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.file.FileVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StorageOrphanCleanupService} unit tests — Mockito only, Docker 미요구.
 *
 * <p>liveSet/walk를 mock으로 주입해 service의 결정 로직(diff/cap/per-row 실패 isolation/audit
 * after_state JSON)만 검증. Spring/Postgres 통합은 OC.5 integration test에서 별도 검증.
 */
class StorageOrphanCleanupServiceTest {

    private FileVersionRepository fileVersionRepository;
    private StorageClient storageClient;
    private AuditService auditService;
    private ObjectMapper objectMapper;
    private StorageOrphanCleanupService service;

    @BeforeEach
    void setUp() {
        fileVersionRepository = mock(FileVersionRepository.class);
        storageClient = mock(StorageClient.class);
        auditService = mock(AuditService.class);
        objectMapper = new ObjectMapper();
        service = new StorageOrphanCleanupService(
            fileVersionRepository, storageClient, auditService, objectMapper);
    }

    private static String key(UUID storageKey) {
        return "2026/05/" + storageKey;
    }

    private static StorageObject obj(UUID storageKey, Instant mtime) {
        return new StorageObject(key(storageKey), mtime);
    }

    @Test
    @DisplayName("happy — orphan만 삭제, 카운터 정확, audit 1건")
    void happyPath_deletesOrphansAndEmitsAudit() throws IOException {
        UUID liveKey = UUID.randomUUID();
        UUID orphan1 = UUID.randomUUID();
        UUID orphan2 = UUID.randomUUID();

        when(fileVersionRepository.streamActiveStorageKeys()).thenReturn(Stream.of(liveKey));
        when(storageClient.listOlderThan(any())).thenReturn(Stream.of(
            obj(liveKey, Instant.now().minusSeconds(86400)),
            obj(orphan1, Instant.now().minusSeconds(86400)),
            obj(orphan2, Instant.now().minusSeconds(86400))
        ));

        StorageOrphanCleanupResult result = service.runDailyCleanup(100, 24);

        assertThat(result.scanned()).isEqualTo(3);
        assertThat(result.candidates()).isEqualTo(2);
        assertThat(result.deleted()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(0);
        assertThat(result.truncated()).isFalse();

        verify(storageClient).delete(key(orphan1));
        verify(storageClient).delete(key(orphan2));
        verify(storageClient, never()).delete(key(liveKey));
        verify(auditService, times(1)).record(any(AuditEvent.class));
    }

    @Test
    @DisplayName("empty — liveSet 빔 + walk 빔 → no-op + audit 1건 (scanned=0)")
    void emptyResult_emitsAuditWithZeros() throws IOException {
        when(fileVersionRepository.streamActiveStorageKeys()).thenReturn(Stream.empty());
        when(storageClient.listOlderThan(any())).thenReturn(Stream.empty());

        StorageOrphanCleanupResult result = service.runDailyCleanup(100, 24);

        assertThat(result.scanned()).isZero();
        assertThat(result.candidates()).isZero();
        assertThat(result.deleted()).isZero();
        verify(storageClient, never()).delete(any());
        verify(auditService).record(any(AuditEvent.class));
    }

    @Test
    @DisplayName("cap — maxPerRun 도달 시 truncated=true + 이후 walk 중단")
    void cap_marksTruncated() throws IOException {
        UUID o1 = UUID.randomUUID();
        UUID o2 = UUID.randomUUID();
        UUID o3 = UUID.randomUUID();

        when(fileVersionRepository.streamActiveStorageKeys()).thenReturn(Stream.empty());
        when(storageClient.listOlderThan(any())).thenReturn(Stream.of(
            obj(o1, Instant.now()), obj(o2, Instant.now()), obj(o3, Instant.now())
        ));

        StorageOrphanCleanupResult result = service.runDailyCleanup(2, 24);

        assertThat(result.deleted()).isEqualTo(2);
        assertThat(result.truncated()).isTrue();
        verify(storageClient, times(2)).delete(any());
    }

    @Test
    @DisplayName("per-row 실패 isolation — IOException 1개 발생해도 나머지 진행")
    void perRowFailure_isolated() throws IOException {
        UUID o1 = UUID.randomUUID();
        UUID o2 = UUID.randomUUID();
        UUID o3 = UUID.randomUUID();

        when(fileVersionRepository.streamActiveStorageKeys()).thenReturn(Stream.empty());
        when(storageClient.listOlderThan(any())).thenReturn(Stream.of(
            obj(o1, Instant.now()), obj(o2, Instant.now()), obj(o3, Instant.now())
        ));

        // o2 삭제만 실패
        org.mockito.Mockito.doNothing().when(storageClient).delete(key(o1));
        org.mockito.Mockito.doThrow(new IOException("disk error")).when(storageClient).delete(key(o2));
        org.mockito.Mockito.doNothing().when(storageClient).delete(key(o3));

        StorageOrphanCleanupResult result = service.runDailyCleanup(100, 24);

        assertThat(result.deleted()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.candidates()).isEqualTo(3);
        verify(storageClient, times(3)).delete(any());
    }

    @Test
    @DisplayName("non-UUID key는 skip — defense-in-depth (listOlderThan에서 이미 필터)")
    void nonUuidKey_skippedDefense() throws IOException {
        when(fileVersionRepository.streamActiveStorageKeys()).thenReturn(Stream.empty());
        when(storageClient.listOlderThan(any())).thenReturn(Stream.of(
            new StorageObject("2026/05/not-a-uuid", Instant.now()),
            obj(UUID.randomUUID(), Instant.now())
        ));

        StorageOrphanCleanupResult result = service.runDailyCleanup(100, 24);

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.candidates()).isEqualTo(1); // non-uuid는 candidate 미카운트
        assertThat(result.deleted()).isEqualTo(1);
    }

    @Test
    @DisplayName("audit after_state JSON — 7개 필드 정확 직렬화")
    void audit_afterStateContainsAllFields() throws IOException {
        when(fileVersionRepository.streamActiveStorageKeys()).thenReturn(Stream.empty());
        when(storageClient.listOlderThan(any())).thenReturn(Stream.of(
            obj(UUID.randomUUID(), Instant.now())
        ));

        service.runDailyCleanup(100, 24);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent emitted = captor.getValue();

        assertThat(emitted.eventType()).isEqualTo(AuditEventType.STORAGE_ORPHAN_CLEANED);
        assertThat(emitted.targetType()).isEqualTo(AuditTargetType.SYSTEM);
        assertThat(emitted.targetId()).isNull();
        assertThat(emitted.actorId()).isNull();

        JsonNode afterState = objectMapper.readTree(emitted.afterState());
        assertThat(afterState.fieldNames()).toIterable().containsExactly(
            "runId", "scanned", "candidates", "deleted", "failed", "truncated", "durationMs");
        assertThat(afterState.get("scanned").asInt()).isEqualTo(1);
        assertThat(afterState.get("candidates").asInt()).isEqualTo(1);
        assertThat(afterState.get("deleted").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("invalid args — maxPerRun <= 0, graceHours <= 0 거부")
    void invalidArgs_rejected() {
        assertThatThrownBy(() -> service.runDailyCleanup(0, 24))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.runDailyCleanup(100, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("walk IOException — log + audit emit (kill switch 차단 없음)")
    void walkIoException_doesNotBlockAudit() throws IOException {
        when(fileVersionRepository.streamActiveStorageKeys()).thenReturn(Stream.empty());
        when(storageClient.listOlderThan(any())).thenThrow(new IOException("root missing"));

        StorageOrphanCleanupResult result = service.runDailyCleanup(100, 24);

        assertThat(result.scanned()).isZero();
        assertThat(result.deleted()).isZero();
        verify(auditService).record(any(AuditEvent.class));
    }
}
