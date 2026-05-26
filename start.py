#!/usr/bin/env python3
import os
import sys
import time
import subprocess
import re
import shutil

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
                     {MAGENTA}— Internal Local OJ System{CYAN}{BOLD}
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
        print(f"{BOLD}1. 🚀 一键启动系统 (增量/热启动){RESET}")
        print(f"{BOLD}2. 🔄 重新打包并编译启动 (全量构建/更新后推荐){RESET}")
        print(f"{BOLD}3. 🛑 关闭并停止系统 (保留数据卷){RESET}")
        print(f"{BOLD}4. 📊 查看系统监控与健康台{RESET}")
        print(f"{BOLD}5. 📄 查看服务运行日志{RESET}")
        print(f"{BOLD}6. 🗑️  全量清理 (停止服务并彻底删除数据和缓存){RESET}")
        print("0. 🚪 退出脚本")
        print("=" * 50)
        
        choice = input(f"{BOLD}请选择操作序号 [0-6]: {RESET}").strip()
        
        if choice == "1":
            if check_requirements():
                run_command(["docker", "compose", "up", "-d"], "启动 Local OJ 系统")
                show_monitor_view(ports)
        elif choice == "2":
            if check_requirements():
                run_command(["docker", "compose", "up", "-d", "--build"], "重新编译并启动系统")
                show_monitor_view(ports)
        elif choice == "3":
            if check_requirements():
                run_command(["docker", "compose", "down"], "停止 Local OJ 系统")
                input(f"\n{GREEN}系统已成功停止。按回车键返回...{RESET}")
        elif choice == "4":
            if check_requirements():
                show_monitor_view(ports)
        elif choice == "5":
            if check_requirements():
                tail_logs()
        elif choice == "6":
            if check_requirements():
                confirm = input(f"\n{RED}{BOLD}⚠️  警告：此操作将永久删除数据库、Redis 队列及题目数据！输入 'Y' 确认清理: {RESET}").strip()
                if confirm.upper() == 'Y':
                    run_command(["docker", "compose", "down", "-v"], "彻底清理系统及数据卷")
                    input(f"\n{GREEN}全量清理已完成。按回车键返回...{RESET}")
                else:
                    print(f"\n{GREEN}操作已取消。{RESET}")
                    time.sleep(1)
        elif choice == "0":
            print(f"\n{BLUE}👋 感谢使用 Local OJ 启动控制台，祝您编码愉快！{RESET}\n")
            sys.exit(0)
        else:
            print(f"\n{RED}❌ 输入错误，请输入 0 到 6 之间的数字！{RESET}")
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
        else:
            print(f"Unknown argument: {arg}")
            print("Usage: python start.py [up | down | build | status]")
    else:
        # Interactive mode
        main_menu()
