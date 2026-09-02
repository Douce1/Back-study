package com.nexon.platform.exception;

import com.nexon.platform.dto.CommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. 비즈니스 로직 예외 (중복 계정, 비밀번호 불일치 등) -> 400 Bad Request
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.fail(e.getMessage()));
    }

    // 2. [추가] 1인 1쿠폰 중복 발급 차단 예외 -> 400 Bad Request
    @ExceptionHandler(DuplicateCouponIssueException.class)
    public ResponseEntity<CommonResponse<Void>> handleDuplicateCouponIssue(DuplicateCouponIssueException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.fail(e.getMessage()));
    }

    // 3. 쿠폰 재고 소진 예외 -> 400 Bad Request
    @ExceptionHandler(CouponOutOfStockException.class)
    public ResponseEntity<CommonResponse<Void>> handleCouponOutOfStock(CouponOutOfStockException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.fail(e.getMessage()));
    }

    // 4. DTO Validation 실패 -> 400 Bad Request
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.fail("입력값 검증에 실패했습니다.", errors));
    }

    // 5. 최상위 서버 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Void>> handleGeneralException(Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonResponse.fail(e.getClass().getSimpleName() + " : " + e.getMessage()));
    }
}