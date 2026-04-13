import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../../src/js/services/session-service.js", () => ({
  getSession: vi.fn(),
  clearSession: vi.fn()
}));

const fetchMock = vi.fn();
const originalFetch = globalThis.fetch;

import { API_ENDPOINTS } from "../../src/js/config/api-endpoints.js";
import { clearSession, getSession } from "../../src/js/services/session-service.js";
import { request } from "../../src/js/services/http-client.js";

describe("http client", () => {
  beforeAll(() => {
    globalThis.fetch = fetchMock;
  });
  afterAll(() => {
    globalThis.fetch = originalFetch;
    vi.resetModules();
  });

  beforeEach(() => {
    fetchMock.mockReset();
    clearSession.mockReset();
    getSession.mockReset();
    getSession.mockReturnValue({ token: "mock-token" });
  });

  const createResponse = ({ ok, status, data }) =>
    Promise.resolve({
      ok,
      status,
      json: vi.fn().mockResolvedValue(data)
    });

  it("should clear session and return API error when response is 401", async () => {
    fetchMock.mockReturnValue(
      createResponse({
        ok: false,
        status: 401,
        data: {
          code: "AUTH_EXPIRED",
          message: "令牌已过期"
        }
      })
    );

    const result = await request("/api/v1/protected", { method: "GET" });

    expect(result.ok).toBe(false);
    expect(result.code).toBe("AUTH_EXPIRED");
    expect(result.message).toBe("令牌已过期");
    expect(clearSession).toHaveBeenCalledOnce();
  });

  it("should send GET request to document tree without body", async () => {
    fetchMock.mockReturnValue(
      createResponse({
        ok: true,
        status: 200,
        data: {
          data: { nodes: [] }
        }
      })
    );

    const response = await request(API_ENDPOINTS.documents.tree, { method: "GET" });

    expect(response.ok).toBe(true);
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining(API_ENDPOINTS.documents.tree),
      expect.objectContaining({
        method: "GET",
        body: undefined
      })
    );
  });

  it("should send GET request to document list without data payload", async () => {
    fetchMock.mockReturnValue(
      createResponse({
        ok: true,
        status: 200,
        data: {
          data: { items: [] }
        }
      })
    );

    await request(API_ENDPOINTS.documents.list, { method: "GET" });

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining(API_ENDPOINTS.documents.list),
      expect.objectContaining({
        method: "GET",
        body: undefined
      })
    );
  });
});

globalThis.fetch = originalFetch;
