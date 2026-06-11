#!/usr/bin/env bash
# CodeRush OJ Linux native deployment helper.
#
# Typical usage:
#   sudo ./deploy.sh install   # first deployment on a fresh Linux server
#   sudo ./deploy.sh update    # pull/build/deploy/restart after code changes
#   sudo ./deploy.sh status
#   sudo ./deploy.sh logs backend

set -Eeuo pipefail

APP_NAME="coderush_oj"
APP_USER="${APP_USER:-coderushoj}"
INSTALL_DIR="${INSTALL_DIR:-/opt/coderush_oj}"
DATA_DIR="${DATA_DIR:-/var/lib/coderush_oj}"
WEB_DIR="${WEB_DIR:-/var/www/coderush_oj}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/coderush_oj}"
GO_JUDGE_DIR="${GO_JUDGE_DIR:-/opt/go-judge}"
GO_JUDGE_VERSION="${GO_JUDGE_VERSION:-1.12.0}"
ENV_FILE="${INSTALL_DIR}/.env"
INITIAL_SECRET_FILE="${INSTALL_DIR}/initial-admin.txt"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SERVICES=(go-judge coderushoj-backend coderushoj-worker)

GREEN=$'\033[0;32m'
YELLOW=$'\033[1;33m'
RED=$'\033[0;31m'
BLUE=$'\033[0;34m'
NC=$'\033[0m'

info() { printf "%s[INFO]%s %s\n" "$BLUE" "$NC" "$*"; }
ok() { printf "%s[ OK ]%s %s\n" "$GREEN" "$NC" "$*"; }
warn() { printf "%s[WARN]%s %s\n" "$YELLOW" "$NC" "$*" >&2; }
die() { printf "%s[FAIL]%s %s\n" "$RED" "$NC" "$*" >&2; exit 1; }

usage() {
    cat <<EOF
CodeRush OJ Linux deploy helper

Usage:
  sudo ./deploy.sh install          First-time install: deps, config, build, services, nginx
  sudo ./deploy.sh update           Pull latest code, rebuild, deploy artifacts, restart services
  sudo ./deploy.sh deploy           Build current checkout and deploy artifacts without git pull
  sudo ./deploy.sh restart          Restart go-judge, backend and worker
  sudo ./deploy.sh status           Show service status
  sudo ./deploy.sh logs [service]   Follow logs: backend | worker | sandbox | nginx | all
  sudo ./deploy.sh backup           Create DB + data backup under /var/backups/coderush_oj
  sudo ./deploy.sh rollback         Restore the latest saved program release
  sudo ./deploy.sh doctor           Check local runtime dependencies and service health

Environment overrides:
  INSTALL_DIR=/opt/coderush_oj DATA_DIR=/var/lib/coderush_oj WEB_DIR=/var/www/coderush_oj
  APP_USER=coderushoj GO_JUDGE_VERSION=1.12.0
EOF
}

need_root() {
    [[ "${EUID}" -eq 0 ]] || die "Please run this command with sudo."
}

random_hex() {
    local bytes="${1:-32}"
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -hex "$bytes"
    else
        od -An -N "$bytes" -tx1 /dev/urandom | tr -d ' \n'
    fi
}

detect_ip() {
    hostname -I 2>/dev/null | awk '{print $1}'
}

prompt_value() {
    local label="$1"
    local default="$2"
    local value
    if [[ -t 0 ]]; then
        read -r -p "${label} [${default}]: " value
        printf "%s" "${value:-$default}"
    else
        printf "%s" "$default"
    fi
}

prompt_secret() {
    local label="$1"
    local default="$2"
    local value
    if [[ -t 0 ]]; then
        read -r -s -p "${label} [press Enter to use generated value]: " value
        printf "\n" >&2
        printf "%s" "${value:-$default}"
    else
        printf "%s" "$default"
    fi
}

env_value() {
    local key="$1"
    local file="${2:-$ENV_FILE}"
    [[ -f "$file" ]] || return 1
    grep -E "^${key}=" "$file" | tail -n 1 | cut -d= -f2-
}

sql_escape() {
    printf "%s" "$1" | sed "s/'/''/g"
}

require_safe_db_name() {
    [[ "$1" =~ ^[A-Za-z0-9_]+$ ]] || die "Database name contains unsupported characters: $1"
}

