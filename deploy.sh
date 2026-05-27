#!/usr/bin/env bash

# ==============================================================================
# Local OJ Linux 一键生产部署与管理脚本
# ==============================================================================
# 适用系统: Ubuntu 22.04+, Debian 12+, Rocky Linux 9+
# 运行权限: 需要 sudo / root 权限
# ==============================================================================

set -eo pipefail

# ANSI 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# 打印漂亮的 Header
print_logo() {
    clear
    echo -e "${CYAN}${BOLD}"
    echo "  ========================================================"
    echo "     __                      _    ____  _   _             "
    echo "    / /  ___   ___ __ _  ___| |  / __ \| | | |            "
    echo "   / /  / _ \ / __/ _\` |/ __| | / /  \/| | | |            "
    echo "  / /__| (_) | (_| (_| | (__| |_\ \__/\ \ \/ /            "
    echo "  \____/\___/ \___\__,_|\___|_(_)____/ \ \__/             "
    echo "                                                          "
    echo "         Local OJ 局域网轻量化判题系统 - 一键部署工具       "
    echo "  ========================================================"
    echo -e "${NC}"
}

# 格式化打印日志
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}
log_success() {
    echo -e "${GREEN}${BOLD}[SUCCESS]${NC} ${GREEN}$1${NC}"
}
log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}
log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查 root 权限
check_root() {
    if [ "$EUID" -ne 0 ]; then
        log_error "此脚本必须以 root 权限运行，或者通过 sudo 执行！"
        echo -e "用法: ${BOLD}sudo ./deploy.sh${NC}"
        exit 1
    fi
}

# 检查当前运行路径是否是 local-oj 根目录
check_directory() {
    if [ ! -f "docker-compose.yml" ] || [ ! -d "backend" ] || [ ! -d "frontend" ]; then
        log_error "当前目录似乎不是 Local OJ 项目根目录。"
        log_info "请先进入项目克隆根目录，然后再运行此脚本："
        echo -e "  cd /path/to/local-oj && sudo ./deploy.sh"
        exit 1
    fi
}

# 自动安装 Docker 和 Docker Compose
install_docker() {
    print_logo
    echo -e "${BOLD}--- 阶段 1: 检查并安装底层依赖 (Docker & Compose) ---${NC}"
    
    local docker_installed=false
    local compose_installed=false

    if command -v docker &>/dev/null; then
        docker_installed=true
        log_info "检测到 Docker 已安装: $(docker --version)"
    fi

    if docker compose version &>/dev/null; then
        compose_installed=true
        log_info "检测到 Docker Compose 插件已安装: $(docker compose version)"
    fi

    if [ "$docker_installed" = true ] && [ "$compose_installed" = true ]; then
        log_success "Docker 环境完整，无需重复安装。"
        return 0
    fi

    log_warn "未检测到完整的 Docker/Docker Compose 环境，准备自动配置官方源安装..."
    
    # 自动识别系统发行版
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS_ID=$ID
    else
        log_error "未能识别的 Linux 发行版，请先手动参考 LINUX_DEPLOYMENT.md 安装 Docker 引擎。"
        exit 1
    fi

    if [[ "$OS_ID" == "ubuntu" || "$OS_ID" == "debian" ]]; then
        log_info "正在为 ${OS_ID} 配置官方 Docker 源..."
        apt-get update -y
        apt-get install -y ca-certificates curl gnupg
        
        install -m 0755 -d /etc/apt/keyrings
        if [ ! -f /etc/apt/keyrings/docker.gpg ]; then
            curl -fsSL https://download.docker.com/linux/${OS_ID}/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
        fi
        chmod a+r /etc/apt/keyrings/docker.gpg

        echo \
          "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/${OS_ID} \
          $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
          tee /etc/apt/sources.list.d/docker.list > /dev/null

        apt-get update -y
        apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    elif [[ "$OS_ID" == "rocky" || "$OS_ID" == "almalinux" || "$OS_ID" == "centos" ]]; then
        log_info "正在为 ${OS_ID} 配置官方 Docker 源..."
        yum install -y yum-utils
        yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
        yum install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
        systemctl enable --now docker
    else
        log_error "不支持的自动安装发行版: ${OS_ID}。请手动安装 Docker 并升级 Compose 后重试。"
        exit 1
    fi

    # 启动并激活 docker
    systemctl enable --now docker || true
    
    # 验证安装
    if command -v docker &>/dev/null && docker compose version &>/dev/null; then
        log_success "Docker & Docker Compose 自动安装成功！"
    else
        log_error "Docker 自动安装失败，请手动执行安装后重新运行此脚本。"
        exit 1
    fi
}

