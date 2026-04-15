import { describe, expect, it } from "vitest";
import {
  convertHtmlToMarkdown,
  extractHeadingsFromMarkdown,
  renderMarkdownToHtml
} from "../../src/js/utils/markdown-renderer.js";

describe("markdown renderer", () => {
  it("should render markdown to html", () => {
    const html = renderMarkdownToHtml("# 标题\n\n|A|B|\n|---|---|\n|1|2|");
    expect(html).toContain("<h1>标题</h1>");
    expect(html).toContain("<table>");
  });

  it("should sanitize unsafe tags", () => {
    const html = renderMarkdownToHtml('<script>alert("xss")</script><iframe src="x"></iframe><p>safe</p>');
    expect(html).not.toContain("<script>");
    expect(html).not.toContain("<iframe");
    expect(html).toContain("<p>safe</p>");
  });

  it("should extract headings with depth", () => {
    const headings = extractHeadingsFromMarkdown("# A\n## B\n### C");
    expect(headings).toHaveLength(3);
    expect(headings[0]).toEqual({ depth: 1, text: "A", id: "md-heading-1" });
    expect(headings[2].depth).toBe(3);
  });

  it("should convert html back to markdown with headings and table", () => {
    const markdown = convertHtmlToMarkdown(`
      <h1>标题</h1>
      <p><strong>加粗</strong> 与 <code>code</code></p>
      <table>
        <thead><tr><th>A</th><th>B</th></tr></thead>
        <tbody><tr><td>1</td><td>2</td></tr></tbody>
      </table>
    `);

    expect(markdown).toContain("# 标题");
    expect(markdown).toContain("**加粗**");
    expect(markdown).toContain("`code`");
    expect(markdown).toContain("| A | B |");
  });
});
