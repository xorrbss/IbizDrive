package com.ibizdrive.storage;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.stream.Stream;

/**
 * Object storage 추상화 — A15 (docs/02 §5, ADR #5).
 *
 * <p>객체 키 형식은 caller 책임 (도메인 = {@code {YYYY}/{MM}/{storage_key UUID}}, docs/02 §5.1).
 * 본 인터페이스는 단순 key→bytes 매핑 + CRUD 시그니처만 노출하며 키 구조에 관여하지 않는다.
 *
 * <p>구현체:
 * <ul>
 *   <li>{@link LocalFsStorageClient} — MVP 단일 인스턴스. {@code ibizdrive.storage.local.root} 하위에 파일 저장.</li>
 *   <li>{@code S3StorageClient} — v1.x. AWS SDK v2 + S3 multipart 위임. ADR #36(가칭) 별도 발효.</li>
 * </ul>
 *
 * <p><b>트랜잭션 invariant (CLAUDE.md §3 원칙 9 — 문제 은폐 금지)</b>: storage write는 DB 트랜잭션과
 * 분리된 외부 작용. write 후 DB INSERT 실패 시 객체가 orphan으로 잔존 — MVP는 알려진 한계, cleanup job은
 * 별도 트랙. caller는 storage write 호출 시점 = "DB INSERT 직전, 실패 시 trigger transaction rollback"으로
 * 사용해야 한다.
 *
 * <p><b>스레드 안정성</b>: 구현체는 동시 호출 안전해야 한다. {@link LocalFsStorageClient}는 atomic rename
 * (Files.move with REPLACE_EXISTING)으로 동일 key 동시 write race를 회피.
 */
public interface StorageClient {

    /**
     * 객체를 key 위치에 저장한다. 상위 디렉터리는 자동 생성. 동일 key 존재 시 overwrite.
     *
     * <p>caller는 key 충돌이 도메인 invariant(storage_key UUID)로 차단됨을 보장해야 한다 — overwrite는
     * 실수로 발생해서는 안 되며, 발생 시 도메인 버그 신호.
     *
     * @param key       객체 key (도메인 = {@code {YYYY}/{MM}/{UUID}})
     * @param src       바이트 stream. 호출자가 close 책임 (try-with-resources 권장).
     * @param sizeBytes 기대 총 바이트 수 (검증/메타데이터용). 실제 read 길이와 다르면 IOException 또는 truncation 가능.
     * @param contentType MIME (구현에 따라 메타데이터 저장; LocalFs는 무시).
     * @throws IOException I/O 실패
     */
    void write(String key, InputStream src, long sizeBytes, String contentType) throws IOException;

    /**
     * 객체를 읽는다. 호출자가 stream close 책임.
     *
     * @param key 객체 key
     * @return 객체 바이트 stream
     * @throws IOException 객체 부재 또는 I/O 실패
     */
    InputStream read(String key) throws IOException;

    /**
     * 객체를 삭제한다. 부재 시 no-op (idempotent).
     *
     * @param key 객체 key
     * @throws IOException I/O 실패 (부재는 예외 아님)
     */
    void delete(String key) throws IOException;

    /**
     * 객체 존재 여부.
     *
     * @param key 객체 key
     * @return 존재 시 true
     */
    boolean exists(String key);

    /**
     * mtime이 {@code now() - grace} 이전인 객체를 lazy stream으로 반환한다 — orphan cleanup용.
     *
     * <p><b>Stream 자원 관리</b>: 구현체는 file walk 등 외부 자원을 소비할 수 있다. caller는 반드시
     * try-with-resources로 close 해야 하며, 본 stream은 {@link Stream#close()} 호출 시 모든
     * 외부 자원을 해제한다.
     *
     * <p><b>객체 키 필터</b>: 도메인 키 형식({@code {YYYY}/{MM}/{UUID}})에 부합하는 항목만 반환한다.
     * 비 UUID 파일/임시 파일/숨김 파일은 skip + WARN 로그 (cleanup 대상 외).
     *
     * <p><b>grace boundary</b>: {@code lastModified <= now - grace} 객체만 yield. 경계 동등도 포함
     * (in-flight 업로드는 호출자 측 추가 보호 권장 — service 레벨 graceHours).
     *
     * @param grace 현재 시각 기준 보호 기간. 음수 또는 0이면 모든 객체.
     * @return lazy stream — 호출자 close 책임.
     * @throws IOException walk 시작 실패 (root 부재 등)
     */
    Stream<StorageObject> listOlderThan(Duration grace) throws IOException;
}
