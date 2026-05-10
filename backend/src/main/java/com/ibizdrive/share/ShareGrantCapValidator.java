package com.ibizdrive.share;

import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.Preset;
import com.ibizdrive.permission.WorkspaceMembershipResolver;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Plan C — §4.2 share grant cap helper.
 *
 * <p>sharer의 workspace 멤버십 default permission 집합 안에서만 grant 허용.
 * preset이 펼치는 Permission 집합({@link Preset#permissions()})이 sharer 멤버권을 초과하면
 * {@link ShareExceedsMembershipException} (→ 403 ERR_SHARE_EXCEEDS_MEMBER).
 *
 * <p>비멤버 sharer는 controller {@code @PreAuthorize("hasPermission(..., 'SHARE')")}가 이미 차단 —
 * 본 helper는 cap 검증만 담당한다.
 *
 * <p><b>Preset 펼침 진실의 출처</b>: {@link Preset#permissions()}. PermissionResolver도 동일 메서드를
 * 호출하므로 중복 없음.
 * <!-- TODO/FIXME: PermissionResolver가 Preset.permissions()를 이미 사용 중 — 단일 진실 출처 유지.
 *      Plan A 완료 후 PermissionResolver 리팩터 시 본 helper와 일관성 재확인 필요. -->
 */
@Component
public class ShareGrantCapValidator {

    private final WorkspaceMembershipResolver membershipResolver;

    public ShareGrantCapValidator(WorkspaceMembershipResolver membershipResolver) {
        this.membershipResolver = membershipResolver;
    }

    /**
     * sharer가 scopeType/scopeId 워크스페이스에서 보유한 멤버십 권한 cap 안에서 preset을 grant할 수 있는지 검증.
     *
     * @param sharerId  grant를 시도하는 사용자
     * @param scopeType 워크스페이스 종류 (DEPARTMENT 또는 TEAM)
     * @param scopeId   워크스페이스 id
     * @param preset    grant하려는 preset
     * @throws ShareExceedsMembershipException preset이 sharer 멤버권 cap을 초과한 경우
     */
    public void validate(UUID sharerId, ScopeType scopeType, UUID scopeId, Preset preset) {
        Set<Permission> sharerPerms = membershipResolver.resolve(sharerId, scopeType, scopeId);
        Set<Permission> presetPerms = preset.permissions();
        if (!sharerPerms.containsAll(presetPerms)) {
            throw new ShareExceedsMembershipException(preset, sharerPerms);
        }
    }
}
