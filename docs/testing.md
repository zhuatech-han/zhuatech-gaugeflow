# GaugeFlow · 验收方法

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2。

## 自动门禁

前端npm ci、format:check、lint、test、build。后端受控环境注入TEST_ADMIN_PASSWORD后mvn spotless:check test。8项日期规则、26项接口集成与11项前端测试，共45项；失败必须修复复测。Docker镜像内再执行完整门禁，不跳过测试。

测试时钟只在JUnit配置中，生产无修改日期接口。H2执行两项Flyway迁移并JPA校验；不能用H2替代真实MySQL验收。

## 全新MySQL

使用独立gaugeflow-check项目、全新mysql-data卷，Compose config/build/up --wait。匿名健康、前端首页、admin登录，然后scripts/smoke.py --run完成合格报告审核、使用、失败冻结、影响分派与评估、退回复核、关闭、新报告审核和恢复。

脚本同时验证冻结登记拒绝、部门与管理接口越权拒绝、精确重试、JSON导出。核对flyway_schema_history V1/V2成功及表结构。依次重启MySQL及后端，再scripts/smoke.py --verify核查报告、事件、评估、使用记录保留。

## 页面及发布

实际浏览器登录、台账增改删、报告审核、影响评估、状态反馈及移动布局检查；六类当前页面截图入docs/screenshots。release-check.py扫描敏感模式、源码署名、LICENSE、README图片和原始LOGO/二维码哈希，人工检查对外文案与差异。

全部通过后提交发布，核对各远程公开状态与main SHA、README图片。完成后仅删除本次测试容器、卷与私有验收配置。
