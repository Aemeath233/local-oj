# Linux 原生部署指南 (Nginx + Systemd + Native Services)

本文档介绍将 CodeRush OJ 局域网评测系统原生部署到 Linux 服务器上的方法（支持一键脚本自动部署与手动分步部署，以 Ubuntu 22.04 LTS / Debian 12 为例）。

---

## ⚡ 极速一键部署 (推荐)

我们在项目根目录下提供了一键部署脚本 `deploy.sh`。该脚本会自动完成依赖检测、代码编译、服务发布、Systemd 托管以及 Nginx 反向代理配置。

### 运行一键部署
在您的 Linux 服务器上，克隆仓库并执行以下命令：
```bash
sudo ./deploy.sh
```
按照命令行提示，输入您的数据库账号密码、设置管理员密码、设置外网IP即可。

> [!TIP]
> 部署脚本执行成功后，您可以使用以下命令诊断状态：
> * **后端日志**: `journalctl -u coderushoj-backend -f`
> * **评测日志**: `journalctl -u coderushoj-worker -f`
> * **沙箱日志**: `journalctl -u go-judge -f`

---

## 1. 系统要求与环境依赖

在部署前，请确保您的服务器已安装并配置好以下基础软件环境。

### 1.1 基础环境与工具链
```bash
sudo apt update
sudo apt install -y git build-essential gcc g++ python3 openjdk-21-jdk curl unzip
```

### 1.2 数据库与中间件
```bash
sudo apt install -y mysql-server redis-server nginx
# 启动服务并设置开机自启
sudo systemctl enable --now mysql redis-server nginx
```

---

## 2. 数据库配置

1. 登录 MySQL 控制台：
   ```bash
   sudo mysql -u root
   ```
2. 创建数据库及专用用户，并配置权限：
   ```sql
   CREATE DATABASE coderush_oj CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   
   -- 创建 coderushoj 用户，密码以 'coderushoj_pass' 为例，请修改为强密码
   CREATE USER 'coderushoj'@'localhost' IDENTIFIED BY 'coderushoj_pass';
   GRANT ALL PRIVILEGES ON coderush_oj.* TO 'coderushoj'@'localhost';
   FLUSH PRIVILEGES;
   EXIT;
   ```

---

## 3. 安装并部署 go-judge 沙箱

`go-judge` 在 Linux 上直接运行，可以无缝使用内核的 `cgroups` 和 `namespaces` 实现极其安全的强沙箱隔离。

1. **下载并存放二进制文件**：
   ```bash
   sudo mkdir -p /opt/go-judge
   cd /opt/go-judge
   # 下载 v1.12.0 Linux 版
   sudo wget https://github.com/criyle/go-judge/releases/download/v1.12.0/go-judge_1.12.0_linux_amd64v2.tar.gz
   sudo tar -zxvf go-judge_1.12.0_linux_amd64v2.tar.gz
   sudo mv go-judge_1.12.0_linux_amd64v2 go-judge
   sudo chmod +x go-judge
   sudo rm go-judge_1.12.0_linux_amd64v2.tar.gz
   ```

2. **编写 Systemd 服务配置文件**：
   创建 `/etc/systemd/system/go-judge.service` 并写入：
   ```ini
   [Unit]
   Description=Go-Judge Sandbox Service
   After=network.target
   
   [Service]
   Type=simple
   # 注意：Linux 原生沙箱限额必须使用 root 或具备 CAP_SYS_ADMIN 权限运行
   User=root
   WorkingDirectory=/opt/go-judge
   ExecStart=/opt/go-judge/go-judge -addr :5050
   Restart=always
   RestartSec=5
   LimitNOFILE=65535
   
   [Install]
   WantedBy=multi-user.target
   ```

3. **启动沙箱服务**：
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable --now go-judge
   # 验证状态
   sudo systemctl status go-judge
   ```

---

## 4. 后端与 Worker 原生部署

### 4.1 在本地编译 Jar 包并上传
在开发机上（或在服务器上装有 Maven），在项目根目录下编译打包：
```bash
./mvnw clean package -Dmaven.test.skip=true
```
编译产物位置：
* 后端 API: `backend/target/backend-0.0.1-SNAPSHOT.jar`
* 判题 Worker: `judge-worker/target/judge-worker-0.0.1-SNAPSHOT.jar`

上传这两个 jar 文件到服务器的部署目录（如 `/opt/coderush_oj`）。

### 4.2 准备生产配置环境 `.env`
在 `/opt/coderush_oj` 目录下，新建 `.env` 文件，内容如下：
```properties
# 数据库与中间件
DB_URL=jdbc:mysql://localhost:3306/coderush_oj?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
DB_USERNAME=coderushoj
DB_PASSWORD=coderushoj_pass
REDIS_HOST=localhost
REDIS_PORT=6379

# 运行端口
SERVER_PORT=8080
FRONTEND_PORT=5173

