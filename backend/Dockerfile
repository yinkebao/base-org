# ---- 后端 Dockerfile ----
# 多阶段构建：Maven 编译 → JRE 运行

# 阶段 1：构建
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# 先复制依赖文件，利用 Docker 缓存层
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并构建（跳过测试，测试在 CI 流水线中单独执行）
COPY src ./src
RUN mvn package -DskipTests -B

# 阶段 2：运行
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 创建非 root 用户运行应用
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# 从构建阶段复制 jar
COPY --from=build /app/target/*.jar app.jar

# 创建存储目录
RUN mkdir -p /home/appuser/docassist/storage && chown -R appuser:appgroup /home/appuser/docassist

USER appuser

EXPOSE 8080

# 通过环境变量注入配置（数据库、API Key、JWT Secret 等）
# 生产环境必须设置的环境变量：
#   SPRING_DATASOURCE_URL      - PostgreSQL 连接地址
#   SPRING_DATASOURCE_USERNAME - 数据库用户名
#   SPRING_DATASOURCE_PASSWORD - 数据库密码
#   JWT_SECRET                 - JWT 签名密钥
#   OPENAI_API_KEY             - OpenAI API Key（可选，使用 Ollama 时不需）
#   OPENAI_BASE_URL            - OpenAI 兼容服务地址（可选）
#   OLLAMA_BASE_URL            - Ollama 服务地址（可选）
ENTRYPOINT ["java", "-jar", "app.jar"]
