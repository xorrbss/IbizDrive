package com.ibizdrive.me;

import com.ibizdrive.me.dto.MySharedWithMeListResponse;
import com.ibizdrive.user.IbizDriveUserDetails;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User Home Dashboard 와 personal 영역을 위한 me-domain controller.
 *
 * <p>v1.x 진입점:
 * <ul>
 *   <li>{@code GET /api/me/shared-with-me} — SharedWithMeCard.</li>
 * </ul>
 *
 * <p>{@code GET /api/me/favorites} 는 별도 {@code FavoriteController} 가 담당 (favorites-list 트랙 PR
 * #243). 동일 prefix 이지만 도메인 응집성을 위해 분리 유지.
 */
@RestController
@RequestMapping("/api/me")
@PreAuthorize("isAuthenticated()")
public class MeController {

    private final MeSharedQueryService meSharedQueryService;

    public MeController(MeSharedQueryService meSharedQueryService) {
        this.meSharedQueryService = meSharedQueryService;
    }

    @GetMapping("/shared-with-me")
    public MySharedWithMeListResponse sharedWithMe(
        @AuthenticationPrincipal IbizDriveUserDetails principal,
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit
    ) {
        return MySharedWithMeListResponse.of(
            meSharedQueryService.list(principal.getUser().getId(), limit)
        );
    }
}
