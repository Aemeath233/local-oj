# 🚀 CodeRush OJ - 秒哒前端联调 API 规格说明书 (PC 网页端)

本说明书专门为**秒哒 (MiaoDa) AI 零代码开发平台**设计。文档中详细定义了所有面向普通用户的核心功能接口、请求报文、响应结构以及最佳联调方案。您可直接将此文档整体或部分复制投喂给秒哒，让其智能体为您一次性生成 100% 正确的前端交互与联调代码。

---

## 🌐 全局技术规范与约定

### 1. 基础访问地址 (Base URL)
*   **开发联调地址**：在 Windows 本地开发时，可使用内网穿透（如 cpolar/ngrok）生成的公网 HTTPS 链接作为 `baseURL`，例如：`https://your-local-oj.cpolar.cn`
*   **生产部署地址**：在您的 Linux 服务器部署上线后，直接使用服务器的公网 IP 或解析域名，例如：`http://your-server-ip:5173` (前端端口) 和 `http://your-server-ip:8080` (后端 API 端口)

### 2. 通信协议与数据格式
*   **协议**：标准的 HTTP/HTTPS。
*   **请求与响应格式**：一律采用 `application/json;charset=UTF-8`。
*   **跨域设置 (CORS)**：已由后端动态支持。请在已部署的**PC管理员后台 ➡️ 管理 ➡️ 设置**中，将秒哒前端的公网域名（或在测试阶段直接填入通配符 `*`）加入 **CORS Allowed Origins**，保存即可打通跨域。

### 3. 会话与安全认证 (JWT Flow)
*   所有需要登录后访问的接口（在下方有 `🔒 需授权认证` 标识），请求时必须在 **HTTP Request Header** 中携带 JWT Token：
    *   **Header Key**: `Authorization`
    *   **Header Value**: `Bearer <Your_JWT_Token>` (注意：Bearer 与 Token 之间有一个空格)
*   *前端存储建议*：登录成功后，将返回的 Token 存入浏览器的 `sessionStorage.setItem('token', token)`，并在每次发起 Axios/fetch 请求时拦截注入。

---

## 🔑 第一部分：用户认证模块 (Authentication)

本模块包含登录、验证码发送、注册、重置密码等接口。接口请求均**无需授权**。

### 1. 发送注册邮箱验证码
*   **接口路径**: `POST /api/auth/register-code`
*   **功能说明**: 输入邮箱，系统向该邮箱发送一封包含 6 位随机验证码的邮件。
*   **请求体 (JSON)**:
    ```json
    {
      "email": "student@your-university.edu.cn"
    }
    ```
*   **响应体 (JSON)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": null
    }
    ```

### 2. 提交注册个人资料
*   **接口路径**: `POST /api/auth/register`
*   **功能说明**: 提交用户名、邮箱、昵称、密码及刚才收到的 6 位邮箱验证码。注册成功后将**直接返回登录凭证**，免去二次登录。
*   **请求体 (JSON)**:
    ```json
    {
      "username": "coder_geek",             // 唯一英文/数字登录用户名
      "email": "student@your-university.edu.cn", // 刚才接收验证码的邮箱
      "displayName": "代码掌控者",           // 页面展示的用户昵称
      "password": "strongPassword123",      // 登录密码
      "code": "123456"                      // 邮箱收到的 6 位验证码
    }
    ```
*   **响应体 (JSON)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": {
        "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJjb2Rlcl9nZWVr...", // 关键：会话 Token
        "username": "coder_geek",
        "displayName": "代码掌控者",
        "email": "student@your-university.edu.cn",
        "role": "USER" // 角色标识: USER
      }
    }
    ```

### 3. 用户登录接口
*   **接口路径**: `POST /api/auth/login`
*   **功能说明**: 输入账号密码获取 Token。
*   **请求体 (JSON)**:
    ```json
    {
      "username": "coder_geek",
      "password": "strongPassword123"
    }
    ```
*   **响应体 (JSON)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": {
        "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "username": "coder_geek",
        "displayName": "代码掌控者",
        "email": "student@your-university.edu.cn",
        "role": "USER"
      }
    }
    ```

### 4. 发送找回密码验证码
*   **接口路径**: `POST /api/auth/reset-password-code`
*   **功能说明**: 找回密码时，向绑定邮箱发送 6 位验证码。
*   **请求体 (JSON)**:
    ```json
    {
      "email": "student@your-university.edu.cn"
    }
    ```
*   **响应体 (JSON)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": null
    }
    ```

