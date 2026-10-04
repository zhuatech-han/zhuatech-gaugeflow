<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import {
  Gauge,
  LogOut,
  Plus,
  RefreshCw,
  X,
  ChevronLeft,
  ChevronRight,
  ArrowUpRight,
  ShieldCheck,
} from "@lucide/vue";
import { api, resetCsrf } from "./api.js";
import {
  states,
  labels,
  commands,
  errors,
  date,
  localInput,
  instant,
  actions,
} from "./schema.js";
const me = ref(null),
  login = ref({ username: "", password: "" }),
  page = ref("workbench"),
  loading = ref(false),
  saving = ref(false),
  error = ref(""),
  notice = ref(""),
  options = ref({
    gauges: [],
    accounts: [],
    departments: [],
    dictionaries: [],
    settings: [],
  }),
  rows = ref([]),
  total = ref(0),
  offset = ref(0),
  search = ref(""),
  status = ref(""),
  sort = ref("newest"),
  detail = ref(null),
  detailKind = ref("gauges"),
  dialog = ref(null),
  stats = ref({}),
  work = ref({ tasks: [], pendingReports: [], reviews: [], gauges: [] }),
  roles = ref([]),
  permissions = ref([]);
const businessPages = ["gauges", "calibrations", "uses", "incidents"];
const can = (p) => me.value?.permissions.includes(p),
  record = computed(() => detail.value?.record),
  title = computed(
    () =>
      me.value?.menus.find((m) => m.code === page.value)?.name || "计量工作台",
  );
const accountName = (id) =>
  options.value.accounts.find((a) => a.id === id)?.name || "#" + id;
const departmentName = (id) =>
  options.value.departments.find((a) => a.id === id)?.name || "#" + id;
const gaugeName = (id) => {
  const g = options.value.gauges.find((a) => a.id === id);
  return g ? g.code + " · " + g.name : "#" + id;
};
const adminFiltered = computed(() =>
    rows.value.filter((r) =>
      JSON.stringify(r).toLowerCase().includes(search.value.toLowerCase()),
    ),
  ),
  visibleRows = computed(() =>
    businessPages.includes(page.value)
      ? rows.value
      : adminFiltered.value.slice(offset.value * 20, offset.value * 20 + 20),
  ),
  pageTotal = computed(() =>
    businessPages.includes(page.value)
      ? total.value
      : adminFiltered.value.length,
  );
const field = (key, type = "text", extra = {}) => ({ key, type, ...extra }),
  choices = (items, label = "name", value = "id") =>
    items.map((a) => ({ label: a[label], value: a[value] }));
