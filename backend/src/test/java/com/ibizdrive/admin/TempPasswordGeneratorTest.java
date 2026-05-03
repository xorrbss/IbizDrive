package com.ibizdrive.admin;

import com.ibizdrive.auth.validation.PasswordPolicyValidator;
import com.ibizdrive.auth.validation.ValidPassword;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TempPasswordGenerator}가 ADR #19 정책({@link PasswordPolicyValidator})을 항상 만족함을 보증한다.
 *
 * <p>회귀 가드 — alphabet 비율로 인해 확률적으로 영문/숫자 누락이 발생할 수 있으므로 generator 알고리즘이
 * 명시적으로 영문 ≥1 + 숫자 ≥1 + 길이 ≥12 + 공백 0을 보장해야 한다.
 *
 * <p>(auth-password-policy 트랙, 2026-05-04)
 */
class TempPasswordGeneratorTest {

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

    record Holder(@ValidPassword String password) {}

    private final TempPasswordGenerator generator = new TempPasswordGenerator();

    /**
     * 200회 sample — 모든 출력이 ADR #19 정책 통과해야 한다.
     * (현재 random alphabet 기반 generator는 통계적으로 missing_digit/missing_alpha를 생성할 수 있으므로
     * 알고리즘 강화 후 본 가드 통과.)
     */
    @Test
    void generate_200samples_allPassPolicy() {
        for (int i = 0; i < 200; i++) {
            String pw = generator.generate();
            Set<ConstraintViolation<Holder>> violations = validator.validate(new Holder(pw));
            assertThat(violations)
                .as("sample #%d generated %s — must satisfy policy", i, pw)
                .isEmpty();
        }
    }

    @Test
    void generate_alwaysHasAtLeastOneAlpha() {
        IntStream.range(0, 200).forEach(i -> {
            String pw = generator.generate();
            assertThat(pw.chars().anyMatch(Character::isLetter))
                .as("sample #%d generated %s — must contain alpha", i, pw)
                .isTrue();
        });
    }

    @Test
    void generate_alwaysHasAtLeastOneDigit() {
        IntStream.range(0, 200).forEach(i -> {
            String pw = generator.generate();
            assertThat(pw.chars().anyMatch(Character::isDigit))
                .as("sample #%d generated %s — must contain digit", i, pw)
                .isTrue();
        });
    }

    @Test
    void generate_neverContainsWhitespace() {
        IntStream.range(0, 200).forEach(i -> {
            String pw = generator.generate();
            assertThat(pw.chars().noneMatch(Character::isWhitespace))
                .as("sample #%d generated %s — must not contain whitespace", i, pw)
                .isTrue();
        });
    }

    @RepeatedTest(20)
    void generate_lengthBetween12And128() {
        String pw = generator.generate();
        assertThat(pw.length()).isBetween(12, 128);
    }
}