repo_owner() {
    local owner
    owner="$(stat -c "%U" "$REPO_DIR" 2>/dev/null || true)"
    if [[ -n "$owner" && "$owner" != "UNKNOWN" && "$owner" != "root" ]]; then
        printf "%s" "$owner"
    elif [[ -n "${SUDO_USER:-}" && "${SUDO_USER}" != "root" ]]; then
        printf "%s" "$SUDO_USER"
    else
        printf "root"
    fi
}

run_as_repo_owner() {
    local workdir="$1"
    shift
    local owner
    owner="$(repo_owner)"
    if [[ "$owner" != "root" ]] && command -v runuser >/dev/null 2>&1; then
        runuser -u "$owner" -- bash -lc 'cd "$1" && shift && "$@"' bash "$workdir" "$@"
    else
        (cd "$workdir" && "$@")
    fi
}

is_local_host() {
    case "$1" in
        localhost|127.0.0.1|::1|"") return 0 ;;
        *) return 1 ;;
    esac
}

ensure_dirs() {
    mkdir -p "$INSTALL_DIR" "$DATA_DIR" "$WEB_DIR" "$BACKUP_DIR" "${INSTALL_DIR}/releases" "${DATA_DIR}/bin" "${DATA_DIR}/logs" "${DATA_DIR}/plagiarism"
    if ! id "$APP_USER" >/dev/null 2>&1; then
        useradd --system --home "$INSTALL_DIR" --shell /usr/sbin/nologin "$APP_USER"
    fi
    chown -R "${APP_USER}:${APP_USER}" "$DATA_DIR"
    chown -R root:"$APP_USER" "$INSTALL_DIR"
    chmod 750 "$INSTALL_DIR" "$DATA_DIR"
}

install_apt_packages() {
    if ! command -v apt-get >/dev/null 2>&1; then
        warn "This script can only auto-install packages on apt-based Linux. Please install dependencies manually."
        return
    fi

    export DEBIAN_FRONTEND=noninteractive
    info "Installing system packages when missing..."
    apt-get update
    apt-get install -y ca-certificates curl wget git unzip tar build-essential gcc g++ python3 pypy3 nginx redis-server default-mysql-client
    apt-get install -y mysql-server || apt-get install -y default-mysql-server

    if ! command -v java >/dev/null 2>&1 || ! command -v javac >/dev/null 2>&1 || [[ "$(java_major 2>/dev/null || echo 0)" -lt 21 ]]; then
        apt-get install -y openjdk-21-jdk || warn "Could not install openjdk-21-jdk automatically."
    fi

    if ! node_major_ok; then
        info "Installing Node.js 22 from NodeSource..."
        curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
        apt-get install -y nodejs
    fi
}

node_major_ok() {
    command -v node >/dev/null 2>&1 || return 1
    local major
    major="$(node -v | sed -E 's/^v([0-9]+).*/\1/')"
    [[ -n "$major" && "$major" -ge 20 ]]
}

java_major() {
    java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}'
}

check_runtime_versions() {
    command -v java >/dev/null 2>&1 || die "Java is not installed. Install JDK 21+ first."
    local java_ver
    java_ver="$(java_major)"
    [[ -n "$java_ver" && "$java_ver" -ge 21 ]] || die "Java 21+ is required, current major version is ${java_ver:-unknown}."

    command -v javac >/dev/null 2>&1 || die "javac is not installed. Install JDK 21+ first."
    command -v jar >/dev/null 2>&1 || die "jar is not installed. Install JDK 21+ first."
    command -v gcc >/dev/null 2>&1 || die "gcc is not installed."
    command -v g++ >/dev/null 2>&1 || die "g++ is not installed."
    command -v python3 >/dev/null 2>&1 || die "python3 is not installed."
    command -v pypy3 >/dev/null 2>&1 || die "pypy3 is not installed."
    node_major_ok || die "Node.js 20+ is required."
    command -v npm >/dev/null 2>&1 || die "npm is not installed."
    command -v git >/dev/null 2>&1 || die "git is not installed."
    command -v mysql >/dev/null 2>&1 || die "mysql client is not installed."
    command -v mysqldump >/dev/null 2>&1 || die "mysqldump is not installed."
    command -v redis-cli >/dev/null 2>&1 || die "redis-cli is not installed."
    command -v nginx >/dev/null 2>&1 || die "nginx is not installed."
    ok "Runtime checks passed."
}