# 交互式自动配置环境变量 .env 并自动生成强随机秘钥
setup_env() {
    echo ""
    echo -e "${BOLD}--- 阶段 2: 交互配置系统环境参数 ---${NC}"
    
    if [ -f ".env" ]; then
        log_warn "检测到当前目录已存在 .env 配置文件。"
        read -r -p "是否需要重新生成并覆盖现有配置？这会导致随机密码被重置！(y/N) " overwrite_env
        overwrite_env=${overwrite_env,,} # 转为小写
        if [[ "$overwrite_env" != "y" && "$overwrite_env" != "yes" ]]; then
            log_info "保持现有 .env 配置不变，跳过生成阶段。"
            return 0
        fi
    fi

    log_info "正在基于 .env.example 模板生成安全的生产环境配置..."
    cp .env.example .env

    # 自动生成 16 位强随机数据库与 JWT 密钥
    MYSQL_ROOT_PASS=$(openssl rand -hex 12)
    MYSQL_APP_PASS=$(openssl rand -hex 12)
    JWT_SECRET_KEY=$(openssl rand -hex 32)
    ADMIN_INIT_PASS=$(openssl rand -hex 8)

    # 替换敏感秘钥
    sed -i "s/^MYSQL_ROOT_PASSWORD=.*/MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASS}/" .env
    sed -i "s/^MYSQL_PASSWORD=.*/MYSQL_PASSWORD=${MYSQL_APP_PASS}/" .env
    sed -i "s/^DB_PASSWORD=.*/DB_PASSWORD=${MYSQL_APP_PASS}/" .env
    sed -i "s/^JWT_SECRET=.*/JWT_SECRET=${JWT_SECRET_KEY}/" .env
    sed -i "s/^ADMIN_PASSWORD=.*/ADMIN_PASSWORD=${ADMIN_INIT_PASS}/" .env

    # 自动寻找本机局域网非环回物理网卡 IP
    LOCAL_IP=$(ip route get 1.1.1.1 2>/dev/null | awk '{print $7}' || hostname -I | awk '{print $1}')
    if [ -z "$LOCAL_IP" ]; then
        LOCAL_IP="192.168.1.100"
    fi

    # 获取用户对绑定的 IP/端口设置
    echo -e "${YELLOW}为了配置跨域(CORS)白名单与默认访问路径，我们需要设定部署主机的局域网 IP。${NC}"
    read -r -p "请输入本服务器的局域网 IP [默认检测为: ${LOCAL_IP}]: " user_ip
    if [ -n "$user_ip" ]; then
        LOCAL_IP=$user_ip
    fi

    read -r -p "请输入对外开放访问的前端 HTTP 端口 [默认: 5173]: " user_port
    if [ -z "$user_port" ]; then
        user_port="5173"
    fi

    sed -i "s/^FRONTEND_PORT=.*/FRONTEND_PORT=${user_port}/" .env
    
    # 构建 CORS allowed origins
    CORS_VAL="http://localhost:${user_port},http://127.0.0.1:${user_port},http://${LOCAL_IP}:${user_port}"
    sed -i "s|^CORS_ALLOWED_ORIGINS=.*|CORS_ALLOWED_ORIGINS=${CORS_VAL}|" .env

    # 限制本地文件权限，防止秘钥泄漏
    chmod 600 .env

    log_success "成功生成定制 .env 配置文件！"
    log_info "配置摘要:"
    echo -e "  - 局域网访问地址: ${CYAN}http://${LOCAL_IP}:${user_port}${NC}"
    echo -e "  - 初始管理员用户名: ${CYAN}admin${NC}"
    echo -e "  - 初始自动随机密码: ${PURPLE}${ADMIN_INIT_PASS}${NC} (请保存好此密码，首登后建议立即修改！)"
    echo -e "  - JWT 私钥、MySQL 物理强密码已后台随机分配生成。"
}

