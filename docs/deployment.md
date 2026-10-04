# GaugeFlow · 部署、备份与恢复

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2。

## 启动与端口

```sh
python3 scripts/init-env.py
docker compose -p gaugeflow-local config --quiet
docker compose -p gaugeflow-local up -d --build --wait
```

配置脚本拒绝覆盖.env，文件权限0600。默认前端http://127.0.0.1:8105，/actuator/health为UP表示后端健康。MySQL和后端不发布宿主端口；前端通过服务名backend代理。

端口冲突时在本项目.env修改WEB_PORT，不停止其他项目。前端非root Nginx监听8080，后端等待MySQL健康，前端等待后端健康。镜像构建执行后端格式检查与完整测试、前端全部检查，BuildKit共享锁缓存Maven依赖并对下载重试。

## 配置与会话

DATABASE_PASSWORD、MYSQL_ROOT_PASSWORD和ADMIN_PASSWORD分别设置独立强密码。admin仅空库初始化；修改环境变量不重置账号。COOKIE_SECURE本地false，HTTPS时true。DATABASE_URL、DATABASE_USER可用于源码调试连接专用数据库，见application.yml。

会话内存存储，重启后重新登录；MySQL业务保存在项目mysql-data卷。重启数据库后先等待健康，再重启后端，避免健康检查和连接暂时失败。

```sh
docker compose -p gaugeflow-local restart mysql
docker compose -p gaugeflow-local up -d --wait mysql
docker compose -p gaugeflow-local restart backend
docker compose -p gaugeflow-local up -d --wait
```

## 备份与清理

升级前用受控数据库工具导出完整库，并保护.env。备份需验证可恢复、迁移一致且业务完整，不能只备份应用文件。恢复在独立项目和卷执行，再验证健康、登录、报告、使用、影响事件和权限。

测试专用卷完成验收后才可执行下列清理；禁止用于业务数据库：

```sh
docker compose -p gaugeflow-check down -v
```

只清理该项目资源；不使用docker system prune或删除其他卷。应用回退不能自动撤销数据库迁移。
