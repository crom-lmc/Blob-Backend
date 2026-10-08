package com.blog.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一业务响应码。
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "ok"),

    /* 通用 */
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已失效"),
    FORBIDDEN(403, "没有权限执行该操作"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方式不支持"),
    TOO_MANY_REQUESTS(429, "操作过于频繁，请稍后再试"),
    BUSINESS_ERROR(500, "业务处理失败"),
    SYSTEM_ERROR(5000, "系统繁忙，请稍后再试"),

    /* 用户与鉴权 1xxx */
    USER_NOT_FOUND(1001, "用户不存在"),
    PASSWORD_ERROR(1002, "用户名或密码错误"),
    ACCOUNT_DISABLED(1003, "账号已停用"),
    ACCOUNT_LOCKED(1004, "登录失败次数过多，账号已被锁定，请稍后再试"),
    CAPTCHA_ERROR(1005, "验证码错误或已过期"),
    TOKEN_INVALID(1006, "令牌无效或已过期"),
    USERNAME_EXISTS(1007, "用户名已存在"),
    OLD_PASSWORD_ERROR(1008, "原密码错误"),

    /* 数据 2xxx */
    DATA_NOT_FOUND(2001, "数据不存在"),
    DATA_ALREADY_EXISTS(2002, "数据已存在"),
    SLUG_DUPLICATE(2003, "别名已存在，请更换"),
    FILE_TYPE_NOT_ALLOWED(2004, "文件类型不允许"),
    FILE_TOO_LARGE(2005, "文件超过大小限制"),
    UPLOAD_FAILED(2006, "文件上传失败"),
    CANNOT_DELETE_ACTIVE_THEME(2007, "不能删除正在使用的主题"),
    CANNOT_DELETE_DEFAULT_THEME(2008, "默认主题不支持删除"),
    CATEGORY_IN_USE(2009, "该分类已被文章引用，不能删除"),
    TAG_IN_USE(2010, "该标签已被文章引用，不能删除"),

    /* 评论 3xxx */
    COMMENT_CLOSED(3001, "该文章已关闭评论"),
    COMMENT_TOO_FREQUENT(3002, "评论过于频繁，请稍后再试"),
    COMMENT_CONTAINS_SENSITIVE(3003, "评论包含敏感词"),
    ARTICLE_NOT_PUBLISHED(3004, "文章未发布"),

    /* 主题 4xxx */
    THEME_CONFIG_INVALID(4001, "主题配置不合法"),
    THEME_CSS_UNSAFE(4002, "自定义 CSS 包含不允许的内容");

    private final int code;
    private final String message;
}
