package com.ibizdrive.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * DB-backed {@link UserDetailsService} — Spring Security 인증 흐름이 사용자를 조회할 때 호출된다.
 *
 * <p>이메일 정규화 책임은 본 서비스가 진다 (docs/03 §2.7). 입력은 trim + lowercase 후
 * {@link UserRepository#findActiveByEmail}로 조회. 조회 실패 시 {@link UsernameNotFoundException}
 * — Spring Security는 이를 인증 실패로 변환하며, 메시지는 {@code BadCredentialsException}과
 * 동일 응답으로 통일된다 (계정 enumeration 방지, docs/03 §2.3).
 *
 * <p>{@code @Service}로 컴포넌트 스캔 — Spring Security {@code DaoAuthenticationProvider}가
 * 자동으로 {@link UserDetailsService} 빈을 발견한다.
 */
@Service
public class DbUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DbUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return userRepository.findActiveByEmail(normalized)
            .map(IbizDriveUserDetails::new)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
