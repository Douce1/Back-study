package com.nexon.platform.dto;

import com.nexon.platform.entity.PlatformUser;
import java.time.LocalDateTime;

public class UserResponse {

    private Long userId;
    private String nexonTag;
    private String accountStatus;
    private LocalDateTime createdAt;

    public UserResponse() {
    }

    public UserResponse(PlatformUser user) {
        this.userId = user.getUserId();
        this.nexonTag = user.getNexonTag();
        this.accountStatus = user.getAccountStatus();
        this.createdAt = user.getCreatedAt();
    }

    public Long getUserId() { return userId; }
    public String getNexonTag() { return nexonTag; }
    public String getAccountStatus() { return accountStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}