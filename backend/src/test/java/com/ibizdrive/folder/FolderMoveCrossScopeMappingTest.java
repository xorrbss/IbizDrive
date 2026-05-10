package com.ibizdrive.folder;

import com.ibizdrive.common.error.ApiError;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class FolderMoveCrossScopeMappingTest {

    @Test
    void crossScopeMoveExceptionMapsTo409() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<ApiError> resp = handler.handleCrossScopeMove(new CrossScopeMoveException());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().error().code()).isEqualTo("ERR_CROSS_SCOPE_MOVE");
    }
}
