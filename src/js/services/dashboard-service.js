import { API_ENDPOINTS } from "../config/api-endpoints.js";
import { isMockMode } from "../config/env.js";
import { request } from "./http-client.js";
import * as MockApi from "./mock/mock-api.js";

export const DashboardService = {
  async getMetrics() {
    if (isMockMode()) {
      return MockApi.getMetrics();
    }
    return request(API_ENDPOINTS.dashboard.metrics, { method: "GET" });
  },

  async getRecentImports() {
    if (isMockMode()) {
      return MockApi.getRecentImports();
    }
    return request(API_ENDPOINTS.dashboard.recentImports, { method: "GET" });
  },

  async getAlerts() {
    if (isMockMode()) {
      return MockApi.getAlerts();
    }
    return request(API_ENDPOINTS.dashboard.alerts, { method: "GET" });
  }
};
