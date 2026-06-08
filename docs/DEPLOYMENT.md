# 部署指南

本文档介绍 CodeRush OJ 系统的生产环境部署方案、环境变量配置、运维管理及数据备份恢复。

---

## 1. 部署方式概览

| 方式 | 适用场景 | 说明 |
|------|---------|------|
| **Linux 一键部署** | 生产/正式环境 (推荐) | `sudo ./deploy.sh` 交互式全自动部署 |
| **Windows/macOS 控制台** | 本地开发/小规模使用 | `python start.py` 图形化控制台 |
| **手动 Docker Compose** | 自定义部署 | 手动配置 `.env` + `docker compose up -d --build` |

---

## 2. Linux 生产部署 (推荐)

### 2.1 系统要求

| 项目 | 最低要求 | 推荐配置 |
|------|---------|---------|
| **操作系统** | Ubuntu 22.04+ / Debian 12+ / Rocky 9+ | Ubuntu 24.04 LTS |
| **CPU** | 2 核 | 4 核+ |
| **内存** | 4 GB | 8 GB+ |
| **磁盘** | 20 GB | 50 GB+ SSD |
| **权限** | root 或 sudo | root |

### 2.2 一键部署步骤

```bash
# 1. 克隆代码
git clone <repository-url>
cd coderush_oj

# 2. 运行部署脚本
sudo ./deploy.sh
```

部署脚本会自动执行以下步骤：

| 阶段 | 操作 |
|------|------|
| **阶段 1** | 检查并自动安装 Docker & Docker Compose |
| **阶段 2** | 交互式配置环境变量 (自动生成强随机密码) |
| **阶段 3** | 中国大陆网络镜像加速配置提示 |
| **阶段 4** | Docker Compose 并行构建并启动 6 个容器 |
| **阶段 5** | 配置 Systemd 开机自启守护 (可选) |
| **完成** | 显示部署报告 (访问地址、管理员密码等) |

### 2.3 deploy.sh 管理菜单

部署完成后，可随时再次运行 `sudo ./deploy.sh` 进入管理菜单：

```
[1] 完整自动一键部署 (推荐全新环境选择)
[2] 启动服务
[3] 停止服务
[4] 查看运行日志
[5] 拉取最新代码并热重构升级
[6] 独立重建并重启单个指定服务
[7] 退出
```

---

## 3. Windows / macOS 部署

### 3.1 前置要求

- 安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- 安装 Python 3.8+
- 确保 Docker Desktop 已启动并处于运行状态

### 3.2 使用交互式控制台

```bash
python start.py
```

控制台菜单功能：

| 选项 | 功能 |
|------|------|
| 1 | 一键启动整个系统 (增量/热启动) |
| 2 | 重新编译并启动整个系统 (全量构建) |
| 3 | 独立重建单个服务 |
| 4 | 关闭系统 |
| 5 | 系统监控台 (实时刷新容器状态) |
| 6 | 查看服务日志 |
| 7 | 全量清理 (含数据删除) |
| 8 | 一键备份与恢复 |

也支持命令行参数直接操作：

```bash
python start.py up        # 启动
python start.py down      # 停止
python start.py build     # 重新构建并启动
python start.py status    # 查看监控台
python start.py backup    # 创建备份
python start.py restore   # 恢复备份
```

---

## 4. 环境变量配置

所有配置通过项目根目录的 `.env` 文件管理。首次部署前，复制模板并修改：

```bash
cp .env.example .env
```

### 4.1 完整变量说明

#### 数据库配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MYSQL_ROOT_PASSWORD` | `localoj_root` | MySQL root 密码 |
| `MYSQL_DATABASE` | `local_oj` | 数据库名 |
| `MYSQL_USER` | `localoj` | 应用连接用户 |
| `MYSQL_PASSWORD` | `localoj_pass` | 应用连接密码 |
| `MYSQL_PORT` | `127.0.0.1:3307` | MySQL 对外端口 |
| `DB_USERNAME` | `localoj` | Spring Boot JDBC 用户 (应与 MYSQL_USER 一致) |
| `DB_PASSWORD` | `localoj_pass` | Spring Boot JDBC 密码 (应与 MYSQL_PASSWORD 一致) |

