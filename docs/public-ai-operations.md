# 网站公开 AI 管理

2026-10-04：单 JVM / 单容器 Demo 部署保护。使用 DeepSeek 官方 chat/completions 和 deepseek-flash；不向浏览器发送密钥，不允许玩家指定付费模型。

## 额度与限流

全站共同使用额度，不因换 Cookie、切换玩法、新一轮或重启清零。默认每日 500000、每月 2000000 Token **预留额度**，每分钟 12 次请求、同时最多 2 次请求。自然日/月使用 Australia/Sydney。单次请求 JSON 最多 32768 UTF-8 字节，输出最多 2048 Token，关闭思考模式。

调用前按 UTF-8 请求字节数 × 2 + 最大输出 + 1024 预留；先原子写入并强制刷新文件，再发出模型请求。成功请求取得有效 usage 后按实际 Token 结算；失败、缺失 usage、超时和进程中断保留全部预留。因此预留额度通常大于实际 Token，实际可体验次数依任务长度变化。超限安全停止委托，保留游戏实际进度；玩家可取消委托后手动游戏。限流恢复后可继续委托。公开评测中心仅开放规则基线。

私有账本：/home/ubuntu/pokemon-demo/agent-saves/ai-budget.json。记录日期、预留量、请求次数、已知实际输入/输出/缓存 Token，不存密钥、提示词或响应正文。损坏账本或写入失败时不允许继续付费调用。不要删除账本来恢复额度；费用记录必须保留。此保护不适用于多个应用副本并行共享密钥，扩容前需数据库锁或统一网关。

## 运维命令

查看用量：`sudo bash /home/ubuntu/pokemon-demo/ai-status.sh`。

按 DeepSeek 控制台当前每百万 Token 单价估算已知费用：`sudo bash /home/ubuntu/pokemon-demo/ai-status.sh 输入单价 缓存单价 输出单价`，三个单价使用相同币种。默认不写死价格、不把 Token 预留额度称为金额硬限额。估算不包含未拿到 usage 的超时/中断调用，账单以供应商控制台为准。

紧急关闭 AI：`sudo bash /home/ubuntu/pokemon-demo/disable-public-ai.sh`。只重建游戏容器，保留游戏存档、密钥及预算账本；不改变其他网站服务。

调整限制：修改 compose.yaml 的 AI_DAILY_TOKENS、AI_MONTHLY_TOKENS、AI_REQUESTS_PER_MINUTE、AI_CONCURRENCY，重新创建 game 容器。提高额度会提高可能费用，重启仍保留历史预留。凭据在服务器 .env.deepseek，由 Compose env_file 加载；不要运行会打印完整环境变量或 Compose 渲染配置的命令。

## 验证边界

新增 11 项额度保护测试包含频率限制、跨重启、失败扣额、每日/月额度、并发、损坏账本、模型及输出限制，全部通过。公开评测基线不发出付费请求。网站面向小规模 Demo，仍沿用最多 8 个服务会话；未增加账号体系或商业运营后台。
