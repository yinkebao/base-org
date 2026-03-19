import { describe, expect, it } from "vitest";
import { getPasswordStrength } from "../../src/js/utils/password-strength.js";

describe("password strength", () => {
  it("should return empty level for blank password", () => {
    expect(getPasswordStrength("")).toEqual({ level: 0, text: "无" });
  });

  it("should return weak for short password", () => {
    expect(getPasswordStrength("abc123")).toEqual({ level: 1, text: "弱" });
  });

  it("should return medium for balanced password", () => {
    expect(getPasswordStrength("Abcdef12")).toEqual({ level: 2, text: "中等" });
  });

  it("should return strong for complex password", () => {
    expect(getPasswordStrength("Admin@123")).toEqual({ level: 3, text: "强" });
  });
});
