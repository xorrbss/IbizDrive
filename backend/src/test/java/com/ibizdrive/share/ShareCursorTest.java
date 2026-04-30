package com.ibizdrive.share;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A10.4 — {@link ShareCursor} encode/decode 라운드트립 + invalid wire 거절.
 *
 * <p>패턴: {@link com.ibizdrive.search.SearchCursorTest} / {@code TrashCursor} (단일 단위 테스트).
 */
class ShareCursorTest {

    @Test
    void roundTripPreservesValues() {
        Instant createdAt = Instant.parse("2026-04-30T12:34:56.789Z");
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");

        String wire = ShareCursor.encode(createdAt, id);
        ShareCursor decoded = ShareCursor.decode(wire);

        assertThat(decoded).isNotNull();
        assertThat(decoded.createdAt()).isEqualTo(createdAt);
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    void encodeReturnsNullWhenAnyArgIsNull() {
        assertThat(ShareCursor.encode(null, UUID.randomUUID())).isNull();
        assertThat(ShareCursor.encode(Instant.now(), null)).isNull();
    }

    @Test
    void decodeReturnsNullForNullOrBlankWire() {
        assertThat(ShareCursor.decode(null)).isNull();
        assertThat(ShareCursor.decode("")).isNull();
        assertThat(ShareCursor.decode("   ")).isNull();
    }

    @Test
    void decodeRejectsInvalidBase64() {
        assertThatThrownBy(() -> ShareCursor.decode("!!!not-base64!!!"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid cursor");
    }

    @Test
    void decodeRejectsMissingPipe() {
        String wire = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("only-one-part".getBytes());
        assertThatThrownBy(() -> ShareCursor.decode(wire))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid cursor format");
    }

    @Test
    void decodeRejectsInvalidInstant() {
        String wire = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(("not-an-instant|" + UUID.randomUUID()).getBytes());
        assertThatThrownBy(() -> ShareCursor.decode(wire))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeRejectsInvalidUuid() {
        String wire = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("2026-04-30T00:00:00Z|not-a-uuid".getBytes());
        assertThatThrownBy(() -> ShareCursor.decode(wire))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
