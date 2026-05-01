package com.ibizdrive.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link LocalFsStorageClient} — A15.1 (StorageClient + LocalFs).
 *
 * <p>Spring 의존성 없이 직접 인스턴스화 — properties는 {@code @TempDir} 기반 record로 주입.
 */
class LocalFsStorageClientTest {

    private static StorageProperties props(Path root) {
        return new StorageProperties("local", new StorageProperties.Local(root.toString()));
    }

    private static LocalFsStorageClient newClient(Path root) throws IOException {
        LocalFsStorageClient c = new LocalFsStorageClient(props(root));
        c.ensureRoot();
        return c;
    }

    private static String objectKey() {
        return "2026/05/" + UUID.randomUUID();
    }

    @Test
    @DisplayName("ensureRoot — root 디렉터리 부재 시 자동 생성")
    void ensureRoot_createsMissingDirectory(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("uploads");
        assertThat(root).doesNotExist();

        newClient(root);

        assertThat(root).isDirectory();
    }

    @Test
    @DisplayName("write — 임의 깊이 prefix 자동 생성 + 파일 저장")
    void write_persistsFileWithNestedPrefix(@TempDir Path tmp) throws IOException {
        LocalFsStorageClient c = newClient(tmp);
        String key = objectKey();
        byte[] content = "안녕 storage".getBytes(StandardCharsets.UTF_8);

        c.write(key, new ByteArrayInputStream(content), content.length, "text/plain");

        Path stored = tmp.resolve(key);
        assertThat(stored).isRegularFile();
        assertThat(Files.readAllBytes(stored)).isEqualTo(content);
    }

