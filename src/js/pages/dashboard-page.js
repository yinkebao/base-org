import { DashboardService } from "../services/dashboard-service.js";
import { clearSession, getSession, isAuthenticated } from "../services/session-service.js";
import { escapeHtml } from "../utils/dom.js";

const ROUTES = ["overview", "imports", "alerts", "requirements", "audit", "settings"];

function getCurrentRoute() {
  const hash = window.location.hash.replace("#", "");
  const route = hash.split("?")[0] || "overview";
  return ROUTES.includes(route) ? route : "overview";
}

function statusClass(status) {
  if (status.includes("异常")) return "status-chip status-chip--error";
  if (status.includes("处理")) return "status-chip status-chip--processing";
  return "status-chip status-chip--ok";
}

function levelClass(level) {
  if (level === "critical") return "alert-card alert-card--critical";
  if (level === "warning") return "alert-card alert-card--warning";
  return "alert-card alert-card--info";
}

function renderMetricCards(metrics) {
  return `
    <section class="metric-grid">
      <article class="metric-card">
        <p class="metric-card__label">Token 今日消耗</p>
        <h2>${escapeHtml(metrics.tokenToday.toLocaleString())}</h2>
        <span class="status-pill status-pill--ok">+${escapeHtml(String(metrics.tokenGrowthPercent))}%</span>
      </article>
      <article class="metric-card">
        <p class="metric-card__label">向量 DB 状态</p>
        <h2>${escapeHtml(metrics.vectorDbStatus)}</h2>
        <span class="status-pill status-pill--ok">${escapeHtml(metrics.vectorDbHealth)}</span>
      </article>
      <article class="metric-card">
        <p class="metric-card__label">LLM 可用率</p>
        <h2>${escapeHtml(metrics.llmAvailability)}</h2>
        <span class="status-pill status-pill--neutral">${escapeHtml(metrics.llmStability)}</span>
      </article>
      <article class="metric-card">
        <p class="metric-card__label">待审批任务</p>
        <h2>${escapeHtml(String(metrics.pendingTasks))}</h2>
        <span class="status-pill status-pill--warn">需跟进</span>
      </article>
    </section>
  `;
}

