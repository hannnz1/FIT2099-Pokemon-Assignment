# 宝可梦游戏全功能 Benchmark v1

测试日期：2026-10-06。用途：版本回归、定位失败、为项目介绍提供可追溯数据。范围是目前已经实现的功能，不包含未来开发计划。

本报告记录免费回归批次；后续 DeepSeek 实网结果已完成并单独归档，见 [真实模型补充报告](../deepseek-benchmark-20261006-513b6a5/README.md)。下文“未执行”仅指免费批次当时状态。

## 本轮结论

免费自动化基准已执行；**尚不能称为所有功能全部验收通过**。真实 DeepSeek、21 项 PostgreSQL 集成、两条未完成的浏览器脚本及真人/真机/生产耐久验收仍有缺口。

| 测试层 | 本轮结果 | 证据 |
|---|---|---|
| Java 规则、服务、持久化与权限 | 646 项：625 通过，21 跳过，0 失败/错误 | java.log、java-cases.csv |
| 前端逻辑 | 220/220 通过 | frontend.log |
| 运维监控故障注入 | 5/5 通过 | operations.log |
| 三只初始伙伴的新手冒险 | 3/3 完成，并以原伙伴赢得见习训练师对战 | benchmarks/animation/newcomer-browser.json、newcomer.log |
| 桌面/390px 布局与操作入口 | 16/16 检查通过，页面异常 0 | player-flow/acceptance.json |
| 六类任务规则基线 | 18/18 完成，Seed 11、23、37 | six-scenarios.json |
| 对战策略基线 | 60/60 正常结束；规则策略胜 28/30，第一合法技能策略胜 25/30 | duel-baseline/summary.json、cases.jsonl |
| 短时动画性能 | 帧间隔 P50 16.7ms，P95 16.8ms；命令回执 P95 20ms；3 次长任务 | animation-performance.json |
| 移动回执、地图缩放专项浏览器脚本 | 未完成，不计通过 | movement-receipt.log、movement-receipt-isolated.log、stable-map.log |
| 本轮付费模型调用 | 0 | 测试服 LLM_PROVIDER=disabled；仅 baseline 策略 |

以上测试层的分母不同，不合并为“全功能通过率”。Java 自动化通过也不代表每项功能已经经过真实浏览器和真实模型的端到端验收。

## 版本与运行条件

- 生产修复版本：513b6a5；测试补充版本：db5cb3e。
- 实际 JAR SHA-256：`ebebb5c1a14d9d6be8768ad7fad4e6b3c516155c4bd4fc95c3c8fdde9c72d125`，与上次云端发布包相同。
- 源码：`C:/Users/Administrator/Desktop/project/pokemon-game/project-main`。
- Windows；Microsoft JDK 21.0.11；本机 Chromium headless。不是实际手机或腾讯云容量测试。
- 浏览器测试使用独立 localhost 服务、384MB 堆、内存存储。Java 文件存储测试使用临时目录。没有修改线上玩家存档。
- 不采用累积 JaCoCo 文件推算本轮覆盖率。用例数量、功能覆盖范围、代码覆盖率是不同指标。

## 功能覆盖清单

下表是全功能验收范围，所列测试类可在 `java-cases.csv` 中查到具体方法和本轮结果。覆盖表示存在相关自动化用例，不表示穷尽该功能的所有组合。

