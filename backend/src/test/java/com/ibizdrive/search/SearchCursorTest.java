package com.ibizdrive.search;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchCursorTest {

    @Test
    void encodeAndDecodeFileCursorRoundTrip() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String wire = SearchCursor.encode(1735689600000L, "file", id);
        SearchCursor decoded = SearchCursor.decode(wire);

        assertThat(decoded).isNotNull();
        assertThat(decoded.updatedAtEpochMs()).isEqualTo(1735689600000L);
        assertThat(decoded.type()).isEqualTo("file");
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    void encodeAndDecodeFolderCursorRoundTrip() {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        String wire = SearchCursor.encode(1700000000123L, "folder", id);
        SearchCursor decoded = SearchCursor.decode(wire);

        assertThat(decoded).isNotNull();
        assertThat(decoded.type()).isEqualTo("folder");
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    void encodeReturnsNullWhenIdIsNull() {
        assertThat(SearchCursor.encode(0L, "file", null)).isNull();
    }

    @Test
    void encodeReturnsNullWhenTypeIsNull() {
        assertThat(SearchCursor.encode(0L, null, UUID.randomUUID())).isNull();
    }

    @Test
    void decodeReturnsNullForNullOrBlankWire() {
        assertThat(SearchCursor.decode(null)).isNull();
        assertThat(SearchCursor.decode("")).isNull();
        assertThat(SearchCursor.decode("   ")).isNull();
    }

    @Test
    void decodeRejectsInvalidBase64() {
        assertThatThrownBy(() -> SearchCursor.decode("!!!not-base64!!!"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid cursor");
    }

    @Test
    void decodeRejectsWrongPartCount() {
        // base64("only-one-part")
        String wire = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("only-one-part".getBytes());
        assertThatThrownBy(() -> SearchCursor.decode(wire))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid cursor format");
    }

    @Test
    void decodeRejectsInvalidType() {
        String wire = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(("123|movie|" + UUID.randomUUID()).getBytes());
        assertThatThrownBy(() -> SearchCursor.decode(wire))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid cursor type");
    }

    @Test
    void decodeRejectsNonNumericTimestamp() {
        String wire = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(("abc|file|" + UUID.randomUUID()).getBytes());
        assertThatThrownBy(() -> SearchCursor.decode(wire))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeRejectsInvalidUuid() {
        String wire = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("123|file|not-a-uuid".getBytes());
        assertThatThrownBy(() -> SearchCursor.decode(wire))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodeHandlesEdgeTimestamps() {
        UUID id = UUID.randomUUID();
        SearchCursor min = SearchCursor.decode(SearchCursor.encode(Long.MIN_VALUE, "file", id));
        SearchCursor max = SearchCursor.decode(SearchCursor.encode(Long.MAX_VALUE, "folder", id));

        assertThat(min.updatedAtEpochMs()).isEqualTo(Long.MIN_VALUE);
        assertThat(max.updatedAtEpochMs()).isEqualTo(Long.MAX_VALUE);
    }
}
