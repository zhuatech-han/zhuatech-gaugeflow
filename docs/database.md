# GaugeFlow · 数据库与版本迁移

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2。

## 初始化

数据库zhuatech_gaugeflow，MySQL8.4。V1__identity.sql创建部门、账号、角色、权限、角色权限关联、菜单、分类、参数和审计。V2__metrology.sql创建gauge、calibration、gauge_use、incident、impact、flow_event、command_record。

量具编号唯一，报告编号在量具内唯一，失败报告和事件一对一，事件与使用快照组合唯一，命令UUID唯一。外键保护业务历史及账号归属。量具引用最近审核合格报告，使用记录引用当时生效报告。所有业务对象有乐观version，量具与事件revision确保关联变化更新版本。时间列timestamp(6)，连接会话UTC，展示按上海时区；有效截止为DATE。

空库Bootstrap初始化总部、四角色、权限、菜单、三分类、三参数及admin。密码只从ADMIN_PASSWORD读取，BCrypt加密；已有账号即跳过初始化，不重置密码或填充业务数据。

## 升级

备份卷与忽略的环境配置，先在副本启动新版本。保留V1/V2字节，新增V3等迁移，JPA ddl-auto=validate。检查flyway_schema_history成功、健康、登录、业务及权限，再部署。

不要手动删除迁移历史、覆盖校验值或删除业务卷。应用回退不等于数据库回退，涉及不兼容迁移时使用已验证的备份恢复方案。