### 5. 提交重置密码
*   **接口路径**: `POST /api/auth/reset-password`
*   **功能说明**: 提交验证码与新密码完成重置。
*   **请求体 (JSON)**:
    ```json
    {
      "email": "student@your-university.edu.cn",
      "code": "654321",
      "newPassword": "newSecretPassword789"
    }
    ```
*   **响应体 (JSON)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": null
    }
    ```

### 6. 获取当前登录用户信息 🔒 `需授权认证`
*   **接口路径**: `GET /api/auth/me`
*   **功能说明**: 校验并读取当前 Session 会话的用户详情，常用于在首页或导航栏个人信息处调用。
*   **响应体 (JSON)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": {
        "id": 12,
        "username": "coder_geek",
        "email": "student@your-university.edu.cn",
        "displayName": "代码掌控者",
        "role": "USER",
        "createdAt": "2026-05-30T10:00:00"
      }
    }
    ```

---

## 📚 第二部分：题库模块 (Problem Set)

题库接口在获取列表和详情时**无需授权**（未登录亦可游览），但是**登录后调用可以额外返回用户对该题的“答题状态”**（是否已通过等）。

### 1. 获取题目列表
*   **接口路径**: `GET /api/problems`
*   **查询参数 (Query Parameters)**:
    *   `q` (可选): 搜索关键字
    *   `status` (可选): 筛选答题状态（`ACCEPTED`/`ATTEMPTED`/`UNATTEMPTED`）
    *   `page` (可选): 页码，1-indexed。传入此参数将开启分页。
    *   `pageSize` (可选, 默认20): 分页大小。
    *   `sortBy` (可选, 默认 `ID_ASC`): 排序规则。可选值：`ID_ASC`, `ID_DESC`, `DIFFICULTY_ASC`, `DIFFICULTY_DESC`, `AC_RATE_ASC`, `AC_RATE_DESC`
*   **响应体 (JSON - 开启分页时)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": {
        "list": [
          {
            "id": 1,
            "title": "A + B Problem",
            "difficulty": "EASY",             // 难度标识：EASY(简单), MEDIUM(中等), HARD(困难)
            "solveStatus": "ACCEPTED",        // 我的状态：ACCEPTED(已通过), ATTEMPTED(尝试未过), UNATTEMPTED(未开始)
            "acceptCount": 142,               // 通过的提交数
            "submitCount": 350                // 总提交数
          }
        ],
        "total": 45 // 题库中匹配的总题目数
      }
    }
    ```

### 2. 获取单个题目详情
*   **接口路径**: `GET /api/problems/{id}`
*   **功能说明**: 获取特定 ID 的题目描述与测试样例。
*   **响应体 (JSON)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": {
        "id": 1,
        "title": "A + B Problem",
        "description": "## 题目描述\n输入两个整数 $A$ 和 $B$，输出它们的和。\n\n## 输入格式\n一行输入两个空格分隔的整数。\n\n## 输出格式\n输出和。",
        "difficulty": "EASY",
        "timeLimit": 1000,   // 时间限制 (毫秒)
        "memoryLimit": 65536, // 内存限制 (KB)
        "solveStatus": "ACCEPTED",
        "stats": {
          "acceptCount": 142,
          "submitCount": 350
        },
        "samples": [ // 供前端页面展示的示例测试样例
          {
            "input": "1 2\n",
            "output": "3\n"
          }
        ]
      }
    }
    ```

---

## 🎯 第三部分：专项练习模块 (Specialized Practice)

练习题单面向登录用户展示。

### 1. 获取所有专项练习题单 🔒 `需授权认证`
*   **接口路径**: `GET /api/training`
*   **响应体 (JSON)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": [
        {
          "id": 1,
          "title": "新手入门 - 基础语法百题斩",
          "description": "专为编程新手准备的语法练习合集，涵盖变量、分支与循环。",
          "problemCount": 15,
          "solvedCount": 4
        }
      ]
    }
    ```

### 2. 获取特定题单下的题目集与我的状态 🔒 `需授权认证`
*   **接口路径**: `GET /api/training/{id}/problems`
*   **响应体 (JSON)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": [
        {
          "id": 101,
          "title": "输出 Hello World",
          "difficulty": "EASY",
          "solveStatus": "ACCEPTED" // ACCEPTED, ATTEMPTED, UNATTEMPTED
        },
        {
          "id": 102,
          "title": "基础数值比较",
          "difficulty": "EASY",
          "solveStatus": "UNATTEMPTED"
        }
      ]
    }
    ```

