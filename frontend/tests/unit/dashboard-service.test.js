import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("../../src/js/config/env.js", () => ({
  isMockMode: vi.fn()
}));

vi.mock("../../src/js/services/http-client.js", () => ({
  request: vi.fn()
}));

vi.mock("../../src/js/services/mock/mock-api.js", () => ({
  getMetrics: vi.fn(),
  getAlerts: vi.fn()
}));

import { isMockMode } from "../../src/js/config/env.js";
import { request } from "../../src/js/services/http-client.js";
import * as MockApi from "../../src/js/services/mock/mock-api.js";
import { DashboardService } from "../../src/js/services/dashboard-service.js";

describe("dashboard service", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("should normalize backend metrics payload", async () => {
    isMockMode.mockReturnValue(false);
    request.mockResolvedValueOnce({
      ok: true,
      data: {
        documents: { totalChunks: 3200 },
        imports: { successRate: 98.2, processingTasks: 3, failedTasks: 1 },
        storage: { usedSpace: 842901 },
        system: { cpuUsage: 12.5, memoryUsage: 45 }
      }
    });

    const response = await DashboardService.getMetrics();

    expect(response.ok).toBe(true);
    expect(response.data).toEqual({
      tokenToday: 842901,
      tokenGrowthPercent: 98.2,
      vectorDbStatus: "3,200 chunks",
      vectorDbHealth: "NORMAL",
      llmAvailability: "99%",
      llmStability: "稳定",
      processingTasks: 3
    });
  });

  it("should normalize backend alerts payload", async () => {
    isMockMode.mockReturnValue(false);
    request.mockResolvedValueOnce({
      ok: true,
      data: {
        alerts: [
          {
            id: "alert-1",
            type: "WARNING",
            title: "导入失败率上升",
            message: "最近一小时失败任务偏多",
            timestamp: "2026-03-31T17:40:00+08:00"
          }
        ]
      }
    });

    const response = await DashboardService.getAlerts();

    expect(response.ok).toBe(true);
    expect(response.data[0]).toMatchObject({
      id: "alert-1",
      level: "warning",
      title: "导入失败率上升",
      description: "最近一小时失败任务偏多",
      actions: []
    });
  });

  it("should keep mock metrics payload unchanged", async () => {
    isMockMode.mockReturnValue(true);
    MockApi.getMetrics.mockResolvedValue({
      ok: true,
      data: {
        tokenToday: 123,
        tokenGrowthPercent: 1.2,
        vectorDbStatus: "健康状态",
        vectorDbHealth: "NORMAL",
        llmAvailability: "100%",
        llmStability: "稳定",
        processingTasks: 5
      }
    });

    const response = await DashboardService.getMetrics();

    expect(response.data.tokenToday).toBe(123);
    expect(response.data.processingTasks).toBe(5);
  });
});
