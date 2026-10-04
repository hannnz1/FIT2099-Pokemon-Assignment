# V1 验收记录

## 最新 Phaser 版本（2026-10-01）

现有 RPG 素材已接入 Java 原地图与训练场，支持真实状态、键盘和目标选择。最新运行包 pokemon-phaser-game-runtime.zip；Java260/260、前端50/50。详见 [Phaser 接入说明](phaser-integration.md)。下方历史验收数字保留当时结果。


日期：2026-09-30。验收范围是用户指定的全 Java 游戏逻辑与默认 OpenAI `gpt-6-luna`。浏览器为本地中文操作页，Gemini 可选接口未做在线验收。

| 原 Spec | 结果与证据 |
| --- | --- |
| AC-01 基础委托 | 真实 Luna 从自然语言解析、玩家确认到函数调用，博士实际收到 3 个树果；`BerryQuestIntegrationTest` / `V1AcceptanceTest` |
| AC-02 不花金币 | 四项约束实网任务金币保持 5；无审批购买被拒绝，审批仅一次，`BerryApprovalTest` / `V1TaskConstraintTest` |
| AC-03 NPC Memory | Treecko 独立真实观察、带时间/来源的历史线索；世界资源不随叙述改变；`NpcDialogueServiceTest` / `NpcDialogueTest` |
| AC-04 Replanning | 离线完整流程实际资源不足触发重新规划；在线任务出现 4 次 OUT_OF_REACH 并恢复完成；`V1AcceptanceTest` / `AgentLoopTest` |
| AC-05 玩家审批 | 实际购买扣币、精确授权、拒绝/取消、安全重放拒绝；完整离线流程模拟玩家 DENY 后收集替代树果；`AgentRoomTest` / `BerryApprovalTest` / `V1RecoveryTest` |
| AC-06 服务器权威 | 交付只看实际库存和 Quest，不接受模型宣称；`BerryQuestTest` / `BerryQuestIntegrationTest` |
| AC-07 额度耗尽 | 按用户要求换为 OpenAI 默认，429/超时不推进世界、不虚构动作，手动仍可用；Gemini 错误适配也有离线测试；`V1RecoveryTest` / `OpenAiGatewayTest` / `GeminiGatewayTest` |
| AC-08 取消安全 | 迟到结果、旧任务、旧动作无效，合法已完成动作保留；`AgentRoomTest` / `AgentLoopTest` |

附加验收：区域检查所有中间移动格；截止前不能延迟交付；暂停接管后移出允许区域须返回才可恢复 AI；原地图/独立昼夜/实际 NPC 游荡；存档失败停止动作；审批重启不恢复令牌；统计和执行预算跨重启保留。

最终 Java 170/170，通过真实 localhost PostgreSQL 的完整套件；客户端 27/27，Java 8 生产编译通过。PostgreSQL 使用任务专用临时集群，没有修改现有数据库。实际 upsert 后注入提交前错误验证回滚，不能将其理解为模拟所有断电/提交响应丢失情况。

真实浏览器验收结果：Luna，COMPLETED，博士收到 3 个，金币 5，第 21/40 回合，26 个工具步骤，4 次无效调用，3 次重新规划，无购买审批。模型未使用固定脚本或强制路线。此前一轮因木守宫堵住研究所入口失败；限制其在户外游荡后，重新运行成功，并增加真实行为回归测试。

另外验证暂停→手动等待→服务重启→暂停恢复→继续；背包、世界回合和记忆保持。完成状态的最终包重启验证保留交付和统计。

`game.agent.demo.V1AcceptanceDemo` 明确是 OFFLINE 固定模型响应和模拟玩家拒绝，用真实引擎规则连续执行旧线索失败、商人询问、请求审批、拒绝、替代路线和交付，并检查存档往返。不是在线 AI 的替代或故障回退。

独立审查修复：存档失败后的取消不能保存未持久化世界；失败模型/非法工具不能推进回合；世界地形不得泄漏全局时间观察者；外部拒绝审批计入重新规划。均有回归测试。

标准 Maven 流程受本机 JDK ZIP 文件系统访问限制，尚未通过。V1 运行包经过 Java 8 编译、真实数据库、浏览器和离线验收程序验证；Gemini 实网、公开服务、多玩家并发和完整战斗 Agent 不在此次完成声明中。
2026-10-01 更新：Gemini 在线验收按玩家指令由本机 OpenAI 实网验收替代，该必验项已完成。最新可重复原地图四约束任务与训练场捕捉均真实通过；最新 Java185/185。详见 [OpenAI 实网验收](openai-live-acceptance.md)。

## 2026-10-01 单人玩法扩展完成

原地图野生战斗/捕捉、切换已召唤出战伙伴及原生水跃鱼收藏已完成，含受伤恢复与濒危救助。最新 Java259/259（含真实 PostgreSQL）、客户端46/46、Java8编译、打包浏览器及无模型整链验收通过。详情见 [完成说明与验收](gameplay-completion.md)。更早章节的剩余列表和测试数量是阶段历史，当前范围以此说明为准。
