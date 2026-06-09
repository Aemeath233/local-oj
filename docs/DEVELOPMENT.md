# 开发指南

本文档面向开发者，详细介绍如何搭建本地开发环境、进行前后端联调、编写和运行测试。

---

## 1. 前置要求

### 必需工具

| 工具 | 版本要求 | 用途 |
|------|---------|------|
| **JDK** | 21+ | 后端 Java 编译运行 |
| **Maven Wrapper** | 项目已内置 | 后端依赖管理和构建，不要求全局安装 Maven |
| **Node.js** | 20+ | 前端构建与运行 |
| **MySQL** | 8.0+ | 本地数据库存储 |
| **Redis** | 5.0+ | 本地缓存与判题任务队列 |
| **GCC & G++** | 建议安装 | 本地编译 C/C++ 提交代码所需 |
| **Python** | 3.10+ | 本地运行 Python 提交代码及启动脚本所需 |
| **Git** | 任意 | 版本管理 |

本地 Java 构建统一使用项目根目录的 `mvnw` / `mvnw.cmd`。Wrapper 默认使用 `.mvn/settings-cn.xml`，并把 Maven 发行版、依赖仓库和临时目录放在 `scratch/` 下，避免写入用户全局 `~/.m2`。

### 推荐 IDE

- **后端**: IntelliJ IDEA (推荐) 或 VS Code + Java Extension Pack
- **前端**: VS Code + Volar (Vue 3 扩展)

---

## 2. 本地开发环境一键运行

