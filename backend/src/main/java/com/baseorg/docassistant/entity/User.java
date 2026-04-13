package com.baseorg.docassistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("username")
    private String username;

    @TableField("email")
    private String email;

    @TableField("password")
    private String password;

    @TableField("nickname")
    private String nickname;

    @TableField("avatar")
    private String avatar;

    @TableField("status")
    private UserStatus status;

    @TableField("role")
    private UserRole role;

    @TableField("sso_provider")
    private String ssoProvider;

    @TableField("sso_id")
    private String ssoId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * 用户状态
     */
    public enum UserStatus {
        PENDING,    // 待激活
        ACTIVE,     // 已激活
        LOCKED      // 已锁定
    }

    /**
     * 用户角色
     */
    public enum UserRole {
        USER,       // 普通用户
        ADMIN       // 管理员
    }
}
