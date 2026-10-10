# 安心到家：智能家政服务预约平台

安心到家是一个基于 Spring Boot、Spring Cloud 和 Spring AI 的家政服务微服务系统。平台覆盖客户与地址管理、服务项目、服务人员与技能、排班与可用性、预约履约、评价、身份认证以及 AI 辅助预约等完整业务流程。

AI 助手采用主控 Agent 与三个专业 Agent 协作：方案顾问负责需求分析，预约调度专员负责地址、价格、人员和排期查询，履约售后专员负责订单状态与取消准备。所有业务数据均通过受控 Tool 调用后端微服务，创建和取消操作必须经过一次性确认令牌，模型不能直接修改业务数据库。

## 核心能力

- 客户注册、登录、客户档案和多个上门服务地址管理
- 家政服务项目、技能、服务人员和人员排班管理
- 实时人员可用性检查、后端计价和预约冲突校验
- 预约创建、接单、服务中、完成、取消和评价流程
- CUSTOMER、WORKER、ADMIN 角色权限控制
- JWT 用户认证和独立的内部服务 JWT
- 主控 Agent、方案顾问、预约调度、履约售后多 Agent 协作
- Redis 对话记忆和一次性待确认操作
- PostgreSQL 长期记忆与可选 PGVector 语义检索
- Eureka 服务发现、Config Server、Gateway、Resilience4j 和可观测性支持

## 系统架构

```text
浏览器
  |
  v
API Gateway :8080
  |-- Customers Service :8181  -> 客户、地址
  |-- Vets Service      :8083  -> 服务人员、技能、项目、排班
  |-- Visits Service    :8082  -> 预约、状态、评价
  `-- GenAI Service     :8084  -> 认证、Agent、Tool、记忆、确认令牌

基础设施
  |-- Config Server     :8888
  |-- Eureka Server     :8761
  |-- Redis                    -> 对话记忆、待确认操作
  `-- PostgreSQL               -> 业务数据、账户、审计、长期记忆
```

## 模块说明

| 模块 | 当前职责 |
|---|---|
| `housekeeping-api-gateway` | 前端页面、统一 API 入口、JWT 校验和路由 |
| `housekeeping-customers-service` | 客户资料和服务地址 |
| `housekeeping-vets-service` | 家政人员、技能、服务项目、排班和可用性 |
| `housekeeping-visits-service` | 服务预约、状态流转、取消和评价 |
| `housekeeping-genai-service` | 登录注册、AI Agent、Tool、记忆、审计和确认操作 |
| `housekeeping-config-server` | 集中配置服务 |
| `housekeeping-discovery-server` | Eureka 服务注册与发现 |
| `housekeeping-admin-server` | Spring Boot Admin 运维监控 |

## 技术栈

- Java 17
- Spring Boot 4.0.1
- Spring Cloud 2025.1.0
- Spring Cloud Gateway、Config、Eureka、Circuit Breaker
- Spring Security、OAuth2 Resource Server、JWT
- Spring AI、DeepSeek Chat、DashScope/Qwen Embedding
- Spring Data JPA、HSQLDB、PostgreSQL、PGVector
- Redis、Resilience4j、Micrometer、Prometheus、Grafana

## Windows 本地启动

### 1. 环境要求

- JDK 17 或更高版本
- PowerShell
- DeepSeek API Key
- 需要语义记忆时配置 DashScope API Key

设置用户级环境变量：

```powershell
[Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "你的DeepSeek密钥", "User")
[Environment]::SetEnvironmentVariable("DASHSCOPE_API_KEY", "你的DashScope密钥", "User")
```

重新打开终端后启动：

```powershell
.\scripts\start-housekeeping.ps1 -Restart
```

脚本会构建并按以下顺序启动服务：

```text
Config Server -> Eureka -> Customers -> Vets -> Visits -> GenAI -> Gateway
```

访问地址：

- 应用首页：http://localhost:8080
- Eureka：http://localhost:8761
- Config Server：http://localhost:8888
- 运行日志：`target/run`

本地 `local` 配置提供演示账号：

| 角色 | 用户名 | 密码 |
|---|---|---|
| 客户 | `customer` | `customer123` |
| 服务人员 | `worker` | `worker123` |
| 管理员 | `admin` | `admin123` |

演示账号只用于本地开发，不应在生产环境启用。

## 手动构建与测试

完整构建：

```powershell
.\mvnw.cmd clean package
```

运行重点模块测试：

```powershell
.\mvnw.cmd -pl housekeeping-genai-service -am test
.\mvnw.cmd -pl housekeeping-customers-service,housekeeping-vets-service,housekeeping-visits-service -am test
```

## Docker 启动

Docker 模式使用 Redis、PostgreSQL/PGVector、Prometheus、Grafana、Zipkin 和各 Java 服务。启动前必须设置独立的强密码和密钥：

```text
HOUSEKEEPING_JWT_SECRET
HOUSEKEEPING_INTERNAL_JWT_SECRET
REDIS_PASSWORD
GENAI_DATABASE_ADMIN_PASSWORD
GENAI_DATABASE_PASSWORD
BUSINESS_DATABASE_ADMIN_PASSWORD
CUSTOMERS_DATABASE_PASSWORD
VETS_DATABASE_PASSWORD
VISITS_DATABASE_PASSWORD
DEEPSEEK_API_KEY
DASHSCOPE_API_KEY
```

构建镜像并启动：

```bash
./mvnw clean install -PbuildDocker
docker compose up
```

两个 JWT 密钥必须不同且至少包含 32 字节。各业务服务使用独立数据库账号和 Schema。

## AI 请求链

```text
用户问题
  -> HousekeepingChatClient 主控 Agent
  -> SpecialistAgents 专业 Agent
  -> SpecialistAgentTools Tool 白名单
  -> HousekeepingTools 权限与参数校验
  -> AIDataProvider 调用业务微服务
  -> 业务微服务校验并访问数据库
```

预约和取消采用后端确认流程：

```text
Agent 调用 prepare Tool
  -> Java 生成待确认操作并写入 Redis
  -> Token 由 ChatResponse.pendingActions 直接返回前端
  -> 用户点击确认并携带登录 JWT
  -> 后端校验用户、Token、有效期和业务状态
  -> 执行真实业务操作
```

确认 Token 不经过模型文本，十分钟过期，并通过 Redis `getAndDelete` 保证一次性消费。

## 安全设计

- 用户身份和角色只从 JWT 获取，不接受模型自行声明
- Tool 在 Java 层检查用户、角色和数据归属
- 业务微服务再次验证内部 Token 和业务规则
- 模型只获得完成任务所需的最小 Tool 集合
- 用户记忆按用户隔离，外部记忆内容按不可信数据处理
- 写操作先准备、再由用户确认
- 模型输出以纯文本渲染，避免执行不可信 HTML
- 审计记录会脱敏密码、Token 和 Secret

## 配置说明

主要配置文件：

- `housekeeping-genai-service/src/main/resources/application.yml`
- `housekeeping-genai-service/src/main/resources/application-local.yml`
- `housekeeping-genai-service/src/main/resources/application-postgresql.yml`
- `housekeeping-api-gateway/src/main/resources/application.yml`
- `docker-compose.yml`

API Key 应通过环境变量或未纳入版本控制的本地凭据文件提供，不要写入源码、README 或提交记录。

## License

本项目使用 Apache License 2.0，详情见 [LICENSE](LICENSE)。
