package com.baseorg.docassistant.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码定义
 * 与前端Mock接口错误码对齐
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 认证相关 1xxx
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "用户名或密码错误"),
    AUTH_NOT_ACTIVATED("AUTH_NOT_ACTIVATED", "请先激活您的账户，检查注册邮箱"),
    AUTH_LOCKED("AUTH_LOCKED", "账户已锁定，请联系管理员"),
    AUTH_TOKEN_EXPIRED("AUTH_TOKEN_EXPIRED", "登录已过期，请重新登录"),
    AUTH_UNAUTHORIZED("AUTH_UNAUTHORIZED", "未授权访问"),

    // 用户相关 2xxx
    USERNAME_TAKEN("USERNAME_TAKEN", "该用户名已被占用"),
    EMAIL_TAKEN("EMAIL_TAKEN", "邮箱已被注册"),
    PASSWORD_WEAK("PASSWORD_WEAK", "密码复杂度不足"),
    USER_NOT_FOUND("USER_NOT_FOUND", "用户不存在"),

    // 文档相关 3xxx
    DOC_NOT_FOUND("DOC_NOT_FOUND", "文档不存在"),
    DOC_ACCESS_DENIED("DOC_ACCESS_DENIED", "无权访问该文档"),
    DOC_VERSION_CONFLICT("DOC_VERSION_CONFLICT", "文档版本冲突，请刷新后重试"),
    DOC_IMAGE_INVALID_TYPE("DOC_IMAGE_INVALID_TYPE", "仅支持 PNG/JPEG/WEBP/GIF 图片"),
    DOC_IMAGE_TOO_LARGE("DOC_IMAGE_TOO_LARGE", "图片大小超过限制"),

    // 导入任务相关 4xxx
    IMPORT_TASK_NOT_FOUND("IMPORT_TASK_NOT_FOUND", "任务不存在"),
    IMPORT_TASK_FINALIZED("IMPORT_TASK_FINALIZED", "任务已结束，无法操作"),
    IMPORT_FILE_TOO_LARGE("IMPORT_FILE_TOO_LARGE", "文件大小超过限制"),
    IMPORT_PARSE_ERROR("IMPORT_PARSE_ERROR", "文档解析失败"),

    // QA检索相关 5xxx
    QA_QUERY_EMPTY("QA_QUERY_EMPTY", "查询内容不能为空"),
    QA_NO_RESULTS("QA_NO_RESULTS", "未找到相关内容"),
    QA_LLM_TIMEOUT("QA_LLM_TIMEOUT", "LLM响应超时，已返回降级结果"),
    QA_RATE_LIMITED("QA_RATE_LIMITED", "请求频率超限，请稍后重试"),
    QA_SENSITIVE_FILTERED("QA_SENSITIVE_FILTERED", "部分敏感来源已过滤"),

    // 模板相关 6xxx
    TEMPLATE_NOT_FOUND("TEMPLATE_NOT_FOUND", "模板不存在"),
    TEMPLATE_VARIABLE_MISSING("TEMPLATE_VARIABLE_MISSING", "模板变量缺失"),

    // 系统错误 7xxx
    INTERNAL_ERROR("INTERNAL_ERROR", "系统内部错误"),
    INVALID_PARAMETER("INVALID_PARAMETER", "参数错误"),
    FORBIDDEN("FORBIDDEN", "无权限访问"),
    NOT_FOUND("NOT_FOUND", "资源不存在"),
    VALIDATION_ERROR("VALIDATION_ERROR", "参数校验失败");

    private final String code;
    private final String message;
}
