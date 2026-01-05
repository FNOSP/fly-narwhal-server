# fly-narwhal-server

飞鲸影视客户端的服务端

## 项目简介
作为飞鲸影视客户端的后端服务，提供对电视节目的智能片头/片尾检测支持，支持黑帧检测、章节识别、声纹检测等方式来检测片头/片尾。

## 部署教程

### 准备工作
在飞牛 NAS 应用中心安装 Java 21

### 使用 releases 中打包好的 jar 包（推荐）

下载 [releases](https://github.com/FNOSP/fly-narwhal-server/releases/latest) 中的 jar 包，并将其放到飞牛 NAS 某个目录下


### 运行
1. **在终端工具中进入 Jar 包所在目录**

2. **使用 Jar 包运行 (后台)**：

   ```bash
   # 请根据实际构建出的版本号替换{version}
   nohup java -jar fly-narwhal-server-{version}.jar > /dev/null 2>&1 &
   
   # 服务默认运行在 5365 端口，如果需要更换默认端口
   nohup java -jar fly-narwhal-server-{version}.jar --server.port=8080 > /dev/null 2>&1 &
   ```

3. **停止服务**：
   
   ```bash
   kill $(lsof -t -i:5365)
   ```

### 从源码构建

#### 准备工作
- 安装 JDK 21

#### 构建 Jar 包
1. **克隆项目：**
   ```bash
   git clone https://github.com/FNOSP/fly-narwhal-server
   cd fly-narwhal-server
   ```
2. **赋予脚本执行权限**：
   ```bash
   chmod +x gradlew
   ```
3. **清理并打包**：
   ```bash
   ./gradlew clean :fly-narwhal-web:bootJar -x test
   ```

### Docker 部署

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

## 开源参考
本项目参考了 [intro-skipper](https://github.com/intro-skipper/intro-skipper) 的设计思路。
