package com.nexon.platform.controller;

import com.nexon.platform.dto.CommonResponse;
import com.nexon.platform.dto.UserCreateRequest;
import com.nexon.platform.dto.UserResponse;
import com.nexon.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "01. User Account API", description = "넥슨 계정 등록, 조회 및 Redis 캐싱 연동 API")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "유저 신규 등록", description = "NexonTag를 기반으로 새로운 플랫폼 유저를 등록합니다.")
    @PostMapping
    public CommonResponse<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        UserResponse response = userService.createUser(request);
        return CommonResponse.ok("유저 등록 성공", response);
    }

    @Operation(summary = "유저 단건 조회 (Cache-Aside)", description = "Redis 캐시를 우선 조회하고, 캐시 미스 시 DB에서 읽어 캐시에 적재합니다.")
    @GetMapping("/{userId}")
    public CommonResponse<UserResponse> getUser(
            @Parameter(description = "조회할 유저 ID", example = "1") @PathVariable("userId") Long userId
    ) {
        UserResponse response = userService.getUser(userId);
        return CommonResponse.ok("유저 조회 성공", response);
    }
}