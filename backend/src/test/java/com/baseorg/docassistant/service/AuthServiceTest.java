package com.baseorg.docassistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baseorg.docassistant.dto.auth.AuthResponse;
import com.baseorg.docassistant.dto.auth.CheckUsernameResponse;
import com.baseorg.docassistant.dto.auth.LoginRequest;
import com.baseorg.docassistant.dto.auth.RegisterRequest;
import com.baseorg.docassistant.dto.auth.SsoStartResponse;
import com.baseorg.docassistant.entity.User;
import com.baseorg.docassistant.exception.BusinessException;
import com.baseorg.docassistant.exception.ErrorCode;
import com.baseorg.docassistant.mapper.UserMapper;
import com.baseorg.docassistant.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userMapper, passwordEncoder, jwtUtil);
    }

    @Test
    void shouldLoginSuccessfullyWhenCredentialsValid() {
        // Given
        String rawPassword = "password123";
        String encodedPassword = "$2a$10$encodedPassword";
        LoginRequest request = new LoginRequest("testuser", rawPassword);

        User existingUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password(encodedPassword)
                .nickname("Test User")
                .status(User.UserStatus.ACTIVE)
                .role(User.UserRole.USER)
                .build();

        when(userMapper.selectOne(any())).thenReturn(existingUser);
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);
        when(jwtUtil.generateToken(1L, "testuser", "USER")).thenReturn("test-jwt-token");
        when(jwtUtil.getExpiration()).thenReturn(3600000L);

        // When
        AuthResponse response = authService.login(request);

        // Then
        assertThat(response.getToken()).isEqualTo("test-jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600000L);
        assertThat(response.getUser().getUsername()).isEqualTo("testuser");
        assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");

        verify(userMapper).updateById(any(User.class)); // lastLoginAt updated
    }

    @Test
    void shouldThrowExceptionWhenUserNotFound() {
        // Given
        LoginRequest request = new LoginRequest("nonexistent", "password");
        when(userMapper.selectOne(any())).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS.getCode());

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void shouldThrowExceptionWhenPasswordInvalid() {
        // Given
        String rawPassword = "wrongPassword";
        String encodedPassword = "$2a$10$encodedPassword";
        LoginRequest request = new LoginRequest("testuser", rawPassword);

        User existingUser = User.builder()
                .id(1L)
                .username("testuser")
                .password(encodedPassword)
                .status(User.UserStatus.ACTIVE)
                .build();

        when(userMapper.selectOne(any())).thenReturn(existingUser);
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS.getCode());
    }

    @Test
    void shouldThrowExceptionWhenUserPending() {
        // Given
        LoginRequest request = new LoginRequest("testuser", "password");

        User pendingUser = User.builder()
                .id(1L)
                .username("testuser")
                .password("encoded")
                .status(User.UserStatus.PENDING)
                .build();

        when(userMapper.selectOne(any())).thenReturn(pendingUser);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.AUTH_NOT_ACTIVATED.getCode());
    }

    @Test
    void shouldThrowExceptionWhenUserLocked() {
        // Given
        LoginRequest request = new LoginRequest("testuser", "password");

        User lockedUser = User.builder()
                .id(1L)
                .username("testuser")
                .password("encoded")
                .status(User.UserStatus.LOCKED)
                .build();

        when(userMapper.selectOne(any())).thenReturn(lockedUser);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.AUTH_LOCKED.getCode());
    }

    @Test
    void shouldAcceptLegacyUsernameFieldForLoginRequest() throws Exception {
        LoginRequest request = objectMapper.readValue("""
                {"username":"testuser","password":"password123"}
                """, LoginRequest.class);

        assertThat(request.getIdentity()).isEqualTo("testuser");
        assertThat(request.getUsername()).isEqualTo("testuser");
        assertThat(request.getPassword()).isEqualTo("password123");
    }

    @Test
    void shouldAcceptIdentityFieldForLoginRequest() throws Exception {
        LoginRequest request = objectMapper.readValue("""
                {"identity":"test@example.com","password":"password123"}
                """, LoginRequest.class);

        assertThat(request.getIdentity()).isEqualTo("test@example.com");
        assertThat(request.getPassword()).isEqualTo("password123");
    }

    @Test
    void shouldRegisterSuccessfullyWhenDataValid() {
        // Given
        RegisterRequest request = new RegisterRequest("newuser", "new@example.com", "password123");

        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(jwtUtil.generateToken(any(), eq("newuser"), eq("USER"))).thenReturn("new-jwt-token");
        when(jwtUtil.getExpiration()).thenReturn(3600000L);

        // When
        AuthResponse response = authService.register(request);

        // Then
        assertThat(response.getToken()).isEqualTo("new-jwt-token");
        assertThat(response.getUser().getUsername()).isEqualTo("newuser");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("newuser");
        assertThat(savedUser.getEmail()).isEqualTo("new@example.com");
        assertThat(savedUser.getPassword()).isEqualTo("encodedPassword");
        assertThat(savedUser.getStatus()).isEqualTo(User.UserStatus.PENDING);
        assertThat(savedUser.getRole()).isEqualTo(User.UserRole.USER);
    }

    @Test
    void shouldThrowExceptionWhenUsernameTaken() {
        // Given
        RegisterRequest request = new RegisterRequest("existing", "test@example.com", "password");

        when(userMapper.selectCount(any())).thenReturn(1L);

        // When & Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.USERNAME_TAKEN.getCode());

        verify(userMapper, never()).insert(any());
    }

    @Test
    void shouldThrowExceptionWhenEmailTaken() {
        // Given
        RegisterRequest request = new RegisterRequest("newuser", "existing@example.com", "password");

        when(userMapper.selectCount(any()))
                .thenReturn(0L) // username available
                .thenReturn(1L); // email taken

        // When & Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.EMAIL_TAKEN.getCode());
    }

    @Test
    void shouldReturnAvailableWhenUsernameNotTaken() {
        // Given
        when(userMapper.selectCount(any())).thenReturn(0L);

        // When
        CheckUsernameResponse response = authService.checkUsername("newuser");

        // Then
        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getMessage()).isEqualTo("用户名可用");
        assertThat(response.getUsername()).isEqualTo("newuser");
    }

    @Test
    void shouldReturnUnavailableWhenUsernameTaken() {
        // Given
        when(userMapper.selectCount(any())).thenReturn(1L);

        // When
        CheckUsernameResponse response = authService.checkUsername("existing");

        // Then
        assertThat(response.isAvailable()).isFalse();
        assertThat(response.getMessage()).isEqualTo("用户名已被占用");
    }

    @Test
    void shouldStartFeishuSsoSuccessfully() {
        // When
        SsoStartResponse response = authService.startSso("feishu");

        // Then
        assertThat(response.getProvider()).isEqualTo("feishu");
        assertThat(response.getState()).isNotEmpty();
        assertThat(response.getAuthorizeUrl()).contains("open.feishu.cn");
        assertThat(response.getAuthorizeUrl()).contains(response.getState());
    }

    @Test
    void shouldStartGithubSsoSuccessfully() {
        // When
        SsoStartResponse response = authService.startSso("github");

        // Then
        assertThat(response.getProvider()).isEqualTo("github");
        assertThat(response.getState()).isNotEmpty();
        assertThat(response.getAuthorizeUrl()).contains("github.com");
        assertThat(response.getAuthorizeUrl()).contains("client_id");
    }

    @Test
    void shouldThrowExceptionWhenSsoProviderInvalid() {
        // When & Then
        assertThatThrownBy(() -> authService.startSso("invalid"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_PARAMETER.getCode());
    }

    @Test
    void shouldStartSsoCaseInsensitive() {
        // When
        SsoStartResponse response = authService.startSso("FEISHU");

        // Then
        assertThat(response.getProvider()).isEqualTo("FEISHU");
        assertThat(response.getAuthorizeUrl()).contains("open.feishu.cn");
    }
}