# 沙箱连接
GO_JUDGE_BASE_URL=http://localhost:5050
GO_JUDGE_CONNECT_TIMEOUT_MS=5000
GO_JUDGE_READ_TIMEOUT_MS=300000

# 数据及日志存储路径
APP_DATA_ROOT=/var/lib/coderush_oj

# 安全与授权
SPRING_PROFILES_ACTIVE=prod
# 请使用强随机生成的 JWT 密钥
JWT_SECRET=e78f902ac793d56b08fc29712a4df872f232490bca87932c028ea78f89e2182c
JWT_TTL_MINUTES=10080

# 初始超级管理员账号
ADMIN_USERNAME=admin
ADMIN_PASSWORD=your_secure_admin_password

# 跨域配置 (这里写前端能访问到的所有外网/内网 IP 或域名)
CORS_ALLOWED_ORIGINS=http://your-server-ip:5173,http://localhost:5173
CONTEST_VISIBILITY_RELEASE_DELAY_MS=60000
```
创建数据及日志存放目录：
```bash
sudo mkdir -p /var/lib/coderush_oj
sudo chmod 700 /var/lib/coderush_oj
```

### 4.3 配置后端服务的 Systemd 托管

1. **后端 API 服务**：
   创建 `/etc/systemd/system/coderushoj-backend.service`：
   ```ini
   [Unit]
   Description=CodeRush OJ Backend Service
   After=network.target mysql.service redis-server.service
   
   [Service]
   Type=simple
   User=root
   WorkingDirectory=/opt/coderush_oj
   # 载入环境变量
   EnvironmentFile=/opt/coderush_oj/.env
   ExecStart=/usr/bin/java -jar /opt/coderush_oj/backend-0.0.1-SNAPSHOT.jar
   Restart=always
   RestartSec=5
   StandardOutput=syslog
   StandardError=syslog
   SyslogIdentifier=oj-backend
   
   [Install]
   WantedBy=multi-user.target
   ```

2. **判题 Worker 服务**：
   创建 `/etc/systemd/system/coderushoj-worker.service`：
   ```ini
   [Unit]
   Description=CodeRush OJ Judge Worker Service
   After=network.target mysql.service redis-server.service go-judge.service
   
   [Service]
   Type=simple
   User=root
   WorkingDirectory=/opt/coderush_oj
   EnvironmentFile=/opt/coderush_oj/.env
   ExecStart=/usr/bin/java -jar /opt/coderush_oj/judge-worker-0.0.1-SNAPSHOT.jar
   Restart=always
   RestartSec=5
   StandardOutput=syslog
   StandardError=syslog
   SyslogIdentifier=oj-worker
   
   [Install]
   WantedBy=multi-user.target
   ```

3. **启动并使能服务**：
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable --now coderushoj-backend coderushoj-worker
   # 检查状态
   sudo systemctl status coderushoj-backend
   sudo systemctl status coderushoj-worker
   ```

---

## 5. 前端编译与 Nginx 配置

### 5.1 本地打包并上传
在开发机或本地机器的 `frontend` 目录下进行生产打包：
```bash
cd frontend
npm install
npm run build
```
打包后生成的 `frontend/dist` 文件夹，将它整体上传到服务器路径 `/var/www/coderush_oj`。

### 5.2 配置 Nginx
1. 编辑 Nginx 默认站点配置文件（通常位于 `/etc/nginx/sites-available/default`）：
   ```nginx
   server {
       listen 80;
       server_name _;
       client_max_body_size 256M;
   
       # 指向上传的前端 dist 静态资源目录
       root /var/www/coderush_oj;
       index index.html;
   
       # Gzip 压缩配置
       gzip on;
       gzip_min_length 1024;
       gzip_buffers 4 16k;
       gzip_comp_level 6;
       gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript image/svg+xml;
       gzip_vary on;
   
       # 静态资源缓存控制
       location ~* \.(?:css|js|woff2?|svg|gif|png|jpe?g|ico|webp)$ {
           expires 1y;
           add_header Cache-Control "public, no-transform";
       }
   
       # 判题 SSE 结果实时推送通道（反向代理）
       location /api/submissions/live {
           proxy_pass http://localhost:8080/api/submissions/live;
           proxy_http_version 1.1;
           proxy_set_header Connection "";
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
           proxy_buffering off;
           proxy_cache off;
           proxy_read_timeout 1h;
           add_header X-Accel-Buffering no;
       }
   
       # 业务 API 反向代理
       location /api/ {
           proxy_pass http://localhost:8080/api/;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }
   
       # 解决 SPA 页面路由刷新 404 问题
       location / {
           try_files $uri $uri/ /index.html;
       }
   }
   ```
2. 测试并重新载入 Nginx 配置：
   ```bash
   sudo nginx -t
   sudo systemctl restart nginx
   ```

现在，您可以通过 Linux 服务器的公网或局域网 IP 直接在浏览器中访问您的在线评测系统了！