function renderImportsTable(imports) {
  if (imports.length === 0) {
    return `<div class="empty-state">暂无导入任务数据</div>`;
  }
  return `
    <table class="data-table">
      <thead>
        <tr>
          <th>任务名称</th>
          <th>类型</th>
          <th>进度</th>
          <th>状态</th>
        </tr>
      </thead>
      <tbody>
        ${imports
          .map(
            (item) => `
              <tr>
                <td>
                  <strong>${escapeHtml(item.name)}</strong>
                  <small>ID: ${escapeHtml(item.id)}</small>
                </td>
                <td><span class="tag">${escapeHtml(item.type)}</span></td>
                <td>
                  <div class="progress">
                    <div class="progress__bar" style="width: ${escapeHtml(String(item.progress))}%"></div>
                  </div>
                  <span>${escapeHtml(String(item.progress))}%</span>
                </td>
                <td><span class="${statusClass(item.status)}">${escapeHtml(item.status)}</span></td>
              </tr>
            `
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function renderAlerts(alerts) {
  if (alerts.length === 0) {
    return `<div class="empty-state">暂无告警信息</div>`;
  }
  return `
    <section class="alert-stack">
      ${alerts
        .map(
          (alert) => `
            <article class="${levelClass(alert.level)}">
              <div>
                <h3>${escapeHtml(alert.title)}</h3>
                <p>${escapeHtml(alert.description)}</p>
              </div>
              <div class="alert-card__meta">
                <span>${escapeHtml(alert.ago)}</span>
                <div class="alert-card__actions">
                  ${alert.actions.map((action) => `<button type="button">${escapeHtml(action)}</button>`).join("")}
                </div>
              </div>
            </article>
          `
        )
        .join("")}
    </section>
  `;
}

function renderPlaceholder(title, description) {
  return `
    <section class="placeholder-card">
      <h2>${escapeHtml(title)}</h2>
      <p>${escapeHtml(description)}</p>
    </section>
  `;
}

function renderByRoute(route, data, query = "") {
  const q = query.trim().toLowerCase();
  const filteredImports = q
    ? data.imports.filter((item) => item.name.toLowerCase().includes(q) || item.id.toLowerCase().includes(q))
    : data.imports;
  const filteredAlerts = q
    ? data.alerts.filter((item) => item.title.toLowerCase().includes(q) || item.description.toLowerCase().includes(q))
    : data.alerts;

  if (route === "overview") {
    return `
      ${renderMetricCards(data.metrics)}
      <section class="dashboard-grid">
        <article class="panel-card">
          <header class="panel-card__header">
            <h2>最近导入任务</h2>
            <a href="#imports">查看全部</a>
          </header>
          ${renderImportsTable(filteredImports)}
        </article>
        <article class="panel-card">
          <header class="panel-card__header">
            <h2>实时告警</h2>
            <a href="#alerts">历史事件日志</a>
          </header>
          ${renderAlerts(filteredAlerts)}
        </article>
      </section>
    `;
  }

  if (route === "imports") {
    return `
      <article class="panel-card panel-card--single">
        <header class="panel-card__header">
          <h2>文档导入任务</h2>
          <button type="button" class="btn btn--secondary btn--small">新建导入</button>
        </header>
        ${renderImportsTable(filteredImports)}
      </article>
    `;
  }

  if (route === "alerts") {
    return `
      <article class="panel-card panel-card--single">
        <header class="panel-card__header">
          <h2>AI 告警中心</h2>
          <button type="button" class="btn btn--secondary btn--small">导出告警</button>
        </header>
        ${renderAlerts(filteredAlerts)}
      </article>
    `;
  }

  if (route === "requirements") {
    return renderPlaceholder("需求编排", "需求模板编排能力将在下一迭代开放，当前支持从仪表盘查看相关任务进度。");
  }

  if (route === "audit") {
    return renderPlaceholder("审计管理", "支持按用户、行为类型、时间范围检索审计日志，并导出合规报告。");
  }

  return renderPlaceholder("系统配置", "在此配置模型提供商、API Key、预算阈值和自动化告警策略。");
}

function renderLoading(container) {
  container.innerHTML = `
    <section class="panel-card panel-card--single">
      <div class="skeleton skeleton--title"></div>
      <div class="skeleton"></div>
      <div class="skeleton"></div>
      <div class="skeleton"></div>
    </section>
  `;
}

function markActiveRoute(route) {
  document.querySelectorAll(".dashboard-nav__item").forEach((item) => {
    item.classList.toggle("is-active", item.dataset.route === route);
  });
}

export async function initDashboardPage() {
  if (!isAuthenticated()) {
    window.location.href = "/login.html";
    return;
  }

  const content = document.querySelector("#dashboardContent");
  const searchInput = document.querySelector("#dashboardSearch");
  const userName = document.querySelector("#currentUserName");
  const userEmail = document.querySelector("#currentUserEmail");
  const logoutButton = document.querySelector("#logoutBtn");
  if (!content) return;

  const session = getSession();
  userName.textContent = session?.user?.username || "Admin User";
  userEmail.textContent = session?.user?.email || "admin@system.com";

  logoutButton?.addEventListener("click", () => {
    clearSession();
    window.location.href = "/login.html";
  });

  renderLoading(content);

  const [metricsResp, importsResp, alertsResp] = await Promise.all([
    DashboardService.getMetrics(),
    DashboardService.getRecentImports(),
    DashboardService.getAlerts()
  ]);

  if (!metricsResp.ok || !importsResp.ok || !alertsResp.ok) {
    content.innerHTML = `
      <section class="panel-card panel-card--single">
        <h2>加载失败</h2>
        <p>仪表盘数据加载异常，请稍后重试。</p>
      </section>
    `;
    return;
  }

  const data = {
    metrics: metricsResp.data,
    imports: importsResp.data,
    alerts: alertsResp.data
  };

  function draw() {
    const route = getCurrentRoute();
    markActiveRoute(route);
    content.innerHTML = renderByRoute(route, data, searchInput?.value || "");
  }

  if (!window.location.hash) {
    window.location.hash = "#overview";
  }

  draw();
  window.addEventListener("hashchange", draw);
  searchInput?.addEventListener("input", draw);
}
