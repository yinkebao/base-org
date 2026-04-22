/**
 * QA 工作区页面组件
 * 聊天式交互 UI + 会话列表 + 来源面板
 */

import { QAService } from "../services/qa-service.js";
import { escapeHtml } from "../utils/dom.js";
import { renderMarkdownToHtml } from "../utils/markdown-renderer.js";
import { hydrateMermaidDiagrams } from "../utils/mermaid-renderer.js";

const QA_INPUT_MIN_ROWS = 2;
const QA_INPUT_MAX_ROWS = 6;
const QA_INPUT_LINE_HEIGHT = 24;

function truncateContent(text, limit = 100) {
  const normalized = String(text || "");
  if (normalized.length <= limit) return normalized;
  return `${normalized.slice(0, limit)}...`;
}

function generateMessageId() {
  return `msg_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}

function escapeSelectorValue(value) {
  const normalized = String(value || "");
  if (typeof CSS !== "undefined" && typeof CSS.escape === "function") {
    return CSS.escape(normalized);
  }
  return normalized.replace(/["\\]/g, "\\$&");
}

function formatTime(isoString) {
  if (!isoString) return "--:--";
  const date = new Date(isoString);
  return date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" });
}

function formatDateTime(isoString) {
  if (!isoString) return "刚刚";
  const date = new Date(isoString);
  return date.toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" });
}

function startOfDay(date) {
  const next = new Date(date);
  next.setHours(0, 0, 0, 0);
  return next;
}

function resolveSessionGroupKey(isoString) {
  const value = new Date(isoString || Date.now());
  if (Number.isNaN(value.getTime())) {
    return "earlier";
  }

  const now = new Date();
  const todayStart = startOfDay(now);
  const valueStart = startOfDay(value);
  const dayDiff = Math.round((todayStart.getTime() - valueStart.getTime()) / 86400000);

  if (dayDiff <= 0) return "today";
  if (dayDiff === 1) return "yesterday";
  if (dayDiff <= 7) return "last7";
  if (dayDiff <= 30) return "last30";
  return "earlier";
}

function formatMarkdown(text) {
  const normalized = String(text || "").trim();
  return normalized ? renderMarkdownToHtml(normalized) : "";
}

function formatUserText(text) {
  return escapeHtml(String(text || "")).replace(/\n/g, "<br>");
}

function renderStreamingPlaceholder() {
  return `
    <div class="qa-message__stream" aria-live="polite">
      <span class="qa-message__cursor" aria-hidden="true"></span>
      <span>正在生成回答...</span>
    </div>
  `;
}

function renderSessionList(state) {
  if (state.sessions.length === 0) {
    return '<div class="qa-session-empty">暂无会话，发送第一条问题开始。</div>';
  }

  const groupMeta = [
    { key: "today", title: "今天" },
    { key: "yesterday", title: "昨天" },
    { key: "last7", title: "7天内" },
    { key: "last30", title: "30天内" },
    { key: "earlier", title: "更早" }
  ];
  const groups = new Map(groupMeta.map((item) => [item.key, []]));

  state.sessions.forEach((session) => {
    const groupKey = resolveSessionGroupKey(session.lastMessageAt || session.createdAt);
    groups.get(groupKey)?.push(session);
  });

  return `
    <div class="qa-session-groups">
      ${groupMeta
        .map((group) => {
          const sessions = groups.get(group.key) || [];
          if (sessions.length === 0) {
            return "";
          }
          return `
            <section class="qa-session-group">
              <h4 class="qa-session-group__title">${group.title}</h4>
              <div class="qa-session-list">
                ${sessions
                  .map((session) => `
                    <button
                      type="button"
                      class="qa-session-item ${String(state.currentSessionId) === String(session.sessionId) ? "is-active" : ""}"
                      data-action="switch-session"
                      data-session-id="${escapeHtml(session.sessionId)}"
                      title="${escapeHtml(session.title || "新对话")}"
                    >
                      <span>${escapeHtml(session.title || "新对话")}</span>
                    </button>
                  `)
                  .join("")}
              </div>
            </section>
          `;
        })
        .join("")}
    </div>
  `;
}

function renderSourcePanel(sources, selectedId) {
  if (!sources || sources.length === 0) {
    return "";
  }

  return `
    <div class="qa-sources-list">
      ${sources
        .map(
          (source, index) => `
        <article class="qa-source-card ${selectedId === source.chunkId ? "is-selected" : ""}" data-chunk-id="${escapeHtml(source.chunkId)}" data-doc-id="${escapeHtml(source.docId)}">
          <header>
            <span class="qa-source-index">#${index + 1}</span>
            <span class="qa-source-score">${(Number(source.score || 0) * 100).toFixed(0)}%</span>
          </header>
          <h4>${escapeHtml(source.docTitle)}</h4>
          <p>${escapeHtml(truncateContent(source.content, 160))}</p>
          <footer>
            <span class="qa-source-dept">${escapeHtml(source.metadata?.dept || "-")}</span>
            <span class="qa-source-sensitivity qa-source-sensitivity--${source.metadata?.sensitivity || "internal"}">${escapeHtml(source.metadata?.sensitivity || "internal")}</span>
          </footer>
        </article>
      `
        )
        .join("")}
    </div>
  `;
}

function renderAssistantBadges(message) {
  const badges = [];
  if (message.fallbackMode === "GENERAL_NO_HIT") {
    badges.push('<div class="qa-message__badge qa-message__badge--warn">未命中知识库，当前回复基于通用经验</div>');
  }
  if (message.rewrittenQuery) {
    badges.push(`<div class="qa-message__badge qa-message__badge--info">检索改写：${escapeHtml(message.rewrittenQuery)}</div>`);
  }
  if (message.degraded && message.degradeReason && message.fallbackMode !== "GENERAL_NO_HIT") {
    badges.push(`<div class="qa-message__badge qa-message__badge--muted">${escapeHtml(message.degradeReason)}</div>`);
  }
  if (message.planSummary) {
    badges.push(`<div class="qa-message__badge qa-message__badge--info">工具规划：${escapeHtml(message.planSummary)}</div>`);
  }
  if (Array.isArray(message.externalSources) && message.externalSources.length > 0) {
    badges.push('<div class="qa-message__badge qa-message__badge--info">已使用联网搜索补充</div>');
  }

  if (badges.length === 0) {
    return "";
  }

  return `<div class="qa-message__badges" data-role="message-badges">${badges.join("")}</div>`;
}

function renderAssistantContent(message) {
  const html = formatMarkdown(message.content);
  if (html) {
    return `<div class="qa-markdown" data-role="message-markdown">${html}</div>`;
  }
  if (message.pending) {
    return renderStreamingPlaceholder();
  }
  return '<div class="qa-message__empty">暂无回答内容</div>';
}

function renderAssistantSources(message) {
  if (!message.sources?.length) {
    return "";
  }

  return `
    <div class="qa-message__sources-header">
      <span>引用来源</span>
      <span>${message.sources.length} 条</span>
    </div>
    ${renderSourcePanel(message.sources, null)}
  `;
}

function renderAssistantToolTrace(message) {
  if (!Array.isArray(message.toolTrace) || message.toolTrace.length === 0) {
    return "";
  }

  return `
    <div class="qa-tool-trace">
      <div class="qa-tool-trace__header">
        <span>工具轨迹</span>
        <span>${message.toolTrace.length} 步</span>
      </div>
      <div class="qa-tool-trace__list">
        ${message.toolTrace.map((item) => `
          <div class="qa-tool-trace__item qa-tool-trace__item--${escapeHtml(String(item.status || "").toLowerCase())}">
            <div class="qa-tool-trace__name">${escapeHtml(item.toolName || item.toolId || "工具")}</div>
            <div class="qa-tool-trace__meta">${escapeHtml(item.goal || item.summary || "")}</div>
            ${item.errorMessage ? `<div class="qa-tool-trace__error">${escapeHtml(item.errorMessage)}</div>` : ""}
          </div>
        `).join("")}
      </div>
    </div>
  `;
}

function renderAssistantDiagrams(message) {
  if (!Array.isArray(message.diagrams) || message.diagrams.length === 0) {
    return "";
  }

  return `
    <div class="qa-diagrams">
      ${message.diagrams.map((diagram, index) => `
        <section class="qa-diagram-card" data-diagram-id="${escapeHtml(diagram.diagramId || `diagram_${index}`)}">
          <header class="qa-diagram-card__header">
            <span>${escapeHtml(diagram.title || "Mermaid 图表")}</span>
            <span>${escapeHtml(diagram.diagramType || "flowchart")}</span>
          </header>
          <div class="qa-mermaid" data-role="mermaid-diagram">
            <pre class="qa-mermaid__dsl" data-role="mermaid-source"><code>${escapeHtml(diagram.mermaidDsl || "")}</code></pre>
          </div>
        </section>
      `).join("")}
    </div>
  `;
}

function renderExternalSources(message) {
  if (!Array.isArray(message.externalSources) || message.externalSources.length === 0) {
    return "";
  }

  return `
    <div class="qa-external-sources">
      <div class="qa-external-sources__header">
        <span>外部公开来源</span>
        <span>${message.externalSources.length} 条</span>
      </div>
      <div class="qa-external-sources__list">
        ${message.externalSources.map((item) => `
          <article class="qa-external-source-card">
            <h4>${escapeHtml(item.title || item.toolName || "外部来源")}</h4>
            <p>${escapeHtml(truncateContent(item.content || "", 220))}</p>
            <footer>
              <span>${escapeHtml(item.metadata?.domain || "外部站点")}</span>
              <span>${escapeHtml(item.metadata?.publishedAt || "时间未知")}</span>
            </footer>
          </article>
        `).join("")}
      </div>
    </div>
  `;
}

function renderAssistantStatus(message) {
  if (message.pending) {
    return `
      <span class="qa-message__status">
        <span class="qa-message__cursor" aria-hidden="true"></span>
        <span>实时生成中</span>
      </span>
    `;
  }

  if (message.degraded && message.degradeReason) {
    return `<span class="qa-message__status qa-message__status--warn">${escapeHtml(message.degradeReason)}</span>`;
  }

  return "";
}

function renderUserMessage(message) {
  return `
    <article class="qa-message qa-message--user" data-message-id="${escapeHtml(message.id)}">
      <div class="qa-message__body">
        <div class="qa-message__panel">
          <div class="qa-message__content">
            <p>${formatUserText(message.content)}</p>
          </div>
        </div>
        <div class="qa-message__footer qa-message__footer--user">
          <span class="qa-message__time" data-role="message-time">${formatTime(message.timestamp)}</span>
        </div>
      </div>
    </article>
  `;
}

function renderAssistantMessage(message) {
  return `
    <article class="qa-message qa-message--assistant ${message.pending ? "qa-message--pending" : ""}" data-message-id="${escapeHtml(message.id)}">
      <div class="qa-message__avatar" aria-hidden="true">AI</div>
      <div class="qa-message__body">
        <div class="qa-message__panel">
          ${renderAssistantBadges(message)}
          <div class="qa-message__content" data-role="message-content">
            ${renderAssistantContent(message)}
          </div>
          <div class="qa-message__tooling" data-role="message-tooling">
            ${renderAssistantToolTrace(message)}
            ${renderAssistantDiagrams(message)}
            ${renderExternalSources(message)}
          </div>
          <div class="qa-message__meta">
            ${renderAssistantStatus(message)}
          </div>
        </div>
        <div class="qa-message__sources" data-role="message-sources">
          ${renderAssistantSources(message)}
        </div>
        <div class="qa-message__footer qa-message__footer--assistant">
          <span class="qa-message__time" data-role="message-time">${formatTime(message.timestamp)}</span>
        </div>
      </div>
    </article>
  `;
}

function renderMessage(message) {
  return message.role === "user" ? renderUserMessage(message) : renderAssistantMessage(message);
}

function renderMessages(messages) {
  if (messages.length === 0) {
    return `
      <div class="qa-welcome">
        <div class="qa-welcome__icon">💬</div>
        <h2>智能问答助手</h2>
        <p>基于企业知识库的 RAG 检索增强问答，支持多轮会话与实时返回。</p>
        <div class="qa-welcome__tips">
          <div class="qa-tip">
            <span class="qa-tip__icon">💡</span>
            <span>输入自然语言问题，系统会自动做改写、检索与引用标注。</span>
          </div>
          <div class="qa-tip">
            <span class="qa-tip__icon">🔒</span>
            <span>结果只会引用您有权访问的已发布知识内容。</span>
          </div>
        </div>
      </div>
    `;
  }

  return messages.map(renderMessage).join("");
}

function renderFilteredNotice(filteredCount) {
  if (filteredCount <= 0) return "";
  return `
    <div class="qa-filtered-notice">
      <span class="qa-filtered-notice__icon">🔒</span>
      <span>因权限限制，已过滤 ${filteredCount} 条敏感来源</span>
    </div>
  `;
}

function renderErrorNotice(error) {
  if (!error) return "";
  return `
    <div class="qa-error">
      <span>⚠️</span>
      <span>${escapeHtml(error)}</span>
    </div>
  `;
}

export function createDefaultQAState() {
  return {
    messages: [],
    inputText: "",
    loading: false,
    loadingSessions: false,
    sessionsLoaded: false,
    sessions: [],
    currentSessionId: null,
    error: "",
    webSearchEnabled: false,
    webSearchMenuOpen: false,
    filteredCount: 0,
    selectedSource: null,
    streamCancel: null,
    isAutoFollow: true,
    isStreaming: false,
    bottomThresholdPx: 64,
    pendingAssistantMessageId: null,
    rafScrollToken: 0
  };
}

export function renderQAPage(state) {
  return `
    <section class="qa-page" data-qa-root>
      <div class="qa-main">
        <aside class="qa-sidebar">
          <div class="qa-sidebar__header">
            <div class="qa-sidebar__heading">
              ${state.loadingSessions ? '<span class="qa-sidebar__loading">加载中...</span>' : ""}
            </div>
            <button
              type="button"
              class="btn btn--secondary btn--small qa-sidebar__new-session"
              data-action="new-session"
              aria-label="新建会话"
              title="新建会话"
            >
              <svg viewBox="0 0 20 20" aria-hidden="true">
                <path d="M5.5 6.5h6a3 3 0 0 1 0 6H10l-3 2v-2H6a3 3 0 0 1-.5-6Z"></path>
                <path d="M14.5 4.5v4"></path>
                <path d="M12.5 6.5h4"></path>
              </svg>
            </button>
          </div>
          <div id="qaSessionList">
            ${renderSessionList(state)}
          </div>
        </aside>

        <div class="qa-chat">
          <div class="qa-messages-shell">
            <div class="qa-messages" id="qaMessages">
              ${renderMessages(state.messages)}
            </div>
          </div>

          <div class="qa-chat-status">
            <div id="qaFilteredNotice">${renderFilteredNotice(state.filteredCount)}</div>
            <div id="qaErrorNotice">${renderErrorNotice(state.error)}</div>
          </div>

          <div class="qa-input-area">
            <div class="qa-input-box">
            
              // <div class="qa-input-toolbar">
              //   <div class="qa-input-toolbar__actions">
              //     <button
              //       type="button"
              //       class="qa-input-tool-btn ${state.webSearchMenuOpen ? "is-open" : ""}"
              //       data-action="toggle-search-menu"
              //       aria-label="打开增强工具"
              //     >+</button>
              //     ${state.webSearchEnabled ? '<span class="qa-input-tool-indicator" title="联网搜索已开启">🌐</span>' : ""}
              //     ${state.webSearchMenuOpen ? `
              //       <div class="qa-input-tool-menu">
              //         <button
              //           type="button"
              //           class="qa-input-tool-option ${state.webSearchEnabled ? "is-active" : ""}"
              //           data-action="toggle-web-search"
              //         >联网搜索</button>
              //       </div>
              //     ` : ""}
              //   </div>
              // </div>
              <textarea
                id="qaInput"
                placeholder="输入您的问题，按 Enter 发送..."
                rows="${QA_INPUT_MIN_ROWS}"
                maxlength="2000"
              >${escapeHtml(state.inputText)}</textarea>
            </div>
            <div class="qa-input-hint">
              <span>按 Enter 发送，Option+Enter 换行</span>
              <span id="qaSessionHint">${state.currentSessionId ? "已连接当前会话" : "将自动创建会话"}</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  `;
}

async function ensureSessionLoaded(state, draw) {
  if (typeof QAService.listSessions !== "function") {
    state.sessionsLoaded = true;
    return;
  }
  if (state.sessionsLoaded || state.loadingSessions) return;

  state.loadingSessions = true;
  draw();

  const sessionsResult = await QAService.listSessions();
  if (sessionsResult.ok) {
    state.sessions = sessionsResult.data || [];
    if (state.sessions.length > 0 && !state.currentSessionId) {
      state.currentSessionId = state.sessions[0].sessionId;
      const messagesResult = typeof QAService.getSessionMessages === "function"
        ? await QAService.getSessionMessages(state.currentSessionId)
        : { ok: true, data: [] };
      if (messagesResult.ok) {
        state.messages = messagesResult.data || [];
      }
    }
  }

  state.loadingSessions = false;
  state.sessionsLoaded = true;
  state.isAutoFollow = true;
  draw();
}

export function setupQAPage(content, state, draw) {
  let root = content.querySelector("[data-qa-root]");
  if (!root) return;

  const newRoot = root.cloneNode(true);
  root.parentNode.replaceChild(newRoot, root);
  root = newRoot;

  const refs = {
    root,
    input: root.querySelector("#qaInput"),
    messagesContainer: root.querySelector("#qaMessages"),
    sessionList: root.querySelector("#qaSessionList"),
    filteredNotice: root.querySelector("#qaFilteredNotice"),
    errorNotice: root.querySelector("#qaErrorNotice"),
    sessionHint: root.querySelector("#qaSessionHint")
  };

  ensureSessionLoaded(state, draw);

  function cancelScheduledScroll() {
    if (state.rafScrollToken) {
      cancelAnimationFrame(state.rafScrollToken);
      state.rafScrollToken = 0;
    }
  }

  function resetRealtimeState() {
    cancelScheduledScroll();
    state.isStreaming = false;
    state.pendingAssistantMessageId = null;
    state.isAutoFollow = true;
    state.streamCancel = null;
  }

  function syncInputHeight() {
    if (!refs.input) return;
    refs.input.style.height = "auto";
    const minHeight = QA_INPUT_MIN_ROWS * QA_INPUT_LINE_HEIGHT;
    const maxHeight = QA_INPUT_MAX_ROWS * QA_INPUT_LINE_HEIGHT;
    refs.input.style.height = `${Math.min(Math.max(refs.input.scrollHeight, minHeight), maxHeight)}px`;
  }

  function getDistanceToBottom() {
    if (!refs.messagesContainer) return 0;
    return refs.messagesContainer.scrollHeight - refs.messagesContainer.clientHeight - refs.messagesContainer.scrollTop;
  }

  function refreshAutoFollow() {
    state.isAutoFollow = getDistanceToBottom() <= state.bottomThresholdPx;
  }

  function scheduleScrollToBottom(force = false) {
    if (!refs.messagesContainer) return;
    if (force) {
      state.isAutoFollow = true;
    }
    if (!state.isAutoFollow) {
      return;
    }
    if (state.rafScrollToken) {
      return;
    }

    state.rafScrollToken = requestAnimationFrame(() => {
      state.rafScrollToken = 0;
      if (!refs.messagesContainer) return;
      refs.messagesContainer.scrollTop = refs.messagesContainer.scrollHeight;
    });
  }

  function updateStatusArea() {
    if (refs.filteredNotice) {
      refs.filteredNotice.innerHTML = renderFilteredNotice(state.filteredCount);
    }
    if (refs.errorNotice) {
      refs.errorNotice.innerHTML = renderErrorNotice(state.error);
    }
  }

  function updateSessionUI() {
    if (refs.sessionList) {
      refs.sessionList.innerHTML = renderSessionList(state);
    }
    if (refs.sessionHint) {
      refs.sessionHint.textContent = state.currentSessionId ? "已连接当前会话" : "将自动创建会话";
    }
  }

  function renderMessagesList() {
    if (!refs.messagesContainer) return;
    refs.messagesContainer.innerHTML = renderMessages(state.messages);
    refs.messagesContainer.querySelectorAll('.qa-message--assistant').forEach((node) => {
      hydrateAssistantEnhancements(node);
    });
  }

  function findMessageElement(messageId) {
    if (!refs.messagesContainer || !messageId) return null;
    return refs.messagesContainer.querySelector(`[data-message-id="${escapeSelectorValue(messageId)}"]`);
  }

  function appendMessage(message) {
    if (!refs.messagesContainer) return;
    if (state.messages.length === 1 || refs.messagesContainer.querySelector(".qa-welcome")) {
      refs.messagesContainer.innerHTML = "";
    }
    refs.messagesContainer.insertAdjacentHTML("beforeend", renderMessage(message));
    if (message.role === "assistant") {
      const node = findMessageElement(message.id);
      hydrateAssistantEnhancements(node);
    }
  }

  function upsertMessageInDom(message) {
    const existing = findMessageElement(message.id);
    if (!existing) {
      appendMessage(message);
      return;
    }

    if (message.role === "user") {
      const replacement = document.createElement("div");
      replacement.innerHTML = renderUserMessage(message).trim();
      const nextNode = replacement.firstElementChild;
      if (nextNode) {
        existing.replaceWith(nextNode);
      }
      return;
    }

    const replacement = document.createElement("div");
    replacement.innerHTML = renderAssistantMessage(message).trim();
    const nextNode = replacement.firstElementChild;
    if (nextNode) {
      existing.replaceWith(nextNode);
      hydrateAssistantEnhancements(nextNode);
    }
  }

  function updateAssistantMessage(message) {
    const node = findMessageElement(message.id);
    if (!node) {
      upsertMessageInDom(message);
      return;
    }

    node.className = `qa-message qa-message--assistant ${message.pending ? "qa-message--pending" : ""}`.trim();

    const badges = node.querySelector('[data-role="message-badges"]');
    const badgesHtml = renderAssistantBadges(message);
    if (badgesHtml) {
      if (badges) {
        badges.outerHTML = badgesHtml;
      } else {
        node.querySelector(".qa-message__panel")?.insertAdjacentHTML("afterbegin", badgesHtml);
      }
    } else if (badges) {
      badges.remove();
    }

    const content = node.querySelector('[data-role="message-content"]');
    if (content) {
      content.innerHTML = renderAssistantContent(message);
    }

    const tooling = node.querySelector('[data-role="message-tooling"]');
    if (tooling) {
      tooling.innerHTML = `${renderAssistantToolTrace(message)}${renderAssistantDiagrams(message)}${renderExternalSources(message)}`;
    }

    const meta = node.querySelector(".qa-message__meta");
    if (meta) {
      const statusHtml = renderAssistantStatus(message);
      let statusNode = meta.querySelector(".qa-message__status");
      if (statusHtml) {
        if (statusNode) {
          statusNode.outerHTML = statusHtml;
        } else {
          meta.insertAdjacentHTML("afterbegin", statusHtml);
        }
      } else if (statusNode) {
        statusNode.remove();
      }
    }

    const time = node.querySelector('[data-role="message-time"]');
    if (time) {
      time.textContent = formatTime(message.timestamp);
    }

    const sources = node.querySelector('[data-role="message-sources"]');
    if (sources) {
      sources.innerHTML = renderAssistantSources(message);
    }

    hydrateAssistantEnhancements(node);
  }

  function hydrateAssistantEnhancements(node) {
    if (!node) return;
    hydrateMermaidDiagrams(node);
  }

  function prependOrMoveSession(sessionId) {
    const index = state.sessions.findIndex((item) => String(item.sessionId) === String(sessionId));
    if (index < 0) {
      return;
    }
    const [session] = state.sessions.splice(index, 1);
    state.sessions.unshift(session);
  }

  function upsertSessionMeta(sessionId) {
    const index = state.sessions.findIndex((item) => String(item.sessionId) === String(sessionId));
    if (index >= 0) {
      const existing = state.sessions[index];
      state.sessions.splice(index, 1);
      state.sessions.unshift({
        ...existing,
        lastMessageAt: new Date().toISOString()
      });
      updateSessionUI();
      return;
    }
    state.sessions.unshift({
      sessionId,
      title: "新对话",
      lastMessageAt: new Date().toISOString(),
      createdAt: new Date().toISOString()
    });
    updateSessionUI();
  }

  async function ensureCurrentSession() {
    if (state.currentSessionId) {
      return state.currentSessionId;
    }
    if (typeof QAService.createSession !== "function") {
      state.currentSessionId = `local_${Date.now()}`;
      updateSessionUI();
      return state.currentSessionId;
    }

    const sessionResult = await QAService.createSession();
    if (!sessionResult.ok) {
      throw new Error(sessionResult.message || "创建会话失败");
    }

    const session = sessionResult.data;
    state.currentSessionId = session.sessionId;
    state.sessions = [session, ...state.sessions.filter((item) => String(item.sessionId) !== String(session.sessionId))];
    updateSessionUI();
    return state.currentSessionId;
  }

  async function handleSendQuery() {
    const query = state.inputText.trim();
    if (!query || state.loading) return;

    state.error = "";
    updateStatusArea();

    let sessionId;
    try {
      sessionId = await ensureCurrentSession();
    } catch (error) {
      state.error = error.message || "创建会话失败";
      updateStatusArea();
      return;
    }

    const userMessage = {
      id: generateMessageId(),
      role: "user",
      content: query,
      timestamp: new Date().toISOString()
    };
    const assistantMessage = {
      id: generateMessageId(),
      role: "assistant",
      content: "",
      sources: [],
      toolTrace: [],
      diagrams: [],
      externalSources: [],
      planSummary: "",
      rewrittenQuery: "",
      timestamp: new Date().toISOString(),
      pending: true,
      degraded: false,
      degradeReason: "",
      fallbackMode: ""
    };

    state.messages.push(userMessage, assistantMessage);
    state.inputText = "";
    state.loading = true;
    state.isStreaming = typeof QAService.queryStream === "function";
    state.pendingAssistantMessageId = assistantMessage.id;
    const webSearchEnabled = state.webSearchEnabled;
    state.webSearchEnabled = false;
    state.webSearchMenuOpen = false;
    if (refs.input) {
      refs.input.value = "";
      syncInputHeight();
    }
    upsertSessionMeta(sessionId);
    appendMessage(userMessage);
    appendMessage(assistantMessage);
    scheduleScrollToBottom(true);

    const useStream = typeof QAService.queryStream === "function";
    if (!useStream) {
      const result = await QAService.query({ question: query, sessionId, topK: 5, webSearchEnabled });
      state.loading = false;
      state.isStreaming = false;
      state.pendingAssistantMessageId = null;
      state.streamCancel = null;

      if (!result.ok) {
        state.error = result.message || "查询失败";
        assistantMessage.pending = false;
        assistantMessage.content = assistantMessage.content || "本次问答未完成。";
        updateAssistantMessage(assistantMessage);
        updateStatusArea();
        scheduleScrollToBottom();
        return;
      }

      assistantMessage.content = result.data.answer || "";
      assistantMessage.sources = result.data.sources || [];
      assistantMessage.rewrittenQuery = result.data.rewrittenQuery || "";
      assistantMessage.intent = result.data.intent || "";
      assistantMessage.degraded = Boolean(result.data.degraded);
      assistantMessage.degradeReason = result.data.degradeReason || "";
      assistantMessage.fallbackMode = result.data.fallbackMode || "";
      assistantMessage.planSummary = result.data.planSummary || "";
      assistantMessage.toolTrace = result.data.toolTrace || [];
      assistantMessage.diagrams = result.data.diagrams || [];
      assistantMessage.externalSources = result.data.externalSources || [];
      assistantMessage.pending = false;
      updateAssistantMessage(assistantMessage);
      updateStatusArea();
      scheduleScrollToBottom(true);
      return;
    }

    state.streamCancel = QAService.queryStream(
      { question: query, sessionId, messageId: assistantMessage.id, webSearchEnabled },
      {
        onPlan(payload) {
          assistantMessage.planSummary = payload?.planSummary || "";
          updateAssistantMessage(assistantMessage);
          scheduleScrollToBottom();
        },
        onToolStart(trace) {
          if (!trace) return;
          assistantMessage.toolTrace = [...(assistantMessage.toolTrace || []), trace];
          updateAssistantMessage(assistantMessage);
          scheduleScrollToBottom();
        },
        onToolResult(trace) {
          if (!trace) return;
          assistantMessage.toolTrace = upsertToolTrace(assistantMessage.toolTrace, trace);
          updateAssistantMessage(assistantMessage);
          scheduleScrollToBottom();
        },
        onToolError(payload) {
          assistantMessage.toolTrace = upsertToolTrace(assistantMessage.toolTrace, {
            stepId: payload?.stepId || generateMessageId(),
            toolId: payload?.toolId || "",
            toolName: payload?.toolId || "工具",
            status: "FAILED",
            errorMessage: payload?.message || "工具执行失败"
          });
          updateAssistantMessage(assistantMessage);
          scheduleScrollToBottom();
        },
        onDiagram(diagram) {
          if (!diagram) return;
          assistantMessage.diagrams = [...(assistantMessage.diagrams || []), diagram];
          updateAssistantMessage(assistantMessage);
          scheduleScrollToBottom();
        },
        onChunk(contentValue) {
          assistantMessage.content = contentValue;
          updateAssistantMessage(assistantMessage);
          scheduleScrollToBottom();
        },
        onSources(sources) {
          assistantMessage.sources = sources || [];
          updateAssistantMessage(assistantMessage);
          scheduleScrollToBottom();
        },
        onDone(payload) {
          state.loading = false;
          state.isStreaming = false;
          state.pendingAssistantMessageId = null;
          state.streamCancel = null;
          assistantMessage.content = payload.answer || assistantMessage.content;
          assistantMessage.sources = payload.sources || assistantMessage.sources;
          assistantMessage.rewrittenQuery = payload.rewrittenQuery || "";
          assistantMessage.intent = payload.intent || "";
          assistantMessage.degraded = Boolean(payload.degraded);
          assistantMessage.degradeReason = payload.degradeReason || "";
          assistantMessage.fallbackMode = payload.fallbackMode || "";
          assistantMessage.planSummary = payload.planSummary || assistantMessage.planSummary || "";
          assistantMessage.toolTrace = payload.toolTrace || assistantMessage.toolTrace || [];
          assistantMessage.diagrams = payload.diagrams || assistantMessage.diagrams || [];
          assistantMessage.externalSources = payload.externalSources || assistantMessage.externalSources || [];
          assistantMessage.pending = false;

          if (payload.sessionId) {
            state.currentSessionId = payload.sessionId;
            prependOrMoveSession(payload.sessionId);
            updateSessionUI();
          }

          updateAssistantMessage(assistantMessage);
          updateStatusArea();
          scheduleScrollToBottom(true);
        },
        onError(message) {
          state.loading = false;
          state.isStreaming = false;
          state.pendingAssistantMessageId = null;
          state.streamCancel = null;
          assistantMessage.pending = false;
          assistantMessage.content = assistantMessage.content || "本次问答未完成。";
          state.error = message || "查询失败，请稍后重试";
          updateAssistantMessage(assistantMessage);
          updateStatusArea();
          scheduleScrollToBottom();
        }
      }
    );
  }

  if (refs.input) {
    syncInputHeight();
    refs.input.addEventListener("input", () => {
      state.inputText = refs.input.value;
      syncInputHeight();
    });

    refs.input.addEventListener("keydown", (event) => {
      if (event.key === "Enter" && !event.shiftKey && !event.altKey) {
        event.preventDefault();
        if (!state.loading && state.inputText.trim()) {
          handleSendQuery();
        }
      }
    });
  }

  refs.messagesContainer?.addEventListener("scroll", () => {
    refreshAutoFollow();
  });

  root.addEventListener("click", async (event) => {
    const target = event.target.closest("[data-action]");
    if (!target) return;

    const action = target.getAttribute("data-action");
    if (action === "new-session") {
      state.streamCancel?.();
      resetRealtimeState();
      state.messages = [];
      state.currentSessionId = null;
      state.inputText = "";
      state.error = "";
      state.webSearchEnabled = false;
      state.webSearchMenuOpen = false;
      draw();
      return;
    }

    if (action === "toggle-search-menu") {
      state.webSearchMenuOpen = !state.webSearchMenuOpen;
      draw();
      return;
    }

    if (action === "toggle-web-search") {
      state.webSearchEnabled = !state.webSearchEnabled;
      state.webSearchMenuOpen = false;
      draw();
      return;
    }

    if (action === "switch-session") {
      const sessionId = target.getAttribute("data-session-id");
      if (!sessionId || String(sessionId) === String(state.currentSessionId)) return;

      state.streamCancel?.();
      resetRealtimeState();
      state.currentSessionId = sessionId;
      state.loading = true;
      state.error = "";
      draw();

      const result = typeof QAService.getSessionMessages === "function"
        ? await QAService.getSessionMessages(sessionId)
        : { ok: true, data: [] };

      state.loading = false;
      if (result.ok) {
        state.messages = result.data || [];
      } else {
        state.error = result.message || "会话加载失败";
      }
      draw();
    }
  });

  root.addEventListener("click", (event) => {
    const card = event.target.closest(".qa-source-card");
    if (card) {
      const docId = card.getAttribute("data-doc-id");
      if (docId) {
        window.location.hash = `#doc-view?docId=${encodeURIComponent(docId)}`;
      }
    }
  });

  updateStatusArea();
  updateSessionUI();
  if (state.messages.length > 0) {
    renderMessagesList();
    scheduleScrollToBottom(true);
  }
}

function upsertToolTrace(toolTrace = [], nextItem = null) {
  if (!nextItem?.stepId) {
    return Array.isArray(toolTrace) ? toolTrace : [];
  }
  const current = Array.isArray(toolTrace) ? [...toolTrace] : [];
  const index = current.findIndex((item) => String(item.stepId) === String(nextItem.stepId));
  if (index >= 0) {
    current[index] = { ...current[index], ...nextItem };
    return current;
  }
  current.push(nextItem);
  return current;
}
