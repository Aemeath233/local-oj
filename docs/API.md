# API 参考文档

CodeRush OJ 后端提供 RESTful JSON API，所有响应统一封装为 `ApiResponse<T>` 格式。

---

## 通用约定

### 基础路径

```
/api
```

### 统一响应格式

**成功响应**:
```json
{
  "data": <T>
}
```

**错误响应** (HTTP 4xx/5xx):
```json
{
  "message": "错误描述"
}
```

### 认证

需要认证的接口在 HTTP Header 中携带 JWT Token：

```
Authorization: Bearer <jwt-token>
```

Token 通过 `/api/auth/login` 或 `/api/auth/register` 获取。

### 权限标记

| 标记 | 含义 |
|------|------|
| `Public` | 无需登录 |
| `Auth` | 需要登录 |
| `Admin` | 需要 ADMIN 或 SUPER_ADMIN 角色 |
| `SuperAdmin` | 需要 SUPER_ADMIN 角色 |

---

## 1. 认证 (Auth)

### POST /auth/login
`Public` — 用户登录

**请求体**:
```json
{
  "username": "string",
  "password": "string"
}
```

**响应**: `LoginResult` — `{ token, user }`

---

### POST /auth/register-code
`Public` — 请求注册邮箱验证码

**请求体**:
```json
{
  "email": "string"
}
```

---

### POST /auth/register
`Public` — 用户注册

**请求体**:
```json
{
  "username": "string",
  "email": "string",
  "displayName": "string (可选)",
  "password": "string",
  "code": "string"
}
```

**响应**: `LoginResult` — `{ token, user }`

---

### POST /auth/reset-password-code
`Public` — 请求重置密码验证码

**请求体**: `{ "email": "string" }`

---

### POST /auth/reset-password
`Public` — 重置密码

**请求体**:
```json
{
  "email": "string",
  "code": "string",
  "newPassword": "string"
}
```

---

## 2. 用户资料 (Profile)

### GET /profile
`Auth` — 获取当前用户信息

**响应**: `User`

---

### PUT /profile
`Auth` — 更新个人资料

**请求体**:
```json
{
  "username": "string (可选)",
  "displayName": "string",
  "studentNo": "string (可选)",
  "major": "string (可选)"
}
```

**响应**: `User`

---

### GET /profile/stats
`Auth` — 获取当前用户统计数据 (热力图、难度分布)

**响应**: `UserStats`

---

### GET /profile/{userId}/public
`Auth` — 获取指定用户的公开主页

**响应**: `PublicProfile`

---

### POST /profile/avatar
`Auth` — 上传头像 (multipart/form-data)

**请求体**: `file` — 图片文件

**响应**: `User`

---

### POST /profile/email-change-code
`Auth` — 请求更换邮箱验证码

**请求体**: `{ "newEmail": "string" }`

---

### PUT /profile/email
`Auth` — 更换邮箱

**请求体**: `{ "newEmail": "string", "code": "string" }`

**响应**: `User`

---

### POST /profile/password-code
`Auth` — 请求修改密码验证码

---

### PUT /profile/password
`Auth` — 修改密码

**请求体**: `{ "code": "string", "newPassword": "string" }`

---

## 3. 题目 (Problems)

### GET /problems
`Public` — 获取题目列表 (分页)

**查询参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| `q` | string | 搜索关键词 (标题/Slug) |
| `status` | string | 筛选: `UNATTEMPTED` / `ATTEMPTED` / `ACCEPTED` |
| `tags` | string | 按标签筛选 |
| `page` | number | 页码 |
| `pageSize` | number | 每页数量 |
| `sortBy` | string | 排序字段 |

**响应**: 分页的 `ProblemSummary[]`

---

### GET /problems/daily
`Public` — 获取每日一题

**响应**: `ProblemSummary`

---

### GET /problems/{id}
`Auth` — 获取题目详情 (含题面和样例)

**响应**: `ProblemDetail`

---

### GET /problems/{id}/solutions
`Auth` — 获取题目的题解列表

**响应**: `ProblemSolutionSummary[]`

---

### GET /problems/{problemId}/solutions/{solutionId}
`Auth` — 获取题解详情

**响应**: `ProblemSolutionDetail`

