# fly-narwhal-server

飞鲸影视客户端的服务端

## 项目简介
作为飞鲸影视客户端的后端服务，配合飞鲸影视客户端实现弹幕拉取和缓存、电视节目智能片头/片尾检测等功能。

## 部署教程

> 服务端已上架应用中心，请优先从飞牛应用中心下载使用

### 准备工作
无需安装 Java：服务端以 GraalVM 原生二进制形式发布，自带运行时。

### 使用 releases 中打包好的二进制（推荐）

服务端不再发布 jar，只发布 Linux 原生二进制，按 CPU 架构分为两个包：

| 架构 | 文件名 |
| --- | --- |
| x86_64 / amd64 | `fly-narwhal-server-linux-amd64-{version}.tar.gz` |
| ARM64 / aarch64 | `fly-narwhal-server-linux-arm64-{version}.tar.gz` |

在 [releases](https://github.com/FNOSP/fly-narwhal-server/releases/latest) 页面下载与你的
NAS 架构匹配的包，放到飞牛 NAS 某个目录下并解压：

```bash
tar -xzf fly-narwhal-server-linux-amd64-{version}.tar.gz
```

> 解压后是同目录下的一个可执行文件加若干 `lib*.so`，**必须放在一起**，程序启动时会从同目录加载它们。
> 压缩包已保留可执行权限，无需再 `chmod +x`。

### 运行
1. **在终端工具中进入解压目录**

2. **后台运行**：

   ```bash
   # 服务默认运行在 5365 端口
   nohup ./fly-narwhal-server > /dev/null 2>&1 &

   # 如果需要更换默认端口
   nohup ./fly-narwhal-server --server.port=8080 > /dev/null 2>&1 &
   ```

   > **建议加上内存参数**，否则进程占用会明显偏高。原生镜像使用的 Serial GC
   > 默认允许堆增长到物理内存的 80%，并且回收后不把空闲页还给系统，实测评测中
   > 常驻内存会因此停在 200MB 上下；加上下面三个参数后可以降到 120MB 左右：
   >
   > ```bash
   > nohup ./fly-narwhal-server \
   >   -XX:MaxHeapSize=67108864 \
   >   -XX:MaxHeapFree=8388608 \
   >   -XX:StackSize=524288 \
   >   > /dev/null 2>&1 &
   > ```
   >
   > 含义依次是：堆上限 64MB、回收后只保留 8MB 空闲堆（这样 GC 会主动把多余
   > 的页交还系统）、每线程栈减半到 512KB。如果日志里出现 `OutOfMemoryError`
   > （剧集特别多、并发分析时可能出现），把 `MaxHeapSize` 提高即可，
   > 例如 `134217728` 是 128MB，另外两个不用动。

3. **停止服务**：

   ```bash
   kill $(lsof -t -i:5365)
   ```

#### 用脚本管理（可选）

仓库根目录提供了 [`run.sh.example`](run.sh.example)，包含上面的内存参数以及
`start` / `stop` / `restart` / `status` 四个命令，省去每次手写启动参数：

```bash
cp run.sh.example run.sh && chmod +x run.sh
./run.sh start      # 默认 5365 端口，可用 SERVER_PORT=8080 ./run.sh start 覆盖
./run.sh status
./run.sh stop
```

内存参数可用环境变量覆盖，例如 `FLY_NARWHAL_MAX_HEAP=134217728 ./run.sh start`。

### 从源码构建

#### 准备工作
- 安装 GraalVM JDK 21（需要其中的 `native-image`）
- 原生镜像**只能为本机架构构建**，无法交叉编译：在 x86_64 机器上编出 amd64，
  在 ARM64 机器上编出 arm64

#### 构建二进制
1. **克隆项目：**
   ```bash
   git clone https://github.com/FNOSP/fly-narwhal-server
   cd fly-narwhal-server
   ```
2. **赋予脚本执行权限**：
   ```bash
   chmod +x gradlew
   ```
3. **清理并构建**：
   ```bash
   ./gradlew clean :fly-narwhal-web:linkNativeBinary -x test
   ```
4. **产物位置**：`fly-narwhal-web/build/native/nativeCompile/`
   下的 `fly-narwhal-server`（以及同目录的 `lib*.so`，需一并分发）

### Docker 部署

镜像内置的是原生二进制（没有 JRE），所以 `docker build` 之前必须先在本机构建好：

```bash
./gradlew clean :fly-narwhal-web:linkNativeBinary -x test
```

> 原生镜像只能为本机架构构建，因此镜像也要在与目标运行环境相同的架构上构建。

#### 使用 Docker Compose 启动
1. **确保已安装 Docker 和 Docker Compose**。
2. **在项目根目录下运行：**
   ```bash
   docker-compose up -d --build
   ```
如果使用 nvidia 显卡编解码：
1. **在宿主机安装 NVIDIA Container Toolkit**

   **参考:**

   安装 NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html)

   Linux 使用 CUDA Docker 镜像加速视频转码](https://www.cnblogs.com/myzony/p/18270956/linux-cuda-docker-video-transcoding)

2. **修改 docker-compose.nvidia.yml 文件中的挂载路径，添加媒体库路径映射**

3. **构建镜像**

```shell
docker compose -f docker-compose.nvidia.yml build
```
##### 运行

```shell
docker compose -f docker-compose.nvidia.yml up -d
```

## 🙏 特别感谢
本项目参考或使用了以下开源项目：
- [intro-skipper](https://github.com/intro-skipper/intro-skipper) - 自动检测并跳过片头/片尾字幕的 Jellyfin 插件。
- [fnos-tv](https://github.com/thshu/fnos-tv) - 基于飞牛影视接口开发的网页端
