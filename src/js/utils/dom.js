export function setText(element, text) {
  if (!element) return;
  element.textContent = text || "";
}

export function setInputError(input, errorElement, message) {
  if (!input || !errorElement) return;
  input.setAttribute("aria-invalid", message ? "true" : "false");
  errorElement.textContent = message || "";
}

export function toggleButtonLoading(button, loading, loadingText = "处理中...") {
  if (!button) return;
  if (loading) {
    button.dataset.originText = button.textContent || "";
    button.textContent = loadingText;
    button.disabled = true;
    button.classList.add("is-loading");
    return;
  }
  button.textContent = button.dataset.originText || button.textContent || "";
  button.disabled = false;
  button.classList.remove("is-loading");
}

export function debounce(fn, delay = 300) {
  let timer = null;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  };
}

export function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
