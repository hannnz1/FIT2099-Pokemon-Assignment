# HTTP 契约

同源 Cookie 身份，HttpOnly、SameSite=Lax；生产 Secure 和 `__Host-pokemon`。变更操作严格比较 Origin；API 响应 no-store。JSON 上限 16 KiB。

| 方法 | 路径 | 结果 |
|---|---|---|
| POST | /api/games | `{}` 创建 201 / 返回已有局 200 |
| GET | /api/games/current | 当前完整快照 |
| GET | /api/games/{id} | 自己局的完整快照 |
| POST | /api/games/{id}/actions | 执行快照提供的 actionId |
| DELETE | /api/games/{id} | 幂等结束自己的局，204 |
| GET | /health | `{"status":"ok"}` |

动作请求：`{"requestId":"UUID","expectedRevision":0,"actionId":"0:8"}`。actionId 是随 revision 失效的不透明能力标识，不得由客户端拼装移动目标或修改道具数据。CONTINUE 存在时仅该动作可用。

动作结果含 `requestId,outcome,appliedRevision,replayed,snapshot,events`。原请求重试先查记录，再查 revision；相同 ID 不同载荷返回 409。重放保留原 appliedRevision、返回最新 snapshot，events 为空，不再执行游戏规则。

快照字段及事件 DTO 见 `src/game/web/dto` 和 `web-client/src/api/types.ts`。角色、地形、地面物品、背包分层；角色 ID 在移动、捕获、球内与丢弃后保持。前端以 kind/ID 驱动逻辑，不解析英文日志。

错误：400 格式；403 Origin；404 不存在/非所属；409 请求冲突；410 已知过期；413 请求过大；415 非 JSON；422 动作失效/已结束；429 限流或动作上限；503 容量；500 未预期异常。游戏执行异常将局标记 ENDED，用户需要重开；不会声称事务回滚。

前端状态：READY → REQUESTING → ANIMATING → READY；ERROR 保留未确定结果请求，重试沿用 requestId；409 同步最新状态；旧 revision 响应不覆盖当前状态。动画、菜单、同步、刷新都不发出游戏动作。
