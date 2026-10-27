package com.blog.module.user.controller;

import com.blog.common.PageResult;
import com.blog.common.R;
import com.blog.common.log.OpLog;
import com.blog.module.user.dto.UserVO;
import com.blog.module.user.entity.User;
import com.blog.module.user.service.UserService;
import com.blog.module.setting.service.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台用户管理（仅管理员）。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "后台-用户", description = "用户增删改查、角色分配、启用停用、改密")
public class AdminUserController {

    private final UserService userService;
    private final SettingService settingService;

    @GetMapping
    @Operation(summary = "用户分页列表")
    public R<PageResult<UserVO>> page(@RequestParam(defaultValue = "1") long page,
                                      @RequestParam(required = false) Long size,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String role) {
        long pageSize = size != null && size > 0 ? size : settingService.getInt("page_size", 10);
        return R.ok(userService.page(page, pageSize, keyword, role).convert(UserVO::from));
    }

    @GetMapping("/list")
    @Operation(summary = "用户下拉列表")
    public R<List<UserVO>> list() {
        return R.ok(userService.list().stream().map(UserVO::from).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "用户详情")
    public R<UserVO> detail(@PathVariable Long id) {
        return R.ok(UserVO.from(userService.getById(id)));
    }

    @PostMapping
    @OpLog(module = "user", action = "create")
    @Operation(summary = "新建用户")
    public R<Long> create(@RequestBody CreateUserRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setAvatar(request.getAvatar());
        user.setRole(request.getRole());
        user.setStatus(request.getStatus());
        return R.ok(userService.create(user, request.getPassword()));
    }

    @PutMapping("/{id}")
    @OpLog(module = "user", action = "update")
    @Operation(summary = "修改用户")
    public R<Void> update(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        User user = new User();
        user.setId(id);
        user.setUsername(request.getUsername());
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setAvatar(request.getAvatar());
        user.setRole(request.getRole());
        user.setStatus(request.getStatus());
        userService.update(user);
        return R.ok();
    }

    @PutMapping("/{id}/password")
    @OpLog(module = "user", action = "change-password")
    @Operation(summary = "修改密码", description = "管理员重置他人密码时 oldPassword 可为空")
    public R<Void> changePassword(@PathVariable Long id, @RequestBody ChangePasswordRequest request) {
        userService.changePassword(id, request.getOldPassword(), request.getNewPassword());
        return R.ok();
    }

    @PutMapping("/{id}/status")
    @OpLog(module = "user", action = "update-status")
    @Operation(summary = "启用 / 停用用户")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "user", action = "delete")
    @Operation(summary = "删除用户")
    public R<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok();
    }

    // ---------------- DTO ----------------

    @Data
    public static class CreateUserRequest {
        private String username;
        private String password;
        private String nickname;
        private String email;
        private String avatar;
        private String role;
        private Integer status;
    }

    @Data
    public static class UpdateUserRequest {
        private String username;
        private String nickname;
        private String email;
        private String avatar;
        private String role;
        private Integer status;
    }

    @Data
    public static class ChangePasswordRequest {
        /** 管理员重置他人密码时可为空 */
        private String oldPassword;
        private String newPassword;
    }
}
