export const API_ENDPOINTS = {
  auth: {
    login: "/api/v1/auth/login",
    register: "/api/v1/auth/register",
    checkUsername: "/api/v1/auth/check-username",
    ssoStart: "/api/v1/auth/sso/start"
  },
  dashboard: {
    metrics: "/api/v1/admin/metrics",
    recentImports: "/api/v1/documents/import/recent",
    alerts: "/api/v1/admin/alerts"
  }
};
