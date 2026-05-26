# Local OJ Linux 生产部署与维护手册

> **最后更新时间**: 2026-05-26  
> **文档版本**: v1.2.0  
> **适用场景**: 企业/学校内网独立部署、私有化轻量级判题系统搭建。本文档不推荐将沙箱直接暴露于公网。

---

## ✨ 极速部署：使用一键自动化部署脚本（最推荐）

为了极大简化部署难度，我们为系统编写了一个交互式的 **一键自动化部署与管理脚本** `deploy.sh`。该脚本会自动为您处理系统检测、依赖安装、安全密匙生成、服务编译拉起以及开机自启等全套工作。

### 运行一键部署

在服务器上克隆完仓库代码并进入目录后，只需执行以下一条命令：

```bash
sudo ./deploy.sh
```

> [!TIP]
> **非交互式静默部署**:  
> 如果您希望在脚本运行时跳过所有交互式提问（使用全默认检测的 IP/端口，自动生成所有随机高强度密钥并启用 Systemd 自启），可以直接运行：
> ```bash
> sudo ./deploy.sh --auto
> ```

### 脚本为您自动处理的事情：
1. **安装环境组件**：自动识别发行版（Ubuntu/Debian/Rocky Linux等），下载并安装最新官方版本的 Docker Engine 和 Docker Compose 插件。
2. **零手动配置环境变量**：复制并配置安全的 `.env` 文件。**自动随机生成**高强度安全的 `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD`、`JWT_SECRET`，并计算您的局域网内网 IP 及设定 CORS 安全跨域白名单。
3. **中国大陆网络加速**：自动融合并检测国内的 Maven 依赖加速及 go-judge 沙箱内的 APT 镜像加速器。
4. **编译与拉起服务**：自动执行 `docker compose up -d --build` 并轮询检验健康检查（Health Checks）状态，确保所有子容器（MySQL/Redis/Backend/Frontend/Worker/Sandbox）真正全绿运行。
5. **宿主机开机自启**：为您编写并注册 Systemd 系统服务单位 `localoj.service`，激活开机随 Docker 守护进程自启。
6. **多功能管理面板**：后续您也可以随时通过 `sudo ./deploy.sh` 调出交互式管理控制台，极速执行“启动系统”、“优雅关停”、“查看实时汇总日志”及“**一键热重构无缝拉取代码升级**”等操作。

---


## 1. 部署架构设计

本系统基于 Docker Compose 实现微服务化容器部署，旨在简化开发环境与 Linux 生产环境之间的迁移成本。系统由 6 个核心服务容器构成：

```text
               ┌──────────────────────────────────────────────────┐
               │                  用户浏览器 (Client)              │
               └────────────────────────┬─────────────────────────┘
                                        │ (访问前端网页)
                                        ▼ [Port 80/443/5173]
               ┌──────────────────────────────────────────────────┐
               │           Nginx 容器 (localoj-frontend)          │
               └───────┬──────────────────────────────────┬───────┘
                       │                                  │
      (静态文件及路由)  │                                  │ (/api/* 反向代理)
                       ▼                                  ▼ [Internal:8080]
     ┌───────────────────────────┐             ┌───────────────────────────┐
     │      Vue3 静态资源文件      │             │    Spring Boot 业务后台    │
     │     (dist 目录 HTML/JS)   │             │     (localoj-backend)     │
     └───────────────────────────┘             └──────┬──────────────┬─────┘
                                                      │              │
                                     (缓存/判题队列)   │              │ (数据库读取)
                                                      ▼ [Int:6379]   ▼ [Int:3306]
┌───────────────────────────┐                 ┌──────────────┐ ┌─────────────┐
│   异步判题消费者 Worker     ├────────────────►│  Redis 容器  │ │ MySQL 容器  │
│  (localoj-judge-worker)   │ (获取任务/状态)  │(localoj-redis)│ │(localoj-mysql)│
└─────────────┬─────────────┘                 └──────────────┘ └─────────────┘
              │                                       ▲
              │ (双通道提交沙箱)                       │ (Flyway 自动迁移)
              ▼ [Internal:5050]                       │
┌─────────────────────────────────────────────────────┴──────────────────────┐
│                      go-judge 沙箱容器 (localoj-go-judge)                   │
├────────────────────────────────────────────────────────────────────────────┤
│ * 隔离机制: Linux Namespace, cgroups                                       │
│ * 安全限制: 默认关闭网络访问, 严格限制 CPU、内存、进程数、输出文件大小         │
│ * 内置编译器环境: gcc, g++, openjdk-21-jdk-headless, python3               │
└────────────────────────────────────────────────────────────────────────────┘
```

