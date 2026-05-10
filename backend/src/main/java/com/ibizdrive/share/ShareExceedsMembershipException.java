package com.ibizdrive.share;

import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.Preset;

import java.util.Set;

/**
 * Plan C — §4.2 share grant cap 위반 시 발생.
 *
 * <p>sharer의 workspace 멤버십 default permission 집합을 넘어선 preset을 grant하려 할 때.
 * 예: sharer가 MEMBER ({READ, UPLOAD, EDIT})인데 ADMIN preset으로 share 시도.
 *
 * <p>{@link com.ibizdrive.common.error.GlobalExceptionHandler}가 403 +
 * {@code ERR_SHARE_EXCEEDS_MEMBER}로 매핑.
 */
public class ShareExceedsMembershipException extends RuntimeException {

    private final Preset requestedPreset;
    private final Set<Permission> sharerMembershipPerms;

    public ShareExceedsMembershipException(Preset requestedPreset,
                                           Set<Permission> sharerMembershipPerms) {
        super("share preset " + requestedPreset
            + " exceeds sharer membership perms " + sharerMembershipPerms);
        this.requestedPreset = requestedPreset;
        this.sharerMembershipPerms = sharerMembershipPerms;
    }

    public Preset getRequestedPreset() {
        return requestedPreset;
    }

    public Set<Permission> getSharerMembershipPerms() {
        return sharerMembershipPerms;
    }
}