---

## 🏆 第四部分：全站排行榜模块 (Leaderboard)

排行榜用于展示本平台学生的学成硕果，按 AC 数降序排列。接口均**无需授权**。

### 1. 获取全站排行榜前 N 名
*   **接口路径**: `GET /api/leaderboard`
*   **查询参数**:
    *   `limit` (可选, 默认 100): 前多少名。
*   **响应体 (JSON)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": [
        {
          "rank": 1,
          "nickname": "代码掌控者",
          "studentNumber": "202601001",
          "major": "计算机科学与技术",
          "acceptedCount": 42,
          "submitCount": 98
        },
        {
          "rank": 2,
          "nickname": "Bug猎手",
          "studentNumber": "202601005",
          "major": "软件工程",
          "acceptedCount": 38,
          "submitCount": 110
        }
      ]
    }
    ```

### 2. 获取我个人的排名信息 (高亮底栏) 🔒 `需授权认证`
*   **接口路径**: `GET /api/leaderboard/my-rank`
*   **响应体 (JSON)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": {
        "rank": 12,
        "nickname": "我的昵称",
        "studentNumber": "202601099",
        "major": "人工智能",
        "acceptedCount": 15,
        "submitCount": 45
      }
    }
    ```

---

## 🏁 第五部分：比赛模块 (Contests)

比赛相关获取列表与详情不强制登录，但是参赛报名与获取题目**必须授权认证**。

### 1. 获取比赛列表
*   **接口路径**: `GET /api/contests`
*   **响应体 (JSON)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": [
        {
          "id": 1,
          "title": "2026年夏季极客算法排位赛",
          "startTime": "2026-06-15T14:00:00",
          "endTime": "2026-06-15T17:00:00",
          "status": "NOT_STARTED", // NOT_STARTED(未开始), RUNNING(进行中), ENDED(已结束)
          "type": "PUBLIC"
        }
      ]
    }
    ```

### 2. 检查我的比赛报名状态 🔒 `需授权认证`
*   **接口路径**: `GET /api/contests/{id}/registration`
*   **响应体 (JSON)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": {
        "registered": true // 是否已报名
      }
    }
    ```

### 3. 报名参加比赛 🔒 `需授权认证`
*   **接口路径**: `POST /api/contests/{id}/register`
*   **响应体 (JSON)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": {
        "registered": true
      }
    }
    ```

### 4. 获取比赛专属题目集 (限进行中/已结束) 🔒 `需授权认证`
*   **接口路径**: `GET /api/contests/{id}/problems`
*   **响应体 (JSON)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": [
        {
          "id": 1001,
          "title": "极客生存挑战",
          "difficulty": "MEDIUM",
          "solveStatus": "ATTEMPTED"
        }
      ]
    }
    ```

### 5. 获取比赛专属题目详情 (限进行中/已结束) 🔒 `需授权认证`
*   **接口路径**: `GET /api/contests/{id}/problems/{problemId}`
*   *参数说明*：`id` 为比赛 ID，`problemId` 为题目 ID。
*   **响应体 (JSON)**: 格式完全等同于**普通题目详情**响应结构。

