# 基于社交网络的开放学术发布系统

本项目是一个面向开放学术交流场景的研究成果发布与社区评价平台。系统支持作者发布论文、项目和数据集，读者可以进行搜索筛选、阅读、评论回复、点赞收藏、关注作者、五维评分、AI 辅助阅读和订阅通知，形成“发布 - 传播 - 评价 - 反馈 - 修改”的学术社交闭环。

项目并不试图替代正式期刊审稿流程，而是探索开放发布后如何通过社区评价、专家参考、兴趣画像和通知推送，为读者提供更清晰的质量判断依据，也为作者提供持续改进研究成果的反馈渠道。

## 在线访问

- 在线演示：http://39.105.193.95:8080/login.html
- GitHub 分支：https://github.com/Goodnight77zz/twieeetter/tree/FYP1

测试账号：

- 用户名：student1
- 密码：123456

说明：演示环境仅用于功能体验，数据可能不定期清理，请勿上传敏感文件。

## 技术栈

- 后端：Java 17，Spring Boot，Spring MVC，Spring Data JPA
- 数据库与缓存：MySQL，Redis
- 搜索与文件处理：MeiliSearch，Apache PDFBox，Apache POI
- AI 能力：DeepSeek API
- 前端：HTML，CSS，JavaScript
- 移动端：Flutter
- 工程化：Maven，GitHub Actions，阿里云 ECS

## 核心功能

- 用户体系：注册、登录、Session 登录态校验、个人主页、关注关系。
- 成果发布：填写标题、摘要、研究领域、关键词、作者机构等元数据，支持附件上传与内容维护。
- 搜索发现：支持关键词、多字段筛选、排序、同义词扩展和可选 MeiliSearch 检索。
- 社区互动：支持评论、回复、点赞、收藏、关注作者、作者采纳评论和站内通知。
- 五维评分：从创新性、方法合理性、证据充分性、可复现性和影响价值五个维度评价研究成果。
- 兴趣画像：结合订阅、收藏、发文、关注作者和标签信号，生成可解释的用户兴趣方向。
- 订阅推送：支持研究领域订阅、站内通知和邮件摘要推送。
- AI 辅助：支持 AI 阅读摘要、AI 初审、讨论引导、关键词提取、摘要润色和自然语言搜索解析。
- 多端访问：Web 页面与 Flutter 移动端复用同一套后端接口。

## 项目亮点

- 基于 `Session + Spring MVC Interceptor` 实现接口登录态校验，保护发布、评论、评分、通知等受保护接口。
- 设计五维评分与信誉权重机制，将普通用户评价和高信誉用户评价结合起来，为成果质量提供多维参考。
- 构建规则加权的兴趣画像模型，推荐结果可解释，适合数据规模较小的毕设系统和早期产品原型。
- 使用 Redis 缓存首页列表、成果详情、用户统计、通知列表和兴趣画像等热点数据，降低重复数据库查询压力。
- 接入 DeepSeek API，将大模型能力落地到阅读辅助、搜索意图解析和发布辅助等具体业务场景。
- 配置 GitHub Actions 自动部署流程，代码 push 到 `FYP1` 分支后自动构建 Spring Boot Jar、上传云服务器并执行部署脚本。
- 使用 k6 对云服务器环境下的高频接口进行并发压测，完成 500、700、1000 虚拟用户场景验证。

## 目录结构

```text
.
├── .github/workflows/          # GitHub Actions 自动部署配置
├── src/main/java/              # 后端 Java 源码
│   └── com/example/backend/
│       ├── config/             # 登录拦截器、Web 配置、缓存配置
│       ├── controller/         # REST API 控制器
│       ├── entity/             # JPA 实体
│       ├── repository/         # Spring Data JPA 仓库
│       └── service/            # 核心业务逻辑
├── src/main/resources/
│   ├── static/                 # Web 前端页面
│   ├── application.properties
│   ├── application-dev.properties
│   └── application-prod.properties
├── pom.xml
└── server-env.example          # 服务器环境变量示例
```

