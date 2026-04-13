import { initDashboardPage } from "./pages/dashboard-page.js";

window.addEventListener("DOMContentLoaded", () => {
  initDashboardPage().catch((error) => {
    console.error("Dashboard init failed:", error);
    const content = document.querySelector("#dashboardContent");
    if (content instanceof HTMLElement) {
      content.innerHTML = `
        <section class="panel-card panel-card--single">
          <h2>页面初始化失败</h2>
          <p>${String(error?.message || "请查看控制台错误并重试")}</p>
        </section>
      `;
    }
  });
});
