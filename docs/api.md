# GaugeFlow · 接口说明

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2。

所有/api接口除/auth/csrf、/auth/login外需会话。写请求携带CSRF头。错误返回HTTP状态与code，400参数、401认证、403权限、409状态或版本冲突。

| 接口 | 行为 / 权限 |
|---|---|
| GET /auth/csrf | 匿名取得header与token |
| POST /auth/login | username/password登录，8次失败5分钟限制 |
| GET /auth/me | 当前角色、范围与授权菜单 |
| POST /auth/logout、/auth/password | 退出 / 验证旧密码修改本人密码 |
| GET /options、/workbench | gauge.read，授权目录与待办 |
| GET /gauges、/calibrations、/uses、/incidents | gauge.read；search/status/page/size/sort=newest或oldest |
| GET /{业务类型}/{id} | gauge.read及部门范围，明细和关联历史 |
| POST /gauges、PUT /gauges/{id} | gauge.manage；code/name/model/category/departmentId；更新携带version |
| DELETE /gauges/{id}?version= | gauge.manage；仅无校准引用 |
| POST /calibrations | calibration.write；gaugeId/version/reportNo/provider/checkedAt/validUntil/result/evidence/requestKey |
| POST /uses | use.write；gaugeId/version/batchRef/taskRef/product/note/requestKey |
| POST /calibrations/{id}/commands/approve或reject | calibration.review，独立复核 |
| POST /gauges/{id}/commands/release或retire | gauge.manage，恢复 / 报废 |
| POST /uses/{id}/void | gauge.manage，原记录冲销 |
| POST /impacts/{id}/commands/assign | incident.manage；assigneeId |
| POST /impacts/{id}/commands/assess | impact.assess；resolution与note，被分派本人 |
| POST /incidents/{id}/commands/submit | incident.manage；note为范围与处置汇总 |
| POST /incidents/{id}/commands/close或return | incident.review，独立复核 |
| GET /{业务类型}/{id}/report.json | export及gauge.read、部门范围，JSON下载 |
| GET /dashboard、/audit | dashboard / audit，部门过滤 |
| GET/POST/PUT/DELETE /admin/{type}[/{id}] | admin且ALL，users/roles/departments/dictionaries等目录 |

状态命令携带version、requestKey和note；assign需要assigneeId，assess需要resolution。UUID精确重试成功但响应可为replayed=true，客户端随后读取明细；不同载荷复用同一UUID冲突。校准时间为带时区ISO时间，validUntil为YYYY-MM-DD。FAIL必须没有截止日。系统不接收密码散列或任意表名。

PASS/FAIL为提交者录入的人工结论；影响处置为NO_IMPACT/RETEST_PASS/REJECTED_PRODUCT。只读状态EXPIRED从当前上海日期计算。
