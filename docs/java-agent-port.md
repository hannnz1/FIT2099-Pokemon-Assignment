# Java Agent 模块移植

本次把 Mindcraft、AI Town、Generative Agents 中选定的工具注册、执行控制、持续决策、寻路、感知、记忆查询和保存机制改写为 Java，并接入原引擎的回合接口。保持 Java 8 兼容，没有增加生产依赖，也没有更改原控制台游戏入口或 Phaser 客户端。

## 运行

标准构建环境：

```sh
mvn clean verify
java -cp target/classes game.agent.demo.AgentPortDemo
```

示例明确使用脚本决策，不调用 LLM。它在真实 GameMap 中创建 AgentMudkip、Treecko、墙与 Berry，记录并保存/重新加载 Treecko 的经历，移除物品，再通过 `playTurn()` 返回的引擎 Action 驱动 Mudkip 绕墙到达目标。移动只需要一次决策；实际目标位置满足规则后完成任务。最后验证当前位置没有树果，且取消后的异步结果被拒绝。它没有领取或完成树果交付 Quest。

预期末行：

```text
PASS: engine turns, bounded automatic loop, navigation, memory save/load, isolation, stale observation and cancellation
```

## 模块与接入方式

- `game.agent.tools`：结构化请求、严格参数 schema、工具白名单、执行策略、结果复用。每个角色/会话创建一个 registry；不跨玩家共享。所有参数均必填，额外参数拒绝，整数不接受截断或字符串强转。策略函数返回拒绝 reasonCode，返回 null 表示允许。
- `game.agent.action.ActionResult`：不可变结果，区分成功、进行中、拒绝、中断、超时、失败。工具成功不代表 Quest 完成。
- `game.agent.runtime.AgentTask`：操作 ID、generation、deadline、暂停/恢复/取消/审批状态。取消使未提交的操作失效，已完成动作不回滚。`markCompleted()` 只能由权威任务规则调用。
- `game.agent.runtime.AgentRunner`：通过模型 executor 执行决策 supplier，把结果交回世界 executor；不在模型线程直接改变地图。输入 supplier 必须只读取调用前构造的不可变观察快照。
- `game.agent.runtime.AgentLoop`：完整的有限持续循环。模型收到不可变目标、观察、工具定义和上次结果；等待模型期间 `tick()` 立即返回。每回合最多执行一次工具，IN_PROGRESS 自动保留动作继续下一回合，不逐格请求模型。配置决策次数、长动作步数、连续失败上限，保留最多 64 条结果；暂停/恢复/取消会清除旧动作，超时或模型不可用需要显式恢复。
- `game.agent.runtime.AgentTurnAction / AgentMudkip`：通过原 `playTurn()` 接口运行循环。AgentMudkip 只使用 Agent 行为选择，保留水地形武器、物种能力和原昼夜规则；同一个回合 Action 重复执行不会重复移动。其他物种可在自己的 `playTurn()` 返回 AgentTurnAction。
- `game.agent.navigation.NavigationService`：按照地图实际出口搜索完整最短路径，每次 `step()` 最多移动一个出口，重新检查占用与区域限制。不把靠近目标当成到达。
- `game.agent.memory`：按 worldId/npcId 隔离，事件不可变，来源明确，按发生回合查询。`perceive()` 过滤区域、半径、时间、观察者和来源，去重后按距离限制注意数量。`record()` 是可信引擎接口，不暴露给模型。
- `MemoryStore`：版本化事件记忆文件，保存所有信息来源、位置与时间；限制文件大小/事件数量，拒绝损坏、重复事件、未知版本和尾随数据。原子替换防止失败覆盖旧文件；不支持原子替换的文件系统会报告失败。不是完整游戏存档。
- `MemoryTools`：`recall_memory(subject, limit)` 只查询绑定角色截至当前游戏回合的历史记忆，输出来源与发生时间；模型不能选择其他 NPC 或写入伪造记忆。

`GameTools.register()` 已提供真实引擎适配：`observe`（当前格）、`inspect_inventory`（自己的背包）、`move_to`（向目标走一个游戏回合）。其他游戏操作可按同一注册接口增加。

一个简单的接入顺序：

```text
世界线程：生成观察快照、创建 operation
    → 模型线程：返回结构化 ToolRequest
    → 世界线程：核对 operation、任务状态与 deadline
    → registry：校验 schema、执行策略与 actionId
    → 引擎：执行动作，得到 ActionResult
```

### 必须保留的执行约束

