package com.blog.security;

import com.blog.module.user.entity.User;
import com.blog.module.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 根据用户名加载用户详情，供 JWT 过滤器与 Spring Security 使用。
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在：" + username);
        }
        return SecurityUser.of(user.getId(), user.getUsername(), user.getPasswordHash(), user.getRole(),
                user.getNickname(), user.getAvatar(), user.getEmail(), user.getStatus() != null && user.getStatus() == 1);
    }
}
