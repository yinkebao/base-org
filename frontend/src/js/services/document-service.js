import { API_ENDPOINTS } from "../config/api-endpoints.js";
import { isMockMode } from "../config/env.js";
import { request } from "./http-client.js";
import * as MockApi from "./mock/mock-api.js";

function buildQuery(params = {}) {
  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null) return;
    if (Array.isArray(value)) {
      value.forEach((item) => searchParams.append(key, String(item)));
      return;
    }
    searchParams.append(key, String(value));
  });
  const query = searchParams.toString();
  return query ? `?${query}` : "";
}

export const DocumentService = {
  async getDocumentTree(dept = "") {
    if (isMockMode()) {
      return MockApi.getDocumentTree(dept);
    }
    const query = buildQuery({ dept });
    return request(`${API_ENDPOINTS.documents.tree}${query}`, {
      method: "GET"
    });
  },

  async getDocumentList(params = {}) {
    if (isMockMode()) {
      return MockApi.getDocumentList(params);
    }
    const queryString = buildQuery(params);
    return request(`${API_ENDPOINTS.documents.list}${queryString}`, {
      method: "GET"
    });
  },

  async getDocumentDetail(docId) {
    if (isMockMode()) {
      return MockApi.getDocumentDetail(docId);
    }
    return request(`${API_ENDPOINTS.documents.detailBase}/${docId}`, {
      method: "GET"
    });
  },

  async getDocumentBreadcrumb(docId) {
    if (isMockMode()) {
      return MockApi.getDocumentBreadcrumb(docId);
    }
    return request(`${API_ENDPOINTS.documents.breadcrumbBase}/${docId}/breadcrumb`, {
      method: "GET"
    });
  },

  async createDocument(folderId, payload = {}) {
    if (isMockMode()) {
      return MockApi.createDocument(folderId, payload);
    }
    return request(API_ENDPOINTS.documents.list, {
      method: "POST",
      data: {
        folderId,
        ...payload
      }
    });
  },

  async updateDocument(docId, payload) {
    if (isMockMode()) {
      return MockApi.updateDocument(docId, payload);
    }
    return request(`${API_ENDPOINTS.documents.detailBase}/${docId}`, {
      method: "PUT",
      data: payload
    });
  },

  async uploadDocumentImage(docId, file, alt = "") {
    if (isMockMode()) {
      return MockApi.uploadDocumentImage(docId, file, alt);
    }

    const formData = new FormData();
    formData.append("image", file);
    formData.append("alt", alt);

    return request(`${API_ENDPOINTS.documents.detailBase}/${docId}${API_ENDPOINTS.documents.imageUploadSuffix}`, {
      method: "POST",
      data: formData
    });
  }
};