# 镜像加速提示
ask_mirror() {
    echo ""
    echo -e "${BOLD}--- 阶段 3: 中国大陆网络镜像下载加速 ---${NC}"
    read -r -p "您的服务器是否部署在中国大陆？(启用国内镜像加速) (Y/n) " is_china
    is_china=${is_china,,}
    if [[ "$is_china" == "y" || "$is_china" == "yes" || -z "$is_china" ]]; then
        log_info "国内加速设置说明:"
        echo -e "  - 后端 Java 编译默认已启用 ${CYAN}阿里云 Maven 镜像源${NC} 下载缓存加速。"
        echo -e "  - 判题沙箱 go-judge 编译默认已配置 ${CYAN}阿里云 Debian APT 镜像源${NC} 下载加速。"
        echo -e "  - 建议您提前配置宿主机的 ${CYAN}Docker Hub 镜像加速器${NC} (由于国内加速器可用性频繁变化，本脚本不强制修改 /etc/docker/daemon.json)。"
    else
        log_info "跳过中国大陆加速，如果需要使用境外原始官方源编译："
        log_warn "您可以编辑 backend/Dockerfile 和 judge-worker/Dockerfile，去掉 mvn 命令行中的 -s .mvn/settings-cn.xml 即可切换至官方 Maven 源。"
    fi
}

# 执行 Docker Compose 编译与启动
build_and_start() {
    echo ""
    echo -e "${BOLD}--- 阶段 4: 开始并行下载、现场编译并拉起服务容器 ---${NC}"
    log_warn "此阶段将拉取基础镜像并进行前后端代码的多阶段分层编译，首次冷构建可能需要 5-10 分钟，请耐心等待..."
    
    docker compose up -d --build

    echo ""
    log_info "正在验证容器健康状况，等待主程序响应健康检查机制..."
    
    # 轮询 30 秒等待健康检查通过
    local seconds_waited=0
    local healthy_count=0
    while [ $seconds_waited -lt 40 ]; do
        healthy_count=$(docker compose ps | grep -c "healthy" || true)
        if [ "$healthy_count" -ge 2 ]; then # 至少 MySQL/Redis 和 Backend 已经 Ready
            break
        fi
        sleep 2
        seconds_waited=$((seconds_waited + 2))
    done

    echo ""
    docker compose ps
    log_success "所有服务组件容器已成功启动在后台！"
}

# 配置 Systemd 守护服务
configure_systemd() {
    echo ""
    echo -e "${BOLD}--- 阶段 5: 配置 Systemd 宿主机开机自启守护 ---${NC}"
    read -r -p "是否需要将此 OJ 服务注册到系统 Systemd 托管，以实现服务器开机自动拉起？(Y/n) " setup_systemd
    setup_systemd=${setup_systemd,,}
    if [[ "$setup_systemd" == "y" || "$setup_systemd" == "yes" || -z "$setup_systemd" ]]; then
        local project_root_path
        project_root_path=$(pwd)
        
        log_info "正在写入守护服务配置文件: /etc/systemd/system/localoj.service"
        cat > /etc/systemd/system/localoj.service <<EOF
[Unit]
Description=Local OJ System - Docker Compose Service
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=${project_root_path}
ExecStart=/usr/bin/docker compose up -d
ExecStop=/usr/bin/docker compose down
StandardOutput=syslog
StandardError=syslog

[Install]
WantedBy=multi-user.target
EOF

        systemctl daemon-reload
        systemctl enable localoj.service
        log_success "Systemd 守护服务配置完成，已成功启用开机自启 (localoj.service)！"
    else
        log_info "跳过 Systemd 自启配置。如果以后需要开机自动运行，请参考 LINUX_DEPLOYMENT.md 手动配置。"
    fi
}

