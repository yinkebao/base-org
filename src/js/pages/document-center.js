import { DocumentService } from "../services/document-service.js";
import { escapeHtml } from "../utils/dom.js";
import { extractHeadingsFromMarkdown } from "../utils/markdown-headings.js";
import { createToastDocEditor } from "../utils/toast-doc-editor.js";

const EXPANDED_NODE_STORAGE_KEY = "docs-expanded-node-ids";
const DOC_AUTOSAVE_INTERVAL = 30_000;
let markdownRendererPromise = null;

function isBrowser() {
  return typeof window !== "undefined";
}

function getHashQuery() {
  if (!isBrowser()) return new URLSearchParams();
  const hash = window.location.hash.replace("#", "");
  const queryString = hash.includes("?") ? hash.slice(hash.indexOf("?") + 1) : "";
  return new URLSearchParams(queryString);
}

function getRouteDocId() {
  return String(getHashQuery().get("docId") || "").trim();
}

function getRouteFolderId() {
  return String(getHashQuery().get("folderId") || "").trim();
}

export function getDocumentRouteModeFromHash(hash) {
  const normalized = String(hash || "");
  const cleanHash = normalized.replace(/^#/, "");
  const queryString = cleanHash.includes("?") ? cleanHash.slice(cleanHash.indexOf("?") + 1) : "";
  return String(new URLSearchParams(queryString).get("mode") || "").trim();
}

function getRouteMode() {
  if (!isBrowser()) return "";
  return getDocumentRouteModeFromHash(window.location.hash);
}

function attachHeadingIds(html, headings) {
  let result = String(html || "");
  headings.forEach((heading) => {
    result = result.replace(/<h([1-3])>/, `<h$1 id="${heading.id}">`);
  });
  return result;
}

function toExpandedSet(values) {
  if (values instanceof Set) return values;
  return new Set(Array.isArray(values) ? values : []);
}

function persistExpandedNodeIds(expandedNodeIds) {
  if (!isBrowser() || !window.sessionStorage) return;
  const value = JSON.stringify(Array.from(toExpandedSet(expandedNodeIds)));
  window.sessionStorage.setItem(EXPANDED_NODE_STORAGE_KEY, value);
}

function readExpandedNodeIds() {
  const fallback = ["folder-kb", "folder-tech"];
  if (!isBrowser() || !window.sessionStorage) return new Set(fallback);
  const raw = window.sessionStorage.getItem(EXPANDED_NODE_STORAGE_KEY);
  if (!raw) return new Set(fallback);
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return new Set(fallback);
    return new Set(parsed);
  } catch (_error) {
    return new Set(fallback);
  }
}

function nodeMap(nodes) {
  const map = new Map();
  nodes.forEach((node) => map.set(node.id, node));
  return map;
}

function visibleNodeSet(nodes) {
  const set = new Set();
  nodes.forEach((node) => {
    set.add(node.id);
    if (node.type === "doc" && node.docId) {
      set.add(node.docId);
    }
  });
  return set;
}

function findTreeNodeByReference(map, nodes, nodeRef) {
  if (map.has(nodeRef)) {
    return map.get(nodeRef) || null;
  }
  return nodes.find((node) => node.type === "doc" && node.docId === nodeRef) || null;
}

function hasVisibleChildren(folder, visibleNodeSet) {
  return folder.childrenIds.some((id) => visibleNodeSet.has(id));
}

function renderTree(nodes, expandedNodeIds, activeDocId, creatingFolderId) {
  const map = nodeMap(nodes);
  const visibleNodes = visibleNodeSet(nodes);

  function renderNode(nodeId, depth = 0) {
    const node = findTreeNodeByReference(map, nodes, nodeId);
    if (!node) return "";

    if (node.type === "doc") {
      const activeClass = node.docId === activeDocId ? "is-active" : "";
      return `
        <li class="docs-tree-node docs-tree-node--doc ${activeClass}" style="--tree-depth:${depth}">
          <div class="docs-tree-node__row docs-tree-node__row--doc">
            <button type="button" class="docs-tree-node__doc-button" data-action="open-doc" data-doc-id="${escapeHtml(node.docId)}">
              <span class="docs-tree-node__glyph docs-tree-node__glyph--doc" aria-hidden="true"></span>
              <span class="docs-tree-node__label">${escapeHtml(node.name)}</span>
            </button>
            <div class="docs-tree-node__actions">
              <button
                type="button"
                class="docs-tree-action-btn docs-tree-edit-btn"
                data-action="edit-doc"
                data-doc-id="${escapeHtml(node.docId)}"
                aria-label="编辑 ${escapeHtml(node.name)}"
              ><span aria-hidden="true">✎</span></button>
            </div>
          </div>
        </li>
      `;
    }

    const children = node.childrenIds.filter((childId) => visibleNodes.has(childId));
    const expandable = hasVisibleChildren(node, visibleNodes);
    const expanded = expandedNodeIds.has(node.id);
    const folderClass = expanded ? "is-expanded" : "";
    const creating = creatingFolderId === node.id;

    return `
      <li class="docs-tree-node docs-tree-node--folder ${folderClass}" style="--tree-depth:${depth}">
        <div class="docs-tree-node__row">
          <button type="button" class="docs-tree-node__toggle" data-action="toggle-folder" data-node-id="${escapeHtml(node.id)}" ${
            expandable ? "" : "disabled"
          } aria-label="${expanded ? "收起" : "展开"} ${escapeHtml(node.name)}">
            <span class="docs-tree-node__caret" aria-hidden="true">${expanded ? "▾" : "▸"}</span>
          </button>
          <button type="button" class="docs-tree-node__folder-button" data-action="open-folder" data-node-id="${escapeHtml(node.id)}">
            <span class="docs-tree-node__glyph docs-tree-node__glyph--folder" aria-hidden="true"></span>
            <span class="docs-tree-node__label">${escapeHtml(node.name)}</span>
          </button>
          <div class="docs-tree-node__actions">
            <button
              type="button"
              class="docs-tree-action-btn docs-tree-create-btn ${creating ? "is-loading" : ""}"
              data-action="create-doc"
              data-node-id="${escapeHtml(node.id)}"
              aria-label="在 ${escapeHtml(node.name)} 下新建文档"
              ${creatingFolderId ? "disabled" : ""}
            >${creating ? "..." : "+"}</button>
          </div>
        </div>
        ${
          expandable && expanded
            ? `<ul class="docs-tree-children">${children.map((childId) => renderNode(childId, depth + 1)).join("")}</ul>`
            : ""
        }
      </li>
    `;
  }

  const roots = nodes.filter((node) => node.parentId === null).map((node) => node.id);
  return `<ul class="docs-tree-root">${roots.map((id) => renderNode(id, 0)).join("")}</ul>`;
}

