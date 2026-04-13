import { DashboardService } from "../services/dashboard-service.js";
import { ImportService } from "../services/import-service.js";
import {
  createDefaultDocumentState,
  disposeDocumentRoute,
  ensureDocumentData,
  setupDocumentRoute,
  updateDocumentMain,
  updateDocumentSidebar
} from "./document-center.js";
import {
  createDefaultQAState,
  renderQAPage,
  setupQAPage
} from "./qa-page.js";
import { clearSession, getSession, isAuthenticated } from "../services/session-service.js";
import { escapeHtml } from "../utils/dom.js";
import {
  SUPPORTED_UPLOAD_EXTENSIONS,
  validateChunkConfig,
  validateImportMetadata,
  validateImportSource
} from "../utils/import-validators.js";

const ROUTES = ["overview", "docs", "doc-view", "imports", "qa", "alerts", "audit", "settings"];
const IMPORT_STEPS = [
  { key: "source", title: "选择来源" },
  { key: "metadata", title: "填写元数据" },
  { key: "chunk", title: "分块配置" },
  { key: "review", title: "预览提交" }
];
const IMPORT_STATUS_FINAL = new Set(["COMPLETED", "FAILED", "CANCELLED"]);
const DASHBOARD_ASIDE_COLLAPSED_KEY = "dashboard-aside-collapsed";
const IMPORT_UNSUPPORTED_MESSAGE = "暂未支持，仅保留入口展示";

let importPollTimer = null;
let importPollInFlight = false;

function createDefaultImportState() {
  return {
    stepIndex: 0,
    sourceType: "upload",
    selectedFile: null,
    sourcePayload: {
      file: null,
      fileMeta: null,
      url: "",
      s3Path: "",
      confluencePath: ""
    },
    metadata: {
      dept: "",
      sensitivity: "INTERNAL",
      tags: ["技术文档", "2024Q3"],
      version: "v1.0.0"
    },
    chunkConfig: {
      size: 500,
      overlap: 100,
      structuredChunk: true,
      ocr: false
    },
    tagInput: "",
    errors: {},
    submitError: "",
    submitting: false,
    task: null,
    lastSubmittedPayload: null
  };
}

function getCurrentRoute() {
  const hash = window.location.hash.replace("#", "");
  const route = hash.split("?")[0] || "overview";
  return ROUTES.includes(route) ? route : "overview";
}

function readAsideCollapsed() {
  try {
    return window.localStorage.getItem(DASHBOARD_ASIDE_COLLAPSED_KEY) === "1";
  } catch (_error) {
    return false;
  }
}

function persistAsideCollapsed(collapsed) {
  try {
    window.localStorage.setItem(DASHBOARD_ASIDE_COLLAPSED_KEY, collapsed ? "1" : "0");
  } catch (_error) {
    // ignore storage failure
  }
}

function applyAsideCollapsed(collapsed) {
  const shell = document.querySelector(".dashboard-shell");
  const toggle = document.querySelector("#dashboardAsideToggle");
  const enableCollapse = window.innerWidth > 920;
  if (shell) {
    shell.classList.toggle("is-aside-collapsed", enableCollapse && collapsed);
  }
  if (toggle instanceof HTMLButtonElement) {
    const effectiveCollapsed = enableCollapse && collapsed;
    toggle.setAttribute("aria-expanded", effectiveCollapsed ? "false" : "true");
    toggle.setAttribute("aria-label", effectiveCollapsed ? "展开侧边栏" : "收起侧边栏");
  }
}

function statusClass(status) {
  if (status.includes("异常")) return "status-chip status-chip--error";
  if (status.includes("取消")) return "status-chip status-chip--cancelled";
  if (status.includes("处理")) return "status-chip status-chip--processing";
  return "status-chip status-chip--ok";
}

function levelClass(level) {
  if (level === "critical") return "alert-card alert-card--critical";
  if (level === "warning") return "alert-card alert-card--warning";
  return "alert-card alert-card--info";
}

