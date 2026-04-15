export const API_ENDPOINTS = {
  auth: {
    // 认证接口 - 后端已实现
    login: "/api/v1/auth/login",           // POST
    register: "/api/v1/auth/register",     // POST
    checkUsername: "/api/v1/auth/check-username", // GET
    // SSO 接口 - 待后端实现
    ssoStart: "/api/v1/auth/sso/start"
  },
  imports: {
    createTask: "/api/v1/documents/import",
    taskStatusBase: "/api/v1/documents/import",
    cancelSuffix: "/cancel",
    recent: "/api/v1/documents/import/recent"
  },
  documents: {
    tree: "/api/v1/documents/tree",
    list: "/api/v1/documents",
    detailBase: "/api/v1/documents",
    breadcrumbBase: "/api/v1/documents",
    imageUploadSuffix: "/assets/images"
  },
  dashboard: {
    metrics: "/api/v1/admin/metrics",
    recentImports: "/api/v1/documents/import/recent",
    alerts: "/api/v1/admin/alerts"
  }
};
