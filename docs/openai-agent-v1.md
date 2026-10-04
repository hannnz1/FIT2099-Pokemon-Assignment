最新本机 OpenAI 开发与实网验收见 [OpenAI 实网验收](openai-live-acceptance.md)。以下是早期接入阶段记录，约束数量与未完成模块描述属于历史状态。

# OpenAI Java 接入与真实任务验证

2026-09-30。新增 OpenAI Responses API 决策接口，默认 `gpt-6-luna`，
复用原有 Java 树果规则、任务生命周期、工具验证与玩家审批。兼容 Java 8，无新增生产依赖。
Gemini 入口继续保留，两个提供方使用同一个任务解析规则和执行入口。

## 此前 gpt-6.1-sol 的真实调用

按玩家要求复用本机已配置的 `OPENAI_API_KEY`，没有新建、复制或输出密钥。

- 官方 `https://api.openai.com/v1/responses`：HTTP 200，模型 `gpt-6.1-sol`，
  返回状态 completed，准确回复 `OPENAI_API_OK`。该探测使用 14 个输入 Token、8 个输出 Token。
- Java 真实任务：输入“帮我完成博士的树果任务，不要花金币，也不要主动战斗”，
  模型结构化为 `NO_SPENDING` 和 `NO_ACTIVE_BATTLE`，玩家确认后启动。
- 模型返回函数调用，Java 按真实地图出口移动，先拾取果园的 2 个 Berry，再拾取另一地点的 1 个，
  最后向博士实际交付 3 个。引擎返回 `QUEST_COMPLETED`，余额保持 5，进程退出码为 0。
- 以上联调使用真实 OpenAI 响应，没有脚本或随机动作替代模型。

本机 .NET 网络入口最初在 TLS 建连阶段失败；Node 探测和 Java 游戏入口均成功，
没有关闭证书校验。Windows 本机日志捕获出现中文编码乱码，英文动作码与退出状态可正常读取。
这个编码问题不影响本次任务解析与引擎交付，但终端显示仍需按运行环境调整为 UTF-8。

## 运行

本机已有可用密钥，无需为了这个入口再注册 Gemini Key。
其他机器应把密钥保存在服务器环境变量 `OPENAI_API_KEY`；不写入源码、命令行参数或前端。

```sh
java -jar pokemon-openai-agent.jar
```

输入自然语言任务，确认解析结果时输入 `y`。涉及购买时，由玩家选择允许、拒绝或取消。
没有购买审批令牌不能购买；自然语言“已完成”不能完成任务。

| 环境变量 | 默认值 | 含义 |
| --- | --- | --- |
| OPENAI_API_KEY | 必填 | 服务端凭据 |
| OPENAI_MODEL | gpt-6-luna | 实际请求模型；设置此变量可覆盖默认值 |
| OPENAI_TIMEOUT_MS | 15000 | 单请求超时，1–60000 毫秒 |

当前 HTTP 传输只请求 OpenAI 官方固定地址，不读取 `OPENAI_BASE_URL` 或使用任意第三方代理。
此前使用 gpt-6.1-sol 的联调记录见上方。当前默认已改为低价 gpt-6-luna，
其他机器的模型权限与额度以实际接口结果为准。不配置自动回退到昂贵模型。

根据 2026-09-30 的[官方 Standard 价格](https://developers.openai.com/api/docs/pricing)，
短上下文每百万 Token：Luna 输入 $0.10、输出 $0.50；Sol 6.1 输入 $2.00、输出 $10.00。
相同 Token 数下输入/输出单价各降低 95%；实际任务费用仍取决于调用次数与 Token 用量。
Luna 支持[工具调用与结构化输出](https://developers.openai.com/api/docs/models/gpt-6-luna)，
现有 Responses 协议、low reasoning 配置、任务约束和玩家审批规则继续沿用。

本次改为 Luna 后，已重新运行真实 Java 树果任务：不设置 `OPENAI_MODEL` 覆盖，
入口显示 `OpenAI (gpt-6-luna)`，正确解析两个约束、实际交付 3 个 Berry，金币保持 5，
返回 `QUEST_COMPLETED`，进程退出码为 0。全部 Java 测试 111/111、Java 8 编译通过。
本次捕获显式指定 Java 标准输出为 UTF-8，中文日志正常。

标准 Maven 环境可编译后运行 `game.agent.demo.OpenAiAgentConsole`。
本机验证仍使用独立编译/测试流程，未宣称 Maven 完整构建通过。

## 接口与规则

- `OpenAiGateway` 实现 `LlmGateway` 和 `TaskInterpreter`。
  Responses 请求使用 `store=false`；工具为 strict JSON schema，
  `tool_choice=required`、`parallel_tool_calls=false`，每次只决定下一步。
- 返回内容再次经过单调用、完成状态、白名单、参数类型、范围与未知字段校验。
  不接受 Shell、额外工具或普通文字模拟动作。模型调用 ID 不作为执行幂等键。
- 自然任务解析与 Gemini 共用 `TaskIntentCodec`，只接受当前可信 Quest ID 和两个已实现约束。
  自定义区域/时间/其他目标会拒绝，玩家必须确认解析结果后才启动。
- `QuestAgentConsole` 共用实际执行与购买审批流程。
  模型在后台请求，世界修改仅在执行线程提交，并重新检查任务状态和当前规则。
- `OpenAiHttpTransport` 固定官方 HTTPS、禁用重定向、保留证书校验，
  限制请求/响应大小，验证 UTF-8，设置连接/读取及整体请求截止时间。
- 凭据仅放 Authorization 请求头。错误正文不进入日志或任务轨迹。
  429 的 `insufficient_quota` 转为 `PROVIDER_QUOTA_EXHAUSTED`；普通限流为 `PROVIDER_RATE_LIMIT`。
- 取消会立刻使迟到结果失效；已经发往提供方的请求不保证立即终止，可能继续消耗额度。

工具、金币、背包、商店库存与 Quest 完成仍由既有游戏规则决定。
本阶段场景仍是隔离内存演示，没有替代原游戏糖果经济或实现多人共享经济。

## 验证与交付范围

- 全部 Java 测试 **111/111**：本轮新增 15 项，覆盖 Responses 格式、拒绝非法调用、
  结构化约束、错误脱敏、额度区别、固定 HTTPS、请求截止时间、玩家确认、实际游戏完成与失败停机。
- 现有客户端回归 **18/18**；生产代码通过 `javac --release 8`。
- 离线测试仅在 HTTP 边界注入明确标记的假响应，真实游戏规则不替换。
- 只读代码审查未发现可确认的高/中优先级问题。

本轮先完成可用的 OpenAI Java 接入，以满足“先调用本地 OpenAI API”的要求。
后续已增加本机 Java HTTP 房间和中文任务/审批页面，见 [浏览器阶段](agent-browser-v1.md)。
数据库、NPC 自然对话、原世界联调和完整 V1 验收仍待接通；当前进度见 [开发进度](development-progress.md)。
现有“回声群岛”浏览器客户端没有因为本轮 API 验证而自动连接到此树果场景。

参考：[OpenAI 官方 Function calling](https://developers.openai.com/api/docs/guides/function-calling)、
[Gemini 接入](gemini-agent-v1.md)、[树果任务和审批](berry-quest-v1.md)。
