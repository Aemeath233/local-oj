#!/usr/bin/env bash
# ==============================================================================
# CodeRush OJ Linux Native One-Click Deployment Script
# Author: Antigravity
# Description: Automates maven build, node compile, directories, systemd & nginx
# ==============================================================================

set -eo pipefail

# Color Output Helpers
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}======================================================================${NC}"
echo -e "${BLUE}          CodeRush OJ Linux Native One-Click Deployer                 ${NC}"
echo -e "${BLUE}======================================================================${NC}"

# 1. Prerequisite: Root Check
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}❌ 错误: 此脚本必须以 root 权限运行。请使用: sudo $0${NC}"
    exit 1
fi

# 2. Verify Command Dependencies
echo -e "\n${BLUE}Step 1: 检查系统必备指令与依赖环境...${NC}"

check_cmd() {
    if ! command -v "$1" &> /dev/null; then
        echo -e "${RED}❌ 未检测到指令 '$1'。请参考部署文档安装相应的依赖软件（OpenJDK 21, Node.js, MySQL, Redis, Nginx）。${NC}"
        exit 1
    fi
}

check_cmd java
check_cmd node
check_cmd npm
check_cmd mysql
check_cmd redis-cli
check_cmd nginx
check_cmd unzip
check_cmd wget

# Java version verification (Must be Java 21+)
JAVA_VER=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | awk -F '.' '{print $1}')
if [ -z "$JAVA_VER" ] || [ "$JAVA_VER" -lt 21 ]; then
    echo -e "${RED}❌ 错误: 系统当前 Java 版本低于 21。请配置 JDK 21+ 环境变量后再行部署！${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Java 版本检测通过: JVM $JAVA_VER${NC}"
echo -e "${GREEN}✓ 各依赖软件工具已安装就绪。${NC}"

# 3. Interactive configuration prompts
echo -e "\n${BLUE}Step 2: 配置数据库与系统参数...${NC}"

read -rp "请输入 MySQL 主机名/IP [默认: localhost]: " DB_HOST
DB_HOST=${DB_HOST:-localhost}

read -rp "请输入 MySQL 端口 [默认: 3306]: " DB_PORT
DB_PORT=${DB_PORT:-3306}

read -rp "请输入 MySQL 数据库名 [默认: coderush_oj]: " DB_NAME
DB_NAME=${DB_NAME:-coderush_oj}

read -rp "请输入 MySQL 用户名 [默认: coderushoj]: " DB_USER
DB_USER=${DB_USER:-coderushoj}

read -sp "请输入 MySQL 密码 [默认: coderushoj_pass]: " DB_PASS
echo ""
DB_PASS=${DB_PASS:-coderushoj_pass}

read -rp "请设置 OJ 超级管理员用户名 [默认: admin]: " ADMIN_USER
ADMIN_USER=${ADMIN_USER:-admin}

read -sp "请设置 OJ 超级管理员密码 [默认: admin123]: " ADMIN_PASS
echo ""
ADMIN_PASS=${ADMIN_PASS:-admin123}

read -rp "请输入前端可访问的外网/局域网 IP 或域名 (多个用逗号隔开) [默认: http://localhost:5173]: " CORS_ORIGINS
CORS_ORIGINS=${CORS_ORIGINS:-http://localhost:5173}

# 4. Create Database if not exists
echo -e "\n${BLUE}正在自动配置 MySQL 数据库...${NC}"
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" -e "CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" || {
    echo -e "${YELLOW}⚠️ 警告: 无法使用提供凭证自动创建数据库。这可能因为数据库已被手动建立或当前用户权限不足。${NC}"
}

# 5. Build Project
echo -e "\n${BLUE}Step 3: 开始编译后端 API 与判题 Worker Jar 包...${NC}"
chmod +x ./mvnw
./mvnw clean package -DskipTests

echo -e "\n${BLUE}Step 4: 开始编译前端静态资源包...${NC}"
cd frontend
npm install
npm run build
cd ..

# 6. Set up directories
echo -e "\n${BLUE}Step 5: 创建系统目录并释放文件...${NC}"
mkdir -p /opt/coderush_oj
mkdir -p /var/lib/coderush_oj
mkdir -p /var/www/coderush_oj

chmod 700 /var/lib/coderush_oj

# Copy jars
cp backend/target/backend-0.1.0-SNAPSHOT.jar /opt/coderush_oj/backend.jar
cp judge-worker/target/judge-worker-0.1.0-SNAPSHOT.jar /opt/coderush_oj/judge-worker.jar
echo -e "${GREEN}✓ 后端与 Worker JAR 包已部署至 /opt/coderush_oj${NC}"

