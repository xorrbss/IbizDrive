package com.ibizdrive.common.normalize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Loads the shared corpus {@code docs/normalize-fixtures.json} and verifies that
 * each Java implementation matches the {@code expected} block. Frontend Vitest
 * test runs the same fixtures — drift between the two halves fails CI (ADR #16).
 */
class NormalizeUtilTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TestFactory
    List<DynamicTest> normalizeFixtures() throws IOException {
        Path fixturesPath = locateFixtures();
        JsonNode root = MAPPER.readTree(Files.readAllBytes(fixturesPath));
        JsonNode fixtures = root.get("fixtures");

        List<DynamicTest> tests = new ArrayList<>(fixtures.size() * 3);
        for (JsonNode fx : fixtures) {
            String id = fx.get("id").asText();
            String input = fx.get("input").asText();
            JsonNode expected = fx.get("expected");

            tests.add(dynamicTest(id + " :: normalizeFileName",
                    () -> assertFunction(input, expected.get("normalizeFileName"),
                            NormalizeUtil::normalizeFileName)));
            tests.add(dynamicTest(id + " :: normalizedNameForDedup",
                    () -> assertFunction(input, expected.get("normalizedNameForDedup"),
                            NormalizeUtil::normalizedNameForDedup)));
            tests.add(dynamicTest(id + " :: normalizeForSearch",
                    () -> assertFunction(input, expected.get("normalizeForSearch"),
                            NormalizeUtil::normalizeForSearch)));
        }
        return tests;
    }

    private static void assertFunction(String input, JsonNode expected, Function<String, String> fn) {
        boolean ok = expected.get("ok").asBoolean();
        if (ok) {
            String actual = fn.apply(input);
            assertEquals(expected.get("value").asText(), actual,
                    () -> "expected ok value mismatch for input length " + input.length());
        } else {
            String expectedCode = expected.get("error").asText();
            NormalizationException ex = assertThrows(NormalizationException.class,
                    () -> fn.apply(input));
            assertEquals(expectedCode, ex.getCode(),
                    () -> "expected error code mismatch");
        }
    }

    /**
     * Find the fixtures file from the repo root. Tests usually run with
     * {@code workingDir = backend/} (see build.gradle.kts), so the file is
     * one directory up. Fall back to the working dir for IDE runs.
     */
    private static Path locateFixtures() {
        Path[] candidates = {
                Paths.get("../docs/normalize-fixtures.json"),
                Paths.get("docs/normalize-fixtures.json"),
                Paths.get("../../docs/normalize-fixtures.json"),
        };
        for (Path p : candidates) {
            if (Files.exists(p)) return p.toAbsolutePath().normalize();
        }
        throw new IllegalStateException(
                "normalize-fixtures.json not found. Tried: " + java.util.Arrays.toString(candidates)
                        + " (cwd=" + Paths.get("").toAbsolutePath() + ")");
    }
}
