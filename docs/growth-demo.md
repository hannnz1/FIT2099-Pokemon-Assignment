# 成长 Demo 第一批（2026-10-02）

## 已完成与运行

Java 原生地图与战斗控制真实结果，Phaser 显示研究室和森林；使用现有 Monster Tamer/AxulArt 地图和角色，沿用统一风格。角色外观是现有素材占位，并非宝可梦原版图像。原 AI 委托、混合委托和协作 V2 保留。

解压 pokemon-growth-agent-runtime.zip，安装 Java 8 或更高版本，双击启动游戏.cmd。默认端口8088，进入 http://127.0.0.1:8088/growth/。AI 使用本机 OPENAI_API_KEY 和 gpt-6-luna；没有密钥仍可手动玩。包内没有密钥、环境文件或测试存档。保留解压目录中的存档及同一浏览器身份以恢复进度。

## 玩法流程

1. 在研究室选择 Lv.5 木守宫、水跃鱼或火稚鸡。
2. 前往森林，每次点击走一步，或使用方向键/WASD。选择目标、接近目标后使用技能或尝试捕捉。
3. 击败野生目标获得经验，捕捉收入队伍但不直接奖励经验。返回研究室恢复全队 HP、PP 和状态，也可切换出战个体。
4. 探索的三个目标全部击败或捕捉后，返回研究室开始下一轮探索。首次捕捉、清场、进化可领取一次里程碑经验。
5. Lv.16 在研究室点击进化：木守宫→森林蜥蜴、水跃鱼→沼跃鱼、火稚鸡→力壮鸡。进化保留个体ID和经验。
6. 最多装备四个技能，升级解锁技能，在研究室主动学习/替换；沼跃鱼 Lv.16 可学习泥巴射击。
7. 委托 AI 将当前个体训练到指定等级（最高20）；AI 真实导航、战斗、回程恢复和开始下一轮，支持暂停、继续、取消。AI 不代玩家捕捉、换伙伴、进化或替换技能。

## 数值范围与存档

参考红宝石/蓝宝石/绿宝石的六项种族值、Medium Slow 经验曲线、第三世代属性划分与部分升级技能；固定 IV15、EV0、中性数值，无性格修正。Lv.5累计135经验，Lv.16累计2535，Lv.20累计5460。战斗奖励为 floor(经验产出×野生等级/7)×3，是 Demo 加速规则。三个初始形态和三个一阶进化，共6形态、15个技能，等级上限20。技能含PP、命中、属性加成、克制、部分状态/能力等级与PP耗尽挣扎。

成长队伍单独持久化，未把原委托收藏直接迁入成长；不能混用两种队伍。存档保存个体、经验、HP/PP、地图位置、实际野生目标状态、里程碑、AI任务和预算；运行中任务重启后暂停，须玩家继续。写入失败冻结并允许恢复最后提交状态。重复请求防重放，旧任务取消不能停止新任务。

## 验收结果

- Java347/347通过，包含11项真实 PostgreSQL 测试；前端77/77；Maven成功（独立Maven未配置PG的11项跳过，PG另行全跑）。运行类文件Java8 major52，JAR前端资源与源码一致。
- 原生 HTTP 从 Lv.5 实际战斗到 Lv.16，再进化和替换技能，共299次地图/战斗动作，没有直接修改经验。
- 真实本机 OpenAI gpt-6-luna：进化个体沼跃鱼 Lv.16→18，16次决策、27次动作，无无效调用，无脚本AI回退。
- 浏览器实际战斗从Lv.15升到16，回研究室恢复、进化、学习泥巴射击；刷新及服务器重启后保留Lv.16、EXP2976、HP50和技能。无浏览器警告/错误。

## 后续未完成

本批完成研究室→森林→战斗成长→恢复→一阶进化闭环。河流/山地等地图、更多物种、二阶进化、成长队伍与原委托协作整合仍未完成。没有完整复刻RSE（完整技能/特性/性格/EV培养/完整异常状态等不在本批）。素材授权沿用 client/THIRD_PARTY_NOTICES.md，公开发布前需核对各美术来源的许可。

## 参考

独立 Java 实现，参考数据：[种族值](https://raw.githubusercontent.com/pret/pokeemerald/master/src/data/pokemon/species_info.h)、[经验表](https://raw.githubusercontent.com/pret/pokeemerald/master/src/data/pokemon/experience_tables.h)、[升级技能](https://raw.githubusercontent.com/pret/pokeemerald/master/src/data/pokemon/level_up_learnsets.h)、[进化条件](https://raw.githubusercontent.com/pret/pokeemerald/master/src/data/pokemon/evolution.h)。
