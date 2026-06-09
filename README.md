# CodeRush OJ

**局域网轻量化在线评测系统 (Online Judge)**

CodeRush OJ 是一套面向教学场景的轻量化在线评测系统，支持题目管理、在线编程、自动判题、竞赛模式和排行榜等核心功能。系统采用前后端分离架构，提供一键启动脚本并支持原生 Linux 服务托管部署，特别适合校内局域网环境使用。

---

## 功能特性

- **题目管理** — 支持 Markdown 题面、样例、测试数据文件管理，批量导入题目包
- **在线编程** — 集成 Monaco Editor 代码编辑器，支持 C / C++ / Java / Python / PyPy3 多语言
- **自动评测** — 基于 [go-judge](https://github.com/criyle/go-judge) 安全沙箱的代码编译与运行
- **竞赛模式** — 支持 ACM / OI 赛制，实时榜单（含封榜机制）、赛后题目可见性控制
- **训练集** — 按专题组织题目，支持做题进度追踪
- **排行榜** — 全局排名统计，支持排名变化趋势
- **用户系统** — JWT 认证，三级权限体系 (SUPER_ADMIN / ADMIN / STUDENT)，SMTP 邮箱注册验证
- **题解系统** — 用户可发布和浏览题解
- **后台管理** — 仪表盘、题目/竞赛/用户/标签/训练集管理、系统日志、数据导入导出
- **备份恢复** — 一键数据备份/恢复，支持冷热备份和跨机器迁移

---

## 技术栈

| 层级 | 技术 |
|------|------|
| **前端** | Vue 3 + TypeScript + Vite 7 + Element Plus + Pinia + Vue Router |
| **代码编辑** | Monaco Editor (本地离线加载) |
| **数学公式** | KaTeX (本地离线渲染) |
| **后端 API** | Spring Boot 3.5 + Java 21 + MyBatis-Plus 3.5 |
| **数据库** | MySQL 8.4 + Flyway 自动迁移 |
| **缓存/队列** | Redis 7.4 (判题任务队列 + SSE 结果推送) |
| **评测沙箱** | go-judge v1.12 (支持 C/C++/Java/Python/PyPy3) |
| **认证** | JWT (Spring Security) |
| **部署** | Nginx 反向代理，Systemd 进程守护 |

---

## 系统架构

```
┌────────────────────────────────────────────────────────────┐
│                      浏览器 (Vue 3 SPA)                     │
└────────────────────────┬───────────────────────────────────┘
                         │ HTTP / SSE
                         ▼
┌────────────────────────────────────────────────────────────┐
│               Nginx (前端静态资源 + API 反向代理)              │
│               服务端口: :80                                 │
└────────────────────────┬───────────────────────────────────┘
                         │ /api/*
                         ▼
┌────────────────────────────────────────────────────────────┐
│              Spring Boot 后端 (REST API)                    │
│              服务端口: :8080                                │
│  ┌──────┐  ┌──────────┐  ┌────────┐  ┌─────────────────┐  │
│  │ Auth │  │ Problem  │  │Contest │  │  Submission/SSE  │  │
│  │(JWT) │  │ Service  │  │Service │  │     Service      │  │
│  └──────┘  └──────────┘  └────────┘  └────────┬────────┘  │
└───────┬────────────────────────────────────────┼──────────┘
        │                                        │ Redis LPUSH
        ▼                                        ▼
┌──────────────┐                    ┌──────────────────────┐
│  MySQL 8.4   │                    │     Redis 7.4        │
│  数据库持久化  │                    │  判题队列 + Pub/Sub   │
└──────────────┘                    └───────────┬──────────┘
                                                │ BRPOPLPUSH
                                                ▼
                                   ┌──────────────────────┐
                                   │   Judge Worker        │
                                   │   (Spring Boot)       │
                                   │   消费队列 → 调用沙箱   │
                                   └───────────┬──────────┘
                                                │ HTTP REST
                                                ▼
                                   ┌──────────────────────┐
                                   │   go-judge 原生沙箱   │
                                   │   编译 + 运行 + 资源限制 │
                                   │   (Linux Cgroup/NS 隔离) │
                                   └──────────────────────┘
```

---

## 快速开始

### 前置要求

- **JDK 21+** (后端运行依赖)
- **Node.js 20+** (前端开发运行依赖)
- **MySQL 8.0+** & **Redis** (本地运行并处于启动状态)
- **Python 3.10+** (运行管理脚本依赖)
- **Git**

### 本地快速启动

1. **克隆项目并进入目录**：
   ```bash
   git clone <repository-url>
   cd coderush_oj
   ```

2. **运行启动控制台**：
   ```bash
   python start.py
   ```
   *该脚本会自动检测环境、创建本地 `.env` 配置文件、自动从 GitHub 下载适合当前系统的原生 `go-judge` 沙箱并并发启动所有服务进程。*

### 生产部署

请参考详细文档 [docs/NATIVE_DEPLOYMENT.md](file:///d:/Code/coderush_oj/docs/NATIVE_DEPLOYMENT.md) 进行原生 Linux 生产环境配置（基于 Nginx 反向代理与 Systemd 进程托管守护）。

### 访问系统

| 地址 | 说明 |
|------|------|
| `http://localhost:5173` | 前端界面 |
| `http://localhost:8080/api/` | 后端 API 接口 |

开发模式默认管理员账号：`admin` / `admin123` (首次启动后会由 Flyway 自动导入并在启动日志中打印管理员初始凭证，首次登录后请务必修改密码)。

---

## 项目结构

```
coderush_oj/
├── backend/                  # Spring Boot 后端 API 服务
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/coderushoj/backend/
│       │   ├── BackendApplication.java
│       │   ├── api/              # 统一响应封装 & 全局异常处理
│       │   ├── config/           # Redis Pub/Sub 等配置
│       │   ├── controller/       # REST 控制器
│       │   ├── gojudge/          # go-judge HTTP 客户端
│       │   ├── security/         # JWT 认证 & Spring Security
│       │   ├── seed/             # 数据初始化 (管理员账号)
│       │   └── service/          # 业务服务层
│       └── resources/
│           ├── application.yml   # Spring Boot 配置
│           └── db/migration/     # Flyway 数据库迁移 (V1~V28)
│
├── judge-worker/             # 评测判题消费者服务
│   ├── pom.xml
│   └── src/main/java/com/coderushoj/worker/
│       ├── JudgeWorkerApplication.java
│       ├── gojudge/              # go-judge HTTP 客户端
│       ├── queue/                # Redis 队列消费者
│       └── service/              # 判题逻辑 & 结果比对
│
├── common/                   # 共享模块 (实体 & Mapper)
│   ├── pom.xml
│   └── src/main/java/com/coderushoj/common/
│       ├── enums/                # 枚举: Language, Role, Verdict 等
│       ├── mapper/               # MyBatis-Plus Mapper 接口
│       ├── model/                # 数据库实体类
│       └── queue/                # 判题任务消息体
│
├── frontend/                 # Vue 3 前端 SPA
│   ├── nginx.conf                # Nginx 反向代理配置
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── App.vue               # 根组件 (含全局导航栏)
│       ├── main.ts               # 入口文件
│       ├── types.ts              # TypeScript 类型定义
│       ├── api/http.ts           # Axios HTTP 封装 & 所有 API 调用
│       ├── router/index.ts       # Vue Router 路由配置
│       ├── stores/               # Pinia 状态管理 (auth, theme)
│       ├── components/           # 可复用组件
│       │   ├── CodeEditor.vue        # Monaco 代码编辑器
│       │   ├── CodeDiffEditor.vue    # 代码差异对比
│       │   ├── MarkdownView.vue      # Markdown + KaTeX 渲染
│       │   ├── VerdictTag.vue        # 评测结果标签
│       │   ├── SubmissionDetailDrawer.vue  # 提交详情抽屉
│       │   └── ...
│       ├── views/                # 页面视图
│       │   ├── HomeView.vue          # 首页
│       │   ├── LoginView.vue         # 登录/注册
│       │   ├── ProblemListView.vue    # 题目列表
│       │   ├── ProblemDetailView.vue  # 题目详情 & 在线提交
│       │   ├── ContestListView.vue    # 竞赛列表
│       │   ├── ContestDetailView.vue  # 竞赛详情 & 实时榜单
│       │   ├── TrainingListView.vue   # 训练集列表
│       │   ├── LeaderboardView.vue    # 全局排行榜
│       │   ├── Admin*.vue            # 管理后台系列页面
│       │   └── ...
│       └── utils/                # 工具函数
│
├── data/                     # 运行时数据 (git-ignored)
│   └── oj/                      # 测试数据文件 & 后端日志
│
├── .mvn/
│   ├── settings-cn.xml          # 阿里云 Maven 镜像源配置
│   └── wrapper/                 # Maven Wrapper 配置
├── scratch/                  # 本地临时缓存 (Maven wrapper / repo / tmp，git-ignored)
│
├── .env.example              # 环境变量模板
├── .env                      # 本地环境变量 (git-ignored)
├── pom.xml                   # Maven 多模块根 POM
├── mvnw / mvnw.cmd           # 项目内 Maven Wrapper，不依赖全局 Maven
├── start.py                  # Windows/macOS 交互式控制台
└── README.md                 # 本文档
```

本地开发和测试优先使用 `./mvnw` 或 `.\mvnw.cmd`。Wrapper 会把 Maven 发行版、依赖仓库和临时目录放到项目内 `scratch/`，不会写入用户全局 `~/.m2`。

---

## 详细文档

| 文档 | 说明 |
|------|------|
| [架构设计](docs/ARCHITECTURE.md) | 系统架构、数据流、数据库模型、判题流程详解 |
| [开发指南](docs/DEVELOPMENT.md) | 本地开发环境搭建、项目构建、前后端联调、代码规范 |
| [部署指南](docs/NATIVE_DEPLOYMENT.md) | 原生生产环境部署、环境变量配置、运维管理、备份恢复 |
| [API 参考](docs/API.md) | 后端 REST API 接口文档 |

---

## 支持语言

| 语言 | 编译器/运行时 | 沙箱内版本 |
|------|-------------|-----------|
| C | GCC | Debian 默认版本 |
| C++ | G++ | Debian 默认版本 |
| C++ (O3) | G++ -O3 | 同上 |
| Java | OpenJDK 21 | 21 |
| Python 3 | CPython 3.12 | 3.12 |
| PyPy3 | PyPy3 | Debian 默认版本 |

---

## License

本项目为内部教学用途开发。
