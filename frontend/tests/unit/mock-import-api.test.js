import { beforeEach, describe, expect, it } from "vitest";
import { clearDb } from "../../src/js/services/mock/mock-db.js";
import {
  cancelImportTask,
  createImportTask,
  getImportTaskStatus,
  getRecentImports
} from "../../src/js/services/mock/mock-api.js";

describe("mock import api", () => {
  beforeEach(() => {
    clearDb();
  });

  it("should create import task", async () => {
    const file = new File(["spec"], "spec.md", { type: "text/markdown" });
    const createResp = await createImportTask({
      sourceType: "upload",
      file,
      fileMeta: { name: "spec.md", size: 1000, type: "text/markdown" },
      sensitivity: "INTERNAL",
      tags: ["技术文档"],
      versionLabel: "v1.0.0",
      chunkConfig: { size: 500, overlap: 100, structuredChunk: true }
    });

    expect(createResp.ok).toBe(true);
    expect(createResp.data.taskId).toContain("TSK-");
    expect(createResp.data.status).toBe("PENDING");
  });

  it("should query and cancel task", async () => {
    const file = new File(["spec"], "spec.md", { type: "text/markdown" });
    const createResp = await createImportTask({
      sourceType: "upload",
      file,
      fileMeta: { name: "spec.md", size: 1000, type: "text/markdown" },
      sensitivity: "PUBLIC",
      tags: ["官网"],
      versionLabel: "v2.0.0",
      chunkConfig: { size: 600, overlap: 100, structuredChunk: true }
    });

    const taskId = createResp.data.taskId;
    const statusResp = await getImportTaskStatus(taskId);
    expect(statusResp.ok).toBe(true);

    const cancelResp = await cancelImportTask(taskId);
    expect(cancelResp.ok).toBe(true);
    expect(cancelResp.data.status).toBe("CANCELLED");
  });

  it("should list recent imports", async () => {
    const file = new File(["archive"], "a.docx", {
      type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    });
    await createImportTask({
      sourceType: "upload",
      file,
      fileMeta: { name: "a.docx", size: 1200, type: file.type },
      sensitivity: "CONFIDENTIAL",
      tags: ["归档"],
      versionLabel: "v1.3.0",
      chunkConfig: { size: 500, overlap: 120, structuredChunk: true }
    });
    const listResp = await getRecentImports();
    expect(listResp.ok).toBe(true);
    expect(listResp.data.length).toBeGreaterThan(0);
  });

  it("should reject unsupported source types", async () => {
    const createResp = await createImportTask({
      sourceType: "url"
    });

    expect(createResp.ok).toBe(false);
    expect(createResp.code).toBe("INVALID_PARAMETER");
  });
});
