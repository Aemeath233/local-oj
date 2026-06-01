#!/usr/bin/env python3
import os
import sys
import time
import subprocess
import re
import shutil

# Force stdout/stderr to use UTF-8 encoding to avoid UnicodeEncodeError on Windows CP936/GBK environments
if hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
if hasattr(sys.stderr, 'reconfigure'):
    try:
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

# Initialize ANSI color codes on Windows
if os.name == 'nt':
    os.system('color')

# Color Constants
GREEN = '\033[92m'
BLUE = '\033[94m'
YELLOW = '\033[93m'
RED = '\033[91m'
BOLD = '\033[1m'
UNDERLINE = '\033[4m'
CYAN = '\033[96m'
MAGENTA = '\033[95m'
RESET = '\033[0m'
CLEAR_SCREEN = '\033[H\033[2J'

SERVICES = [
    {"service": "mysql", "name": "localoj-mysql", "desc": "MySQL 8.4 数据库"},
    {"service": "redis", "name": "localoj-redis", "desc": "Redis 7.4 缓存与队列"},
    {"service": "go-judge", "name": "localoj-go-judge", "desc": "go-judge 评测沙箱"},
    {"service": "backend", "name": "localoj-backend", "desc": "Spring Boot 后端 API"},
    {"service": "judge-worker", "name": "localoj-judge-worker", "desc": "评测判题消费机"},
    {"service": "frontend", "name": "localoj-frontend", "desc": "Vue 3 前端界面"}
]

