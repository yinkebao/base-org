export function extractHeadingsFromMarkdown(markdownText) {
  const headings = [];
  const lines = String(markdownText || "").split("\n");
  let index = 1;

  for (const line of lines) {
    const match = line.match(/^(#{1,3})\s+(.+)$/);
    if (!match) continue;
    headings.push({
      depth: match[1].length,
      text: match[2].trim(),
      id: `md-heading-${index}`
    });
    index += 1;
  }

  return headings;
}