enable_base_services() {
    systemctl enable --now redis-server >/dev/null 2>&1 || systemctl enable --now redis >/dev/null 2>&1 || warn "Could not start Redis automatically."
    systemctl enable --now mysql >/dev/null 2>&1 || systemctl enable --now mariadb >/dev/null 2>&1 || warn "Could not start MySQL automatically."
    systemctl enable --now nginx >/dev/null 2>&1 || warn "Could not start Nginx automatically."
}

create_env_if_missing() {
    ensure_dirs
    if [[ -f "$ENV_FILE" ]]; then
        ok "Keeping existing production config: $ENV_FILE"
        return
    fi

    local server_ip public_origin db_host db_port db_name db_user db_pass admin_user admin_pass jwt_secret cors
    server_ip="$(detect_ip)"
    public_origin="$(prompt_value "Public origin, for example http://your-domain.com" "http://${server_ip:-localhost}")"
    db_host="$(prompt_value "MySQL host" "localhost")"
    db_port="$(prompt_value "MySQL port" "3306")"
    db_name="$(prompt_value "MySQL database" "coderush_oj")"
    db_user="$(prompt_value "MySQL app user" "coderushoj")"
    db_pass="$(prompt_secret "MySQL app password" "$(random_hex 16)")"
    admin_user="$(prompt_value "Initial super admin username" "admin")"
    admin_pass="$(prompt_secret "Initial super admin password" "$(random_hex 12)")"
    jwt_secret="$(random_hex 32)"
    cors="$(prompt_value "CORS allowed origins" "${public_origin},http://localhost,http://127.0.0.1")"

    require_safe_db_name "$db_name"
    cat > "$ENV_FILE" <<EOF
# CodeRush OJ production config.
# Generated by deploy.sh. It is preserved by future update commands.
DB_URL=jdbc:mysql://${db_host}:${db_port}/${db_name}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
DB_USERNAME=${db_user}
DB_PASSWORD=${db_pass}
REDIS_HOST=localhost
REDIS_PORT=6379
SERVER_PORT=8080
GO_JUDGE_BASE_URL=http://localhost:5050
GO_JUDGE_CONNECT_TIMEOUT_MS=5000
GO_JUDGE_READ_TIMEOUT_MS=300000
APP_DATA_ROOT=${DATA_DIR}
SPRING_PROFILES_ACTIVE=prod
JWT_SECRET=${jwt_secret}
JWT_TTL_MINUTES=10080
ADMIN_USERNAME=${admin_user}
ADMIN_PASSWORD=${admin_pass}
CORS_ALLOWED_ORIGINS=${cors}
CONTEST_VISIBILITY_RELEASE_DELAY_MS=60000
JPLAG_JAR_PATH=${DATA_DIR}/bin/jplag.jar
JPLAG_WORKSPACE=${DATA_DIR}/plagiarism
EOF

    cat > "$INITIAL_SECRET_FILE" <<EOF
Initial CodeRush OJ administrator
Username: ${admin_user}
Password: ${admin_pass}

This file is generated only on first install. Remove it after you have saved the password.
EOF

    chown root:"$APP_USER" "$ENV_FILE" "$INITIAL_SECRET_FILE"
    chmod 640 "$ENV_FILE"
    chmod 600 "$INITIAL_SECRET_FILE"
    ok "Generated production config: $ENV_FILE"
    warn "Initial admin password was saved to $INITIAL_SECRET_FILE. Remove that file after recording it."
}

