package com.baseorg.docassistant.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSO 登录启动响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsoStartResponse {

    private String authorizeUrl;
    private String state;
    private String provider;
}
