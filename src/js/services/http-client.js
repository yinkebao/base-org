import { API_BASE_URL } from "../config/env.js";

function buildHeaders(extraHeaders = {}) {
  return {
    "Content-Type": "application/json",
    ...extraHeaders
  };
}

function resolveUrl(path) {
  if (path.startsWith("http://") || path.startsWith("https://")) {
    return path;
  }
  return `${API_BASE_URL}${path}`;
}

function createTraceId() {
  return `trace_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}

function mapFetchError(error) {
  if (error.name === "AbortError") {
    return {
      ok: false,
      code: "REQUEST_TIMEOUT",
      message: "请求超时，请稍后重试"
    };
  }
  return {
    ok: false,
    code: "NETWORK_ERROR",
    message: "网络异常，请检查连接"
  };
}

export async function request(path, options = {}) {
  const {
    method = "GET",
    data,
    headers = {},
    timeout = 10000
  } = options;
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeout);
  const traceId = createTraceId();

  try {
    const response = await fetch(resolveUrl(path), {
      method,
      headers: buildHeaders(headers),
      body: data ? JSON.stringify(data) : undefined,
      signal: controller.signal
    });
    clearTimeout(timeoutId);

    const responseData = await response.json().catch(() => ({}));
    if (!response.ok) {
      return {
        ok: false,
        code: responseData.code || `HTTP_${response.status}`,
        message: responseData.message || "请求失败",
        details: responseData.details,
        traceId
      };
    }

    return {
      ok: true,
      data: responseData.data ?? responseData,
      traceId
    };
  } catch (error) {
    clearTimeout(timeoutId);
    return {
      ...mapFetchError(error),
      traceId
    };
  }
}
