package com.nexon.platform.dto;

public class CommonResponse<T> {

    private boolean success;
    private String message;
    private T data;

    public CommonResponse() {
    }

    public CommonResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> CommonResponse<T> ok(T data) {
        return new CommonResponse<>(true, "SUCCESS", data);
    }

    public static <T> CommonResponse<T> ok(String message, T data) {
        return new CommonResponse<>(true, message, data);
    }

    public static <T> CommonResponse<T> fail(String message) {
        return new CommonResponse<>(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}