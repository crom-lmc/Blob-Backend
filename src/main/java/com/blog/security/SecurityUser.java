package com.blog.security;

import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security 用户主体。
 */
@Data
public class SecurityUser implements UserDetails {

    private Long userId;

    private String username;

    private String password;

    private String nickname;

    private String avatar;

    private String email;

    /** admin / author */
    private String role;

    private boolean enabled = true;

    private Collection<? extends GrantedAuthority> authorities = List.of();

    public static SecurityUser of(Long userId, String username, String password, String role,
                                  String nickname, String avatar, String email, boolean enabled) {
        SecurityUser user = new SecurityUser();
        user.setUserId(userId);
        user.setUsername(username);
        user.setPassword(password);
        user.setRole(role);
        user.setNickname(nickname);
        user.setAvatar(avatar);
        user.setEmail(email);
        user.setEnabled(enabled);
        user.setAuthorities(List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
        return user;
    }

    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
