package com.ibizdrive.common.error;

import com.ibizdrive.team.LastOwnerRequiredException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GlobalExceptionHandler} 단위 검증 — Spring 컨텍스트 없이 핸들러 메서드를 직접 호출.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleLastOwnerRequired_returns400WithEnvelope() {
        LastOwnerRequiredException ex = new LastOwnerRequiredException(UUID.randomUUID());

        ResponseEntity<ApiError> response = handler.handleLastOwnerRequired(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiError.Body body = response.getBody().error();
        assertThat(body.code()).isEqualTo("TEAM_OWNER_REQUIRED");
        assertThat(body.message()).isNotBlank();
        assertThat(body.details()).isNull();
    }
}
