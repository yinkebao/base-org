# ---- 前端 Dockerfile ----
# 多阶段构建：node 构建 → nginx 运行

# 阶段 1：构建
FROM node:20-alpine AS build

WORKDIR /app

# 先复制依赖文件，利用 Docker 缓存层
COPY package.json package-lock.json ./
RUN npm ci

# 复制源码并构建
COPY . .
RUN npm run build

# 阶段 2：运行
FROM nginx:alpine

# 复制构建产物到 nginx 目录
COPY --from=build /app/dist /usr/share/nginx/html

# nginx 配置：支持前端多页面路由
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
