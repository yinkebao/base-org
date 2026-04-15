import { expect, test } from "@playwright/test";

test("文档模块从列表进入详情并回跳目录", async ({ page }) => {
  await page.goto("/login.html");
  await page.fill("#loginIdentity", "admin@system.com");
  await page.fill("#loginPassword", "Admin@123");
  await page.click("#loginSubmit");
  await expect(page).toHaveURL(/dashboard\.html#overview/);

  await page.goto("/dashboard.html#docs");
  await expect(page.getByText("文档管理中心")).toBeVisible();
  await page.getByRole("button", { name: "查看详情" }).first().click();

  await expect(page).toHaveURL(/dashboard\.html#doc-view\?docId=/);
  await expect(page.getByText("Azure Logic 架构核心规范 v2.4")).toBeVisible();

  await page.getByRole("link", { name: "技术规格" }).click();
  await expect(page).toHaveURL(/dashboard\.html#docs\?folderId=folder-tech/);
});

test("部门筛选联动文档列表", async ({ page }) => {
  await page.goto("/login.html");
  await page.fill("#loginIdentity", "admin@system.com");
  await page.fill("#loginPassword", "Admin@123");
  await page.click("#loginSubmit");

  await page.goto("/dashboard.html#docs");
  // 等待文档列表加载完成
  await page.waitForTimeout(2000);
  await page.waitForSelector("#docsDeptSelect", { timeout: 5000 });
  await page.selectOption("#docsDeptSelect", "产品设计部");
  // 等待列表重新加载
  await page.waitForTimeout(1000);
  await expect(page.getByText("Design Token 指南")).toBeVisible();
  await expect(page.getByText("03 月架构周会纪要")).toHaveCount(0);
});

test("目录树文件夹支持新建文档并直接进入编辑态", async ({ page }) => {
  await page.goto("/login.html");
  await page.fill("#loginIdentity", "admin@system.com");
  await page.fill("#loginPassword", "Admin@123");
  await page.click("#loginSubmit");
  await expect(page).toHaveURL(/dashboard\.html#overview/);

  await page.goto("/dashboard.html#docs");
  await page.locator('[data-action="create-doc"][data-node-id="folder-ui"]').click();

  await expect(page).toHaveURL(/dashboard\.html#doc-view\?docId=/);
  await expect(page.locator("#docEditTitle")).toBeVisible();
  await expect(page.locator("#docEditorSurface")).toBeVisible();

  // 等待 TOAST UI Editor 加载完成
  await page.waitForFunction(() => {
    const editor = document.querySelector('#docEditorSurface .ProseMirror');
    return editor && window.getComputedStyle(editor).display !== 'none' && window.getComputedStyle(editor).visibility !== 'hidden';
  }, { timeout: 5000 });

  await page.fill("#docEditTitle", "UI 规范新文档");
  await page.locator("#docEditorSurface").click();
  await page.keyboard.type("新建文档内容\n\n已完成保存验证。");
  await page.getByRole("button", { name: "保存" }).click();
  await expect(page.locator("#docSaveStatus")).toContainText("已保存");

  await page.getByRole("button", { name: "取消编辑" }).click();
  // 使用导航栏返回列表
  await page.goto("/dashboard.html#docs");
  await expect(page.locator(".docs-list-card h3").filter({ hasText: "UI 规范新文档" }).first()).toBeVisible();
});
