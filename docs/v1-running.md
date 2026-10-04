# V1 运行说明

## 最新 Phaser 版本（2026-10-01）

现有 RPG 素材已接入 Java 原地图与训练场，支持真实状态、键盘和目标选择。最新运行包 pokemon-phaser-game-runtime.zip；Java260/260、前端50/50。详见 [Phaser 接入说明](phaser-integration.md)。下方历史验收数字保留当时结果。


解压运行包，保持 `pokemon-agent-v1.jar` 与 `lib/postgresql-42.7.11.jar` 的相对位置。安装 Java 8 或更高版本，在解压目录运行：

```powershell
java -jar pokemon-agent-v1.jar
```

打开 http://127.0.0.1:8088 。`AGENT_PORT` 可覆盖端口。只在本机监听；默认文件存档目录 `data/agent-saves`，`AGENT_SAVE_DIR` 可覆盖。保留浏览器 Cookie 才能恢复对应玩家存档，Cookie 有效期 30 天；清除 Cookie 会创建另一位玩家。

AI 使用本机服务器环境变量 `OPENAI_API_KEY`，默认模型 `gpt-6-luna`，`OPENAI_MODEL` 可覆盖。不要将密钥写入浏览器或版本库。未配置密钥时仍可手动游戏。可选 `LLM_PROVIDER=gemini` 并配置 `GEMINI_API_KEY`；此次只验证 Gemini 离线接口。

PostgreSQL 可配置：

```powershell
$env:AGENT_STORAGE='postgres'
$env:AGENT_DB_URL='jdbc:postgresql://127.0.0.1:5432/pokemon'
$env:AGENT_DB_USER='你的专用数据库角色'
# AGENT_DB_PASSWORD 在本机安全配置
java -jar pokemon-agent-v1.jar
```

先创建专用数据库及角色，表结构见 `docs/sql/pokemon-agent-storage.sql`。服务只初始化自己的存档表。配置 PostgreSQL 后失败不会悄悄切换文件。`AGENT_STORAGE=memory` 显式关闭持久化。数据库与 API 凭据仅在服务器使用。

输入例如“帮我收集3个树果交给博士，不要花金币，不要主动战斗，只在任务区域活动，在第40回合之前完成”。先理解，再确认执行。支持“任务区域”和“果园区域”，截止回合为 1–60 的绝对世界回合；不支持的区域或含糊参数会拒绝。只允许果园区域时无法到博士处交付，服务器不会越界替玩家完成任务。

AI 运行中先暂停才能手动移动、拾取、交付或等待。模型超时/不可用时可直接手动继续。暂停等待、模型响应和审批等待不推进世界；实际成功动作推进回合。手动移出约束区域后须回到区域内才能恢复 AI。购买需要精确授权，可批准、拒绝或取消；拒绝后模型重新规划。

服务重启恢复实际位置、库存、金币、资源、回合、NPC 记忆和记录；未完成 AI 任务保持暂停，需点击继续。恢复使用新的任务身份，旧审批与旧请求失效。存档写入失败时停止动作，点击“重新读取存档”回到最后持久化状态；已发生但未成功保存的动作可能消失，不能继续累计未保存操作。

V1 使用原 59×13 地图的和平任务模式：第 60 回合天黑、关闭野生生成、训练水跃鱼生命值 1000、木守宫在户外真实游荡。传统控制台入口 `game.Application` 仍运行原战斗和捕捉游戏；`game.Application --agent-v1` 进入本演示。

离线稳定验收，不消耗模型额度：

```powershell
java -cp "pokemon-agent-v1.jar;lib/postgresql-42.7.11.jar" game.agent.demo.V1AcceptanceDemo
```

它明确使用固定模型响应和模拟玩家拒绝，真实执行规则、检查交付与存档往返，不代表实网模型调用。设置数据库环境变量后也检查数据库存档。

源码使用 Maven 配置和 Java 8；推荐正常环境运行 `mvn clean verify`。本机 Maven 受 JDK ZIP 文件系统访问限制，此次使用独立编译/JUnit 流程验证，见验收记录。移植来源及许可位于 `third_party`；PostgreSQL JDBC 的许可证在驱动 JAR 的 META-INF 中，保留原课程和素材署名。
