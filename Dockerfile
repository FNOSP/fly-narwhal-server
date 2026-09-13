FROM linuxserver/ffmpeg:version-8.0-cli

# No JRE: the server ships as a GraalVM native image. GraalVM only produces
# binaries for the machine that builds them, so build this image on the same
# architecture you intend to run it on.
WORKDIR /app

# 复制本地构建好的原生二进制（含它依赖的同级 lib*.so）
# 请确保在执行 docker build 之前已经运行了
#   ./gradlew :fly-narwhal-web:linkNativeBinary -x test
# 二进制在启动时会 dlopen 同目录下的 lib*.so，两者必须一起拷进来。
# linkNativeBinary 会额外产出一个不带版本号的 fly-narwhal-server，方便这里引用固定路径。
COPY fly-narwhal-web/build/native/nativeCompile/ /app/

# authx 验签器以独立文件形式随二进制同目录分发，运行时由 fly-narwhal-server
# 从自身所在目录解析（见 ExternalAuthxVerifier.resolveExternalBinary）。
# 它不在 nativeCompile/ 里，必须单独拷入，否则启动后无法验签。
# 构建时需带 FLY_NARWHAL_BUILD_AUTHX_VERIFIER=1，产物架构须与镜像一致。
COPY fly-narwhal-web/build/generated-resources/authx-verifier/native/authx/linux-amd64/flynarwhal-authx /app/flynarwhal-authx

# 暴露端口
EXPOSE 5365

# 设置时区为 Asia/Shanghai
ENV TZ=Asia/Shanghai

# 设置数据卷
VOLUME /app/data

# 启动命令
ENTRYPOINT ["/app/fly-narwhal-server"]