| 编号 | 模块与需要验证的行为 | 已有核心用例/证据 | 当前覆盖与缺口 |
|---|---|---|---|
| F01 | 新玩家围绕同一个体完成移动、战斗、恢复、捕捉、成长、见习对战 | newcomer-browser.json；GrowthRoomTest、DuelRoomTest | 三伙伴自动流程通过；真人观察待做 |
| F02 | 森林/河岸/山道/研究室导航、出口、碰撞、跨图状态 | NavigationTest、GrowthRegionRoomTest、GrowthContinuityTest | 规则通过；本轮未重新截图巡检所有地图 |
| F03 | 移动不扣 HP、营地恢复 HP/PP/状态、倒下返程 | ExplorationHealthTest、GrowthReturnTest、前端 recovery 用例 | 自动化通过；独立真实浏览器全组合待补 |
| F04 | 战斗命中、伤害、状态、技能 PP、捕捉与野生刷新 | CombatTrainingTest、GrowthExpansionRoomTest、DuelBattleTest | 规则和三伙伴流程通过 |
| F05 | 升级、自动进化、物种名称和美术、个体成长属性 | GrowthRoomTest、FieldGrowthTest、GrowthExpansionAssetsTest | 自动化通过；素材主观观感需人工 |
| F06 | 收藏、背包、携带、召唤、跟随、回收与跨模式迁移 | PokemonCarry/Follow/SummonTest、GrowthCarryTest、LegacyGrowthMigrationTest | 免费自动化通过；相关 PostgreSQL 用例跳过 |
| F07 | 自然语言委托、确认、限制条件、采集、交付、暂停与取消 | NaturalTaskParserTest、BerryQuestIntegrationTest、AgentRoomTest | 模拟提供方/规则通过；真实语言多样性待实测 |
| F08 | 参数校验、错误分类、受限重规划、恢复次数用尽 | BerryLiteralTest、SoloBerryRecoveryFlowTest、AgentPausePersistenceTest、前端 failure-recovery | 最新修复离线回归通过；真实模型复测待做 |
| F09 | 玩家与 Agent 协作、背包与剩余数量、竞争资源、贡献账本 | CooperativeQuestTest、DeliveryPriorityTest、BerryCoordinationAdviceTest | shared/competition 各 3/3 基线完成 |
| F10 | 培养委托、序列/混合委托、个体绑定、跨场景执行 | FieldAgentTest、OwnedAgentTest、SequenceAgentTest、MixedAgentTest | 规则回归通过；森林培养 3/3 基线完成 |
| F11 | NPC 信息转发、来源记录、记忆、认知与多角色生命周期 | NpcAgentRuntimeTest、MemoryToolsTest、V3CognitionTest | information 3/3 基线完成；不是模型认知能力证明 |
| F12 | 训练师对战、合法动作、换人、结算、回放 | DuelBattle/Room/Http/EvaluationTest；60 局对战 | 规则测试通过；真实对战 Agent 待另测 |
| F13 | 评测创建、暂停/继续/取消、归档删除、重载、导出与费用保留 | EvaluationPlatform/History/HttpTest、PublicEvaluationTest | 回归通过；六场景 18 局完成 |
| F14 | 存档持久化、旧存档迁移、Lv.11 大存档、写失败与恢复 | GrowthLevel11StorageTest、FileWorldStoreTest、AgentPausePersistenceTest | 文件模式通过；真实 PostgreSQL 21 项未执行 |
| F15 | 会话隔离、CSRF、重复请求、跨设备身份、额度/限流/并发 | AgentWebServerTest、PlayerIdentityHttpTest、PublicAiBudgetTest | 自动化通过；不等于渗透测试或公开容量保证 |
| F16 | 输入、弹窗、布局、提示、缩放、弱网与回执重试 | 前端 220 项；player-flow 16 项；movement-receipt/stable-map | 主流程通过；两项浏览器专项没有完成记录 |
| F17 | 健康检查、预算告警、备份异常、运行监控 | WebDeploymentTest；operations.log | 监控注入 5/5；本轮未做生产恢复演练及长时压测 |

## 六类任务的统一 Benchmark

当前基线矩阵：6 场景 × 3 Seed × 1 次 × baseline = 18 局；初始伙伴 TREECKO；每局最多 100 次决策、180 秒。每局保存场景、Seed、状态、动作、Trace、故障、统计与版本。

| 场景 | 成功标准 | 本轮结果 |
|---|---|---|
| forest | 同一伙伴在森林通过原生移动和战斗从 Lv.5 培养到至少 Lv.6 | 3/3 |
| shared | 玩家与 Agent 共享目标，正确结合已交付与各自库存完成交付 | 3/3 |
| competition | 资源被其他角色取走后仍能完成目标，不重复消耗资源 | 3/3 |
| information | 沿 TORCHIC→PROFESSOR→MUDKIP 传递有来源的观察 | 3/3 |
| fault | 注入一次工具拒绝后恢复并完成培养 | 3/3；故障恢复 3/3 |
| battle | 在固定对手条件下完成训练师对战；胜负单独统计 | 3/3，胜 3/3 |

合计动作拒绝率约 1.42%，对应 fault 的 3 次主动故障注入；其他场景拒绝动作 0。不能把这 3 次注入写成系统自然失败，也不能把它们从原始报告删除。

对战另有固定 Seed 1–30 的 60 局对比：规则策略 93.3% 胜率，95% Wilson 区间 78.7%–98.2%；第一合法技能策略 83.3%，区间 66.4%–92.7%。区间重叠，不宣称显著优于另一策略或真实模型。

## 真模型扩展规则（未执行）

1. DeepSeek 与 baseline 必须使用相同源码版本、场景、Seed、伙伴、决策上限和墙钟上限；记录模型标识、执行策略和日期。Seed 固定游戏环境，不固定模型输出。
2. 首次可跑 18 局探索矩阵；稳定性评估建议扩大到至少 10 Seed、每 Seed 3 次重复，共 180 局/模型。扩样需独立费用授权，不自动执行。
3. 单独统计端到端完成率、失败分类、有效动作率、任务/调用延迟 P50/P95、Token、实际费用、故障恢复率与 Wilson 区间。中位数/P95 必须写明样本量和是否包含限流等待。
4. 报告必须同时给出计划数、实际尝试数、完成数、失败数、预算阻止数、取消数。预算中止不能当作成功或自然模型失败；不能删除困难样本后重算漂亮的成功率。
5. 引擎确定性补救动作和模型选择的动作分开标记。Token 不完整或单价未冻结时显示未知，不填 0；费用以提供商账单为准。
6. 预算、限流和超时达到上限即停止并保留证据；本轮没有新的付费调用授权。

## 验收门槛

