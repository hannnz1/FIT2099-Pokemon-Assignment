# 前端参考与实际素材整合

2026-09-28 视觉整合版。参考项目：remarkablegames/phaser-rpg，固定提交 b75f53223367df40379a785b861b705241145f7a。

## 实际复用

- `web-client/public/assets/phaser-rpg/tuxemon-sample-32px-extruded.png`：原始 Tuxemon 环境瓦片图集，图片字节不变。
- `atlas.png`、`atlas.json`：原始 Misa 人物图集，四方向行走与停止帧。训练家直接使用；博士、商人复用人物并在运行时改变色调，用名称区分。
- `tuxemon-town.json`：完整上游 40×40 小镇原件。房屋、道路、围栏、草地、树木及三层布局被实际保留。
- `maps/demo.tmj`：可编辑整合地图，保留 Below Player / World / Above Player / Objects，增加隐藏 terrain 语义层（firstgid 1001）及 entities。
- `world.json`：从整合 TMJ 导出的视觉地图；Java JSON 与前端版本一起导出，World 碰撞属性必须逐格等于 Java WALL。

Phaser RPG 的 Arcade 连续移动改为 Java 权威位置 + Phaser Tween 表现；保留镜头跟随、像素画面、人物动画与树冠上层遮挡。不能由前端物理引擎另算碰撞。

## 素材许可

Phaser RPG 代码：Menglin “Mark” Xu，MIT（完整原文随资产附带）。参考教程代码：Michael Hadley，MIT。美术不以代码 MIT 替代许可。

Misa：Sanglorian / Tuxemon，CC BY-SA 4.0。Tuxemon 瓦片：Buch，由 luke83 委托，luke83 与 Past the Future 补充修改，CC BY-SA 3.0；完整上游 Tuxemon 贡献者与分别适用的许可保留在 TUXEMON-CREDITS.md 中。证据版本 f33b2f7bcbd93b3af15fa47dc07ba79b6dff17d6。

来源：
- https://github.com/remarkablegames/phaser-rpg
- https://github.com/mikewesthad/phaser-3-tilemap-blog-posts/blob/master/licenses.md
- https://github.com/Tuxemon/Tuxemon/blob/f33b2f7bcbd93b3af15fa47dc07ba79b6dff17d6/CREDITS.md
- https://opengameart.org/content/tuxemon-tileset

`/credits.html` 为页面可见署名入口。原始图集没有修改像素；动态生态使用原瓦片组合与色调，整合地图适配部分按 CC BY-SA 4.0 提供，既有素材保留原许可。原始图像和地图随源码及 JAR 静态资源分发。

## 占位边界与失败处理

宝可梦、糖果/精灵球图标、技能/好感数值效果，以及本游戏专属动作和背包面板仍为占位表现。其余环境和人物使用上述实际资源，不再用色块替代地图。

任何必需地图/人物图集加载失败，页面显示素材错误并阻止建局与输入，刷新重试；不能悄悄回退成几何地图。API 初次失败可以重新读取，不重复自动创建会话。

动态地形读取 Java snapshot，不改玩法以适配美术。静态参考图层保留；生态变化以原图集叠加。原有街道、建筑、围栏和装饰格为 FLOOR/WALL，避免生态覆盖永久设施。现采用四方向格子移动，行动驱动回合不变。

## 编辑和构建

正常编辑 `maps/demo.tmj` 后执行 `node tools/export-map.mjs`。`tools/import-reference-map.mjs` 仅供主动重置地图到固定上游布局，会覆盖编辑，不在日常构建中执行。新增美术时先登记来源与许可，再更新 WorldScene 的加载/绘制映射。语义角色名称与宝可梦占位样式在 asset-manifest.ts。
