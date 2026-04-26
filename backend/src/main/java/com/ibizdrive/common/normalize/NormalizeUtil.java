package com.ibizdrive.common.normalize;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Implements the 7-step normalization pipeline defined in
 * {@code docs/02-backend-data-model.md §3}. Mirror of frontend
 * {@code src/lib/normalize.ts}; both implementations are validated against
 * the shared corpus {@code docs/normalize-fixtures.json} (ADR #16).
 *
 * <p>Three public functions — see the spec for which step set each applies.
 */
public final class NormalizeUtil {

    public static final String ERR_NUL_CHAR = "INVALID_NAME_NUL_CHAR";
    public static final String ERR_EMPTY = "INVALID_NAME_EMPTY";
    public static final String ERR_TOO_LONG = "INVALID_NAME_TOO_LONG";
    public static final String ERR_FORBIDDEN_CHAR = "INVALID_NAME_FORBIDDEN_CHAR";
    public static final String ERR_RESERVED = "INVALID_NAME_RESERVED";
    public static final String ERR_TRAILING_DOT = "INVALID_NAME_TRAILING_DOT";

    private static final int MAX_LENGTH = 255;

    private static final Set<String> RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private NormalizeUtil() {
        // utility class
    }

    /** Steps 1-2-3-6-7: NFC, control strip, whitespace unify, trim, validate. */
    public static String normalizeFileName(String input) {
        String s = nfc(input);
        s = stripControlChars(s);
        s = unifyWhitespace(s);
        s = s.trim();
        validateForFilename(s);
        return s;
    }

    /** Steps 1-2-3-5-6-7: + lowercase (Locale.ROOT). */
    public static String normalizedNameForDedup(String input) {
        String s = nfc(input);
        s = stripControlChars(s);
        s = unifyWhitespace(s);
        s = s.toLowerCase(Locale.ROOT);
        s = s.trim();
        validateForFilename(s);
        return s;
    }

    /** Steps 1-2-3-5-4-6: + collapse, no length/forbidden/reserved/trailing-dot validation. */
    public static String normalizeForSearch(String input) {
        String s = nfc(input);
        s = stripControlChars(s);
        s = unifyWhitespace(s);
        s = s.toLowerCase(Locale.ROOT);
        s = collapseWhitespace(s);
        s = s.trim();
        return s;
    }

    // ---- pipeline steps ----

    private static String nfc(String input) {
        if (input == null) {
            throw new NormalizationException(ERR_EMPTY);
        }
        return Normalizer.normalize(input, Normalizer.Form.NFC);
    }

    /**
     * Step 2: NUL is rejected; C0 (excluding NUL) becomes a space; DEL/C1/ZWSP/BIDI/WJ/BOM are dropped.
     */
    private static String stripControlChars(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        int len = input.length();
        for (int i = 0; i < len; ) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);

            if (cp == 0x0000) {
                throw new NormalizationException(ERR_NUL_CHAR);
            }
            if (cp >= 0x0001 && cp <= 0x001F) {
                sb.append(' ');
                continue;
            }
            if (cp == 0x007F) continue;
            if (cp >= 0x0080 && cp <= 0x009F) continue;
            if (cp >= 0x200B && cp <= 0x200F) continue;
            if (cp >= 0x202A && cp <= 0x202E) continue;
            if (cp == 0x2060) continue;
            if (cp == 0xFEFF) continue;

            sb.appendCodePoint(cp);
        }
        return sb.toString();
    }

    /** Step 3: NBSP, fullwidth space, etc → ASCII space. */
    private static String unifyWhitespace(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        int len = input.length();
        for (int i = 0; i < len; ) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);

            if (cp == 0x00A0
                    || (cp >= 0x2000 && cp <= 0x200A)
                    || cp == 0x202F
                    || cp == 0x205F
                    || cp == 0x3000) {
                sb.append(' ');
            } else {
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    /** Step 4 (search only): collapse runs of ASCII spaces. */
    private static String collapseWhitespace(String input) {
        return input.replaceAll(" +", " ");
    }

    /** Step 7: shared validation for filename / dedup. */
    private static void validateForFilename(String s) {
        if (s.isEmpty()) {
            throw new NormalizationException(ERR_EMPTY);
        }
        if (s.length() > MAX_LENGTH) {
            throw new NormalizationException(ERR_TOO_LONG);
        }
        if (s.indexOf('/') >= 0 || s.indexOf('\\') >= 0) {
            throw new NormalizationException(ERR_FORBIDDEN_CHAR);
        }
        if (s.endsWith(".")) {
            throw new NormalizationException(ERR_TRAILING_DOT);
        }
        // Reserved name check: base name (everything before the first dot), case-insensitive.
        int dot = s.indexOf('.');
        String base = (dot >= 0) ? s.substring(0, dot) : s;
        if (RESERVED_NAMES.contains(base.toUpperCase(Locale.ROOT))) {
            throw new NormalizationException(ERR_RESERVED);
        }
    }
}
