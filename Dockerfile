FROM linuxserver/ffmpeg:version-8.0-cli

# 安装 OpenJDK 21 (linuxserver/ffmpeg 基于 Ubuntu)
RUN apt-get update && \
    apt-get install -y openjdk-21-jre-headless && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 直接复制本地构建好的 jar 包
# 请确保在执行 docker build 之前已经运行了 ./gradlew :fly-narwhal-web:bootJar -x test
COPY fly-narwhal-web/build/libs/*.jar app.jar

# 暴露端口
EXPOSE 5365

# 设置时区为 Asia/Shanghai
ENV TZ=Asia/Shanghai

# 设置数据卷
VOLUME /app/data

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]