# Copy frontend
rm -rf /var/www/coderush_oj/*
cp -r frontend/dist/* /var/www/coderush_oj/
echo -e "${GREEN}✓ 前端静态资源包已部署至 /var/www/coderush_oj${NC}"

# Generate .env configuration file
JWT_SECRET=$(head -c 32 /dev/urandom | xxd -p)
cat <<EOF > /opt/coderush_oj/.env
# ==============================================================================
# CodeRush OJ Linux Production Environment Configuration
# ==============================================================================

# Database Settings
DB_URL=jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
DB_USERNAME=${DB_USER}
DB_PASSWORD=${DB_PASS}

# Redis Settings
REDIS_HOST=localhost
REDIS_PORT=6379

# Service Ports
SERVER_PORT=8080
FRONTEND_PORT=5173

# Sandbox Configs
GO_JUDGE_BASE_URL=http://localhost:5050
GO_JUDGE_CONNECT_TIMEOUT_MS=5000
GO_JUDGE_READ_TIMEOUT_MS=300000

# File Storage & Logs path
APP_DATA_ROOT=/var/lib/coderush_oj

# Spring Profiles & Security
SPRING_PROFILES_ACTIVE=prod
JWT_SECRET=${JWT_SECRET}
JWT_TTL_MINUTES=10080

# Seed Administrator
ADMIN_USERNAME=${ADMIN_USER}
ADMIN_PASSWORD=${ADMIN_PASS}

# CORS Allowed Origins
CORS_ALLOWED_ORIGINS=${CORS_ORIGINS}
CONTEST_VISIBILITY_RELEASE_DELAY_MS=60000

# JPlag Config
JPLAG_JAR_PATH=/var/lib/coderush_oj/bin/jplag.jar
JPLAG_WORKSPACE=/var/lib/coderush_oj/plagiarism
EOF
chmod 600 /opt/coderush_oj/.env
echo -e "${GREEN}✓ 生产环境配置文件已生成并加密: /opt/coderush_oj/.env${NC}"

# 7. Setup go-judge sandbox
echo -e "\n${BLUE}Step 6: 下载并配置 go-judge 判题沙箱...${NC}"
mkdir -p /opt/go-judge
cd /opt/go-judge

ARCH=$(uname -m)
DOWNLOAD_URL=""
if [ "$ARCH" = "x86_64" ]; then
    DOWNLOAD_URL="https://github.com/criyle/go-judge/releases/download/v1.12.0/go-judge_1.12.0_linux_amd64v2.tar.gz"
elif [[ "$ARCH" = "aarch64" || "$ARCH" = "arm64" ]]; then
    DOWNLOAD_URL="https://github.com/criyle/go-judge/releases/download/v1.12.0/go-judge_1.12.0_linux_arm64.tar.gz"
else
    echo -e "${RED}❌ 错误: 不支持的 CPU 架构: $ARCH，请手动下载 go-judge！${NC}"
    exit 1
fi

echo -e "正在下载沙箱二进制文件 ($ARCH)..."
wget -qO go-judge.tar.gz "$DOWNLOAD_URL"
tar -zxf go-judge.tar.gz
# Find and extract the binary
find . -name "go-judge*" -type f -exec mv {} ./go-judge \;
chmod +x ./go-judge
rm -f go-judge.tar.gz
cd - > /dev/null
echo -e "${GREEN}✓ go-judge 沙箱下载并提取完成。${NC}"

# 8. Create systemd services
echo -e "\n${BLUE}Step 7: 注册系统 Systemd 服务...${NC}"

# go-judge systemd unit
cat <<EOF > /etc/systemd/system/go-judge.service
[Unit]
Description=Go-Judge Sandbox Service
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/go-judge
ExecStart=/opt/go-judge/go-judge -addr :5050
Restart=always
RestartSec=5
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF

# backend API systemd unit
cat <<EOF > /etc/systemd/system/coderushoj-backend.service
[Unit]
Description=CodeRush OJ Backend Service
After=network.target mysql.service redis-server.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/coderush_oj
EnvironmentFile=/opt/coderush_oj/.env
ExecStart=/usr/bin/java -jar /opt/coderush_oj/backend.jar
Restart=always
RestartSec=5
StandardOutput=syslog
StandardError=syslog
SyslogIdentifier=oj-backend

[Install]
WantedBy=multi-user.target
EOF

# worker systemd unit
cat <<EOF > /etc/systemd/system/coderushoj-worker.service
[Unit]
Description=CodeRush OJ Judge Worker Service
After=network.target mysql.service redis-server.service go-judge.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/coderush_oj
EnvironmentFile=/opt/coderush_oj/.env
ExecStart=/usr/bin/java -jar /opt/coderush_oj/judge-worker.jar
Restart=always
RestartSec=5
StandardOutput=syslog
StandardError=syslog
SyslogIdentifier=oj-worker

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now go-judge coderushoj-backend coderushoj-worker
echo -e "${GREEN}✓ Systemd 判题沙箱、后端服务及评测机已成功注册并启动！${NC}"

# 9. Configure Nginx Virtual Host
echo -e "\n${BLUE}Step 8: 配置 Nginx 反向代理...${NC}"

cat <<'EOF' > /etc/nginx/sites-available/default
server {
    listen 80;
    server_name _;
    client_max_body_size 256M;

    # 指向前端构建出的静态目录
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

    # 判题 SSE 结果实时推送反代
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

    # 业务 API 反代
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # SPA 路由 fallback 机制
    location / {
        try_files $uri $uri/ /index.html;
    }
}
EOF

nginx -t
systemctl restart nginx
echo -e "${GREEN}✓ Nginx 配置及重启载入完成。${NC}"

# Final Banner
echo -e "\n${GREEN}======================================================================${NC}"
echo -e "${GREEN}🎉 部署完成! CodeRush OJ 已在本机运行！${NC}"
echo -e "${GREEN}👉 前端访问主页: http://localhost:80 (或者您的服务器公网/局域网IP)${NC}"
echo -e "${GREEN}👉 后端接口地址: http://localhost:8080/api/${NC}"
echo -e "${GREEN}👉 判题沙箱端口: http://localhost:5050${NC}"
echo -e "${GREEN}======================================================================${NC}"
echo -e "日志状态诊断:"
echo -e "  - 查看后端日志: journalctl -u coderushoj-backend -f"
echo -e "  - 查看评测日志: journalctl -u coderushoj-worker -f"
echo -e "  - 查看沙箱日志: journalctl -u go-judge -f"
echo -e "${GREEN}======================================================================${NC}"
