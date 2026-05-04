package com.ibizdrive.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ADR #19 — 비밀번호 정책 5규칙 일괄 검증 (docs/03 §2.7).
 *
 * <p>위반 시 {@link PasswordPolicyValidator}가 우선순위에 따라 단일 rule만 노출:
 * {@code whitespace -> max_length -> min_length -> missing_alpha -> missing_digit}.
 *
 * <p>{@code @NotBlank}/{@code @NotNull}과 함께 결합해 사용한다. {@code null}은 본 validator가 통과시키고
 * {@code @NotBlank}가 처리하도록 위임 (double-violation 회피).
 *
 * <p>위반 메시지는 rule code 자체 (예: {@code "min_length"}) — {@code AuthExceptionHandler}가
 * {@code fe.getDefaultMessage()}로 매핑해 {@code VALIDATION_ERROR} envelope의 {@code rule} 필드로 노출.
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Documented
public @interface ValidPassword {

    String message() default "invalid_password";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
