package com.ibizdrive.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * Spring Security {@link UserDetails} 어댑터. 도메인 {@link User}를 감싼다.
 *
 * <p>계정 상태 게이트 (Spring Security 호출자 → {@code DaoAuthenticationProvider}가 검사):
 * <ul>
 *   <li>{@code isEnabled} — {@code user.isActive() && !user.isDeleted()}.
 *       관리자 비활성화 또는 soft delete 상태에서 로그인 차단.</li>
 *   <li>{@code isAccountNonLocked} — {@code !user.isLocked()}.
 *       관리자 수동 잠금 (ADR #20 {@code locked_at}) 시 차단. 5회 실패 lockout은
 *       이와 별개로 인증 전(pre-auth) Redis/DB 카운터에서 처리 (A1.3).</li>
 *   <li>{@code isAccountNonExpired} — 항상 true. 계정 만료 정책은 v1.x.</li>
 *   <li>{@code isCredentialsNonExpired} — 항상 true.
 *       {@code mustChangePassword} (ADR #21)는 인증 성공 후 별도 처리 — 자격 증명 만료가 아님.</li>
 * </ul>
 *
 * <p>Authority: {@code ROLE_<role>} (Spring Security 관례 — {@code @PreAuthorize("hasRole('ADMIN')")}와
 * 매칭). 권한 enum 9종은 별도 layer (A1.5 {@code PermissionService})에서 처리.
 *
 * <p>다운스트림(예: 로그인 후 audit, last_login_at 갱신)은 {@link #getUser()}로 도메인 entity에 접근.
 */
public class IbizDriveUserDetails implements UserDetails, Serializable {

    /**
     * Spring Session JDBC 직렬화 대상 — Spring Security가 SPRING_SECURITY_CONTEXT 세션 attribute에
     * principal로 본 인스턴스를 저장. {@link UserDetails} 자체가 {@link Serializable}을 super-interface로
     * 갖지만 명시 선언으로 의도를 분명히 하고 serialVersionUID를 함께 고정한다.
     */
    private static final long serialVersionUID = 1L;

    private final User user;

    public IbizDriveUserDetails(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        // Spring Security 관례 상 "username" — 우리는 email을 식별자로 사용.
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !user.isLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isActive() && !user.isDeleted();
    }
}
