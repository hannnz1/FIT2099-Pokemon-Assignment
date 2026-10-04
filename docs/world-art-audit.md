# 场景名称与素材对应审计（2026-10-03）

21 种宝可梦已有完整的 42 张 PNG；本次未下载新宝可梦或加入物种。此前果园、商店、研究室、林间仅显示名称，树果资源没有图标；博士与商人共用 AxulArt 人物的不同朝向。补充的 `client/agent/world-art.mjs` 是显示映射，不能增加资源、移动角色或修改规则。

| ID / 名称 | 表现 | 来源与准确性 |
|---|---|---|
| professor / 博士 | 原创64×88像素SVG：灰发、眼镜、白大褂、研究册 | Demo原创研究员角色，独立于商人 |
| merchant / 商人 | 原创64×88像素SVG：宽檐帽、暖色马甲、货袋 | Demo原创商人角色，独立于博士 |
| player / 玩家 | AxulArt frame 7 | 已有人物向下帧 |
| market / 商店 | 原地图商店完整切片，1472,1632,448,384 | 已有同风格商店素材 |
| laboratory / 研究室 | 原地图房屋切片，640,1344,256,192 | 普通房屋代表本地 Demo 研究室，不是官方研究室 |
| alternative / 林间 | 原地图树切片，1280,4992,128,128 | 已有树木素材 |
| orchard / 果园 | 原创带果实树 SVG | UI 地点符号，不是上游/官方素材 |
| berry / 树果 | 原创树果 SVG | UI 资源符号，不对应任何官方命名树果 |
| recovery / 恢复点 | 原创帐篷与恢复符号 SVG | UI 功能标识，不是宝可梦中心素材 |
| gate / 入口 | 原创门框箭头 SVG | UI 方向标识 |

切片在原始 PNG 上实测预览，均处于图片边界内。没有从不明网站下载素材；SVG 为本项目代码生成的原创界面符号，不套用任何第三方许可。

## 可核查的来源

- 原导入 [Monster Tamer 上游仓库及 Credits](https://github.com/devshareacademy/monster-tamer#credits)，项目导入 commit 为 `a964bba7ca0ae1aeeb712065a01b09ce3366f395`（见 `client/UPSTREAM.md`）。现有地图许可限制仍见 `client/THIRD_PARTY_NOTICES.md`；仓库 MIT 不能作为地图图像的统一许可。
- 人物作者 [AxulArt: Small 8-direction Characters](https://axulart.itch.io/small-8-direction-characters)，2026-10-03 在线检查：作者页标注 CC Attribution 4.0，且允许修改并要求署名。本地导入的 `license.txt` 则保留 CC Attribution-ShareAlike 4.0 和不得转售/再分发字样，两者存在差异。本次复用原本地 Demo 图像，保留旧文件，不把网页许可自动视为本地历史包的重新授权。
- 现有 `custom.png` SHA256：`c51b6b8b2a6912512c2aa9a5a5cb315514f60199b68bc49ffff25f36b4211739`。
- 现有 `main_1_level_background.png` SHA256：`67113ec592169ad232e4e7e2fa266f9f556fedc9847b46c089a8e1f0ff091f79`。

## 接入 API

`getWorldArt(id)` 返回冻结描述，未知 ID 抛 `UNKNOWN_WORLD_ART`。`worldArtUrl(id)` 返回 `/rpg/` 下已打包 PNG 路径或 SVG data URL。`worldArtDataUrl(id)` 只接受原创 symbol。`worldArtDomStyle(id, size)` 返回可 `Object.assign(element.style, ...)` 的 CSS 切片/符号样式；需保留旁边的文本或 `aria-label`，不要只展示背景图。

Phaser：`atlas` 条目沿用现有 `terrain` texture，在 create 阶段 `texture.add(art.frame, 0, crop.x, crop.y, crop.width, crop.height)`；`sprite` 沿用现有 `human` 64×88 sheet、指定 `frame` 与 `tint`；`portrait` 使用 `/rpg/assets/ui/npcs/<id>.svg` 与独立 texture；`symbol` 在 preload 阶段使用 `/rpg/assets/ui/world/<id>.svg` 加载，避免Phaser data URI阻塞。每种 symbol 的 texture 是 `world-` 加英文 ID；渲染按种类使用 `this.add.image`。

地点和树果的显示必须来自当前服务端坐标、可见区域与真实库存；资源耗尽就移除图标。不得从此 manifest 推断资源量、碰撞、NPC 状态或恢复权限。DOM 背景切片不会自动应用 Phaser tint；人物旁需保留角色名称。

## 建筑外围草地去除

研究室和商店在Phaser创建显示纹理时使用building-cutout.mjs，去除与切片边缘相连的草地，再保留建筑主体。原PNG、地图坐标与碰撞不变；DOM原切片API仍为原图描述。
