package com.ibizdrive.auth.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR #19 — 비밀번호 정책 5규칙 검증.
 *
 * <p>규칙 (docs/03 §2.7):
 * <ul>
 *   <li>{@code min_length}: 최소 12자</li>
 *   <li>{@code max_length}: 최대 128자</li>
 *   <li>{@code missing_alpha}: 영문자 1자 이상</li>
 *   <li>{@code missing_digit}: 숫자 1자 이상</li>
 *   <li>{@code whitespace}: 공백 문자(스페이스/탭/개행) 금지</li>
 * </ul>
 *
 * <p>위반 우선순위 (사용자 한 번에 하나씩 고치는 UX):
 * {@code whitespace -> max_length -> min_length -> missing_alpha -> missing_digit}.
 *
 * <p>{@code null}은 {@link jakarta.validation.constraints.NotBlank}/{@link jakarta.validation.constraints.NotNull}이 처리하므로
 * 본 validator는 {@code null}/blank-only를 통과시킨다(double-violation 회피). DTO에서는 {@code @NotBlank @ValidPassword} 결합.
 */
class PasswordPolicyValidatorTest {

    static ValidatorFactory factory;
    static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    /** Validator를 격리해 검증할 holder. DTO 의존 없이 어노테이션만 적용. */
    record Holder(@ValidPassword String password) {}

    private Set<ConstraintViolation<Holder>> violate(String password) {
        return validator.validate(new Holder(password));
    }

    private String firstRule(Set<ConstraintViolation<Holder>> violations) {
        assertThat(violations).hasSize(1);
        return violations.iterator().next().getMessage();
    }

    // === Pass 케이스 ===========================================================

    @Test
    @DisplayName("12자 (경계, 영+숫) 통과")
    void boundary_12_alpha_digit_passes() {
        assertThat(violate("abcdefghijk1")).isEmpty();
    }

    @Test
    @DisplayName("128자 (경계, 영+숫) 통과")
    void boundary_128_alpha_digit_passes() {
        // 64 alpha + 64 digit = 128
        String p = "a".repeat(64) + "1".repeat(64);
        assertThat(p.length()).isEqualTo(128);
        assertThat(violate(p)).isEmpty();
    }

    @Test
    @DisplayName("영+숫+기호 혼합 통과 (특수문자 비강제)")
    void mixed_with_symbols_passes() {
        assertThat(violate("Password1!@#")).isEmpty();
    }

    @Test
    @DisplayName("null은 @NotBlank/@NotNull에 위임 — ValidPassword 단독은 통과")
    void null_passes_to_defer_to_notblank() {
        assertThat(violate(null)).isEmpty();
    }

    // === min_length ==========================================================

    @Test
    @DisplayName("11자 (경계 미만, 영+숫) → min_length")
    void boundary_11_alpha_digit_violates_min_length() {
        assertThat(firstRule(violate("abcdefghij1"))).isEqualTo("min_length");
    }

    @Test
    @DisplayName("빈 문자열 → min_length (validator 단독 적용 시)")
    void empty_string_violates_min_length() {
        assertThat(firstRule(violate(""))).isEqualTo("min_length");
    }

    // === max_length ==========================================================

    @Test
    @DisplayName("129자 (경계 초과, 영+숫) → max_length")
    void boundary_129_alpha_digit_violates_max_length() {
        String p = "a".repeat(64) + "1".repeat(65);
        assertThat(p.length()).isEqualTo(129);
        assertThat(firstRule(violate(p))).isEqualTo("max_length");
    }

    // === missing_alpha =======================================================

    @Test
    @DisplayName("숫자만 12자 → missing_alpha")
    void digits_only_violates_missing_alpha() {
        assertThat(firstRule(violate("123456789012"))).isEqualTo("missing_alpha");
    }

    @Test
    @DisplayName("숫자 + 기호만 (영문자 0) → missing_alpha")
    void digits_and_symbols_violates_missing_alpha() {
        assertThat(firstRule(violate("123456!@#$%^"))).isEqualTo("missing_alpha");
    }

    // === missing_digit =======================================================

    @Test
    @DisplayName("영문자만 12자 → missing_digit")
    void alpha_only_violates_missing_digit() {
        assertThat(firstRule(violate("abcdefghijkl"))).isEqualTo("missing_digit");
    }

    @Test
    @DisplayName("영문자 + 기호만 (숫자 0) → missing_digit")
    void alpha_and_symbols_violates_missing_digit() {
        assertThat(firstRule(violate("abcdef!@#$%^"))).isEqualTo("missing_digit");
    }

    // === whitespace ==========================================================

    @Test
    @DisplayName("스페이스 포함 → whitespace")
    void contains_space_violates_whitespace() {
        assertThat(firstRule(violate("abcdef 12345"))).isEqualTo("whitespace");
    }

    @Test
    @DisplayName("탭 포함 → whitespace")
    void contains_tab_violates_whitespace() {
        assertThat(firstRule(violate("abcdef\t12345"))).isEqualTo("whitespace");
    }

    @Test
    @DisplayName("개행 포함 → whitespace")
    void contains_newline_violates_whitespace() {
        assertThat(firstRule(violate("abcdef\n12345"))).isEqualTo("whitespace");
    }

    @Test
    @DisplayName("CR(\\r) 포함 → whitespace")
    void contains_cr_violates_whitespace() {
        assertThat(firstRule(violate("abcdef\r12345"))).isEqualTo("whitespace");
    }

    // === 우선순위 ============================================================

    @Test
    @DisplayName("우선순위: 공백 + 짧음 → whitespace 먼저")
    void priority_whitespace_over_min_length() {
        // 길이 6, 공백 포함 → whitespace 먼저
        assertThat(firstRule(violate("ab 12"))).isEqualTo("whitespace");
    }

    @Test
    @DisplayName("우선순위: 공백 + 길이 초과 → whitespace 먼저")
    void priority_whitespace_over_max_length() {
        String p = "a".repeat(64) + " " + "1".repeat(64); // 129자, 공백 포함
        assertThat(firstRule(violate(p))).isEqualTo("whitespace");
    }

    @Test
    @DisplayName("우선순위: 길이 초과 + 영문 누락 → max_length 먼저")
    void priority_max_length_over_missing_alpha() {
        String p = "1".repeat(129); // 129자, 숫자만
        assertThat(firstRule(violate(p))).isEqualTo("max_length");
    }

    @Test
    @DisplayName("우선순위: 길이 부족 + 영문 누락 → min_length 먼저")
    void priority_min_length_over_missing_alpha() {
        // 11자, 숫자만 → min_length 먼저 (missing_alpha보다 우선)
        assertThat(firstRule(violate("12345678901"))).isEqualTo("min_length");
    }

    @Test
    @DisplayName("우선순위: 영문 누락 + 숫자 누락 (기호만 12자) → missing_alpha 먼저")
    void priority_missing_alpha_over_missing_digit() {
        assertThat(firstRule(violate("!@#$%^&*()_+"))).isEqualTo("missing_alpha");
    }
}