1. 所有世界修改（玩家、NPC、Agent）使用同一个世界 executor。任务锁不能让原本非线程安全的 GameMap 自动变为线程安全。
2. 同一动作重试必须使用相同 actionId 与载荷；新动作使用新 ID，ID 应由服务器分配。结果复用也适用于查询，所以每次新的 observe 使用新 ID。
3. IN_PROGRESS 移动由世界调度器保留目的地、每回合继续执行，不要求每格调用模型。每步采用新 actionId。外层决定何时 tick 世界、消耗回合、处理中断及 deadline。不要在正在等 LLM 的线程推进时间。
4. 定时调用 `task.expire(now)` 处理永不返回的 provider，并配置 provider 自身的 HTTP 超时。此模块不创建后台线程；未结束的 provider 工作不会因状态失效而自动终止，但迟到结果不能提交。
5. AgentLoop 构造参数中的 `decisionLimit / stepLimit / failureLimit` 控制自动执行上限。可用 `System.nanoTime()/1000000L` 提供单调毫秒时钟；不要把游戏回合编号当生产环境的毫秒超时。观察与完成判定回调只在世界线程运行；DecisionProvider 只能读 Context，不捕获地图等可变对象。生产使用独立、有容量限制的模型 executor。
6. 审批状态不是支付授权。购买工具仍需单独检查 Approval Token、约束、库存、金额、距离与权限。
7. registry 缓存上限为每会话 10000 个已验证请求，满后拒绝新执行，不驱逐旧凭据而允许重复动作。AgentLoop 的动作 ID 由调度器生成，不采用模型提供的 ID。记忆可以通过 MemoryStore 保存/恢复；缓存、活动任务和游戏状态仍不持久化，重启必须建立新会话，不可重放旧动作。
8. handler 必须先验证再修改；若 handler 抛异常，返回 TOOL_ERROR 并缓存结果，避免盲目重试已发生的副作用。框架不提供数据库回滚。
9. perception 的区域/坐标与候选事件由可信游戏代码提供。内置过滤不是障碍物视线算法；需要视线遮挡时由引擎先过滤。

## 与完整 V1 的边界

已完成本次选定开源模块的 Java 移植、持续执行、回合接入和记忆存取。后续开发增加了真实树果拾取/购买/交付、精确审批、Gemini/OpenAI 接口和本机浏览器页面，见 [浏览器阶段](agent-browser-v1.md) 和 [当前进度](development-progress.md)。数据库游戏存档、NPC 自然交流、完整世界联调和评测仍待完成。原 `game.Application` 不会自动启用 AI；演示入口已经启用 AgentMudkip。不要同时使用 AgentRunner 和 AgentLoop 驱动同一个任务，也不要同时让旧行为和 Agent 控制同一角色。原昼夜伤害、其他宝可梦攻击等规则仍会生效，演示只驱动 Agent 回合以保持确定性。

接入新场景时：先把 AgentMudkip 放到已经加入 World 的 GameMap；启动 AgentTask；创建角色专属 registry 并注册 GameTools/MemoryTools；构造 AgentLoop，提供可信观察、目标完成判定和 DecisionProvider；调用 `mudkip.attach(loop, map, clock)`。之后原 World 的回合调用会驱动循环。暂停/恢复/取消使用 loop 对应方法，UI/网络请求须排队到世界线程执行。

## 移植来源

详见 `third_party/AGENT-PORT-NOTICE.md` 与 `third_party/agent-port-sources.json`。保留三个项目完整许可证。选定源文件被固定到 commit，运行不依赖 Node/Python/Convex。

## 本机验证记录（2026-09-30）

- `javac --release 8` 编译全部生产代码通过。
- Windows 本会话的 Maven/JDK ZIP 文件系统读取测试依赖时发生 AccessDeniedException，标准 Maven 全流程未验证通过。
- 使用 Eclipse ECJ 3.40.0 按 Java 8 目标编译全部源码与测试，JUnit Platform Console 1.10.3 运行整个 Java 测试目录。
- 示例在真实 Java 引擎对象上执行；没有模型调用或模拟 AI 成功的声明。
- 后续树果任务/审批开发完成后，全部 Java 测试 72/72 通过（原 51 个加本轮 21 个）；现有客户端测试 18/18 通过。
- Java 8 API 编译通过；可执行 JAR 验证了引擎回合、有限循环、寻路、记忆存取/隔离、历史观察失效和取消后的迟到结果。
- 精确测试结果见本次交付中的 verification.txt。
