export const IMPORT_SOURCE_TYPES = ["upload", "url", "s3", "confluence"];
export const SUPPORTED_UPLOAD_EXTENSIONS = ["pdf", "docx", "md"];
export const MAX_UPLOAD_FILE_SIZE = 200 * 1024 * 1024;

function validUrl(value) {
  try {
    const parsed = new URL(String(value || "").trim());
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch (_error) {
    return false;
  }
}

function validS3Path(value) {
  const raw = String(value || "").trim();
  if (!raw) return false;
  if (raw.startsWith("s3://")) return raw.length > 5;
  return raw.includes("/") && !raw.startsWith("http");
}

function validConfluencePath(value) {
  const raw = String(value || "").trim();
  if (!raw) return false;
  return validUrl(raw) || raw.startsWith("space/");
}

export function detectFileExtension(fileName) {
  const parts = String(fileName || "").split(".");
  if (parts.length < 2) return "";
  return parts[parts.length - 1].toLowerCase();
}

export function validateImportSource(sourceType, sourcePayload = {}) {
  const errors = {};
  if (!IMPORT_SOURCE_TYPES.includes(sourceType)) {
    errors.sourceType = "请选择导入来源";
    return { valid: false, errors };
  }

  if (sourceType === "upload") {
    if (!sourcePayload.fileMeta?.name || !sourcePayload.file) {
      errors.file = "请上传文件";
      return { valid: false, errors };
    }
    const ext = detectFileExtension(sourcePayload.fileMeta.name);
    if (!SUPPORTED_UPLOAD_EXTENSIONS.includes(ext)) {
      errors.file = "仅支持 PDF、DOCX、MD 文件";
    }
    if ((sourcePayload.fileMeta.size || 0) > MAX_UPLOAD_FILE_SIZE) {
      errors.file = "文件大小不可超过 200MB";
    }
  }

  if (sourceType === "url" && !validUrl(sourcePayload.url)) {
    errors.url = "请输入有效的网页 URL";
  }
  if (sourceType === "s3" && !validS3Path(sourcePayload.s3Path)) {
    errors.s3Path = "请输入有效的 S3 路径";
  }
  if (sourceType === "confluence" && !validConfluencePath(sourcePayload.confluencePath)) {
    errors.confluencePath = "请输入有效的 Confluence 地址或路径";
  }

  return { valid: Object.keys(errors).length === 0, errors };
}

export function validateImportMetadata(metadata = {}) {
  const errors = {};
  if (!String(metadata.sensitivity || "").trim()) {
    errors.sensitivity = "请选择敏感等级";
  }
  if (!String(metadata.version || "").trim()) {
    errors.version = "请输入版本号";
  }
  return { valid: Object.keys(errors).length === 0, errors };
}

export function validateChunkConfig(chunkConfig = {}) {
  const errors = {};
  const chunkSize = Number(chunkConfig.size);
  const overlap = Number(chunkConfig.overlap);

  if (!Number.isFinite(chunkSize) || chunkSize < 100 || chunkSize > 2000) {
    errors.size = "Chunk 大小需在 100-2000 之间";
  }
  if (!Number.isFinite(overlap) || overlap < 0) {
    errors.overlap = "重叠 token 不能为负数";
  }
  if (Number.isFinite(chunkSize) && Number.isFinite(overlap) && overlap >= chunkSize) {
    errors.overlap = "重叠 token 必须小于 chunk 大小";
  }

  return { valid: Object.keys(errors).length === 0, errors };
}
