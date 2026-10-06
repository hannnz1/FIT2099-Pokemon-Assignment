# Pokemon AI Agent Game

Java 游戏引擎 + Phaser 网页交互的可执行 AI Agent Demo，源自 FIT2099 宝可梦控制台项目。

在线体验：https://pokemon.hanzhu-lab.online/ ｜ 伙伴冒险：https://pokemon.hanzhu-lab.online/growth/

## 最新游戏更新（2026-10-06）

新增 `/duel/` 训练师对战：玩家对 AI、委托己方、AI 对 AI；队伍副本、技能/换人工具、隐藏选择、回放与固定种子评测。详细范围及验收见 [V5 对战 Agent](docs/v5-battle-agent-acceptance.md)。V5 已接入在线游戏；本次同时同步新手冒险、评测中心、界面优化、稳定地图缩放、移动回执与受限委托恢复。

## 当前功能

- 自然语言委托：采集、交付、战斗、捕捉、伙伴培养；工具校验、区域/回合/费用约束及购买审批。
- 玩家与 Agent 共享任务、资源竞争、贡献账本、暂停/取消与手动接管。
- 多区域探索、升级进化、技能 PP、营地恢复、条件式野生刷新及键盘/触控界面。
- 原子文件与 PostgreSQL 存档、跨地图记忆、重启恢复、行动记录与批次评测。
- DeepSeek / OpenAI 适配；公开站采用 deepseek-flash，持久化 Token 额度、限流与并发保护。

## 运行与测试

Java 8+ 与 Maven：`mvn clean verify`。前端无额外依赖：`cd client`，`node --test test/unit/*.test.mjs`。
网页入口和服务启动见 [运行说明](docs/unified-entry.md)，公开 AI 管理见 [运维说明](docs/public-ai-operations.md)。密钥只配置在服务端环境变量中。

2026-10-06 benchmark：Java 625 项通过、21 项 PostgreSQL 集成测试跳过；前端 220 项通过。详见 [全功能回归报告](benchmarks/full-benchmark-20261006/README.md)，其中仍未完成的浏览器专项、真机和生产耐久验收单独列出。

## 最新 Benchmark（2026-10-06）

六场景 × 三 Seed 的规则基线完成 18/18。DeepSeek 实网在 300,000 Token 上限内完成 9 个案例，1 个响应格式失败，8 个预算保护未完成；实际消耗 286,997 Token，99 次 HTTP 请求。六类场景均有成功样本，但不能称为完整矩阵全部通过。训练目标 ID 混用和响应解码失败仍待修复；详见 [实网报告及原始案例](benchmarks/deepseek-benchmark-20261006-513b6a5/README.md)。

以下 2026-10-04 数据为历史批次，不能与本轮 DeepSeek 结果混用。

## Benchmark（2026-10-04）

五类固定 Seed 场景：培养、共享交付、资源竞争、信息转发及受控工具拒绝恢复。
规则基线100/100完成，同Seed重跑100/100初始与最终指纹一致；本机OpenAI gpt-6-luna完成13/15任务，147次模型决策调用平均1.33秒、P95 2.03秒，累计292032 Token。
这是内部小样本，不是线上DeepSeek指标或生产SLA；两项失败均保留。详见 [报告](benchmarks/2026-10-04/resume-benchmark-report.txt)与[案例数据](benchmarks/2026-10-04/resume-benchmark-cases.csv)。

## 来源与许可

保留 FIT2099 教学引擎作者署名和原始项目说明。Phaser、参考模块和像素素材的来源与许可见 [第三方声明](client/THIRD_PARTY_NOTICES.md)、[素材来源](client/UPSTREAM.md)与 third_party 下的许可证。宝可梦形象与名称仅用于学习 Demo，不表示拥有相关商标或商业授权。

## 原始项目说明

# FIT2099 Pokemon Console Game
## 最新扩展：Lv.40 与最终进化

