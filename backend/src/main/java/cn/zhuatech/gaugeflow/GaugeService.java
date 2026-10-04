// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.gaugeflow;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 量具、校准、使用和超差评估的事务边界；共享行锁避免使用与冻结竞态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class GaugeService {
  final Store db;
  final AccessService access;
  final Clock clock;

  public GaugeService(Store db, AccessService access, Clock clock) {
    this.db = db;
    this.access = access;
    this.clock = clock;
  }

  /** 台账输入；版本阻止旧页面覆盖。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record GaugeInput(
      String code, String name, String model, String category, Long departmentId, Long version) {}

  /** 提交后不可修改的校准事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record CalibrationInput(
      Long gaugeId,
      Long version,
      String reportNo,
      String provider,
      Instant checkedAt,
      LocalDate validUntil,
      String result,
      String evidence,
      String requestKey) {}

  /** 当前实际使用，不允许回填使用时间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record UseInput(
      Long gaugeId,
      Long version,
      String batchRef,
      String taskRef,
      String product,
      String note,
      String requestKey) {}

  /** 生命周期命令载荷，UUID绑定完整内容以精确重试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(
      Long version, String note, Long assigneeId, String resolution, String requestKey) {}

  /** 部门权限内的下拉目录，不公开密码或部门外成员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    access.require("gauge.read");
    var accounts =
        db.all(Account.class).stream()
            .filter(a -> access.visible(a.departmentId))
            .map(
                a ->
                    Map.of(
                        "id",
                        a.id,
                        "name",
                        a.displayName,
                        "departmentId",
                        a.departmentId,
                        "enabled",
                        a.enabled,
                        "scope",
                        db.get(AccessRole.class, a.roleId).scope,
                        "permissions",
                        db.get(AccessRole.class, a.roleId).permissions))
            .toList();
    return Map.of(
        "accounts",
        accounts,
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList(),
        "gauges",
        bounded(Gauge.class).stream().map(this::gaugeView).toList(),
        "dictionaries",
        db.all(DictionaryEntry.class),
        "settings",
        db.all(SystemSetting.class),
        "now",
        clock.instant());
  }

  /** 在数据库执行授权过滤、搜索、分页与固定排序。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(String kind, String search, String status, int page, int size, String sort) {
    access.require("gauge.read");
    if (page < 0
        || page > 100000
        || size < 1
        || size > 100
        || search.length() > 120
        || status.length() > 30) throw new Problem(400, "INVALID_INPUT");
    Class<?> type = type(kind);
    String field =
        switch (kind) {
          case "gauges" -> "code";
          case "calibrations" -> "reportNo";
          case "uses" -> "batchRef";
          default -> "scopeNote";
        };
    String where =
        scope()
            + " and (lower(e."
            + field
            + ") like :q or "
            + (kind.equals("gauges") ? "lower(e.name) like :q" : "1=0")
            + ")";
    boolean expired = kind.equals("gauges") && status.equals("EXPIRED");
    if (!status.isBlank())
      where +=
          expired ? " and e.status='AVAILABLE' and e.validUntil < :today" : " and e.status=:state";
    if (kind.equals("gauges") && status.equals("AVAILABLE")) where += " and e.validUntil >= :today";
    String order =
        switch (sort) {
          case "newest" -> "e.id desc";
          case "oldest" -> "e.id asc";
          default -> throw new Problem(400, "INVALID_INPUT");
        };
    var q =
        db.jpql(type, "from " + type.getSimpleName() + " e where " + where + " order by " + order);
    var count =
        db.jpql(Long.class, "select count(e) from " + type.getSimpleName() + " e where " + where);
    for (var query : List.of(q, count)) {
      if (!"ALL".equals(access.role().scope))
        query.setParameter("department", access.current().departmentId);
      query.setParameter("q", "%" + search.toLowerCase(Locale.ROOT) + "%");
      if (!status.isBlank() && !expired) query.setParameter("state", status);
      if (expired || kind.equals("gauges") && status.equals("AVAILABLE"))
        query.setParameter("today", GaugePolicy.today(clock.instant()));
    }
    var items = q.setFirstResult(page * size).setMaxResults(size).getResultList();
    return Map.of(
        "items",
        kind.equals("gauges") ? items.stream().map(v -> gaugeView((Gauge) v)).toList() : items,
        "total",
        count.getSingleResult(),
        "page",
        page,
        "size",
        size);
  }

  /** 返回授权明细及历史事件，关联量具状态同源计算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(String kind, Long id) {
    access.require("gauge.read");
    var out = new LinkedHashMap<String, Object>();
    Object entity = db.get(type(kind), id);
    Long dept;
    if (entity instanceof Gauge g) {
      dept = g.departmentId;
      out.put("record", gaugeView(g));
    } else if (entity instanceof Calibration c) {
      dept = c.departmentId;
      out.put("record", c);
      out.put("gauge", gaugeView(db.get(Gauge.class, c.gaugeId)));
    } else if (entity instanceof GaugeUse u) {
      dept = u.departmentId;
      out.put("record", u);
      out.put("gauge", gaugeView(db.get(Gauge.class, u.gaugeId)));
    } else {
      var i = (Incident) entity;
      dept = i.departmentId;
      out.put("record", i);
      out.put("gauge", gaugeView(db.get(Gauge.class, i.gaugeId)));
      out.put("calibration", db.get(Calibration.class, i.calibrationId));
    }
    access.department(dept);
    if (entity instanceof Gauge) {
      out.put(
          "calibrations",
          db.query(Calibration.class, "from Calibration where gaugeId=?1 order by id desc", id));
      out.put(
          "incidents",
          db.query(Incident.class, "from Incident where gaugeId=?1 order by id desc", id));
    }
    if (entity instanceof Incident) {
      out.put(
          "impacts",
          db.query(Impact.class, "from Impact where incidentId=?1 order by id", id).stream()
              .map(p -> Map.of("impact", p, "usage", db.get(GaugeUse.class, p.useId)))
              .toList());
    }
    out.put(
        "events",
        db.query(
            FlowEvent.class, "from FlowEvent where kind=?1 and objectId=?2 order by id", kind, id));
    return out;
  }

  /** 新建或编辑台账；有校准或使用历史时保留编号、分类与归属。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveGauge(Long id, GaugeInput v) {
    gate("gauge.manage");
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    var g = id == null ? new Gauge() : gauge(id);
    if (id != null) GaugePolicy.version(g.version, v.version);
    String code = AdminService.text(v.code, 60), category = AdminService.text(v.category, 60);
    db.get(Department.class, required(v.departmentId));
    access.department(v.departmentId);
    if (db.query(
            DictionaryEntry.class, "from DictionaryEntry where type='gauge' and code=?1", category)
        .isEmpty()) throw new Problem(400, "INVALID_DICTIONARY");
    if (id != null
        && !db.query(Calibration.class, "from Calibration where gaugeId=?1", id).isEmpty()
        && (!Objects.equals(g.code, code)
            || !Objects.equals(g.category, category)
            || !Objects.equals(g.departmentId, v.departmentId)))
      throw new Problem(409, "HISTORY_PROTECTED");
    g.code = code;
    g.name = AdminService.text(v.name, 120);
    g.model = AdminService.text(v.model, 120);
    g.category = category;
    g.departmentId = v.departmentId;
    if (id == null) {
      g.status = "UNQUALIFIED";
      g.createdAt = clock.instant();
      db.save(g);
    }
    event("gauges", g.id, g.departmentId, "SAVE", "台账维护");
    db.flush();
    return gaugeView(g);
  }

  /** 仅删除没有校准或使用引用的台账；外键提供最终保护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteGauge(Long id, Long version) {
    gate("gauge.manage");
    var g = gauge(id);
    GaugePolicy.version(g.version, version);
    if (!db.query(Calibration.class, "from Calibration where gaugeId=?1", id).isEmpty())
      throw new Problem(409, "HISTORY_PROTECTED");
    event("gauges", id, g.departmentId, "DELETE", "删除无业务台账");
    db.delete(g);
  }

  /** 提交校准报告；失败在同一事务立即冻结并保存潜在受影响记录快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object calibrate(CalibrationInput v) {
    gate("calibration.write");
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    var g = gauge(required(v.gaugeId));
    String key = "CALIBRATE:" + v;
    if (replay(v.requestKey, key)) return Map.of("replayed", true);
    GaugePolicy.version(g.version, v.version);
    if (g.status.equals("RETIRED")) throw new Problem(409, "INVALID_STATE");
    var prior =
        db.query(
            Calibration.class, "from Calibration where gaugeId=?1 order by checkedAt desc", g.id);
    if (prior.stream().anyMatch(c -> c.status.equals("PENDING")))
      throw new Problem(409, "PENDING_CALIBRATION");
    GaugePolicy.calibration(
        v.checkedAt,
        v.validUntil,
        v.result,
        prior.isEmpty() ? null : prior.getFirst().checkedAt,
        clock.instant());
    var c = new Calibration();
    c.gaugeId = g.id;
    c.departmentId = g.departmentId;
    c.reportNo = AdminService.text(v.reportNo, 120);
    c.provider = AdminService.text(v.provider, 120);
    c.checkedAt = v.checkedAt;
    c.validUntil = v.validUntil;
    c.result = v.result;
    c.evidence = AdminService.text(v.evidence, 4000);
    c.status = "PENDING";
    c.creatorId = access.current().id;
    c.createdAt = clock.instant();
    db.save(c);
    // A pending report also blocks new usage; an instrument sent for calibration must not be used.
    if (v.result.equals("FAIL")) {
      g.status = "QUARANTINED";
      g.lastFailAt = c.checkedAt;
      var i = new Incident();
      i.gaugeId = g.id;
      i.calibrationId = c.id;
      i.departmentId = g.departmentId;
      i.creatorId = c.creatorId;
      i.status = "OPEN";
      i.windowFrom = g.lastPassAt == null ? g.createdAt : g.lastPassAt;
      i.windowTo = c.createdAt;
      i.scopeNote = "";
      db.save(i);
      for (var u :
          db.query(
              GaugeUse.class,
              "from GaugeUse where gaugeId=?1 and usedAt>=?2 and usedAt<=?3 order by id",
              g.id,
              i.windowFrom,
              i.windowTo)) {
        var p = new Impact();
        p.incidentId = i.id;
        p.useId = u.id;
        p.resolution = "PENDING";
        db.save(p);
      }
      event("incidents", i.id, g.departmentId, "OPEN", "校准失败，冻结量具并建立使用快照");
    }
    // Force the gauge version to change even when submitting a passing report without a state
    // change.
    g.revision++;
    event("calibrations", c.id, g.departmentId, "SUBMIT", c.reportNo);
    event("gauges", g.id, g.departmentId, "CALIBRATION_SUBMIT", c.reportNo);
    stamp(v.requestKey, key);
    db.flush();
    return c;
  }

  /** 审核报告时禁止提交者自审；失败报告被拒绝仍保留冻结与追溯事件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object calibrationCommand(Long id, String action, Command v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    gate("calibration.review");
    var c = db.get(Calibration.class, id);
    access.department(c.departmentId);
    var g = gauge(c.gaugeId);
    String fingerprint = "CAL:" + id + ":" + action + ":" + v;
    if (replay(v.requestKey, fingerprint)) return c;
    GaugePolicy.version(c.version, v.version);
    if (!c.status.equals("PENDING")) throw new Problem(409, "INVALID_STATE");
    if (c.creatorId.equals(access.current().id)) throw new Problem(403, "INDEPENDENT_REVIEW");
    if (!Set.of("approve", "reject").contains(action)) throw new Problem(400, "INVALID_ACTION");
    c.reviewNote = AdminService.text(v.note, 2000);
    c.reviewerId = access.current().id;
    c.reviewedAt = clock.instant();
    c.status = action.equals("approve") ? "APPROVED" : "REJECTED";
    if (action.equals("approve") && c.result.equals("PASS")) {
      g.lastPassAt = c.checkedAt;
      g.validUntil = c.validUntil;
      g.approvedReportId = c.id;
      if (!g.status.equals("QUARANTINED")) g.status = "AVAILABLE";
    }
    event("calibrations", id, c.departmentId, action.toUpperCase(Locale.ROOT), c.reviewNote);
    stamp(v.requestKey, fingerprint);
    db.flush();
    return c;
  }

  /** 登记当前实际使用；冻结、过期和待审校准均拒绝，记录引用生效报告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object use(UseInput v) {
    gate("use.write");
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    var g = gauge(required(v.gaugeId));
    String fingerprint = "USE:" + v;
    if (replay(v.requestKey, fingerprint)) return Map.of("replayed", true);
    GaugePolicy.version(g.version, v.version);
    GaugePolicy.usable(g, clock.instant());
    if (!db.query(Calibration.class, "from Calibration where gaugeId=?1 and status='PENDING'", g.id)
        .isEmpty()) throw new Problem(409, "PENDING_CALIBRATION");
    var u = new GaugeUse();
    u.gaugeId = g.id;
    u.departmentId = g.departmentId;
    u.reportId = g.approvedReportId;
    u.operatorId = access.current().id;
    u.batchRef = AdminService.text(v.batchRef, 120);
    u.taskRef = AdminService.text(v.taskRef, 120);
    u.product = AdminService.text(v.product, 120);
    u.note = AdminService.text(v.note, 2000);
    u.status = "ACTIVE";
    u.usedAt = clock.instant();
    db.save(u);
    event("uses", u.id, u.departmentId, "REGISTER", u.batchRef);
    stamp(v.requestKey, fingerprint);
    db.flush();
    return u;
  }

  /** 冲销错录使用事实，保留原记录及已建立的影响快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object voidUse(Long id, Command v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    gate("gauge.manage");
    var u = db.get(GaugeUse.class, id);
    access.department(u.departmentId);
    String f = "VOID:" + id + ":" + v;
    if (replay(v.requestKey, f)) return u;
    GaugePolicy.version(u.version, v.version);
    if (!u.status.equals("ACTIVE")) throw new Problem(409, "INVALID_STATE");
    u.voidReason = AdminService.text(v.note, 2000);
    u.status = "VOID";
    event("uses", id, u.departmentId, "VOID", u.voidReason);
    stamp(v.requestKey, f);
    db.flush();
    return u;
  }

  /** 分派及评估单条影响记录，评估者必须是当前启用的被分派人员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object impactCommand(Long id, String action, Command v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    gate(action.equals("assign") ? "incident.manage" : "impact.assess");
    var p = db.get(Impact.class, id);
    var i = db.get(Incident.class, p.incidentId);
    access.department(i.departmentId);
    String f = "IMPACT:" + id + ":" + action + ":" + v;
    if (replay(v.requestKey, f)) return p;
    GaugePolicy.version(p.version, v.version);
    if (!i.status.equals("OPEN")) throw new Problem(409, "INVALID_STATE");
    if (action.equals("assign")) {
      var a = db.get(Account.class, required(v.assigneeId));
      if (!a.enabled
          || (!db.get(AccessRole.class, a.roleId).scope.equals("ALL")
              && !a.departmentId.equals(i.departmentId))
          || !db.get(AccessRole.class, a.roleId).permissions.contains("impact.assess"))
        throw new Problem(400, "INVALID_ASSIGNEE");
      p.assigneeId = a.id;
      p.assessorId = null;
      p.resolution = "PENDING";
      p.evidence = null;
      p.assessedAt = null;
    } else if (action.equals("assess")) {
      if (!Objects.equals(p.assigneeId, access.current().id))
        throw new Problem(403, "NOT_ASSIGNEE");
      if (!Set.of("NO_IMPACT", "RETEST_PASS", "REJECTED_PRODUCT")
          .contains(Objects.toString(v.resolution, ""))) throw new Problem(400, "INVALID_INPUT");
      p.resolution = v.resolution;
      p.evidence = AdminService.text(v.note, 4000);
      p.assessorId = access.current().id;
      p.assessedAt = clock.instant();
    } else throw new Problem(400, "INVALID_ACTION");
    // Parent version changes so submission cannot silently include an unseen assessment.
    i.revision++;
    event(
        "incidents",
        i.id,
        i.departmentId,
        action.toUpperCase(Locale.ROOT),
        "影响记录 #" + p.id + " · " + (action.equals("assign") ? "分派 #" + p.assigneeId : p.resolution));
    stamp(v.requestKey, f);
    db.flush();
    return p;
  }

  /** 提交全量处置并独立复核；退回后保留评估证据，关闭不自动恢复量具。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object incidentCommand(Long id, String action, Command v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    gate(action.equals("submit") ? "incident.manage" : "incident.review");
    var i = db.get(Incident.class, id);
    access.department(i.departmentId);
    String f = "INCIDENT:" + id + ":" + action + ":" + v;
    if (replay(v.requestKey, f)) return i;
    GaugePolicy.version(i.version, v.version);
    String note = AdminService.text(v.note, 2000);
    var impacts = db.query(Impact.class, "from Impact where incidentId=?1", id);
    if (action.equals("submit")) {
      if (!i.status.equals("OPEN")) throw new Problem(409, "INVALID_STATE");
      if (impacts.stream().anyMatch(p -> p.resolution.equals("PENDING") || p.assessorId == null))
        throw new Problem(409, "INCOMPLETE_IMPACT");
      i.scopeNote = note;
      i.status = "REVIEW";
    } else if (Set.of("close", "return").contains(action)) {
      if (!i.status.equals("REVIEW")) throw new Problem(409, "INVALID_STATE");
      Long me = access.current().id;
      if (i.creatorId.equals(me)
          || impacts.stream().anyMatch(p -> Objects.equals(p.assessorId, me)))
        throw new Problem(403, "INDEPENDENT_REVIEW");
      i.reviewNote = note;
      i.reviewerId = me;
      i.status = action.equals("close") ? "CLOSED" : "OPEN";
      if (action.equals("close")) i.closedAt = clock.instant();
    } else throw new Problem(400, "INVALID_ACTION");
    event("incidents", id, i.departmentId, action.toUpperCase(Locale.ROOT), note);
    stamp(v.requestKey, f);
    db.flush();
    return i;
  }

  /** 恢复要求所有追溯事件关闭且失败后报告已独立审核合格；报废不删历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object gaugeCommand(Long id, String action, Command v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    gate("gauge.manage");
    var g = gauge(id);
    String f = "GAUGE:" + id + ":" + action + ":" + v;
    if (replay(v.requestKey, f)) return gaugeView(g);
    GaugePolicy.version(g.version, v.version);
    String note = AdminService.text(v.note, 2000);
    if (action.equals("release")) {
      if (!g.status.equals("QUARANTINED")) throw new Problem(409, "INVALID_STATE");
      if (!db.query(Incident.class, "from Incident where gaugeId=?1 and status<>'CLOSED'", id)
          .isEmpty()) throw new Problem(409, "OPEN_INCIDENT");
      if (g.approvedReportId == null
          || g.lastPassAt == null
          || g.lastFailAt == null
          || !g.lastPassAt.isAfter(g.lastFailAt)
          || g.validUntil.isBefore(GaugePolicy.today(clock.instant())))
        throw new Problem(409, "RECOVERY_CALIBRATION_REQUIRED");
      if (!db.query(Calibration.class, "from Calibration where gaugeId=?1 and status='PENDING'", id)
          .isEmpty()) throw new Problem(409, "PENDING_CALIBRATION");
      g.status = "AVAILABLE";
    } else if (action.equals("retire")) {
      if (g.status.equals("RETIRED")) throw new Problem(409, "INVALID_STATE");
      if (!db.query(Incident.class, "from Incident where gaugeId=?1 and status<>'CLOSED'", id)
              .isEmpty()
          || !db.query(
                  Calibration.class, "from Calibration where gaugeId=?1 and status='PENDING'", id)
              .isEmpty()) throw new Problem(409, "OPEN_INCIDENT");
      g.status = "RETIRED";
    } else throw new Problem(400, "INVALID_ACTION");
    event("gauges", id, g.departmentId, action.toUpperCase(Locale.ROOT), note);
    stamp(v.requestKey, f);
    db.flush();
    return gaugeView(g);
  }

  /** 当前授权范围统计与本人待评估任务，超过上限明确报错。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var gauges = bounded(Gauge.class);
    var counts = new TreeMap<String, Long>();
    for (var g : gauges) counts.merge(GaugePolicy.status(g, clock.instant()), 1L, Long::sum);
    int days =
        Integer.parseInt(
            db.query(SystemSetting.class, "from SystemSetting where code='dueSoonDays'")
                .getFirst()
                .value);
    var today = GaugePolicy.today(clock.instant());
    return Map.of(
        "gauges",
        gauges.size(),
        "byStatus",
        counts,
        "dueSoon",
        gauges.stream()
            .filter(
                g ->
                    GaugePolicy.status(g, clock.instant()).equals("AVAILABLE")
                        && !g.validUntil.isAfter(today.plusDays(days)))
            .count(),
        "pendingReports",
        bounded(Calibration.class).stream().filter(c -> c.status.equals("PENDING")).count(),
        "openIncidents",
        bounded(Incident.class).stream().filter(i -> !i.status.equals("CLOSED")).count(),
        "usageCount",
        bounded(GaugeUse.class).size());
  }

  /** 本人待办由授权事件范围与分派关系共同限定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object workbench() {
    access.require("gauge.read");
    var incidents = bounded(Incident.class);
    var ids = new HashSet<>(incidents.stream().map(i -> i.id).toList());
    var tasks =
        db
            .query(
                Impact.class,
                "from Impact where assigneeId=?1 and resolution='PENDING'",
                access.current().id)
            .stream()
            .filter(p -> ids.contains(p.incidentId))
            .toList();
    return Map.of(
        "tasks",
        tasks,
        "pendingReports",
        bounded(Calibration.class).stream().filter(c -> c.status.equals("PENDING")).toList(),
        "reviews",
        incidents.stream().filter(i -> i.status.equals("REVIEW")).toList(),
        "gauges",
        bounded(Gauge.class).stream()
            .filter(
                g ->
                    Set.of("QUARANTINED", "EXPIRED", "UNQUALIFIED")
                        .contains(GaugePolicy.status(g, clock.instant())))
            .map(this::gaugeView)
            .toList());
  }

  /** 审计按角色数据范围过滤。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    return bounded(AuditEvent.class);
  }

  /** 授权 JSON 报告复用明细，无额外广告载荷。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object report(String kind, Long id) {
    access.require("export");
    return detail(kind, id);
  }

  private Long required(Long id) {
    if (id == null) throw new Problem(400, "INVALID_INPUT");
    return id;
  }

  private Gauge gauge(Long id) {
    var g = db.get(Gauge.class, id);
    access.department(g.departmentId);
    return g;
  }

  private void gate(String permission) {
    db.lock(Department.class, 1L);
    var a = access.current();
    db.refresh(a);
    db.refresh(db.get(AccessRole.class, a.roleId));
    access.require(permission);
  }

  private Class<?> type(String kind) {
    return switch (kind) {
      case "gauges" -> Gauge.class;
      case "calibrations" -> Calibration.class;
      case "uses" -> GaugeUse.class;
      case "incidents" -> Incident.class;
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  private String scope() {
    return "ALL".equals(access.role().scope) ? "1=1" : "e.departmentId=:department";
  }

  private <T> List<T> bounded(Class<T> type) {
    var q =
        db.jpql(type, "from " + type.getSimpleName() + " e where " + scope() + " order by e.id");
    if (!"ALL".equals(access.role().scope))
      q.setParameter("department", access.current().departmentId);
    var out = q.setMaxResults(10001).getResultList();
    if (out.size() > 10000) throw new Problem(400, "REPORT_LIMIT");
    return out;
  }

  private Map<String, Object> gaugeView(Gauge g) {
    var m = new LinkedHashMap<String, Object>();
    m.put("id", g.id);
    m.put("version", g.version);
    m.put("code", g.code);
    m.put("name", g.name);
    m.put("model", g.model);
    m.put("category", g.category);
    m.put("departmentId", g.departmentId);
    m.put("status", GaugePolicy.status(g, clock.instant()));
    m.put("validUntil", g.validUntil);
    m.put("lastPassAt", g.lastPassAt);
    m.put("lastFailAt", g.lastFailAt);
    m.put("approvedReportId", g.approvedReportId);
    m.put("createdAt", g.createdAt);
    return m;
  }

  private void event(String kind, Long id, Long department, String action, String note) {
    var e = new FlowEvent();
    e.kind = kind;
    e.objectId = id;
    e.departmentId = department;
    e.actorId = access.current().id;
    e.action = action;
    e.note = note;
    e.createdAt = clock.instant();
    db.save(e);
    access.audit(kind + ":" + action, id, department);
  }

  private String fingerprint(String content) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest((access.current().id + ":" + content).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private boolean replay(String key, String content) {
    if (key == null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
      throw new Problem(400, "INVALID_REQUEST_KEY");
    var prior = db.query(CommandRecord.class, "from CommandRecord where requestKey=?1", key);
    if (prior.isEmpty()) return false;
    if (!prior.getFirst().fingerprint.equals(fingerprint(content)))
      throw new Problem(409, "IDEMPOTENCY_CONFLICT");
    return true;
  }

  private void stamp(String key, String content) {
    var c = new CommandRecord();
    c.requestKey = key;
    c.fingerprint = fingerprint(content);
    db.save(c);
  }
}
