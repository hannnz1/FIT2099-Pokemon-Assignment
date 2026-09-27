# 前端参考与素材说明

参考项目：[remarkablegames/phaser-rpg](https://github.com/remarkablegames/phaser-rpg)，固定参考提交 `b75f53223367df40379a785b861b705241145f7a`。

参考其 Phaser 场景、Tiled 地图、角色层与俯视移动组织方式。由于本项目需要由 Java 决定格子回合，本版自建前端适配器，未沿用上游 Arcade 连续物理移动规则。

依赖锁定 Phaser 4.2.1、TypeScript 6.0.3、Vite 8.3.0；npm 传递依赖由 `package-lock.json` 固定，后端 Jackson 2.21.4，JDK 21、Maven 3.9.11。

本版未复制上游或 Monster Tamer 的人物、地图图片、字体、音频。所有占位角色、地形和图标由本项目程序绘制，中文字体使用操作系统字体。木守宫、水跃鱼、火稚鸡均以名称和不同几何轮廓表示，不冒充精细官方形象。

Phaser 库采用 MIT，保留 npm 包内许可证和构建中的许可证注释；原 Java 项目作者和 Monash 引擎文件署名保留。上游代码 MIT 不代表其美术素材全部拥有相同授权。

## 替换素材

逻辑 ID 与图像配置集中在 `web-client/src/game/asset-manifest.ts`。

1. 将已确认可用素材放到 `web-client/public/art/`，记录来源、作者和授权条件。
2. 对相应 ID 设置 `file:'/art/xxx.png'`、`scale`、`anchor`；精灵表另设置 `frameWidth`、`frameHeight`、`frame`。
3. 重新构建；加载失败时记录 warning 并回退到清晰的几何占位图。
4. 地形颜色与符号在同文件 `terrain` 中配置；需要完整地形图集时扩展渲染适配器，Java Ground 与 API 不变。

禁止用改动碰撞、捕捉或动作规则来适配美术。新增动画帧仅用于表现。
