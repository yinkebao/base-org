import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../../src/js/config/env.js", () => ({
  API_MODE: "mock",
  API_BASE_URL: "",
  isMockMode: () => true
}));

import { DocumentService } from "../../src/js/services/document-service.js";
import { clearDb } from "../../src/js/services/mock/mock-db.js";

describe("document service", () => {
  beforeEach(() => {
    clearDb();
  });

  it("should return document tree and departments", async () => {
    const resp = await DocumentService.getDocumentTree();
    expect(resp.ok).toBe(true);
    expect(resp.data.departments.includes("全部部门")).toBe(true);
    expect(resp.data.nodes.length).toBeGreaterThan(0);
  });

  it("should filter document list by dept and folder", async () => {
    const resp = await DocumentService.getDocumentList({
      dept: "产品设计部",
      folderId: "folder-ui"
    });
    expect(resp.ok).toBe(true);
    expect(resp.data.length).toBeGreaterThanOrEqual(2);
    expect(resp.data.some((item) => item.docId === "doc-ui-token")).toBe(true);
  });

  it("should return detail and breadcrumb", async () => {
    const detailResp = await DocumentService.getDocumentDetail("doc-arch-v24");
    const breadcrumbResp = await DocumentService.getDocumentBreadcrumb("doc-arch-v24");

    expect(detailResp.ok).toBe(true);
    expect(detailResp.data.title).toContain("架构核心规范");
    expect(breadcrumbResp.ok).toBe(true);
    expect(breadcrumbResp.data.at(-1).type).toBe("doc");
  });

  it("should return not found for invalid doc id", async () => {
    const resp = await DocumentService.getDocumentDetail("doc-not-exist");
    expect(resp.ok).toBe(false);
    expect(resp.code).toBe("DOC_NOT_FOUND");
  });

  it("should update document content and title in place", async () => {
    const updateResp = await DocumentService.updateDocument("doc-ui-token", {
      title: "Design Token 指南（内联编辑）",
      contentMarkdown: "# Design Token 指南（内联编辑）\n\n## 新段落\n\n内容已持久化。"
    });

    expect(updateResp.ok).toBe(true);
    expect(updateResp.data.title).toBe("Design Token 指南（内联编辑）");
    expect(updateResp.data.contentMarkdown).toContain("内容已持久化");

    const detailResp = await DocumentService.getDocumentDetail("doc-ui-token");
    expect(detailResp.ok).toBe(true);
    expect(detailResp.data.title).toBe("Design Token 指南（内联编辑）");
    expect(detailResp.data.contentMarkdown).toContain("内容已持久化");

    const treeResp = await DocumentService.getDocumentTree();
    const treeNode = treeResp.data.nodes.find((node) => node.docId === "doc-ui-token");
    expect(treeNode?.name).toBe("Design Token 指南（内联编辑）");
  });

  it("should create a document under target folder and inherit metadata", async () => {
    const createResp = await DocumentService.createDocument("folder-ui", {
      deptFilter: "全部部门"
    });

    expect(createResp.ok).toBe(true);
    expect(createResp.data.docId).toContain("doc-generated-");
    expect(createResp.data.title).toBe("未命名文档");
    expect(createResp.data.dept).toBe("产品设计部");
    expect(createResp.data.scope).toBe("团队可见");
    expect(createResp.data.visibility).toBe("公开");
    expect(createResp.data.folderPathIds).toEqual(["folder-kb", "folder-ui"]);

    const detailResp = await DocumentService.getDocumentDetail(createResp.data.docId);
    expect(detailResp.ok).toBe(true);
    expect(detailResp.data.contentMarkdown).toBe("");

    const treeResp = await DocumentService.getDocumentTree();
    const treeNode = treeResp.data.nodes.find((node) => node.docId === createResp.data.docId);
    expect(treeNode?.parentId).toBe("folder-ui");
  });

  it("should upload document image in mock mode", async () => {
    const file = new File([Uint8Array.from([137, 80, 78, 71])], "demo.png", { type: "image/png" });
    const resp = await DocumentService.uploadDocumentImage("doc-ui-token", file, "演示图片");

    expect(resp.ok).toBe(true);
    expect(resp.data.alt).toBe("演示图片");
    expect(resp.data.filename).toBe("demo.png");
    expect(resp.data.url.startsWith("data:image/png;base64,")).toBe(true);
  });
});

async function loadDocumentService(requestMock) {
  vi.resetModules();
  vi.doMock("../../src/js/config/env.js", async () => {
    const actual = await vi.importActual("../../src/js/config/env.js");
    return {
      ...actual,
      API_MODE: "real",
      isMockMode: () => false
    };
  });
  vi.doMock("../../src/js/services/http-client.js", () => ({
    request: requestMock
  }));
  const { DocumentService: RealDocumentService } = await import("../../src/js/services/document-service.js");
  return RealDocumentService;
}

describe("document service real requests", () => {
  afterEach(() => {
    vi.resetModules();
  });

  it("calls GET for document tree with dept query", async () => {
    const requestMock = vi.fn().mockResolvedValue({ ok: true, data: {} });
    const RealDocumentService = await loadDocumentService(requestMock);

    await RealDocumentService.getDocumentTree("产品设计部");

    expect(requestMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/documents/tree?dept="),
      expect.objectContaining({ method: "GET" })
    );
  });

  it("calls GET for document list with filters", async () => {
    const requestMock = vi.fn().mockResolvedValue({ ok: true, data: {} });
    const RealDocumentService = await loadDocumentService(requestMock);

    await RealDocumentService.getDocumentList({ dept: "平台组", folderId: "folder-ui" });

    expect(requestMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/documents?dept=%E5%B9%B3%E5%8F%B0%E7%BB%84&folderId=folder-ui"),
      expect.objectContaining({ method: "GET" })
    );
  });

  it("propagates ApiResponse code/message when request fails", async () => {
    const requestMock = vi.fn().mockResolvedValue({
      ok: false,
      code: "DOC_DENIED",
      message: "权限不足"
    });
    const RealDocumentService = await loadDocumentService(requestMock);

    const resp = await RealDocumentService.getDocumentTree();

    expect(resp.ok).toBe(false);
    expect(resp.code).toBe("DOC_DENIED");
    expect(resp.message).toBe("权限不足");
  });
});
