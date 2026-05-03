package com.ibizdrive.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * ADR #19 — 비밀번호 정책 5규칙 검증 (docs/03 §2.7).
 *
 * <p>규칙 (위반 우선순위 순):
 * <ol>
 *   <li>{@code whitespace} — 공백 문자(스페이스/탭/CR/LF) 금지</li>
 *   <li>{@code max_length} — 최대 128자</li>
 *   <li>{@code min_length} — 최소 12자</li>
 *   <li>{@code missing_alpha} — 영문자 1자 이상</li>
 *   <li>{@code missing_digit} — 숫자 1자 이상</li>
 * </ol>
 *
 * <p>{@code null}은 통과시켜 {@link jakarta.validation.constraints.NotBlank}/{@link jakarta.validation.constraints.NotNull}에 위임 — DTO에서 결합 사용.
 */
public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

    static final int MIN_LENGTH = 12;
    static final int MAX_LENGTH = 128;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) {
            return true;
        }

        String rule = checkRule(value);
        if (rule == null) {
            return true;
        }

        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(rule).addConstraintViolation();
        return false;
    }

    private String checkRule(String value) {
        if (containsWhitespace(value)) {
            return "whitespace";
        }
        if (value.length() > MAX_LENGTH) {
            return "max_length";
        }
        if (value.length() < MIN_LENGTH) {
            return "min_length";
        }
        if (!hasAlpha(value)) {
            return "missing_alpha";
        }
        if (!hasDigit(value)) {
            return "missing_digit";
        }
        return null;
    }

    /**
     * ADR #19 "공백 문자 금지" — 모든 Unicode 공백 류 거부.
     * {@link Character#isWhitespace}은 ASCII tab/LF/CR/space/FF/VT + line/paragraph separator를 포함하지만
     * NBSP(U+00A0)는 포함하지 않으므로 {@link Character#isSpaceChar}(Unicode SPACE_SEPARATOR 카테고리)도 함께 사용.
     * frontend `password.ts`의 {@code /\s/} 매칭과 의미적으로 정렬된다 (principle 11).
     */
    private boolean containsWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ADR #19 "영문자 1자 이상" — ASCII A-Z/a-z (frontend {@code /[A-Za-z]/}와 정렬, principle 11).
     * Unicode letter ({@link Character#isLetter} 한글/한자 등)은 본 정책에서 영문자가 아니다.
     */
    private boolean hasAlpha(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                return true;
            }
        }
        return false;
    }

    /** ADR #19 "숫자 1자 이상" — ASCII 0-9 (frontend {@code /[0-9]/}와 정렬). */
    private boolean hasDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                return true;
            }
        }
        return false;
    }
}
