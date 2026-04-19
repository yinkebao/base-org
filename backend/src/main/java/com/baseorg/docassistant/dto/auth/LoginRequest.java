package com.baseorg.docassistant.dto.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @JsonAlias("username")
    @NotBlank(message = "用户名或邮箱不能为空")
    private String identity;

    @NotBlank(message = "密码不能为空")
    private String password;

    public String getUsername() {
        return identity;
    }

    public void setUsername(String username) {
        this.identity = username;
    }
}
