let editorRuntimePromise = null;

function isBrowser() {
  return typeof window !== "undefined" && typeof document !== "undefined";
}

async function loadToastEditorRuntime() {
  if (!editorRuntimePromise) {
    editorRuntimePromise = import("./toast-doc-editor-runtime.js");
  }
  return editorRuntimePromise;
}

export async function createToastDocEditor({
  element,
  markdown = "",
  placeholder = "请输入正文",
  onChange,
  onUploadImage
}) {
  if (!isBrowser() || !(element instanceof HTMLElement)) {
    return null;
  }

  const runtime = await loadToastEditorRuntime();
  return runtime.createToastDocEditorRuntime({
    element,
    markdown,
    placeholder,
    onChange,
    onUploadImage
  });
}
