package com.nexon.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public class AuthDto {

    @Schema(description = "회원가입 및 로그인 요청 DTO")
    public static class LoginRequest {
        @Schema(description = "넥슨 태그", example = "MapleMaster#1234")
        @NotBlank(message = "넥슨 태그는 필수입니다.")
        private String nexonTag;

        @Schema(description = "비밀번호", example = "pass1234!")
        @NotBlank(message = "비밀번호는 필수입니다.")
        private String password;

        public LoginRequest() {}

        public LoginRequest(String nexonTag, String password) {
            this.nexonTag = nexonTag;
            this.password = password;
        }

        public String getNexonTag() {
            return nexonTag;
        }

        public String getPassword() {
            return password;
        }
    }

    @Schema(description = "JWT 토큰 발급 응답 DTO")
    public static class TokenResponse {
        @Schema(description = "토큰 타입", example = "Bearer")
        private String grantType = "Bearer";

        @Schema(description = "Access Token")
        private String accessToken;

        @Schema(description = "유저 고유 ID", example = "1")
        private Long userId;

        @Schema(description = "넥슨 태그", example = "MapleMaster#1234")
        private String nexonTag;

        public TokenResponse(String accessToken, Long userId, String nexonTag) {
            this.accessToken = accessToken;
            this.userId = userId;
            this.nexonTag = nexonTag;
        }

        public String getGrantType() {
            return grantType;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public Long getUserId() {
            return userId;
        }

        public String getNexonTag() {
            return nexonTag;
        }
    }
}