## 本地运行

### 1. 准备环境

- JDK 17
- MySQL 8.x
- Redis，可选，生产配置默认使用 Redis
- MeiliSearch，可选，未启用时系统会回退到数据库搜索

### 2. 创建数据库

```sql
CREATE DATABASE twieeetter_db
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### 3. 配置环境变量

项目中的真实密钥、数据库密码和邮箱授权码均通过环境变量读取，不会写入 Git 仓库。可以参考根目录下的 `server-env.example`。

本地开发时至少需要配置：

```bash
DB_PASSWORD=your_mysql_password
DEEPSEEK_API_KEY=your_deepseek_api_key
MAIL_USERNAME=your_mail_account
MAIL_PASSWORD=your_mail_authorization_code
MAIL_FROM=your_mail_account
```

Windows PowerShell 示例：

```powershell
$env:SPRING_PROFILES_ACTIVE="dev"
$env:DB_PASSWORD="your_mysql_password"
$env:DEEPSEEK_API_KEY="your_deepseek_api_key"
$env:MAIL_USERNAME="your_mail_account"
$env:MAIL_PASSWORD="your_mail_authorization_code"
$env:MAIL_FROM="your_mail_account"
```

### 4. 启动项目

```bash
./mvnw spring-boot:run
```

Windows 下可以使用：

```powershell
.\mvnw.cmd spring-boot:run
```

访问地址：

```text
http://localhost:8080/login.html
```

## 生产部署

当前仓库配置了 GitHub Actions 自动部署，触发条件为 push 到 `FYP1` 分支：

```yaml
on:
  push:
    branches: [ FYP1 ]
```

部署流程：

1. GitHub Actions 拉取代码并配置 JDK 17。
2. 执行 `mvn clean package -DskipTests` 构建 Jar。
3. 将 `target/backend-0.0.1-SNAPSHOT.jar` 上传到服务器 `/root`。
4. SSH 到服务器并执行 `/root/deploy.sh`。
5. `deploy.sh` 加载 `/root/twieeetter.env` 中的环境变量，停止旧进程并启动新版本。

服务器环境变量文件示例：

```bash
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
FILE_UPLOAD_DIR=/root/fyp_uploads/

DB_URL='jdbc:mysql://localhost:3306/twieeetter_db?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8&allowPublicKeyRetrieval=true'
DB_USERNAME=root
DB_PASSWORD='your_mysql_password'

DEEPSEEK_API_KEY='your_deepseek_api_key'

MAIL_USERNAME='your_mail_account'
MAIL_PASSWORD='your_mail_authorization_code'
MAIL_FROM='your_mail_account'

APP_BASE_URL='http://your_server_ip:8080'
APP_DIGEST_CRON='0 0 8 * * ?'

MEILISEARCH_ENABLED=false
MEILISEARCH_HOST=http://127.0.0.1:7700
MEILISEARCH_API_KEY=
MEILISEARCH_INDEX=tweets

APP_DEFAULT_USER_EMAIL=demo@example.com
```

该文件只应保存在服务器上，不应提交到 Git 仓库。

## 安全说明

- 仓库中的配置文件只保留环境变量占位符，不包含真实 API Key、数据库密码或邮箱授权码。
- 本地真实环境文件如 `twieeetter.env` 已被 `.gitignore` 排除。
- 若曾经将密钥提交到公开仓库，应及时在对应平台作废旧密钥并重新生成。

## 项目定位

这是一个本科毕业设计项目，重点在于完整实现开放学术发布系统的核心业务闭环，并将社交互动、结构化评价、兴趣画像、AI 辅助和自动部署等能力结合到一个可运行的工程系统中。项目仍有继续优化空间，例如引入更成熟的推荐算法、完善权限模型、优化 UI 组件化和补充自动化测试。