function buildFolderPath(nodes, folderId) {
  if (!folderId) return [];
  const map = nodeMap(nodes);
  const result = [];
  const visited = new Set();
  let current = map.get(folderId);

  while (current && current.type === "folder" && !visited.has(current.id)) {
    visited.add(current.id);
    result.unshift({ id: current.id, name: current.name, type: "folder" });
    current = current.parentId ? map.get(current.parentId) : null;
  }
  return result;
}

function renderBreadcrumb(pathItems, docTitle = "") {
  if (!pathItems.length) {
    return docTitle
      ? `<span>文档管理</span><span class="docs-breadcrumb-sep">›</span><span data-doc-breadcrumb-title>${escapeHtml(docTitle)}</span>`
      : `<span>文档管理</span>`;
  }
  const segments = [];
  pathItems.forEach((item) => {
    segments.push(
      `<a href="#docs?folderId=${escapeHtml(item.id)}" data-action="crumb-folder" data-folder-id="${escapeHtml(item.id)}">${escapeHtml(
        item.name
      )}</a>`
    );
  });
  if (docTitle) {
    segments.push(`<span data-doc-breadcrumb-title>${escapeHtml(docTitle)}</span>`);
  }
  return segments.join('<span class="docs-breadcrumb-sep">›</span>');
}

function renderDocumentListPanel(state, keyword) {
  const lowerKeyword = String(keyword || "").trim().toLowerCase();
  const filtered = state.list.filter((item) => {
    if (!lowerKeyword) return true;
    const target = `${item.title} ${item.dept} ${item.folderPathNames.join(" ")}`.toLowerCase();
    return target.includes(lowerKeyword);
  });
  if (filtered.length === 0) {
    return `<div class="empty-state">暂无符合条件的文档</div>`;
  }
  return `
    <div class="docs-list-grid">
      ${filtered
        .map(
          (doc) => `
            <article class="docs-list-card">
              <header>
                <div>
                  <span class="docs-list-card__eyebrow">${escapeHtml(doc.dept)}</span>
                  <h3>${escapeHtml(doc.title)}</h3>
                </div>
                <span class="docs-list-card__status">${escapeHtml(doc.status)}</span>
              </header>
              <p class="docs-list-card__path">${escapeHtml(doc.folderPathNames.join(" / "))}</p>
              <footer>
                <span>最近更新 ${escapeHtml(doc.updatedAt)}</span>
                <button type="button" data-action="open-doc" data-doc-id="${escapeHtml(doc.docId)}">查看详情</button>
              </footer>
            </article>
          `
        )
        .join("")}
    </div>
  `;
}

function renderDocumentSearchBar(keyword) {
  return `
    <div class="docs-search">
      <input
        id="docsSearchInput"
        type="search"
        value="${escapeHtml(String(keyword || ""))}"
        placeholder="搜索文档标题、目录或部门..."
        autocomplete="off"
      />
    </div>
  `;
}

function toDocumentListItem(doc) {
  return {
    docId: doc.docId,
    title: doc.title,
    dept: doc.dept,
    visibility: doc.visibility,
    scope: doc.scope,
    status: doc.status,
    updatedBy: doc.updatedBy,
    updatedAt: doc.updatedAt,
    folderPathIds: doc.folderPathIds,
    folderPathNames: doc.folderPathNames
  };
}

function renderMarkdownSection(state, doc) {
  const headings = extractHeadingsFromMarkdown(doc.contentMarkdown);
  const html = attachHeadingIds(state.currentDocHtml || "", headings);
  return {
    html,
    headings
  };
}

async function loadMarkdownRenderer() {
  if (!markdownRendererPromise) {
    markdownRendererPromise = import("../utils/markdown-renderer.js");
  }
  return markdownRendererPromise;
}

async function renderDocumentMarkdownHtml(markdownText) {
  try {
    const renderer = await loadMarkdownRenderer();
    return renderer.renderMarkdownToHtml(markdownText);
  } catch (_error) {
    return `<pre>${escapeHtml(String(markdownText || ""))}</pre>`;
  }
}

