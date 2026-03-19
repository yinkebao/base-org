const DEFAULT_API_MODE = "mock";
const DEFAULT_API_BASE_URL = "";

export const API_MODE = (import.meta.env.VITE_API_MODE || DEFAULT_API_MODE).toLowerCase();
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL;

export function isMockMode() {
  return API_MODE !== "real";
}
