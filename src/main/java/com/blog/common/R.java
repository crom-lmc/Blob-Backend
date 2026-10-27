package com.blog.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体：{ code, message, data }
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 0 表示成功，其它为业务码
     */
    private int code;

    private String message;

    private T data;

    private long timestamp = System.currentTimeMillis();

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(ErrorCode.SUCCESS.getCode());
        r.setMessage(ErrorCode.SUCCESS.getMessage());
        r.setData(data);
        return r;
    }

    public static <T> R<T> fail(String message) {
        return fail(ErrorCode.BUSINESS_ERROR.getCode(), message);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        return fail(errorCode.getCode(), message);
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS.getCode();
    }
}