configure_database() {
    local db_url db_host db_port db_name db_user db_pass user_sql pass_sql
    db_url="$(env_value DB_URL)"
    db_user="$(env_value DB_USERNAME)"
    db_pass="$(env_value DB_PASSWORD)"
    db_host="$(printf "%s" "$db_url" | sed -E 's#^jdbc:mysql://([^:/?]+).*#\1#')"
    db_port="$(printf "%s" "$db_url" | sed -E 's#^jdbc:mysql://[^:/?]+:([0-9]+).*#\1#')"
    [[ "$db_port" =~ ^[0-9]+$ ]] || db_port="3306"
    db_name="$(printf "%s" "$db_url" | sed -E 's#^jdbc:mysql://[^/]+/([^?]+).*#\1#')"
    require_safe_db_name "$db_name"

    if ! is_local_host "$db_host"; then
        warn "DB host is ${db_host}; skipping automatic database/user creation."
        return
    fi

    if ! mysql --protocol=socket -u root -e "SELECT 1" >/dev/null 2>&1; then
        warn "Cannot access local MySQL as root through unix socket. Please create database/user manually."
        return
    fi

    user_sql="$(sql_escape "$db_user")"
    pass_sql="$(sql_escape "$db_pass")"
    info "Creating/updating local MySQL database and app user..."
    mysql --protocol=socket -u root <<SQL
CREATE DATABASE IF NOT EXISTS \`${db_name}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${user_sql}'@'localhost' IDENTIFIED BY '${pass_sql}';
ALTER USER '${user_sql}'@'localhost' IDENTIFIED BY '${pass_sql}';
GRANT ALL PRIVILEGES ON \`${db_name}\`.* TO '${user_sql}'@'localhost';
CREATE USER IF NOT EXISTS '${user_sql}'@'127.0.0.1' IDENTIFIED BY '${pass_sql}';
ALTER USER '${user_sql}'@'127.0.0.1' IDENTIFIED BY '${pass_sql}';
GRANT ALL PRIVILEGES ON \`${db_name}\`.* TO '${user_sql}'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
    ok "Database is ready: ${db_name}"
}

git_pull_if_clean() {
    [[ -d "${REPO_DIR}/.git" ]] || return
    if [[ -n "$(run_as_repo_owner "$REPO_DIR" git status --porcelain)" ]]; then
        warn "Working tree has local changes; skipping git pull. Build will use current files."
        return
    fi
    info "Pulling latest code..."
    run_as_repo_owner "$REPO_DIR" git pull --ff-only
}

build_project() {
    info "Building backend and judge worker..."
    run_as_repo_owner "$REPO_DIR" chmod +x ./mvnw
    run_as_repo_owner "$REPO_DIR" ./mvnw clean package -DskipTests

    info "Building frontend..."
    run_as_repo_owner "${REPO_DIR}/frontend" npm ci
    run_as_repo_owner "${REPO_DIR}/frontend" npm run build
    ok "Build finished."
}

latest_jar() {
    local dir="$1"
    local prefix="$2"
    find "$dir" -maxdepth 1 -type f -name "${prefix}-*.jar" ! -name "*.original" | sort | tail -n 1
}

backup_current_release() {
    local stamp release_dir
    stamp="$(date +%Y%m%d_%H%M%S)"
    release_dir="${INSTALL_DIR}/releases/${stamp}"
    if [[ -f "${INSTALL_DIR}/backend.jar" || -f "${INSTALL_DIR}/judge-worker.jar" || -d "$WEB_DIR" ]]; then
        mkdir -p "$release_dir"
        [[ -f "${INSTALL_DIR}/backend.jar" ]] && cp -a "${INSTALL_DIR}/backend.jar" "$release_dir/"
        [[ -f "${INSTALL_DIR}/judge-worker.jar" ]] && cp -a "${INSTALL_DIR}/judge-worker.jar" "$release_dir/"
        if [[ -d "$WEB_DIR" ]]; then
            mkdir -p "${release_dir}/frontend"
            cp -a "${WEB_DIR}/." "${release_dir}/frontend/" 2>/dev/null || true
        fi
        ok "Saved previous program release: $release_dir"
    fi
}

deploy_artifacts() {
    ensure_dirs
    local backend_jar worker_jar
    backend_jar="$(latest_jar "${REPO_DIR}/backend/target" "backend")"
    worker_jar="$(latest_jar "${REPO_DIR}/judge-worker/target" "judge-worker")"
    [[ -n "$backend_jar" ]] || die "Backend jar was not found. Build failed?"
    [[ -n "$worker_jar" ]] || die "Judge worker jar was not found. Build failed?"
    [[ -d "${REPO_DIR}/frontend/dist" ]] || die "Frontend dist was not found. Build failed?"

    backup_current_release
    install -m 0644 "$backend_jar" "${INSTALL_DIR}/backend.jar"
    install -m 0644 "$worker_jar" "${INSTALL_DIR}/judge-worker.jar"
    rm -rf "${WEB_DIR:?}/"*
    cp -a "${REPO_DIR}/frontend/dist/." "$WEB_DIR/"
    chown -R root:"$APP_USER" "$INSTALL_DIR"
    chown -R www-data:www-data "$WEB_DIR" 2>/dev/null || true
    ok "Program artifacts deployed."
}

download_go_judge() {
    mkdir -p "$GO_JUDGE_DIR"
    if [[ -x "${GO_JUDGE_DIR}/go-judge" ]]; then
        ok "go-judge already exists: ${GO_JUDGE_DIR}/go-judge"
        return
    fi

    local arch filename url tmp_dir archive binary
    arch="$(uname -m)"
    case "$arch" in
        x86_64|amd64) filename="go-judge_${GO_JUDGE_VERSION}_linux_amd64v2.tar.gz" ;;
        aarch64|arm64) filename="go-judge_${GO_JUDGE_VERSION}_linux_arm64.tar.gz" ;;
        *) die "Unsupported CPU architecture for automatic go-judge download: $arch" ;;
    esac
    url="https://github.com/criyle/go-judge/releases/download/v${GO_JUDGE_VERSION}/${filename}"
    tmp_dir="$(mktemp -d)"
    archive="${tmp_dir}/${filename}"
    info "Downloading go-judge ${GO_JUDGE_VERSION} for ${arch}..."
    curl -fL "$url" -o "$archive"
    tar -xzf "$archive" -C "$tmp_dir"
    binary="$(find "$tmp_dir" -maxdepth 2 -type f -name 'go-judge*' ! -name '*.tar.gz' | head -n 1)"
    [[ -n "$binary" ]] || die "Could not find go-judge binary in downloaded archive."
    install -m 0755 "$binary" "${GO_JUDGE_DIR}/go-judge"
    rm -rf "$tmp_dir"
    ok "go-judge installed: ${GO_JUDGE_DIR}/go-judge"
}

write_systemd_units() {
    cat > /etc/systemd/system/go-judge.service <<EOF
[Unit]
Description=go-judge sandbox service
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=${GO_JUDGE_DIR}
ExecStart=${GO_JUDGE_DIR}/go-judge -http-addr :5050
Restart=always
RestartSec=5
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF

    cat > /etc/systemd/system/coderushoj-backend.service <<EOF
[Unit]
Description=CodeRush OJ backend service
After=network.target mysql.service mariadb.service redis-server.service redis.service

[Service]
Type=simple
User=${APP_USER}
Group=${APP_USER}
WorkingDirectory=${INSTALL_DIR}
EnvironmentFile=${ENV_FILE}
ExecStart=/usr/bin/java -jar ${INSTALL_DIR}/backend.jar
Restart=always
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF

    cat > /etc/systemd/system/coderushoj-worker.service <<EOF
[Unit]
Description=CodeRush OJ judge worker service
After=network.target mysql.service mariadb.service redis-server.service redis.service go-judge.service
Wants=go-judge.service

[Service]
Type=simple
User=${APP_USER}
Group=${APP_USER}
WorkingDirectory=${INSTALL_DIR}
EnvironmentFile=${ENV_FILE}
ExecStart=/usr/bin/java -jar ${INSTALL_DIR}/judge-worker.jar
Restart=always
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    ok "Systemd units written."
}

write_nginx_site() {
    local site_available="/etc/nginx/sites-available/coderush_oj.conf"
    local site_enabled="/etc/nginx/sites-enabled/coderush_oj.conf"
    cat > "$site_available" <<EOF
server {
    listen 80;
    server_name _;
    client_max_body_size 256M;

    root ${WEB_DIR};
    index index.html;

    gzip on;
    gzip_min_length 1024;
    gzip_comp_level 6;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml image/svg+xml;
    gzip_vary on;

    location ~* \.(?:css|js|woff2?|svg|gif|png|jpe?g|ico|webp)$ {
        expires 1y;
        add_header Cache-Control "public, no-transform";
        try_files \$uri =404;
    }

    location /api/submissions/live {
        proxy_pass http://127.0.0.1:8080/api/submissions/live;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 1h;
        add_header X-Accel-Buffering no;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    location / {
        try_files \$uri \$uri/ /index.html;
    }
}
EOF
    ln -sfn "$site_available" "$site_enabled"
    if [[ -L /etc/nginx/sites-enabled/default ]]; then
        mv /etc/nginx/sites-enabled/default /etc/nginx/sites-enabled/default.disabled-by-coderush-oj
    fi
    nginx -t
    systemctl reload nginx || systemctl restart nginx
    ok "Nginx site configured."
}

restart_services() {
    systemctl daemon-reload
    systemctl enable "${SERVICES[@]}"
    systemctl restart "${SERVICES[@]}"
    ok "Services restarted."
}

show_status() {
    systemctl --no-pager --full status "${SERVICES[@]}" || true
}

show_logs() {
    local target="${1:-all}"
    case "$target" in
        backend) journalctl -u coderushoj-backend -f ;;
        worker) journalctl -u coderushoj-worker -f ;;
        sandbox|go-judge) journalctl -u go-judge -f ;;
        nginx) journalctl -u nginx -f ;;
        all) journalctl -u go-judge -u coderushoj-backend -u coderushoj-worker -f ;;
        *) die "Unknown log target: $target" ;;
    esac
}

health_check() {
    check_runtime_versions
    for service in "${SERVICES[@]}"; do
        if systemctl is-active --quiet "$service"; then
            ok "$service is active."
        else
            warn "$service is not active."
        fi
    done
    if curl -fsS --max-time 5 http://127.0.0.1:8080/api/system/versions >/dev/null 2>&1; then
        ok "Backend API health check passed."
    else
        warn "Backend API health check failed. Check logs with: sudo ./deploy.sh logs backend"
    fi
}

backup_data() {
    mkdir -p "$BACKUP_DIR"
    local stamp out_dir db_url db_host db_port db_name db_user db_pass
    stamp="$(date +%Y%m%d_%H%M%S)"
    out_dir="${BACKUP_DIR}/${stamp}"
    mkdir -p "$out_dir"
    db_url="$(env_value DB_URL)"
    db_host="$(printf "%s" "$db_url" | sed -E 's#^jdbc:mysql://([^:/?]+).*#\1#')"
    db_port="$(printf "%s" "$db_url" | sed -E 's#^jdbc:mysql://[^:/?]+:([0-9]+).*#\1#')"
    [[ "$db_port" =~ ^[0-9]+$ ]] || db_port="3306"
    db_name="$(printf "%s" "$db_url" | sed -E 's#^jdbc:mysql://[^/]+/([^?]+).*#\1#')"
    db_user="$(env_value DB_USERNAME)"
    db_pass="$(env_value DB_PASSWORD)"
    require_safe_db_name "$db_name"

    info "Creating backup under $out_dir..."
    if is_local_host "$db_host"; then
        MYSQL_PWD="$db_pass" mysqldump -u "$db_user" "$db_name" > "${out_dir}/db.sql"
    else
        MYSQL_PWD="$db_pass" mysqldump -h "$db_host" -P "$db_port" -u "$db_user" "$db_name" > "${out_dir}/db.sql"
    fi
    tar -C "$(dirname "$DATA_DIR")" -czf "${out_dir}/data.tar.gz" "$(basename "$DATA_DIR")"
    ok "Backup created: $out_dir"
}

rollback_latest() {
    local latest
    latest="$(find "${INSTALL_DIR}/releases" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1)"
    [[ -n "$latest" ]] || die "No saved release found under ${INSTALL_DIR}/releases."
    [[ -f "${latest}/backend.jar" ]] && cp -a "${latest}/backend.jar" "${INSTALL_DIR}/backend.jar"
    [[ -f "${latest}/judge-worker.jar" ]] && cp -a "${latest}/judge-worker.jar" "${INSTALL_DIR}/judge-worker.jar"
    if [[ -d "${latest}/frontend" ]]; then
        rm -rf "${WEB_DIR:?}/"*
        cp -a "${latest}/frontend/." "$WEB_DIR/"
    fi
    restart_services
    ok "Rolled back to: $latest"
}

install_all() {
    need_root
    install_apt_packages
    check_runtime_versions
    enable_base_services
    create_env_if_missing
    configure_database
    download_go_judge
    build_project
    deploy_artifacts
    write_systemd_units
    write_nginx_site
    restart_services
    health_check
    ok "Install finished. Open the server in your browser. Initial admin info: ${INITIAL_SECRET_FILE}"
}

deploy_current_tree() {
    need_root
    check_runtime_versions
    create_env_if_missing
    configure_database
    download_go_judge
    build_project
    deploy_artifacts
    write_systemd_units
    write_nginx_site
    restart_services
    health_check
}

update_all() {
    need_root
    git_pull_if_clean
    deploy_current_tree
    ok "Update finished."
}

main() {
    local command="${1:-}"
    case "$command" in
        install) install_all ;;
        update) update_all ;;
        deploy) deploy_current_tree ;;
        restart) need_root; restart_services ;;
        status) show_status ;;
        logs) show_logs "${2:-all}" ;;
        backup) need_root; backup_data ;;
        rollback) need_root; rollback_latest ;;
        doctor) health_check ;;
        -h|--help|help|"") usage ;;
        *) usage; die "Unknown command: $command" ;;
    esac
}

main "$@"
