package com.blog.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.BusinessException;
import com.blog.common.ErrorCode;
import com.blog.common.PageResult;
import com.blog.module.user.entity.User;
import com.blog.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户服务。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public User getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    public User getByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    public PageResult<User> page(long page, long size, String keyword, String role) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(keyword), w -> w
                        .like(User::getUsername, keyword)
                        .or().like(User::getNickname, keyword)
                        .or().like(User::getEmail, keyword))
                .eq(StringUtils.hasText(role), User::getRole, role)
                .orderByDesc(User::getId);
        return PageResult.of(userMapper.selectPage(new Page<>(page, size), wrapper));
    }

    public List<User> list() {
        return userMapper.selectList(new LambdaQueryWrapper<User>().orderByAsc(User::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(User user, String rawPassword) {
        if (getByUsername(user.getUsername()) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        if (user.getRole() == null || user.getRole().isBlank()) {
            user.setRole("author");
        }
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        if (user.getNickname() == null || user.getNickname().isBlank()) {
            user.setNickname(user.getUsername());
        }
        userMapper.insert(user);
        return user.getId();
    }

    /**
     * 更新用户资料（不含密码）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(User user) {
        User exists = getById(user.getId());
        if (user.getUsername() != null && !user.getUsername().equals(exists.getUsername())) {
            User conflict = getByUsername(user.getUsername());
            if (conflict != null && !conflict.getId().equals(user.getId())) {
                throw new BusinessException(ErrorCode.USERNAME_EXISTS);
            }
        }
        User target = new User();
        target.setId(user.getId());
        target.setUsername(user.getUsername());
        target.setNickname(user.getNickname());
        target.setAvatar(user.getAvatar());
        target.setEmail(user.getEmail());
        target.setRole(user.getRole());
        target.setStatus(user.getStatus());
        userMapper.updateById(target);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long id, String oldPassword, String newPassword) {
        User user = getById(id);
        if (StringUtils.hasText(oldPassword) && !passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_ERROR);
        }
        User target = new User();
        target.setId(id);
        target.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(target);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        User user = getById(id);
        if (user.getId().equals(1L) && status != null && status == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "初始管理员账号不可停用");
        }
        User target = new User();
        target.setId(id);
        target.setStatus(status);
        userMapper.updateById(target);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        User user = getById(id);
        if (user.getId().equals(1L)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "初始管理员账号不可删除");
        }
        userMapper.deleteById(id);
    }

    public void updateLastLogin(Long id) {
        User target = new User();
        target.setId(id);
        target.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(target);
    }

    public long count() {
        return userMapper.selectCount(null);
    }
}