### 6. 获取该比赛的 ACM 实时排行榜 (Standings)
*   **接口路径**: `GET /api/contests/{id}/leaderboard`
*   **响应体 (JSON)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": [
        {
          "rank": 1,
          "username": "coder_geek",
          "nickname": "代码掌控者",
          "solvedCount": 4,      // 通过的题目总数
          "totalPenalty": 280,   // 总罚时(分钟)
          "problems": {          // 每个人在各题的通过状态
            "1001": {
              "accepted": true,
              "penalty": 45,
              "failedCount": 1  // AC 前失败的次数
            }
          }
        }
      ]
    }
    ```

---

## 🕒 第六部分：代码提交与评测轮询模块 (Submissions)

这是系统实现的核心闭环。用户在此处向沙箱发起代码评测，并启用 1 秒定时器进行高频结果轮询。

### 1. 提交代码至评测沙箱 🔒 `需授权认证`
*   **接口路径**: `POST /api/submissions`
*   **请求体 (JSON)**:
    ```json
    {
      "problemId": 1,
      "language": "CPP",     // 评测语言支持：CPP (C++) 或 PYTHON (Python 3)
      "sourceCode": "#include <iostream>\nusing namespace std;\nint main() {\n  int a, b;\n  cin >> a >> b;\n  cout << a + b;\n  return 0;\n}",
      "contestId": null      // 如果是普通题库答题，传 null；如果是比赛中提交，则传入比赛 ID
    }
    ```
*   **响应体 (JSON)**:
    *   后端将**立刻**返回提交成功，生成唯一的提交记录 ID。
    ```json
    {
      "code": 200,
      "message": "success",
      "data": {
        "id": 2026060100012, // 关键：本次评测的唯一 ID
        "problemId": 1,
        "language": "CPP",
        "verdict": "PENDING" // 初始评测状态：PENDING
      }
    }
    ```

### 2. 查询全站/我个人的提交记录列表 🔒 `需授权认证`
*   **接口路径**: `GET /api/submissions`
*   **响应体 (JSON)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": [
        {
          "id": 2026060100012,
          "problemId": 1,
          "problemTitle": "A + B Problem",
          "username": "coder_geek",
          "language": "CPP",
          "verdict": "ACCEPTED",     // 评测状态结果
          "timeCost": 12,            // 消耗时间 (ms)
          "memoryCost": 1124,        // 消耗内存 (KB)
          "createdAt": "2026-06-01T09:25:00"
        }
      ]
    }
    ```

### 3. 获取单条提交详情（★ 用于前端开启 1s 定时器轮询的核心接口）🔒 `需授权认证`
*   **接口路径**: `GET /api/submissions/{id}`
*   **功能说明**: 前端在拿到 `POST` 提交成功的 `id` 后，应当开启一个每 1 秒执行一次的 `setInterval`，不断请求该接口，直到返回的 `verdict` 不是 `PENDING`, `COMPILING`, `JUDGING`。
*   **响应体 (JSON)**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": {
        "submission": {
          "id": 2026060100012,
          "problemId": 1,
          "language": "CPP",
          "sourceCode": "#include <iostream>...",
          "verdict": "ACCEPTED",       // 评测状态：PENDING, COMPILING, JUDGING, ACCEPTED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED, COMPILE_ERROR, RUNTIME_ERROR
          "timeCost": 12,              // 消耗时间(ms)
          "memoryCost": 1124,          // 消耗内存(KB)
          "compileError": null,        // 编译错误时的错误日志 (String)，verdict 为 COMPILE_ERROR 时该字段有值
          "createdAt": "2026-06-01T09:25:00"
        },
        "problem": {
          "id": 1,
          "title": "A + B Problem"
        },
        "cases": [ // 详细评测点判定结果
          {
            "id": 101,
            "verdict": "ACCEPTED",
            "timeCost": 2,
            "memoryCost": 512,
            "score": 10
          }
        ]
      }
    }
    ```

---

## 💡 终极前端开发代码样板 (Copy-Paste)

您可以直接让秒哒**基于以下两段完美的前端代码样板**进行拖拽及页面生成。

### 1. 简易高亮文本框 HTML/CSS/JS 全量样板
在秒哒的“自定义组件/自定义代码”中填入如下逻辑，实现高亮代码编辑框：

```html
<!-- HTML 结构 -->
<div class="code-box">
  <pre class="underlay" id="underlayArea"><code id="codeArea"></code></pre>
  <textarea class="textarea-input" id="codeEditor" placeholder="在这里编写您的代码..." spellcheck="false"></textarea>
</div>

