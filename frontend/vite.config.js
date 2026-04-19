import { defineConfig } from "vite";

export default defineConfig({
  optimizeDeps: {
    force: true,
    include: [
      "@toast-ui/editor",
      "marked",
      "dompurify",
      "turndown",
      "turndown-plugin-gfm"
    ]
  },
  build: {
    rollupOptions: {
      input: {
        index: "index.html",
        login: "login.html",
        register: "register.html",
        dashboard: "dashboard.html"
      }
    }
  }
});
