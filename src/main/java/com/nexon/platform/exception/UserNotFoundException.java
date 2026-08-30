package com.nexon.platform.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }

    public UserNotFoundException(Long userId) {
        super("유저를 찾을 수 없습니다. ID: " + userId);
    }
}