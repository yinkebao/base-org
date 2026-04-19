import Editor from "@toast-ui/editor";
import "@toast-ui/editor/dist/i18n/zh-cn";
import { extractHeadingsFromMarkdown } from "./markdown-headings.js";

function normalizeMarkdownValue(markdown) {
  return String(markdown || "")
    .replace(/\r\n/g, "\n")
    .replace(/\u00a0/g, " ")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

export function createToastDocEditorRuntime({
  element,
  markdown = "",
  placeholder = "请输入正文",
  onChange,
  onUploadImage
}) {
  const emitState = (editorInstance) => {
    const nextMarkdown = normalizeMarkdownValue(editorInstance.getMarkdown());
    onChange?.({
      markdown: nextMarkdown,
      headings: extractHeadingsFromMarkdown(nextMarkdown).map((heading) => ({
        ...heading,
        id: heading.id.replace("md-heading", "doc-edit-heading")
      }))
    });
  };

  const editor = new Editor({
    el: element,
    initialValue: normalizeMarkdownValue(markdown),
    placeholder,
    previewStyle: "tab",
    initialEditType: "wysiwyg",
    hideModeSwitch: true,
    height: "560px",
    minHeight: "420px",
    language: "zh-CN",
    usageStatistics: false,
    autofocus: false,
    toolbarItems: [
      ["heading", "bold", "italic", "strike"],
      ["hr", "quote"],
      ["ul", "ol", "task", "indent", "outdent"],
      ["table", "image", "link"],
      ["code", "codeblock"]
    ],
    events: {
      change() {
        emitState(editor);
      }
    },
    hooks: {
      addImageBlobHook: async (blob, callback) => {
        if (typeof onUploadImage !== "function") {
          return;
        }

        const result = await onUploadImage(blob);
        if (result?.url) {
          callback(result.url, result.alt || "");
        }
      }
    }
  });

  emitState(editor);

  return {
    focus() {
      editor.focus();
    },
    destroy() {
      editor.destroy();
    },
    getMarkdown() {
      return normalizeMarkdownValue(editor.getMarkdown());
    },
    setMarkdown(nextMarkdown) {
      editor.setMarkdown(normalizeMarkdownValue(nextMarkdown), false);
      emitState(editor);
    }
  };
}
