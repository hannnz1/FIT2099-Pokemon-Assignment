# 战斗与捕捉训练场

2026-09-30：下一阶段的独立 Java 控制台训练场已实现并验证。V1 浏览器仍是和平树果委托；训练场尚未接入浏览器按钮或自然语言捕捉任务解析。这是完成的可运行训练场，不是整个 V2 已完成。

解压训练场运行包，保留 JAR 与 lib 的相对位置，Java 8 或更高版本运行：

```powershell
java -jar pokemon-battle-training.jar --new
```

手动命令：`status`、`move N/S/E/W`、`attack wild-treecko`、`capture wild-treecko`、`attack wild-torchic`、`capture wild-torchic`、`quit`。先 `move E` 到木守宫旁边，可攻击或捕捉。`--new` 明确开始新局，会在保存时替换此控制台玩家的旧训练场存档；不影响 V1 浏览器玩家存档。

真实 AI 自动完成固定捕捉目标：

```powershell
java -jar pokemon-battle-training.jar --live --new --no-battle
```

使用服务器环境变量 `OPENAI_API_KEY` 和默认 `gpt-6-luna`；`OPENAI_MODEL` 可覆盖。沿用已有接入，不把密钥写入存档。AI 目标为捕捉 wild-treecko，尚不是任意自然语言任务解析。`--no-battle` 禁止 AI 主动攻击，允许和平捕捉；已保存的禁止战斗约束不会被恢复时静默移除。模型不可用时停止，不自动使用脚本，重启手动模式可接管。

明确离线、不调用 API 的规则演示：

```powershell
java -jar pokemon-battle-training.jar --demo
```

输出 OFFLINE，固定提供方选择移动/捕捉，世界动作仍执行真实规则。使用独立演示存档，不加载玩家旧局。

新增注册工具：`observe_combat`、`attack`、`capture`。攻击复用原 AttackAction 的实际随机命中和武器伤害；存活目标在同一步实际反击，死亡目标被移除。每次操作只允许服务端绑定的野生目标，检查控制权、存活、真实相邻位置与允许区域；重复动作 ID 不再次造成伤害。捕捉只从原种类的 allowableActions 获取 CaptureAction，成功后训练师拥有的 Pokeball 包含同一个真实 Pokemon 对象，AI 文字不能代替它。

原代码规则：普通精灵球无限；木守宫可直接捕捉；火稚鸡没有捕捉动作。当前捕捉实现不检查低血量或亲密度，本阶段没有自行添加这些条件。训练场水跃鱼生命值为 1000；训练师拥有捕获物，水跃鱼负责邻近代理执行。没有增加野生生成、昼夜伤害或远程捕捉。

默认专用文件目录 `data/battle-training`。可用 `AGENT_SAVE_DIR` 修改，或沿用 V1 的 `AGENT_STORAGE=postgres` 与数据库环境变量。检查点保存生命值、位置、目标状态和球内对象；完整恢复有新任务身份。未完成且存活的任务读档后暂停，只有显式 `--live` 才重新开始；零血量不能继续控制。存档失败立即停止，须重新读档。此控制台使用单个本机玩家存档身份，不是多人服务。

验证：Java 182/182，含真实隔离 PostgreSQL；Java 8 生产编译通过。新增 12 个测试覆盖真实捕捉、不可捕捉种类、距离/区域、禁止战斗、攻击及反击、重复动作、接管、存档一致性、超额伤害归零、恢复约束及取消恢复。独立审查发现的恢复问题已用先失败后通过的回归修复。

实际 OpenAI 验收：ARRIVED → CAPTURED → GOAL_COMPLETED，实际球中 1 个木守宫；没有脚本回退。手动打击烟测出现攻击未命中、敌方反击 10 伤害，随后成功捕捉并移动到原目标格；重启后 HP990、坐标(2,1)、回合4、球内对象保留。离线固定流程另外通过文件存档往返。

后续接入：浏览器战斗面板和动画、自然语言捕捉/击败任务解析、战斗任务的审批扩展与任务奖励；多人协作和完整 V2 尚未完成。原 V1 的 Gemini 实网与本机标准 Maven 验证限制仍然存在。