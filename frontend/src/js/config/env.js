const DEFAULT_API_MODE = "mock";
const DEFAULT_API_BASE_URL = "";

const ENV = typeof import.meta !== "undefined" && import.meta.env ? import.meta.env : {};

export const API_MODE = String(ENV.VITE_API_MODE || DEFAULT_API_MODE).toLowerCase();
export const API_BASE_URL = ENV.VITE_API_BASE_URL || DEFAULT_API_BASE_URL;

export function isMockMode() {
  return API_MODE !== "real";
}
