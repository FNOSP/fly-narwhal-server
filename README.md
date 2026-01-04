# fly-narwhal-server

飞鲸影视客户端的服务端

## 项目简介
作为飞鲸影视客户端的后端服务，提供对电视节目的智能片头/片尾检测支持，支持黑帧检测、章节识别、声纹检测来判断片头/片尾。

## 部署教程

### 从源码构建

#### 准备工作
- 安装 JDK 21

#### 构建 Jar 包
1. 克隆项目：
   ```bash
   git clone https://github.com/FNOSP/fly-narwhal-server
   cd fly-narwhal-server
   ```
2. 赋予脚本执行权限：
   ```bash
   chmod +x gradlew
   ```
3. 清理并打包：
   ```bash
   ./gradlew clean :fly-narwhal-web:bootJar -x test
   ```

#### 本地运行
```bash
java -jar fly-narwhal-web/build/libs/fly-narwhal-web-0.0.1.jar
```
服务默认运行在 `5365` 端口。

---

### Docker 部署

#### 使用 Docker Compose 启动
1. 确保已安装 Docker 和 Docker Compose。
2. 在项目根目录下运行：
   ```bash
   docker-compose up -d --build
   ```
如果使用 nvidia 显卡编解码：
1. 在宿主机安装 NVIDIA Container Toolkit

   **参考:**

   安装 NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html)

   Linux 使用 CUDA Docker 镜像加速视频转码](https://www.cnblogs.com/myzony/p/18270956/linux-cuda-docker-video-transcoding)

2. 修改 docker-compose.nvidia.yml 文件中的挂载路径，添加媒体库路径映射

3. 构建镜像

```shell
docker compose -f docker-compose.nvidia.yml build
```
##### 运行

```shell
docker compose up -d
```

## 配置说明
- **端口**: 默认为 `5365`。
- **数据存储**: 默认使用 H2 数据库，数据文件存储在容器内的 `/app/data` 目录下。

## 开源参考
本项目参考了 [intro-skipper](https://github.com/intro-skipper/intro-skipper) 的设计思路。