三条初始伙伴进化链延伸到Lv.36最终形态，等级上限40，9形态、27技能，长期文件存档回执修复。Java355/355（真实PG）、前端77/77、真实OpenAI与服务器重启通过。运行包pokemon-growth2-agent-runtime.zip，入口/growth/。详见[玩法与验收](docs/growth-demo-stage2.md)。


## 最新扩展：混合委托（2026-10-02）

支持按顺序完成2–3阶段树果交付与捕捉/击败；树果由水跃鱼执行，野外可交给选定收藏伙伴。Java328/328真实PG、前端73/73、真实OpenAI/浏览器/重启通过，原文档协作V2保留。最新运行包pokemon-mixed-agent-runtime.zip，详见[玩法与验收](docs/mixed-agent.md)。

## 最新扩展：AI 指挥收藏伙伴（2026-10-02）

在AI出战伙伴中选择收藏个体，确认后自动召唤并执行捕捉/击败及顺序野外任务。原文档协作V2保留。Java317/317真实PG、前端70/70，真实OpenAI和浏览器重启验收通过。最新包pokemon-owned-agent-runtime.zip，详见[玩法与验收](docs/owned-agent.md)。

## 最新版本：原文档协作 V2（2026-10-02）

独立玩家与AI同时行动、共享任务、贡献账本、资源竞争及Phaser同步已完成。运行包pokemon-v2-complete-runtime.zip，Java306/306真实PG、前端67/67、真实OpenAI/浏览器/重启验收通过。详见[完成与验收说明](docs/v2-complete.md)。下方版本数字为历史记录。

## 最新 Phaser 版本（2026-10-01）

现有 RPG 素材已接入 Java 原地图与训练场，支持真实状态、键盘和目标选择。最新运行包 pokemon-phaser-game-runtime.zip；Java260/260、前端50/50。详见 [Phaser 接入说明](docs/phaser-integration.md)。下方历史验收数字保留当时结果。

## Agent 游戏统一入口（2026-10-01）

一个 Java 服务现在可同时提供原地图树果委托 `/quest/` 与浏览器战斗训练场 `/training/`。页面导航暂停来源 AI，两个场景分别存档并支持重启恢复。运行包双击“启动游戏.cmd”；OpenAI 默认模型为 `gpt-6-luna`。详见 [统一入口说明](docs/unified-entry.md)。已支持把捕捉的木守宫带回原地图玩家收藏背包，保留生命值和存档。详见 [携带说明](docs/pokemon-carry.md)。已支持原地图召唤与收回收藏木守宫，真实占位并支持重启恢复。详见 [召唤说明](docs/pokemon-summon.md)。已支持可开启/停止的自动跟随，位置与回合一起恢复。详见 [跟随说明](docs/pokemon-follow.md)。已支持切换出战伙伴、原地图野生战斗/捕捉及水跃鱼收藏；详见 [最新完成说明](docs/gameplay-completion.md)。