#### 服务端口

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `FRONTEND_PORT` | `5173` | 前端对外端口 (对外全部 IP 监听) |
| `BACKEND_PORT` | `127.0.0.1:8080` | 后端端口 (默认仅本机访问) |
| `REDIS_PORT` | `127.0.0.1:6379` | Redis 端口 |
| `GO_JUDGE_PORT` | `127.0.0.1:5050` | go-judge 端口 |

> **安全提示**: 生产环境中，`BACKEND_PORT`、`REDIS_PORT`、`GO_JUDGE_PORT` 应绑定 `127.0.0.1`，仅通过 Nginx 反向代理对外暴露前端端口。

#### 认证配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `JWT_SECRET` | `change-this-...` | JWT 签名密钥 (**生产环境必须修改为强随机字符串**) |
| `JWT_TTL_MINUTES` | `10080` (7天) | JWT Token 有效期 |
| `ADMIN_USERNAME` | `admin` | 初始超级管理员用户名 |
| `ADMIN_PASSWORD` | `admin123` | 初始超级管理员密码 |

#### 跨域配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,...` | 允许的跨域来源 (逗号分隔) |

配置示例 (局域网部署)：
```
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://192.168.1.100:5173
```

#### 沙箱配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `GO_JUDGE_BASE_URL` | `http://go-judge:5050` | go-judge API 地址 |
| `GO_JUDGE_CONNECT_TIMEOUT_MS` | `5000` | 连接超时 (毫秒) |
| `GO_JUDGE_READ_TIMEOUT_MS` | `300000` | 读取超时 (须大于最大编译耗时) |

#### 判题队列配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `JUDGE_QUEUE_KEY` | `judge:queue` | Redis 待判题队列 Key |
| `JUDGE_PROCESSING_KEY` | `judge:processing` | Redis 处理中队列 Key |
| `JUDGE_DLQ_KEY` | `judge:dlq` | Redis 死信队列 Key |
| `APP_WORKER_ID` | `judge-worker-1` | Worker 实例 ID |

#### 其他配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `APP_DATA_ROOT` | `/data` | 容器内数据挂载路径 |
| `CONTEST_VISIBILITY_RELEASE_DELAY_MS` | `60000` | 竞赛结束后题目可见性延迟释放 (毫秒) |

---

## 5. Docker 容器管理

### 5.1 常用命令

```bash
# 启动所有服务
docker compose up -d

# 停止所有服务
docker compose down

# 查看服务状态
docker compose ps

# 查看某个服务的日志
docker compose logs -f backend
docker compose logs -f judge-worker
docker compose logs -f go-judge

# 重新构建并启动所有服务
docker compose up -d --build

# 仅重建某个服务
docker compose up -d --build frontend
docker compose up -d --build backend

# 进入容器 Shell
docker exec -it localoj-backend sh
docker exec -it localoj-mysql mysql -u localoj -p local_oj
```

### 5.2 服务容器一览

| 容器名 | 镜像 | 端口 | 健康检查 |
|--------|------|------|---------|
| localoj-mysql | mysql:8.4 | 3307→3306 | mysqladmin ping |
| localoj-redis | redis:7.4-alpine | 6379→6379 | redis-cli ping |
| localoj-go-judge | 自构建 (criyle/go-judge) | 5050→5050 | — |
| localoj-backend | 自构建 (Spring Boot) | 8080→8080 | — |
| localoj-judge-worker | 自构建 (Spring Boot) | — | — |
| localoj-frontend | 自构建 (Nginx) | 5173→80 | — |

### 5.3 数据持久化

所有数据以 bind mount 方式挂载到项目根目录 `./data/`：

```
./data/mysql/    → MySQL 数据文件
./data/redis/    → Redis 持久化文件
./data/oj/       → 测试数据 + 应用日志
```

> 停止/删除容器不会丢失数据。仅 `docker compose down -v` 或手动删除 `./data/` 会清除数据。

---

## 6. 版本升级

### 6.1 Linux (通过 deploy.sh)

```bash
sudo ./deploy.sh
# 选择 [5] 拉取最新代码并热重构升级
```

脚本会自动:
1. 暂停 judge-worker (避免判题中断)
2. `git pull` 拉取最新代码
3. `docker compose up -d --build` 增量重建变更的服务
4. 恢复所有服务

### 6.2 手动升级

