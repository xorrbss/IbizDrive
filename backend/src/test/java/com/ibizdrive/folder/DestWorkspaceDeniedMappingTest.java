package com.ibizdrive.folder;

import com.ibizdrive.common.error.ApiError;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class DestWorkspaceDeniedMappingTest {

    @Test
    void destWorkspaceDeniedMapsTo403() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<ApiError> resp = handler.handleDestWorkspaceDenied(
            new DestWorkspaceDeniedException("destination requires UPLOAD"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().error().code()).isEqualTo("ERR_DEST_WORKSPACE_DENIED");
    }
}
