# Stage 1: Build
FROM gradle:8.10-jdk21 AS builder
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew :fly-narwhal-web:bootJar -x test

# Stage 2: Runtime
FROM linuxserver/ffmpeg:version-8.0-cli

# 安装 OpenJDK 17 (linuxserver/ffmpeg 基于 Ubuntu)
RUN apt-get update && \
    apt-get install -y openjdk-21-jre-headless && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
# 从构建阶段复制生成的 jar 包
COPY --from=builder /app/fly-narwhal-web/build/libs/*.jar app.jar

# 暴露端口
EXPOSE 5365

# 设置时区为 Asia/Shanghai
ENV TZ=Asia/Shanghai

# 设置数据卷
VOLUME /app/data

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]
