# 树果任务浏览器接入

目标：把已有真实 Java 任务与玩家审批搬到可操作页面，补齐 V1 Agent Panel。
默认继续使用低价 OpenAI gpt-6-luna；Gemini 作为环境变量指定的可选提供方。
现有回声群岛客户端属于另一份协议与玩法，此页面单独部署，不复制其预写状态。

本阶段范围：Java 8 JDK HttpServer，只监听 127.0.0.1；最多 8 个独立 Cookie 会话/房间。
单一世界调度线程创建场景、执行控制命令、读取快照、提交模型结果；后台工作池只接收不可变上下文。
内存存储，重启清空。静态页面与 API 同源，500 毫秒轮询，不宣称 WebSocket 或数据库已完成。
资产与任务完成由实际规则判定。等待模型、审批和暂停时不推进这个确定性场景的游戏回合；
该规则不等于原 World 的昼夜与 NPC 调度已联调。

API：GET /api/agent/session 获取 HttpOnly SameSite=Strict 会话 Cookie 与 CSRF token；
POST /api/agent/rooms 创建/获取当前会话房间；
GET /api/agent/rooms/{id}/snapshot 获取当前房间状态；
POST /api/agent/rooms/{id}/commands 接收 requestId、taskId、expectedRevision、command、params。
命令：PARSE、CONFIRM、PAUSE、RESUME、CANCEL、APPROVE、DENY、RESET。
服务端绑定 owner，不接受玩家 ID 或任意工具执行；审批只传 proposalId，不向前端返回批准令牌。
所有 POST 必须同源 Origin、CSRF header、会话 Cookie；Host 必须对应本机绑定地址，禁用 CORS。
静态资源精确白名单，不允许读取任意项目文件。请求大小与 JSON 结构限制，服务器和模型工作池有界。
每房间请求幂等缓存 256 项，超出拒绝不驱逐；每会话限频，每房间解析/恢复次数与 Agent 步数有界。
状态 revision 单调；旧任务 ID 拒绝。Pause/Cancel 允许同任务旧视图，以便及时停止；其他命令要求当前 revision。

界面：自然语言表单、解析确认、约束/状态、实际位置、携带/已交付数量、金币、回合、真实动作列表、
购买审批卡、控制按钮、错误提示与重复演示。模型隐藏推理、密钥、批准令牌不展示。
断线禁用操作，命令仅确认接收，资产只由快照更新；请求重试保持同一 requestId/payload。

验收：Cookie 隔离、跨会话拒绝、CSRF/Origin/Host、严格输入、重复命令、任务 revision、
解析取消/超时/迟到结果、暂停/恢复/取消、安全审批、实际完成、浏览器断线/重连与新任务旧快照。
真实模型联调一次；审批分支用明确假 HTTP 响应验证真实 Java 规则，不假称模型一定选择购买。

本轮之后：Treecko 真实记忆对话、PostgreSQL 存档与恢复、原 World 调度与手动控制、
扩展约束、持久化详细 Trace/统计与完整 V1 验收。