[![Java CI](https://github.com/hannnz1/FIT2099-Pokemon-Assignment/actions/workflows/build.yml/badge.svg)](https://github.com/hannnz1/FIT2099-Pokemon-Assignment/actions/workflows/build.yml)

A turn-based Pokemon-inspired console game developed for Monash University's FIT2099 Object-Oriented Design and Implementation unit.

The project was built on top of a teaching game engine and starter framework supplied by the unit. The engine provides reusable infrastructure such as actors, actions, maps, locations, items, weapons, menus, and the world loop. My work focused on extending that framework with the concrete Pokemon gameplay and domain logic required by the assignment.

## Project Overview

The player explores a map populated by Pokemon and non-player characters. Gameplay includes movement, combat, Pokemon-specific behaviours, capture mechanics, items, environmental effects, trading-related components, affection management, and time-based world behaviour.

The implementation uses object-oriented modelling to keep game entities and behaviours separate. Actions encapsulate player interactions, behaviours control autonomous actors, and specialised Pokemon and environment classes extend common abstractions supplied by the engine.

## Main Features

- Turn-based map exploration through a console interface
- Pokemon actors including Mudkip, Torchic, and Treecko
- Combat with weapon damage, hit probability, defeat, and item-drop handling
- Autonomous attack, follow, and wander behaviours
- Pokemon capture actions and different Poke Ball types
- Element and capability-based interaction rules
- Affection-point management
- Professor Oak and Shopkeeper NPCs
- Tradable items and candy
- Terrain and environmental elements such as lava, puddles, waterfalls, trees, hay, and craters
- Day/night and time-perception components

## My Contribution - Han Zhu

This repository contains a teaching framework whose original source headers name the framework authors, including Riordan D. Alfredo and Ian K. Felix. Those headers identify the origin of the supplied scaffold; they do not mean that the submitted gameplay requirements were already implemented.

Working within that scaffold, I contributed to the implementation and integration of the assignment-specific gameplay, including:

- extending the supplied `Actor`, `Action`, `Ground`, `Item`, and `Weapon` abstractions for the Pokemon domain;
- implementing and integrating concrete Pokemon, NPC, action, behaviour, item, environment, and world components under `src/game`;
- connecting combat, capture, elemental capability, affection, inventory, map, and time-related rules to the engine lifecycle;
- configuring the playable world, map terrain, actors, items, and starting state;
- applying inheritance, polymorphism, interfaces, composition, and behaviour/action abstractions to keep responsibilities separated; and
- contributing to the UML class and sequence-diagram documentation supplied in `docs`.

This was a team assignment. The statements above describe my contribution to implementing functionality on top of the supplied framework and do not claim authorship of the FIT2099 engine or sole authorship of every file in the team submission.

## Supplied Course Code vs Student Implementation

### Course-supplied engine

The reusable engine is located primarily under:

```text
src/edu/monash/fit2099/engine/
```

It supplies the generic game runtime, including actors, actions, maps, display, items, weapons, and turn processing.

### Assignment implementation

The Pokemon-specific implementation is located primarily under:

```text
src/game/
```

This package adapts and extends the engine for the assignment domain. It contains Pokemon, NPCs, player actions, AI behaviours, environmental objects, items, weapons, conditions, time management, and world configuration.

## Object-Oriented Design

The project demonstrates several object-oriented techniques and design ideas:

- **Command-style actions:** interactions such as attacking, capturing, talking, and trading are represented as action objects.
- **Strategy-style behaviours:** autonomous actors can select between attack, follow, and wander behaviours.
- **Inheritance and polymorphism:** concrete Pokemon and terrain types specialise shared base classes.
- **Capability-based rules:** actor and element capabilities are used to decide which interactions are permitted.
- **Template Method:** affection interactions share one workflow while each action supplies its favourite capability.
- **Observer:** time-aware actors and environments subscribe to a deterministic day/night cycle.
- **Factory:** type-safe trade offers and special-attack definitions create independent item instances.
- **Manager components:** affection and time-cycle state are separated from individual actors.

Design material is available in the `docs` directory, including class diagrams and an attack-action sequence diagram.

## Project Structure

```text
src/
|-- edu/monash/fit2099/engine/  # Course-supplied reusable game engine
`-- game/                       # Pokemon assignment implementation
    |-- actions/
    |-- actors/
    |-- behaviours/
    |-- conditions/
    |-- environments/
    |-- items/
    |-- positions/
    |-- time/
    `-- weapons/
test/                           # JUnit 5 tests for assignment logic
docs/                           # UML and design documentation
```

## Build and Test

The project uses Java 8, Maven, JUnit 5, and JaCoCo.

```bash
mvn clean verify
```

The command compiles the project, runs all tests, and generates a coverage report at
`target/site/jacoco/index.html`. GitHub Actions runs the same verification for every
push and pull request to `main`.

## Running the Project

1. Install JDK 8 or later and Maven 3.8 or later.
2. Run `mvn clean package` from the repository root.
3. Open the project in IntelliJ IDEA and run `game.Application`.
4. Follow the numbered commands displayed in the console.

## Current Limitations

The completed opt-in V1 Agent demo provides a Chinese browser interface on the original map in peaceful quest mode. It uses server-side OpenAI credentials and defaults to `gpt-6-luna`; Gemini remains optional. Actual engine movement, inventory, delivery, NPC memory, manual takeover, typed constraints and player-approved purchases are authoritative. File and PostgreSQL checkpoints restore world state and paused tasks with invalidated approvals.
See [V1 running guide](docs/v1-running.md), [acceptance evidence](docs/v1-acceptance.md) and [development progress](docs/development-progress.md). The original console entry point remains available; `game.Application --agent-v1` starts the demo. This is a local quest demo, with later multiplayer and battle-agent work outside V1.
This repository has been refactored from the original coursework submission into an
OOP and testing portfolio project. Affection, NPC dialogue, Candy trading, special
weapons, deterministic behaviour priority, and the day/night observer workflow have
been completed or reworked. Focused unit tests cover the main state transitions.
Further improvement can concentrate on capture-policy variants, broader map-level
integration tests, and replacing the remaining long-form coursework comments with
concise API documentation.

## Team

- Rian Barrett
- Xinwei Li
- Han Zhu

## Academic Attribution

This project was created for educational purposes as part of FIT2099 at Monash University. The engine and starter framework were supplied by the teaching team. Pokemon-related names and concepts belong to their respective rights holders. This repository is intended only as a coursework and portfolio demonstration.

## Agent combat/capture training

A separate playable Java arena exposes actual attack, enemy retaliation and trainer-owned capture through trusted Agent tools. It includes a Chinese browser interface and confirmed OpenAI natural-language capture/defeat tasks. Set `AGENT_MODE=battle` for the arena, or omit it for the V1 quest page. It retains the legacy species capture rules. See [browser arena guide](docs/browser-battle.md) and [console training guide](docs/battle-training.md). Multiplayer and the complete V2 scope remain future work.

## Current online acceptance provider

The user-selected online acceptance uses locally configured OpenAI credentials and defaults to gpt-6-luna. Gemini live acceptance is no longer a required unfinished item. The repeatable script exercises the real original-map quest and real capture, without scripted fallback. See [OpenAI live acceptance and development entry](docs/openai-live-acceptance.md).

## 2026-10-01 单人玩法扩展完成

原地图野生战斗/捕捉、切换已召唤出战伙伴及原生水跃鱼收藏已完成，含受伤恢复与濒危救助。最新 Java259/259（含真实 PostgreSQL）、客户端46/46、Java8编译、打包浏览器及无模型整链验收通过。详情见 [完成说明与验收](docs/gameplay-completion.md)。更早章节的剩余列表和测试数量是阶段历史，当前范围以此说明为准。

## 最新扩展：成长 Demo 第一批

研究室与森林、Lv.5–20成长、四技能/PP、Lv.16一阶进化及真实OpenAI培养已接入Phaser。成长队伍单独存档。运行包pokemon-growth-agent-runtime.zip，进入/growth/。详见[玩法、范围与验收](docs/growth-demo.md)。

## 最新更新：宝可梦形象

9形态插画与第三世代地图像素图已接入；新增初始伙伴图片卡和进化预览。新版运行包 pokemon-art-agent-runtime.zip，入口 /growth/。[验收与使用说明](docs/pokemon-art-update.md)。

## 最新开发：成长伙伴带回原地图

研究室可将伙伴移入原地图收藏，保留等级与技能数据；可以召唤和委托AI捕捉。该批为第1阶段第一批，原地图完整成长战斗尚未接入。运行包pokemon-growth-carry-agent-runtime.zip。[说明与验收](docs/growth-carry.md)。

## 最新开发：收藏伙伴返回培养

已有成长个体可在研究室与原地图双向移动，返回后继续AI培养；原地图完整成长战斗尚未接入。运行包pokemon-growth-return-agent-runtime.zip。[说明与验收](docs/growth-return.md)。

## 最新交付：V2完成复验，V3基础启动

协作V2通过当前OpenAI实网与重启复验。V3已有独立可运行的调度/消息/预算/持久化基础模块，尚未接入主地图NPC模型循环。按用户要求停在V3启动位置。[运行与范围](docs/v3-start.md)。最新运行包pokemon-v2-complete-v3-start-runtime.zip。

## 最新开发：V3独立NPC与角色消息

原地图开启多角色协作，让火稚鸡、博士和商人各自调用模型并分享真实观察；支持暂停、预算和恢复。V3第一批完成，长期记忆/Reflection/Daily Planning尚未完成。[运行和验收](docs/v3-npc-agents.md)。运行包pokemon-v3-npc-agent-runtime.zip。

## 最新开发：V3观察历史与去重

原地图开启多角色协作，让火稚鸡、博士和商人各自调用模型并分享真实观察；支持暂停、预算和恢复。V3第二批完成，观察历史与成功去重已实现；模型Reflection/Daily Planning尚未完成。[运行和验收](docs/v3-memory-agents.md)。运行包pokemon-v3-memory-agent-runtime.zip。

## 2026-10-03 V3 Demo完成

2026-10-03：原文档V3 Demo完成。角色私有长期记忆/压缩检索、模型反思、三阶段日计划与重规划、来源链转述、前端计划面板及共享任务实网通过。425项Java（16项真实PG）、81项前端、Maven通过。实网玩家1+Agent2，第27回合完成，金币5；重启记录一致。详细边界见v3-complete.md。 [运行与验收](docs/v3-complete.md)。

## 2026-10-03 V3最终调度与记忆复验

2026-10-03：原文档V3 Demo最终复验完成。修复空闲角色额度扣减和重复观察时间校验，新增6项边界测试。角色私有长期记忆/压缩检索、模型反思、三阶段日计划与重规划、来源链转述、前端计划面板及共享任务实网通过。431项Java（16项真实PG）、81项前端、Maven通过。实网玩家1+Agent2，第31回合完成，金币5；重启记录一致。详细边界见v3-hardened.md。 [运行与验收](docs/v3-hardened.md)。

## 2026-10-03 旧收藏成长迁移

旧收藏成长迁移已完成：木守宫/水跃鱼显式转为Lv.5，保留ID与HP比例，失败恢复及往返/重启通过；435项Java（含17项真实PG）、81项前端、Maven通过；OpenAI真实捕捉后迁移全流程通过。原地图技能/PP/状态/EXP及顺序混合战斗验收仍待实现。 [运行与验收](docs/legacy-growth-migration.md)。

## 2026-10-03 P0成长整合完成

P0阶段1按Demo范围完成：旧收藏迁移、成长档案双向转移、原地图共享技能/PP/状态/经验、顺序/混合培养伙伴委托与重启恢复。445项Java含18项真实PG、84项前端、Maven通过；最终JAR实网顺序第8回合、混合第32回合完成；自然Lv.5浏览器技能和重启通过。下一项为河流/山地地图扩展。 [规则与验收](docs/field-growth-complete.md)。

## 2026-10-03 阶段2地图扩展完成

阶段2地图扩展按Demo范围完成：河流/山地、连接门与碰撞、分区遭遇和轮次、营地恢复、固定区域AI委托、旧存档迁移及文件/PG恢复。Java466含19项真实PG、前端86、Maven通过；OpenAI三区域与河流清场往返新轮次、浏览器和重启通过。下一阶段为新增6–8个基础物种。 [规则与验收](docs/regions-complete.md)。

## 2026-10-03 V3内容与成长拓展完成

V3内容拓展按开发计划阶段3–5的Demo范围完成：6个新基础物种及6个进化形态、共21形态统一外观，三地区连续冒险/解锁、终身图鉴和一次性奖励，普通会心/首批状态/特性/5性格/有界EV及共享结算。Java487含20项真实PG、前端86、Maven通过；6种新伙伴及最终自然Lv.5共7次OpenAI实网培养、1152场脚本原生平衡样本、浏览器、重启及解压运行包通过。完整RSE复刻不在此Demo范围。 [规则与验收](docs/v3-expansion-complete.md)。

## 2026-10-03 V4评测基础启动

V4已启动：固定Seed/独立Reset、单场景批量脚本与真实OpenAI评测、完整Trace及每API一次的usage统计。Java494含20真实PG、Maven通过；Seed11/22实网2/2完成，Reset基线指纹复现。V4多场景、多模型比较、批次持久化/仪表盘和价格/恢复率仍未完成。 [说明与验收](docs/v4-evaluation-foundation.md)。

## 2026-10-03 V4 Demo 完成

V4五场景、多模型比较、批次队列/暂停/取消/重启恢复、评测仪表盘/回放/下载及用量/成本配置完成。Java514含21真实PG、前端89、Maven通过；真实AI五场景5/5与两模型重复4/4通过。范围与证据见 docs/v4-evaluation-complete.md。V5未启动。

## 2026-10-03 UI优化第一批完成

按用户要求暂不开发V5。完成主冒险界面、伙伴/收藏卡片、真实AI状态与示例、信息折叠、统一导航与窄屏整理。前端94、Java514含21真实PG、Maven通过；真实AI培养/捕捉、伙伴往返、浏览器与UI复审通过。见 docs/ui-polish-complete.md。

## 2026-10-03 UI优化全部完成（已确认 Demo 清单）

完成剩余五项：真实捕捉/升级/进化与技能反馈；训练/图鉴/技能学习/收藏统一；评测表格与回放；390px 触控；新手全流程引导。前端113、Java514含21真实PG、Maven通过；原生Lv16进化与技能替换、真实AI培养/捕捉、收藏往返与四页窄屏验收通过。V5继续暂缓。详见 docs/ui-optimization-complete.md。

## 2026-10-03 地图素材与失败恢复修复

补齐命名地点/人物/物品素材，标明真实边界、视口和出口；修正到期手动权限并加入三页失败恢复入口。前端133、Java515含21真实PG、Maven通过。验收见 docs/ui-bugfix-report.md。

## 2026-10-03 博士与商人独立形象

原创像素博士（白大褂/眼镜/研究册）与商人（宽檐帽/暖色马甲/货袋）接入地图及对话。前端134、WebServer5通过。详见 docs/npc-personality-report.md。

## 2026-10-03 建筑外围绿色去除

研究室和商店外围草地改为透明，直接贴合现有地面。前端137、WebServer5通过；位置、碰撞与存档未改。详见 docs/building-cutout-report.md。

## 2026-10-03 伙伴冒险入口修复

默认关闭粉色障碍辅助层；伙伴走到已解锁入口自动切换，入口使用小径与木质路牌。Java523、前端139通过，并完成浏览器键盘、触控与存档恢复验收。详见 docs/growth-entrance-report.md。

## 2026-10-03 AI培养跨地图委托修复

跨地图保留服务器培养目标，并自动显示AI实际动作目标，无需重新点选。前端142通过；Java新增跨三地图自动战斗完成测试通过，并完成浏览器脚本决策器验收。详见 docs/growth-continuity-report.md。

## 2026-10-03 伙伴自动进化修复

成长队伍达到等级门槛自动进化，旧超过门槛存档读档补进化，名称/头像/地图形象同步。Java530（含实际数据库）、前端142通过，并完成真实文件旧Lv17水跃鱼变为沼跃鱼浏览器验收。详见 docs/auto-evolution-report.md。

## 2026-10-03 冒险手册与旧档连续冒险入口修复

冒险手册禁用原因直接显示，已有存档回研究室可保留队伍成长并重新生成连续冒险调查遭遇；未完成委托先取消。Java533（含实际数据库）、前端145通过，并完成旧档转换、重载和手机布局浏览器验收。详见 docs/adventure-migration-report.md。

## 2026-10-03 连续冒险状态与下一步引导优化

已开启使用绿色状态卡，显示下一步和区域剩余目标；增加沿现有路径导航入口和目标列表定位，调查奖励改为状态卡。前端148通过、Java8包构建成功，浏览器验证导航进入森林和手机布局。详见 docs/adventure-ui-report.md。

## 2026-10-03 按钮点击后键盘移动修复

成长和其他Phaser地图移除普通按钮焦点阻断，方向键继续控制角色，保护表单编辑、快捷键及操作锁。前端151通过、Java8包构建成功，浏览器复现旧问题并验证修复。详见 docs/keyboard-focus-report.md。

## 2026-10-03 自动返程恢复

出战伙伴倒下自动返回区域营地并恢复，HP不高于30%时手动一键返程；支持取消、失败停止和AI互斥。前端155通过、Java8构建成功，真实文件浏览器验收恢复HP/PP、低血量及取消流程。详见 docs/auto-recovery-report.md。

## 2026-10-03 探索规则与地图营地

取消探索昼夜固定扣血/回血；到营地自动恢复整队；丰富河岸和山道并将营地与出口分开；清空后实际移动10步刷新下一批野生伙伴，上限3只。Java540项、前端156项及独立浏览器验收通过。详见 docs/region-polish-report.md。

## 2026-10-03 森林与训练场画面优化

苔叶森林新增林间小径、花丛及营地铺地；独立训练场统一像素素材，增加木质围栏和捕捉/对战标牌。前端158项、相关Java70项及浏览器移动/捕捉/营地恢复检查通过。详见 docs/forest-arena-report.md。

## 2026-10-06 四批体验优化

地图和实体持续复用，移动输入串行且支持按住方向键／触控；战斗和成长效果只播放服务器确认的新事件。新手围绕同一个伙伴完成战斗、营地恢复、捕捉、成长和见习训练师对战；旧存档保持自由探索。评测历史可归档／删除，不返还已用额度，未知付费用量保留。

邀请身份默认关闭。需持久化存储并设置 `PLAYER_IDENTITY_ENABLED=true` 与 `PLAYER_INVITE_HASHES`（逗号分隔的 SHA-256 邀请码摘要）后启用；原始邀请码由管理员私下分发。启用后匿名玩家仍可免费游玩及使用 baseline，真实模型需要绑定身份。绑定成功保存一次性恢复凭据；第二设备先预览，再确认采用云存档。最多32个身份、每个身份8条设备记录；撤销当前设备，恢复凭据可重新登录。运行中的任务／评测或未结算用量会阻止迁移。

绑定请求的强凭据在浏览器标签页的 sessionStorage 中暂存，仅用于响应丢失后的同一请求重试，确认成功后清除；服务器只保存摘要。不要关闭尚未确认绑定的标签页。恢复凭据具备登录权限，不能发给别人。

运维脚本在 `deploy/tencent/operations/`：定期一致性备份、SHA-256 校验、只读／无网络恢复演练、预算与服务告警日志。备份不包含 API 密钥。恢复演练不会覆盖生产存档或回滚费用账本；生产恢复需先单独保留现行费用账本和凭据。异地自动同步、外部告警接收渠道仍需管理员配置。

测试证据及真实模型失败见 `docs/optimization-execution.md` 和 `benchmarks/`。真实模型不能用免费基线结果替代。当前第一轮18个计划案例中6个完成、5个模型／规划失败、7个触及独立预算停止；修复后的付费复测已结束，完整结果见下面的最终验收记录。


最终四批验收记录见 [optimization-four-batches-report.md](docs/optimization-four-batches-report.md)。修复实网复测18例：8完成、3失败、7预算停止，283,151 Token（新增上限30万）；不将预算停止当作成功。资源竞争三个 Seed 完成，故障/对战的修复版实网验收仍缺；日常异地同步、外部通知、真人及实际手机验证也待完成。公开游戏镜像尚未更新。
