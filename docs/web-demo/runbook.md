# 本地运行与发布

## 可运行包

需要 JDK / JRE 21。在发行目录运行 `java -jar pokemon-web-demo.jar`，打开 `http://localhost:8080`。Windows 可双击发行包内 `start-demo.cmd`。关闭窗口或 Ctrl+C 停止。

当前局保存在进程内存中；网页刷新不会重置，重启服务会丢失。30 分钟无交互过期；100 局、每局 5,000 动作、每身份每秒 5 次动作请求。重新开始清空自己的背包与进度。

## 从源码构建

```sh
node tools/export-map.mjs
npm ci --prefix web-client
npm test --prefix web-client
npm run lint --prefix web-client
npm run build --prefix web-client
mvn clean verify
java -jar target/fit2099-pokemon-assignment-1.0.0.jar
```

工具版本：Node 24.18.0、Maven 3.9.11、JDK 21。源码保持 Java 8 语法；运行与测试统一使用 JDK 21。前端产物复制入 `resources/public`，只从资源清单提供文件。

终端版仍可通过 `java -cp target/fit2099-pokemon-assignment-1.0.0.jar game.Application` 运行，与网页共享 `GameSession`。

开发前端：先运行后端，再 `npm run dev --prefix web-client`。Vite 将 API 代理到 8080 并设置对应 Origin。

## 测试

```sh
npm exec --prefix web-client playwright install chrome
npm run e2e --prefix web-client
node tools/load-test.mjs
```

E2E 使用真实服务，无测试作弊接口。默认 localhost:8080；其他地址通过 `TEST_BASE_URL` 设置。需要本机 Chrome；可设置 `PLAYWRIGHT_CHANNEL=msedge`。

## Docker

```sh
docker build -t pokemon-web-demo:local .
docker run --rm -p 127.0.0.1:8080:8080 -e PUBLIC_ORIGIN=http://localhost:8080 pokemon-web-demo:local
```

容器以非 root 用户运行。实际公网发布应使用同域 HTTPS 反向代理：`PUBLIC_ORIGIN=https://你的域名`、`SECURE_COOKIE=true`、`PORT=8080`。Origin 需完整精确匹配，不带尾部 `/`。健康检查 `GET /health`。服务同时提供网页与 API。

禁止直接复制扩大为多个无状态副本：游戏状态在单个进程中。代理需设置请求超时、请求体限制和资源限制；先保留单实例，按部署环境设定 JVM 堆上限及监控。日志含 requestId/gameId/revision，不输出身份 Cookie。

托管平台、域名和费用尚未指定；开发阶段只创建本机镜像与服务，未执行公网部署、购买或推送。

## 发布和回滚

先在目标环境检查首页、/health、新局、等待、刷新、双访客、重复请求、Secure Cookie 和 HTTPS Origin。记录镜像摘要与配置，保留上一镜像标签。回滚时切回旧镜像与匹配配置；重启后旧内存局会失效，前端提示重新开始，不能承诺存档恢复。

## 已知限制

- 桌面键盘/鼠标优先，小屏采用上下布局；最小占位美术。
- 仅单人内存局，没有账号、永久存档、多人、召唤/收回、跟随或高级球使用策略。
- 原概率与伤害节奏保持；环境会持续生长，长局地图可能拥挤。
- Phaser 主包约 1.4 MB（gzip 约 366 KB），Vite 提示大 chunk；可玩 demo 可用，后续可按网络目标拆包优化。
- 当前动作/引擎日志保留部分原英文；按钮、状态、说明和错误提示为中文。