function normalizeMarkdownValue(markdown) {
  return String(markdown || "")
    .replace(/\r\n/g, "\n")
    .replace(/\u00a0/g, " ")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

function renderEditorToolbar(state) {
  return `
    <section class="docs-editor-toolbar docs-editor-toolbar--toast">
      <div class="docs-editor-toolbar__hint">
        <strong>TOAST UI Editor</strong>
        <span>使用编辑器原生工具栏处理标题、表格、图片、链接和代码块</span>
      </div>
      <div id="docEditorUploadStatus" class="docs-editor-upload-status ${state.imageUploadError ? "is-error" : ""}" ${
        state.imageUploadError || state.uploadingImage ? "" : "hidden"
      }>
        ${escapeHtml(state.imageUploadError || (state.uploadingImage ? "图片上传中..." : ""))}
      </div>
    </section>
  `;
}

function renderTocItems(headings) {
  if (!Array.isArray(headings) || headings.length === 0) {
    return `<li class="depth-1"><span class="docs-toc__empty">当前正文暂无 h1-h3 标题</span></li>`;
  }
  return headings
    .map(
      (heading) =>
        `<li class="depth-${heading.depth}"><button type="button" data-action="jump-heading" data-heading-id="${escapeHtml(
          heading.id
        )}">${escapeHtml(heading.text)}</button></li>`
    )
    .join("");
}

function getSaveStatusText(state) {
  if (state.saving) return "保存中...";
  if (state.saveError) return `保存失败：${state.saveError}`;
  if (state.dirty) return "有未保存修改，系统将自动保存";
  if (state.lastSavedAt) return `已保存 · ${state.lastSavedAt}`;
  return "自动保存每 30 秒执行一次";
}

function getSaveStatusClass(state) {
  if (state.saveError) return "is-error";
  if (state.saving) return "is-saving";
  if (state.dirty) return "is-dirty";
  return "is-saved";
}

function cleanupOutlineInteractions(state) {
  if (typeof state.outlineCleanup === "function") {
    state.outlineCleanup();
    state.outlineCleanup = null;
  }
}

function clearDocumentAutoSave(state) {
  if (state.autoSaveTimer) {
    clearInterval(state.autoSaveTimer);
    state.autoSaveTimer = null;
  }
}

function destroyDocumentEditor(state) {
  if (state.editorController && typeof state.editorController.destroy === "function") {
    state.editorController.destroy();
  }
  state.editorController = null;
  state.editorBootstrapping = false;
  state.editorMountToken = (state.editorMountToken || 0) + 1;
}

function updateTocUi(state) {
  const tocList = state.mainEl?.querySelector("#docsTocList");
  if (tocList instanceof HTMLElement) {
    tocList.innerHTML = renderTocItems(state.editHeadings);
  }
}

function updateEditorAuxUi(state) {
  const mainRoot = state.mainEl;
  if (!(mainRoot instanceof HTMLElement)) return;

  const uploadStatus = mainRoot.querySelector("#docEditorUploadStatus");
  if (uploadStatus instanceof HTMLElement) {
    const message = state.imageUploadError || (state.uploadingImage ? "图片上传中..." : "");
    uploadStatus.hidden = !message;
    uploadStatus.classList.toggle("is-error", Boolean(state.imageUploadError));
    uploadStatus.textContent = message;
  }
}

function syncDirtyFlagFromState(state) {
  if (!state.currentDoc) return;
  if (state.titleInputEl instanceof HTMLInputElement) {
    state.editTitle = state.titleInputEl.value.trim() || state.currentDoc.title;
  }
  if (state.editorController) {
    state.editMarkdown = state.editorController.getMarkdown();
  }
  state.dirty =
    state.editTitle !== state.currentDoc.title ||
    normalizeMarkdownValue(state.editMarkdown) !== normalizeMarkdownValue(state.savedEditMarkdown);
  if (state.dirty) {
    state.saveError = "";
  }
  updateSaveUi(state);
  updateEditorAuxUi(state);
}

function handleEditorStateChange(state, nextState) {
  state.editMarkdown = nextState.markdown;
  state.editHeadings = nextState.headings;
  updateTocUi(state);
  syncDirtyFlagFromState(state);
}

async function ensureDocumentEditor(state) {
  if (!(state.editorEl instanceof HTMLElement) || state.editorController || state.editorBootstrapping) return;

  const host = state.editorEl;
  const mountToken = (state.editorMountToken || 0) + 1;
  state.editorMountToken = mountToken;
  state.editorBootstrapping = true;

  const controller = await createToastDocEditor({
    element: host,
    markdown: state.editMarkdown,
    placeholder: "请输入正文",
    onChange: (editorState) => handleEditorStateChange(state, editorState),
    onUploadImage: (file) => uploadEditorImage(state, file)
  }).catch((error) => {
    state.imageUploadError = error?.message || "编辑器初始化失败";
    updateEditorAuxUi(state);
    return null;
  });

  state.editorBootstrapping = false;

  if (!controller) {
    return;
  }

  if (state.detailMode !== "edit" || state.editorEl !== host || state.editorMountToken !== mountToken) {
    controller.destroy();
    return;
  }

  state.editorController = controller;
  state.editMarkdown = controller.getMarkdown();
  state.savedEditMarkdown = state.savedEditMarkdown || state.editMarkdown;
  state.editHeadings = extractHeadingsFromMarkdown(state.editMarkdown).map((heading) => ({
    ...heading,
    id: heading.id.replace("md-heading", "doc-edit-heading")
  }));
  updateTocUi(state);
  updateEditorAuxUi(state);
}

async function uploadEditorImage(state, file) {
  if (!state.currentDoc || !file) return;
  state.uploadingImage = true;
  state.imageUploadError = "";
  updateEditorAuxUi(state);

  const alt = String(file.name || "文档图片").replace(/\.[^.]+$/, "") || "文档图片";
  const resp = await DocumentService.uploadDocumentImage(state.currentDoc.docId, file, alt);
  state.uploadingImage = false;

  if (!resp.ok) {
    state.imageUploadError = resp.message || "图片上传失败";
    updateEditorAuxUi(state);
    return null;
  }

  state.imageUploadError = "";
  updateEditorAuxUi(state);
  return resp.data;
}

function syncEditorStateFromCurrentDoc(state) {
  if (!state.currentDoc) {
    resetDocumentEditorState(state);
    return;
  }
  state.editingDocId = state.currentDoc.docId;
  state.editTitle = state.currentDoc.title;
  state.editMarkdown = normalizeMarkdownValue(state.currentDoc.contentMarkdown);
  state.editHeadings = extractHeadingsFromMarkdown(state.currentDoc.contentMarkdown).map((heading) => ({
    ...heading,
    id: heading.id.replace("md-heading", "doc-edit-heading")
  }));
  state.dirty = false;
  state.saving = false;
  state.saveError = "";
  state.lastSavedAt = state.currentDoc.updatedAt;
  state.savedEditMarkdown = normalizeMarkdownValue(state.currentDoc.contentMarkdown);
  state.imageUploadError = "";
  state.uploadingImage = false;
}

function resetDocumentEditorState(state) {
  destroyDocumentEditor(state);
  state.detailMode = "view";
  state.currentDocHtml = "";
  state.editingDocId = "";
  state.editTitle = "";
  state.editMarkdown = "";
  state.savedEditMarkdown = "";
  state.editHeadings = [];
  state.dirty = false;
  state.saving = false;
  state.saveError = "";
  state.lastSavedAt = "";
  state.creatingFolderId = "";
  state.pendingAutoEditDocId = "";
  state.outlineOpen = false;
  state.outlinePinnedByHover = false;
  state.imageUploadError = "";
  state.uploadingImage = false;
}

export function disposeDocumentRoute(state) {
  cleanupOutlineInteractions(state);
  clearDocumentAutoSave(state);
  resetDocumentEditorState(state);
  state.shellMounted = false;
  state.rootEl = null;
  state.sidebarEl = null;
  state.mainEl = null;
  state.editorEl = null;
  state.titleInputEl = null;
  state.activeRoute = "";
  state.activeKeyword = "";
}

function renderDocDetailPanel(state) {
  if (state.loadingDetail) {
    return `
      <section class="docs-main-panel">
        <div class="skeleton skeleton--title"></div>
        <div class="skeleton"></div>
        <div class="skeleton"></div>
        <div class="skeleton"></div>
      </section>
    `;
  }
  if (!state.currentDoc) {
    return `<section class="docs-main-panel"><div class="empty-state">未找到文档详情</div></section>`;
  }

  const isEditMode = state.detailMode === "edit";
  const markdown = renderMarkdownSection(state, state.currentDoc);
  const tocItems = isEditMode ? renderTocItems(state.editHeadings) : renderTocItems(markdown.headings);
  const docTitle = isEditMode ? state.editTitle || state.currentDoc.title : state.currentDoc.title;
  const outlineClass = state.outlineOpen ? "is-open" : "";

  return `
    <section class="docs-main-panel">
      ${state.notice ? `<div class="docs-notice">${escapeHtml(state.notice)}</div>` : ""}
      <header class="docs-toolbar">
        <nav class="docs-breadcrumb">${renderBreadcrumb(state.breadcrumbFolders, docTitle)}</nav>
        <div class="docs-toolbar-actions">
          ${
            isEditMode
              ? `
                <span id="docSaveStatus" class="docs-save-status ${getSaveStatusClass(state)}">${escapeHtml(getSaveStatusText(state))}</span>
                <button type="button" data-action="doc-cancel-edit">取消编辑</button>
                <button type="button" data-action="doc-save" class="is-primary" ${state.saving ? "disabled" : ""}>保存</button>
              `
              : `
                <span class="docs-toolbar-meta">最后修改: <span data-doc-updated-by>${escapeHtml(state.currentDoc.updatedBy)}</span> · <span data-doc-updated-at>${escapeHtml(
                  state.currentDoc.updatedAt
                )}</span></span>
                <button type="button" data-action="doc-edit" class="is-primary">编辑文档</button>
              `
          }
        </div>
      </header>

      <div class="docs-content-layout">
        <article class="docs-article-shell ${isEditMode ? "is-editing" : "is-reading"}">
          <div class="docs-article-topline">
            <span class="docs-article-kicker">${isEditMode ? "编辑中" : "文档详情"}</span>
            <div class="docs-badges">
              <span>${escapeHtml(state.currentDoc.scope)}</span>
              <span>${escapeHtml(state.currentDoc.visibility)}</span>
            </div>
          </div>
          ${
            isEditMode
              ? `<input id="docEditTitle" class="docs-title-input" value="${escapeHtml(
                  state.editTitle
                )}" maxlength="120" placeholder="请输入标题" />`
              : `<h1 class="docs-article-title">${escapeHtml(state.currentDoc.title)}</h1>`
          }
          <p class="docs-article-caption">
            所属部门：${escapeHtml(state.currentDoc.dept)} · 状态：${escapeHtml(state.currentDoc.status)} · 最近修改：
            <span data-doc-updated-by>${escapeHtml(state.currentDoc.updatedBy)}</span> ·
            <span data-doc-updated-at>${escapeHtml(state.currentDoc.updatedAt)}</span>
          </p>
          ${
            isEditMode
              ? `
                ${renderEditorToolbar(state)}
                <div
                  id="docEditorSurface"
                  class="docs-editor-surface docs-editor-surface--toast"
                  spellcheck="false"
                  data-placeholder="请输入正文"
                ></div>
              `
              : `<div class="docs-markdown-body" id="docsMarkdownBody">${markdown.html}</div>`
          }
        </article>
        <div id="docsOutlineShell" class="docs-outline-shell ${outlineClass}">
          <button
            id="docsOutlineTrigger"
            type="button"
            class="docs-outline-trigger"
            data-action="toggle-outline"
            aria-expanded="${state.outlineOpen ? "true" : "false"}"
            aria-label="显示大纲"
          >
            <span class="docs-outline-trigger__lines" aria-hidden="true">
              <span></span>
              <span></span>
              <span></span>
            </span>
          </button>
          <aside id="docsOutlineDrawer" class="docs-outline-drawer" aria-hidden="${state.outlineOpen ? "false" : "true"}">
            <h4>大纲</h4>
            <ul id="docsTocList">${tocItems}</ul>
          </aside>
        </div>
      </div>
    </section>
  `;
}

function renderDocumentLeftPanel(state) {
  const deptOptions = state.departments
    .map(
      (dept) =>
        `<option value="${escapeHtml(dept)}" ${state.deptFilter === dept ? "selected" : ""}>${escapeHtml(dept)}</option>`
    )
    .join("");
  return `
    <aside class="docs-side-panel">
      <div class="docs-side-panel__filters">
        <select id="docsDeptSelect">${deptOptions}</select>
      </div>
      <div class="docs-tree-panel">
        <nav class="docs-tree-wrap">
          ${renderTree(state.tree, state.expandedNodeIds, state.currentDocId, state.creatingFolderId)}
        </nav>
      </div>
    </aside>
  `;
}

export function createDefaultDocumentState() {
  return {
    initialized: false,
    loadingDetail: false,
    error: "",
    notice: "",
    departments: ["全部部门"],
    deptFilter: "全部部门",
    tree: [],
    list: [],
    expandedNodeIds: readExpandedNodeIds(),
    folderFilter: "",
    currentDocId: "",
    currentDoc: null,
    currentDocHtml: "",
    breadcrumbFolders: [],
    detailMode: "view",
    editingDocId: "",
    editTitle: "",
    editMarkdown: "",
    savedEditMarkdown: "",
    editHeadings: [],
    dirty: false,
    saving: false,
    autoSaveTimer: null,
    lastSavedAt: "",
    saveError: "",
    editorController: null,
    editorBootstrapping: false,
    editorMountToken: 0,
    uploadingImage: false,
    imageUploadError: "",
    creatingFolderId: "",
    pendingAutoEditDocId: "",
    outlineOpen: false,
    outlinePinnedByHover: false,
    outlineCleanup: null,
    shellMounted: false,
    rootEl: null,
    sidebarEl: null,
    mainEl: null,
    activeRoute: "",
    activeKeyword: "",
    lastLoadedDeptFilter: "",
    lastLoadedKeyword: "",
    lastLoadedFolderId: "",
    forceTreeReload: false
  };
}

async function ensureDocumentTreeAndList(state, route, keyword, force = false) {
  const targetRoute = route === "doc-view" ? "doc-view" : "docs";
  const folderId = targetRoute === "docs" ? getRouteFolderId() : state.lastLoadedFolderId || "";
  const shouldReloadTree =
    force ||
    state.forceTreeReload ||
    !state.initialized ||
    state.lastLoadedDeptFilter !== state.deptFilter;
  const shouldReloadList =
    shouldReloadTree ||
    !state.initialized ||
    state.lastLoadedKeyword !== keyword ||
    (targetRoute === "docs" && state.lastLoadedFolderId !== folderId);

  if (!shouldReloadTree && !shouldReloadList) {
    return false;
  }

  if (shouldReloadTree) {
    const treeResp = await DocumentService.getDocumentTree(state.deptFilter);
    if (!treeResp.ok) {
      state.error = treeResp.message || "文档目录加载失败";
      return false;
    }
    state.tree = treeResp.data.nodes;
    state.departments = treeResp.data.departments;
    state.expandedNodeIds = toExpandedSet(state.expandedNodeIds);
  }

  if (shouldReloadList) {
    const listResp = await DocumentService.getDocumentList({
      dept: state.deptFilter,
      folderId,
      keyword
    });
    if (!listResp.ok) {
      state.error = listResp.message || "文档列表加载失败";
      return false;
    }
    state.list = listResp.data;
    state.lastLoadedKeyword = keyword;
    state.lastLoadedFolderId = folderId;
  }

  state.error = "";
  state.initialized = true;
  state.lastLoadedDeptFilter = state.deptFilter;
  state.forceTreeReload = false;
  return shouldReloadTree;
}

async function ensureCurrentDocumentDetail(state, route) {
  if (route !== "doc-view") {
    return false;
  }
  const docId = getRouteDocId();
  const routeMode = getRouteMode();
  const shouldKeepEditorState = state.detailMode === "edit" && state.editingDocId === docId && state.editMarkdown;
  const needsReload = state.currentDocId !== docId || !state.currentDoc;
  state.currentDocId = docId;
  if (!docId) {
    state.currentDoc = null;
    state.currentDocHtml = "";
    state.notice = "未指定文档 ID，请从文档列表重新进入。";
    state.breadcrumbFolders = [];
    clearDocumentAutoSave(state);
    resetDocumentEditorState(state);
    return true;
  }

  if (!needsReload) {
    if (routeMode === "edit") {
      state.detailMode = "edit";
      state.editingDocId = docId;
      state.pendingAutoEditDocId = "";
      if (isBrowser() && window.history?.replaceState) {
        const nextHash = `#doc-view?docId=${encodeURIComponent(docId)}`;
        window.history.replaceState(null, "", `${window.location.pathname}${nextHash}`);
      }
      return true;
    }
    return false;
  }

  state.loadingDetail = true;
  const [detailResp, breadcrumbResp] = await Promise.all([
    DocumentService.getDocumentDetail(docId),
    DocumentService.getDocumentBreadcrumb(docId)
  ]);
  state.loadingDetail = false;
  if (!detailResp.ok) {
    state.currentDoc = null;
    state.currentDocHtml = "";
    state.notice = detailResp.message || "文档详情加载失败";
    state.breadcrumbFolders = [];
    clearDocumentAutoSave(state);
    resetDocumentEditorState(state);
    return true;
  }

  state.currentDoc = detailResp.data;
  state.currentDocHtml = await renderDocumentMarkdownHtml(detailResp.data.contentMarkdown);
  state.breadcrumbFolders = breadcrumbResp.ok ? breadcrumbResp.data.filter((item) => item.type === "folder") : [];
  state.folderFilter = state.currentDoc.folderPathIds[state.currentDoc.folderPathIds.length - 1] || "";
  const expanded = toExpandedSet(state.expandedNodeIds);
  state.currentDoc.folderPathIds.forEach((id) => expanded.add(id));
  state.expandedNodeIds = expanded;
  persistExpandedNodeIds(state.expandedNodeIds);

  if (!shouldKeepEditorState) {
    syncEditorStateFromCurrentDoc(state);
  }

  if (routeMode === "edit") {
    state.detailMode = "edit";
    state.editingDocId = docId;
    state.pendingAutoEditDocId = "";
    if (isBrowser() && window.history?.replaceState) {
      const nextHash = `#doc-view?docId=${encodeURIComponent(docId)}`;
      window.history.replaceState(null, "", `${window.location.pathname}${nextHash}`);
    }
  }
  return true;
}