你可以通过项目根目录下的 [start.py](file:///d:/Code/coderush_oj/start.py) 脚本一键拉起本地全部服务。

### 2.1 启动本地 MySQL 和 Redis
在运行脚本之前，请确保你本地 of MySQL 和 Redis 服务已启动。
* **MySQL**：默认监听端口为 `3306`（如使用其他端口或密码，请在本地复制 `.env` 并在其中修改 `DB_URL` 等连接属性）。
* **Redis**：默认监听端口为 `6379`。

### 2.2 一键启动服务进程
在项目根目录下，直接运行一键启动脚本：
```bash
python start.py
```
该脚本将按顺序自动执行以下操作：
1. **配置文件生成**：若没有 `.env`，会根据 `.env.example` 自动拷贝生成。
2. **前置环境检测**：校验本地 Java (21+)、Node.js 运行时，以及 MySQL 和 Redis 的 TCP 端口连通性。如果本地检测不到 C++ 或 Python 编译器，会发出友情警告。
3. **沙箱自动下载**：自动为您的平台（Windows/Linux/macOS）下载对应版本的原生 `go-judge` 二进制文件至 `scratch/bin/`。
4. **并发拉起进程**：在后台并发启动沙箱、后端服务（Maven）、判题机（Maven）以及前端开发服务器（Vite），并以不同颜色标记输出合并后的日志。
5. **优雅关机**：在终端按下 `Ctrl+C` 即可优雅关闭所有后台子进程（脚本会自动终止 Windows 下的 Java 进程树，防止端口残留占用）。

### 2.3 手动单独运行（可选）
如果你希望使用 IDE (如 IntelliJ IDEA 或 VS Code) 单独断点调试后端或前端，可以这样手工运行：
* **沙箱服务**：直接运行 `scratch/bin/go-judge -addr :5050`
* **后端 API**：在根目录下运行 `.\mvnw.cmd -pl backend -am spring-boot:run` 或在 IDEA 中运行 `BackendApplication.java`。
* **判题 Worker**：在根目录下运行 `.\mvnw.cmd -pl judge-worker -am spring-boot:run` 或在 IDEA 中运行 `JudgeWorkerApplication.java`。
* **前端开发**：`cd frontend && npm install && npm run dev`

### 2.4 目录约定

| 路径 | 说明 |
|------|------|
| `backend/`, `common/`, `judge-worker/` | Java 多模块源码 |
| `frontend/` | 前端源码和前端唯一的 `package.json` |
| `data/` | 本地测试数据和系统日志存储路径，git-ignored |
| `scratch/` | Maven Wrapper、本地 Maven 仓库、原生 go-judge 运行目录，git-ignored |
| `frontend/dist/`, `frontend/node_modules/` | 前端构建产物 and 依赖，git-ignored |

根目录不再维护 Node 包配置；前端依赖安装和构建都在 `frontend/` 下执行。

---

## 3. 后端开发

### 3.1 模块结构

```
backend/src/main/java/com/coderushoj/backend/
├── BackendApplication.java        # Spring Boot 入口
├── api/
│   ├── ApiResponse.java           # 统一响应封装 record
│   ├── ApiError.java              # 错误响应 record
│   └── GlobalExceptionHandler.java  # 全局异常处理 (@RestControllerAdvice)
├── config/
│   └── RedisPubSubConfig.java     # Redis 消息监听配置
├── controller/                    # REST 控制器 (按功能划分)
│   ├── AuthController.java            # 登录/注册
│   ├── ProblemController.java         # 题目 CRUD
│   ├── SubmissionController.java      # 提交代码
│   ├── SubmissionSseController.java   # SSE 实时推送
│   ├── ContestController.java         # 竞赛
│   ├── LeaderboardController.java     # 排行榜
│   ├── TrainingController.java        # 训练集
│   ├── SelfTestController.java        # 自测运行
│   ├── ProfileController.java         # 用户资料
│   ├── Admin*Controller.java          # 管理后台系列
│   └── ...
├── security/
│   ├── SecurityConfig.java        # Spring Security 配置
│   ├── JwtService.java            # JWT 生成与解析
│   ├── JwtAuthenticationFilter.java  # JWT 请求过滤器
│   ├── SecurityUtils.java         # 安全工具方法
│   └── CurrentUser.java           # 当前用户注解
├── seed/
│   └── DataSeeder.java            # 初始管理员账号创建
├── gojudge/
│   ├── GoJudgeClient.java         # go-judge HTTP 客户端
│   ├── GoJudgeResult.java         # 运行结果模型
│   └── GoJudgeFileError.java      # 文件错误模型
└── service/                       # 业务逻辑层
```

### 3.2 添加新的 API 接口

1. **定义数据模型** (如需新表)
   - 在 `common/src/.../model/` 添加实体类
   - 在 `common/src/.../mapper/` 添加 Mapper 接口
   - 在 `backend/src/.../resources/db/migration/` 添加 Flyway SQL

2. **编写 Service**
   - 在 `backend/src/.../service/` 添加服务类

3. **编写 Controller**
   - 在 `backend/src/.../controller/` 添加控制器

4. **安全配置** (如需)
   - 在 `SecurityConfig.java` 中配置端点访问权限

### 3.3 数据库迁移

项目使用 Flyway 管理数据库 Schema 变更。迁移文件位于：

```
backend/src/main/resources/db/migration/
├── V1__init.sql                           # 初始化表结构
├── V2__smtp_registration.sql              # SMTP 和注册功能
├── ...
└── V28__add_user_auth_token_version.sql   # 最新迁移
```

**添加新迁移**:

```bash
# 命名格式: V{序号}__{描述}.sql
# 例如: V29__add_user_avatar_cropping.sql
```

> 注意: Flyway 迁移一旦执行就不可修改。如需调整已有表结构，必须创建新的迁移文件。

### 3.4 配置项

所有配置通过环境变量注入，默认值在 `application.yml` 中定义：

| 配置项 | 环境变量 | 默认值 | 说明 |
|--------|---------|--------|------|
| 数据库 URL | `DB_URL` | `jdbc:mysql://localhost:3306/coderush_oj...` | JDBC 连接串 |
| 数据库用户 | `DB_USERNAME` | `coderushoj` | MySQL 用户名 |
| 数据库密码 | `DB_PASSWORD` | `coderushoj_pass` | MySQL 密码 |
| Redis 地址 | `REDIS_HOST` | `localhost` | Redis 主机 |
| Redis 端口 | `REDIS_PORT` | `6379` | Redis 端口 |
| JWT 密钥 | `JWT_SECRET` | `change-this...` | JWT 签名密钥，生产环境必须使用 32 字符以上随机值 |
| JWT 有效期 | `JWT_TTL_MINUTES` | `10080` (7天) | Token 过期时间 |
| go-judge 地址 | `GO_JUDGE_BASE_URL` | `http://localhost:5050` | 沙箱 API 地址 |
| 数据根目录 | `APP_DATA_ROOT` | `/data` | 测试数据和日志存储路径 |
| CORS 白名单 | `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,...` | 跨域允许源 |
| 管理员账号 | `ADMIN_USERNAME` | `admin` | 初始管理员用户名 |
| 管理员密码 | `ADMIN_PASSWORD` | `admin123` | 初始管理员密码，生产环境必须修改 |

生产模式 (`SPRING_PROFILES_ACTIVE=prod`) 会拒绝默认或占位的 `JWT_SECRET` / `ADMIN_PASSWORD`。本地开发可以使用默认值；生产部署请按 `.env.example` 复制并填写真实密钥。

---

## 4. 前端开发

### 4.1 技术栈与工具链

| 工具 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5+ | Composition API + SFC |
| TypeScript | 5.9+ | 类型安全 |
| Vite | 7.0+ | 构建工具，支持 HMR |
| Element Plus | 2.11+ | UI 组件库 |
| Pinia | 3.0+ | 状态管理 |
| Vue Router | 4.5+ | SPA 路由 |
| Axios | 1.13+ | HTTP 客户端 |
| Monaco Editor | 0.55+ | 代码编辑器 |
| KaTeX | 0.16+ | LaTeX 数学公式渲染 |

### 4.2 目录结构

```
frontend/src/
├── main.ts              # 应用入口 (挂载 Vue, 注册插件)
├── App.vue              # 根组件 (全局导航栏 + <router-view>)
├── types.ts             # TypeScript 类型/接口定义
├── styles.css           # 全局 CSS 样式
├── api/
│   └── http.ts          # Axios 实例 + 所有 API 函数
├── router/
│   └── index.ts         # 路由定义 + 导航守卫
├── stores/
│   ├── auth.ts          # 认证状态 (token, user, login/logout)
│   └── theme.ts         # 主题状态 (亮色/暗色)
├── components/
│   ├── CodeEditor.vue       # Monaco 代码编辑器封装
│   ├── CodeDiffEditor.vue   # 代码差异对比
│   ├── MarkdownView.vue     # Markdown 渲染 (含 KaTeX)
│   ├── VerdictTag.vue       # 评测结果标签组件
│   ├── SubmissionDetailDrawer.vue  # 提交详情抽屉
│   ├── AvatarCropperDialog.vue     # 头像裁剪对话框
│   ├── AdminNav.vue         # 管理后台侧边栏导航
│   └── ...
├── views/               # 页面视图
│   ├── HomeView.vue         # 首页
│   ├── LoginView.vue        # 登录/注册
│   ├── Problem*.vue         # 题目相关
│   ├── Contest*.vue          # 竞赛相关
│   ├── Training*.vue        # 训练集相关
│   ├── Admin*.vue           # 管理后台
│   └── ...
└── utils/               # 工具函数
```

### 4.3 API 调用规范

所有 API 调用统一定义在 `src/api/http.ts` 中：

```typescript
// Axios 实例自动附加 JWT token
const api = axios.create({ baseURL: '/api' })
api.interceptors.request.use(config => {
  const token = localStorage.getItem('coderushoj.token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// API 函数示例
export async function getProblems(page: number): Promise<...> {
  const { data } = await api.get('/problems', { params: { page } })
  return data.data
}
```

**规范**：
- 每个 API 函数应有明确的 TypeScript 返回类型
- 错误由 Axios 拦截器统一处理 (401 自动登出)
- 类型定义在 `types.ts` 中集中管理

### 4.4 添加新页面

1. 在 `src/views/` 创建新的 `.vue` 文件
2. 在 `src/router/index.ts` 添加路由 (使用懒加载):
   ```typescript
   const NewView = () => import('../views/NewView.vue')
   // ...
   { path: '/new-path', component: NewView, meta: { requiresAuth: true } }
   ```
3. 如需新 API，在 `src/api/http.ts` 添加对应函数
4. 如需新类型，在 `src/types.ts` 添加 interface

### 4.5 构建生产版本

```bash
cd frontend
npm run build    # 输出到 frontend/dist/
```

构建会执行 `vue-tsc --noEmit` 进行 TypeScript 类型检查，然后通过 Vite 打包。

---

## 5. Judge Worker 开发

### 5.1 核心模块

```
judge-worker/src/main/java/com/coderushoj/worker/
├── JudgeWorkerApplication.java    # Spring Boot 入口
├── queue/
│   └── JudgeQueueConsumer.java    # Redis 队列消费者 (BRPOPLPUSH 循环)
├── service/
│   ├── JudgeService.java          # 判题核心逻辑
│   ├── OutputComparator.java      # 输出比对 (忽略行尾空白)
│   ├── SandboxSettingsProvider.java  # 沙箱参数配置
│   ├── TestCaseDataReader.java    # 测试数据文件读取
│   └── WorkerSystemLogService.java  # 系统日志记录
└── gojudge/
    ├── GoJudgeClient.java         # go-judge HTTP 客户端
    ├── GoJudgeResult.java         # 运行结果模型
    └── GoJudgeFileError.java      # 文件错误模型
```

### 5.2 判题流程

```
JudgeQueueConsumer (循环)
  ↓
  BRPOPLPUSH 获取 submissionId
  ↓
  JudgeService.judge(submissionId)
    ├── 读取 Submission + Problem + TestCase
    ├── 编译源代码 (GoJudgeClient.run)
    │   └── 编译失败 → verdict = CE, 结束
    ├── 逐个测试用例运行
    │   ├── GoJudgeClient.run(编译产物, 输入数据)
    │   ├── OutputComparator.compare(stdout, expectedOutput)
    │   └── 记录 SubmissionCaseResult
    ├── 汇总结果 (verdict, score, maxTime, maxMemory)
    ├── 更新 Submission (status=FINISHED)
    └── Redis PUBLISH 通知 Backend
```

---

## 6. 完整构建与测试

### 6.1 构建全部 Java 模块

```bash
# 在项目根目录
# Windows PowerShell
.\mvnw.cmd clean package -Dmaven.test.skip=true

# macOS / Linux
./mvnw clean package -Dmaven.test.skip=true
```

### 6.2 Maven 镜像与本地缓存

项目已预配置阿里云 Maven 镜像源，Wrapper 默认会使用该配置。也可以显式指定：

```bash
.\mvnw.cmd -s .mvn/settings-cn.xml clean package -Dmaven.test.skip=true
```

Wrapper 的 Maven home 和本地仓库位于 `scratch/maven-home/`，可按需删除后重新下载依赖。

## 7. 常用开发命令速查

```bash
# === 一键开发启动 ===
python start.py                             # 一键启动本地沙箱、后端、Worker和前端

# === 后端与 Worker ===
# Windows PowerShell
.\mvnw.cmd -pl backend -am spring-boot:run       # 启动后端 API
.\mvnw.cmd -pl judge-worker -am spring-boot:run  # 启动判题 Worker
.\mvnw.cmd test                                  # 运行单元测试

# macOS / Linux
./mvnw -pl backend -am spring-boot:run           # 启动后端 API
./mvnw -pl judge-worker -am spring-boot:run      # 启动判题 Worker
./mvnw test                                      # 运行单元测试

# === 前端 ===
cd frontend
npm install                                      # 安装依赖
npm run dev                                      # 启动 Vite 开发服务器 (HMR)
npm run build                                    # 构建生产版本 (输出至 dist/)
```

---

## 8. 代码规范

### 后端 (Java)

- 使用 Java 21 特性 (record, sealed class, pattern matching 等)
- Controller 方法使用 `@GetMapping` / `@PostMapping` 等注解
- 统一使用 `ApiResponse<T>` 封装响应
- 业务异常使用 `ResponseStatusException` 或自定义异常
- 数据库操作使用 MyBatis-Plus API，避免手写 SQL

### 前端 (TypeScript/Vue)

- 使用 Composition API (`<script setup>`) 编写组件
- 所有 API 返回类型在 `types.ts` 中定义
- 使用 Element Plus 组件，保持 UI 风格一致
- CSS 使用全局 `styles.css` 中的 CSS 变量
