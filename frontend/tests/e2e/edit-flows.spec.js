import { expect, test } from "@playwright/test";

async function login(page) {
  await page.goto("/login.html");
  await page.fill("#loginIdentity", "admin@system.com");
  await page.fill("#loginPassword", "Admin@123");
  await page.click("#loginSubmit");
  await expect(page).toHaveURL(/dashboard\.html#overview/);
}

test("文档详情页点击编辑文档后原地进入编辑态并保存", async ({ page }) => {
  await login(page);

  await page.goto("/dashboard.html#docs");
  await page.getByRole("button", { name: "查看详情" }).first().click();
  await expect(page).toHaveURL(/dashboard\.html#doc-view\?docId=/);
  await expect(page.locator("#docsOutlineDrawer")).toHaveAttribute("aria-hidden", "true");

  await page.locator("#docsOutlineTrigger").hover({ force: true });
  await expect(page.locator("#docsOutlineDrawer")).toHaveAttribute("aria-hidden", "false");
  await page.locator(".docs-article-shell").click({ force: true });
  await expect(page.locator("#docsOutlineDrawer")).toHaveAttribute("aria-hidden", "true");

  const beforeUrl = page.url();
  await page.getByRole("button", { name: "编辑文档" }).click();
  await expect(page.locator("#docEditTitle")).toBeVisible();
  await expect(page.locator("#docEditorSurface")).toBeVisible();
  await expect(page).toHaveURL(beforeUrl);
  await expect(page.locator("#docEditTitle")).toHaveAttribute("placeholder", "请输入标题");
  await expect(page.locator("#docEditorSurface")).toHaveAttribute("data-placeholder", "请输入正文");

  await page.fill("#docEditTitle", "Azure Logic 架构核心规范 v2.4 - 内联编辑");
  await page.locator("#docEditorSurface").click();
  await page.keyboard.press("ControlOrMeta+a");
  await page.keyboard.type("内联编辑正文\n\n第一段内容已更新。");
  await page.locator("#docsOutlineTrigger").click({ force: true });
  await expect(page.locator("#docsOutlineDrawer")).toHaveAttribute("aria-hidden", "false");
  await page.locator(".docs-toolbar").click();
  await expect(page.locator("#docsOutlineDrawer")).toHaveAttribute("aria-hidden", "true");
  await page.getByRole("button", { name: "保存" }).click();

  await expect(page.locator("#docSaveStatus")).toContainText("已保存");
  await expect(page.locator("#docEditTitle")).toHaveValue("Azure Logic 架构核心规范 v2.4 - 内联编辑");

  await page.getByRole("button", { name: "取消编辑" }).click();
  await expect(page.locator("#docEditTitle")).toHaveCount(0);
  // 等待页面重新渲染
  await page.waitForTimeout(500);
  await expect(page.locator(".docs-article-title")).toContainText("Azure Logic 架构核心规范 v2.4 - 内联编辑");
  await expect(page.getByText("第一段内容已更新。")).toBeVisible();
});

test("文档详情页自动保存触发后仍可继续输入", async ({ page }) => {
  test.setTimeout(45000);

  await login(page);

  await page.goto("/dashboard.html#docs");
  await page.getByRole("button", { name: "查看详情" }).first().click();
  await page.getByRole("button", { name: "编辑文档" }).click();

  // 等待 TOAST UI Editor 加载完成
  await page.waitForFunction(() => {
    const editor = document.querySelector('#docEditorSurface .ProseMirror');
    return editor && window.getComputedStyle(editor).display !== 'none' && window.getComputedStyle(editor).visibility !== 'hidden';
  }, { timeout: 5000 });

  await page.locator("#docEditorSurface").click();
  await page.keyboard.press("ControlOrMeta+a");
  await page.keyboard.type("自动保存验证\n\n等待 30 秒触发自动保存。");
  await page.waitForTimeout(31000);

  await expect(page.locator("#docSaveStatus")).toContainText("已保存");
  await page.locator("#docEditorSurface").press("End");
  await page.locator("#docEditorSurface").type("\n自动保存后仍可继续输入。");
  await expect(page.locator("#docEditorSurface")).toContainText("自动保存后仍可继续输入。");
});

test("文档详情页支持富文本工具栏、气泡菜单、图片与表格编辑", async ({ page }) => {
  await login(page);

  await page.goto("/dashboard.html#docs");
  await page.locator('[data-action="create-doc"][data-node-id="folder-ui"]').click();
  await expect(page.locator("#docEditorSurface")).toBeVisible();

  // 等待 TOAST UI Editor 加载完成
  await page.waitForFunction(() => {
    const editor = document.querySelector('#docEditorSurface .ProseMirror');
    return editor && window.getComputedStyle(editor).display !== 'none' && window.getComputedStyle(editor).visibility !== 'hidden';
  }, { timeout: 5000 });

  await page.fill("#docEditTitle", "富文本能力验收文档");
  await page.locator("#docEditorSurface").click();
  await page.keyboard.type("Rich text body");
  await page.keyboard.press("ControlOrMeta+a");
  // TOAST UI Editor heading button - click heading button then select H2 from dropdown
  await page.locator(".toastui-editor-toolbar .toolbar-heading").click();
  await page.locator(".toastui-editor-popup-heading ul li").filter({ hasText: "H2" }).click();

  await page.keyboard.press("End");
  await page.keyboard.press("Enter");
  await page.keyboard.type("bubble");
  await page.keyboard.press("Shift+ArrowLeft");
  await page.keyboard.press("Shift+ArrowLeft");
  await page.keyboard.press("Shift+ArrowLeft");
  await page.keyboard.press("Shift+ArrowLeft");
  await page.keyboard.press("Shift+ArrowLeft");
  await expect(page.locator("#docsBubbleMenu")).toBeVisible();
  await page.locator('#docsBubbleMenu [data-action="editor-bold"]').click();

  await page.getByRole("button", { name: "图片 URL" }).click();
  await page.fill("#docImageUrlInput", "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=800&q=80");
  await page.fill("#docImageAltInput", "芯片图");
  await page.getByRole("button", { name: "应用" }).click();

  await page.getByRole("button", { name: "表格" }).click();
  await page.locator("#docEditorSurface td").first().click();
  await page.keyboard.type("单元格");

  await page.getByRole("button", { name: "保存" }).click();
  await expect(page.locator("#docSaveStatus")).toContainText("已保存");

  await page.getByRole("button", { name: "取消编辑" }).click();
  await expect(page.locator("#docsMarkdownBody h2")).toContainText("Rich text body");
  await expect(page.locator("#docsMarkdownBody strong")).toContainText("bubble");
  await expect(page.locator('#docsMarkdownBody img[alt="芯片图"]')).toBeVisible();
  await expect(page.locator("#docsMarkdownBody table")).toBeVisible();
  await expect(page.locator("#docsMarkdownBody td")).toContainText("单元格");
});