export async function ensureDocumentData(state, route, keyword) {
  const targetRoute = route === "doc-view" ? "doc-view" : "docs";
  state.notice = "";
  const folderId = getRouteFolderId();

  const treeChanged = await ensureDocumentTreeAndList(state, targetRoute, keyword);
  if (state.error) return { treeChanged, detailChanged: false };

  if (targetRoute === "docs") {
    clearDocumentAutoSave(state);
    resetDocumentEditorState(state);
    state.folderFilter = folderId;
    state.currentDocId = "";
    state.currentDoc = null;
    state.currentDocHtml = "";
    state.breadcrumbFolders = buildFolderPath(state.tree, state.folderFilter);
    return { treeChanged, detailChanged: true };
  }

  const detailChanged = await ensureCurrentDocumentDetail(state, targetRoute);
  return { treeChanged, detailChanged };
}

export function renderDocumentShell() {
  return `
    <section class="docs-layout">
      <aside class="docs-layout__sidebar" data-docs-sidebar></aside>
      <section class="docs-layout__main" data-docs-main></section>
    </section>
  `;
}

export function renderDocumentSidebar(state) {
  if (state.error) {
    return `<section class="panel-card panel-card--single"><h2>加载失败</h2><p>${escapeHtml(state.error)}</p></section>`;
  }
  return renderDocumentLeftPanel(state);
}

