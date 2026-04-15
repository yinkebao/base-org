import { describe, expect, it } from "vitest";
import {
  validateEmail,
  validatePassword,
  validatePasswordMatch,
  validateRequired,
  validateUsername
} from "../../src/js/utils/validators.js";

describe("validators", () => {
  it("should validate required fields", () => {
    expect(validateRequired("text")).toBe(true);
    expect(validateRequired("")).toBe(false);
    expect(validateRequired("   ")).toBe(false);
  });

  it("should validate email format", () => {
    expect(validateEmail("user@example.com")).toBe(true);
    expect(validateEmail("invalid@com")).toBe(false);
  });

  it("should validate username format", () => {
    expect(validateUsername("dev_user")).toBe(true);
    expect(validateUsername("ab")).toBe(false);
    expect(validateUsername("invalid-user")).toBe(false);
  });

  it("should validate password complexity", () => {
    expect(validatePassword("Admin@123")).toBe(true);
    expect(validatePassword("simplepass")).toBe(false);
  });

  it("should validate password match", () => {
    expect(validatePasswordMatch("Admin@123", "Admin@123")).toBe(true);
    expect(validatePasswordMatch("Admin@123", "Admin@1234")).toBe(false);
  });
});