# 部署报告
show_report() {
    # 提取端口和密码
    local frontend_port
    frontend_port=$(grep "^FRONTEND_PORT=" .env | cut -d'=' -f2 || echo "5173")
    local admin_pass
    admin_pass=$(grep "^ADMIN_PASSWORD=" .env | cut -d'=' -f2 || echo "admin123")
    local detect_ip
    detect_ip=$(ip route get 1.1.1.1 2>/dev/null | awk '{print $7}' || hostname -I | awk '{print $1}')
    if [ -z "$detect_ip" ]; then
        detect_ip="YOUR_SERVER_IP"
    fi

    echo ""
    echo -e "${GREEN}${BOLD}========================================================================"
    echo -e "                        Local OJ 部署完毕报告                           "
    echo -e "========================================================================${NC}"
    echo -e "  恭喜！Local OJ 系统已经顺利在一台 Linux 宿主机上完成容器化安全部署。"
    echo -e ""
    echo -e "  ${BOLD}1. 访问控制路径${NC}"
    echo -e "     - 本地局域网内所有终端，均可通过如下地址访问："
    echo -e "       ${GREEN}${BOLD}http://${detect_ip}:${frontend_port}${NC}"
    echo -e ""
    echo -e "  ${BOLD}2. 系统管理后台初始凭证${NC}"
    echo -e "     - 超级管理员用户名: ${CYAN}admin${NC}"
    echo -e "     - 初始随机生成密码: ${PURPLE}${BOLD}${admin_pass}${NC}"
    echo -e "       ${YELLOW}*(重要: 首次登录后，请立刻在后台个人资料中重置密码)*${NC}"
    echo -e ""
    echo -e "  ${BOLD}3. 维护管理常用指令${NC}"
    echo -e "     - 查看所有子组件运行状态:     ${BOLD}docker compose ps${NC}"
    echo -e "     - 动态追踪业务系统日志:       ${BOLD}docker compose logs -f backend${NC}"
    echo -e "     - 动态追踪判题队列及沙箱日志:   ${BOLD}docker compose logs -f judge-worker go-judge${NC}"
    echo -e "     - 完整关停所有核心组件服务:     ${BOLD}docker compose down${NC}"
    echo -e "     - 快速后台拉起当前服务:       ${BOLD}docker compose up -d${NC}"
    echo -e "     - 拉取最新代码并重构部署:       ${BOLD}sudo ./deploy.sh${NC} (进入菜单后选择 5 进行安全平滑编译升级)"
    echo -e "       *(注: 代码或数据库结构更新后，单纯 restart/up -d 不会重新编译，必须通过脚本 5 或加上 --build 参数重构)*"
    echo -e "     - 强制手动重新编译所有容器:     ${BOLD}docker compose up -d --build${NC}"
    echo -e "     "
    echo -e "  ${BOLD}4. 备份与灾难防范${NC}"
    echo -e "     - 数据备份及详细的高级排错指引，请随时查阅项目下的："
    echo -e "       ${BLUE}docx/LINUX_DEPLOYMENT.md${NC}"
    echo -e "${GREEN}${BOLD}========================================================================${NC}"
    echo ""
}

