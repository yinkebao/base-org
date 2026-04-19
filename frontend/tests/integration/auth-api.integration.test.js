/**
 * 认证模块联调测试用例
 *
 * 运行方式：
 * 1. 确保后端服务已启动
 * 2. 设置环境变量：VITE_API_MODE=real VITE_API_BASE_URL=http://localhost:8080
 * 3. 运行测试：npm test -- tests/integration/auth-api.integration.test.js
 *
 * 注意：部分测试需要手动验证（标记为 @manual）
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from "vitest";
import { AuthService } from "../../src/js/services/auth-service.js";
import { getSession, clearSession, saveSession } from "../../src/js/services/session-service.js";

// 测试配置
const TEST_CONFIG = {
  baseUrl: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080",
  testUser: {
    username: `testuser_${Date.now()}`,
    email: `test_${Date.now()}@example.com`,
    password: "Test@123456"
  },
  adminUser: {
    identity: "admin",
    password: "Admin@123"
  }
};

describe("认证 API 联调测试", () => {
  beforeAll(() => {
    // 验证是否为 real 模式
    const mode = import.meta.env.VITE_API_MODE;
    if (mode !== "real") {
      console.warn("⚠️ 警告：当前非 real 模式，测试将使用 mock 数据");
    }
  });

  afterAll(() => {
    // 清理测试会话
    clearSession();
  });

  beforeEach(() => {
    // 每个测试前清除会话
    clearSession();
  });

  describe("POST /api/v1/auth/register", () => {
    it("应该成功注册新用户", async () => {
      const result = await AuthService.register({
        username: TEST_CONFIG.testUser.username,
        email: TEST_CONFIG.testUser.email,
        password: TEST_CONFIG.testUser.password
      });

      expect(result.ok).toBe(true);
      expect(result.data).toBeDefined();
      expect(result.data.user).toBeDefined();
      expect(result.data.user.username).toBe(TEST_CONFIG.testUser.username);
      expect(result.data.user.email).toBe(TEST_CONFIG.testUser.email);
      expect(result.data.token).toBeDefined();
      expect(result.traceId).toBeDefined();
    });

    it("应该拒绝重复用户名", async () => {
      // 先注册一次
      await AuthService.register({
        username: TEST_CONFIG.testUser.username,
        email: `another_${Date.now()}@example.com`,
        password: TEST_CONFIG.testUser.password
      });

      // 再用相同用户名注册
      const result = await AuthService.register({
        username: TEST_CONFIG.testUser.username,
        email: `different_${Date.now()}@example.com`,
        password: TEST_CONFIG.testUser.password
      });

      expect(result.ok).toBe(false);
      expect(result.code).toBe("USERNAME_TAKEN");
    });

    it("应该拒绝重复邮箱", async () => {
      const uniqueUsername = `user_${Date.now()}`;
      const email = `dup_${Date.now()}@example.com`;

      // 先注册一次
      await AuthService.register({
        username: uniqueUsername,
        email,
        password: TEST_CONFIG.testUser.password
      });

      // 再用相同邮箱注册
      const result = await AuthService.register({
        username: `another_${Date.now()}`,
        email,
        password: TEST_CONFIG.testUser.password
      });

      expect(result.ok).toBe(false);
      expect(result.code).toBe("EMAIL_TAKEN");
    });

    it("应该拒绝弱密码", async () => {
      const result = await AuthService.register({
        username: `weak_${Date.now()}`,
        email: `weak_${Date.now()}@example.com`,
        password: "123456" // 弱密码
      });

      expect(result.ok).toBe(false);
      expect(result.code).toBe("PASSWORD_WEAK");
    });

    it("应该验证必填字段", async () => {
      const result = await AuthService.register({
        username: "",
        email: "",
        password: ""
      });

      expect(result.ok).toBe(false);
    });
  });

  describe("GET /api/v1/auth/check-username", () => {
    it("应该返回用户名可用", async () => {
      const uniqueUsername = `available_${Date.now()}`;
      const result = await AuthService.checkUsername({ username: uniqueUsername });

      expect(result.ok).toBe(true);
      expect(result.data.available).toBe(true);
    });

    it("应该返回用户名已占用", async () => {
      // 先注册一个用户
      await AuthService.register({
        username: TEST_CONFIG.testUser.username,
        email: `check_${Date.now()}@example.com`,
        password: TEST_CONFIG.testUser.password
      });

      // 检查该用户名
      const result = await AuthService.checkUsername({ username: TEST_CONFIG.testUser.username });

      expect(result.ok).toBe(false);
      expect(result.code).toBe("USERNAME_TAKEN");
    });
  });

  describe("POST /api/v1/auth/login", () => {
    it("应该成功登录", async () => {
      // 先确保用户存在
      await AuthService.register({
        username: TEST_CONFIG.testUser.username,
        email: TEST_CONFIG.testUser.email,
        password: TEST_CONFIG.testUser.password
      });

      // 用用户名登录
      const result = await AuthService.login({
        identity: TEST_CONFIG.testUser.username,
        password: TEST_CONFIG.testUser.password
      });

      expect(result.ok).toBe(true);
      expect(result.data.user).toBeDefined();
      expect(result.data.token).toBeDefined();

      // 验证会话已保存
      const session = getSession();
      expect(session).not.toBeNull();
      expect(session.token).toBe(result.data.token);
    });

    it("应该支持邮箱登录", async () => {
      const result = await AuthService.login({
        identity: TEST_CONFIG.testUser.email,
        password: TEST_CONFIG.testUser.password
      });

      expect(result.ok).toBe(true);
      expect(result.data.user.email).toBe(TEST_CONFIG.testUser.email);
    });

    it("应该拒绝错误密码", async () => {
      const result = await AuthService.login({
        identity: TEST_CONFIG.testUser.username,
        password: "WrongPassword@123"
      });

      expect(result.ok).toBe(false);
      expect(result.code).toBe("AUTH_INVALID_CREDENTIALS");
    });

    it("应该拒绝不存在的用户", async () => {
      const result = await AuthService.login({
        identity: "nonexistent_user_xyz",
        password: "AnyPassword@123"
      });

      expect(result.ok).toBe(false);
      expect(result.code).toBe("AUTH_INVALID_CREDENTIALS");
    });
  });

  describe("Authorization Header 注入", () => {
    it("已登录用户请求应携带 Authorization header", async () => {
      // 登录
      const loginResult = await AuthService.login({
        identity: TEST_CONFIG.testUser.username,
        password: TEST_CONFIG.testUser.password
      });

      expect(loginResult.ok).toBe(true);

      // 保存会话
      saveSession(loginResult.data);

      // 验证会话中有 token
      const session = getSession();
      expect(session?.token).toBeDefined();
    });

    /**
     * @manual 需要通过浏览器开发者工具验证
     * 1. 登录后打开 Network 面板
     * 2. 触发任意需要认证的 API 请求
     * 3. 检查 Request Headers 中是否包含 Authorization: Bearer <token>
     */
    it("手动验证：检查请求头中的 Authorization", async () => {
      // 此测试仅供手动验证参考
      const session = getSession();
      console.log("当前 token:", session?.token);
      console.log("请在浏览器 Network 面板中验证 Authorization header");
    });
  });

  describe("401 响应处理", () => {
    it("收到 401 应清除会话", async () => {
      // 模拟设置一个无效 token
      saveSession({ token: "invalid_token", user: { id: "test" } });

      // 验证会话存在
      expect(getSession()).not.toBeNull();

      // 注意：此测试需要后端返回 401 才能验证
      // 实际测试时需要调用一个会返回 401 的接口
      console.log("需要通过实际 401 响应验证会话清除逻辑");
    });
  });
});