function formatFileSize(bytes) {
  const size = Number(bytes || 0);
  if (size < 1024 * 1024) {
    return `${Math.max(1, Math.round(size / 1024))}KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(1)}MB`;
}

function fileAcceptDescription() {
  return SUPPORTED_UPLOAD_EXTENSIONS.map((item) => item.toUpperCase()).join("、");
}

function renderMetricCards(metrics) {
  return `
    <section class="metric-grid">
      <article class="metric-card card1">
        <p class="metric-card__label">Token 今日消耗</p>
        <h2>${escapeHtml(metrics.tokenToday.toLocaleString())}</h2>
        <span class="status-pill status-pill--ok">+${escapeHtml(String(metrics.tokenGrowthPercent))}%</span>
      </article>
      <article class="metric-card card2">
        <p class="metric-card__label">向量 DB 状态</p>
        <h2>${escapeHtml(metrics.vectorDbStatus)}</h2>
        <span class="status-pill status-pill--ok">${escapeHtml(metrics.vectorDbHealth)}</span>
      </article>
      <article class="metric-card card3">
        <p class="metric-card__label">LLM 可用率</p>
        <h2>${escapeHtml(metrics.llmAvailability)}</h2>
        <span class="status-pill status-pill--neutral">${escapeHtml(metrics.llmStability)}</span>
      </article>
      <article class="metric-card card4">
        <p class="metric-card__label">处理中导入任务</p>
        <h2>${escapeHtml(String(metrics.processingTasks))}</h2>
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
                <td><span class="${statusClass(item.statusLabel)}">${escapeHtml(item.statusLabel)}</span></td>
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

function renderImportStepNav(importState) {
  return `
    <ol class="imports-steps">
      ${IMPORT_STEPS.map((step, index) => {
        const stepClass = index === importState.stepIndex ? "imports-step is-active" : "imports-step";
        return `
          <li class="${stepClass}">
            <span class="imports-step__meta">STEP ${index + 1}</span>
            <strong>${escapeHtml(step.title)}</strong>
          </li>
        `;
      }).join("")}
    </ol>
  `;
}

function renderImportSourcePanel(importState) {
  const cards = [
    { type: "upload", title: "本地上传", icon: "⤴", enabled: true },
    { type: "url", title: "网页 URL", icon: "🔗", enabled: false },
    { type: "s3", title: "S3 存储", icon: "☁", enabled: false },
    { type: "confluence", title: "Confluence", icon: "✶", enabled: false }
  ];
  const current = importState.sourceType;
  const fileMeta = importState.sourcePayload.fileMeta;

  return `
    <section class="imports-section">
      <div class="imports-section__title"><span>1</span>选择导入来源</div>
      <div class="imports-source-grid">
        ${cards
          .map((card) => {
            const cardClass = [
              "imports-source-card",
              card.type === current ? "is-active" : "",
              card.enabled ? "" : "is-disabled"
            ]
              .filter(Boolean)
              .join(" ");
            return `
              <button type="button" class="${cardClass}" data-action="select-source" data-source-type="${card.type}" ${
                card.enabled ? "" : `disabled title="${IMPORT_UNSUPPORTED_MESSAGE}"`
              }>
                <span>${escapeHtml(card.icon)}</span>
                <strong>${escapeHtml(card.title)}</strong>
                ${card.enabled ? "" : `<small>${escapeHtml(IMPORT_UNSUPPORTED_MESSAGE)}</small>`}
              </button>
            `;
          })
          .join("")}
      </div>
      <p class="imports-field-error">${escapeHtml(importState.errors.sourceType || "")}</p>

      ${current === "upload"
        ? `
          <div class="imports-upload-zone" data-action="pick-file">
            <input id="importsFileInput" type="file" class="imports-hidden-input" accept=".pdf,.docx,.md" />
            <div class="imports-upload-zone__icon">☁</div>
            <strong>点击或将文件拖拽到此处上传</strong>
            <p>支持 ${fileAcceptDescription()} 格式，单个文件不超过 200MB</p>
            ${fileMeta ? `<small>已选择：${escapeHtml(fileMeta.name)} (${escapeHtml(formatFileSize(fileMeta.size))})</small>` : ""}
          </div>
          <p class="imports-field-error">${escapeHtml(importState.errors.file || "")}</p>
        `
        : ""}
    </section>
  `;
}

function renderMetadataPanel(importState) {
  const sensitivityOptions = [
    { value: "PUBLIC", label: "公开" },
    { value: "INTERNAL", label: "内部" },
    { value: "CONFIDENTIAL", label: "机密" },
    { value: "SECRET", label: "绝密" }
  ];
  const departments = ["产品研发部", "平台组", "架构组", "测试与质量部", "市场策略部"];

  return `
    <section class="imports-section">
      <div class="imports-section__title"><span>2</span>填写文档元数据</div>

      <div class="imports-form-grid">
        <label class="imports-input-group">
          <span>所属部门</span>
          <select id="importsDeptSelect" disabled>
            <option value="">请选择部门</option>
            ${departments
              .map(
                (dept) =>
                  `<option value="${escapeHtml(dept)}" ${
                    importState.metadata.dept === dept ? "selected" : ""
                  }>${escapeHtml(dept)}</option>`
              )
              .join("")}
          </select>
          <small>暂未接入后端，不参与本次提交</small>
        </label>

        <div class="imports-input-group">
          <span>敏感等级</span>
          <div class="imports-segment">
            ${sensitivityOptions
              .map((item) => {
                const activeClass = item.value === importState.metadata.sensitivity ? "is-active" : "";
                return `<button type="button" class="${activeClass}" data-action="set-sensitivity" data-sensitivity="${item.value}">${escapeHtml(
                  item.label
                )}</button>`;
              })
              .join("")}
          </div>
          <small class="imports-field-error">${escapeHtml(importState.errors.sensitivity || "")}</small>
        </div>

        <div class="imports-input-group">
          <span>标签（多选）</span>
          <div class="imports-tag-box">
            ${importState.metadata.tags
              .map(
                (tag) =>
                  `<span class="imports-tag">${escapeHtml(
                    tag
                  )}<button type="button" data-action="remove-tag" data-tag="${escapeHtml(tag)}">×</button></span>`
              )
              .join("")}
            <input id="importsTagInput" type="text" placeholder="输入标签后按 Enter" value="${escapeHtml(importState.tagInput)}" />
          </div>
        </div>

        <label class="imports-input-group">
          <span>版本号</span>
          <input id="importsVersionInput" type="text" value="${escapeHtml(importState.metadata.version)}" />
          <small class="imports-field-error">${escapeHtml(importState.errors.version || "")}</small>
        </label>
      </div>
    </section>
  `;
}

function renderChunkPanel(importState) {
  return `
    <section class="imports-section">
      <div class="imports-section__title"><span>3</span>分块与解析配置</div>
      <div class="imports-form-grid">
        <label class="imports-input-group">
          <span>Chunk 大小（tokens）</span>
          <input id="importsChunkSizeInput" type="number" min="100" max="2000" value="${escapeHtml(
            String(importState.chunkConfig.size)
          )}" />
          <small class="imports-field-error">${escapeHtml(importState.errors.size || "")}</small>
        </label>

        <label class="imports-input-group">
          <span>Overlap（tokens）</span>
          <input id="importsChunkOverlapInput" type="number" min="0" value="${escapeHtml(
            String(importState.chunkConfig.overlap)
          )}" />
          <small class="imports-field-error">${escapeHtml(importState.errors.overlap || "")}</small>
        </label>

        <label class="imports-switch">
          <input id="importsStructuredSwitch" type="checkbox" ${importState.chunkConfig.structuredChunk ? "checked" : ""} />
          <div>
            <strong>结构化分块</strong>
            <p>代码块与表格按语义结构切分</p>
          </div>
        </label>

        <label class="imports-switch">
          <input id="importsOcrSwitch" type="checkbox" ${importState.chunkConfig.ocr ? "checked" : ""} disabled />
          <div>
            <strong>启用 OCR（可选）</strong>
            <p>暂未支持，本次提交不会生效</p>
          </div>
        </label>
      </div>
    </section>
  `;
}

function sourcePayloadPreview(importState) {
  const sourceTypeMap = {
    upload: "本地上传",
    url: "网页 URL",
    s3: "S3 存储",
    confluence: "Confluence"
  };
  const source = importState.sourceType;
  const payload = importState.sourcePayload;
  let detail = "-";
  if (source === "upload") {
    detail = payload.fileMeta?.name || "未选择文件";
  }
  if (source === "url") detail = payload.url || "未填写 URL";
  if (source === "s3") detail = payload.s3Path || "未填写路径";
  if (source === "confluence") detail = payload.confluencePath || "未填写路径";

  return {
    sourceLabel: sourceTypeMap[source],
    sourceDetail: detail
  };
}

function renderReviewPanel(importState) {
  const preview = sourcePayloadPreview(importState);
  return `
    <section class="imports-section">
      <div class="imports-section__title"><span>4</span>预览并提交</div>
      <div class="imports-review-grid">
        <article>
          <h3>来源信息</h3>
          <dl>
            <dt>导入来源</dt>
            <dd>${escapeHtml(preview.sourceLabel)}</dd>
            <dt>来源详情</dt>
            <dd>${escapeHtml(preview.sourceDetail)}</dd>
          </dl>
        </article>

        <article>
          <h3>元数据</h3>
          <dl>
            <dt>部门</dt>
            <dd>暂未接入</dd>
            <dt>敏感等级</dt>
            <dd>${escapeHtml(importState.metadata.sensitivity)}</dd>
            <dt>标签</dt>
            <dd>${escapeHtml(importState.metadata.tags.join(", ") || "-")}</dd>
            <dt>版本号</dt>
            <dd>${escapeHtml(importState.metadata.version || "-")}</dd>
          </dl>
        </article>

        <article>
          <h3>分块配置</h3>
          <dl>
            <dt>Chunk 大小</dt>
            <dd>${escapeHtml(String(importState.chunkConfig.size))}</dd>
            <dt>Overlap</dt>
            <dd>${escapeHtml(String(importState.chunkConfig.overlap))}</dd>
            <dt>结构化分块</dt>
            <dd>${importState.chunkConfig.structuredChunk ? "是" : "否"}</dd>
            <dt>OCR</dt>
            <dd>暂未支持</dd>
          </dl>
        </article>
      </div>
      ${importState.submitError ? `<p class="imports-submit-error">${escapeHtml(importState.submitError)}</p>` : ""}
    </section>
  `;
}

function renderImportTaskCard(importState) {
  if (!importState.task) return "";
  const running = !IMPORT_STATUS_FINAL.has(importState.task.status);
  const failed = importState.task.status === "FAILED";
  const cancelled = importState.task.status === "CANCELLED";

  return `
    <section class="imports-task-card">
      <header>
        <h3>任务状态：${escapeHtml(importState.task.taskId)}</h3>
        <span class="imports-task-status imports-task-status--${escapeHtml(importState.task.status.toLowerCase())}">${escapeHtml(
          importState.task.statusLabel
        )}</span>
      </header>
      <div class="imports-task-progress">
        <div class="imports-task-progress__bar" style="width:${escapeHtml(String(importState.task.progress))}%"></div>
      </div>
      <p>当前进度：${escapeHtml(String(importState.task.progress))}%</p>
      ${importState.task.processedChunks || importState.task.totalChunks
        ? `<p>已处理分块：${escapeHtml(String(importState.task.processedChunks || 0))}/${escapeHtml(
            String(importState.task.totalChunks || 0)
          )}</p>`
        : ""}
      ${importState.task.resultDocId ? `<p>生成文档 ID：${escapeHtml(String(importState.task.resultDocId))}</p>` : ""}
      ${importState.task.errorMessage ? `<p class="imports-task-error">${escapeHtml(importState.task.errorMessage)}</p>` : ""}
      <div class="imports-task-actions">
        ${running ? '<button type="button" data-action="cancel-task">取消导入</button>' : ""}
        ${(failed || cancelled) && importState.lastSubmittedPayload ? '<button type="button" data-action="retry-task">重试导入</button>' : ""}
      </div>
    </section>
  `;
}

function renderImportActions(importState) {
  const atFirst = importState.stepIndex === 0;
  const atLast = importState.stepIndex === IMPORT_STEPS.length - 1;
  const submitting = importState.submitting;

  return `
    <footer class="imports-actions">
      <button type="button" class="btn btn--secondary btn--small" data-action="reset-imports" ${submitting ? "disabled" : ""}>取消</button>
      <div class="imports-actions__right">
        ${!atFirst ? `<button type="button" class="btn btn--secondary btn--small" data-action="step-prev" ${submitting ? "disabled" : ""}>上一步</button>` : ""}
        <button type="button" class="btn btn--primary btn--small" data-action="${atLast ? "submit-imports" : "step-next"}" ${
          submitting ? "disabled" : ""
        }>${submitting ? "提交中..." : atLast ? "提交导入" : "下一步"}</button>
      </div>
    </footer>
  `;
}

function renderImportWizard(importState, imports) {
  const stepKey = IMPORT_STEPS[importState.stepIndex]?.key || "source";
  let stepPanel = renderImportSourcePanel(importState);
  if (stepKey === "metadata") stepPanel = renderMetadataPanel(importState);
  if (stepKey === "chunk") stepPanel = renderChunkPanel(importState);
  if (stepKey === "review") stepPanel = renderReviewPanel(importState);

  return `
    <section class="imports-page" data-imports-root>
      <header class="imports-page__header">
        <p>文档管理 &gt; 导入向导</p>
      </header>
      ${renderImportStepNav(importState)}
      <article class="imports-wizard-card">
        ${stepPanel}
        ${renderImportActions(importState)}
      </article>
      ${renderImportTaskCard(importState)}
      <article class="panel-card">
        <header class="panel-card__header">
          <h2>最近导入任务</h2>
        </header>
        ${renderImportsTable(imports)}
      </article>
    </section>
  `;
}

function renderByRoute(route, data, query, importState, qaState) {
  const q = query.trim().toLowerCase();
  const filteredImports = q
    ? data.imports.filter((item) => item.name.toLowerCase().includes(q) || item.id.toLowerCase().includes(q))
    : data.imports;
  const filteredAlerts = q
    ? data.alerts.filter((item) => item.title.toLowerCase().includes(q) || item.description.toLowerCase().includes(q))
    : data.alerts;

  if (route === "qa") {
    return renderQAPage(qaState);
  }

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
    return renderImportWizard(importState, filteredImports);
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
  const activeRoute = route === "doc-view" ? "docs" : route;
  document.querySelectorAll(".dashboard-nav__item").forEach((item) => {
    item.classList.toggle("is-active", item.dataset.route === activeRoute);
  });
}

function createImportPayload(importState) {
  return {
    sourceType: importState.sourceType,
    file: importState.selectedFile,
    fileMeta: importState.sourcePayload.fileMeta,
    sensitivity: importState.metadata.sensitivity,
    tags: importState.metadata.tags,
    versionLabel: importState.metadata.version,
    chunkConfig: {
      size: Number(importState.chunkConfig.size),
      overlap: Number(importState.chunkConfig.overlap),
      structuredChunk: importState.chunkConfig.structuredChunk
    }
  };
}

function validateStep(importState) {
  if (importState.stepIndex === 0) {
    return validateImportSource(importState.sourceType, importState.sourcePayload);
  }
  if (importState.stepIndex === 1) {
    return validateImportMetadata(importState.metadata);
  }
  if (importState.stepIndex === 2) {
    return validateChunkConfig(importState.chunkConfig);
  }

  const sourceResult = validateImportSource(importState.sourceType, importState.sourcePayload);
  const metadataResult = validateImportMetadata(importState.metadata);
  const chunkResult = validateChunkConfig(importState.chunkConfig);
  const errors = {
    ...sourceResult.errors,
    ...metadataResult.errors,
    ...chunkResult.errors
  };
  return { valid: Object.keys(errors).length === 0, errors };
}

function syncImportsStateFromDom(importState, content) {
  const versionInput = content.querySelector("#importsVersionInput");
  if (versionInput) importState.metadata.version = versionInput.value.trim();

  const chunkSizeInput = content.querySelector("#importsChunkSizeInput");
  const chunkOverlapInput = content.querySelector("#importsChunkOverlapInput");
  const structuredSwitch = content.querySelector("#importsStructuredSwitch");
  if (chunkSizeInput) importState.chunkConfig.size = Number(chunkSizeInput.value);
  if (chunkOverlapInput) importState.chunkConfig.overlap = Number(chunkOverlapInput.value);
  if (structuredSwitch) importState.chunkConfig.structuredChunk = structuredSwitch.checked;
}

function stopImportPolling() {
  if (importPollTimer) {
    clearInterval(importPollTimer);
    importPollTimer = null;
  }
}

async function refreshRecentImports(data) {
  const importsResp = await ImportService.getRecentImports();
  if (importsResp.ok) {
    data.imports = importsResp.data;
  }
}

function applySelectedFile(importState, file) {
  importState.sourceType = "upload";
  importState.selectedFile = file;
  importState.sourcePayload.file = file;
  importState.sourcePayload.fileMeta = file
    ? {
        name: file.name,
        size: file.size,
        type: file.type
      }
    : null;
}

function startImportPolling(importState, data, draw) {
  stopImportPolling();
  importPollTimer = setInterval(async () => {
    if (importPollInFlight || !importState.task?.taskId) return;
    importPollInFlight = true;
    const statusResp = await ImportService.getImportTaskStatus(importState.task.taskId);
    importPollInFlight = false;
    if (!statusResp.ok) {
      importState.submitError = statusResp.message || "任务状态查询失败";
      draw();
      return;
    }
    importState.task = statusResp.data;
    if (IMPORT_STATUS_FINAL.has(importState.task.status)) {
      stopImportPolling();
      await refreshRecentImports(data);
    }
    draw();
  }, 1200);
}

function setupImportsRoute(rootContent, importState, data, draw) {
  const root = rootContent.querySelector("[data-imports-root]");
  if (!root) return;

  const fileInput = root.querySelector("#importsFileInput");
  if (fileInput) {
    fileInput.addEventListener("change", () => {
      const file = fileInput.files?.[0];
      if (!file) return;
      applySelectedFile(importState, file);
      importState.errors = {};
      draw();
    });
  }

  const uploadZone = root.querySelector(".imports-upload-zone");
  if (uploadZone) {
    uploadZone.addEventListener("dragover", (event) => {
      event.preventDefault();
      uploadZone.classList.add("is-dragover");
    });
    uploadZone.addEventListener("dragleave", () => {
      uploadZone.classList.remove("is-dragover");
    });
    uploadZone.addEventListener("drop", (event) => {
      event.preventDefault();
      uploadZone.classList.remove("is-dragover");
      const file = event.dataTransfer?.files?.[0];
      if (!file) return;
      applySelectedFile(importState, file);
      importState.errors = {};
      draw();
    });
  }

  root.addEventListener("keydown", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    if (target.id !== "importsTagInput") return;
    if (event.key !== "Enter" && event.key !== ",") return;
    event.preventDefault();
    syncImportsStateFromDom(importState, rootContent);
    const input = root.querySelector("#importsTagInput");
    const value = input ? input.value.trim().replace(/,$/, "") : "";
    if (!value) return;
    if (!importState.metadata.tags.includes(value)) {
      importState.metadata.tags.push(value);
    }
    importState.tagInput = "";
    if (input) input.value = "";
    draw();
  });

  root.addEventListener("click", async (event) => {
    const target = event.target;
    if (!(target instanceof Element)) return;
    const actionTarget = target.closest("[data-action]");
    if (!actionTarget) return;
    const action = actionTarget.getAttribute("data-action");

    if (action === "pick-file") {
      fileInput?.click();
      return;
    }

    if (action === "select-source") {
      const nextSource = actionTarget.getAttribute("data-source-type") || "upload";
      if (nextSource !== "upload") {
        importState.submitError = IMPORT_UNSUPPORTED_MESSAGE;
        draw();
        return;
      }
      syncImportsStateFromDom(importState, rootContent);
      importState.sourceType = nextSource;
      importState.errors = {};
      importState.submitError = "";
      draw();
      return;
    }

    if (action === "set-sensitivity") {
      importState.metadata.sensitivity = actionTarget.getAttribute("data-sensitivity") || "INTERNAL";
      importState.errors.sensitivity = "";
      draw();
      return;
    }

    if (action === "remove-tag") {
      syncImportsStateFromDom(importState, rootContent);
      const tag = actionTarget.getAttribute("data-tag");
      importState.metadata.tags = importState.metadata.tags.filter((item) => item !== tag);
      draw();
      return;
    }

    if (action === "step-prev") {
      syncImportsStateFromDom(importState, rootContent);
      importState.stepIndex = Math.max(0, importState.stepIndex - 1);
      importState.errors = {};
      draw();
      return;
    }

    if (action === "step-next") {
      syncImportsStateFromDom(importState, rootContent);
      const validateResult = validateStep(importState);
      if (!validateResult.valid) {
        importState.errors = validateResult.errors;
        draw();
        return;
      }
      importState.errors = {};
      importState.stepIndex = Math.min(IMPORT_STEPS.length - 1, importState.stepIndex + 1);
      draw();
      return;
    }

    if (action === "reset-imports") {
      stopImportPolling();
      const nextState = createDefaultImportState();
      Object.assign(importState, nextState);
      draw();
      return;
    }

    if (action === "submit-imports") {
      syncImportsStateFromDom(importState, rootContent);
      const validateResult = validateStep(importState);
      if (!validateResult.valid) {
        importState.errors = validateResult.errors;
        draw();
        return;
      }

      importState.submitError = "";
      importState.submitting = true;
      draw();

      const payload = createImportPayload(importState);
      const createResp = await ImportService.createImportTask(payload);
      importState.submitting = false;
      if (!createResp.ok) {
        importState.submitError = createResp.message || "提交导入失败，请稍后重试";
        draw();
        return;
      }

      importState.lastSubmittedPayload = payload;
      importState.task = createResp.data;
      draw();
      startImportPolling(importState, data, draw);
      return;
    }

    if (action === "cancel-task" && importState.task?.taskId) {
      const cancelResp = await ImportService.cancelImportTask(importState.task.taskId);
      if (!cancelResp.ok) {
        importState.submitError = cancelResp.message || "取消任务失败";
        draw();
        return;
      }
      importState.task = cancelResp.data;
      stopImportPolling();
      await refreshRecentImports(data);
      draw();
      return;
    }

    if (action === "retry-task" && importState.lastSubmittedPayload) {
      if (!importState.lastSubmittedPayload.file) {
        importState.submitError = "原始文件已丢失，请重新选择文件后再重试";
        draw();
        return;
      }
      importState.stepIndex = IMPORT_STEPS.length - 1;
      importState.submitError = "";
      importState.submitting = true;
      draw();
      const retryResp = await ImportService.createImportTask(importState.lastSubmittedPayload);
      importState.submitting = false;
      if (!retryResp.ok) {
        importState.submitError = retryResp.message || "重试提交失败";
        draw();
        return;
      }
      importState.task = retryResp.data;
      draw();
      startImportPolling(importState, data, draw);
    }
  });
}

export async function initDashboardPage() {
  if (!isAuthenticated()) {
    window.location.href = "/login.html";
    return;
  }

  const content = document.querySelector("#dashboardContent");
  const dashboardMain = document.querySelector(".dashboard-main");
  const userName = document.querySelector("#currentUserName");
  const userInitial = document.querySelector("#currentUserInitial");
  const logoutButton = document.querySelector("#logoutBtn");
  const userMenuContainer = document.querySelector("[data-dashboard-user-menu]");
  const userMenuToggle = document.querySelector("#dashboardUserToggle");
  const userMenu = document.querySelector("#dashboardUserMenu");
  const asideToggleButton = document.querySelector("#dashboardAsideToggle");
  if (!content) return;

  const session = getSession();
  const username = session?.user?.username || "Admin User";
  if (userName) {
    userName.textContent = username;
  }
  if (userInitial) {
    userInitial.textContent = String(username).trim().charAt(0).toUpperCase() || "A";
  }

  const closeUserMenu = () => {
    if (!userMenu || !userMenuToggle) return;
    userMenu.hidden = true;
    userMenuToggle.setAttribute("aria-expanded", "false");
  };

  const openUserMenu = () => {
    if (!userMenu || !userMenuToggle) return;
    userMenu.hidden = false;
    userMenuToggle.setAttribute("aria-expanded", "true");
  };

  userMenuToggle?.addEventListener("click", (event) => {
    event.stopPropagation();
    if (!userMenu) return;
    if (userMenu.hidden) {
      openUserMenu();
      return;
    }
    closeUserMenu();
  });

  document.addEventListener("click", (event) => {
    if (!userMenuContainer?.contains(event.target)) {
      closeUserMenu();
    }
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      closeUserMenu();
    }
  });

  logoutButton?.addEventListener("click", () => {
    closeUserMenu();
    stopImportPolling();
    clearSession();
    window.location.href = "/login.html";
  });

  let asideCollapsed = readAsideCollapsed();
  applyAsideCollapsed(asideCollapsed);
  asideToggleButton?.addEventListener("click", () => {
    asideCollapsed = !asideCollapsed;
    persistAsideCollapsed(asideCollapsed);
    applyAsideCollapsed(asideCollapsed);
  });
  window.addEventListener("resize", () => {
    applyAsideCollapsed(asideCollapsed);
  });

  renderLoading(content);
  const importState = createDefaultImportState();
  const documentState = createDefaultDocumentState();
  const qaState = createDefaultQAState();
  let drawRequestToken = 0;

  const [metricsResp, importsResp, alertsResp] = await Promise.all([
    DashboardService.getMetrics(),
    ImportService.getRecentImports(),
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

  function renderDocumentSidebarPane() {
    if (documentState.sidebarEl instanceof HTMLElement) {
      updateDocumentSidebar(documentState);
    }
  }

  function renderDocumentMainPane() {
    if (documentState.mainEl instanceof HTMLElement) {
      updateDocumentMain(getCurrentRoute(), documentState, documentState.activeKeyword || "");
    }
  }

  async function draw() {
    const currentToken = ++drawRequestToken;
    const route = getCurrentRoute();
    dashboardMain?.classList.toggle("is-qa-layout", route === "qa");
    closeUserMenu();
    markActiveRoute(route);
    const keyword = documentState.activeKeyword || "";

    if (route === "docs" || route === "doc-view") {
      if (!documentState.shellMounted) {
        setupDocumentRoute(content, documentState, {
          renderSidebar: renderDocumentSidebarPane,
          renderMain: renderDocumentMainPane,
          requestDraw: draw
        });
      }
      try {
        const { treeChanged } = await ensureDocumentData(documentState, route, keyword);
        if (currentToken !== drawRequestToken) return;
        if (treeChanged || !documentState.sidebarEl?.hasChildNodes()) {
          updateDocumentSidebar(documentState);
        }
        updateDocumentMain(route, documentState, keyword);
        documentState.activeRoute = route;
        documentState.activeKeyword = keyword;
      } catch (_error) {
        documentState.error = "文档模块加载失败，请稍后重试。";
        if (documentState.mainEl instanceof HTMLElement) {
          updateDocumentMain(route, documentState, keyword);
        }
      }
      return;
    }

    disposeDocumentRoute(documentState);

    content.innerHTML = renderByRoute(route, data, keyword, importState, qaState);
    if (route === "imports") {
      setupImportsRoute(content, importState, data, draw);
    }
    if (route === "qa") {
      setupQAPage(content, qaState, draw);
    }
  }

  if (!window.location.hash) {
    window.location.hash = "#overview";
  }

  await draw();
  window.addEventListener("hashchange", () => {
    draw();
  });
}
