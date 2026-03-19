import { beforeEach, describe, expect, it } from "vitest";
import { clearDb } from "../../src/js/services/mock/mock-db.js";
import { checkUsername, login, register } from "../../src/js/services/mock/mock-api.js";

describe("mock auth api", () => {
  beforeEach(() => {
    clearDb();
  });

  it("should login admin successfully", async () => {
    const result = await login({ identity: "admin@system.com", password: "Admin@123" });
    expect(result.ok).toBe(true);
    expect(result.data.user.username).toBe("admin");
  });

  it("should reject invalid credentials", async () => {
    const result = await login({ identity: "admin@system.com", password: "wrong" });
    expect(result.ok).toBe(false);
    expect(result.code).toBe("AUTH_INVALID_CREDENTIALS");
  });

  it("should register and return token", async () => {
    const result = await register({
      username: "new_user",
      email: "new-user@example.com",
      password: "Register@123"
    });
    expect(result.ok).toBe(true);
    expect(result.data.token).toContain("mock-token");
  });

  it("should reject duplicated username", async () => {
    const result = await checkUsername({ username: "admin" });
    expect(result.ok).toBe(false);
    expect(result.code).toBe("USERNAME_TAKEN");
  });
});
