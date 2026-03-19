import { API_ENDPOINTS } from "../config/api-endpoints.js";
import { isMockMode } from "../config/env.js";
import { request } from "./http-client.js";
import * as MockApi from "./mock/mock-api.js";

export const AuthService = {
  async login(payload) {
    if (isMockMode()) {
      return MockApi.login(payload);
    }
    return request(API_ENDPOINTS.auth.login, {
      method: "POST",
      data: payload
    });
  },

  async register(payload) {
    if (isMockMode()) {
      return MockApi.register(payload);
    }
    return request(API_ENDPOINTS.auth.register, {
      method: "POST",
      data: payload
    });
  },

  async checkUsername(payload) {
    if (isMockMode()) {
      return MockApi.checkUsername(payload);
    }
    return request(API_ENDPOINTS.auth.checkUsername, {
      method: "POST",
      data: payload
    });
  },

  async startSso() {
    if (isMockMode()) {
      return MockApi.startSso();
    }
    return request(API_ENDPOINTS.auth.ssoStart, {
      method: "GET"
    });
  }
};
