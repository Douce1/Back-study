package com.nexon.platform.controller;

import com.nexon.platform.dto.AuthDto;
import com.nexon.platform.dto.CommonResponse;
import com.nexon.platform.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "00. Auth API", description = "넥슨 통합 계정 회원가입, 로그인 및 JWT 토큰 발급 API")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService orderService;

    public AuthController(AuthService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "넥슨 계정 신규 가입 & 토큰 발급", description = "신규 유저를 등록하고 즉시 인가에 필요한 JWT Access Token을 발급합니다.")
    @PostMapping("/signup")
    public CommonResponse<AuthDto.TokenResponse> signup(@Valid @RequestBody AuthDto.LoginRequest request) {
        return CommonResponse.ok("회원가입 완료 및 JWT 토큰 발급 성공", orderService.signup(request));
    }

    @Operation(summary = "넥슨 계정 로그인 & 토큰 발급", description = "NexonTag와 비밀번호를 검증하고 유효한 JWT Access Token을 반환합니다.")
    @PostMapping("/login")
    public CommonResponse<AuthDto.TokenResponse> login(@Valid @RequestBody AuthDto.LoginRequest request) {
        return CommonResponse.ok("로그인 성공 및 JWT 토큰 발급", orderService.login(request));
    }
}