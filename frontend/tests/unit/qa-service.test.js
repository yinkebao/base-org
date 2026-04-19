import { afterEach, describe, expect, it, vi } from "vitest";

async function loadQAService(requestMock) {
  vi.resetModules();
  vi.doMock("../../src/js/config/env.js", () => ({
    API_MODE: "real",
    API_BASE_URL: ""
  }));
  vi.doMock("../../src/js/services/http-client.js", () => ({
    request: requestMock
  }));
  const { QAService } = await import("../../src/js/services/qa-service.js");
  return QAService;
}

describe("QA service", () => {
  afterEach(() => {
    vi.resetModules();
  });

  it("sends standardized payload to /api/v1/qa", async () => {
    const requestMock = vi.fn().mockResolvedValue({
      ok: true,
      data: {
        sources: []
      }
    });
    const QAService = await loadQAService(requestMock);

    await QAService.query({ question: "如何导入文档" });

    expect(requestMock).toHaveBeenCalledOnce();
    expect(requestMock).toHaveBeenCalledWith("/api/v1/qa", expect.objectContaining({
      method: "POST",
      timeout: 30000,
      data: expect.objectContaining({
        question: "如何导入文档",
        topK: 5,
        includeSources: true,
        scoreThreshold: 0.7
      })
    }));
  });

  it("maps sources with chunkId/docId/docTitle/content/score/metadata", async () => {
    const requestMock = vi.fn().mockResolvedValue({
      ok: true,
      data: {
        sources: [
          {
            chunkId: 101,
            docId: "doc-arch",
            docTitle: "Architecture Spec",
            content: "导入后自动触发向量化",
            score: 0.88
          }
        ]
      }
    });
    const QAService = await loadQAService(requestMock);

    const result = await QAService.query({ question: "导入流程" });

    expect(result.ok).toBe(true);
    const source = result.data?.sources?.[0];
    expect(source).toBeDefined();
    expect(source).toMatchObject({
      chunkId: "101",
      docId: "doc-arch",
      docTitle: "Architecture Spec",
      score: 0.88,
      metadata: {
        dept: "技术研发部",
        sensitivity: "internal"
      }
    });
    expect(source.content).toBe("导入后自动触发向量化");
  });
});
