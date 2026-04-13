import { describe, expect, it } from "vitest";
import {
  createDefaultDocumentState,
  getDocumentRouteModeFromHash,
  renderDocumentRoute
} from "../../src/js/pages/document-center.js";

describe("document center route helpers", () => {
  it("should parse edit mode from hash", () => {
    expect(getDocumentRouteModeFromHash("#doc-view?docId=doc-generated-00001&mode=edit")).toBe("edit");
    expect(getDocumentRouteModeFromHash("#doc-view?docId=doc-ui-token")).toBe("");
    expect(getDocumentRouteModeFromHash("")).toBe("");
  });

  it("should render unified outline label and placeholders in edit mode", () => {
    const state = createDefaultDocumentState();
    state.currentDocId = "doc-ui-token";
    state.currentDoc = {
      docId: "doc-ui-token",
      title: "",
      dept: "产品设计部",
      visibility: "公开",
      scope: "团队可见",
      status: "已发布",
      updatedBy: "当前用户",
      updatedAt: "刚刚",
      folderPathIds: ["folder-kb", "folder-ui"],
      folderPathNames: ["知识库", "UI 规范"],
      contentMarkdown: ""
    };
    state.detailMode = "edit";
    state.editTitle = "";

    const html = renderDocumentRoute("doc-view", state, "");
    expect(html).toContain('placeholder="请输入标题"');
    expect(html).toContain('data-placeholder="请输入正文"');
    expect(html).toContain("TOAST UI Editor");
    expect(html).toContain("使用编辑器原生工具栏处理标题、表格、图片、链接和代码块");
    expect(html).toContain(">大纲<");
    expect(html).not.toContain("实时大纲");
    expect(html).not.toContain("本文大纲");
    expect(html).not.toContain('id="docImageUploadInput"');
    expect(html).not.toContain('id="docLinkHrefInput"');
  });
});
