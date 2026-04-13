import { expect, test } from "@playwright/test";

test("文档导入向导 happy path", async ({ page }) => {
  await page.goto("/login.html");
  await page.fill("#loginIdentity", "admin@system.com");
  await page.fill("#loginPassword", "Admin@123");
  await page.click("#loginSubmit");
  await expect(page).toHaveURL(/dashboard\.html#overview/);

  await page.goto("/dashboard.html#imports");
  await expect(page.getByText("选择导入来源")).toBeVisible();

  await page.setInputFiles("#importsFileInput", {
    name: "spec.md",
    mimeType: "text/markdown",
    buffer: Buffer.from("# 导入测试\n\n这是一段用于导入流程的测试内容。")
  });
  await page.getByRole("button", { name: "下一步" }).click();

  await page.fill("#importsVersionInput", "v1.2.0");
  await page.getByRole("button", { name: "下一步" }).click();

  await page.fill("#importsChunkSizeInput", "500");
  await page.fill("#importsChunkOverlapInput", "100");
  await page.getByRole("button", { name: "下一步" }).click();
  await page.getByRole("button", { name: "提交导入" }).click();

  await expect(page.getByText("任务状态：")).toBeVisible();
});
