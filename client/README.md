# 当前 Java Demo 的 Phaser 入口

实际游戏请启动新版 Java 运行包并访问 `/quest/` 或 `/training/`。本地 Phaser 与 RPG 美术已随 Java 打包，位置/HP/收藏/操作与实际存档联动。详见 [接入说明](../docs/phaser-integration.md)。

以下“回声群岛”页面属于保留的独立章节方案，并非当前 V1 正式入口。

# 回声群岛 · Phaser UI

双击 `start-ui.cmd`，或在本目录运行 `node tools/serve.mjs`，打开 http://127.0.0.1:4173/preview.html 。无需安装依赖。当前机器 npm 启动器不完整，直接使用 Node 命令即可。

- `/index.html`：正式接口入口。需要同源 Java HTTP / WebSocket 服务，该旧章节方案尚无对应服务，静态开发服务器明确返回 503。
- `/preview.html`：可交互的中文 UI 开发夹具。借用→考核→捕捉→调查→修复→救援均为预写画面状态，不是已实现的 Java 游戏逻辑或多人功能。无正式存档。
- `/reference.html`：直接引入的原版 Monster Tamer 单机，方向键、空格确认、Shift 返回。单机存档独立于产品入口。
- `node --test test/unit/*.test.mjs`：纯逻辑适配层测试（npm 环境正常时也可用 npm test）。

## 正式对接

客户端与 Java 同源部署；身份使用服务端 Cookie 会话，不接受输入玩家 ID。
GET `/api/v1/session`，POST `/api/v1/rooms`，GET `/api/v1/rooms/{roomId}/snapshot`，
POST `/api/v1/rooms/{roomId}/commands`，WS `/ws/v1/rooms/{roomId}`。

房间创建响应 `{roomId}`。命令响应 `{requestId,outcome,reasonCode?,message?}`；
相同 requestId 必须返回原结果。HTTP 响应不直接更新资产，状态从 WS 更新。
WS 首个快照由 HTTP 获取，连接期间的消息先缓冲；事件格式
`{type:'STATE',sessionId,seq,eventId,snapshot}`。当前协议使用完整状态事件，
暂不接受局部 patch。快照字段见 `test/fixtures/fixture-gateway.js` 的 reset。
可选 presentationEvents 使用稳定 eventId 与 kind；CAPTURE 只触发表现动画，
重复事件不重播，跳过或中断动画不改变资产。
这份夹具用于展示数据结构，不可拷贝为服务端规则。

服务端必须下发 allowed actions：世界 availableActions、对话 dialog.options、
战斗 encounterView.actions、伙伴/物品 actions、调查 quest.actions。
每项 `{label,action,targetId,params,expectedRevision,enabled}`；业务前置和权限
仍由 Java 校验。地图约定 chapter1/version1，64px/格；当前地图为上游参考
图，仅用于 UI，正式首章地图和碰撞数据尚待制作，不可直接作为地图验收。

## 已实现与待接入

已实现：直接引入原前端及素材、离线运行库、中文导航、房间/地图/伙伴/
背包/调查板/战斗/对白界面、输入、偏好、只读状态、真实 HTTP/WS 适配、
请求原载荷重试、消息序号检查、明确断线错误、独立流程预览。

待接入：Java 房间/存档/规则服务、首章真实地图、最终三种精灵素材、
双客户端端到端与房主冻结恢复验收。当前不宣称通过 T01–T38 或 U10。

素材与来源见 THIRD_PARTY_NOTICES.md、UPSTREAM.md。开发服务器仅监听本机。