def check_requirements():
    """Verify docker, docker daemon, and docker-compose are running."""
    print(f"{BLUE}🔍 正在检查本地运行环境...{RESET}")
    
    # 1. Check if Docker CLI is installed
    if not shutil.which("docker"):
        print(f"{RED}❌ 错误: 未检测到 Docker CLI，请先下载并安装 Docker Desktop！{RESET}")
        return False
        
    # 2. Check if Docker Daemon (Docker Desktop) is actually running
    try:
        res_info = subprocess.run(["docker", "info"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="ignore")
        if res_info.returncode != 0:
            print(f"\n{RED}❌ 错误: 检测到 Docker 守护进程 (Docker Daemon) 并未启动！{RESET}")
            
            # Auto-start Docker Desktop on Windows
            docker_desktop_path = r"C:\Program Files\Docker\Docker\Docker Desktop.exe"
            if os.name == 'nt' and os.path.exists(docker_desktop_path):
                print(f"{CYAN}💡 系统检测到您的电脑上安装了 Docker Desktop，路径为：{RESET}")
                print(f"   {UNDERLINE}{docker_desktop_path}{RESET}")
                confirm = input(f"\n{YELLOW}{BOLD}是否由本脚本为您自动启动 Docker Desktop？ (Y/N): {RESET}").strip()
                if confirm.upper() == 'Y':
                    print(f"\n{BLUE}🚀 正在为您在后台启动 Docker Desktop...{RESET}")
                    # Launch Docker Desktop in background
                    subprocess.Popen([docker_desktop_path], shell=True)
                    
                    print(f"{YELLOW}⏳ 正在等待 Docker 守护进程就绪（这可能需要 15 ~ 45 秒，请观察任务栏 Docker 图标）...{RESET}")
                    max_retries = 25
                    for attempt in range(1, max_retries + 1):
                        time.sleep(3)
                        res_poll = subprocess.run(["docker", "info"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="ignore")
                        if res_poll.returncode == 0:
                            print(f"\n{GREEN}🎉 Docker 守护进程已启动并成功建立连接！{RESET}")
                            break
                        print(f"   [第 {attempt}/{max_retries} 次检测] 守护进程仍在加载中，请稍候...", end="\r")
                    else:
                        print(f"\n{RED}❌ 启动超时：Docker Desktop 启动时间过长，请手动打开并确认其转为 Green (Running) 状态后重试。{RESET}")
                        return False
                else:
                    print(f"{RED}❌ 请手动打开 Docker Desktop 并确保其处于运行状态，然后重新运行本脚本。{RESET}")
                    return False
            else:
                print(f"{RED}❌ 请手动打开并运行您的 Docker / Docker Desktop 服务，然后重新运行本脚本。{RESET}")
                return False
    except Exception as e:
        print(f"{RED}❌ 无法确认 Docker 守护进程状态: {e}{RESET}")
        return False

    # 3. Check Docker Compose
    try:
        res = subprocess.run(["docker", "compose", "version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="ignore")
        if res.returncode != 0:
            print(f"{RED}❌ 错误: 未检测到 Docker Compose v2 (docker compose)！{RESET}")
            return False
        print(f"{GREEN}✅ Docker 引擎与 Docker Compose 均已就绪且运行正常。{RESET}")
    except Exception:
        print(f"{RED}❌ 错误: 无法运行 docker compose 命令。{RESET}")
        return False
        
    return True


def init_env():
    """Check and copy .env file if not present."""
    if not os.path.exists(".env"):
        if os.path.exists(".env.example"):
            print(f"{YELLOW}⚠️  未发现 .env 配置文件，正在根据 .env.example 自动生成...{RESET}")
            shutil.copy(".env.example", ".env")
            print(f"{GREEN}✅ .env 配置文件初始化成功！{RESET}")
        else:
            print(f"{RED}❌ 错误: 未发现 .env.example 模板文件，无法自动初始化配置！{RESET}")

def get_env_ports():
    """Read customized ports from .env, fallback to defaults."""
    ports = {"frontend": "5173", "backend": "8080", "mysql": "3307", "redis": "6379", "go-judge": "5050"}
    if not os.path.exists(".env"):
        return ports
        
    try:
        with open(".env", "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                match_frontend = re.match(r"FRONTEND_PORT\s*=\s*(.+)", line)
                if match_frontend:
                    ports["frontend"] = match_frontend.group(1).split(":")[-1].strip()
                match_backend = re.match(r"BACKEND_PORT\s*=\s*(.+)", line)
                if match_backend:
                    ports["backend"] = match_backend.group(1).split(":")[-1].strip()
    except Exception:
        pass
        
    return ports

def get_container_states():
    """Get active status and health check information of the localoj containers."""
    states = {}
    for s in SERVICES:
        states[s["service"]] = {"status": "offline", "health": "N/A", "ip": "N/A"}
        
    try:
        res = subprocess.run(["docker", "compose", "ps", "-a", "--format", "json"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="ignore")
        if res.returncode == 0 and res.stdout.strip():
            # Parse line-by-line JSON or array JSON
            output = res.stdout.strip()
            # Docker Compose might return list or new-line separated JSON objects
            import json
            try:
                data = json.loads(output)
                if not isinstance(data, list):
                    data = [data]
            except Exception:
                data = []
                for line in output.splitlines():
                    if line.strip():
                        try:
                            data.append(json.loads(line))
                        except Exception:
                            pass
                            
            for item in data:
                svc = item.get("Service", "").lower()
                state = item.get("State", "").lower()
                status = item.get("Status", "").lower()
                health = "N/A"
                if "healthy" in status:
                    health = "healthy"
                elif "unhealthy" in status:
                    health = "unhealthy"
                elif "starting" in status:
                    health = "starting"
                    
                if svc in states:
                    states[svc]["status"] = state
                    states[svc]["health"] = health
        else:
            # Fallback to plain table parse if JSON is not supported or failed
            res_table = subprocess.run(["docker", "compose", "ps", "-a"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="ignore")
            if res_table.returncode == 0:
                lines = res_table.stdout.splitlines()
                for line in lines[1:]: # Skip header
                    for s in SERVICES:
                        if s["name"] in line or s["service"] in line:
                            status_lower = line.lower()
                            if "up" in status_lower:
                                states[s["service"]]["status"] = "running"
                                if "(healthy)" in status_lower:
                                    states[s["service"]]["health"] = "healthy"
                                elif "(unhealthy)" in status_lower:
                                    states[s["service"]]["health"] = "unhealthy"
                                elif "starting" in status_lower:
                                    states[s["service"]]["health"] = "starting"
                            elif "exited" in status_lower:
                                states[s["service"]]["status"] = "exited"
    except Exception as e:
        pass
        
    return states

def print_header():
    """Print top-level ascii logo."""
    print(f"""{CYAN}{BOLD}
 ██████╗      ██████╗  ██████╗ ██████╗  ██████╗███████╗
██╔═══██╗    ██╔═══██╗ ██╔═══██╗██╔══██╗██╔════╝██╔════╝
██║   ██║    ██║   ██║ ██║   ██║██║  ██║██║     ███████╗
██║   ██║    ██║   ██║ ██║   ██║██║  ██║██║     ╚════██║
╚██████╔╝    ╚██████╔╝ ╚██████╔╝██████╔╝╚██████╗███████║
 ╚═════╝      ╚═════╝   ╚═════╝ ╚═════╝  ╚═════╝╚══════╝
                     {MAGENTA}— Internal CodeRush OJ System{CYAN}{BOLD}
{RESET}""")

def run_command(cmd, desc):
    """Run interactive subprocess command."""
    print(f"\n{BLUE}🚀 正在执行: {desc}...{RESET}")
    print(f"{YELLOW}命令: {' '.join(cmd)}{RESET}\n")
    try:
        # Run it directly in the foreground, letting user see stdout/stderr
        subprocess.run(cmd, check=True)
        print(f"\n{GREEN}✅ 执行成功！{RESET}")
        return True
    except subprocess.CalledProcessError:
        print(f"\n{RED}❌ 错误: {desc} 失败，退出码不为 0！{RESET}")
        return False
    except KeyboardInterrupt:
        print(f"\n{YELLOW}⚠️  已取消执行。{RESET}")
        return False

def get_dir_size(path):
    """Calculate total directory size recursively."""
    total = 0
    try:
        for entry in os.scandir(path):
            if entry.is_file():
                total += entry.stat().st_size
            elif entry.is_dir():
                total += get_dir_size(entry.path)
    except Exception:
        pass
    return total

def get_file_size_str(size_bytes):
    """Format bytes to human readable string."""
    if size_bytes < 1024:
        return f"{size_bytes} B"
    elif size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.2f} KB"
    elif size_bytes < 1024 * 1024 * 1024:
        return f"{size_bytes / (1024 * 1024):.2f} MB"
    else:
        return f"{size_bytes / (1024 * 1024 * 1024):.2f} GB"

def get_dir_size_str(path):
    if not os.path.exists(path):
        return "0 B"
    return get_file_size_str(get_dir_size(path))

def find_backup_files():
    """Find all potential backup zip files in root and backups directory."""
    backups = []
    # Search in root
    try:
        for file in os.listdir("."):
            if file.endswith(".zip") and ("backup" in file.lower() or "localoj" in file.lower()):
                backups.append(os.path.join(".", file))
    except Exception:
        pass
            
    # Search in backups folder
    if os.path.exists("./backups"):
        try:
            for file in os.listdir("./backups"):
                if file.endswith(".zip") and ("backup" in file.lower() or "localoj" in file.lower()):
                    backups.append(os.path.join("backups", file))
        except Exception:
            pass
                
    # Deduplicate and sort by modification time descending (latest first)
    unique_backups = list(set(backups))
    try:
        unique_backups.sort(key=os.path.getmtime, reverse=True)
    except Exception:
        pass
    return unique_backups

def backup_data():
    """Backup the entire system data folder."""
    print(CLEAR_SCREEN)
    print_header()
    print(f"{BOLD}💾 系统数据一键备份 (Backup Utility){RESET}\n")
    
    if not os.path.exists("./data") or not os.path.isdir("./data"):
        print(f"{RED}❌ 错误: 未检测到本地数据目录 './data'！{RESET}")
        print(f"{YELLOW}💡 提示: 请先运行主菜单选项 [1] 启动系统，系统会自动创建并初始化 './data' 目录。{RESET}")
        input(f"\n{BLUE}按回车键返回...{RESET}")
        return

    # Check container states to see if any are running
    states = get_container_states()
    running_count = sum(1 for s in SERVICES if states[s["service"]]["status"] == "running")
    
    print(f"{CYAN}系统检测到数据目录：{RESET} ./data")
    print(f"├─ MySQL 数据库数据: ./data/mysql (约 {get_dir_size_str('./data/mysql')})")
    print(f"├─ Redis 缓存与队列: ./data/redis (约 {get_dir_size_str('./data/redis')})")
    print(f"└─ OJ 评测题目与文件: ./data/oj (约 {get_dir_size_str('./data/oj')})")
    print("-" * 50)
    
    print(f"{BOLD}请选择备份模式：{RESET}")
    print(f"  {GREEN}{BOLD}[1] 安全冷备份 (推荐){RESET} - 自动暂停容器，备份数据，最后恢复运行。100% 保证数据库完整性。")
    print(f"  [2] 快速热备份       - 不暂停容器，直接在线压缩。可能导致备份中包含未落盘的临时事务。")
    print("  [0] 取消并返回")
    
    choice = input(f"\n{BOLD}请选择备份方式 [0-2]: {RESET}").strip()
    if choice not in ["1", "2"]:
        print(f"\n{YELLOW}操作已取消。{RESET}")
        time.sleep(1)
        return
        
    was_running = (running_count > 0)
    
    if choice == "1" and was_running:
        print(f"\n{BLUE}⏳ 正在安全暂停所有运行中的 Docker 容器...{RESET}")
        subprocess.run(["docker", "compose", "down"])
        print(f"{GREEN}✅ 容器已成功停止运行。{RESET}")
        
    print(f"\n{BLUE}📦 正在对数据包进行高比例压缩打包...{RESET}")
    
    # Ensure backups folder exists
    os.makedirs("./backups", exist_ok=True)
    
    # Generate timestamped archive
    timestamp = time.strftime("%Y%m%d_%H%M%S")
    backup_filename = f"backup_localoj_{timestamp}"
    backup_filepath = os.path.join("backups", backup_filename)
    
    try:
        archive_path = shutil.make_archive(backup_filepath, "zip", "./data")
        archive_size = os.path.getsize(archive_path)
        archive_size_str = get_file_size_str(archive_size)
        
        print("\n" + "=" * 55)
        print(f"{GREEN}{BOLD}🎉 备份打包成功！{RESET}")
        print(f"📂 备份文件: {BOLD}{archive_path}{RESET}")
        print(f"📊 文件大小: {BOLD}{archive_size_str}{RESET}")
        print(f"📅 备份时间: {BOLD}{time.strftime('%Y-%m-%d %H:%M:%S')}{RESET}")
        print("=" * 55)
        
    except Exception as e:
        print(f"\n{RED}❌ 备份压缩失败: {e}{RESET}")
        
    if choice == "1" and was_running:
        print(f"\n{BLUE}🚀 正在重新恢复并启动所有 Docker 容器...{RESET}")
        subprocess.run(["docker", "compose", "up", "-d"])
        print(f"{GREEN}✅ 系统已恢复运行！{RESET}")
        
    input(f"\n{BLUE}按回车键返回...{RESET}")

def restore_data():
    """Select and restore a backup ZIP archive."""
    print(CLEAR_SCREEN)
    print_header()
    print(f"{BOLD}🔄 恢复历史备份 (Restore Utility){RESET}\n")
    
    backups = find_backup_files()
    
    if not backups:
        print(f"{YELLOW}⚠️  未检测到任何可用的备份包 (*.zip)！{RESET}")
        print(f"💡 提示:")
        print(f"  1. 您可以使用选项 [1] 创建一个新的数据备份包。")
        print(f"  2. 如果您是跨机器迁移，请将从其他机器导出的备份 zip 文件放置在项目根目录")
        print(f"     或新建的 {BOLD}./backups{RESET} 文件夹下，然后重新进入该菜单。")
        input(f"\n{BLUE}按回车键返回...{RESET}")
        return
        
    print(f"{CYAN}检测到以下备份包 (已按时间由新到旧排序):{RESET}")
    print("-" * 70)
    print(f"{BOLD}{'序号':<6} | {'备份文件路径':<38} | {'文件大小':<12}{RESET}")
    print("-" * 70)
    
    for idx, path in enumerate(backups, 1):
        size_bytes = os.path.getsize(path)
        size_str = get_file_size_str(size_bytes)
        print(f" [{idx:<2}]  | {path:<36} | {size_str:<12}")
    print("-" * 70)
    print(" [0]   | 返回上一级")
    print("-" * 70)
    
    choice = input(f"\n{BOLD}请选择要恢复的备份序号 [0-{len(backups)}]: {RESET}").strip()
    if choice == "0" or not choice:
        return
        
    try:
        idx = int(choice)
        if 1 <= idx <= len(backups):
            selected_backup = backups[idx - 1]
        else:
            print(f"{RED}❌ 输入序号无效！{RESET}")
            time.sleep(1.5)
            return
    except ValueError:
        print(f"{RED}❌ 输入序号无效！{RESET}")
        time.sleep(1.5)
        return
        
    print(f"\n{RED}{BOLD}⚠️  警告：该操作是覆盖性恢复！{RESET}")
    print(f"您选择恢复的备份是: {BOLD}{selected_backup}{RESET}")
    print(f"这将会覆盖您现有的系统数据 (数据库、Redis、题目包等)。")
    print(f"虽然脚本会自动备份当前数据到 `./data.old_*` 文件夹，但也请谨慎操作。")
    
    confirm = input(f"\n{YELLOW}{BOLD}请输入大写 'RESTORE' 确认恢复数据: {RESET}").strip()
    if confirm != "RESTORE":
        print(f"\n{GREEN}操作已取消。{RESET}")
        time.sleep(1)
        return
        
    # Perform cold restore
    states = get_container_states()
    running_count = sum(1 for s in SERVICES if states[s["service"]]["status"] == "running")
    was_running = (running_count > 0)
    
    print(f"\n{BLUE}⏳ 正在停止运行中的 Docker 容器...{RESET}")
    subprocess.run(["docker", "compose", "down"])
    time.sleep(2) # Give Windows/WSL a brief moment to close files
    
    # 2. Safety rename of existing data
    backup_old_dir = None
    if os.path.exists("./data"):
        old_data_timestamp = time.strftime("%Y%m%d_%H%M%S")
        backup_old_dir = f"./data.old_{old_data_timestamp}"
        print(f"\n{YELLOW}⚠️  正在将现有的数据目录重命名以作安全备份: {RESET}")
        print(f"   ./data  ==>  {backup_old_dir}")
        try:
            os.rename("./data", backup_old_dir)
            print(f"{GREEN}✅ 重命名备份完成！若恢复有误，您可在同目录下找回该文件夹。{RESET}")
        except Exception as e:
            print(f"{RED}❌ 无法重命名 './data' 目录 (可能是文件被占用): {e}{RESET}")
            print(f"{YELLOW}💡 请尝试手动关闭任何打开了该目录的程序 (如 VS Code、命令行、文件管理器) 后重试。{RESET}")
            if was_running:
                print(f"\n{BLUE}🚀 正在重新启动容器恢复原状...{RESET}")
                subprocess.run(["docker", "compose", "up", "-d"])
            input(f"\n{BLUE}按回车键返回...{RESET}")
            return
            
    # 3. Unzip
    print(f"\n{BLUE}📦 正在解压并恢复数据文件...{RESET}")
    try:
        os.makedirs("./data", exist_ok=True)
        shutil.unpack_archive(selected_backup, "./data")
        print(f"{GREEN}✅ 数据包解压并还原成功！{RESET}")
    except Exception as e:
        print(f"{RED}❌ 解压恢复失败: {e}{RESET}")
        # Rollback
        if backup_old_dir and os.path.exists(backup_old_dir):
            print(f"{YELLOW}⚠️  正在尝试自动恢复旧的数据目录...{RESET}")
            try:
                if os.path.exists("./data"):
                    shutil.rmtree("./data")
                os.rename(backup_old_dir, "./data")
                print(f"{GREEN}✅ 旧数据目录已成功还原。{RESET}")
            except Exception as re_err:
                print(f"{RED}❌ 还原旧数据目录失败，请手动将 '{backup_old_dir}' 重命名为 'data'。{RESET}")
        if was_running:
            print(f"\n{BLUE}🚀 正在重新启动容器...{RESET}")
            subprocess.run(["docker", "compose", "up", "-d"])
        input(f"\n{BLUE}按回车键返回...{RESET}")
        return
        
    # 4. Restart containers
    print(f"\n{BLUE}🚀 正在重新启动所有 Docker 容器...{RESET}")
    subprocess.run(["docker", "compose", "up", "-d"])
    print(f"\n{GREEN}{BOLD}🎉 系统数据恢复已完全成功！所有服务已恢复运行。{RESET}")
    
    if backup_old_dir:
        print(f"{CYAN}💡 提示: 原有数据已安全存档在 '{backup_old_dir}' 目录下，确认数据完整后可手动将其删除。{RESET}")
        
    input(f"\n{BLUE}按回车键返回...{RESET}")

def backup_restore_menu():
    """Backup & Restore management menu."""
    while True:
        print(CLEAR_SCREEN)
        print_header()
        print(f"{BOLD}💾 一键备份与恢复系统数据 (Backup & Restore){RESET}")
        print("=" * 55)
        print(f"{BOLD}1. 📦 创建数据备份 (Create Backup Archive){RESET}")
        print(f"   将当前系统数据目录压缩归档至 backups 目录（支持冷热备份）。")
        print()
        print(f"{BOLD}2. 🔄 恢复数据备份 (Restore Backup Archive){RESET}")
        print(f"   选择已有的压缩包恢复数据，自动重命名旧数据保护安全，支持跨机器。")
        print()
        print("0. 🔙 返回主菜单")
        print("=" * 55)
        
        choice = input(f"{BOLD}请选择操作序号 [0-2]: {RESET}").strip()
        if choice == "1":
            backup_data()
        elif choice == "2":
            restore_data()
        elif choice == "0":
            break
        else:
            print(f"\n{RED}❌ 输入错误，请输入 0 到 2 之间的数字！{RESET}")
            time.sleep(1)

def show_monitor_view(ports):
    """Real-time updating dashboard showing container statuses."""
    try:
        while True:
            states = get_container_states()
            print(CLEAR_SCREEN)
            print_header()
            print(f"{BOLD}📊 服务实时监控台 (每 2 秒自动更新，按 Ctrl+C 返回主菜单){RESET}\n")
            print("=" * 76)
            print(f"{BOLD}{'服务名称':<18} | {'系统容器名':<22} | {'服务状态':<14} | {'健康状态':<12}{RESET}")
            print("-" * 76)
            
            all_healthy = True
            any_offline = False
            
            for s in SERVICES:
                svc = s["service"]
                desc = s["desc"]
                c_name = s["name"]
                
                info = states[svc]
                status_raw = info["status"]
                health_raw = info["health"]
                
                # Colors
                if status_raw == "running":
                    status_str = f"{GREEN}● 运行中{RESET}"
                elif status_raw in ["exited", "offline"]:
                    status_str = f"{RED}○ 已停止{RESET}"
                    all_healthy = False
                    any_offline = True
                else:
                    status_str = f"{YELLOW}⏳ {status_raw}{RESET}"
                    all_healthy = False
                
                if health_raw == "healthy":
                    health_str = f"{GREEN}healthy{RESET}"
                elif health_raw == "starting":
                    health_str = f"{YELLOW}starting{RESET}"
                    all_healthy = False
                elif health_raw == "unhealthy":
                    health_str = f"{RED}unhealthy{RESET}"
                    all_healthy = False
                else:
                    health_str = f"{CYAN}N/A{RESET}"
                
                print(f"{desc:<16} | {c_name:<22} | {status_str:<23} | {health_str:<21}")
                
            print("=" * 76)
            
            # Print localized address tips
            print(f"\n{BOLD}🔗 快速访问链接：{RESET}")
            print(f"├─ {GREEN}OJ 前端系统{RESET} : {BOLD}{UNDERLINE}http://localhost:{ports['frontend']}{RESET}")
            print(f"└─ {GREEN}OJ 后端 API{RESET} : {BOLD}{UNDERLINE}http://localhost:{ports['backend']}/swagger-ui/index.html{RESET} (API 调试)")
            
            if all_healthy and not any_offline:
                print(f"\n{GREEN}{BOLD}🎉 系统运行正常！所有核心服务已全部就绪。{RESET}")
            elif any_offline:
                print(f"\n{RED}{BOLD}⚠️  警告：检测到部分服务处于下线状态，系统可能无法正常工作。{RESET}")
            else:
                print(f"\n{YELLOW}{BOLD}⏳ 系统启动中... 部分服务正在加载健康检查，请稍候。{RESET}")
                
            time.sleep(2)
    except KeyboardInterrupt:
        pass

def rebuild_single_service():
    """Let the user select and rebuild/restart a specific service."""
    print(f"\n{BLUE}🛠️  请选择要独立重建并重启的服务：{RESET}")
    buildable = [
        {"service": "frontend", "desc": "Vue 3 前端界面"},
        {"service": "backend", "desc": "Spring Boot 后端 API"},
        {"service": "judge-worker", "desc": "评测判题消费机"},
        {"service": "go-judge", "desc": "go-judge 评测沙箱"}
    ]
    for i, s in enumerate(buildable, 1):
        print(f"  {BOLD}[{i}]{RESET} {s['desc']} ({s['service']})")
    print("  [0] 返回主菜单")
    
    choice = input(f"\n{BOLD}请输入选项序号: {RESET}").strip()
    if choice == "0" or not choice:
        return
        
    try:
        idx = int(choice)
        if 1 <= idx <= len(buildable):
            target = buildable[idx - 1]["service"]
            desc = buildable[idx - 1]["desc"]
            run_command(["docker", "compose", "up", "-d", "--build", target], f"独立重建并重启 {desc}")
            input(f"\n{GREEN}服务 {target} 已成功完成重建与重启。按回车键返回...{RESET}")
        else:
            print(f"{RED}❌ 输入序号无效！{RESET}")
            time.sleep(1.5)
    except ValueError:
        print(f"{RED}❌ 输入序列无效！{RESET}")
        time.sleep(1.5)

def tail_logs():
    """Tail Docker Compose logs interactively."""
    print(f"\n{BLUE}📄 请选择要查看日志的服务：{RESET}")
    print("1. 全部服务联合日志")
    for i, s in enumerate(SERVICES, 1):
        print(f"{i+1}. {s['desc']} ({s['service']})")
    print("0. 返回主菜单")
    
    choice = input(f"\n{BOLD}请输入选项序号: {RESET}").strip()
    if choice == "0" or not choice:
        return
        
    cmd = ["docker", "compose", "logs", "-f", "--tail=100"]
    
    try:
        idx = int(choice)
        if idx == 1:
            pass
        elif 2 <= idx <= len(SERVICES) + 1:
            cmd.append(SERVICES[idx - 2]["service"])
        else:
            print(f"{RED}❌ 输入序号无效！{RESET}")
            return
    except ValueError:
        print(f"{RED}❌ 输入序列无效！{RESET}")
        return
        
    print(f"\n{YELLOW}📢 正在进入实时日志视图 (按 Ctrl+C 退出日志并返回主菜单)...{RESET}\n")
    try:
        subprocess.run(cmd)
    except KeyboardInterrupt:
        print(f"\n{GREEN}已退出日志。{RESET}")

def main_menu():
    """Render interactive main menu."""
    init_env()
    ports = get_env_ports()
    
    while True:
        print(CLEAR_SCREEN)
        print_header()
        
        # Detect state to show compact summary
        states = get_container_states()
        running_count = sum(1 for s in SERVICES if states[s["service"]]["status"] == "running")
        
        status_banner = f"{GREEN}● 运行中 ({running_count}/6 已启动){RESET}" if running_count > 0 else f"{RED}○ 已停止{RESET}"
        print(f"当前系统状态: {status_banner}")
        print("=" * 50)
        print(f"{BOLD}1. 🚀 一键启动整个系统 (增量/热启动){RESET}")
        print(f"{BOLD}2. 🔄 重新编译并启动整个系统 (全量构建){RESET}")
        print(f"{BOLD}3. ⚡ 独立重建并重启单个指定服务 (前端/后端/判题等){RESET}")
        print(f"{BOLD}4. 🛑 关闭并停止整个系统 (保留数据卷){RESET}")
        print(f"{BOLD}5. 📊 查看系统监控与健康台{RESET}")
        print(f"{BOLD}6. 📄 查看服务运行日志{RESET}")
        print(f"{BOLD}7. 🗑️  全量清理 (停止服务并彻底删除数据和缓存){RESET}")
        print(f"{BOLD}8. 💾 一键备份与恢复系统数据 (Backup & Restore){RESET}")
        print("0. 🚪 退出脚本")
        print("=" * 50)
        
        choice = input(f"{BOLD}请选择操作序号 [0-8]: {RESET}").strip()
        
        if choice == "1":
            if check_requirements():
                run_command(["docker", "compose", "up", "-d"], "启动 CodeRush OJ 系统")
                show_monitor_view(ports)
        elif choice == "2":
            if check_requirements():
                run_command(["docker", "compose", "up", "-d", "--build"], "重新编译并启动整个系统")
                show_monitor_view(ports)
        elif choice == "3":
            if check_requirements():
                rebuild_single_service()
        elif choice == "4":
            if check_requirements():
                run_command(["docker", "compose", "down"], "停止 CodeRush OJ 系统")
                input(f"\n{GREEN}系统已成功停止。按回车键返回...{RESET}")
        elif choice == "5":
            if check_requirements():
                show_monitor_view(ports)
        elif choice == "6":
            if check_requirements():
                tail_logs()
        elif choice == "7":
            if check_requirements():
                confirm = input(f"\n{RED}{BOLD}⚠️  警告：此操作将永久删除数据库、Redis 队列及题目数据！输入 'Y' 确认清理: {RESET}").strip()
                if confirm.upper() == 'Y':
                    run_command(["docker", "compose", "down", "-v"], "彻底清理系统及数据卷")
                    input(f"\n{GREEN}全量清理已完成。按回车键返回...{RESET}")
                else:
                    print(f"\n{GREEN}操作已取消。{RESET}")
                    time.sleep(1)
        elif choice == "8":
            backup_restore_menu()
        elif choice == "0":
            print(f"\n{BLUE}👋 感谢使用 CodeRush OJ 启动控制台，祝您编码愉快！{RESET}\n")
            sys.exit(0)
        else:
            print(f"\n{RED}❌ 输入错误，请输入 0 到 8 之间的数字！{RESET}")
            time.sleep(1.5)

if __name__ == "__main__":
    if len(sys.argv) > 1:
        # Command line arguments mode
        arg = sys.argv[1].lower()
        init_env()
        if arg == "up":
            if check_requirements():
                subprocess.run(["docker", "compose", "up", "-d"])
        elif arg == "down":
            if check_requirements():
                subprocess.run(["docker", "compose", "down"])
        elif arg == "build":
            if check_requirements():
                subprocess.run(["docker", "compose", "up", "-d", "--build"])
        elif arg == "status":
            if check_requirements():
                show_monitor_view(get_env_ports())
        elif arg == "backup":
            backup_data()
        elif arg == "restore":
            restore_data()
        else:
            print(f"Unknown argument: {arg}")
            print("Usage: python start.py [up | down | build | status | backup | restore]")
    else:
        # Interactive mode
        main_menu()
