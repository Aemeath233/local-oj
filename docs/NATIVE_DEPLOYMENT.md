# Linux 原生部署指南

本文档面向“Windows 开发、Linux 部署”的生产/内网服务器场景。推荐使用项目根目录的 `deploy.sh`，它会完成依赖安装、配置生成、数据库初始化、编译发布、Systemd 托管、Nginx 反向代理、备份和回滚。

支持环境以 Ubuntu 22.04/24.04、Debian 12 这类 apt 系 Linux 为主。其他发行版也可以部署，但系统依赖需要手工安装。

---

## 1. 首次一键部署

在 Linux 服务器上执行：

```bash
git clone <repository-url> coderush_oj
cd coderush_oj
chmod +x deploy.sh
sudo ./deploy.sh install
```

脚本会提示你确认或填写：

- 站点访问地址，例如 `http://192.168.1.10` 或 `https://oj.example.com`
- MySQL 地址、库名、应用账号和密码
- 初始超级管理员账号和密码
- 允许跨域访问的前端地址

首次安装完成后，浏览器访问服务器 IP 或域名即可进入系统。

初始管理员信息会保存在：

```bash
/opt/coderush_oj/initial-admin.txt
```

记录好账号密码后，建议删除这个文件：

```bash
sudo rm /opt/coderush_oj/initial-admin.txt
```

---

## 2. 后续更新

从 GitHub 拉取最新代码、重新构建并重启服务：

```bash
cd coderush_oj
sudo ./deploy.sh update
```

`update` 会尽量执行 `git pull --ff-only`。如果服务器仓库里存在本地改动，脚本会跳过拉取，直接用当前代码构建，避免覆盖现场改动。

如果你已经手动拉好了代码，只想部署当前工作区：

```bash
sudo ./deploy.sh deploy
```

更新前建议先做一次备份：

```bash
sudo ./deploy.sh backup
```

数据库结构变更由 Flyway 在后端启动时自动迁移。

---

## 3. 常用运维命令

查看服务状态：

```bash
sudo ./deploy.sh status
```

查看日志：

```bash
sudo ./deploy.sh logs backend
sudo ./deploy.sh logs worker
sudo ./deploy.sh logs sandbox
sudo ./deploy.sh logs nginx
sudo ./deploy.sh logs all
```

重启全部服务：

```bash
sudo ./deploy.sh restart
```

检查依赖与健康状态：

```bash
sudo ./deploy.sh doctor
```

创建备份：

```bash
sudo ./deploy.sh backup
```

回滚到上一个程序版本：

```bash
sudo ./deploy.sh rollback
```

---

## 4. 目录约定

脚本默认使用以下路径：

| 路径 | 说明 |
|------|------|
| `/opt/coderush_oj` | 后端 jar、Worker jar、生产配置、历史发布版本 |
| `/opt/coderush_oj/.env` | 生产环境配置，后续更新会保留 |
| `/opt/coderush_oj/releases` | 每次发布前保存的上一版程序文件 |
| `/var/lib/coderush_oj` | 测试数据、日志、查重工作区等运行时数据 |
| `/var/www/coderush_oj` | 前端静态文件 |
| `/var/backups/coderush_oj` | 数据库与运行时数据备份 |
| `/opt/go-judge` | go-judge 沙箱二进制文件 |

可以通过环境变量覆盖默认路径：

```bash
sudo INSTALL_DIR=/srv/coderush_oj DATA_DIR=/data/coderush_oj ./deploy.sh install
```

---

## 5. 脚本会安装和管理什么

首次安装时，脚本会自动处理：

- Java 21 JDK、Node.js 20+、npm、Git、GCC/G++、Python3、PyPy3
- MySQL、Redis、Nginx
- go-judge v1.12.0，并以 `-http-addr :5050` 运行
- `coderushoj-backend`、`coderushoj-worker`、`go-judge` 三个 Systemd 服务
- Nginx 站点配置，前端静态资源走 `/`，后端 API 走 `/api/`

脚本会创建系统用户 `coderushoj` 来运行后端和 Worker。go-judge 需要 Linux 沙箱能力，默认以 root 运行。

---

## 6. 生产配置

生产配置位于：

```bash
/opt/coderush_oj/.env
```

常用配置项：

```properties
DB_URL=jdbc:mysql://localhost:3306/coderush_oj?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
DB_USERNAME=coderushoj
DB_PASSWORD=your_db_password
REDIS_HOST=localhost
REDIS_PORT=6379
SERVER_PORT=8080
GO_JUDGE_BASE_URL=http://localhost:5050
APP_DATA_ROOT=/var/lib/coderush_oj
SPRING_PROFILES_ACTIVE=prod
JWT_SECRET=replace_with_a_long_random_secret
JWT_TTL_MINUTES=10080
ADMIN_USERNAME=admin
ADMIN_PASSWORD=replace_with_initial_admin_password
CORS_ALLOWED_ORIGINS=http://your-server-ip
CONTEST_VISIBILITY_RELEASE_DELAY_MS=60000
JPLAG_JAR_PATH=/var/lib/coderush_oj/bin/jplag.jar
JPLAG_WORKSPACE=/var/lib/coderush_oj/plagiarism
```

更新时不会覆盖 `.env`。如果你修改了数据库、域名、跨域地址或数据目录，改完后重启服务：

```bash
sudo ./deploy.sh restart
```

---

## 7. 备份与回滚

创建备份：

```bash
sudo ./deploy.sh backup
```

备份会写入 `/var/backups/coderush_oj/<timestamp>/`，包含：

- `db.sql`：MySQL 数据库导出
- `data.tar.gz`：运行时数据目录压缩包

程序回滚：

```bash
sudo ./deploy.sh rollback
```

回滚只恢复上一版 jar 和前端静态文件，不会回滚数据库结构和业务数据。因此大版本升级前，仍然建议先执行 `backup`。

---

## 8. 网络与安全建议

- 内网部署时，确认学生端能访问服务器 80 端口。
- 公网部署时，建议额外配置 HTTPS，可以使用 Nginx + Certbot 或放在已有网关之后。
- 首次登录后立即修改超级管理员密码。
- 不要把 `/opt/coderush_oj/.env` 和 `/opt/coderush_oj/initial-admin.txt` 上传到 Git。
- 如果使用远程 MySQL，请先在数据库侧创建账号并开放网络访问，脚本会跳过远程数据库自动建库。

---

## 9. 手动部署提示

正常情况下不需要手动部署。若你要自定义部署平台，可以参考 `deploy.sh` 中的 Systemd 与 Nginx 模板。关键约定如下：

- 后端服务监听 `127.0.0.1:8080`
- go-judge 监听 `127.0.0.1:5050`
- go-judge v1.12 使用参数 `-http-addr :5050`
- 前端生产产物来自 `frontend/dist`
- 后端和 Worker 需要读取同一份生产 `.env`
