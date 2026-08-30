package com.nexon.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserUpdateRequest {

    @NotBlank(message = "변경할 넥슨 태그는 필수입니다.")
    @Size(min = 3, max = 50, message = "넥슨 태그는 3자 이상 50자 이하여야 합니다.")
    private String nexonTag;

    public UserUpdateRequest() {
    }

    public UserUpdateRequest(String nexonTag) {
        this.nexonTag = nexonTag;
    }

    public String getNexonTag() {
        return nexonTag;
    }

    public void setNexonTag(String nexonTag) {
        this.nexonTag = nexonTag;
    }
}