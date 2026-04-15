import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("../../src/js/config/env.js", () => ({
  isMockMode: vi.fn()
}));

vi.mock("../../src/js/services/http-client.js", () => ({
  request: vi.fn()
}));

vi.mock("../../src/js/services/mock/mock-api.js", () => ({
  getRecentImports: vi.fn(),
  createImportTask: vi.fn(),
  getImportTaskStatus: vi.fn(),
  cancelImportTask: vi.fn()
}));

import { isMockMode } from "../../src/js/config/env.js";
import { request } from "../../src/js/services/http-client.js";
import * as MockApi from "../../src/js/services/mock/mock-api.js";
import { ImportService } from "../../src/js/services/import-service.js";

describe("import service", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("should normalize backend recent imports payload", async () => {
    isMockMode.mockReturnValue(false);
    request.mockResolvedValue({
      ok: true,
      data: {
        tasks: [
          {
            taskId: "task-1",
            filename: "需求文档.md",
            fileType: "MD",
            progress: 80,
            status: "COMPLETED"
          }
        ],
        total: 1,
        page: 0,
        size: 10
      }
    });

    const response = await ImportService.getRecentImports();

    expect(response.ok).toBe(true);
    expect(response.data).toEqual([
      {
        id: "task-1",
        name: "需求文档.md",
        type: "MD",
        progress: 80,
        status: "COMPLETED",
        statusLabel: "导入完成"
      }
    ]);
  });

  it("should keep mock recent imports payload as array", async () => {
    isMockMode.mockReturnValue(true);
    MockApi.getRecentImports.mockResolvedValue({
      ok: true,
      data: [
        {
          taskId: "TSK-00001",
          filename: "本地导入任务",
          fileType: "PDF",
          progress: 25,
          status: "PENDING"
        }
      ]
    });

    const response = await ImportService.getRecentImports();

    expect(response.ok).toBe(true);
    expect(response.data).toEqual([
      {
        id: "TSK-00001",
        name: "本地导入任务",
        type: "PDF",
        progress: 25,
        status: "PENDING",
        statusLabel: "任务排队中"
      }
    ]);
  });

  it("should build form data and normalize create task response", async () => {
    isMockMode.mockReturnValue(false);
    request.mockResolvedValue({
      ok: true,
      data: {
        taskId: "task-2",
        status: "PENDING",
        progress: 0
      }
    });

    const file = new File(["doc"], "spec.md", { type: "text/markdown" });
    const response = await ImportService.createImportTask({
      sourceType: "upload",
      file,
      sensitivity: "INTERNAL",
      versionLabel: "v1.0.0",
      tags: ["技术文档"],
      chunkConfig: { size: 500, overlap: 100, structuredChunk: true }
    });

    expect(response.ok).toBe(true);
    expect(response.data.status).toBe("PENDING");
    expect(response.data.statusLabel).toBe("任务排队中");
    expect(request).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        method: "POST",
        data: expect.any(FormData)
      })
    );
  });
});
