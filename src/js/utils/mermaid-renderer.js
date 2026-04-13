import { escapeHtml } from "./dom.js";

function renderFallback(container, dsl) {
  container.innerHTML = `
    <pre class="qa-mermaid__dsl"><code>${escapeHtml(String(dsl || ""))}</code></pre>
  `;
}

export function hydrateMermaidDiagrams(root) {
  if (!root || typeof root.querySelectorAll !== "function") {
    return;
  }

  const containers = root.querySelectorAll('[data-role="mermaid-diagram"]');
  if (!containers.length) {
    return;
  }

  const mermaid = typeof window !== "undefined" ? window.mermaid : null;
  containers.forEach((container, index) => {
    const sourceNode = container.querySelector('[data-role="mermaid-source"]');
    const dsl = sourceNode?.textContent || "";
    if (!dsl.trim() || !mermaid || typeof mermaid.render !== "function") {
      renderFallback(container, dsl);
      return;
    }

    const renderId = `qa_mermaid_${Date.now()}_${index}`;
    try {
      const rendered = mermaid.render(renderId, dsl);
      if (rendered && typeof rendered.then === "function") {
        rendered
          .then(({ svg }) => {
            if (svg) {
              container.innerHTML = svg;
            } else {
              renderFallback(container, dsl);
            }
          })
          .catch(() => renderFallback(container, dsl));
        return;
      }
      if (typeof rendered === "string" && rendered) {
        container.innerHTML = rendered;
        return;
      }
      if (rendered?.svg) {
        container.innerHTML = rendered.svg;
        return;
      }
      renderFallback(container, dsl);
    } catch {
      renderFallback(container, dsl);
    }
  });
}
