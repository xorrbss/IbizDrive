package com.ibizdrive.share;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A10.4 — {@code GET /api/shares/by-me}, {@code GET /api/shares/with-me} (docs/02 §7.9, ADR #34).
 *
 * <p><b>책임</b>:
 * <ul>
 *   <li>cursor 파싱 ({@link ShareCursor#decode}) + limit clamp.</li>
 *   <li>{@link ShareRepository#findActiveBySharedBy} / {@link ShareRepository#findActiveWithMeBySubjectUser}
 *       호출 — limit + 1 over-fetch.</li>
 *   <li>hasMore 판정 + nextCursor 발급 ({@link ShareCursor#encode}).</li>
 * </ul>
 *
 * <p><b>by-me</b>: 본인이 만든 active share. {@code shared_by = actor} AND {@code revoked_at IS NULL}.
 *
 * <p><b>with-me</b>: 본인이 받은 active share. MVP — {@code subject_type='user'}만 매칭. department/role/everyone은
 * backlog ADR #34. permissions.expires_at 도과 row는 repository SQL에서 자동 제외 (ADR #34 결정 7).
 *
 * <p><b>READ 후처리 미적용</b>: by-me는 actor 자기 자산, with-me는 actor가 받은 share 자체가 자기 view.
 * 별도 권한 게이트 불필요 ({@link com.ibizdrive.trash.TrashQueryService}와 다른 결정).
 *
 * <p><b>limit 정책</b>: 휴지통과 일관 — default 50, max 100.
 */
@Service
public class ShareQueryService {

    /** docs/02 §7.9 묵시 약속 — by-me/with-me 응답이 비대해지는 것을 막는 hard cap (TrashQueryService 일관). */
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 50;

    private final ShareRepository shareRepository;

    public ShareQueryService(ShareRepository shareRepository) {
        this.shareRepository = shareRepository;
    }

    /**
     * GET /api/shares/by-me
     *
     * @param actorId    호출자 (shared_by 매칭).
     * @param cursorWire opaque base64 cursor — null/blank이면 첫 페이지.
     * @param limitOpt   null/0 이하 → {@link #DEFAULT_LIMIT}, {@link #MAX_LIMIT} 초과 → cap.
     */
    @Transactional(readOnly = true)
    public SharePage listByMe(UUID actorId, String cursorWire, Integer limitOpt) {
        if (actorId == null) throw new IllegalArgumentException("actorId must not be null");
        ShareCursor cursor = ShareCursor.decode(cursorWire);
        Instant cursorAt = cursor != null ? cursor.createdAt() : null;
        UUID cursorId = cursor != null ? cursor.id() : null;
        int limit = clampLimit(limitOpt);

        // limit + 1 over-fetch — TrashQueryService와 동형 hasMore 판정 패턴.
        List<Share> rows = shareRepository.findActiveBySharedBy(
            actorId, cursorAt, cursorId, limit + 1
        );
        return toPage(rows, limit);
    }

    /**
     * GET /api/shares/with-me — MVP는 subject_type='user' 한정.
     *
     * @param subjectUserId 호출자 (permissions.subject_id 매칭).
     * @param cursorWire    opaque base64 cursor — null/blank이면 첫 페이지.
     * @param limitOpt      null/0 이하 → {@link #DEFAULT_LIMIT}, {@link #MAX_LIMIT} 초과 → cap.
     */
    @Transactional(readOnly = true)
    public SharePage listWithMe(UUID subjectUserId, String cursorWire, Integer limitOpt) {
        if (subjectUserId == null) throw new IllegalArgumentException("subjectUserId must not be null");
        ShareCursor cursor = ShareCursor.decode(cursorWire);
        Instant cursorAt = cursor != null ? cursor.createdAt() : null;
        UUID cursorId = cursor != null ? cursor.id() : null;
        int limit = clampLimit(limitOpt);

        List<Share> rows = shareRepository.findActiveWithMeBySubjectUser(
            subjectUserId, cursorAt, cursorId, limit + 1
        );
        return toPage(rows, limit);
    }

    /**
     * limit + 1 over-fetch 결과를 SharePage로 정리.
     *
     * <p>hasMore이면 마지막 row를 잘라내고 그 위치에서 nextCursor를 발급한다 — 이렇게 해야 다음 페이지가
     * 정확히 잘려나간 row부터 시작한다 (cursor `<` 비교이므로).
     */
    private static SharePage toPage(List<Share> rows, int limit) {
        boolean hasMore = rows.size() > limit;
        List<Share> page = hasMore ? rows.subList(0, limit) : rows;
        List<ShareDto> dtos = new ArrayList<>(page.size());
        for (Share s : page) dtos.add(ShareDto.from(s));

        String nextCursor = null;
        if (hasMore) {
            Share last = page.get(page.size() - 1);
            nextCursor = ShareCursor.encode(last.getCreatedAt(), last.getId());
        }
        return new SharePage(List.copyOf(dtos), nextCursor);
    }

    private static int clampLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }
}
