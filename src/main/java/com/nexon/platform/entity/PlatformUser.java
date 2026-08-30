package com.nexon.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "platform_user")
public class PlatformUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "nexon_tag", nullable = false, unique = true, length = 50)
    private String nexonTag;

    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "account_status", nullable = false, length = 20)
    private String accountStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PlatformUser() {}

    // 일반 생성자 (회원가입/신규 등록용)
    public PlatformUser(String nexonTag, String password, UserRole role) {
        this.nexonTag = nexonTag;
        this.password = password != null ? password : "NO_PASSWORD";
        this.role = role != null ? role : UserRole.ROLE_USER;
        this.accountStatus = "ACTIVE";
        this.createdAt = LocalDateTime.now();
    }

    // 넥슨 태그 변경 메서드
    public void updateNexonTag(String nexonTag) {
        this.nexonTag = nexonTag;
    }

    public Long getUserId() {
        return userId;
    }

    public String getNexonTag() {
        return nexonTag;
    }

    public String getPassword() {
        return password;
    }

    public UserRole getRole() {
        return role;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}