<style>
/* CSS 样式 */
.code-box {
  position: relative;
  width: 100%;
  height: 400px;
  background-color: #1e1e1e;
  border: 1px solid #3a3a3a;
  border-radius: 6px;
  overflow: hidden;
  font-family: 'Consolas', 'Courier New', monospace;
  font-size: 14px;
}
.textarea-input, .underlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 12px;
  border: none;
  background: transparent;
  font-family: inherit;
  font-size: inherit;
  line-height: 20px;
  white-space: pre-wrap;
  word-wrap: break-word;
  box-sizing: border-box;
}
.textarea-input {
  color: transparent; /* 输入文字透明 */
  caret-color: #ffffff; /* 保留白色光标 */
  resize: none;
  outline: none;
  z-index: 2;
}
.underlay {
  color: #abb2bf; /* 默认文字灰色 */
  z-index: 1;
  pointer-events: none;
  overflow-y: auto;
}
</style>

<script>
// JS 关键字着色控制
const editor = document.getElementById('codeEditor');
const codeArea = document.getElementById('codeArea');
const underlay = document.getElementById('underlayArea');

const keywords = {
  cpp: /\b(int|double|float|char|bool|void|if|else|for|while|return|include|using|namespace|std|cin|cout|main|class|struct|public|private)\b/g,
  python: /\b(def|class|import|from|if|elif|else|for|while|return|print|in|and|or|not|None|True|False)\b/g
};

let selectedLanguage = 'cpp'; // 可选 'cpp' 或 'python'

function doHighlight(text, lang) {
  let escaped = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  if (lang === 'cpp') {
    escaped = escaped.replace(keywords.cpp, '<span style="color: #c678dd; font-weight: bold;">$1</span>');
    escaped = escaped.replace(/(#include|#define)/g, '<span style="color: #e06c75;">$1</span>');
  } else if (lang === 'python') {
    escaped = escaped.replace(keywords.python, '<span style="color: #c678dd; font-weight: bold;">$1</span>');
  }
  return escaped;
}

editor.addEventListener('input', (e) => {
  codeArea.innerHTML = doHighlight(e.target.value, selectedLanguage);
});

editor.addEventListener('scroll', (e) => {
  underlay.scrollTop = e.target.scrollTop;
  underlay.scrollLeft = e.target.scrollLeft;
});
</script>
```

### 2. 定时器 1 秒高频轮询评测 JS 逻辑
向秒哒提供如下 AJAX 请求流控制，实现全自动的评测反馈：

```javascript
let pollTimer = null;

// 入口：提交代码
function submitCodeToJudge(problemId, lang, code) {
  // 发起 POST 请求
  fetch('https://your-server-api.com/api/submissions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + sessionStorage.getItem('token') // 注入 JWT Token
    },
    body: JSON.stringify({
      problemId: problemId,
      language: lang,
      sourceCode: code
    })
  })
  .then(res => res.json())
  .then(resData => {
    if (resData.code === 200) {
      const subId = resData.data.id;
      showInfoModal("代码已上传！开始进行后台编译与沙箱评测...");
      
      // 开启 1 秒高频轮询定时器
      pollTimer = setInterval(() => {
        pollStatus(subId);
      }, 1000);
    } else {
      alert("提交出错: " + resData.message);
    }
  });
}

// 轮询核心逻辑
function pollStatus(submissionId) {
  fetch('https://your-server-api.com/api/submissions/' + submissionId, {
    method: 'GET',
    headers: {
      'Authorization': 'Bearer ' + sessionStorage.getItem('token')
    }
  })
  .then(res => res.json())
  .then(resData => {
    if (resData.code === 200) {
      const details = resData.data.submission;
      const verdict = details.verdict; // 当前评测状态
      
      updateUIStatus(verdict); // 更新页面显示的评测状态
      
      // 当 verdict 变成最终状态（非等待或判题中）时，停止轮询并结案
      if (verdict !== "PENDING" && verdict !== "COMPILING" && verdict !== "JUDGING") {
        clearInterval(pollTimer);
        pollTimer = null;
        
        if (verdict === "ACCEPTED") {
          showConfettiEffect(); // 评测通过！触发庆祝小礼花
          alert("🎉 恭喜！您的代码通过了所有测试数据点 (ACCEPTED)！");
        } else {
          alert("❌ 评测未通过！结果为: " + verdict);
          if (verdict === "COMPILE_ERROR") {
            showCompileLogs(details.compileError); // 编译失败，展示详细报错日志
          }
        }
      }
    }
  });
}
```

---

有了这套堪称**教科书级别**的 API 对接字典和完美的代码样板，秒哒在开发时就像拥有了地图一样顺畅。祝你在“秒哒杯”中顺利降维打击、成功捧得大奖！🏆
