# 开发指南

本文档面向开发者，详细介绍如何搭建本地开发环境、进行前后端联调、编写和运行测试。

---

## 1. 前置要求

### 必需工具

| 工具 | 版本要求 | 用途 |
|------|---------|------|
| **JDK** | 21+ | 后端 Java 编译运行 |
| **Maven** | 3.9+ | 后端依赖管理和构建 |
| **Node.js** | 20+ | 前端构建 |
| **npm** | 9+ | 前端包管理 |
| **Docker & Docker Compose** | v2+ | 基础设施服务 (MySQL, Redis, go-judge) |
| **Git** | 任意 | 版本管理 |

### 推荐 IDE

- **后端**: IntelliJ IDEA (推荐) 或 VS Code + Java Extension Pack
- **前端**: VS Code + Volar (Vue 3 扩展)

---

## 2. 本地开发环境搭建

### 2.1 启动基础设施服务

开发时只需启动 MySQL、Redis 和 go-judge 三个基础设施容器，后端和前端直接在本地运行以便热重载调试。

```bash
# 在项目根目录执行
docker compose up -d mysql redis go-judge
```

等待 MySQL 和 Redis 健康检查通过：

```bash
docker compose ps
# 确认 mysql 和 redis 状态为 healthy
```

### 2.2 启动后端

```bash
# 方式一: 使用 Maven 命令行
cd backend
mvn spring-boot:run -pl backend -am

# 方式二: 在 IDEA 中直接运行
# 打开根目录 pom.xml 作为 Maven 项目
# 运行 backend/src/.../BackendApplication.java 的 main 方法
```

后端启动后默认监听 `http://localhost:8080`。

> **首次启动**会自动执行 Flyway 数据库迁移和管理员账号初始化。

### 2.3 启动前端

```bash
cd frontend
npm install        # 首次需要安装依赖
npm run dev        # 启动 Vite 开发服务器
```

前端开发服务器默认监听 `http://localhost:5173`，Vite 已配置 API 代理：

```typescript
// vite.config.ts
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',  // 代理到本地后端
      changeOrigin: true
    }
  }
}
```

访问 `http://localhost:5173` 即可进入系统，前端代码修改后自动热重载。

---

## 3. 后端开发

### 3.1 模块结构

```
backend/src/main/java/com/localoj/backend/
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
│   ├── Admin*Controller.java          # 管理后台系列 (11 个)
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
└── service/                       # 业务逻辑层 (21 个 Service)
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
└── V26__add_problem_tag_relation_index.sql  # 最新迁移
```

**添加新迁移**:

```bash
# 命名格式: V{序号}__{描述}.sql
# 例如: V27__add_user_avatar_cropping.sql
```

> 注意: Flyway 迁移一旦执行就不可修改。如需调整已有表结构，必须创建新的迁移文件。

### 3.4 配置项

所有配置通过环境变量注入，默认值在 `application.yml` 中定义：

| 配置项 | 环境变量 | 默认值 | 说明 |
|--------|---------|--------|------|
| 数据库 URL | `DB_URL` | `jdbc:mysql://localhost:3306/local_oj...` | JDBC 连接串 |
| 数据库用户 | `DB_USERNAME` | `localoj` | MySQL 用户名 |
| 数据库密码 | `DB_PASSWORD` | `localoj_pass` | MySQL 密码 |
| Redis 地址 | `REDIS_HOST` | `localhost` | Redis 主机 |
| Redis 端口 | `REDIS_PORT` | `6379` | Redis 端口 |
| JWT 密钥 | `JWT_SECRET` | `change-this...` | JWT 签名密钥 (生产环境必须修改) |
| JWT 有效期 | `JWT_TTL_MINUTES` | `10080` (7天) | Token 过期时间 |
| go-judge 地址 | `GO_JUDGE_BASE_URL` | `http://localhost:5050` | 沙箱 API 地址 |
| 数据根目录 | `APP_DATA_ROOT` | `/data` | 测试数据和日志存储路径 |
| CORS 白名单 | `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,...` | 跨域允许源 |
| 管理员账号 | `ADMIN_USERNAME` | `admin` | 初始管理员用户名 |
| 管理员密码 | `ADMIN_PASSWORD` | `admin123` | 初始管理员密码 |

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
├── views/               # 页面视图 (26 个)
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
  const token = localStorage.getItem('localoj.token')
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
judge-worker/src/main/java/com/localoj/worker/
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
mvn clean package -Dmaven.test.skip=true
```

### 6.2 使用中国大陆 Maven 镜像

项目已预配置阿里云 Maven 镜像源：

```bash
mvn -s .mvn/settings-cn.xml clean package -Dmaven.test.skip=true
```

### 6.3 Docker 构建

```bash
# 构建并启动所有服务
docker compose up -d --build

# 仅构建某个服务
docker compose build backend
docker compose build judge-worker
docker compose build frontend
docker compose build go-judge
```

---

## 7. 常用开发命令速查

```bash
# === 基础设施 ===
docker compose up -d mysql redis go-judge   # 启动开发依赖
docker compose down                         # 停止所有服务
docker compose logs -f backend              # 查看后端日志
docker compose logs -f judge-worker         # 查看判题日志

# === 后端 ===
cd backend && mvn spring-boot:run           # 启动后端 (热加载需 IDEA devtools)
mvn clean package -Dmaven.test.skip=true    # 构建 JAR

# === 前端 ===
cd frontend && npm install                  # 安装依赖
cd frontend && npm run dev                  # 启动开发服务器 (HMR)
cd frontend && npm run build                # 构建生产版本

# === Docker 全量 ===
docker compose up -d --build                # 重新构建并启动
python start.py                             # 交互式控制台
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