---

### POST /problems/{problemId}/solutions
`Auth` — 发布题解

**请求体**:
```json
{
  "title": "string",
  "content": "string (Markdown)"
}
```

**响应**: `ProblemSolutionDetail`

---

### DELETE /problems/{problemId}/solutions/{solutionId}
`Auth` — 删除题解 (仅本人或管理员)

---

## 4. 提交 (Submissions)

### POST /submissions
`Auth` — 提交代码评测

**请求体**:
```json
{
  "problemId": 1,
  "language": "CPP",
  "sourceCode": "#include...",
  "contestId": null
}
```

**语言枚举**: `C` / `CPP` / `CPP_O3` / `JAVA` / `PYTHON` / `PYPY3`

**响应**: `Submission`

---

### GET /submissions
`Auth` — 获取提交记录列表

**查询参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| `problemId` | number | 按题目筛选 |
| `mine` | boolean | 仅显示自己的提交 |

**响应**: `SubmissionSummary[]`

---

### GET /submissions/{id}
`Auth` — 获取提交详情 (含每个测试点结果)

**响应**: `SubmissionDetail` — `{ submission, problem, cases }`

---

### POST /submissions/sse-ticket
`Auth` — 获取 SSE 连接 ticket

**响应**: `{ ticket: "string" }`

---

### GET /submissions/{id}/events?ticket=xxx
`Auth` — SSE 长连接，实时监听评测结果

**事件**: 判题完成时推送 `SubmissionDetail`

---

## 5. 自测 (Self Test)

### POST /self-tests
`Auth` — 运行代码自测 (不计入提交记录)

**请求体**:
```json
{
  "problemId": 1,
  "contestId": null,
  "language": "CPP",
  "sourceCode": "...",
  "stdin": "输入数据"
}
```

**响应**: `SelfTestResult` — `{ verdict, timeMs, memoryKb, stdout, stderr }`

---

## 6. 竞赛 (Contests)

### GET /contests
`Public` — 获取竞赛列表

**响应**: `Contest[]`

---

### GET /contests/{id}
`Auth` — 获取竞赛详情

**响应**: `Contest`

---

### GET /contests/{id}/registration
`Auth` — 获取当前用户的竞赛报名状态

**响应**: `ContestRegistrationStatus`

---

### POST /contests/{id}/register
`Auth` — 报名参加竞赛

**响应**: `ContestRegistrationStatus`

---

### GET /contests/{id}/problems
`Auth` — 获取竞赛题目列表

**响应**: `ContestProblemDetail[]`

---

### GET /contests/{contestId}/problems/{problemId}
`Auth` — 获取竞赛中的题目详情

**响应**: `ProblemDetail`

---

### GET /contests/{id}/submissions
`Auth` — 获取竞赛提交列表

**响应**: `SubmissionSummary[]`

---

### GET /contests/{id}/leaderboard
`Auth` — 获取竞赛榜单

**响应**: `ContestStandingsRow[]`

---

### GET /contests/{id}/leaderboard/export
`Auth` — 导出竞赛榜单 (Excel/CSV)

**响应**: Blob 文件下载

---

## 7. 训练集 (Training)

### GET /training
`Auth` — 获取训练集列表

---

### GET /training/{id}
`Auth` — 获取训练集详情 (含题目列表和做题进度)

---

## 8. 排行榜 (Leaderboard)

### GET /leaderboard
`Public` — 获取全局排行榜

**查询参数**: `limit` — 返回数量 (默认 100)

**响应**: `LeaderboardRow[]`

---

### GET /leaderboard/my-rank
`Auth` — 获取当前用户排名

**响应**: `LeaderboardRow`

---

## 9. 其他

### POST /code/format
`Auth` — 代码格式化

**请求体**: `{ "language": "CPP", "sourceCode": "..." }`

**响应**: 格式化后的代码 string

---

### GET /system/versions
`Public` — 获取系统版本信息

**响应**: `Record<string, number>`

---

## 10. 管理后台 API (Admin)

