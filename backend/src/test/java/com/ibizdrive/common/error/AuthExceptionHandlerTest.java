package com.ibizdrive.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.validation.FieldError;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuthExceptionHandler#ruleOf(FieldError)} 단위 검증.
 *
 * <p>{@code @ValidPassword}는 {@code buildConstraintViolationWithTemplate(rule)} 패턴으로
 * defaultMessage에 rule code를 넣는다. 다른 표준 어노테이션({@code @Size}, {@code @NotBlank} 등)은
 * defaultMessage가 사람이 읽는 문구이므로 {@code fe.getCode()}(어노테이션 simple name)을 rule로 노출한다.
 */
class AuthExceptionHandlerTest {

    @Test
    void ruleOf_validPasswordAnnotation_usesDefaultMessage() {
        FieldError fe = new FieldError(
            "signupRequest",
            "password",
            null,
            false,
            new String[]{"ValidPassword"},
            null,
            "min_length"
        );

        assertThat(AuthExceptionHandler.ruleOf(fe)).isEqualTo("min_length");
    }

    @Test
    void ruleOf_sizeAnnotation_usesCode() {
        FieldError fe = new FieldError(
            "signupRequest",
            "displayName",
            null,
            false,
            new String[]{"Size"},
            null,
            "size must be between 1 and 100"
        );

        assertThat(AuthExceptionHandler.ruleOf(fe)).isEqualTo("Size");
    }

    @Test
    void ruleOf_notBlankAnnotation_usesCode() {
        FieldError fe = new FieldError(
            "signupRequest",
            "displayName",
            null,
            false,
            new String[]{"NotBlank"},
            null,
            "must not be blank"
        );

        assertThat(AuthExceptionHandler.ruleOf(fe)).isEqualTo("NotBlank");
    }

    @Test
    void ruleOf_emailAnnotation_usesCode() {
        FieldError fe = new FieldError(
            "signupRequest",
            "email",
            null,
            false,
            new String[]{"Email"},
            null,
            "must be a well-formed email address"
        );

        assertThat(AuthExceptionHandler.ruleOf(fe)).isEqualTo("Email");
    }
}