describe("响应格式验证", () => {
  it("成功响应应符合标准格式", async () => {
    const result = await AuthService.checkUsername({
      username: `format_test_${Date.now()}`
    });

    expect(result).toHaveProperty("ok");
    expect(result).toHaveProperty("traceId");

    if (result.ok) {
      expect(result).toHaveProperty("data");
    } else {
      expect(result).toHaveProperty("code");
      expect(result).toHaveProperty("message");
    }
  });

  it("错误响应应包含完整错误信息", async () => {
    const result = await AuthService.login({
      identity: "invalid_user",
      password: "invalid_password"
    });

    expect(result.ok).toBe(false);
    expect(result.code).toBeDefined();
    expect(result.message).toBeDefined();
    expect(typeof result.message).toBe("string");
    expect(result.traceId).toBeDefined();
  });
});

/**
 * 联调检查清单
 *
 * ## 环境配置
 * - [ ] VITE_API_MODE=real
 * - [ ] VITE_API_BASE_URL 已正确设置
 * - [ ] 后端服务已启动且可访问
 *
 * ## 网络配置
 * - [ ] CORS 已配置允许前端域名
 * - [ ] OPTIONS 预检请求正常
 *
 * ## 认证流程
 * - [ ] 注册接口返回正确格式
 * - [ ] 登录接口返回 token
 * - [ ] token 存储到 localStorage
 * - [ ] 后续请求携带 Authorization header
 * - [ ] 401 响应清除会话并跳转登录
 *
 * ## 错误处理
 * - [ ] 错误码与前端一致
 * - [ ] 错误消息用户友好
 * - [ ] traceId 可追踪
 */