### 核心服务清单
1. **`localoj-frontend`**: Vue 3 + Vite + TypeScript 前端静态资源，在容器内由 Nginx (1.27) 托管。作为对外的统一入口，负责代理 `/api` 流量到后端。
2. **`localoj-backend`**: Spring Boot (3.5.x) + Java 21 业务 API 模块。处理用户认证、题目管理、比赛管理、历史记录展示等；支持调用沙箱执行“前台代码自测”（不进队列，不记录成绩）。
3. **`localoj-judge-worker`**: 独立运行的 Java 21 判题消费者服务。从 Redis 的 `judge:queue` 队列原子移出任务至 `judge:processing`，将代码提交至 `go-judge` 执行，并比对测试点输出结果，最终将 Verdict 回写至 MySQL 并提交确认。
4. **`localoj-go-judge`**: 基于 [go-judge](https://github.com/criyle/go-judge) 的高效沙箱容器（镜像 `criyle/go-judge:v1.12.0`），通过宿主机的 cgroup 和 namespace 隔离用户提交的不可信代码。
5. **`localoj-mysql`**: MySQL 8.4 长期支持版，持久化用户及题目数据。
6. **`localoj-redis`**: Redis 7.4 缓存与消息队列服务。

---

## 2. 宿主机服务器要求

go-judge 判题沙箱强依赖 Linux 内核的底层安全隔离机制（如 **Namespaces** 和 **Control Groups / cgroups**）。为了确保沙箱可以正常且安全地限制代码的 CPU/内存占用，请严格按照以下标准选型：

### 2.1 推荐硬件配置
| 规格级别 | 适用场景 | CPU (核心数) | 内存 (RAM) | 磁盘空间 |
| :--- | :--- | :--- | :--- | :--- |
| **基础配置** | 个人测试、低频日常练习、内网开发 | 2 核 | 4 GB | 30 GB SSD |
| **标准配置** | 50人以内的日常教学、小型算法训练 | 4 核 | 8 GB | 50 GB SSD |
| **高配推荐** | 100人以上同时段竞赛、高频多并发提交 | 8 核或以上 | 16 GB或以上 | 100 GB+ NVMe |

> [!IMPORTANT]
> - **强烈建议使用 SSD/NVMe 固态硬盘**。在高并发判题场景下，沙箱在容器内读写测试点 `.in` / `.out` 样例会有高频的磁盘 I/O 交互，机械硬盘可能会成为判题吞吐量的瓶颈。
> - 系统**不支持部署在纯 Windows/macOS 生产服务器**上，因为 Docker 在非 Linux 环境中是通过轻量级虚拟机运行的，沙箱在多层虚拟化下的 cgroups 隔离与高频时钟精度可能失效或引发不可预测的资源泄漏。

### 2.2 推荐操作系统与内核
- **Ubuntu Server**: 22.04 LTS 或 24.04 LTS（首选，开箱即用支持 cgroup v2）。
- **Debian**: 12 (Bookworm) 或以上。
- **Rocky Linux / AlmaLinux**: 9.x 系列。
- **内核版本**: `Linux Kernel 5.15` 或更高版本。

---

## 3. 安装基础依赖

在干净的 Linux 系统中，首先需要更新软件包管理器并安装必备的系统依赖。

### 3.1 安装 Docker Engine 和 Docker Compose Plugin
请使用 Docker 官方源以确保安装最新的稳定版（避免使用发行版自带的老旧版本）：

```bash
# 1. 更新索引并安装前置工具
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg

# 2. 导入 Docker 官方的 GPG Key
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# 3. 添加 Docker APT 软件源
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 4. 安装 Docker 引擎、CLI 工具和 Compose 插件
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

> [!TIP]
> 如果你在非 Ubuntu/Debian 系统（如 Rocky Linux/CentOS）上部署，请参考 [Docker 官方安装指南](https://docs.docker.com/engine/install/)。

### 3.2 配置当前用户权限
为了避免每次执行 Docker 命令时都必须加上 `sudo`，应将当前运维账户加入 `docker` 用户组：

```bash
# 将当前用户加入 docker 组
sudo usermod -aG docker "$USER"

# 激活组修改（或者直接重新建立 SSH 会话连接）
newgrp docker
```

验证安装是否成功：
```bash
docker --version
docker compose version
```
必须确保 Docker Compose 版本为 **v2.20.0** 或以上。

---

## 4. 拉取私有仓库

本仓库属于私有（Private）仓库，在服务器端部署需要进行 GitHub 权限凭证认证。我们提供以下两种常用拉取方式：

### 方式 A：使用 Deploy Key（单仓库只读权限，最推荐）
1. 在部署服务器上生成一对新的 SSH 密钥：
   ```bash
   ssh-keygen -t ed25519 -C "deploy-key-localoj@server" -f ~/.ssh/id_localoj -N ""
   ```
2. 查看并复制生成的公钥内容：
   ```bash
   cat ~/.ssh/id_localoj.pub
   ```
3. 登录 GitHub 进入本项目的仓库主页，点击 **Settings** -> **Deploy keys** -> **Add deploy key**。
   - **Title**: 输入如 `Production Server Key`
   - **Key**: 粘贴上一步中复制的公钥内容
   - **Allow write access**: **不要勾选**（保持只读权限，提升安全性）
4. 在服务器配置临时 SSH 连接配置文件 `~/.ssh/config`，使之能正确使用该 Deploy Key 访问目标仓库：
   ```text
   Host github-localoj
       HostName github.com
       User git
       IdentityFile ~/.ssh/id_localoj
       IdentitiesOnly yes
   ```
5. 克隆仓库：
   ```bash
   git clone git@github-localoj:Aemeath233/local-oj.git
   cd local-oj
   ```

### 方式 B：使用 Personal Access Token (PAT) 方式
如果在无密环境下直接部署，也可以使用包含 `repo` 权限的 GitHub 经典个人访问令牌或 Fine-grained Token：
```bash
git clone https://<your-username>:<your-github-token>@github.com/Aemeath233/local-oj.git
cd local-oj
```

---

## 5. 配置 `.env` 环境变量清单

在项目根目录下，必须基于模板创建 `.env` 配置文件，并更改敏感配置项。

```bash
# 复制模板文件
cp .env.example .env

# 修改权限限制，仅允许当前所有者读写（极重要，防止秘钥泄漏）
chmod 600 .env
```

### 5.1 环境变量详细参考表

以下是系统依赖的完整环境变量清单，您需要使用 `vim .env` 或 `nano .env` 打开并根据说明逐一修改：

| 变量名称 | 默认值 | 生产环境推荐配置说明 | 是否必须修改 |
| :--- | :--- | :--- | :--- |
| **`COMPOSE_PROJECT_NAME`** | `localoj` | Docker Compose 在管理容器、Volume 时的项目名前缀。 | 否 |
| **`MYSQL_ROOT_PASSWORD`** | `localoj_root` | MySQL 的 `root` 账户最高管理员密码。请生成强随机密码。 | **是** |
| **`MYSQL_DATABASE`** | `local_oj` | 业务数据库的初始化名称。 | 否 |
| **`MYSQL_USER`** | `localoj` | 业务应用所使用的普通数据库用户名。 | 否 |
| **`MYSQL_PASSWORD`** | `localoj_pass` | 业务数据库普通用户的密码。必须为高强度随机字符串。 | **是** |
| **`DB_USERNAME`** | `localoj` | 传入 Spring Boot backend 容器的数据库连接用户名。通常保持与 `MYSQL_USER` 一致。 | 否 |
| **`DB_PASSWORD`** | `localoj_pass` | 传入 Spring Boot backend 容器的数据库连接密码。**必须与 `MYSQL_PASSWORD` 保持一致**。 | **是** |
| **`MYSQL_PORT`** | `127.0.0.1:3307` | 宿主机映射的 MySQL 端口。默认为 `127.0.0.1` 环回接口绑定以防外网扫描。若仅供容器互联，请保持默认。 | 否 |
| **`REDIS_PORT`** | `127.0.0.1:6379` | 宿主机映射的 Redis 端口。默认限制仅本机环回地址可连。 | 否 |
| **`BACKEND_PORT`** | `127.0.0.1:8080` | 后端 Spring Boot 接口在宿主机的监听地址。默认 `127.0.0.1` 确保仅可通过前端 Nginx 反向代理访问。 | 否 |
| **`FRONTEND_PORT`** | `5173` | 前端 Nginx 监听的宿主机对外端口。如果系统直接裸露访问，即为用户访问的端口（可按需改为 `80`）。 | **是** |
| **`GO_JUDGE_PORT`** | `127.0.0.1:5050` | 宿主机映射的沙箱端口。安全起见，务必保持绑定到 `127.0.0.1`，绝不能向外网开放。 | 否 |
| **`GO_JUDGE_BASE_URL`** | `http://go-judge:5050` | 业务后端在容器网络中对沙箱服务的寻址 URL。在 Compose 桥接网络中，保持默认即可。 | 否 |
| **`APP_DATA_ROOT`** | `/data` | 容器内的共享数据根目录，用于挂载题目测试数据、日志等文件。 | 否 |
| **`JUDGE_QUEUE_KEY`** | `judge:queue` | Redis 判题就绪队列的 Key。 | 否 |
| **`JUDGE_PROCESSING_KEY`**| `judge:processing`| Redis 中正在处理的判题任务队列 Key（用于防丢机制）。 | 否 |
| **`JUDGE_DLQ_KEY`** | `judge:dlq` | 死信队列 Key，存放因编译超限或内部崩溃无法解析的脏任务。 | 否 |
| **`JWT_SECRET`** | *默认明文密钥* | **用于 JWT Token 签名的对称密钥。必须替换为至少 32 字节长（建议 64 字节）的强混淆随机字符串**。 | **是** |
| **`ADMIN_USERNAME`** | `admin` | 系统首次初始化时自动插入数据库的初始超级管理员账户用户名。 | 否 |
| **`ADMIN_PASSWORD`** | `admin123` | **初始超级管理员的登录密码。首次运行前必须改为高强度的安全密码**。 | **是** |
| **`CORS_ALLOWED_ORIGINS`**| *多本地 origin 列表* | 允许跨域请求的 Origin 列表，以逗号分隔。**若通过特定内网 IP（如 `http://192.168.1.100:5173`）访问，必须将其包含在内**。 | **是** |
| **`CONTEST_VISIBILITY_...`**| `60000` | 比赛可见性判定缓存的同步延迟周期（毫秒）。 | 否 |

---

## 6. 构建与网络差异优化 (中国大陆 vs 境外服务器)

在构建阶段，不同的宿主机网络环境会极大地影响 `docker compose up --build` 的速度。本系统已在多处预先配置了加速机制：

### 6.1 Java 依赖加速 (Maven 镜像)
本项目的 Java 模块（`backend` 和 `judge-worker`）在 Dockerfile 中默认配置了基于多阶段构建的 Maven 依赖下载。  
构建文件显式引入了 `.mvn/settings-cn.xml`，配置指向 **阿里云 Maven 中央仓库镜像**（`https://maven.aliyun.com`）。
- **在中国大陆服务器部署**: 无需任何调整，可极大地加快 jar 包下载与构建。
- **在境外（如 AWS/香港/东京）服务器部署**: 若不需要阿里云镜像，可选择编辑 `backend/Dockerfile` (第 17 行) 和 `judge-worker/Dockerfile` (第 17 行)，去掉其中的 `-s .mvn/settings-cn.xml` 参数，恢复使用 Maven 官方默认源。

### 6.2 沙箱 Debian 源与网络超时
在构建 `go-judge` 时，其 Dockerfile (位于 `docker/go-judge/Dockerfile`) 在国内网络环境下若频繁发生 apt 安装失败，已通过如下两点增强健壮性：
1. **自动替换清华大学/阿里云的 Debian 镜像源**：
   ```dockerfile
   sed -i -e 's|http://deb.debian.org/debian|http://mirrors.aliyun.com/debian|g'
   ```
2. **多轮自动重试机制与延长超时**：内置了 `retry_install` 函数，在网络抖动时会自动尝试 8 次，并配合超时环境变量防止构建流程死锁中断。

---

## 7. 运行与引导服务

配置就绪后，即可通过 Docker Compose 执行完整的编译与拉起服务。

### 7.1 启动服务
在项目根目录（包含 `docker-compose.yml` 的路径）下，运行以下命令开始在后台进行编译与启动：

```bash
docker compose up -d --build
```
> **首次构建注意**：此步骤需要拉取 node、maven、temurin 等大容量基础镜像并现场编译前端代码与 Java jar 包。中国大陆内网环境首次编译大约需要 5~10 分钟，后续再次构建由于有 Docker Cache 缓存，通常仅需 20~30 秒。

### 7.2 确认容器运行状态
```bash
docker compose ps
```
确保以下容器状态全为 `Up (healthy)`（对于配置了健康检查的服务）：
```text
NAME                     IMAGE                     COMMAND                  SERVICE        CREATED         STATUS                   PORTS
localoj-backend          localoj-backend           "java -jar /app/app.…"   backend        2 minutes ago   Up 2 minutes (healthy)   127.0.0.1:8080->8080/tcp
localoj-frontend         localoj-frontend          "/docker-entrypoint.…"   frontend       2 minutes ago   Up 2 minutes             0.0.0.0:5173->80/tcp
localoj-go-judge         localoj-go-judge          "/app/judger"            go-judge       2 minutes ago   Up 2 minutes             127.0.0.1:5050->5050/tcp
localoj-judge-worker     localoj-judge-worker      "java -jar /app/app.…"   judge-worker   2 minutes ago   Up 2 minutes             
localoj-mysql            mysql:8.4                 "docker-entrypoint.s…"   mysql          2 minutes ago   Up 2 minutes (healthy)   127.0.0.1:3307->3306/tcp
localoj-redis            redis:7.4-alpine          "docker-entrypoint.s…"   redis          2 minutes ago   Up 2 minutes (healthy)   127.0.0.1:6379->6379/tcp
```

### 7.3 日志追踪与排查
若发现某些容器无法启动或状态异常，可通过日志实时排错：

```bash
# 实时滚动查看后端业务和判题消费者的运行日志
docker compose logs -f backend
docker compose logs -f judge-worker

# 单独确认沙箱容器输出
docker compose logs -f go-judge
```

---

## 8. 配置 Systemd 实现宿主机开机自启

为了防止服务器意外断电或重启导致 Online Judge 系统离线，建议在宿主机中将系统的 Docker Compose 服务注册为 Systemd 系统服务。

1. 创建并编辑 Systemd 服务配置文件：
   ```bash
   sudo vim /etc/systemd/system/localoj.service
   ```
2. 填入以下内容（**注意：务必将 `/opt/local-oj` 路径修改为你克隆代码的实际绝对路径！**）：
   ```ini
   [Unit]
   Description=Local OJ System - Docker Compose Application
   Requires=docker.service
   After=docker.service

   [Service]
   Type=oneshot
   RemainAfterExit=yes
   WorkingDirectory=/opt/local-oj
   ExecStart=/usr/bin/docker compose up -d
   ExecStop=/usr/bin/docker compose down
   StandardOutput=syslog
   StandardError=syslog

   [Install]
   WantedBy=multi-user.target
   ```
3. 保存并退出后，重载 Systemd 并激活自启：
   ```bash
   # 重载系统服务配置
   sudo systemctl daemon-reload

   # 启用开机自启
   sudo systemctl enable localoj.service

   # 手动启动/停止/重启服务测试
   sudo systemctl start localoj
   sudo systemctl status localoj
   ```

---

## 9. 局域网防火墙与网络安全策略 (UFW)

为了确保判题机内部组件（如 MySQL, Redis, go-judge 沙箱等）不被外界恶意扫描或渗透攻击，必须在宿主机上对外部网络端口的暴露做严格限制。

本系统在 `.env.example` 和 `docker-compose.yml` 中默认对非前端端口做了宿主机本机的环回地址监听限制（即前缀 `127.0.0.1:`）。

### 9.1 开放/阻断端口矩阵说明
| 容器服务 | 容器内部端口 | 默认映射宿主机地址端口 | 内网对外暴露建议 | 理由说明 |
| :--- | :--- | :--- | :--- | :--- |
| **frontend** | `80` | `0.0.0.0:5173` | **开放 (ALLOW)** | 用户浏览器直接访问的 HTTP 入口。 |
| **backend** | `8080` | `127.0.0.1:8080` | **禁止开放 (DENY)** | 所有的 API 交互应强制经过前端 Nginx 反向代理，防止旁路注入。 |
| **go-judge** | `5050` | `127.0.0.1:5050` | **绝对禁止 (DENY)** | 沙箱 API 如果泄漏，任何普通人都可以向其发送任意代码并控制运行！ |
| **mysql** | `3306` | `127.0.0.1:3307` | **禁止开放 (DENY)** | 保护数据库，只能通过宿主机 SSH 隧道连接。 |
| **redis** | `6379` | `127.0.0.1:6379` | **禁止开放 (DENY)** | Redis 判题队列裸连具有极高的未授权访问安全风险。 |

### 9.2 UFW（Ubuntu 默认防火墙）安全指令配置
```bash
# 1. 默认拒绝所有外部入站请求
sudo ufw default deny incoming
sudo ufw default allow outgoing

# 2. 必须允许 SSH 运维端口（否则会断开远程连接！）
sudo ufw allow 22/tcp

# 3. 仅允许访问前端服务的统一入口（例如配置 FRONTEND_PORT 为 5173）
sudo ufw allow 5173/tcp

# 4. 如果宿主机安装了 Nginx 代理，请改开 80 和 443
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# 5. 激活防火墙
sudo ufw enable
sudo ufw status verbose
```

---

## 10. 生产环境反向代理与 HTTPS / SSL 部署 (可选)

虽然本系统多用于内网，但如果需要配置自定义域名访问（如 `http://oj.local`）或启用加密的 HTTPS 安全通道，推荐在宿主机部署独立的 Nginx 实例进行反向代理。

### 10.1 宿主机 Nginx 配置文件示例
创建 Nginx 虚拟主机配置文件：
```bash
sudo vim /etc/nginx/sites-available/localoj
```
写入配置内容：
```nginx
server {
    listen 80;
    server_name oj.local; # 替换为你内网的 DNS 域名或服务器静态 IP

    # 题目 ZIP 包和头像上传可能会达到几十MB，必须调大限制（默认为 1MB 会报 413 Payload Too Large）
    client_max_body_size 128M;

    location / {
        # 将流量转发给前端 Nginx 容器监听的端口
        proxy_pass http://127.0.0.1:5173; 
        
        # 传递真实客户端 IP 头以在后台日志中追踪提交来源
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket 支持（便于今后可能的判题结果实时轮询）
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

启用此站点：
```bash
sudo ln -s /etc/nginx/sites-available/localoj /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

### 10.2 配置 SSL (HTTPS) 自签名证书 (适用于纯内网)
如果在内网不能直接获取 Let's Encrypt 等权威证书，可以使用 OpenSSL 自行生成内网 SSL 证书：
```bash
# 创建自签名证书存放目录
sudo mkdir -p /etc/nginx/ssl

# 生成有效期为 10 年的证书和私钥
sudo openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
  -keyout /etc/nginx/ssl/localoj.key \
  -out /etc/nginx/ssl/localoj.crt \
  -subj "/C=CN/ST=Shanghai/L=Shanghai/O=LocalOJ/CN=oj.local"
```
在 Nginx 配置中增加 HTTPS 监听及证书路径引用即可：
```nginx
server {
    listen 443 ssl;
    server_name oj.local;

    ssl_certificate /etc/nginx/ssl/localoj.crt;
    ssl_certificate_key /etc/nginx/ssl/localoj.key;

    client_max_body_size 128M;

    location / {
        proxy_pass http://127.0.0.1:5173;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

# 同时配置 80 端口自动重定向到 HTTPS
server {
    listen 80;
    server_name oj.local;
    return 301 https://$host$request_uri;
}
```

---

## 11. 数据持久化与存储目录结构

系统内部所有的状态、题目文件和日志均被持久化映射到 Docker 的卷（Volumes）中，以确保容器销毁重建时数据完全不受影响。

宿主机目录通常对应关系如下（在标准的 Docker Linux 环境下）：
```text
/var/lib/docker/volumes/
  ├── localoj_mysql-data/             # MySQL 的原始底层物理表文件
  ├── localoj_redis-data/             # Redis 持久化 AOF/RDB 快照目录
  └── localoj_oj-data/                # 本 OJ 题目及附件的最核心的存储卷
        ├── logs/                     # 后端输出的日志文件夹 (如 backend.log)
        └── problems/                 # 后端存放题目的物理路径
              └── {problem_id}/       
                    └── cases/        # 每个题目解压出的真实 .in / .out 测试数据点文件
```

> [!WARNING]
> 不要直接通过宿主机直接向 `problems/{problem_id}/cases/` 目录拷贝文件，所有的题目导入动作，请在管理员后台前端 UI 界面中通过规范的 “ZIP 压缩包” 导入或“手动维护”交互进行。否则，MySQL 数据库中的关系记录将与文件存储发生不同步，导致测试数据校验失效。

---

## 12. 数据库与测试用例备份及恢复流程

为了防止不可抗力的数据损坏或硬件物理故障，建议将以下备份流程写入 Cron 定时任务，每日执行。

在项目根目录下新建一个备份执行目录：
```bash
mkdir -p /opt/local-oj/backups
```

### 12.1 手动备份脚本
创建一个备份脚本 `backup.sh` 并设置执行权限：

```bash
#!/bin/bash
# 配置参数
PROJECT_DIR="/opt/local-oj"
BACKUP_DIR="${PROJECT_DIR}/backups"
DATE_TAG=$(date +%Y%m%d_%H%M%S)

# 加载 .env 环境变量
cd "${PROJECT_DIR}"
set -a
source .env
set +a

# 1. 导出 MySQL 数据库
docker compose exec -T mysql \
  mysqldump -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${MYSQL_DATABASE}" \
  > "${BACKUP_DIR}/mysql_dump_${DATE_TAG}.sql"

# 2. 打包题目物理测试用例数据
docker run --rm \
  -v "${COMPOSE_PROJECT_NAME:-localoj}_oj-data:/data:ro" \
  -v "${BACKUP_DIR}:/backup" \
  alpine \
  tar czf "/backup/oj_data_files_${DATE_TAG}.tar.gz" -C /data .

# 3. 自动删除 30 天前的历史旧备份
find "${BACKUP_DIR}" -name "mysql_dump_*" -mtime +30 -delete
find "${BACKUP_DIR}" -name "oj_data_files_*" -mtime +30 -delete

echo "Backup success: ${DATE_TAG}"
```
```bash
chmod +x /opt/local-oj/backups/backup.sh
```
通过 `crontab -e` 可以添加以下定时配置，使系统在每日凌晨 3 点自动运行备份：
```text
0 3 * * * /opt/local-oj/backups/backup.sh >> /opt/local-oj/backups/backup.log 2>&1
```

### 12.2 数据灾难恢复流程

当宿主机物理损坏重装系统，或者需要将数据迁移到新物理机时，可遵循以下流程完整恢复状态。

#### 第一步：克隆代码，准备 `.env` 环境，构建并初始化新容器
1. 将备份的 `mysql_dump_xxx.sql` 和 `oj_data_files_xxx.tar.gz` 传输至新机器的 `/opt/local-oj/backups/`。
2. 保持 `.env` 中的 `MYSQL_PASSWORD` / `JWT_SECRET` 等敏感秘钥与原备份主机完全一致。
3. 运行基础服务：
   ```bash
   docker compose up -d mysql redis go-judge
   ```
   *注意：此时不要先运行 `backend` 和 `judge-worker`，防止它们向空白数据库写入错乱的数据或创建冲突的表结构。*

#### 第二步：恢复 MySQL 数据库
```bash
# 将备份的 SQL 文件灌入新实例
docker compose exec -T mysql \
  mysql -u"${MYSQL_USER:-localoj}" -p"${MYSQL_PASSWORD:-localoj_pass}" "${MYSQL_DATABASE:-local_oj}" \
  < /opt/local-oj/backups/mysql_dump_xxx.sql
```

#### 第三步：恢复题目测试用例物理 Volume
```bash
# 启动一个临时的 Alpine 容器挂载 oj-data 卷，清空并解压备份数据
docker run --rm \
  -v "localoj_oj-data:/data" \
  -v "/opt/local-oj/backups:/backup" \
  alpine \
  sh -c 'rm -rf /data/* && tar xzf /backup/oj_data_files_xxx.tar.gz -C /data'
```

#### 第四步：拉起整个核心应用
数据库和测试用例到位后，启动剩下的业务容器：
```bash
docker compose up -d
```
系统将完全继承之前的数据并重新投入运行。

---

## 13. 版本无缝升级策略

当有新的功能合并到仓库后，可以通过以下命令快速升级而不丢失任何历史用户及判题数据：

```bash
# 1. 临时停止消费者，防止升级打包期间有判题请求卡在处理态
docker compose stop judge-worker

# 2. 备份当前数据（防范不可知升级 bug 导致结构损坏）
/opt/local-oj/backups/backup.sh

# 3. 拉取最新代码
git pull origin main

# 4. 全量重新构建编译镜像，后台运行拉起
docker compose up -d --build
```

### Flyway 数据库表自动迁移机制
本系统使用 **Flyway** 控制数据库的版本演进。在 `docker compose up -d` 重新构建并运行后，后端 `localoj-backend` 在启动阶段会自动检索 `db/migration` 下最新的 `.sql` 脚本，并在数据库中原子应用更新，不需要运维人员手动执行任何 SQL 语句。

如果您需要排查升级失败原因，可以随时查看后端启动阶段的日志输出：
```bash
docker compose logs backend | grep -i "flyway"
```

---

## 14. 常见问题排查与技术 FAQ

### Q1: 用户提交代码后状态一直停留在 `Pending`，没有任何返回结果
1. **排查后台管理面板**: 
   - 登录超级管理员账号，进入**“日志”**页面（管理员专享后台）。
   - 过滤条件选择 `ERROR` 或搜索该 `submissionId`。日志中会打印该任务是卡在 Redis 入队、消费者 `judge-worker` 连接不上，还是沙箱计算时报了错。
2. **确认消息队列服务状态**:
   - 运行 `docker compose ps` 确认 `localoj-redis` 处于正常服务态。
   - 连接进入 Redis 检查 `judge:queue` 长度是否积压：
     ```bash
     docker compose exec redis redis-cli LLEN judge:queue
     ```
3. **消费者 Worker 是否离线**:
   - 检查 `localoj-judge-worker` 的日志。它是否不断抛出 `JedisConnectionException`？如果是，说明是 Redis 配置地址错误。
   - 确认 `judge-worker` 能否正确解析沙箱服务域名：
     ```bash
     docker compose exec judge-worker ping go-judge
     ```
4. **沙箱权限或系统调用被宿主机拦截（极重要）**:
   - 在某些启用了极度严格的安全内核模块（如 **SELinux** 或宿主机自带的 **AppArmor**）的 Linux 系统上，可能会阻止容器中的 `go-judge` 创建底层子命名空间（Namespace）。
   - **排查**: 运行 `docker compose logs go-judge`。如果输出提示 `unshare... Operation not permitted` 或 `cgroup... not found`，请编辑宿主机 `/etc/default/grub` 以开启内核的 cgroup 功能，或者在临时测试中关闭 SELinux 观察是否恢复：
     ```bash
     sudo setenforce 0  # 临时关闭 CentOS 的 SELinux 验证
     ```

### Q2: 导入题目 ZIP 压缩包时上传失败，出现 `413 Payload Too Large` 报错
1. **原因**: Nginx 或 Spring Boot 默认对上传的单次文件容量有硬性限制。当题目的测试数据包含大规模输入输出文件（例如 30MB+ 的 ZIP 压缩包）时，请求会在入口被拦截。
2. **已置配置保证**:
   - 本项目的**容器内 Nginx 配置文件**中已明确配置：
     ```nginx
     client_max_body_size 128M;
     ```
   - 后端配置 `application.yml` 的 multipart 上传大小也已设为 `128MB`。
3. **额外排查**: 如果你在宿主机外层又额外包裹了一层反向代理 Nginx，必须在宿主机的反向代理 `server {}` 或者 `http {}` 区块中同样加入一行配置：
   ```nginx
   client_max_body_size 128M;
   ```
   然后运行 `sudo systemctl reload nginx`。

### Q3: 用户浏览器请求报跨域错误 (CORS Error)
1. **原因**: 前端和后端请求源地址不匹配。本系统默认启用 CORS 域名白名单校验。
2. **解决方法**: 
   - 检查你的 `.env` 文件中的 `CORS_ALLOWED_ORIGINS` 变量。
   - 如果用户通过域名 `http://oj.mycompany.lan` 访问，或者直接通过局域网 IP `http://192.168.1.10:5173` 访问，**必须**将该完整的前缀包含在该变量列表中，以英文逗号分隔。
   - 注意：如果配置有修改，必须执行 `docker compose up -d` 重新拉起后端容器以生效。

### Q4: go-judge 判题时抛出编译错误 (CE)，提示编译器缺失
1. **确认支持的语言**:  
   当前的 `go-judge` 构建环境默认安装并支持以下语言编译环境：
   - **C** (`gcc`)
   - **C++** (`g++`)
   - **Java 21** (`openjdk-21-jdk-headless` 中的 `javac` 和 `java`)
   - **Python 3** (`python3`)
2. **自定义扩展语言编译器**:  
   如果后续需要支持如 Go, Rust 或 Pascal 等更多语言，请编辑 `docker/go-judge/Dockerfile` 的第 31 行，在 `retry_install` 中添加所需的 Linux 原生编译器包（如 `golang` 或 `rustc`），然后运行：
   ```bash
   docker compose build --no-cache go-judge
   docker compose up -d
   ```

---

## 15. 部署后验证与日常巡检清单

在部署完成或者每次版本重大升级后，应当进行如下的一轮测试，确保所有子功能链路完整通畅：

- [ ] **物理容器自检**: 执行 `docker compose ps`，确认 6 个服务全部存活，且 `localoj-mysql` 和 `localoj-redis` 处于 `healthy` 状态。
- [ ] **前端入口访问**: 通过浏览器打开部署地址（例如 `http://服务器IP:5173`），能正确渲染现代化的登录界面，未发现静态资源 404 加载失败。
- [ ] **首次管理员登入**: 使用 `.env` 中预先设定的 `ADMIN_USERNAME` 和 `ADMIN_PASSWORD` 可以成功登入系统。
- [ ] **题库管理功能**: 管理员可以在后台“题目管理”页面成功通过 ZIP 导入测试题目（检查测试点配对状态正常且能自动解包）。
- [ ] **自测运行 (Self-Test)**: 打开任意一题，在代码编辑器中输入样例代码，点击“自测运行”。确认能返回正确的编译及各测试点运行状态，且**绝对不会**在主判题排行榜、用户提交历史记录中产生脏数据。
- [ ] **正式代码提交判题 (Submit Verdict)**: 在题目下选择正式提交一段 A+B 问题的代码：
  - 确认提交后，系统显示短暂的 `Pending` 或 `Judging`。
  - 数秒内状态自动刷新为 `Accepted` (AC)。
  - 在“提交历史”中能够清晰查阅每一个单独测试数据点的详细耗时 (ms) 及内存占用 (MB)。
- [ ] **排行榜与统计刷新**: 用户成功 AC 题目后，系统排行榜（Leaderboard）中的题量排名和用户个人资料中的 AC 数值能够立刻发生增量变更。
- [ ] **内网防护验证**: 在外部其他电脑上运行 `telnet 服务器IP 3307` 和 `telnet 服务器IP 5050`。**必须全部被拒绝 (Connection Refused)**，以验证核心敏感数据库与沙箱只对本地及容器局域网开放。