    @Test
    @DisplayName("read — write한 바이트와 동일")
    void read_returnsSameBytes(@TempDir Path tmp) throws IOException {
        LocalFsStorageClient c = newClient(tmp);
        String key = objectKey();
        byte[] content = new byte[]{1, 2, 3, 4, 5};
        c.write(key, new ByteArrayInputStream(content), content.length, "application/octet-stream");

        try (InputStream in = c.read(key)) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    @DisplayName("exists — write 후 true, delete 후 false")
    void exists_reflectsWriteAndDelete(@TempDir Path tmp) throws IOException {
        LocalFsStorageClient c = newClient(tmp);
        String key = objectKey();
        assertThat(c.exists(key)).isFalse();

        c.write(key, new ByteArrayInputStream(new byte[]{0}), 1, "text/plain");
        assertThat(c.exists(key)).isTrue();

        c.delete(key);
        assertThat(c.exists(key)).isFalse();
    }

    @Test
    @DisplayName("delete — 부재 key는 no-op (idempotent)")
    void delete_isIdempotent(@TempDir Path tmp) throws IOException {
        LocalFsStorageClient c = newClient(tmp);
        c.delete(objectKey()); // throws X
    }

    @Test
    @DisplayName("read — 부재 key는 IOException")
    void read_throwsOnMissingKey(@TempDir Path tmp) throws IOException {
        LocalFsStorageClient c = newClient(tmp);
        assertThatIOException().isThrownBy(() -> c.read(objectKey()));
    }

    @Test
    @DisplayName("write — sizeBytes 불일치 시 IOException + 객체 미생성")
    void write_rejectsSizeMismatch(@TempDir Path tmp) throws IOException {
        LocalFsStorageClient c = newClient(tmp);
        String key = objectKey();
        byte[] content = "abc".getBytes();

        assertThatIOException().isThrownBy(() ->
            c.write(key, new ByteArrayInputStream(content), 99 /* lie */, "text/plain"));

        assertThat(c.exists(key)).isFalse(); // tmp 정리 + target 미생성
    }

    @Test
    @DisplayName("write — 동일 key 재 write 시 overwrite (atomic)")
    void write_overwritesSameKey(@TempDir Path tmp) throws IOException {
        LocalFsStorageClient c = newClient(tmp);
        String key = objectKey();
        c.write(key, new ByteArrayInputStream("first".getBytes()), 5, "text/plain");
        c.write(key, new ByteArrayInputStream("second".getBytes()), 6, "text/plain");

        try (InputStream in = c.read(key)) {
            assertThat(new String(in.readAllBytes())).isEqualTo("second");
        }
    }

    @Test
    @DisplayName("write — 경로 traversal(`../`) key는 IOException")
    void write_rejectsPathTraversal(@TempDir Path tmp) throws IOException {
        LocalFsStorageClient c = newClient(tmp);
        assertThatIOException().isThrownBy(() ->
            c.write("../escape", new ByteArrayInputStream(new byte[]{0}), 1, "text/plain"));
    }

    // --- listOlderThan (OC.2) ---

    private static String dateKey(int yearOffset, String name) {
        // yearOffset 0 = 2026. {YYYY}/{MM}/{name} 형식.
        int year = 2026 + yearOffset;
        return String.format("%04d/%02d/%s", year, 1, name);
    }

    private static void writeAt(LocalFsStorageClient c, Path root, String key, Instant mtime) throws IOException {
        c.write(key, new ByteArrayInputStream(new byte[]{0x42}), 1, "application/octet-stream");
        Files.setLastModifiedTime(root.resolve(key), FileTime.from(mtime));
    }

    @Test
    @DisplayName("listOlderThan — root 미생성/empty 시 빈 stream")
    void listOlderThan_emptyWalk(@TempDir Path tmp) throws IOException {
        LocalFsStorageClient c = newClient(tmp);
        try (Stream<StorageObject> s = c.listOlderThan(Duration.ofHours(24))) {
            assertThat(s).isEmpty();
        }
    }

    @Test
    @DisplayName("listOlderThan — grace 보다 오래된 UUID 객체만 yield, 신규는 제외")
    void listOlderThan_filtersByGrace(@TempDir Path tmp) throws IOException {
        LocalFsStorageClient c = newClient(tmp);
        Instant now = Instant.now();
        String oldKey = dateKey(0, UUID.randomUUID().toString());
        String newKey = dateKey(0, UUID.randomUUID().toString());

        writeAt(c, tmp, oldKey, now.minus(Duration.ofHours(48)));
        writeAt(c, tmp, newKey, now.minus(Duration.ofMinutes(5)));

        try (Stream<StorageObject> s = c.listOlderThan(Duration.ofHours(24))) {
            List<String> keys = s.map(StorageObject::key).toList();
            assertThat(keys).containsExactly(oldKey);
        }
    }

    @Test
    @DisplayName("listOlderThan — UUID가 아닌 파일명은 skip (매니페스트/임시 파일 보호)")
    void listOlderThan_skipsNonUuidFiles(@TempDir Path tmp) throws IOException {
        LocalFsStorageClient c = newClient(tmp);
        Instant old = Instant.now().minus(Duration.ofHours(72));

        String uuidKey = dateKey(0, UUID.randomUUID().toString());
        String nonUuidKey = dateKey(0, "manifest.json"); // skip 대상

        writeAt(c, tmp, uuidKey, old);
        writeAt(c, tmp, nonUuidKey, old);

        try (Stream<StorageObject> s = c.listOlderThan(Duration.ofHours(24))) {
            List<String> keys = s.map(StorageObject::key).toList();
            assertThat(keys).containsExactly(uuidKey);
        }
    }

    @Test
    @DisplayName("listOlderThan — 경계 동등(mtime == now-grace)은 포함")
    void listOlderThan_boundaryInclusive(@TempDir Path tmp) throws IOException {
        LocalFsStorageClient c = newClient(tmp);
        Instant now = Instant.now();
        Duration grace = Duration.ofHours(24);
        // 정확히 경계: mtime = now - grace
        String key = dateKey(0, UUID.randomUUID().toString());
        writeAt(c, tmp, key, now.minus(grace).minusSeconds(1));

        try (Stream<StorageObject> s = c.listOlderThan(grace)) {
            assertThat(s.map(StorageObject::key).toList()).contains(key);
        }
    }

    @Test
    @DisplayName("listOlderThan — 임시 파일(.tmp.*) skip")
    void listOlderThan_skipsTempFiles(@TempDir Path tmp) throws IOException {
        LocalFsStorageClient c = newClient(tmp);
        Instant old = Instant.now().minus(Duration.ofHours(72));

        // 정상 UUID 객체 + 동일 디렉터리에 임시 파일 수동 배치
        String uuidKey = dateKey(0, UUID.randomUUID().toString());
        writeAt(c, tmp, uuidKey, old);

        Path tmpFile = tmp.resolve("2026/01/." + UUID.randomUUID() + ".tmp.123");
        Files.createDirectories(tmpFile.getParent());
        Files.writeString(tmpFile, "leftover");
        Files.setLastModifiedTime(tmpFile, FileTime.from(old));

        try (Stream<StorageObject> s = c.listOlderThan(Duration.ofHours(24))) {
            List<String> keys = s.map(StorageObject::key).toList();
            assertThat(keys).containsExactly(uuidKey);
        }
    }

    @Test
    @DisplayName("listOlderThan — StorageObject.lastModified는 파일 mtime과 일치")
    void listOlderThan_propagatesMtime(@TempDir Path tmp) throws IOException {
        LocalFsStorageClient c = newClient(tmp);
        Instant mtime = Instant.parse("2026-04-01T12:00:00Z");
        String key = dateKey(0, UUID.randomUUID().toString());
        writeAt(c, tmp, key, mtime);

        try (Stream<StorageObject> s = c.listOlderThan(Duration.ofHours(1))) {
            List<StorageObject> all = s.toList();
            assertThat(all).hasSize(1);
            // FileTime → Instant 라운딩(파일시스템마다 ms 또는 ns 정밀도) 흡수
            assertThat(all.get(0).lastModified()).isCloseTo(mtime, within(1, java.time.temporal.ChronoUnit.SECONDS));
        }
    }
}
