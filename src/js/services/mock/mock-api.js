import { getDb, saveDb } from "./mock-db.js";
import { validatePassword } from "../../utils/validators.js";

function traceId() {
  return `mock_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
}

function wait(ms = 200) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function ok(data) {
  return { ok: true, data, traceId: traceId() };
}

function fail(code, message, details = null) {
  return { ok: false, code, message, details, traceId: traceId() };
}

function createToken(userId) {
  return `mock-token-${userId}-${Date.now()}`;
}

function findUserByIdentity(users, identity) {
  const value = String(identity || "").trim().toLowerCase();
  return users.find((user) => user.email.toLowerCase() === value || user.username.toLowerCase() === value);
}

function sanitizeUser(user) {
  return {
    id: user.id,
    username: user.username,
    email: user.email,
    role: user.role,
    active: user.active
  };
}

export async function login(payload) {
  await wait();
  const db = getDb();
  const user = findUserByIdentity(db.users, payload.identity);

  if (!user) {
    return fail("AUTH_INVALID_CREDENTIALS", "用户名或密码错误");
  }

  if (!user.active) {
    return fail("AUTH_NOT_ACTIVATED", "请先激活您的账户，检查注册邮箱");
  }

  if (user.lockedUntil && user.lockedUntil > Date.now()) {
    return fail("AUTH_LOCKED", "账户已锁定，请联系管理员");
  }

  if (user.password !== payload.password) {
    user.failedCount += 1;
    if (user.failedCount >= 5) {
      user.lockedUntil = Date.now() + 15 * 60 * 1000;
      saveDb(db);
      return fail("AUTH_LOCKED", "账户已锁定，请联系管理员");
    }
    saveDb(db);
    return fail("AUTH_INVALID_CREDENTIALS", "用户名或密码错误");
  }

  user.failedCount = 0;
  user.lockedUntil = null;
  saveDb(db);

  return ok({
    user: sanitizeUser(user),
    token: createToken(user.id)
  });
}

export async function checkUsername(payload) {
  await wait(120);
  const db = getDb();
  const username = String(payload.username || "").trim().toLowerCase();
  const exists = db.users.some((user) => user.username.toLowerCase() === username);
  if (exists) {
    return fail("USERNAME_TAKEN", "该用户名已被占用");
  }
  return ok({ available: true });
}

export async function register(payload) {
  await wait();
  const db = getDb();
  const normalizedEmail = String(payload.email || "").trim().toLowerCase();
  const normalizedUsername = String(payload.username || "").trim().toLowerCase();

  const usernameExists = db.users.some((user) => user.username.toLowerCase() === normalizedUsername);
  if (usernameExists) {
    return fail("USERNAME_TAKEN", "用户名已存在");
  }

  const emailExists = db.users.some((user) => user.email.toLowerCase() === normalizedEmail);
  if (emailExists) {
    return fail("EMAIL_TAKEN", "邮箱已被注册");
  }

  if (!validatePassword(payload.password)) {
    return fail("PASSWORD_WEAK", "密码复杂度不足");
  }

  const user = {
    id: `u_${Math.random().toString(36).slice(2, 10)}`,
    username: payload.username.trim(),
    email: normalizedEmail,
    password: payload.password,
    role: "pm",
    active: true,
    failedCount: 0,
    lockedUntil: null
  };
  db.users.push(user);
  saveDb(db);

  return ok({
    user: sanitizeUser(user),
    token: createToken(user.id)
  });
}

export async function startSso() {
  await wait(80);
  return ok({
    redirectUrl: "/dashboard.html#overview?sso=1"
  });
}

export async function getMetrics() {
  await wait(150);
  const db = getDb();
  return ok(db.metrics);
}

export async function getRecentImports() {
  await wait(180);
  const db = getDb();
  return ok(db.recentImports);
}

export async function getAlerts() {
  await wait(180);
  const db = getDb();
  return ok(db.alerts);
}
