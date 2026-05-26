# Local OJ Linux 部署文档

Last reviewed: 2026-05-23

本文档面向内网 Linux 部署。目标是把 GitHub 私有仓库中的当前版本部署成一套可长期维护的服务，而不是公网大规模平台。

## 1. 部署结构

默认使用 Docker Compose 启动以下服务：

```text
browser
  -> frontend nginx:80
       -> /api proxy to backend:8080

backend
  -> mysql:3306
  -> redis:6379
  -> go-judge:5050 for self-test

judge-worker
  -> redis:6379
  -> mysql:3306
  -> go-judge:5050 for official submissions
```

Compose 服务：

- `frontend`: Vue 构建产物 + Nginx，用户主要访问入口。
- `backend`: Spring Boot API，负责登录、题库、提交、管理后台等业务逻辑。
- `judge-worker`: Redis 队列消费者，负责正式提交判题。
- `go-judge`: 沙箱执行服务，必须只给后端和 worker 使用。
- `mysql`: 业务数据库。
- `redis`: 判题队列、processing 队列和 DLQ。

## 2. 服务器要求

建议起步配置：

- Linux x86_64，推荐 Ubuntu 22.04/24.04 LTS、Debian 12 或同类发行版。
- CPU: 2 核起步，比赛或多人同时提交建议 4 核以上。
- 内存: 4 GB 起步，建议 8 GB 以上。
- 磁盘: 30 GB 起步，题目数据和提交量增长后需要更多空间。
- 已安装 Git、Docker Engine、Docker Compose plugin。

go-judge 依赖 Linux namespace/cgroup 等隔离能力。正式部署不要选择 Windows 服务器。

## 3. 安装基础依赖

Ubuntu/Debian 可参考下面的基础命令。Docker 版本以 Docker 官方文档为准。

```bash
sudo apt update
sudo apt install -y ca-certificates curl git

curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"
```

执行 `usermod` 后重新登录 SSH，再确认 Docker 可用：

```bash
docker version
docker compose version
```

## 4. 拉取私有仓库

仓库是 private，服务器需要能认证 GitHub。二选一即可。

方式 A：GitHub CLI 登录。如果服务器尚未安装 `gh`，先按 GitHub CLI 官方文档安装，或改用下面的 SSH 方式。

```bash
gh auth login
git clone https://github.com/Aemeath233/local-oj.git
cd local-oj
```

方式 B：SSH key 或 deploy key。

```bash
git clone git@github.com:Aemeath233/local-oj.git
cd local-oj
```

## 5. 配置环境变量

复制示例配置：

```bash
cp .env.example .env
chmod 600 .env
```

至少修改以下值：

```env
MYSQL_ROOT_PASSWORD=replace-with-strong-root-password
MYSQL_PASSWORD=replace-with-strong-app-password
DB_PASSWORD=replace-with-strong-app-password
JWT_SECRET=replace-with-at-least-32-random-bytes
ADMIN_USERNAME=admin
ADMIN_PASSWORD=replace-with-initial-admin-password
```

注意：

- `MYSQL_PASSWORD` 和 `DB_PASSWORD` 必须保持一致，除非你手动改了数据库用户。
- `ADMIN_PASSWORD` 只用于首次创建管理员账号。管理员账号已经创建后，再改 `.env` 不会自动改数据库里的密码，之后请在系统里改密码或手动处理数据库。
- `JWT_SECRET` 必须换掉默认值，否则任何拿到代码的人都知道你的签名密钥。

如果通过服务器 IP 和默认前端端口访问，例如 `http://192.168.1.10:5173`，建议设置：

```env
FRONTEND_PORT=5173
CORS_ALLOWED_ORIGINS=http://192.168.1.10:5173,http://localhost:5173,http://127.0.0.1:5173
```

如果通过域名访问，例如 `http://oj.lan`，建议设置：

```env
FRONTEND_PORT=127.0.0.1:5173
CORS_ALLOWED_ORIGINS=http://oj.lan
```

`.env.example` 默认把 MySQL、Redis、backend、go-judge 绑定到 `127.0.0.1`，避免在内网里裸露这些端口。正常用户只需要访问 frontend。

## 6. 启动服务

首次启动：

```bash
docker compose up -d --build
```

查看状态：

```bash
docker compose ps
```

查看日志：

```bash
docker compose logs -f backend
docker compose logs -f judge-worker
docker compose logs -f go-judge
```

如果使用默认端口，访问：

```text
http://服务器IP:5173
```

默认管理员账号来自 `.env`：

```text
ADMIN_USERNAME / ADMIN_PASSWORD
```

## 7. 反向代理可选方案

如果希望用户直接访问 `http://oj.lan` 或 `https://oj.example.com`，可以让 Compose 的 frontend 只监听本机：

```env
FRONTEND_PORT=127.0.0.1:5173
```

然后用宿主机 Nginx 代理到 frontend：

