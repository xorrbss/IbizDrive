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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

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
}
