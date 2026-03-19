# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## 项目概述

**技术文档 & 需求文档助手** — 基于企业 RAG 能力与 Spring 生态构建的文档辅助系统。

核心能力：
- 企业知识检索（RAG）
- 文档生成/补全
- 需求模板化生成
- 变更审批与审计工作流

## 技术栈

| 层级 | 技术选型 |
|------|---------|
| 应用框架 | Spring Boot |
| LLM 集成 | Spring AI |
| 向量存储 | PostgreSQL + pgvector（或 Milvus） |
| 对象存储 | S3 兼容存储 |
| 监控 | Prometheus + Grafana |
| Secrets | HashiCorp Vault / 云 KMS |

## 架构概览

```
┌─────────────────────────────────────────────────────────┐
│                      API Gateway                        │
│              (Spring Boot + Spring AI)                  │
├─────────────────────────────────────────────────────────┤
│  文档导入  │  检索问答  │  草稿生成  │  审批工作流      │
├─────────────────────────────────────────────────────────┤
│  向量存储 (pgvector/Milvus)  │  对象存储 (S3)          │
└─────────────────────────────────────────────────────────┘
```

## 核心数据模型

- **Document**: `doc_id, title, source_url, author, dept, version, sensitivity`
- **Chunk**: `chunk_id, doc_id, chunk_index, text, embedding, tokens, metadata`
- **Draft**: `draft_id, template_id, owner_id, content, status, versions[]`
- **AuditLog**: `audit_id, user_id, action, resource_type, resource_id, timestamp`

## API 端点

```
POST /api/v1/documents/import          # 文档导入（异步）
GET  /api/v1/documents/import/{taskId} # 查询导入状态
POST /api/v1/qa                        # 问答检索
POST /api/v1/generate/requirement      # 生成需求草稿
GET  /api/v1/drafts/{id}               # 获取草稿
POST /api/v1/drafts/{id}/submit        # 提交审批
POST /api/v1/admin/templates           # 模板管理
GET  /api/v1/audit/logs                # 审计查询
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