export function renderDocumentMain(route, state, keyword) {
  if (state.error) {
    return `<section class="panel-card panel-card--single"><h2>加载失败</h2><p>${escapeHtml(state.error)}</p></section>`;
  }

  if (route === "doc-view") {
    return renderDocDetailPanel(state);
  }

  return `
    <section class="docs-main-panel">
      ${state.notice ? `<div class="docs-notice">${escapeHtml(state.notice)}</div>` : ""}
      <header class="docs-toolbar">
        <nav class="docs-breadcrumb">${renderBreadcrumb(state.breadcrumbFolders, "文档列表")}</nav>
        <div class="docs-toolbar-actions">
          <span class="docs-toolbar-meta">共 ${escapeHtml(String(state.list.length))} 篇文档</span>
          <button type="button" data-action="go-imports">导入新文档</button>
        </div>
      </header>
      ${renderDocumentSearchBar(keyword)}
      <section class="docs-title-block docs-title-block--list">
        <span class="docs-title-block__eyebrow">文档管理中心</span>
        <h1>统一浏览文档目录、详情与编辑状态</h1>
        <p>当前筛选：${escapeHtml(state.deptFilter)}。目录树、详情页和新建文档入口保持同一条交互链路。</p>
      </section>
      ${renderDocumentListPanel(state, keyword)}
    </section>
  `;
}

