# GaugeFlow · 知华科技计量器具校准与超差追溯

<img src="frontend/public/brand/logo.jpg" width="86" alt="知华科技">

**公开源码学习版 0.1.0** · 上海如静知华信息科技有限公司 · [知华科技官网](https://www.zhuatech.cn/) · 商业咨询微信 **zhuatech / zhuatech2**。

量具校准发现不合格时，除了停止使用，还需要找出曾经用它检验的产品与任务，并记录影响判断和复检结论。GaugeFlow 为制造企业计量人员、检验人员和质量复核人员提供这条协作流程。

自有源码仅限个人学习、技术研究与非商业交流，未经书面授权不得商用。许可正文见 [LICENSE](LICENSE)，第三方组件许可独立保留。

## 一条可追溯的计量流程

```text
量具建档 → 合格校准提交 → 独立审核 → 当前使用登记
                              ↓
不合格报告登记 → 立即冻结 + 使用快照 → 分派评估 → 提交复核
                                              ↑        ↓
                                              └─ 退回 ─┤
                                                       ↓
                                                   独立关闭
                                                       +
                                      失败后重新校准、审核合格
                                                       ↓
                                             单独审批恢复使用
```

使用记录保存产品、批次、检验任务、人员、服务端时间以及当时有效的报告。报告提交后事实不再修改；审核通过之前暂停登记使用。不合格报告即使被拒绝，冻结和追溯仍然保留。

追溯窗口从上次审核合格的校准时间开始，到失败报告登记时结束，包含校准完成至报告登记之间的使用，也包含被冲销的原始记录。没有历史合格报告时，从建档时间开始；这只覆盖本系统记录，提交时需要补充系统外记录核查和范围说明。系统列出的记录是**潜在受影响记录**，不自动认定产品不合格。

| 模块 | 已实现能力 |
|---|---|
| 量具台账 | 分类、编号、型号、部门、搜索筛选分页排序；无校准引用时可删除；有历史后保护编号、分类和归属；报废保留历史 |
| 校准审核 | 报告编号、机构、校准时间、合格结论、有效截止日、文本证据引用；独立通过或拒绝；报告时间严格递增 |
| 使用记录 | 本人登记当前使用；无有效报告、冻结、过期、报废或待审报告时拒绝登记；错录可带原因冲销 |
| 超差追溯 | 自动快照、分派责任人、无影响/复检通过/产品拒收处置、证据说明、提交/退回/独立关闭 |
| 恢复使用 | 所有事件关闭，并有失败后重新校准、独立审核且未过期的合格报告，再执行恢复 |
| 工作台及统计 | 本人待评估、待审报告、待复核事件、异常状态量具；有效性分布、到期窗口及使用统计 |
| 系统管理 | 账号、角色、接口权限、部门数据范围、内建菜单配置、分类字典、系统参数、操作审计、本人修改密码 |
| 报告 | 授权明细 JSON 下载，包含关联证据与事件，不插入广告或客户信息 |

### 使用人员与管理人员

- **使用人员**：读取本部门资料，登记本人当前使用，查看部门统计。
- **计量员**：维护本部门量具、提交校准、分派和评估影响记录、提交复核、恢复或报废。
- **质量复核员**：审核本部门报告和影响事件，评估分派给自己的记录。报告提交者不能自审，事件创建者及任何该事件评估者不能关闭或退回该事件。
- **管理员**：全部部门及系统目录。具备全部权限仍需遵守独立复核规则。系统目录操作要求全范围管理员，防止本部门角色修改全局账号。

首版适合有明确责任分工的小规模计量协作。尚未实现证书附件上传、电子签章、校准机构在线接口、测量不确定度计算、MSA、自动仪器采集、短信邮件通知、多租户与 ERP/MES 联动。证据目前为文本摘要及引用号，不提供文件下载或虚构证书。软件冻结只限制 GaugeFlow 使用登记；现场隔离和其他系统的使用控制需由责任人员落实。没有业务第三方服务或付费模型依赖。

## 运行界面

### 登录入口
![登录页面](docs/screenshots/login.jpg)

### 使用人员工作台
![用户端工作台](docs/screenshots/workbench.jpg)

### 量具台账
![量具业务页面](docs/screenshots/gauges.jpg)

### 超差影响记录
![超差追溯页面](docs/screenshots/incident.jpg)

### 管理端角色权限
![后台与权限页面](docs/screenshots/roles.jpg)

### 授权范围统计
![统计仪表盘](docs/screenshots/dashboard.jpg)

截图中“验收测试”资料仅用于展示操作流程，不代表真实产品、校准机构或检测结论；空库不初始化量具和使用记录。

## 工程与运行要求

```text
浏览器 → Nginx :8080 → Spring Boot :8080 → MySQL 8.4
                    同源 /api           Flyway + JPA
```

| 层 | 运行版本 |
|---|---|
| 后端 | Java 21、Maven 3.9、Spring Boot 4.0.7、Security、Data JPA、Flyway；MariaDB JDBC 3.5.10 连接 MySQL |
| 前端 | Node.js 24.19.0+、Vue 3.5.40、Vite 8.1.5、Lucide 1.48.0、ESLint、Prettier |
| 部署 | Docker Engine、Compose v2、BuildKit、MySQL 8.4、Nginx 1.29 |
| 测试 | JUnit、MockMvc、H2 MySQL 模式；真实 MySQL 隔离验收；Node 内置测试 |

```text
backend/             认证、系统管理、计量规则及事务
  src/main/resources/db/migration/  V1身份目录、V2计量业务
  src/test/          日期规则与接口事务测试
frontend/            Vue操作页面及请求、状态入口测试
compose.yaml         独立MySQL卷与服务健康依赖
scripts/             安全配置初始化、隔离业务验收、发布扫描
docs/                操作、接口、数据库、架构、安全、部署与测试
```

所有写操作采用基础部门行锁和 READ COMMITTED，锁后刷新当前账号角色。此方式串行处理变更，用于小规模学习部署，没有高并发承诺。业务版本防止旧页面覆盖；UUID命令键绑定账号和完整载荷，精确重试不重复创建使用、报告或事件。核心列表在数据库内执行过滤、分页和固定排序，目录、统计及下拉数据上限一万条。详见 [架构说明](docs/architecture.md)。

## 第一次启动

需要 Docker、Compose v2 和 Python 3。默认账号 **admin**，密码来自 `ADMIN_PASSWORD`，没有固定演示密码。配置脚本生成三个独立强密码，保存在被忽略的本地 `.env` 中，不覆盖已有文件。

```sh
python3 scripts/init-env.py
docker compose -p gaugeflow-local config --quiet
docker compose -p gaugeflow-local up -d --build --wait
```

访问 [http://127.0.0.1:8105](http://127.0.0.1:8105)，从本地 `.env` 读取管理员密码。空库先执行 V1/V2，再初始化总部、四个角色、权限、菜单、三种量具分类、三项参数和管理员。重启不重置业务或密码。

先创建实际部门、计量员、使用人员和独立复核员，再建档量具并录入真实校准事实。[操作手册](docs/operations.md) 包含首次启用及超差事件处理步骤。

### 配置、源码调试及升级

[.env.example](.env.example) 仅提供变量名与说明。

| 变量 | 用途 |
|---|---|
| DATABASE_PASSWORD | 专用 gaugeflow 数据库用户密码 |
| MYSQL_ROOT_PASSWORD | MySQL 初始化管理密码 |
| ADMIN_PASSWORD | 仅空库初始化管理员使用，不用于重置已有账号 |
| WEB_PORT | 默认 8105，可覆盖以避免其他项目占用 |
| BIND_ADDRESS | 默认 127.0.0.1；对外访问先配置 HTTPS 入口 |
| COOKIE_SECURE | 本地 HTTP 为 false；HTTPS 部署使用 true |

源码调试在 `backend/` 执行 `mvn spring-boot:run`，通过环境变量 `DATABASE_URL`、`DATABASE_USER`、`DATABASE_PASSWORD` 和 `ADMIN_PASSWORD` 指向专用测试数据库。默认数据库容器不发布宿主端口，macOS/Windows 推荐完整 Compose。在 `frontend/` 执行 `npm ci`、`npm run dev`，开发服务器仅绑定本机，代理后端 `127.0.0.1:8080`。

数据库名 `zhuatech_gaugeflow`，结构使用 Flyway；JPA仅校验，不自动建表。V1身份目录与V2业务实体有外键、索引和唯一约束。升级先备份、在副本验证，再新增 V3 等迁移，不能修改已执行文件或删除业务卷代替升级。[数据库说明](docs/database.md) 与 [部署手册](docs/deployment.md) 说明版本和恢复方法。

## 验证、故障与安全

```sh
cd frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
cd ../backend
# 从受控环境注入 TEST_ADMIN_PASSWORD：12位以上，大写/小写/数字
mvn spotless:check test
cd ..
docker compose -p gaugeflow-check config --quiet
docker compose -p gaugeflow-check up -d --build --wait
python3 scripts/smoke.py --run
# 重启本项目数据库，等待健康，再重启后端
python3 scripts/smoke.py --verify
python3 scripts/release-check.py
git diff --check
```

后端有 8 项日期规则单元测试、26 项接口集成测试，前端有 11 项测试，共 45 项。覆盖完整追溯恢复、拒绝后保持冻结、过期、待审阻断、回溯窗口、冲销历史、自审、责任人、部门隔离、旧版本、精确重试和使用与冻结并发。镜像构建执行测试，不跳过门禁。[验收说明](docs/testing.md) 包含真实 MySQL 与页面检查方法。

隔离脚本只允许本机地址，需要全新测试数据库，会创建明确标识为“验收测试”的账号与资料。`.smoke-state.json` 保存私有验收凭证并被忽略；不要对业务数据库执行。

| 现象 | 处理 |
|---|---|
| 启动失败 | 检查本项目端口、变量及服务日志，不公开凭证 |
| 登录失败 | 核对空库初始化密码；重启后内存会话失效 |
| 报告被拒绝 | 原事实保留，补交时间递增的新报告；失败报告的冻结事件仍须处理 |
| 使用被拒绝 | 检查量具当前有效性与待审报告 |
| 无法提交追溯 | 全部记录须评估并附证据，范围说明不得为空 |
| 无法复核 | 需由事件创建者及全部评估者之外的授权人员操作 |
| 无法恢复 | 全部事件关闭，重新校准且独立审核合格、未过期，无待审报告 |
| 版本冲突 | 刷新并重新打开操作；不要用相同命令键提交不同内容 |

会话 Cookie 为 HttpOnly、SameSite Strict，写接口有CSRF防护，密码BCrypt 12轮。角色、部门范围和账号启用状态在服务端验证；停用和密码重置使旧会话失效。JSON导出沿用明细权限。配置、密钥、客户数据和未脱敏日志不得进入源码仓库。详见 [安全说明](docs/security.md)。

提交贡献前执行格式、检查、测试和构建。功能反馈使用仓库 Issues，附版本和脱敏复现步骤；安全漏洞通过官网或咨询微信联系后私下提供最小复现，不在公开Issue粘贴密钥或攻击载荷。第三方许可见 [docs/licenses](docs/licenses) 与前端静态许可目录。

本项目用于学习交流，不出具校准证书，不自动判断产品质量或替代专业人员的计量、影响评估和现场处置；实际部署需完成业务验证、容量评估、备份和安全审查。

## 联系知华科技

本项目由知华科技（上海如静知华信息科技有限公司）提供公开源码学习版本，主要用于个人学习、技术研究与非商业交流。未经书面授权不得商用。企业信息化建设、中小企业数字化转型、中小企业 AI 转型、私有化部署、软件外包、软件项目外包、软件实施、FDE 外包、OPC 技术支持及深度定制开发，请访问知华科技官网 [https://www.zhuatech.cn/](https://www.zhuatech.cn/)，或添加微信 zhuatech、zhuatech2 咨询。

- 官网：[https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- 商业授权、定制开发、部署与系统集成咨询微信：**zhuatech**、**zhuatech2**。
- 自有代码许可与品牌联系文案分别以 LICENSE 和本章节为准。

| 微信 zhuatech | 微信 zhuatech2 |
|---|---|
| ![微信 zhuatech](docs/images/wechat-zhuatech.png) | ![微信 zhuatech2](docs/images/wechat-zhuatech2.png) |
