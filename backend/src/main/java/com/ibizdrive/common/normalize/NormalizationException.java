package com.ibizdrive.common.normalize;

/**
 * Thrown when a name fails normalization-time validation.
 * The {@link #getCode()} value matches the {@code errorCodes} enum in
 * {@code docs/normalize-fixtures.json} and is part of the API contract.
 */
public class NormalizationException extends RuntimeException {
    private final String code;

    public NormalizationException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
