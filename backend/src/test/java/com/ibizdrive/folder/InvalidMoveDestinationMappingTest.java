package com.ibizdrive.folder;

import com.ibizdrive.common.error.ApiError;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class InvalidMoveDestinationMappingTest {

    @Test
    void invalidDestinationMapsTo400() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<ApiError> resp = handler.handleInvalidMoveDestination(
            new InvalidMoveDestinationException("destinationFolderId required"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().error().code()).isEqualTo("ERR_INVALID_DESTINATION");
    }
}
