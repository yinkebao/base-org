import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("../../src/js/services/qa-service.js", () => ({
  QAService: {
    query: vi.fn()
  }
}));

import { QAService } from "../../src/js/services/qa-service.js";
import { createDefaultQAState, setupQAPage } from "../../src/js/pages/qa-page.js";

function createInput() {
  const listeners = new Map();
  return {
    value: "",
    style: { height: "auto" },
    scrollHeight: 20,
    addEventListener(type, handler) {
      if (!listeners.has(type)) listeners.set(type, []);
      listeners.get(type).push(handler);
    },
    dispatchEvent(event) {
      const handlers = listeners.get(event.type) || [];
      handlers.forEach((handler) => handler(event));
    }
  };
}

function createButton() {
  const listeners = new Map();
  return {
    disabled: false,
    addEventListener(type, handler) {
      if (!listeners.has(type)) listeners.set(type, []);
      listeners.get(type).push(handler);
    },
    dispatchEvent(event) {
      const handlers = listeners.get(event.type) || [];
      handlers.forEach((handler) => handler(event));
    }
  };
}

function createMessagesContainer() {
  return {
    innerHTML: "",
    scrollTop: 0,
    scrollHeight: 0,
    querySelector() {
      return null;
    },
    querySelectorAll() {
      return [];
    },
    insertAdjacentHTML(_position, html) {
      this.innerHTML += html;
    },
    addEventListener() {}
  };
}

function createFakeContent() {
  const input = createInput();
  const sendBtn = createButton();
  const messages = createMessagesContainer();
  const listeners = new Map();

  const root = {
    querySelector(selector) {
      if (selector === "#qaInput") return input;
      if (selector === "#qaSendBtn") return sendBtn;
      if (selector === "#qaMessages") return messages;
      return null;
    },
    addEventListener(type, handler) {
      if (!listeners.has(type)) listeners.set(type, []);
      listeners.get(type).push(handler);
    },
    cloneNode() {
      return root;
    },
    parentNode: {
      replaceChild() {}
    }
  };

  return {
    content: {
      querySelector(selector) {
        if (selector === "[data-qa-root]") return root;
        return root.querySelector(selector);
      }
    },
    input,
    sendBtn,
    messages
  };
}

async function flushPromiseQueue() {
  await Promise.resolve();
  await Promise.resolve();
}

if (!globalThis.requestAnimationFrame) {
  globalThis.requestAnimationFrame = (cb) => cb();
}

describe("QA page keyboard & send", () => {
  afterEach(() => {
    vi.resetAllMocks();
  });

  it("sends query on Enter without modifier", async () => {
    const state = createDefaultQAState();
    const layout = createFakeContent();
    setupQAPage(layout.content, state, vi.fn());

    const queryMock = vi.mocked(QAService.query);
    queryMock.mockResolvedValue({
      ok: true,
      data: {
        answer: "回答",
        sources: [],
        filteredCount: 0
      }
    });

    layout.input.value = "怎么导入文档";
    state.inputText = layout.input.value;

    layout.input.dispatchEvent({
      type: "keydown",
      key: "Enter",
      shiftKey: false,
      altKey: false,
      preventDefault() {},
      bubbles: true,
      cancelable: true
    });

    await flushPromiseQueue();

    expect(queryMock).toHaveBeenCalledOnce();
    expect(state.messages).toHaveLength(2);
    const assistant = state.messages[1];
    expect(assistant.role).toBe("assistant");
    expect(assistant.content).toBe("回答");
    expect(state.loading).toBe(false);
  });

  it("does not send when Option+Enter pressed (altKey)", async () => {
    const state = createDefaultQAState();
    const layout = createFakeContent();
    setupQAPage(layout.content, state, vi.fn());

    const queryMock = vi.mocked(QAService.query);
    queryMock.mockResolvedValue({
      ok: true,
      data: { answer: "", sources: [], filteredCount: 0 }
    });

    layout.input.value = "多行输入";
    state.inputText = layout.input.value;

    layout.input.dispatchEvent({
      type: "keydown",
      key: "Enter",
      altKey: true,
      preventDefault() {},
      bubbles: true,
      cancelable: true
    });

    await flushPromiseQueue();

    expect(queryMock).not.toHaveBeenCalled();
    expect(state.messages).toHaveLength(0);
    expect(state.loading).toBe(false);
  });

  it("displays QA error message when service returns failure", async () => {
    const state = createDefaultQAState();
    const layout = createFakeContent();
    setupQAPage(layout.content, state, vi.fn());

    const queryMock = vi.mocked(QAService.query);
    queryMock.mockResolvedValue({
      ok: false,
      message: "权限不足"
    });

    layout.input.value = "权限查询";
    state.inputText = layout.input.value;

    layout.input.dispatchEvent({
      type: "keydown",
      key: "Enter",
      preventDefault() {},
      bubbles: true,
      cancelable: true
    });

    await flushPromiseQueue();

    expect(state.messages).toHaveLength(2);
    expect(state.messages[1].role).toBe("assistant");
    expect(state.error).toBe("权限不足");
    expect(state.loading).toBe(false);
  });
});