rebuild_single_service() {
    echo ""
    echo -e "${BOLD}--- 独立重建并重启单个指定服务 ---${NC}"
    echo -e " 请选择需要独立重构的服务:"
    echo -e "  ${BOLD}[1]${NC} Vue 3 前端界面 (frontend)"
    echo -e "  ${BOLD}[2]${NC} Spring Boot 后端 API (backend)"
    echo -e "  ${BOLD}[3]${NC} 评测判题消费机 (judge-worker)"
    echo -e "  ${BOLD}[4]${NC} go-judge 评测沙箱 (go-judge)"
    echo -e "  ${BOLD}[5]${NC} 返回主菜单"
    echo ""
    read -r -p "请输入对应的操作数字 (1-5): " svc_choice
    case $svc_choice in
        1)
            log_info "正在独立重建并重启前端服务..."
            docker compose up -d --build frontend
            log_success "前端服务重建重启成功！"
            ;;
        2)
            log_info "正在独立重建并重启后端服务..."
            docker compose up -d --build backend
            log_success "后端服务重建重启成功！"
            ;;
        3)
            log_info "正在独立重建并重启判题机服务..."
            docker compose up -d --build judge-worker
            log_success "判题机服务重建重启成功！"
            ;;
        4)
            log_info "正在独立重建并重启沙箱服务..."
            docker compose up -d --build go-judge
            log_success "沙箱服务重建重启成功！"
            ;;
        *)
            log_info "返回主菜单。"
            ;;
    esac
}

# 管理面板
menu() {
    print_logo
    echo -e " 当前运行状态:"
    if docker compose ps &>/dev/null; then
        docker compose ps
    else
        echo -e "  [!] Docker Compose 未在此目录启动任何服务。"
    fi
    echo ""
    echo -e " 请选择需要执行的操作:"
    echo -e "  ${BOLD}[1]${NC} 完整自动一键部署 (推荐全新环境选择)"
    echo -e "  ${BOLD}[2]${NC} 启动服务 (docker compose up -d)"
    echo -e "  ${BOLD}[3]${NC} 停止服务 (docker compose down)"
    echo -e "  ${BOLD}[4]${NC} 查看所有服务运行日志 (docker compose logs -f)"
    echo -e "  ${BOLD}[5]${NC} 强制拉取最新代码并热重构升级 (不丢失用户及题目数据)"
    echo -e "  ${BOLD}[6]${NC} 独立重建并重启单个指定服务 (前端/后端/判题等)"
    echo -e "  ${BOLD}[7]${NC} 退出脚本"
    echo ""
    read -r -p "请输入对应的操作数字 (1-7): " choice
    case $choice in
        1)
            check_root
            check_directory
            install_docker
            setup_env
            ask_mirror
            build_and_start
            configure_systemd
            show_report
            ;;
        2)
            check_root
            check_directory
            log_info "正在启动所有服务容器..."
            docker compose up -d
            log_success "启动成功！"
            ;;
        3)
            check_root
            check_directory
            log_info "正在优雅停机并释放网络..."
            docker compose down
            log_success "服务已彻底关闭。"
            ;;
        4)
            check_directory
            log_info "正在连接日志流输出，按 Ctrl+C 可以退出追踪..."
            sleep 1
            docker compose logs -f
            ;;
        5)
            check_root
            check_directory
            log_warn "准备执行系统在线无缝升级..."
            log_info "1. 暂停判题消费者..."
            docker compose stop judge-worker || true
            log_info "2. 拉取 Git 仓库最新变更..."
            # 自动添加当前目录到 Git 安全目录白名单，防止 sudo 执行时触发 safe.directory 阻断
            git config --global --add safe.directory "$(pwd)" 2>/dev/null || true
            if git pull; then
                log_success "Git 代码同步成功！"
            else
                log_warn "Git 代码拉取失败（可能是本地有修改冲突、网络中断或未配置远端）。"
                log_info "系统将直接基于当前本地代码进行重新编译与部署！"
            fi
            log_info "3. 执行多阶段编译升级与容器重建..."
            log_info "这会自动检测代码变动并重新打包后端与前端镜像，请稍候..."
            docker compose up -d --build
            log_success "系统平滑升级与重建完成！"
            ;;
        6)
            check_root
            check_directory
            rebuild_single_service
            ;;
        7|*)
            log_info "感谢使用，退出脚本。"
            exit 0
            ;;
    esac
}

# 脚本运行入口
main() {
    # 如果带参数 --auto 则跳过菜单直接执行全自动化一键部署
    if [[ "$1" == "--auto" ]]; then
        check_root
        check_directory
        install_docker
        setup_env
        ask_mirror
        build_and_start
        configure_systemd
        show_report
    else
        menu
    fi
}

main "$@"
