package com.ibizdrive.file;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * RFC 5987 {@code Content-Disposition} 헤더 빌더 — A15.5 / M-RP.2.1 공용.
 *
 * <p>{@link FileDownloadController}와 {@link FileVersionController}의 다운로드 endpoint가 동일한 헤더
 * 형식을 요구한다 (current 다운로드 vs version-pin 다운로드). 두 controller에 같은 코드가 복제되는 것을
 * 막기 위해 package-private 헬퍼로 분리.
 *
 * <p>형식: {@code attachment; filename="<ascii-fallback>"; filename*=UTF-8''<percent-encoded>}.
 * legacy UA는 ASCII fallback, 모던 UA는 {@code filename*}을 우선시.
 */
final class ContentDispositionHeaders {

    private ContentDispositionHeaders() {}

    /**
     * RFC 5987 {@code Content-Disposition: attachment; filename="..."; filename*=UTF-8''...}.
     *
     * <p>{@link URLEncoder#encode}는 form encoding(공백 → {@code +})이므로 {@code +}를 RFC 3986
     * 표현({@code %20})으로 후처리 — RFC 5987은 {@code *} parameter 값에 form encoding을 허용하지 않는다.
     */
    static String build(String displayName) {
        return build(displayName, "attachment");
    }

    /**
     * {@code inline} disposition 변형 — 미리보기(ADR #51). 호출 전 MIME 화이트리스트 검증은
     * caller({@link FileDownloadController}) 책임 — 본 헬퍼는 헤더 형식만 담당.
     */
    static String buildInline(String displayName) {
        return build(displayName, "inline");
    }

    private static String build(String displayName, String dispositionType) {
        String safeAscii = sanitizeAscii(displayName);
        String utf8Pct = URLEncoder.encode(displayName, StandardCharsets.UTF_8)
            .replace("+", "%20");
        return dispositionType + "; filename=\"" + safeAscii + "\"; filename*=UTF-8''" + utf8Pct;
    }

    /**
     * 비ASCII / 제어문자 / RFC 6266 token 깨짐 유발 문자({@code "}, 백슬래시)를 {@code _}로 치환.
     * legacy UA용 fallback이며 모던 UA는 filename* 사용.
     */
    private static String sanitizeAscii(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c >= 0x7F || c == '"' || c == '\\') {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
