# OpenAI 实网验收与本机开发入口

更新：2026-10-01（Australia/Sydney）。根据玩家要求，原“Gemini 实网验收”改为“使用本机 OpenAI 配置的实网验收”。Gemini 保留可选接口，但不再是当前必做项；并未宣称 Gemini 在线通过。

## 本次结果

`gpt-6-luna` 实际调用通过，检查时间 UTC 2026-09-30T14:19:36Z。本机环境变量 `OPENAI_API_KEY` 仅在 Java 服务器进程读取，请求发送到官方 Responses API；不是在本机运行模型，也不读取任意 `OPENAI_BASE_URL` 代理。

- 原地图 V1 委托：自然语言解析、玩家确认四约束、实际函数调用、实际交付 3 个树果，金币 5。允许区域 `QUEST_AREA`，截止回合 40，服务器权威完成。
- 训练场：真实模型决定移动并捕捉木守宫，训练师精灵球实际保存 1 个目标对象，状态 COMPLETED；禁止主动战斗。
- 两个流程均无固定模型响应、路线脚本或失败回退。验收输入中 `y` 确认本次委托，后续购买默认拒绝；这些是验收玩家输入，模型工具动作仍来自真实 API。
- Java 185/185（含真实隔离 PostgreSQL），Java 8 生产编译通过。429/超时由可重复错误注入测试验证，不通过故意耗尽真实账户额度验证。

## 运行

解压 `pokemon-openai-acceptance-runtime.zip`，使用 Java 8 或以上。保持 lib 内驱动的相对位置。

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\accept-openai.ps1
```

沿用本机 `OPENAI_API_KEY`，默认 `gpt-6-luna`；`OPENAI_MODEL` 可覆盖。不将密钥写入脚本、命令参数或报告。脚本不更改全局提供方，不注册 Gemini，也不新建密钥。

重复验收生成 `acceptance-results/openai-quest.log`、`openai-capture.log` 和 `openai-acceptance.json`。成功要求实际交付与捕捉，并严格核对四约束和 40 回合确认行。每次开始先把报告设为 NOT_PASSED，失败不会保留旧 PASSED；非零退出无脚本回退。训练场使用本次独立临时文件存档，不替换玩家原存档，环境变量在退出时恢复。

项目源码中的同一脚本可用 `-JarPath` 指定此 JAR，`-OutputDirectory` 指定结果目录。

开发或手动使用：

```powershell
# 中文浏览器 V1：使用本机 OpenAI 配置
$env:LLM_PROVIDER='openai'
java -cp "pokemon-openai-acceptance.jar;lib/postgresql-42.7.11.jar" game.agent.web.AgentWebServer
# 浏览器 http://127.0.0.1:8088

# 手动输入自然语言委托
java -jar pokemon-openai-acceptance.jar

# 独立战斗训练场真实 AI
java -cp "pokemon-openai-acceptance.jar;lib/postgresql-42.7.11.jar" game.agent.combat.BattleTrainingConsole --live --no-battle
```

## 验收中修复的问题

首次运行旧控制台等待模型响应也推进回合，导致第40回合失败；修复为仅成功/进行中的实际工具推进，明确排除 DECISION_PENDING/TASK_INACTIVE。额度错误测试确认世界回合仍为0。

第二轮小地图模型反复询问旧线索，触发次数上限；第三轮原地图模型反复返回空果园，到截止回合正确停止。保留失败日志，未将它们当作成功。

改为与浏览器相同的原地图 V1 场景；将已有公开 NPC 对话历史放入后续决策观察，避免其他工具后丢失线索；为既有地点补充资源搜索/商人/交付用途，提醒空地点重复观察不能产生资源。不提供远处实时库存，不强制下一步动作。随后真实任务与捕捉通过。

新增回归覆盖原地图起点、区域和时间确认、NPC 对话跨工具保留；独立审查修复了报告忽略实际截止值及遗留旧成功状态的问题，并复核通过。

在线模型仍可能规划失败；成功记录是这次真实验收证据，不保证所有未来随机决策成功。服务器继续按预算、截止和库存停止错误流程。Maven 本机环境限制仍未解决；浏览器战斗面板和任意自然语言捕捉任务仍是后续开发。