```nginx
server {
    listen 80;
    server_name oj.lan;

    client_max_body_size 100m;

    location / {
        proxy_pass http://127.0.0.1:5173;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

反向代理层只需要代理 frontend。前端容器里的 Nginx 会继续把 `/api` 转发给 backend。

## 8. 防火墙建议

内网部署也不要把沙箱和数据库裸露给普通用户。

若直接用 `FRONTEND_PORT=5173`，只需要允许访问前端端口：

```bash
sudo ufw allow 5173/tcp
```

如果使用宿主机 Nginx，则只开放 80/443：

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
```

不要对普通用户开放：

- MySQL: `3306` 或映射后的 `3307`
- Redis: `6379`
- backend: `8080`
- go-judge: `5050`

## 9. 数据目录和持久化

当前 Compose 使用 named volumes：

- `mysql-data`: MySQL 数据。
- `redis-data`: Redis 数据。
- `oj-data`: 题目测试点文件，容器内路径 `/data`。

题目测试点会落在：

```text
/data/problems/{problemId}/cases/
```

这条路径是容器内路径，对应宿主机上的 `oj-data` Docker volume。

## 10. 备份

建议先创建备份目录：

```bash
mkdir -p backups
```

备份数据库：

```bash
set -a
source .env
set +a

docker compose exec -T mysql \
  mysqldump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" \
  > "backups/local_oj_$(date +%F_%H%M%S).sql"
```

备份题目数据 volume：

```bash
docker run --rm \
  -v "${COMPOSE_PROJECT_NAME:-localoj}_oj-data:/data:ro" \
  -v "$PWD/backups:/backup" \
  alpine \
  tar czf "/backup/oj-data_$(date +%F_%H%M%S).tar.gz" -C /data .
```

恢复数据库前建议先停掉业务服务：

```bash
docker compose stop backend judge-worker
docker compose exec -T mysql \
  mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" \
  < backups/local_oj_xxx.sql
docker compose start backend judge-worker
```

恢复题目数据 volume 示例：

```bash
docker compose stop backend judge-worker
docker run --rm \
  -v "${COMPOSE_PROJECT_NAME:-localoj}_oj-data:/data" \
  -v "$PWD/backups:/backup" \
  alpine \
  sh -c 'rm -rf /data/* && tar xzf /backup/oj-data_xxx.tar.gz -C /data'
docker compose start backend judge-worker
```

## 11. 更新版本

常规更新：

```bash
git pull
docker compose up -d --build
```

查看是否迁移成功：

```bash
docker compose logs --tail=200 backend
docker compose ps
```

数据库迁移由 backend 启动时的 Flyway 自动执行。更新前请先做数据库和 `oj-data` 备份。

## 12. 常见排查

提交一直 pending：

先进入管理员后台的“日志”页面，按 `submissionId` 或 `ERROR` 级别筛选。它会显示提交创建、入队、worker 消费、开始判题、判题完成或内部异常等关键事件。

再看容器日志：

```bash
docker compose logs -f judge-worker
docker compose logs -f redis
docker compose logs -f go-judge
```

重点看 worker 是否连接 Redis、MySQL、go-judge，以及 go-judge 是否启动成功。

页面能打开但接口失败：

```bash
docker compose logs -f frontend
docker compose logs -f backend
```

确认 frontend 容器能通过服务名访问 `backend:8080`。浏览器请求应该走同源 `/api`，不需要直接访问 `http://服务器IP:8080`。

管理员账号密码不符合 `.env`：

- `.env` 里的 `ADMIN_PASSWORD` 只影响首次初始化。
- 已初始化后请在系统内修改密码。
- 如果是纯测试环境想重置全部数据，可以删除 Docker volume 后重启，但这会清空数据库和题目数据。

```bash
docker compose down -v
docker compose up -d --build
```

go-judge 构建失败或编译器缺失：

```bash
docker compose build --no-cache go-judge
docker compose logs -f go-judge
```

当前 go-judge 镜像会安装 `gcc`、`g++`、`openjdk-21-jdk-headless`、`python3`，如果以后新增语言，需要同步更新 `docker/go-judge/Dockerfile`。

## 13. 安全注意事项

- 不要把 `.env` 提交到 GitHub。
- 不要把 go-judge 暴露给普通用户。
- 不要把 Docker socket 挂进 judge 相关容器。
- 不要把数据库账号密码传入 go-judge 容器。
- 后台 SMTP 密码、JWT secret、数据库密码都要使用真实随机值。
- 即使是内网，用户提交的代码也按不可信代码处理。

## 14. 部署后检查清单

- `docker compose ps` 中所有服务为 running/healthy。
- `http://服务器IP:5173` 或域名可打开首页。
- 管理员可以登录。
- 管理后台可以看到系统设置。
- 新建或导入一道题目后，样例和测试点可保存。
- 提交 A+B 能从 pending 变成 AC。
- 自测能返回运行结果，且不生成正式提交记录。
- `go-judge:5050`、`mysql`、`redis` 不对普通用户开放。
