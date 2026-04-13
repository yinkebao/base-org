package com.baseorg.docassistant.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户名检查响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckUsernameResponse {

    private String username;
    private boolean available;
    private String message;
}
