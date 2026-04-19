/**
 * 错误码常量定义
 * 与后端 ErrorCode 枚举保持一致
 * @see docs/API_CONTRACT.md
 */

export const ErrorCode = {
  SUCCESS: 0,

  // 认证相关 1xxx
  UNAUTHORIZED: 1001,
  TOKEN_EXPIRED: 1002,
  FORBIDDEN: 1003,

  // 资源相关 2xxx
  NOT_FOUND: 2001,

  // 验证相关 3xxx
  VALIDATION_ERROR: 3001,

  // 业务相关 4xxx
  SENSITIVE_FILTERED: 4001,
  LLM_TIMEOUT: 4002,
  RATE_LIMITED: 4003,

  // 系统相关 5xxx
  INTERNAL_ERROR: 5001
};

/**
 * 错误码对应的中文提示信息
 */
export const ErrorMessage = {
  [ErrorCode.SUCCESS]: "操作成功",
  [ErrorCode.UNAUTHORIZED]: "未授权，请登录",
  [ErrorCode.TOKEN_EXPIRED]: "登录已过期，请重新登录",
  [ErrorCode.FORBIDDEN]: "无权限访问此资源",
  [ErrorCode.NOT_FOUND]: "请求的资源不存在",
  [ErrorCode.VALIDATION_ERROR]: "参数校验失败",
  [ErrorCode.SENSITIVE_FILTERED]: "部分内容因敏感已被过滤",
  [ErrorCode.LLM_TIMEOUT]: "AI 响应超时，请稍后重试",
  [ErrorCode.RATE_LIMITED]: "请求过于频繁，请稍后重试",
  [ErrorCode.INTERNAL_ERROR]: "系统繁忙，请稍后重试"
};

/**
 * 根据错误码获取错误信息
 * @param {number} code 错误码
 * @param {string} fallback 默认信息
 * @returns {string} 错误信息
 */
export function getErrorMessage(code, fallback = "操作失败") {
  return ErrorMessage[code] || fallback;
}

/**
 * 判断是否为认证相关错误
 * @param {number} code 错误码
 * @returns {boolean}
 */
export function isAuthError(code) {
  return code >= 1001 && code <= 1999;
}

/**
 * 判断是否需要重新登录
 * @param {number} code 错误码
 * @returns {boolean}
 */
export function requireRelogin(code) {
  return code === ErrorCode.UNAUTHORIZED || code === ErrorCode.TOKEN_EXPIRED;
}
