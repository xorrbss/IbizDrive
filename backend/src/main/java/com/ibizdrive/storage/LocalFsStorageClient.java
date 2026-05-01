package com.ibizdrive.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Local FS 백엔드 {@link StorageClient} 구현 — A15 MVP (docs/02 §5).
 *
 * <p>MVP single-instance 가정. {@code ibizdrive.storage.type} 부재 또는 {@code "local"}일 때 활성
 * ({@code matchIfMissing=true}).
 *
 * <p><b>Atomic write 정책</b>: write는 임시 파일(`.{key}.tmp.{nanoTime}`)에 stream 후 원자적 rename
 * ({@link Files#move}, {@link StandardCopyOption#REPLACE_EXISTING}). 동일 key 동시 write race를 차단하고
 * 부분 쓰기 상태 노출 회피.
 *
 * <p><b>경로 traversal 방어</b>: key가 root 외부로 escape하면 IOException. caller는 UUID 기반 key를
 * 사용하므로 정상 경로에서 traversal 발생 불가 — defense-in-depth.
 *
 * <p><b>S3 호환성</b>: 본 구현은 S3 객체 모델(flat key namespace + 디렉터리 자동 처리)을 시뮬레이션 —
 * v1.x S3 도입 시 caller 측 변경 0 (key 형식 동일).
 */
@Component
@ConditionalOnProperty(prefix = "ibizdrive.storage", name = "type",
                       havingValue = "local", matchIfMissing = true)
public class LocalFsStorageClient implements StorageClient {

    private static final Logger log = LoggerFactory.getLogger(LocalFsStorageClient.class);

    private final Path root;

    public LocalFsStorageClient(StorageProperties props) {
        this.root = Paths.get(props.local().root()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void ensureRoot() throws IOException {
        Files.createDirectories(root);
        log.info("LocalFsStorageClient root = {}", root);
    }

    private Path resolve(String key) throws IOException {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("storage key escapes root: " + key);
        }
        return target;
    }

    @Override
    public void write(String key, InputStream src, long sizeBytes, String contentType) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling("." + target.getFileName() + ".tmp." + System.nanoTime());
        try {
            long copied = Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING);
            if (sizeBytes >= 0 && copied != sizeBytes) {
                Files.deleteIfExists(tmp);
                throw new IOException("size mismatch: expected=" + sizeBytes + ", actual=" + copied);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    @Override
    public InputStream read(String key) throws IOException {
        Path target = resolve(key);
        return Files.newInputStream(target);
    }

    @Override
    public void delete(String key) throws IOException {
        Path target = resolve(key);
        Files.deleteIfExists(target);
    }

    @Override
    public boolean exists(String key) {
        try {
            return Files.exists(resolve(key));
        } catch (IOException e) {
            return false;
        }
    }
}