const fields = {
  gauges: () => [
    field("code"),
    field("name"),
    field("model"),
    field("category", "select", {
      options: choices(
        options.value.dictionaries.filter((d) => d.type === "gauge"),
        "name",
        "code",
      ),
    }),
    field("departmentId", "select", {
      options: choices(options.value.departments),
    }),
  ],
  calibrations: () => [
    field("gaugeId", "select", {
      options: options.value.gauges
        .filter((g) => g.status !== "RETIRED")
        .map((g) => ({ value: g.id, label: g.code + " · " + g.name })),
    }),
    field("reportNo"),
    field("provider"),
    field("checkedAt", "datetime-local"),
    field("result", "select", {
      options: [
        { value: "PASS", label: "合格" },
        { value: "FAIL", label: "不合格" },
      ],
    }),
    field("validUntil", "date", { required: false }),
    field("evidence", "textarea", { max: 4000 }),
  ],
  uses: () => [
    field("gaugeId", "select", {
      options: options.value.gauges
        .filter((g) => g.status === "AVAILABLE")
        .map((g) => ({ value: g.id, label: g.code + " · " + g.name })),
    }),
    field("batchRef"),
    field("taskRef"),
    field("product"),
    field("note", "textarea"),
  ],
  users: () => [
    field("username"),
    field("displayName"),
    field("password", "password", { required: !dialog.value?.id }),
    field("roleId", "select", { options: choices(roles.value) }),
    field("departmentId", "select", {
      options: choices(options.value.departments),
    }),
    field("enabled", "checkbox"),
  ],
  roles: () => [
    field("name"),
    field("scope", "select", {
      options: [
        { value: "ALL", label: "全部部门" },
        { value: "DEPARTMENT", label: "本部门" },
      ],
    }),
    field("permissions", "permissions", {
      options: choices(permissions.value, "name", "code"),
    }),
  ],
  departments: () => [field("name")],
  menus: () => [
    field("code", "readonly"),
    field("name"),
    field("nameEn"),
    field("permissionCode", "select", {
      options: choices(permissions.value, "name", "code"),
    }),
    field("position", "number"),
    field("enabled", "checkbox"),
  ],
  permissions: () => [field("code", "readonly"), field("name")],
  dictionaries: () => [
    field("type"),
    field("code"),
    field("name"),
    field("nameEn"),
  ],
  settings: () => [field("code", "readonly"), field("value")],
  password: () => [
    field("oldPassword", "password"),
    field("newPassword", "password"),
  ],
};
const dialogFields = computed(() => {
  if (!dialog.value) return [];
  if (dialog.value.action) {
    const a = dialog.value.action;
    return [
      ...(a === "assign"
        ? [
            field("assigneeId", "select", {
              options: options.value.accounts
                .filter(
                  (u) =>
                    u.enabled &&
                    u.permissions.includes("impact.assess") &&
                    (u.departmentId === record.value.departmentId ||
                      u.scope === "ALL"),
                )
                .map((u) => ({ value: u.id, label: u.name })),
            }),
          ]
        : []),
      ...(a === "assess"
        ? [
            field("resolution", "select", {
              options: ["NO_IMPACT", "RETEST_PASS", "REJECTED_PRODUCT"].map(
                (s) => ({ value: s, label: states[s] }),
              ),
            }),
          ]
        : []),
      ...(a !== "assign"
        ? [field("note", "textarea", { max: a === "assess" ? 4000 : 2000 })]
        : []),
    ];
  }
  return fields[dialog.value.kind]?.() || [];
});
const statuses = computed(
  () =>
    ({
      gauges: ["UNQUALIFIED", "AVAILABLE", "EXPIRED", "QUARANTINED", "RETIRED"],
      calibrations: ["PENDING", "APPROVED", "REJECTED"],
      uses: ["ACTIVE", "VOID"],
      incidents: ["OPEN", "REVIEW", "CLOSED"],
    })[page.value] || [],
);
/** 会话结束时清空当前账号的工作区，避免下次登录显示旧待办。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function clearWorkspace() {
  me.value = null;
  detail.value = null;
  dialog.value = null;
  rows.value = [];
  stats.value = {};
  roles.value = [];
  permissions.value = [];
  work.value = { tasks: [], pendingReports: [], reviews: [], gauges: [] };
  options.value = {
    gauges: [],
    accounts: [],
    departments: [],
    dictionaries: [],
    settings: [],
  };
  page.value = "workbench";
  search.value = "";
  status.value = "";
  offset.value = 0;
  notice.value = "";
}
function fail(e) {
  error.value = errors[e.message] || "操作未完成，请检查输入后重试";
  if (e.message === "UNAUTHENTICATED") {
    clearWorkspace();
  }
}
/** 每次读取先刷新菜单权限，明细与列表复用数据范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function load() {
  if (!me.value) return;
  loading.value = true;
  error.value = "";
  try {
    me.value = await api("/auth/me");
    options.value = await api("/options");
    if (can("admin")) {
      roles.value = await api("/admin/roles");
      permissions.value = await api("/admin/permissions");
    }
    if (!me.value.menus.some((m) => m.code === page.value))
      page.value = me.value.menus[0]?.code || "workbench";
    if (detail.value) {
      detail.value = await api("/" + detailKind.value + "/" + record.value.id);
      return;
    }
    if (businessPages.includes(page.value)) {
      const v = await api(
        "/" +
          page.value +
          "?" +
          new URLSearchParams({
            search: search.value,
            status: status.value,
            page: offset.value,
            size: 20,
            sort: sort.value,
          }),
      );
      rows.value = v.items;
      total.value = v.total;
    } else if (page.value === "workbench") work.value = await api("/workbench");
    else if (page.value === "dashboard") stats.value = await api("/dashboard");
    else if (page.value === "audit") rows.value = await api("/audit");
    else rows.value = await api("/admin/" + page.value);
  } catch (e) {
    fail(e);
  } finally {
    loading.value = false;
  }
}
async function signIn() {
  saving.value = true;
  error.value = "";
  try {
    resetCsrf();
    me.value = await api("/auth/login", "POST", login.value);
    login.value.password = "";
    await load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function signOut() {
  try {
    await api("/auth/logout", "POST");
  } catch (e) {
    fail(e);
  } finally {
    resetCsrf();
    clearWorkspace();
  }
}
async function navigate(code) {
  if (loading.value || saving.value) return;
  page.value = code;
  detail.value = null;
  rows.value = [];
  total.value = 0;
  search.value = "";
  status.value = "";
  offset.value = 0;
  notice.value = "";
  await load();
}
async function show(kind, id) {
  if (loading.value || saving.value) return;
  loading.value = true;
  error.value = "";
  try {
    detailKind.value = kind;
    detail.value = await api("/" + kind + "/" + id);
  } catch (e) {
    fail(e);
  } finally {
    loading.value = false;
  }
}
function edit(kind, r) {
  error.value = "";
  dialog.value = {
    kind,
    id: r?.id,
    version: r?.version,
    values: r
      ? { ...r, password: "" }
      : {
          enabled: true,
          permissions: [],
          scope: "DEPARTMENT",
          departmentId: me.value.departmentId,
          category: "LENGTH",
          type: "gauge",
          result: "PASS",
          checkedAt: localInput(options.value.now),
        },
    requestKey: crypto.randomUUID(),
  };
}
function command(kind, r, action) {
  error.value = "";
  dialog.value = {
    kind,
    id: r.id,
    version: r.version,
    action,
    values: { note: "", resolution: "RETEST_PASS", assigneeId: r.assigneeId },
    requestKey: crypto.randomUUID(),
  };
}
/** 提交表单；请求键在失败重试时保持，成功后刷新版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function save() {
  saving.value = true;
  error.value = "";
  try {
    const d = dialog.value,
      v = { ...d.values };
    let path,
      method = "POST";
    if (d.action) {
      path =
        "/" +
        d.kind +
        "/" +
        d.id +
        (d.action === "void" ? "/void" : "/commands/" + d.action);
      Object.assign(v, { version: d.version, requestKey: d.requestKey });
    } else if (d.kind === "password") {
      path = "/auth/password";
    } else if (d.kind === "gauges") {
      path = "/gauges" + (d.id ? "/" + d.id : "");
      method = d.id ? "PUT" : "POST";
      v.version = d.version;
    } else if (["calibrations", "uses"].includes(d.kind)) {
      path = "/" + d.kind;
      v.version = options.value.gauges.find(
        (g) => g.id === Number(v.gaugeId),
      )?.version;
      v.gaugeId = Number(v.gaugeId);
      v.requestKey = d.requestKey;
      if (d.kind === "calibrations") {
        v.checkedAt = instant(v.checkedAt);
        v.validUntil = v.result === "FAIL" ? null : v.validUntil || null;
      }
    } else {
      path = "/admin/" + d.kind + (d.id ? "/" + d.id : "");
      method = d.id ? "PUT" : "POST";
    }
    await api(path, method, v);
    dialog.value = null;
    notice.value = "已保存";
    if (d.kind === "password") {
      me.value = null;
      resetCsrf();
    } else await load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function remove(kind, r) {
  error.value = "";
  dialog.value = {
    kind,
    id: r.id,
    version: r.version,
    action: "delete",
    values: { note: "" },
    requestKey: crypto.randomUUID(),
  };
}
async function confirmDelete() {
  saving.value = true;
  try {
    const d = dialog.value;
    await api(
      (d.kind === "gauges" ? "/gauges/" : "/admin/" + d.kind + "/") +
        d.id +
        (d.kind === "gauges" ? "?version=" + d.version : ""),
      "DELETE",
    );
    dialog.value = null;
    detail.value = null;
    notice.value = "已删除";
    await load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function exportReport() {
  try {
    const data = await api(
      "/" + detailKind.value + "/" + record.value.id + "/report.json",
    );
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = detailKind.value + "-" + record.value.id + ".json";
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    fail(e);
  }
}
function changePage(delta) {
  offset.value += delta;
  load();
}
onMounted(async () => {
  try {
    me.value = await api("/auth/me");
    await load();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED") fail(e);
  }
});
</script>
<template>
  <div v-if="!me" class="login-shell">
    <section class="login-brand">
      <img src="/brand/logo.jpg" alt="知华科技" /><span class="eyebrow"
        >GAUGEFLOW / 0.1.0</span
      >
      <h1>计量器具校准<br />与超差影响追溯</h1>
      <div class="brand-line"></div>
      <p>量具档案 · 校准审核 · 使用追溯 · 影响评估</p>
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        >知华科技官网 <ArrowUpRight :size="14"
      /></a>
    </section>
    <section class="login-form">
      <form @submit.prevent="signIn">
        <span class="eyebrow">ACCOUNT ACCESS</span>
        <h2>登录工作台</h2>
        <label
          >账号<input
            v-model="login.username"
            autocomplete="username"
            required
            maxlength="60" /></label
        ><label
          >密码<input
            v-model="login.password"
            type="password"
            autocomplete="current-password"
            required
            maxlength="128"
        /></label>
        <div v-if="error" class="error" role="alert">{{ error }}</div>
        <button class="primary wide" :disabled="saving">
          {{ saving ? "正在登录…" : "登录" }}
        </button>
        <p class="muted small">公开源码学习版 · 未经书面授权不得商用</p>
      </form>
      <footer>
        上海如静知华信息科技有限公司<br />商业咨询微信 zhuatech / zhuatech2
      </footer>
    </section>
  </div>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" /><span>GaugeFlow</span>
      </div>
      <div class="sidebar-caption">计量与质量协作</div>
      <nav aria-label="主导航">
        <button
          v-for="m in me.menus"
          :key="m.code"
          :class="{ active: page === m.code }"
          :disabled="loading || saving"
          @click="navigate(m.code)"
        >
          <Gauge
            v-if="businessPages.includes(m.code) || m.code === 'workbench'"
            :size="16"
          /><ShieldCheck v-else :size="16" />{{ m.name }}
        </button>
      </nav>
      <footer>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >知华科技官网 ↗</a
        ><small>公开源码学习版 0.1.0</small>
      </footer>
    </aside>
    <div class="content-shell">
      <header class="topbar">
        <span>{{ departmentName(me.departmentId) }}</span>
        <div>
          <button @click="edit('password')">修改密码</button
          ><span>{{ me.displayName }} · {{ me.role }}</span
          ><button aria-label="退出登录" @click="signOut">
            <LogOut :size="17" />
          </button>
        </div>
      </header>
      <main :aria-busy="loading">
        <div class="page-heading">
          <div>
            <span class="eyebrow">METROLOGY OPERATIONS</span>
            <h1>{{ detail ? "记录详情" : title }}</h1>
          </div>
          <button :disabled="loading" @click="load">
            <RefreshCw :size="16" />刷新
          </button>
        </div>
        <div v-if="error" class="error" role="alert">{{ error }}</div>
        <div v-if="notice" class="success" role="status">{{ notice }}</div>
        <template v-if="detail"
          ><button
            @click="
              detail = null;
              load();
            "
          >
            <ChevronLeft :size="16" />返回列表
          </button>
          <section class="panel detail-head">
            <div>
              <h2>
                {{
                  detailKind === "gauges"
                    ? record.code + " · " + record.name
                    : detailKind === "calibrations"
                      ? record.reportNo
                      : detailKind === "uses"
                        ? record.batchRef
                        : "超差事件 #" + record.id
                }}
              </h2>
              <span class="badge" :class="record.status">{{
                states[record.status]
              }}</span>
            </div>
            <div class="toolbar">
              <button
                v-if="detailKind === 'gauges' && can('gauge.manage')"
                @click="edit('gauges', record)"
              >
                编辑台账</button
              ><button
                v-if="
                  detailKind === 'gauges' &&
                  can('calibration.write') &&
                  record.status !== 'RETIRED'
                "
                @click="
                  edit('calibrations');
                  dialog.values.gaugeId = record.id;
                "
              >
                登记校准</button
              ><button
                v-for="a in actions(detailKind, record, me, detail.impacts)"
                :key="a"
                class="primary"
                @click="command(detailKind, record, a)"
              >
                {{ commands[a] }}</button
              ><button v-if="can('export')" @click="exportReport">
                导出 JSON
              </button>
            </div>
          </section>
          <section class="panel">
            <h3>记录信息</h3>
            <dl class="facts">
              <template v-if="detail.gauge"
                ><dt>量具</dt>
                <dd>
                  <button
                    class="link-button"
                    @click="show('gauges', detail.gauge.id)"
                  >
                    {{ gaugeName(detail.gauge.id) }}
                  </button>
                </dd></template
              ><template v-for="(v, k) in record" :key="k"
                ><template
                  v-if="
                    ![
                      'id',
                      'version',
                      'revision',
                      'status',
                      'gaugeId',
                    ].includes(k)
                  "
                  ><dt>
                    {{
                      labels[k] ||
                      {
                        creatorId: "提交人",
                        reviewerId: "复核人",
                        operatorId: "使用人员",
                        lastPassAt: "最近合格校准",
                        lastFailAt: "最近失败校准",
                        approvedReportId: "有效报告编号",
                        createdAt: "登记时间",
                        reviewedAt: "审核时间",
                        usedAt: "使用时间",
                        closedAt: "关闭时间",
                        windowFrom: "追溯开始",
                        windowTo: "追溯结束",
                        scopeNote: "追溯范围与处置说明",
                        reviewNote: "复核意见",
                        voidReason: "冲销原因",
                        calibrationId: "失败报告编号",
                        reportId: "使用时有效报告编号",
                      }[k] ||
                      k
                    }}
                  </dt>
                  <dd>
                    <button
                      v-if="k === 'calibrationId' && detail.calibration"
                      class="link-button"
                      @click="show('calibrations', detail.calibration.id)"
                    >
                      {{ detail.calibration.reportNo }}
                    </button>
                    <template v-else>
                      {{
                        ["departmentId"].includes(k)
                          ? departmentName(v)
                          : ["creatorId", "reviewerId", "operatorId"].includes(
                                k,
                              )
                            ? v
                              ? accountName(v)
                              : "—"
                            : k.endsWith("At") ||
                                [
                                  "validUntil",
                                  "windowFrom",
                                  "windowTo",
                                ].includes(k)
                              ? date(v)
                              : states[v] || v || "—"
                      }}
                    </template>
                  </dd></template
                ></template
              >
            </dl>
          </section>
          <section v-if="detail.impacts" class="panel">
            <div class="section-heading">
              <h3>潜在受影响使用记录</h3>
              <span class="muted">{{ detail.impacts.length }} 条</span>
            </div>
            <p v-if="!detail.impacts.length" class="empty">
              本系统追溯窗口内没有使用记录，仍需提交范围说明并独立复核。
            </p>
            <div v-else class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>批次 / 产品</th>
                    <th>使用任务 / 时间</th>
                    <th>责任人</th>
                    <th>处置</th>
                    <th>证据</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="x in detail.impacts" :key="x.impact.id">
                    <td>
                      <button
                        class="link-button"
                        @click="show('uses', x.usage.id)"
                      >
                        {{ x.usage.batchRef }}</button
                      ><small
                        >{{ x.usage.product }} ·
                        {{ states[x.usage.status] }}</small
                      >
                    </td>
                    <td>
                      {{ x.usage.taskRef
                      }}<small>{{ date(x.usage.usedAt) }}</small>
                    </td>
                    <td>
                      {{
                        x.impact.assigneeId
                          ? accountName(x.impact.assigneeId)
                          : "未分派"
                      }}
                    </td>
                    <td>
                      <span class="badge" :class="x.impact.resolution">{{
                        states[x.impact.resolution]
                      }}</span>
                    </td>
                    <td class="evidence">{{ x.impact.evidence || "—" }}</td>
                    <td>
                      <div class="toolbar">
                        <button
                          v-if="
                            record.status === 'OPEN' && can('incident.manage')
                          "
                          @click="command('impacts', x.impact, 'assign')"
                        >
                          分派</button
                        ><button
                          v-if="
                            record.status === 'OPEN' &&
                            can('impact.assess') &&
                            x.impact.assigneeId === me.id
                          "
                          @click="command('impacts', x.impact, 'assess')"
                        >
                          评估
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
          <section v-if="detail.calibrations" class="panel">
            <h3>校准历史</h3>
            <div
              v-for="c in detail.calibrations"
              :key="c.id"
              class="history-row"
            >
              <button class="link-button" @click="show('calibrations', c.id)">
                {{ c.reportNo }}</button
              ><span>{{ date(c.checkedAt) }}</span
              ><span class="badge" :class="c.result">{{
                states[c.result]
              }}</span
              ><span>{{ states[c.status] }}</span>
            </div>
            <p v-if="!detail.calibrations.length" class="empty">尚无校准报告</p>
          </section>
          <section v-if="detail.incidents" class="panel">
            <h3>超差事件</h3>
            <div v-for="i in detail.incidents" :key="i.id" class="history-row">
              <button class="link-button" @click="show('incidents', i.id)">
                事件 #{{ i.id }}</button
              ><span>{{ date(i.windowTo) }}</span
              ><span class="badge" :class="i.status">{{
                states[i.status]
              }}</span>
            </div>
            <p v-if="!detail.incidents.length" class="empty">尚无超差事件</p>
          </section>
          <section class="panel">
            <h3>操作轨迹</h3>
            <div v-for="e in detail.events" :key="e.id" class="timeline">
              <span>{{ date(e.createdAt) }}</span
              ><strong
                >{{ accountName(e.actorId) }} ·
                {{
                  (e.action === "SUBMIT" && detailKind === "calibrations"
                    ? "提交校准"
                    : commands[e.action.toLowerCase()]) ||
                  {
                    SAVE: "台账维护",
                    DELETE: "删除",
                    SUBMIT: "提交",
                    REGISTER: "使用登记",
                    OPEN: "建立追溯",
                    CALIBRATION_SUBMIT: "提交校准",
                  }[e.action] ||
                  e.action
                }}</strong
              >
              <p>{{ e.note }}</p>
            </div>
            <p v-if="!detail.events.length" class="empty">暂无操作记录</p>
          </section></template
        >
        <template v-else-if="page === 'workbench'"
          ><div class="summary-strip">
            <div>
              <span>待审核报告</span
              ><strong>{{ work.pendingReports.length }}</strong>
            </div>
            <div>
              <span>本人待评估</span><strong>{{ work.tasks.length }}</strong>
            </div>
            <div>
              <span>待复核事件</span><strong>{{ work.reviews.length }}</strong>
            </div>
            <div>
              <span>未有效量具</span><strong>{{ work.gauges.length }}</strong>
            </div>
          </div>
          <div class="columns">
            <section class="panel">
              <h3>本人评估待办</h3>
              <div v-for="x in work.tasks" :key="x.id" class="task-row">
                <div>
                  <strong>影响记录 #{{ x.id }}</strong
                  ><small>超差事件 #{{ x.incidentId }}</small>
                </div>
                <button @click="show('incidents', x.incidentId)">处理 ↗</button>
              </div>
              <p v-if="!work.tasks.length" class="empty">当前没有待评估任务</p>
              <h3>待复核事件</h3>
              <div v-for="x in work.reviews" :key="x.id" class="task-row">
                <div>
                  <strong>{{ gaugeName(x.gaugeId) }}</strong
                  ><small>事件 #{{ x.id }} · {{ date(x.windowTo) }}</small>
                </div>
                <button @click="show('incidents', x.id)">查看 ↗</button>
              </div>
              <p v-if="!work.reviews.length" class="empty">
                当前没有待复核事件
              </p>
            </section>
            <section class="panel">
              <h3>校准审核</h3>
              <div
                v-for="x in work.pendingReports"
                :key="x.id"
                class="task-row"
              >
                <div>
                  <strong>{{ x.reportNo }}</strong
                  ><small
                    >{{ gaugeName(x.gaugeId) }} · {{ states[x.result] }}</small
                  >
                </div>
                <button @click="show('calibrations', x.id)">查看 ↗</button>
              </div>
              <p v-if="!work.pendingReports.length" class="empty">
                当前没有待审核报告
              </p>
              <h3>量具状态关注</h3>
              <div
                v-for="x in work.gauges.slice(0, 8)"
                :key="x.id"
                class="task-row"
              >
                <div>
                  <strong>{{ x.code }} · {{ x.name }}</strong
                  ><small>{{ states[x.status] }}</small>
                </div>
                <button @click="show('gauges', x.id)">查看 ↗</button>
              </div>
            </section>
          </div></template
        >
        <template v-else-if="page === 'dashboard'"
          ><div class="summary-strip">
            <div>
              <span>授权量具</span><strong>{{ stats.gauges }}</strong>
            </div>
            <div>
              <span>即将到期</span><strong>{{ stats.dueSoon }}</strong>
            </div>
            <div>
              <span>未关闭事件</span><strong>{{ stats.openIncidents }}</strong>
            </div>
            <div>
              <span>使用记录</span><strong>{{ stats.usageCount }}</strong>
            </div>
          </div>
          <section class="panel">
            <h2>量具状态分布</h2>
            <div
              v-for="s in [
                'AVAILABLE',
                'EXPIRED',
                'QUARANTINED',
                'UNQUALIFIED',
                'RETIRED',
              ]"
              :key="s"
              class="chart-row"
            >
              <span>{{ states[s] }}</span>
              <div class="bar-track">
                <div
                  :class="s"
                  :style="{
                    width:
                      (stats.gauges
                        ? ((stats.byStatus?.[s] || 0) / stats.gauges) * 100
                        : 0) + '%',
                  }"
                ></div>
              </div>
              <strong>{{ stats.byStatus?.[s] || 0 }}</strong>
            </div>
            <p class="muted small">
              截止日期按上海时区计算，统计范围与账号权限一致。
            </p>
          </section></template
        >
        <template v-else
          ><section class="panel">
            <div class="toolbar filters">
              <input
                v-model="search"
                aria-label="搜索"
                placeholder="搜索编号或名称"
                maxlength="120"
                @keyup.enter="
                  offset = 0;
                  load();
                "
              /><select
                v-if="businessPages.includes(page)"
                v-model="status"
                aria-label="状态"
                @change="
                  offset = 0;
                  load();
                "
              >
                <option value="">全部状态</option>
                <option v-for="s in statuses" :key="s" :value="s">
                  {{ states[s] }}
                </option></select
              ><select
                v-if="businessPages.includes(page)"
                v-model="sort"
                aria-label="排序"
                @change="
                  offset = 0;
                  load();
                "
              >
                <option value="newest">最近登记</option>
                <option value="oldest">最早登记</option></select
              ><button
                @click="
                  offset = 0;
                  load();
                "
              >
                查询</button
              ><button
                v-if="
                  (page === 'gauges' && can('gauge.manage')) ||
                  (page === 'calibrations' && can('calibration.write')) ||
                  (page === 'uses' && can('use.write')) ||
                  (['users', 'roles', 'departments', 'dictionaries'].includes(
                    page,
                  ) &&
                    can('admin'))
                "
                class="primary"
                @click="edit(page)"
              >
                <Plus :size="16" />{{
                  page === "uses"
                    ? "登记使用"
                    : page === "calibrations"
                      ? "登记校准"
                      : "新建"
                }}
              </button>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr v-if="page === 'gauges'">
                    <th>编号 / 名称</th>
                    <th>规格 / 分类</th>
                    <th>部门</th>
                    <th>状态</th>
                    <th>有效截止</th>
                    <th>操作</th>
                  </tr>
                  <tr v-else-if="page === 'calibrations'">
                    <th>报告编号</th>
                    <th>量具</th>
                    <th>校准时间</th>
                    <th>结论</th>
                    <th>审核状态</th>
                    <th>操作</th>
                  </tr>
                  <tr v-else-if="page === 'uses'">
                    <th>批次 / 产品</th>
                    <th>量具 / 任务</th>
                    <th>人员</th>
                    <th>使用时间</th>
                    <th>状态</th>
                    <th>操作</th>
                  </tr>
                  <tr v-else-if="page === 'incidents'">
                    <th>事件</th>
                    <th>量具</th>
                    <th>追溯开始</th>
                    <th>追溯结束</th>
                    <th>状态</th>
                    <th>操作</th>
                  </tr>
                  <tr v-else-if="page === 'audit'">
                    <th>时间</th>
                    <th>账号</th>
                    <th>操作</th>
                    <th>对象</th>
                    <th>部门</th>
                  </tr>
                  <tr v-else>
                    <th>名称 / 编号</th>
                    <th>配置</th>
                    <th>状态 / 范围</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in visibleRows" :key="r.id">
                    <template v-if="page === 'gauges'"
                      ><td>
                        <strong>{{ r.code }}</strong
                        ><small>{{ r.name }}</small>
                      </td>
                      <td>
                        {{ r.model
                        }}<small>{{
                          options.dictionaries.find(
                            (d) => d.code === r.category,
                          )?.name || r.category
                        }}</small>
                      </td>
                      <td>{{ departmentName(r.departmentId) }}</td>
                      <td>
                        <span class="badge" :class="r.status">{{
                          states[r.status]
                        }}</span>
                      </td>
                      <td>{{ date(r.validUntil) }}</td></template
                    ><template v-else-if="page === 'calibrations'"
                      ><td>{{ r.reportNo }}</td>
                      <td>{{ gaugeName(r.gaugeId) }}</td>
                      <td>{{ date(r.checkedAt) }}</td>
                      <td>
                        <span class="badge" :class="r.result">{{
                          states[r.result]
                        }}</span>
                      </td>
                      <td>{{ states[r.status] }}</td></template
                    ><template v-else-if="page === 'uses'"
                      ><td>
                        {{ r.batchRef }}<small>{{ r.product }}</small>
                      </td>
                      <td>
                        {{ gaugeName(r.gaugeId) }}<small>{{ r.taskRef }}</small>
                      </td>
                      <td>{{ accountName(r.operatorId) }}</td>
                      <td>{{ date(r.usedAt) }}</td>
                      <td>{{ states[r.status] }}</td></template
                    ><template v-else-if="page === 'incidents'"
                      ><td>#{{ r.id }}</td>
                      <td>{{ gaugeName(r.gaugeId) }}</td>
                      <td>{{ date(r.windowFrom) }}</td>
                      <td>{{ date(r.windowTo) }}</td>
                      <td>
                        <span class="badge" :class="r.status">{{
                          states[r.status]
                        }}</span>
                      </td></template
                    ><template v-else-if="page === 'audit'"
                      ><td>{{ date(r.createdAt) }}</td>
                      <td>{{ r.actor }}</td>
                      <td>{{ r.action }}</td>
                      <td>{{ r.objectId }}</td>
                      <td>{{ departmentName(r.departmentId) }}</td></template
                    ><template v-else
                      ><td>
                        <strong>{{ r.displayName || r.name || r.code }}</strong
                        ><small>{{ r.username || r.code || r.type }}</small>
                      </td>
                      <td class="config-cell">
                        {{
                          r.value ||
                          r.permissions
                            ?.map(
                              (p) =>
                                permissions.find((x) => x.code === p)?.name ||
                                p,
                            )
                            .join("、") ||
                          r.nameEn ||
                          (r.roleId
                            ? roles.find((x) => x.id === r.roleId)?.name
                            : "")
                        }}<small v-if="r.departmentId">{{
                          departmentName(r.departmentId)
                        }}</small>
                      </td>
                      <td>
                        {{
                          r.scope === "ALL"
                            ? "全部部门"
                            : r.scope === "DEPARTMENT"
                              ? "本部门"
                              : r.enabled === true
                                ? "启用"
                                : r.enabled === false
                                  ? "停用"
                                  : "—"
                        }}
                      </td></template
                    >
                    <td v-if="page !== 'audit'">
                      <div class="toolbar">
                        <button
                          v-if="businessPages.includes(page)"
                          @click="show(page, r.id)"
                        >
                          详情</button
                        ><button v-else @click="edit(page, r)">编辑</button
                        ><button
                          v-if="
                            (page === 'gauges' &&
                              can('gauge.manage') &&
                              r.status === 'UNQUALIFIED') ||
                            [
                              'users',
                              'roles',
                              'departments',
                              'dictionaries',
                            ].includes(page)
                          "
                          @click="remove(page, r)"
                        >
                          删除
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-if="!visibleRows.length && !loading" class="empty">
              没有符合条件的记录
            </p>
            <div class="pagination">
              <span>共 {{ pageTotal }} 条 · 第 {{ offset + 1 }} 页</span
              ><button
                aria-label="上一页"
                :disabled="offset === 0"
                @click="changePage(-1)"
              >
                <ChevronLeft :size="16" /></button
              ><button
                aria-label="下一页"
                :disabled="(offset + 1) * 20 >= pageTotal"
                @click="changePage(1)"
              >
                <ChevronRight :size="16" />
              </button>
            </div></section
        ></template>
        <footer class="page-footer">
          <span>上海如静知华信息科技有限公司</span
          ><a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
            >官网与商业咨询 ↗</a
          ><span>微信 zhuatech / zhuatech2</span>
        </footer>
      </main>
    </div>
  </div>
  <div
    v-if="dialog"
    class="modal-backdrop"
    @click.self="!saving && (dialog = null)"
  >
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="dialog-title"
    >
      <header>
        <h2 id="dialog-title">
          {{
            dialog.action === "delete"
              ? "删除记录"
              : dialog.action
                ? commands[dialog.action]
                : dialog.kind === "password"
                  ? "修改密码"
                  : dialog.id
                    ? "编辑记录"
                    : dialog.kind === "uses"
                      ? "登记使用"
                      : dialog.kind === "calibrations"
                        ? "登记校准"
                        : "新建记录"
          }}
        </h2>
        <button aria-label="关闭" :disabled="saving" @click="dialog = null">
          <X :size="18" />
        </button>
      </header>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <form v-if="dialog.action !== 'delete'" @submit.prevent="save">
        <div class="form-grid">
          <label
            v-for="f in dialogFields"
            :key="f.key"
            :class="{ full: ['textarea', 'permissions'].includes(f.type) }"
            >{{ labels[f.key] || f.key
            }}<select
              v-if="f.type === 'select'"
              v-model="dialog.values[f.key]"
              :required="f.required !== false"
            >
              <option disabled :value="undefined">请选择</option>
              <option v-for="o in f.options" :key="o.value" :value="o.value">
                {{ o.label }}
              </option></select
            ><textarea
              v-else-if="f.type === 'textarea'"
              v-model="dialog.values[f.key]"
              rows="4"
              :maxlength="f.max || 2000"
              :required="f.required !== false"
            ></textarea>
            <div v-else-if="f.type === 'permissions'" class="permission-grid">
              <label v-for="p in f.options" :key="p.value"
                ><input
                  v-model="dialog.values.permissions"
                  type="checkbox"
                  :value="p.value"
                />{{ p.label }}</label
              >
            </div>
            <input
              v-else-if="f.type === 'checkbox'"
              v-model="dialog.values[f.key]"
              type="checkbox" /><input
              v-else-if="f.type === 'number'"
              v-model.number="dialog.values[f.key]"
              type="number"
              :required="f.required !== false" /><input
              v-else
              v-model="dialog.values[f.key]"
              :type="f.type === 'readonly' ? 'text' : f.type"
              :readonly="f.type === 'readonly'"
              :required="f.required !== false"
              :maxlength="f.type === 'password' ? 128 : 120"
              :autocomplete="f.type === 'password' ? 'new-password' : 'off'"
          /></label>
        </div>
        <p
          v-if="dialog.kind === 'calibrations' && !dialog.action"
          class="muted small"
        >
          报告事实提交后保留。待审期间暂停登记使用；不合格报告立即冻结量具并建立追溯事件。
        </p>
        <p v-if="dialog.action === 'submit'" class="muted small">
          填写追溯范围、系统外记录核查及处置汇总。没有关联记录也需要独立复核。
        </p>
        <footer>
          <button type="button" :disabled="saving" @click="dialog = null">
            取消</button
          ><button class="primary" :disabled="saving">
            {{ saving ? "正在保存…" : "确认提交" }}
          </button>
        </footer>
      </form>
      <div v-else>
        <p>删除没有业务引用的记录？已有历史的资料会受到保护。</p>
        <footer>
          <button :disabled="saving" @click="dialog = null">取消</button
          ><button class="danger" :disabled="saving" @click="confirmDelete">
            确认删除
          </button>
        </footer>
      </div>
    </section>
  </div>
</template>
