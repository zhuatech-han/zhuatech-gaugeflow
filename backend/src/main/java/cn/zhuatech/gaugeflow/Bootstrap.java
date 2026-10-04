// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.gaugeflow;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库初始化权限、目录及管理员，不初始化量具和批次。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${gaugeflow.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
  }

  /** 已有账号时不覆盖密码或业务数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    var d = new Department();
    d.name = "总部";
    db.save(d);
    var names =
        Map.ofEntries(
            Map.entry("gauge.read", "查看计量资料"),
            Map.entry("gauge.manage", "管理量具与冻结恢复"),
            Map.entry("calibration.write", "提交校准报告"),
            Map.entry("calibration.review", "独立审核校准"),
            Map.entry("use.write", "登记本人使用"),
            Map.entry("incident.manage", "分派与提交影响事件"),
            Map.entry("impact.assess", "评估分派记录"),
            Map.entry("incident.review", "独立复核影响事件"),
            Map.entry("dashboard", "计量统计"),
            Map.entry("export", "导出授权报告"),
            Map.entry("audit", "操作审计"),
            Map.entry("admin", "系统管理"));
    new TreeMap<>(names)
        .forEach(
            (k, v) -> {
              var p = new Permission();
              p.code = k;
              p.name = v;
              db.save(p);
            });
    role("管理员", "ALL", names.keySet());
    role("使用人员", "DEPARTMENT", Set.of("gauge.read", "use.write", "dashboard"));
    role(
        "计量员",
        "DEPARTMENT",
        Set.of(
            "gauge.read",
            "gauge.manage",
            "calibration.write",
            "use.write",
            "incident.manage",
            "impact.assess",
            "dashboard",
            "export",
            "audit"));
    role(
        "质量复核员",
        "DEPARTMENT",
        Set.of(
            "gauge.read",
            "calibration.review",
            "incident.review",
            "impact.assess",
            "dashboard",
            "export",
            "audit"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.passwordHash = encoder.encode(password);
    a.roleId = db.all(AccessRole.class).getFirst().id;
    a.departmentId = d.id;
    a.enabled = true;
    db.save(a);
    String[][] menus = {
      {"workbench", "计量工作台", "Workbench", "gauge.read"},
      {"gauges", "量具台账", "Gauges", "gauge.read"},
      {"calibrations", "校准审核", "Calibrations", "gauge.read"},
      {"uses", "使用记录", "Usage", "gauge.read"},
      {"incidents", "超差追溯", "Impact investigations", "gauge.read"},
      {"dashboard", "统计报表", "Statistics", "dashboard"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles", "admin"},
      {"departments", "部门管理", "Departments", "admin"},
      {"menus", "导航管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "量具分类", "Gauge types", "admin"},
      {"settings", "系统参数", "Settings", "admin"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    Map.of("timezone", "Asia/Shanghai", "companyName", "知华计量协作", "dueSoonDays", "30")
        .forEach(
            (k, v) -> {
              var s = new SystemSetting();
              s.code = k;
              s.value = v;
              db.save(s);
            });
    String[][] kinds = {
      {"LENGTH", "长度量具", "Length"}, {"WEIGHT", "称量器具", "Weighing"}, {"OTHER", "其他器具", "Other"}
    };
    for (var k : kinds) {
      var e = new DictionaryEntry();
      e.type = "gauge";
      e.code = k[0];
      e.name = k[1];
      e.nameEn = k[2];
      db.save(e);
    }
  }

  private void role(String n, String s, Set<String> p) {
    var r = new AccessRole();
    r.name = n;
    r.scope = s;
    r.permissions = new HashSet<>(p);
    db.save(r);
  }
}