export function renderDocumentRoute(route, state, keyword) {
  return `<section class="docs-layout">${renderDocumentSidebar(state)}${renderDocumentMain(route, state, keyword)}</section>`;
}

function syncDocumentDomRefs(state) {
  const mainRoot = state.mainEl;
  if (!(mainRoot instanceof HTMLElement)) {
    state.editorEl = null;
    state.titleInputEl = null;
    return;
  }
  state.editorEl = mainRoot.querySelector("#docEditorSurface");
  state.titleInputEl = mainRoot.querySelector("#docEditTitle");
}

function updateSaveUi(state) {
  const mainRoot = state.mainEl;
  if (!(mainRoot instanceof HTMLElement)) return;
  const saveStatus = mainRoot.querySelector("#docSaveStatus");
  const saveButton = mainRoot.querySelector('[data-action="doc-save"]');
  if (saveStatus) {
    saveStatus.textContent = getSaveStatusText(state);
    saveStatus.className = `docs-save-status ${getSaveStatusClass(state)}`;
  }
  if (saveButton instanceof HTMLButtonElement) {
    saveButton.disabled = state.saving;
  }
  mainRoot.querySelectorAll("[data-doc-updated-at]").forEach((node) => {
    node.textContent = state.currentDoc?.updatedAt || "";
  });
  mainRoot.querySelectorAll("[data-doc-updated-by]").forEach((node) => {
    node.textContent = state.currentDoc?.updatedBy || "";
  });
  mainRoot.querySelectorAll("[data-doc-breadcrumb-title]").forEach((node) => {
    node.textContent = state.detailMode === "edit" ? state.editTitle || state.currentDoc?.title || "" : state.currentDoc?.title || "";
  });
}

