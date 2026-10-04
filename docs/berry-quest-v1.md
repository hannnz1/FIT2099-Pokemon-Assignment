# 树果任务与购买审批：Java 核心流程

本轮实现 V1 的任务动作与审批核心，新增内容使用 Java 8，无新增生产依赖。
这是一个真实引擎对象上的单任务内存场景；没有 Gemini、HTTP/WS、数据库或正式 UI。
原游戏入口、糖果交易、原 World 的调度方式保持原样。

## 运行

标准构建环境执行 `mvn clean verify`，然后：

```sh
java -cp target/classes game.agent.demo.BerryQuestDemo
```

交付的独立 JAR 可直接运行：

```sh
java -jar pokemon-berry-quest-demo.jar
```

两个场景都会先尝试拾取 3 个树果，但果园实际上只有 2 个。失败不会产生部分拾取；
下一轮拾取 2 个，再到商人旁请求购买最后 1 个。可信演示程序模拟玩家的选择：

- 批准：支付 1 金币，获得真实 Berry，前往博士旁交付 3 个，余额从 5 变为 4。
- 拒绝：不支付金币，前往另一地点拾取真实 Berry，交付 3 个，余额保持 5。

决策是明确标记的脚本，使用 Context 的观察和执行结果；批准由玩家控制接口执行，
模型不能调用批准接口。示例通过 AgentMudkip.playTurn 返回的 Action 驱动移动和工具，
不执行原 World.run 的其他 NPC 回合及 map.tick，以保证测试确定性。

## 规则与接入

- `Berry` 是原引擎 Item 的真实可携带物品，每个对象代表 1 个树果。
  `itemId=BERRY` 是服务器限定的物品种类 ID，不是玩家或模型提供的任意对象引用。
- `BerryQuestSession` 绑定一个 owner、AgentTask、角色、地图、博士与商人。
  Rules 指定所需树果数量、截止游戏回合、背包容量和批准有效期。
  金币、库存和价格属于这个隔离演示会话；尚未替代原糖果经济或共享房间经济。
- pickup 只从角色当前格拾取，会重新检查物品、数量、可携带性和容量。
  全部校验通过才执行原引擎 PickUpItemAction；资源不足不会部分拾取。
- purchase_item 重新检查任务、截止时间、存活、邻接距离、库存、容量、余额和批准。
  成功后才扣钱、减库存、增加物品、消耗令牌。
- deliver 检查绑定的 questId 和 professor ID、邻接距离、实际背包及截止时间。
  成功后把所需数量的 Berry 转移到博士背包，完成 Quest 和 AgentTask。
  模型文本、背包里的 2 个树果或重复交付都不能完成任务。
- 工具 registry 缓存相同 actionId 的结果，重复购买/拾取/交付不重复产生副作用。
  Session 的直接方法用于可信服务器规则；外部重试必须通过 registry。

注册工具：`pickup`、`purchase_item`、`deliver`、`request_player_approval`、
`observe_quest`。可同时注册原 GameTools 提供移动、观察和背包工具。
所有参数必填；未知工具、额外参数、非法数量或伪造 actorId 均被拒绝。

## 玩家审批

模型通过 request_player_approval 提出 `PURCHASE_BERRY` 的数量；金额和展示说明由服务器
根据实际价格计算，不信任模型的 reason。请求批准不会扣钱或增加物品。

可信玩家接口：

```text
session.pendingApproval(authenticatedOwner)
session.resolveApproval(authenticatedOwner, proposalId, allow)
```

未来 HTTP 接入必须从认证会话获取 authenticatedOwner，不能直接信任请求体中的 ownerId。
批准令牌绑定当前会话、任务生命周期、数量和总价，有独立毫秒有效期，只能成功使用一次。
另一会话、改数量/金额、暂停恢复、取消、到期和重放都不能复用旧授权。
拒绝后进入 REPLANNING；观察包含 DENIED/APPROVED/EXPIRED 等审批结果供下一次决策读取。
令牌只提供给本任务的可信决策上下文，不应写入 NPC 记忆或公开日志。

本阶段所有关键购买都要求批准，包括未设置 NO_SPENDING 的会话。
NO_SPENDING 只允许对具体获批交易作例外，不会永久解除禁止花钱约束。
自然语言到约束的转换尚未实现；本例由可信配置启用 noSpending。

## 时间与执行边界

所有世界操作、审批、任务状态切换和观察都使用同一个世界线程。
角色是否邻接通过真实 GameMap 出口判断，不接受模型声明距离。

`session.advanceTurn()` 为所属世界调度器提供批准冻结接口：等待批准时返回 false，
不推进会话的 Quest 回合；批准 TTL 仍使用独立单调毫秒时钟，避免无限等待。
生产时可提供 `System.nanoTime()/1000000L`。暂停不冻结任务截止时间，取消不回滚已完成动作。

未来房间调度器必须在这个接口返回 false 时同时停止相关地图/时间推进。
当前原 World 未接入这个接口，不能据此宣称已经实现整个多人房间的审批冻结。
原昼夜伤害、其他 NPC 行动等也尚未在这个确定性示例中进行联调。

会话、批准、余额、库存和幂等缓存都在内存；重启必须创建新会话。
MemoryStore 只保存 NPC 事件记忆，不是完整游戏存档。本阶段不实现奖励经济、
自然语言 NPC 对话、数据库事务或浏览器端审批窗口。

## 验证

2026-09-30：全部 Java 测试 72/72，现有客户端测试 18/18。
生产代码通过 javac --release 8。测试使用 ECJ 3.40.0 和 JUnit Console 1.10.3；
标准 Maven 流程仍受本机 JDK 读取测试依赖的权限问题影响，未宣称通过。
完整测试包含资源不足、容量、不可携带物品、截止时间、角色缺失/死亡、
任务暂停/取消、交付数量/距离/身份、重复调用、批准归属/金额/数量/有效期、
价格/库存变化、拒绝与替代路线，以及审批内工具提交与 AgentLoop 恢复。

本轮代码审查未发现高/中优先级问题。
