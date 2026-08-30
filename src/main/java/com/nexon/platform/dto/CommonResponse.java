package com.nexon.platform.dto;

public class CommonResponse<T> {

    private boolean success;
    private String message;
    private T data;

    public CommonResponse() {}

    public CommonResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    // 성공 응답 (데이터 포함)
    public static <T> CommonResponse<T> ok(String message, T data) {
        return new CommonResponse<>(true, message, data);
    }

    // 실패 응답 (메시지만 전달)
    public static <T> CommonResponse<T> fail(String message) {
        return new CommonResponse<>(false, message, null);
    }

    // [추가] 실패 응답 (메시지 + 에러 상세 데이터 전달)
    public static <T> CommonResponse<T> fail(String message, T data) {
        return new CommonResponse<>(false, message, data);
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