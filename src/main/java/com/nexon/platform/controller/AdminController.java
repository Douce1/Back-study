package com.nexon.platform.controller;

import com.nexon.platform.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "03. Admin Platform API", description = "넥슨 플랫폼 운영자/GM 전용 백오피스 API (ROLE_ADMIN 전용)")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @Operation(summary = "관리자 전용 시스템 헬스체크 및 운영자 확인", description = "ROLE_ADMIN 권한을 가진 사용자만 접근 가능합니다.")
    @GetMapping("/dashboard")
    public CommonResponse<Map<String, Object>> getAdminDashboard(@AuthenticationPrincipal Long adminUserId) {
        Map<String, Object> result = new HashMap<>();
        result.put("adminUserId", adminUserId != null ? adminUserId : "UNKNOWN");
        result.put("serverStatus", "OPERATIONAL");
        result.put("activeCouponsCount", 12);
        result.put("kafkaQueueLag", 0);

        return CommonResponse.ok("운영자 대시보드 인증 성공", result);
    }
}