### 仪表盘

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/admin/dashboard` | Admin | 获取管理后台仪表盘数据 |

---

### 题目管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/admin/problems` | Admin | 获取所有题目列表 (含不可见) |
| GET | `/admin/problems/{id}` | Admin | 获取题目详情 (含测试数据) |
| POST | `/admin/problems` | Admin | 创建新题目 |
| PUT | `/admin/problems/{id}` | Admin | 更新题目 |
| DELETE | `/admin/problems/{id}` | Admin | 删除题目 |
| PATCH | `/admin/problems/{id}/visibility` | Admin | 切换题目可见性 |
| GET | `/admin/problems/{id}/cases/{filename}` | Admin | 获取测试数据文件内容 |
| POST | `/admin/problems/import-files` | Admin | 上传测试数据文件 |
| POST | `/admin/problems/import-package` | Admin | 导入题目包 (ZIP) |
| POST | `/admin/problems/import-package/preview` | Admin | 预览题目包 |
| GET | `/admin/problems/example-package` | Admin | 下载示例题目包 |

---

### 提交管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/admin/submissions` | Admin | 获取所有提交记录 |
| POST | `/admin/submissions/{id}/rejudge` | Admin | 重新判题 |
| POST | `/admin/submissions/requeue-unfinished` | Admin | 重新入队未完成的提交 |

---

### 竞赛管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/admin/contests` | Admin | 获取所有竞赛 |
| GET | `/admin/contests/{id}` | Admin | 获取竞赛详情 |
| POST | `/admin/contests` | Admin | 创建竞赛 |
| PUT | `/admin/contests/{id}` | Admin | 更新竞赛 |
| DELETE | `/admin/contests/{id}` | Admin | 删除竞赛 |
| PATCH | `/admin/contests/{id}/visibility` | Admin | 切换竞赛可见性 |

---

### 训练集管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/admin/training` | Admin | 获取所有训练集 |
| GET | `/admin/training/{id}` | Admin | 获取训练集详情 |
| POST | `/admin/training` | Admin | 创建训练集 |
| PUT | `/admin/training/{id}` | Admin | 更新训练集 |

---

### 用户管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/admin/users` | SuperAdmin | 获取用户列表 (支持搜索) |
| PUT | `/admin/users/{id}` | SuperAdmin | 更新用户信息 (含角色、密码重置) |

---

### 标签管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/admin/tags` | Admin | 获取所有标签 |
| POST | `/admin/tags` | Admin | 创建标签 |
| PUT | `/admin/tags/{id}` | Admin | 更新标签 |
| DELETE | `/admin/tags/{id}` | Admin | 删除标签 |

---

### 系统设置

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/admin/settings/smtp` | SuperAdmin | 获取 SMTP 邮件配置 |
| PUT | `/admin/settings/smtp` | SuperAdmin | 更新 SMTP 配置 |
| GET | `/admin/settings/sandbox` | SuperAdmin | 获取沙箱配置 |
| PUT | `/admin/settings/sandbox` | SuperAdmin | 更新沙箱配置 |
| GET | `/admin/settings/system` | SuperAdmin | 获取系统全局设置 |
| PUT | `/admin/settings/system` | SuperAdmin | 更新系统全局设置 |

---

### 日志管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/admin/logs/toggle` | SuperAdmin | 获取日志开关状态 |
| POST | `/admin/logs/toggle` | SuperAdmin | 更新日志开关 |
| GET | `/admin/logs/download?type=backend\|worker` | SuperAdmin | 下载日志文件 |
| DELETE | `/admin/logs/clear?type=backend\|worker` | SuperAdmin | 清空日志文件 |

---

### 数据管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/admin/data/users/import` | SuperAdmin | 批量导入用户 (Excel/CSV) |
| POST | `/admin/data/submissions/cleanup` | SuperAdmin | 清理提交记录 |
| GET | `/admin/data/test-cases/stats` | SuperAdmin | 获取测试数据存储统计 |
| DELETE | `/admin/data/test-cases/orphaned` | SuperAdmin | 清理孤立测试数据文件 |
| GET | `/admin/data/backup/info` | SuperAdmin | 获取备份信息 |

---

### 死信队列 (DLQ)

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/admin/dlq` | Admin | 查看死信队列内容 |
| POST | `/admin/dlq/clear` | Admin | 清空死信队列 |
| POST | `/admin/dlq/requeue` | Admin | 将死信重新入队 |
