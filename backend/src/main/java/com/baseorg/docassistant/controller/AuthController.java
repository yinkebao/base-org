package com.baseorg.docassistant.controller;

import com.baseorg.docassistant.dto.ApiResponse;
import com.baseorg.docassistant.dto.auth.*;
import com.baseorg.docassistant.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@Tag(name = "认证", description = "用户认证相关接口")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录
     */
    @Operation(summary = "用户登录", description = "使用用户名和密码进行登录")
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ApiResponse.success(response);
    }

    /**
     * 用户注册
     */
    @Operation(summary = "用户注册", description = "注册新用户账号")
    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ApiResponse.success(response);
    }

    /**
     * 检查用户名是否可用
     */
    @Operation(summary = "检查用户名", description = "检查用户名是否已被占用")
    @GetMapping("/check-username")
    public ApiResponse<CheckUsernameResponse> checkUsername(@RequestParam String username) {
        CheckUsernameResponse response = authService.checkUsername(username);
        return ApiResponse.success(response);
    }

    /**
     * 启动 SSO 登录
     */
    @Operation(summary = "启动 SSO 登录", description = "获取 SSO 登录的授权 URL")
    @GetMapping("/sso/start")
    public ApiResponse<SsoStartResponse> startSso(@RequestParam String provider) {
        SsoStartResponse response = authService.startSso(provider);
        return ApiResponse.success(response);
    }
}
