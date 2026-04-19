import { describe, expect, it } from "vitest";
import {
  MAX_UPLOAD_FILE_SIZE,
  detectFileExtension,
  validateChunkConfig,
  validateImportMetadata,
  validateImportSource
} from "../../src/js/utils/import-validators.js";

describe("import validators", () => {
  it("should validate upload source", () => {
    const file = new File(["pdf"], "需求文档.pdf", { type: "application/pdf" });
    const result = validateImportSource("upload", {
      file,
      fileMeta: { name: "需求文档.pdf", size: 1024 }
    });
    expect(result.valid).toBe(true);
  });

  it("should reject oversize file", () => {
    const file = new File(["pdf"], "a.pdf", { type: "application/pdf" });
    const result = validateImportSource("upload", {
      file,
      fileMeta: { name: "a.pdf", size: MAX_UPLOAD_FILE_SIZE + 1 }
    });
    expect(result.valid).toBe(false);
    expect(result.errors.file).toContain("200MB");
  });

  it("should validate url source", () => {
    expect(validateImportSource("url", { url: "https://example.com/doc" }).valid).toBe(true);
    expect(validateImportSource("url", { url: "invalid-url" }).valid).toBe(false);
  });

  it("should validate metadata", () => {
    const valid = validateImportMetadata({
      sensitivity: "INTERNAL",
      version: "v1.0.0"
    });
    expect(valid.valid).toBe(true);
  });

  it("should validate chunk config", () => {
    const valid = validateChunkConfig({ size: 500, overlap: 100 });
    expect(valid.valid).toBe(true);
    const invalid = validateChunkConfig({ size: 100, overlap: 200 });
    expect(invalid.valid).toBe(false);
  });

  it("should parse extension", () => {
    expect(detectFileExtension("doc.spec.MD")).toBe("md");
    expect(detectFileExtension("README")).toBe("");
  });
});
