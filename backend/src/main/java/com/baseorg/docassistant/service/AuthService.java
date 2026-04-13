package com.baseorg.docassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baseorg.docassistant.dto.auth.*;
import com.baseorg.docassistant.entity.User;
import com.baseorg.docassistant.exception.BusinessException;
import com.baseorg.docassistant.exception.ErrorCode;
import com.baseorg.docassistant.mapper.UserMapper;
import com.baseorg.docassistant.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 用户登录
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identity = request.getIdentity();
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, identity)
                        .or()
                        .eq(User::getEmail, identity)
        );
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        checkUserStatus(user);

        user.setLastLoginAt(java.time.LocalDateTime.now());
        userMapper.updateById(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name());

        log.info("用户登录成功: username={}", user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpiration())
                .user(AuthResponse.UserInfo.fromEntity(user))
                .build();
    }

    /**
     * 用户注册
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        ) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_TAKEN);
        }

        if (userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail())
        ) > 0) {
            throw new BusinessException(ErrorCode.EMAIL_TAKEN);
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getUsername())
                .status(User.UserStatus.PENDING)
                .role(User.UserRole.USER)
                .build();

        userMapper.insert(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name());

        log.info("用户注册成功: username={}, email={}", user.getUsername(), user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpiration())
                .user(AuthResponse.UserInfo.fromEntity(user))
                .build();
    }

    /**
     * 检查用户名是否可用
     */
    @Transactional(readOnly = true)
    public CheckUsernameResponse checkUsername(String username) {
        boolean available = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        ) == 0;

        return CheckUsernameResponse.builder()
                .username(username)
                .available(available)
                .message(available ? "用户名可用" : "用户名已被占用")
                .build();
    }

    /**
     * 启动 SSO 登录
     */
    public SsoStartResponse startSso(String provider) {
        String state = UUID.randomUUID().toString().replace("-", "");
        String authorizeUrl = buildSsoAuthorizeUrl(provider, state);

        log.info("启动 SSO 登录: provider={}, state={}", provider, state);

        return SsoStartResponse.builder()
                .authorizeUrl(authorizeUrl)
                .state(state)
                .provider(provider)
                .build();
    }

    private void checkUserStatus(User user) {
        switch (user.getStatus()) {
            case PENDING -> throw new BusinessException(ErrorCode.AUTH_NOT_ACTIVATED);
            case LOCKED -> throw new BusinessException(ErrorCode.AUTH_LOCKED);
            case ACTIVE -> {}
        }
    }

    private String buildSsoAuthorizeUrl(String provider, String state) {
        String clientId = "your-client-id";
        String redirectUri = "http://localhost:8080/api/v1/auth/sso/callback";

        return switch (provider.toLowerCase()) {
            case "feishu" -> String.format(
                    "https://open.feishu.cn/open-apis/authen/v1/authorize?app_id=%s&redirect_uri=%s&state=%s",
                    clientId, redirectUri, state);
            case "github" -> String.format(
                    "https://github.com/login/oauth/authorize?client_id=%s&redirect_uri=%s&state=%s&scope=user:email",
                    clientId, redirectUri, state);
            default -> throw new BusinessException(ErrorCode.INVALID_PARAMETER, "不支持的 SSO 提供商: " + provider);
        };
    }
}
