# V4 批量评测基础 · 已启动

日期：2026-10-03。V3拓展已先完成验收并独立封包，见 v3-expansion-complete.md。本批开始V4，完成固定Seed/Reset与单场景批量评测基础，没有把完整V4平台标为完成。

## 本批实现

- SeededGrowthFixture每个案例创建独立原生Java成长世界，随机战斗流和个体ID生成独立固定。Seed与初始伙伴相同，初始个体、性格、遭遇及世界指纹一致；同一动作序列下结算一致。Reset即重建这个隔离fixture，不触碰玩家房间、收藏或实际存档。模型输出与实际选招仍可能随机；不能保证同Seed模型每次得出同一结果。
- 首个场景：连续冒险模式自然Lv.5初始伙伴→森林实际技能战斗达到至少Lv.6。所有模型行动走现有训练工具、移动、PP、HP、恢复点与经验规则，没有直接赋等级、传送或脚本替代模型。
- Java CLI按Seed顺序跑批次，每批1–10个不重复Seed，支持TREECKO/MUDKIP/TORCHIC。每案例最多100模型决策、单动作100步、连续失败8次、真实模型180秒墙钟、HTTP超时沿用本机配置；不同案例从相同版本环境重新开始。固定脚本baseline与live明确区分。
- 导出完整本次Trace、每次实际provider调用的序号/延迟/usage、初始/最终world与SHA256、终态、完成率、平均动作数、无效动作率、成功恢复次数与失败事件。
- 单次API usage由调用包装器记录一次，寻路的IN_PROGRESS续行动不重复加token。缺失或不完整usage则总token为null，同时保留可用的已观察用量。尚未返回的调用会显式标记，不能当完整用量。未配置价格时cost=null；尚无故障场景恢复率则null，不伪造0。
- CLI不保存密钥、Cookie、原始模型文本或完整请求正文。JSON保留可解释的游戏状态、任务目标、工具参数和公开Trace。

## 验收

Java494/494，包含20项真实PG；Maven成功（默认20项PG跳过，已另实测），前端保留V3的86项通过。新增7项验证Seed/Reset、完整Trace、每API计数一次、缺失usage、重复案例、失败模型无脚本回退。生产世界仍默认随机UUID，固定Seed仅用于独立评测fixture。最终解压运行包另通过两个脚本Seed案例，以及V3冒险、技能/PP、营地恢复和42张图片HTTP字节验收。

两个脚本baseline Seed11/22均完成，动作14/9；Reset重复后初始和最终世界指纹完全一致。实际OpenAI与baseline对应Seed初始指纹一致，本批本机实际模型gpt-6-luna两个案例均完成：
- Seed 11：COMPLETED，动作 14，世界回合 13，实际API调用 10，input 30079／output 240／total 30319 tokens，无效动作 0
- Seed 22：COMPLETED，动作 9，世界回合 9，实际API调用 7，input 21019／output 188／total 21207 tokens，无效动作 0

本批完成率100%、无效动作率0是这两个案例的实测值，不代表全部队伍或任务。usage来自实际API响应，共51526 tokens；成本未知，报告不含价格估计。此场景没有注入失败，恢复成功率仍未计算。

## 使用

先解压 pokemon-v4-foundation-agent-runtime.zip。正常游戏仍双击“启动游戏.cmd”；V4第一批暂无专用前端面板，评测通过命令行执行：

```powershell
# 不调用模型的规则基线
java -cp pokemon-phaser-game.jar game.agent.eval.GrowthBatchEvaluation scripted baseline.json 11,22 TREECKO

# 沿用本机OPENAI_API_KEY、OPENAI_MODEL；不要把密钥写入命令或报告
java -cp pokemon-phaser-game.jar game.agent.eval.GrowthBatchEvaluation live live.json 11,22 TREECKO
```

直接使用独立JAR时，把pokemon-phaser-game.jar换为pokemon-v4-foundation-agent.jar即可。输出路径按命令指定。报告出现COMPLETED_WITH_FAILURES意味着至少一项失败，CLI返回退出码1，保留其证据。

## V4后续未完成

1. 多任务场景：共享任务、多Agent信息传播、资源竞争和故障恢复评测集。
2. 多模型相同Seed/预算/规则/提示版本的自动公平比较，重复次数与统计置信度。
3. 批次队列、暂停/取消/断点恢复及固定RNG游标检查点；目前只支持从新fixture Reset，不能声称重启续跑时随机流复现。
4. 评测HTTP接口、前端仪表盘、批次管理与回放视图。
5. 显式配置价格与价格版本，支持缓存/推理用量等计费细分，汇总成本与故障恢复率。

V5对战Agent没有启动。本批V4单场景基础可运行，完整V4仍在开发计划中。
