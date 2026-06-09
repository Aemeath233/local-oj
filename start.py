#!/usr/bin/env python3
import os
import sys
import time
import subprocess
import socket
import urllib.request
import tarfile
import threading
import re
import secrets

# Force stdout/stderr to use UTF-8 encoding
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

# Initialize ANSI colors on Windows
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

def print_header():
    print(f"""{CYAN}{BOLD}
 ██████╗      ██████╗  ██████╗ ██████╗  ██████╗███████╗
██╔═══██╗    ██╔═══██╗ ██╔═══██╗██╔══██╗██╔════╝██╔════╝
██║   ██║    ██║   ██║ ██║   ██║██║  ██║██║     ███████╗
██║   ██║    ██║   ██║ ██║   ██║██║  ██║██║     ╚════██║
╚██████╔╝    ╚██████╔╝ ╚██████╔╝██████╔╝╚██████╗███████║
 ╚═════╝      ╚═════╝   ╚═════╝ ╚═════╝  ╚═════╝╚══════╝
                 {MAGENTA}— CodeRushOJ Native Startup Console{CYAN}{BOLD}
{RESET}""")

def load_env():
    env = {}
    if not os.path.exists(".env"):
        if os.path.exists(".env.example"):
            print(f"{YELLOW}⚠️  未检测到 .env 配置文件，正在根据模板自动生成...{RESET}")
            with open(".env.example", "r", encoding="utf-8") as f:
                lines = f.readlines()
            # Auto-generate secure secrets
            replacements = {
                "JWT_SECRET": secrets.token_hex(32),
                "ADMIN_PASSWORD": "admin" + str(secrets.randbelow(900000) + 100000)
            }
            new_lines = []
            for line in lines:
                key = line.split("=", 1)[0].strip() if "=" in line else None
                if key in replacements:
                    new_lines.append(f"{key}={replacements[key]}\n")
                else:
                    new_lines.append(line)
            with open(".env", "w", encoding="utf-8") as f:
                f.writelines(new_lines)
            print(f"{GREEN}✅ 已自动生成本地 .env 配置文件。{RESET}")
        else:
            print(f"{RED}❌ 错误: 未发现 .env.example 模板文件，无法自动初始化配置！{RESET}")
            sys.exit(1)
            
    with open(".env", "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if "=" in line:
                key, val = line.split("=", 1)
                env[key.strip()] = val.strip()
    return env

def check_port(host, port):
    try:
        with socket.create_connection((host, int(port)), timeout=1.5):
            return True
    except Exception:
        return False

def check_requirements(env):
    print(f"\n{BLUE}🔍 正在检测本地运行环境与中间件依赖...{RESET}")
    
    # 1. Check Java 21+
    try:
        res = subprocess.run(["java", "-version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, errors="ignore", shell=(os.name == 'nt'))
        version_output = res.stderr or res.stdout
        if "version" in version_output:
            match = re.search(r'version "(\d+)', version_output)
            if match:
                major_version = int(match.group(1))
                if major_version < 21:
                    print(f"{RED}❌ 错误: 系统检测到您的 JDK 版本为 {major_version}，但 CodeRush OJ 必须要求 JDK 21+！{RESET}")
                    return False
            print(f"  {GREEN}✓{RESET} JDK 21+ 已就绪: {version_output.splitlines()[0]}")
        else:
            print(f"{RED}❌ 错误: 无法运行 'java -version'，请确保您已正确安装 JDK 21+ 并配置了系统环境变量！{RESET}")
            return False
    except Exception as e:
        print(f"{RED}❌ 错误: 检测 Java 时发生异常: {e}{RESET}")
        return False

    # 2. Check Node.js & NPM
    try:
        res_node = subprocess.run(["node", "-v"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, errors="ignore", shell=(os.name == 'nt'))
        res_npm = subprocess.run(["npm", "-v"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, errors="ignore", shell=(os.name == 'nt'))
        if res_node.returncode == 0 and res_npm.returncode == 0:
            print(f"  {GREEN}✓{RESET} Node.js 已就绪: {res_node.stdout.strip()} (npm: {res_npm.stdout.strip()})")
        else:
            print(f"{RED}❌ 错误: 未检测到 Node.js / npm，请确保已安装 Node.js 20+ 并加入 PATH！{RESET}")
            return False
    except Exception as e:
        print(f"{RED}❌ 错误: 检测 Node.js 时发生异常: {e}{RESET}")
        return False

    # 3. Check MySQL
    db_url = env.get("DB_URL", "jdbc:mysql://localhost:3306/coderush_oj")
    match = re.search(r'jdbc:mysql://([^:/]+):?(\d+)?/', db_url)
    db_host = match.group(1) if match else "localhost"
    db_port = match.group(2) if match and match.group(2) else "3306"
    
    print(f"  ⚙️  正在检测本地 MySQL 连接 ({db_host}:{db_port})...")
    if check_port(db_host, db_port):
        print(f"  {GREEN}✓{RESET} MySQL 服务已处于运行状态。")
    else:
        print(f"{RED}❌ 错误: 无法连接到本地 MySQL 数据库 ({db_host}:{db_port})！{RESET}")
        print(f"    💡 请确保您已在本机安装并启动了 MySQL 服务。")
        return False

    # 4. Check Redis
    redis_host = env.get("REDIS_HOST", "localhost")
    redis_port = env.get("REDIS_PORT", "6379")
    print(f"  ⚙️  正在检测本地 Redis 连接 ({redis_host}:{redis_port})...")
    if check_port(redis_host, redis_port):
        print(f"  {GREEN}✓{RESET} Redis 服务已处于运行状态。")
    else:
        print(f"{RED}❌ 错误: 无法连接到本地 Redis 服务 ({redis_host}:{redis_port})！{RESET}")
        print(f"    💡 请确保您已在本机安装并启动了 Redis 服务。")
        return False

    # 5. Check compilers (Warnings only)
    compilers_ok = True
    print("  ⚙️  正在检测本地编译器环境 (仅作评测支持参考)...")
    
    # GCC
    try:
        res_gcc = subprocess.run(["gcc", "--version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, shell=(os.name == 'nt'))
        if res_gcc.returncode == 0:
            print(f"    - GCC 编译器: {GREEN}已就绪{RESET} ({res_gcc.stdout.splitlines()[0]})")
        else:
            compilers_ok = False
    except Exception:
        print(f"    - GCC 编译器: {YELLOW}未就绪{RESET} (C 代码本地评测将不可用)")
        compilers_ok = False

    # G++
    try:
        res_gpp = subprocess.run(["g++", "--version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, shell=(os.name == 'nt'))
        if res_gpp.returncode == 0:
            print(f"    - G++ 编译器: {GREEN}已就绪{RESET} ({res_gpp.stdout.splitlines()[0]})")
        else:
            compilers_ok = False
    except Exception:
        print(f"    - G++ 编译器: {YELLOW}未就绪{RESET} (C++ 代码本地评测将不可用)")
        compilers_ok = False

    # Python
    try:
        res_py = subprocess.run(["python", "--version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, shell=(os.name == 'nt'))
        if res_py.returncode == 0:
            print(f"    - Python 运行时: {GREEN}已就绪{RESET} ({res_py.stdout.strip()})")
        else:
            compilers_ok = False
    except Exception:
        try:
            res_py = subprocess.run(["python3", "--version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, shell=(os.name == 'nt'))
            if res_py.returncode == 0:
                print(f"    - Python 运行时: {GREEN}已就绪{RESET} ({res_py.stdout.strip()})")
            else:
                compilers_ok = False
        except Exception:
            print(f"    - Python 运行时: {YELLOW}未就绪{RESET} (Python/PyPy3 代码本地评测将不可用)")
            compilers_ok = False

    if not compilers_ok:
        print(f"{YELLOW}⚠️  提示: 部分编译器环境在当前机器上未检测到，这不会影响项目启动，但相关语言的提交将返回评测错误！{RESET}")
        
    return True

def download_file(url, dest_path):
    proxies = [
        "",  # Direct download first
        "https://github.moeyy.xyz/",
        "https://gh-proxy.com/",
        "https://mirror.ghproxy.com/",
        "https://ghp.ci/"
    ]
    
    for i, proxy in enumerate(proxies):
        download_url = proxy + url if proxy else url
        if proxy:
            print(f"  ⚡ 正在尝试通过国内镜像加速下载 ({i}/{len(proxies)-1}):\n  {download_url}")
        else:
            print(f"  正在下载二进制文件:\n  {download_url}")
            
        try:
            req = urllib.request.Request(
                download_url, 
                headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
            )
            with urllib.request.urlopen(req, timeout=30) as response, open(dest_path, 'wb') as out_file:
                meta = response.info()
                content_length = meta.get("Content-Length")
                total_size = int(content_length) if content_length else None
                
                downloaded = 0
                block_size = 8192
                while True:
                    buffer = response.read(block_size)
                    if not buffer:
                        break
                    downloaded += len(buffer)
                    out_file.write(buffer)
                    if total_size:
                        percent = downloaded * 100 / total_size
                        print(f"  📥 下载进度: {percent:.1f}% ({downloaded}/{total_size} 字节)", end="\r")
                print()  # Newline after completion
                
            if total_size and os.path.getsize(dest_path) < total_size:
                raise Exception("文件大小不匹配，下载可能不完整")
            return True
        except Exception as e:
            print(f"  ⚠️  本轮尝试失败: {e}")
            if os.path.exists(dest_path):
                try:
                    os.remove(dest_path)
                except Exception:
                    pass
    return False

def download_go_judge(env):
    os.makedirs("scratch/bin", exist_ok=True)
    ext = ".exe" if os.name == "nt" else ""
    exe_path = os.path.join("scratch", "bin", f"go-judge{ext}")
    
    if os.path.exists(exe_path):
        if os.path.getsize(exe_path) > 10 * 1024 * 1024:  # Must be larger than 10MB
            return exe_path
        else:
            print(f"{YELLOW}⚠️  检测到本地 go-judge 文件不完整（可能是上次未下完），正在删除并重新下载...{RESET}")
            try:
                os.remove(exe_path)
            except Exception:
                pass
        
    print(f"\n{BLUE}📥 正在初始化原生评测沙箱 go-judge...{RESET}")
    version = "1.12.0"
    base_url = f"https://github.com/criyle/go-judge/releases/download/v{version}/"
    
    if os.name == "nt":
        filename = f"go-judge_{version}_windows_amd64v2.exe"
        url = base_url + filename
        if not download_file(url, exe_path):
            print(f"{RED}❌ 错误: 所有下载镜像均尝试失败！{RESET}")
            print(f"  💡 请手动访问以下链接下载，并存为项目根目录的 'scratch/bin/go-judge.exe'：\n  {url}")
            sys.exit(1)
        print(f"  {GREEN}✓{RESET} Windows 平台沙箱配置就绪。")
    else:
        if sys.platform.startswith("linux"):
            filename = f"go-judge_{version}_linux_amd64v2.tar.gz"
        elif sys.platform == "darwin":
            filename = f"go-judge_{version}_macOS_amd64v2.tar.gz"
        else:
            print(f"{RED}❌ 无法识别的系统平台: {sys.platform}，请手动安装并运行 go-judge 服务。{RESET}")
            sys.exit(1)
            
        url = base_url + filename
        tar_path = os.path.join("scratch", "bin", filename)
        if not download_file(url, tar_path):
            print(f"{RED}❌ 错误: 所有下载镜像均尝试失败！{RESET}")
            sys.exit(1)
            
        try:
            with tarfile.open(tar_path, "r:gz") as tar:
                for member in tar.getmembers():
                    if member.name.startswith("go-judge"):
                        member.name = "go-judge"
                        tar.extract(member, path="scratch/bin")
                        break
                else:
                    tar.extractall(path="scratch/bin")
                    for item in os.listdir("scratch/bin"):
                        if item.startswith("go-judge_"):
                            os.rename(os.path.join("scratch/bin", item), exe_path)
            if os.path.exists(tar_path):
                os.remove(tar_path)
            os.chmod(exe_path, 0o755)
            print(f"  {GREEN}✓{RESET} 下载并解压完成，已存至: {exe_path}")
        except Exception as e:
            print(f"{RED}❌ 解压缩失败: {e}{RESET}")
            sys.exit(1)
            
    return exe_path

def download_jplag():
    os.makedirs("scratch/bin", exist_ok=True)
    jar_path = os.path.join("scratch", "bin", "jplag.jar")
    if os.path.exists(jar_path) and os.path.getsize(jar_path) > 5 * 1024 * 1024:
        return jar_path
        
    print(f"\n{BLUE}📥 正在检测/初始化代码查重引擎 JPlag...{RESET}")
    version = "5.0.0"
    url = f"https://github.com/jplag/JPlag/releases/download/v{version}/JPlag-{version}-jar-with-dependencies.jar"
    if not download_file(url, jar_path):
        print(f"{YELLOW}⚠️  警告: JPlag 查重包下载失败，代码查重功能将不可用！{RESET}")
        print(f"  💡 如果您需要此功能，请手动下载并将文件保存为 'scratch/bin/jplag.jar'：\n  {url}")
    else:
        print(f"  {GREEN}✓{RESET} JPlag 查重引擎配置就绪。")
    return jar_path

def log_stream(stream, prefix, color):
    try:
        for line in iter(stream.readline, ''):
            if not line:
                break
            print(f"{color}{prefix}{RESET} {line.rstrip()}")
    except Exception:
        pass

def kill_process_tree(proc):
    if os.name == 'nt':
        try:
            subprocess.run(['taskkill', '/F', '/T', '/PID', str(proc.pid)], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        except Exception:
            pass
    else:
        try:
            proc.terminate()
            proc.wait(timeout=2)
        except subprocess.TimeoutExpired:
            proc.kill()
        except Exception:
            pass

def main():
    print_header()
    env = load_env()
    
    if not check_requirements(env):
        print(f"\n{RED}❌ 环境检查未通过，启动已中止。请检查上面的错误提示。{RESET}")
        sys.exit(1)
        
    sandbox_path = download_go_judge(env)
    jplag_path = download_jplag()
    
    # Configure env for subprocesses
    sub_env = os.environ.copy()
    for k, v in env.items():
        sub_env[k] = v
    # Resolve relative paths to absolute paths for sub-module working directories
    if "APP_DATA_ROOT" in env:
        sub_env["APP_DATA_ROOT"] = os.path.abspath(env["APP_DATA_ROOT"])
    sub_env["JPLAG_JAR_PATH"] = os.path.abspath(os.path.join("scratch", "bin", "jplag.jar"))
    sub_env["JPLAG_WORKSPACE"] = os.path.abspath(os.path.join("scratch", "plagiarism"))
        
    processes = []
    
    # 1. Spawn go-judge sandbox
    go_judge_port = env.get("GO_JUDGE_PORT", "5050")
    print(f"\n{BLUE}🚀 正在启动评测沙箱 go-judge (监听端口: {go_judge_port})...{RESET}")
    p_sandbox = subprocess.Popen(
        [sandbox_path, "-http-addr", f":{go_judge_port}"],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
        encoding="utf-8",
        errors="ignore",
        env=sub_env
    )
    processes.append((p_sandbox, "[SANDBOX]  ", CYAN))
    
    # 2. Spawn Spring Boot Backend
    mvn_cmd = ".\\mvnw.cmd" if os.name == "nt" else "./mvnw"
    print(f"{BLUE}🚀 正在编译并启动后端 API 服务 (监听端口: {env.get('SERVER_PORT', '8080')})...{RESET}")
    p_backend = subprocess.Popen(
        [mvn_cmd, "-pl", "backend", "-am", "spring-boot:run"],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
        encoding="utf-8",
        errors="ignore",
        env=sub_env
    )
    processes.append((p_backend, "[BACKEND]  ", GREEN))
    
    # 3. Spawn Spring Boot Judge Worker
    print(f"{BLUE}🚀 正在编译并启动判题消费机 Worker...{RESET}")
    p_worker = subprocess.Popen(
        [mvn_cmd, "-pl", "judge-worker", "-am", "spring-boot:run"],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
        encoding="utf-8",
        errors="ignore",
        env=sub_env
    )
    processes.append((p_worker, "[JUDGE-WK] ", YELLOW))
    
    # 4. Spawn Vite Frontend
    npm_cmd = "npm.cmd" if os.name == "nt" else "npm"
    print(f"{BLUE}🚀 正在启动前端开发服务器 (监听端口: {env.get('FRONTEND_PORT', '5173')})...{RESET}")
    p_frontend = subprocess.Popen(
        [npm_cmd, "run", "dev"],
        cwd="frontend",
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
        encoding="utf-8",
        errors="ignore",
        env=sub_env
    )
    processes.append((p_frontend, "[FRONTEND] ", MAGENTA))
    
    # Start output stream reader threads
    threads = []
    for proc, prefix, color in processes:
        t = threading.Thread(target=log_stream, args=(proc.stdout, prefix, color), daemon=True)
        t.start()
        threads.append(t)
        
    print(f"\n{GREEN}{BOLD}🎉 所有本地服务进程已在后台并行拉起！{RESET}")
    print(f"├─ 前端访问地址: {BOLD}{UNDERLINE}http://localhost:{env.get('FRONTEND_PORT', '5173')}{RESET}")
    print(f"├─ 后端接口地址: {BOLD}{UNDERLINE}http://localhost:{env.get('SERVER_PORT', '8080')}/api/{RESET}")
    print(f"└─ 沙箱服务地址: {BOLD}{UNDERLINE}http://localhost:{go_judge_port}{RESET}")
    print(f"\n{YELLOW}📢 正在合并输出控制台日志。按 Ctrl+C 可优雅退出并关闭所有相关进程...{RESET}\n")
    
    try:
        # Keep main thread alive
        while True:
            # Check if main processes have crashed
            for proc, prefix, color in processes:
                if proc.poll() is not None:
                    print(f"\n{RED}⚠️  检测到进程 {prefix.strip()} 异常退出 (退出状态码: {proc.returncode})。{RESET}")
                    raise KeyboardInterrupt
            time.sleep(1)
    except KeyboardInterrupt:
        print(f"\n{YELLOW}⏳ 正在优雅关闭并关闭所有子进程，请稍候...{RESET}")
        for proc, prefix, color in processes:
            print(f"  正在中止 {prefix.strip()} (PID: {proc.pid})...")
            kill_process_tree(proc)
        print(f"{GREEN}✅ 所有子进程已完全关闭。退出成功！{RESET}")
        sys.exit(0)

if __name__ == '__main__':
    main()