- P0：已执行规则/持久化/权限回归不得有失败；任务取消、错误恢复不得造成重复扣费、重复交付、跨用户访问或存档丢失。
- P1：三伙伴新手自动流程 3/3；每个重要状态都有可继续的操作；六场景免费基线 18/18；注入故障必须被记录并恢复。
- P1：规定视口 1366×768、875×640、390×844、360×640 下，地图不能因提示出现而跳动或缩放；本轮仅部分布局验收完成，不能认定此条通过。
- 性能建议门槛：同一机器与浏览器下移动场景帧间隔 P95 ≤33.4ms、本地操作回执 P95 ≤200ms、页面未捕获异常 0。本轮短测达到延迟门槛；不替代真机和耐久。
- 真实 AI 建议以每类 ≥90% 完成率作为改进目标，同时列置信区间；18 局探索样本不足以证明长期稳定性。
- PostgreSQL、真实手机、真人引导、长时运行、异地备份和外部告警分别签收，不用离线总分替代。

## 本轮未完成与失败记录

1. 21 项跳过均为未配置独立 PostgreSQL 的集成测试，完整列表见 skipped-cases.json。
2. movement-receipt 首轮等待初始伙伴按钮超时；隔离重跑后没有完成，已终止。stable-map 没有完整新报告。空结果数组不表示通过。
3. 连续创建测试浏览器会累积服务会话，源码限制 8 个；测试准备阶段确认了该限制。但首轮未保留完整网络状态，不能仅据此认定两个脚本的唯一根因。
4. 新手引导脚本通过的是自动化路线，不是观察真实新玩家的可用性研究。
5. 未执行真实 DeepSeek、本轮长时压测、生产存档恢复、每日异地复制和外部告警送达。

## 如何复跑

免费回归：在源码目录运行 Maven `verify`；在 `client` 目录运行 `node --test test/unit/*.test.mjs`；在源码目录运行 Python `tools/test_operations_monitor.py`。三者退出码必须为 0，并检查跳过项。

本机 Maven：`C:/Users/Administrator/Documents/Codex/2026-09-29/docs-specs-pokemon-agent-demo-v1/work/apache-maven-3.9.9/bin/mvn.cmd`。JDK：`C:/Program Files/Microsoft/jdk-21.0.11.10-hotspot`。

本地 UI 测试必须使用最新 JAR、`AGENT_STORAGE=memory`、`LLM_PROVIDER=disabled`、`PLAYER_IDENTITY_ENABLED=false`，并省略 `PUBLIC_ORIGIN`。每组独立启动服务，使用新浏览器上下文，不复用生产 Cookie。不要并行运行性能与其他浏览器负载。

| 现有脚本 | 本机参数/条件 |
|---|---|
| client/test/browser/newcomer-acceptance.cjs | 固定端口 8104；输出前创建 benchmarks/animation |
| client/test/browser/player-flow.cjs | UI_BASE=http://127.0.0.1:8104，UI_OUT=独立结果目录 |
| client/test/browser/animation-performance.cjs | TEST_SITE=http://127.0.0.1:8104/growth/，BENCH_OUT=独立 JSON 路径 |
| client/test/browser/movement-receipt.cjs、stable-map.cjs | UI_BASE、UI_PHASE；目前应设置进程超时，未完成即记 UNVERIFIED |
| tools/V5BaselineBenchmark.java | 使用最新 JAR 编译运行，主类 game.agent.eval.V5BaselineBenchmark，参数为输出目录 |

六场景可在本地评测中心选择 baseline、六场景、Seed `11,23,37`、TREECKO、重复 1 次、100 次决策、180 秒并导出 JSON。每批最多 24 案例，180 局扩样必须分批并保存历史。

现有脚本包含机器专用路径，当前材料是可复跑基准与完整证据集，并非跨机器的一键 CI 工具。后续可在此基础上统一隔离启动、超时和汇总；不要为通过测试而降低功能断言。

## 证据文件

- summary.json：本轮机器可读摘要。
- java-cases.csv：646 个 Java 用例的名称、状态和时间。
- six-scenarios.json：18 个任务的完整导出。
- duel-baseline/cases.jsonl：60 局逐局记录。
- benchmarks/animation/newcomer-browser.json：三伙伴完整路线。
- player-flow/acceptance.json：16 项浏览器断言。
- animation-performance.json：原始性能采样。
- 各 .log：本轮命令输出；包括失败记录，未覆盖历史结果。

本轮只新增测试证据和说明，没有修改或重新部署游戏逻辑。


## 追加：本轮 DeepSeek 实测

2026-10-06 经批准追加最多 30 万 Token，实测结束：9 例完成、1 例响应格式失败、8 例预算不足未完成；实际 286,997 Token。六类场景各有成功样本，同条件基线 18/18 完成。详情和证据见 [DeepSeek 实测报告](../deepseek-benchmark-20261006-513b6a5/README.md)。上文“未执行真实 DeepSeek”描述的是先前免费测试阶段，此追加记录不改变其余未验收项。


公开仓库保留本批次汇总、案例 CSV 和非空日志；浏览器截图与完整配套文件见已有 `benchmarks/animation`、`benchmarks/ui-refinement`，其余机器相关原始归档保留在本地。
