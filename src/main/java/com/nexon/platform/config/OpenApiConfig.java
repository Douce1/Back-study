package com.nexon.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Nexon Game Platform Core API")
                        .description("넥슨 게임 공통 플랫폼 서비스 API 명세서 (동시성 제어 쿠폰 발급, 유저 관리, 비동기 이벤트 큐)")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Platform Backend Team")
                                .email("backend-dev@nexon.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("로컬 개발 서버")
                ));
    }
}