function updateOutlineUi(state) {
  const mainRoot = state.mainEl;
  if (!(mainRoot instanceof HTMLElement)) return;
  const outlineShell = mainRoot.querySelector("#docsOutlineShell");
  const outlineTrigger = mainRoot.querySelector("#docsOutlineTrigger");
  const outlineDrawer = mainRoot.querySelector("#docsOutlineDrawer");
  if (!(outlineShell instanceof HTMLElement) || !(outlineTrigger instanceof HTMLButtonElement) || !(outlineDrawer instanceof HTMLElement)) {
    return;
  }
  outlineShell.classList.toggle("is-open", state.outlineOpen);
  outlineTrigger.setAttribute("aria-expanded", state.outlineOpen ? "true" : "false");
  outlineDrawer.setAttribute("aria-hidden", state.outlineOpen ? "false" : "true");
}

function setOutlineOpen(state, nextValue, pinnedByHover = false) {
  state.outlineOpen = nextValue;
  state.outlinePinnedByHover = nextValue ? pinnedByHover : false;
  updateOutlineUi(state);
}

function updateSidebarDocumentLabel(state) {
  const sidebarRoot = state.sidebarEl;
  if (!(sidebarRoot instanceof HTMLElement) || !state.currentDoc) return;
  sidebarRoot.querySelectorAll('[data-action="open-doc"]').forEach((button) => {
    if (!(button instanceof HTMLButtonElement)) return;
    if (button.getAttribute("data-doc-id") !== state.currentDoc.docId) return;
    const label = button.querySelector(".docs-tree-node__label");
    if (label) {
      label.textContent = state.currentDoc.title;
    }
  });
}

function updateSidebarActiveDoc(state) {
  const sidebarRoot = state.sidebarEl;
  if (!(sidebarRoot instanceof HTMLElement)) return;
  sidebarRoot.querySelectorAll(".docs-tree-node--doc").forEach((node) => {
    const button = node.querySelector('[data-action="open-doc"]');
    const docId = button?.getAttribute("data-doc-id") || "";
    node.classList.toggle("is-active", docId === state.currentDocId);
  });
}

function updateDocumentListEntry(state) {
  if (!state.currentDoc) return;
  const nextItem = toDocumentListItem(state.currentDoc);
  const index = state.list.findIndex((item) => item.docId === nextItem.docId);
  if (index >= 0) {
    state.list[index] = nextItem;
    return;
  }
  state.list.unshift(nextItem);
}

async function persistEdits(state) {
  if (!state.currentDoc || state.detailMode !== "edit") return false;
  if (state.saving) return false;

  syncDirtyFlagFromState(state);
  if (!state.dirty) return false;

  state.saving = true;
  state.saveError = "";
  updateSaveUi(state);

  const resp = await DocumentService.updateDocument(state.currentDoc.docId, {
    title: state.editTitle,
    contentMarkdown: state.editMarkdown,
    dept: state.currentDoc.dept,
    version: state.currentDoc.version
  });

  state.saving = false;
  if (!resp.ok) {
    state.saveError = resp.message || "保存失败，请稍后重试";
    updateSaveUi(state);
    return false;
  }

  state.currentDoc = resp.data;
  state.currentDocHtml = await renderDocumentMarkdownHtml(resp.data.contentMarkdown);
  state.lastSavedAt = resp.data.updatedAt;
  state.savedEditMarkdown = state.editMarkdown;
  state.dirty = false;
  state.saveError = "";
  updateDocumentListEntry(state);
  updateSidebarDocumentLabel(state);
  updateSaveUi(state);
  return true;
}

function syncMainInteractiveState(state) {
  syncDocumentDomRefs(state);
  cleanupOutlineInteractions(state);

  if (state.detailMode === "edit" && state.editorEl instanceof HTMLElement) {
    void ensureDocumentEditor(state);
  } else {
    destroyDocumentEditor(state);
    clearDocumentAutoSave(state);
  }

  if (state.detailMode === "edit" && !state.autoSaveTimer) {
    state.autoSaveTimer = window.setInterval(() => {
      void persistEdits(state);
    }, DOC_AUTOSAVE_INTERVAL);
  }

  if (typeof document !== "undefined") {
    const handleDocumentPointerDown = (event) => {
      const target = event.target;
      if (!(target instanceof Node)) return;
      const shell = state.mainEl?.querySelector("#docsOutlineShell");
      if (shell instanceof HTMLElement && shell.contains(target)) return;
      if (state.outlineOpen) {
        setOutlineOpen(state, false);
      }
    };
    document.addEventListener("pointerdown", handleDocumentPointerDown, true);
    state.outlineCleanup = () => {
      document.removeEventListener("pointerdown", handleDocumentPointerDown, true);
    };
  }

  updateSaveUi(state);
  updateOutlineUi(state);
  updateEditorAuxUi(state);
}

export function updateDocumentSidebar(state) {
  if (!(state.sidebarEl instanceof HTMLElement)) return;
  state.sidebarEl.innerHTML = renderDocumentSidebar(state);
  updateSidebarActiveDoc(state);
}

export function updateDocumentMain(route, state, keyword) {
  if (!(state.mainEl instanceof HTMLElement)) return;
  destroyDocumentEditor(state);
  state.mainEl.innerHTML = renderDocumentMain(route, state, keyword);
  updateSidebarActiveDoc(state);
  syncMainInteractiveState(state);
}

