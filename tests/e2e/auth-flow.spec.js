import { expect, test } from "@playwright/test";

test("登录成功后跳转仪表盘", async ({ page }) => {
  await page.goto("/login.html");
  await page.fill("#loginIdentity", "admin@system.com");
  await page.fill("#loginPassword", "Admin@123");
  await page.click("#loginSubmit");
  await expect(page).toHaveURL(/dashboard\.html#overview/);
  await expect(page.locator("#currentUserName")).toContainText("admin");
});

test("注册成功后自动登录并进入仪表盘", async ({ page }) => {
  const uid = `${Date.now()}`;
  await page.goto("/register.html");

  await page.fill("#registerUsername", `user_${uid}`);
  await page.fill("#registerEmail", `user_${uid}@example.com`);
  await page.fill("#registerPassword", "Register@123");
  await page.fill("#registerConfirmPassword", "Register@123");
  await page.check("#agreeTerms");

  await expect(page.locator("#registerSubmit")).toBeEnabled({ timeout: 4000 });
  await page.click("#registerSubmit");
  await expect(page).toHaveURL(/dashboard\.html#overview/);
});

test("锁定账户登录时给出错误并禁用按钮", async ({ page }) => {
  await page.goto("/login.html");
  await page.fill("#loginIdentity", "locked@system.com");
  await page.fill("#loginPassword", "Locked@123");
  await page.click("#loginSubmit");

  await expect(page.locator("#loginGlobalError")).toContainText("账户已锁定");
  await expect(page.locator("#loginSubmit")).toBeDisabled();
});
