腾讯云现有网站更新包（2026-10-04）

适用已确认环境：Ubuntu，pokemon-web-game，Caddy反代pokemon-game:8080，外部网络morris-demo_default，工作目录/home/ubuntu/pokemon-demo。
本包是新版Java伙伴冒险，包含地图、成长、捕捉与委托页面。AI默认关闭，不包含任何API密钥、玩家存档或数据库密码。

上传与运行：
1. 将pokemon-tencent-update-20261004.tar.gz上传到/home/ubuntu。
2. mkdir -p /home/ubuntu/pokemon-demo/agent-update-20261004
3. tar -xzf /home/ubuntu/pokemon-tencent-update-20261004.tar.gz -C /home/ubuntu/pokemon-demo/agent-update-20261004
4. sudo bash /home/ubuntu/pokemon-demo/agent-update-20261004/update.sh
5. 等待SUCCESS，打开https://pokemon.hanzhu-lab.online/growth/，Ctrl+F5刷新。

脚本先验证包哈希、Docker配置、构建镜像并临时启动验证。通过后才停止旧游戏、备份旧compose、将停止的旧容器提交到本地备份镜像，再替换游戏。Caddy及morris服务不修改、不重启。启动后验证公网/growth/页面。腾讯云Docker构建和公网验收必须以服务器实际执行结果为准；本地未模拟腾讯云Docker环境。
备份位置会打印在屏幕：/home/ubuntu/pokemon-demo/backups/agent-update-时间戳。
如果切换后失败，脚本打印回滚命令，请照抄执行；回滚使用已提交的旧镜像和旧配置，保留新版agent-saves。不要删除本地备份镜像，镜像可能包含旧版私有配置，仅留在本服务器。
新版存档：/home/ubuntu/pokemon-demo/agent-saves（容器非root UID10001）。原线上旧版存档未验证与新引擎兼容，保留在旧镜像备份中，不自动导入。玩家身份依赖浏览器Cookie，清除Cookie会产生新身份。

验收：167项前端测试、7项WebDeployment/AgentWebServer后端测试通过；生产Java8兼容JAR验证HTTPS Host/Origin、Secure Cookie、选择水跃鱼、关闭/重启后恢复同一个体通过；两个Linux脚本bash -n通过。未调用真实AI模型。
范围：少量访客的手动Demo上线。当前引擎每次服务运行最多8个浏览器会话，尚无账号登录与闲置会话回收；达到上限返回SESSION_LIMIT。大量公开访问需要后续会话管理。公开AI暂不开启；启用前应先落实访问限制与费用预算。