```bash
# 1. 拉取最新代码
git pull

# 2. 重新构建并启动
docker compose up -d --build
```

> **注意**: 仅 `docker compose restart` 或 `docker compose up -d` **不会**重新编译代码。代码变更后必须加 `--build` 参数。

---

## 7. 数据备份与恢复

### 7.1 使用 start.py (推荐)

```bash
# 创建备份
python start.py backup

# 恢复备份
python start.py restore
```

交互式菜单支持：
- **冷备份** (推荐): 自动停容器 → 压缩 data/ → 重新启动
- **热备份**: 不停机直接压缩 (可能包含不完整的数据库事务)

备份文件保存在 `./backups/` 目录下，格式为 `backup_localoj_YYYYMMDD_HHMMSS.zip`。

### 7.2 手动备份

```bash
# 1. 停止服务 (保证数据一致性)
docker compose down

# 2. 压缩数据目录
tar czf backup_$(date +%Y%m%d).tar.gz data/

# 3. 重新启动
docker compose up -d
```

### 7.3 恢复数据

```bash
# 1. 停止服务
docker compose down

# 2. 备份当前数据 (以防万一)
mv data data.old

# 3. 解压备份
tar xzf backup_YYYYMMDD.tar.gz

# 4. 启动服务
docker compose up -d
```

### 7.4 跨机器迁移

1. 在源机器上创建备份 zip
2. 将 zip 文件复制到目标机器的项目根目录或 `backups/` 目录
3. 在目标机器上运行 `python start.py restore`

---

## 8. 网络架构 (生产环境)

### 8.1 推荐的网络拓扑

```
局域网客户端 (教室内电脑/手机)
     │
     │  HTTP :5173
     ▼
┌─────────────────────────┐
│   服务器 (宿主机)        │
│                         │
│   Nginx (容器 :80)      │  ← 对外暴露 FRONTEND_PORT
│     ├─ 静态资源          │
│     └─ /api/* → :8080   │  ← 容器内部网络
│                         │
│   其他服务 (仅 127.0.0.1)│  ← 不对外暴露
│     ├─ Backend :8080    │
│     ├─ MySQL :3307      │
│     ├─ Redis :6379      │
│     └─ go-judge :5050   │
└─────────────────────────┘
```

### 8.2 安全注意事项

1. **生产环境必须修改默认密码**
   - `JWT_SECRET` — 使用 `openssl rand -hex 32` 生成
   - `ADMIN_PASSWORD` — 使用强随机密码
   - `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD` — 使用强随机密码

2. **端口绑定**
   - 前端端口 (`FRONTEND_PORT`) 绑定 `0.0.0.0` (对局域网可达)
   - 其他所有端口绑定 `127.0.0.1` (仅容器间内部通信)

3. **防火墙**
   - 仅开放前端端口 (默认 5173)
   - 其余端口全部关闭外部访问

---

## 9. 故障排查

### 9.1 常见问题

| 问题 | 排查方法 |
|------|---------|
| 前端页面无法加载 | 检查 `docker compose ps` 确认 frontend 容器运行中 |
| API 请求 502/504 | 检查 backend 容器日志 `docker compose logs backend` |
| 提交代码后一直 PENDING | 检查 judge-worker 和 go-judge 日志 |
| 数据库连接失败 | 确认 mysql 容器 healthy，检查 DB_USERNAME/DB_PASSWORD |
| 首次构建镜像太慢 | 使用 `deploy.sh` 阶段 3 启用中国大陆镜像加速 |
| Windows 下 data/ 被锁 | 关闭所有打开该目录的程序 (VS Code, 文件管理器) |

### 9.2 日志位置

| 日志 | 位置 |
|------|------|
| 后端应用日志 | `./data/oj/logs/backend.log` |
| Docker 容器日志 | `docker compose logs -f <service>` |
| MySQL 日志 | `docker compose logs -f mysql` |

### 9.3 重置系统

```bash
# 警告: 以下操作会删除所有数据！
docker compose down -v
rm -rf data/
docker compose up -d --build
```

---

## 10. Systemd 服务管理 (Linux)

如果通过 `deploy.sh` 配置了 Systemd 自启：

```bash
# 查看服务状态
systemctl status localoj

# 启动服务
systemctl start localoj

# 停止服务
systemctl stop localoj

# 查看日志
journalctl -u localoj
```
