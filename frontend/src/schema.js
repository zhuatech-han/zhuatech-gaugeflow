// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
export const states = {
  UNQUALIFIED: "未启用",
  AVAILABLE: "有效",
  EXPIRED: "已过期",
  QUARANTINED: "已冻结",
  RETIRED: "已报废",
  PENDING: "待处理",
  APPROVED: "已审核",
  REJECTED: "已拒绝",
  PASS: "合格",
  FAIL: "不合格",
  ACTIVE: "有效记录",
  VOID: "已冲销",
  OPEN: "评估中",
  REVIEW: "待复核",
  CLOSED: "已关闭",
  NO_IMPACT: "无影响",
  RETEST_PASS: "复检通过",
  REJECTED_PRODUCT: "产品拒收 / 处置",
};
export const labels = {
  code: "编号",
  name: "名称",
  model: "规格 / 型号",
  category: "量具分类",
  departmentId: "所属部门",
  gaugeId: "量具",
  reportNo: "报告编号",
  provider: "校准机构 / 人员",
  checkedAt: "校准时间（上海时区）",
  validUntil: "有效截止日期",
  result: "校准结论",
  evidence: "报告数据摘要 / 证据引用",
  batchRef: "产品批次",
  taskRef: "检验任务编号",
  product: "产品名称",
  note: "操作说明 / 评估证据",
  assigneeId: "评估责任人",
  resolution: "处置结论",
  username: "登录账号",
  displayName: "显示名称",
  password: "初始 / 重置密码",
  roleId: "角色",
  enabled: "启用",
  scope: "数据范围",
  permissions: "权限",
  nameEn: "英文名称",
  permissionCode: "菜单所需权限",
  position: "排序",
  type: "字典类型",
  value: "参数值",
  oldPassword: "当前密码",
  newPassword: "新密码",
};
export const commands = {
  approve: "审核通过",
  reject: "拒绝报告",
  assign: "分派评估",
  assess: "记录评估",
  submit: "提交复核",
  close: "复核关闭",
  return: "退回评估",
  release: "恢复使用",
  retire: "报废",
  void: "冲销记录",
};
export const errors = {
  UNAUTHENTICATED: "会话失效，请重新登录",
  LOGIN_FAILED: "账号或密码错误，或账号已停用",
  LOGIN_THROTTLED: "请五分钟后重试",
  FORBIDDEN: "没有操作权限",
  OUT_OF_SCOPE: "没有此部门的数据权限",
  INDEPENDENT_REVIEW: "提交者及本事件评估者不能复核自己的记录",
  STALE_VERSION: "记录已更新，请刷新后操作",
  INVALID_STATE: "当前状态不支持该操作",
  GAUGE_UNAVAILABLE: "量具未启用、已冻结、过期或报废，不能使用",
  PENDING_CALIBRATION: "该量具有待审核报告，请先完成审核",
  INVALID_CALIBRATION_TIME: "校准时间不能在未来，且须晚于该量具前一份报告",
  INVALID_VALIDITY: "合格报告须填写不早于校准日的截止日期；不合格报告不填写",
  INVALID_INPUT: "请检查必填项、文本长度和输入格式",
  INCOMPLETE_IMPACT: "全部影响记录完成评估后才能提交",
  NOT_ASSIGNEE: "只有被分派的责任人可以评估",
  INVALID_ASSIGNEE: "责任人须启用、具备评估权限并能查看此部门",
  OPEN_INCIDENT: "请先关闭全部影响事件，并处理待审核报告",
  RECOVERY_CALIBRATION_REQUIRED: "需要失败后重新校准且审核合格的有效报告",
  HISTORY_PROTECTED: "已有业务历史，编号、分类与归属须保留",
  CONFLICT: "编号重复或记录被业务引用",
  INVALID_REQUEST_KEY: "请重新打开操作",
  IDEMPOTENCY_CONFLICT: "重试内容不同，请重新打开操作",
  WEAK_PASSWORD: "密码至少12位，含大写、小写及数字，UTF-8不超过72字节",
  LAST_ADMIN: "须保留一个启用的全范围管理员",
  OLD_PASSWORD_INVALID: "当前密码错误",
  BUILTIN_RESOURCE: "内建资源不可删除或改编号",
  INVALID_SETTING: "时区固定；到期提醒天数为0–90",
  INVALID_DICTIONARY: "量具分类不存在",
  REPORT_LIMIT: "超过一万条记录，请缩小数据范围",
  NOT_FOUND: "记录不存在",
  NETWORK_ERROR: "网络连接失败，请重试",
};
/** 上海时区显示时间，不受浏览器时区影响。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function date(v) {
  if (!v) return "—";
  if (/^\d{4}-\d{2}-\d{2}$/.test(v)) return v;
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(v));
}
/** UTC转换为上海表单时间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function localInput(v) {
  return v
    ? new Date(new Date(v).getTime() + 8 * 3600000).toISOString().slice(0, 16)
    : "";
}
/** 严格解析上海表单时间，避免不存在的日期被自动归一。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function instant(v) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(v))
    throw new Error("INVALID_CALIBRATION_TIME");
  const d = new Date(v + ":00+08:00");
  if (!Number.isFinite(d.getTime()) || localInput(d.toISOString()) !== v)
    throw new Error("INVALID_CALIBRATION_TIME");
  return d.toISOString();
}
/** 显示授权状态入口，服务器再次验证独立复核与数据范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function actions(kind, r, me, impacts = []) {
  if (!r || !me) return [];
  const p = me.permissions,
    has = (s) => p.includes(s);
  if (me.scope !== "ALL" && me.departmentId !== r.departmentId) return [];
  if (kind === "gauges" && has("gauge.manage"))
    return [
      ...(r.status === "QUARANTINED" ? ["release"] : []),
      ...(r.status !== "RETIRED" ? ["retire"] : []),
    ];
  if (
    kind === "calibrations" &&
    r.status === "PENDING" &&
    has("calibration.review") &&
    r.creatorId !== me.id
  )
    return ["approve", "reject"];
  if (kind === "uses" && r.status === "ACTIVE" && has("gauge.manage"))
    return ["void"];
  if (kind === "incidents") {
    if (r.status === "OPEN" && has("incident.manage")) return ["submit"];
    if (
      r.status === "REVIEW" &&
      has("incident.review") &&
      r.creatorId !== me.id &&
      !impacts.some((x) => x.impact.assessorId === me.id)
    )
      return ["close", "return"];
  }
  return [];
}
