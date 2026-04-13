# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## 执行约束

- 涉及 Maven 命令时，统一使用 `zsh -ic 'mvn39 ...'` 执行，不要直接使用 `mvn`
- 原因：`mvn39` 是本地交互式 `zsh` 中指向 `/Users/yinkebao/.sdkman/candidates/maven/3.9.14/bin/mvn` 的 alias，非交互式 shell 下不会自动生效

## 编码要求
- 关键方法、节点、流程上，一定要有中文注释！

## 项目概述

**技术文档助手** — 基于企业 RAG 能力与 Spring 生态构建的文档检索、导入与编辑系统。

核心能力：
- 企业知识检索（RAG）
- 文档导入与向量化
- 文档管理与内联编辑
- 模板管理与审计

## 技术栈

| 层级 | 技术选型 |
|------|---------|
| 应用框架 | Spring Boot |
| LLM 集成 | Spring AI Alibaba + OpenAI-compatible provider |
| 向量存储 | PostgreSQL + pgvector |
| 对象存储 | S3 兼容存储 |
| 监控 | Prometheus + Grafana |
| Secrets | HashiCorp Vault / 云 KMS |

## 架构概览

```
┌─────────────────────────────────────────────────────────┐
│                      API Gateway                        │
│ (Spring Boot + Spring AI Alibaba + OpenAI-compatible)  │
├─────────────────────────────────────────────────────────┤
│  文档导入  │  检索问答  │  文档管理  │  审计与监控      │
├─────────────────────────────────────────────────────────┤
│  向量存储 (pgvector/Milvus)  │  对象存储 (S3)          │
└─────────────────────────────────────────────────────────┘
```

## 核心数据模型

- **Document**: `doc_id, title, source_url, owner_id, dept, version, sensitivity`
- **Chunk**: `chunk_id, doc_id, chunk_index, text, embedding, tokens, metadata`
- **ImportTask**: `task_id, owner_id, filename, status, progress, result_doc_id`
- **AuditLog**: `audit_id, user_id, action, resource_type, resource_id, timestamp`
- **Template**: `template_id, name, category, content, variables, version`

## API 端点

```
POST /api/v1/auth/login               # 登录
POST /api/v1/auth/register            # 注册
GET  /api/v1/auth/check-username      # 用户名检查
GET  /api/v1/auth/sso/start           # SSO 登录入口
POST /api/v1/documents/import         # 文档导入
GET  /api/v1/documents/import/{taskId}# 查询导入状态
GET  /api/v1/documents                # 文档列表
GET  /api/v1/documents/tree           # 文档树
GET  /api/v1/documents/{id}           # 文档详情
PUT  /api/v1/documents/{id}           # 更新文档
POST /api/v1/qa                       # 问答检索
GET  /api/v1/admin/metrics            # 系统指标
GET  /api/v1/admin/alerts             # 告警列表
GET  /api/v1/admin/health             # 健康检查
```

## 安全与合规要点

- **数据分级**: `public | internal | confidential | secret`
- **ACL 强制过滤**: 检索时在向量查询后执行 metadata-level filter（后端强制）
- **敏感信息处理**: 发送给外部 LLM 的上下文需剔除敏感字段
- **审计**: 每次 LLM 请求记录 `promptHash`，审计日志保存 365 天

## 性能指标

- API 可用性: 99.9%
- 检索+生成 P95: ≤ 3s（不含外部 LLM 延迟）
- 向量 DB: 支持 10M+ chunk 检索

## 验收标准

- 检索召回率: recall@5 ≥ 0.8
- QA 正确率: ≥ 85%（人工评估）
- 50 并发下 P95 latency ≤ 3s

## 详细需求文档

完整需求规格说明书见 [SRS.md](./SRS.md)
