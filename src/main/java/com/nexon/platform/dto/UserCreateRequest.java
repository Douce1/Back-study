package com.nexon.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserCreateRequest {

    @NotBlank(message = "넥슨 태그는 필수 입력 항목입니다.")
    @Size(min = 3, max = 50, message = "넥슨 태그는 3자 이상 50자 이하여야 합니다.")
    private String nexonTag;

    public UserCreateRequest() {
    }

    public UserCreateRequest(String nexonTag) {
        this.nexonTag = nexonTag;
    }

    public String getNexonTag() {
        return nexonTag;
    }

    public void setNexonTag(String nexonTag) {
        this.nexonTag = nexonTag;
    }
}