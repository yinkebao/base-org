import { marked } from "marked";
import createDOMPurify from "dompurify";
import TurndownService from "turndown";
import { gfm, tables } from "turndown-plugin-gfm";
import { extractHeadingsFromMarkdown } from "./markdown-headings.js";

function stripUnsafeTags(html) {
  return String(html || "")
    .replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, "")
    .replace(/<iframe[\s\S]*?>[\s\S]*?<\/iframe>/gi, "")
    .replace(/<object[\s\S]*?>[\s\S]*?<\/object>/gi, "");
}

function createPurifier() {
  if (typeof window !== "undefined" && window.document) {
    return createDOMPurify(window);
  }
  return null;
}

function createTurndown() {
  const service = new TurndownService({
    headingStyle: "atx",
    bulletListMarker: "-",
    codeBlockStyle: "fenced",
    emDelimiter: "*"
  });
  service.use(gfm);
  service.use(tables);
  return service;
}

function normalizeMarkdown(markdownText) {
  return String(markdownText || "")
    .replace(/\r\n/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

export function renderMarkdownToHtml(markdownText) {
  const rawHtml = marked.parse(String(markdownText || ""));
  const purifier = createPurifier();
  if (!purifier) {
    return stripUnsafeTags(rawHtml);
  }
  return purifier.sanitize(rawHtml);
}

export function renderMarkdownToEditableHtml(markdownText) {
  return renderMarkdownToHtml(markdownText);
}

export function convertHtmlToMarkdown(htmlText) {
  const safeHtml = stripUnsafeTags(String(htmlText || ""));
  return normalizeMarkdown(createTurndown().turndown(safeHtml));
}

export { extractHeadingsFromMarkdown };