export function mountDocumentRoute(content, state, callbacks) {
  if (state.shellMounted && content.contains(state.rootEl)) {
    return;
  }

  content.innerHTML = renderDocumentShell();
  state.rootEl = content.querySelector(".docs-layout");
  state.sidebarEl = content.querySelector("[data-docs-sidebar]");
  state.mainEl = content.querySelector("[data-docs-main]");
  state.shellMounted = true;

  const root = state.rootEl;
  if (!(root instanceof HTMLElement)) return;

  root.addEventListener("change", (event) => {
    const target = event.target;
    if (target instanceof HTMLSelectElement && target.id === "docsDeptSelect") {
      state.deptFilter = target.value;
      state.folderFilter = "";
      window.location.hash = "#docs";
      return;
    }
  });

  root.addEventListener("input", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;

    if (target.id === "docsSearchInput" && target instanceof HTMLInputElement) {
      state.activeKeyword = target.value;
      callbacks.requestDraw?.();
      return;
    }

    if (target.id === "docEditTitle") {
      state.editTitle = target instanceof HTMLInputElement ? target.value.trim() : state.editTitle;
      syncDirtyFlagFromState(state);
    }
  });

  root.addEventListener("mouseenter", (event) => {
    const target = event.target;
    if (!(target instanceof Element)) return;
    if (target.closest("#docsOutlineTrigger")) {
      setOutlineOpen(state, true, true);
    }
    if (target.closest("#docsOutlineDrawer")) {
      setOutlineOpen(state, true, true);
    }
  }, true);

  root.addEventListener("focusin", (event) => {
    const target = event.target;
    if (!(target instanceof Element)) return;
    if (target.closest("#docsOutlineTrigger")) {
      setOutlineOpen(state, true, true);
    }
  });

  root.addEventListener("click", async (event) => {
    const target = event.target;
    if (!(target instanceof Element)) return;
    const actionTarget = target.closest("[data-action]");
    if (!actionTarget) return;
    const action = actionTarget.getAttribute("data-action");

    if (action === "toggle-folder") {
      const nodeId = actionTarget.getAttribute("data-node-id");
      if (!nodeId) return;
      const expanded = toExpandedSet(state.expandedNodeIds);
      if (expanded.has(nodeId)) {
        expanded.delete(nodeId);
      } else {
        expanded.add(nodeId);
      }
      state.expandedNodeIds = expanded;
      persistExpandedNodeIds(state.expandedNodeIds);
      callbacks.renderSidebar();
      return;
    }

    if (action === "open-folder" || action === "crumb-folder") {
      const folderId = actionTarget.getAttribute("data-node-id") || actionTarget.getAttribute("data-folder-id") || "";
      if (!folderId) return;
      const expanded = toExpandedSet(state.expandedNodeIds);
      expanded.add(folderId);
      state.expandedNodeIds = expanded;
      persistExpandedNodeIds(state.expandedNodeIds);
      window.location.hash = `#docs?folderId=${encodeURIComponent(folderId)}`;
      return;
    }

    if (action === "create-doc") {
      const folderId = actionTarget.getAttribute("data-node-id") || "";
      if (!folderId || state.creatingFolderId) return;
      const expanded = toExpandedSet(state.expandedNodeIds);
      expanded.add(folderId);
      state.expandedNodeIds = expanded;
      persistExpandedNodeIds(state.expandedNodeIds);
      state.creatingFolderId = folderId;
      callbacks.renderSidebar();

      const resp = await DocumentService.createDocument(folderId, { deptFilter: state.deptFilter });
      state.creatingFolderId = "";

      if (!resp.ok) {
        state.notice = resp.message || "新建文档失败，请稍后重试";
        callbacks.renderSidebar();
        callbacks.renderMain();
        return;
      }

      state.notice = "";
      state.pendingAutoEditDocId = resp.data.docId;
      state.list.unshift(toDocumentListItem(resp.data));
      state.forceTreeReload = true;
      window.location.hash = `#doc-view?docId=${encodeURIComponent(resp.data.docId)}&mode=edit`;
      return;
    }

    if (action === "open-doc") {
      const docId = actionTarget.getAttribute("data-doc-id");
      if (!docId) return;
      window.location.hash = `#doc-view?docId=${encodeURIComponent(docId)}`;
      return;
    }

    if (action === "edit-doc") {
      const docId = actionTarget.getAttribute("data-doc-id");
      if (!docId) return;
      window.location.hash = `#doc-view?docId=${encodeURIComponent(docId)}&mode=edit`;
      return;
    }

    if (action === "go-imports") {
      window.location.hash = "#imports";
      return;
    }

    if (action === "toggle-outline") {
      event.preventDefault();
      event.stopPropagation();
      setOutlineOpen(state, !state.outlineOpen);
      return;
    }

    if (action === "jump-heading") {
      const headingId = actionTarget.getAttribute("data-heading-id");
      if (!headingId) return;
      const heading = state.mainEl?.querySelector(`#${headingId}`);
      if (heading instanceof HTMLElement) {
        heading.scrollIntoView({ behavior: "smooth", block: "start" });
      }
      return;
    }

    if (action === "doc-edit") {
      if (!state.currentDoc) return;
      state.detailMode = "edit";
      state.editingDocId = state.currentDoc.docId;
      callbacks.renderMain();
      return;
    }

    if (action === "doc-cancel-edit") {
      clearDocumentAutoSave(state);
      syncEditorStateFromCurrentDoc(state);
      state.detailMode = "view";
      callbacks.renderMain();
      return;
    }

    if (action === "doc-save") {
      void persistEdits(state);
      return;
    }
  });
}

export function setupDocumentRoute(content, state, callbacks) {
  mountDocumentRoute(content, state, callbacks);
}
