// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.gaugeflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 接口真实事务测试：追溯、权限、独立复核、精确重试及冻结竞态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(GaugeIntegrationTest.TimeConfig.class)
class GaugeIntegrationTest {
  static final String password = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("gaugeflow.admin-password", () -> password);
  }

  /** 测试专用时间，不向运行应用暴露修改时钟入口。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static class MutableClock extends Clock {
    volatile Instant value = Instant.parse("2026-10-05T02:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId z) {
      return this;
    }

    public Instant instant() {
      return value;
    }
  }

  /** 固定业务日期以验证过期边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired MockMvc mvc;
  @Autowired MutableClock clock;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, writer, reviewer, outsider;
  long dept, gauge, writerId, reviewerId;
  String suffix, writerName;
  JsonNode record;

  @BeforeEach
  void setup() throws Exception {
    clock.value = Instant.parse("2026-10-05T02:00:00Z");
    admin = login("admin", password);
    suffix = UUID.randomUUID().toString().substring(0, 8);
    dept =
        ok(admin, "POST", "/admin/departments", Map.of("name", "计量验收-" + suffix))
            .path("id")
            .asLong();
    long w = 0, r = 0, u = 0;
    for (var x : ok(admin, "GET", "/admin/roles", null)) {
      if (x.path("name").asString().equals("计量员")) w = x.path("id").asLong();
      if (x.path("name").asString().equals("质量复核员")) r = x.path("id").asLong();
      if (x.path("name").asString().equals("使用人员")) u = x.path("id").asLong();
    }
    writerName = "writer-" + suffix;
    writerId = user(writerName, w, dept);
    reviewerId = user("review-" + suffix, r, dept);
    user("outside-" + suffix, u, 1);
    writer = login(writerName, password);
    reviewer = login("review-" + suffix, password);
    outsider = login("outside-" + suffix, password);
    gauge =
        ok(
                writer,
                "POST",
                "/gauges",
                Map.of(
                    "code",
                    "G-" + suffix,
                    "name",
                    "验收千分尺",
                    "model",
                    "0–25mm",
                    "category",
                    "LENGTH",
                    "departmentId",
                    dept))
            .path("id")
            .asLong();
  }

  long user(String n, long role, long d) throws Exception {
    return ok(
            admin,
            "POST",
            "/admin/users",
            Map.of(
                "username",
                n,
                "displayName",
                n,
                "password",
                password,
                "roleId",
                role,
                "departmentId",
                d,
                "enabled",
                true))
        .path("id")
        .asLong();
  }

  MockHttpSession login(String n, String p) throws Exception {
    var res =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("username", n, "password", p))))
            .andReturn();
    assertEquals(200, res.getResponse().getStatus());
    return (MockHttpSession) res.getRequest().getSession(false);
  }

  JsonNode request(MockHttpSession s, String method, String path, Object body, int expected)
      throws Exception {
    var b =
        switch (method) {
          case "GET" -> get("/api" + path);
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          default -> delete("/api" + path);
        };
    if (s != null) b.session(s);
    if (!method.equals("GET")) b.with(csrf());
    if (body != null) b.contentType("application/json").content(json.writeValueAsString(body));
    var res = mvc.perform(b).andReturn().getResponse();
    assertEquals(expected, res.getStatus(), path + " " + res.getContentAsString());
    return json.readTree(res.getContentAsString());
  }

  JsonNode ok(MockHttpSession s, String m, String p, Object b) throws Exception {
    return request(s, m, p, b, 200);
  }

  JsonNode gauge() throws Exception {
    return ok(writer, "GET", "/gauges/" + gauge, null).path("record");
  }

  Map<String, Object> command(JsonNode r) {
    var m = new HashMap<String, Object>();
    m.put("version", r.path("version").asLong());
    m.put("requestKey", UUID.randomUUID().toString());
    m.put("note", "验收证据与范围已核实");
    return m;
  }

  JsonNode act(MockHttpSession s, String kind, JsonNode r, String a) throws Exception {
    return ok(s, "POST", "/" + kind + "/" + r.path("id").asLong() + "/commands/" + a, command(r));
  }

  Map<String, Object> calibration(String result) throws Exception {
    var m = new HashMap<String, Object>();
    m.put("gaugeId", gauge);
    m.put("version", gauge().path("version").asLong());
    m.put("reportNo", "R-" + UUID.randomUUID());
    m.put("provider", "验收校准机构");
    m.put("checkedAt", clock.value.minusSeconds(1).toString());
    m.put("validUntil", result.equals("PASS") ? "2026-11-05" : null);
    m.put("result", result);
    m.put("evidence", "验收校准点记录引用");
    m.put("requestKey", UUID.randomUUID().toString());
    return m;
  }

  JsonNode calibrate(String result) throws Exception {
    clock.value = clock.value.plusSeconds(2);
    return ok(writer, "POST", "/calibrations", calibration(result));
  }

  void available() throws Exception {
    act(reviewer, "calibrations", calibrate("PASS"), "approve");
  }

  Map<String, Object> usage() throws Exception {
    return Map.of(
        "gaugeId",
        gauge,
        "version",
        gauge().path("version").asLong(),
        "batchRef",
        "验收-B-01",
        "taskRef",
        "验收-Q-01",
        "product",
        "验收标准件",
        "note",
        "验收测量记录",
        "requestKey",
        UUID.randomUUID().toString());
  }

  JsonNode use() throws Exception {
    clock.value = clock.value.plusSeconds(2);
    return ok(writer, "POST", "/uses", usage());
  }

  JsonNode incident() throws Exception {
    for (var x : ok(writer, "GET", "/incidents", null).path("items"))
      if (x.path("gaugeId").asLong() == gauge)
        return ok(writer, "GET", "/incidents/" + x.path("id").asLong(), null);
    throw new IllegalStateException();
  }

  JsonNode fail() throws Exception {
    act(reviewer, "calibrations", calibrate("FAIL"), "approve");
    return incident();
  }

  void assess(JsonNode d) throws Exception {
    for (var x : d.path("impacts")) {
      var p = x.path("impact");
      var a = command(p);
      a.put("assigneeId", writerId);
      var assigned =
          ok(writer, "POST", "/impacts/" + p.path("id").asLong() + "/commands/assign", a);
      var c = command(assigned);
      c.put("resolution", "RETEST_PASS");
      ok(writer, "POST", "/impacts/" + p.path("id").asLong() + "/commands/assess", c);
    }
  }

  JsonNode submit() throws Exception {
    return act(writer, "incidents", incident().path("record"), "submit");
  }

  @Test
  void completeRecoveryFlow() throws Exception {
    available();
    use();
    var i = fail();
    assertEquals(1, i.path("impacts").size());
    assertEquals("QUARANTINED", gauge().path("status").asString());
    assess(i);
    act(reviewer, "incidents", submit(), "close");
    assertEquals("QUARANTINED", gauge().path("status").asString());
    available();
    assertEquals("QUARANTINED", gauge().path("status").asString());
    act(writer, "gauges", gauge(), "release");
    assertEquals("AVAILABLE", gauge().path("status").asString());
    use();
  }

  @Test
  void unqualifiedGaugeCannotBeUsed() throws Exception {
    request(writer, "POST", "/uses", usage(), 409);
  }

  @Test
  void reportSubmitterCannotReview() throws Exception {
    var c = calibrate("PASS");
    request(
        admin,
        "PUT",
        "/admin/users/" + writerId,
        Map.of(
            "username",
            writerName,
            "displayName",
            "验收管理员",
            "roleId",
            1,
            "departmentId",
            dept,
            "enabled",
            true),
        200);
    request(
        writer,
        "POST",
        "/calibrations/" + c.path("id").asLong() + "/commands/approve",
        command(c),
        403);
  }

  @Test
  void pendingReportBlocksUsage() throws Exception {
    available();
    calibrate("PASS");
    request(writer, "POST", "/uses", usage(), 409);
  }

  @Test
  void expiredGaugeCannotBeUsed() throws Exception {
    available();
    clock.value = Instant.parse("2026-11-05T16:00:00Z");
    request(writer, "POST", "/uses", usage(), 409);
    assertEquals("EXPIRED", gauge().path("status").asString());
    assertEquals(1, ok(writer, "GET", "/gauges?status=EXPIRED", null).path("total").asInt());
  }

  @Test
  void freezeIsImmediateBeforeReportReview() throws Exception {
    available();
    use();
    calibrate("FAIL");
    assertEquals(1, incident().path("impacts").size());
    request(writer, "POST", "/uses", usage(), 409);
  }

  @Test
  void retroactiveFailureIncludesUsageUntilRegistration() throws Exception {
    available();
    clock.value = clock.value.plusSeconds(2);
    var checked = clock.value;
    use();
    clock.value = clock.value.plusSeconds(20);
    var v = calibration("FAIL");
    v.put("checkedAt", checked.toString());
    ok(writer, "POST", "/calibrations", v);
    assertEquals(1, incident().path("impacts").size());
  }

  @Test
  void rejectedFailureStillFreezes() throws Exception {
    available();
    var c = calibrate("FAIL");
    act(reviewer, "calibrations", c, "reject");
    assertEquals("QUARANTINED", gauge().path("status").asString());
    assertEquals("OPEN", incident().path("record").path("status").asString());
  }

  @Test
  void incompleteImpactCannotSubmit() throws Exception {
    available();
    use();
    var i = fail();
    request(
        writer,
        "POST",
        "/incidents/" + i.path("record").path("id").asLong() + "/commands/submit",
        command(i.path("record")),
        409);
  }

  @Test
  void wrongAssessorRejected() throws Exception {
    available();
    use();
    var p = fail().path("impacts").get(0).path("impact");
    var a = command(p);
    a.put("assigneeId", reviewerId);
    var assigned = ok(writer, "POST", "/impacts/" + p.path("id").asLong() + "/commands/assign", a);
    var c = command(assigned);
    c.put("resolution", "NO_IMPACT");
    request(writer, "POST", "/impacts/" + p.path("id").asLong() + "/commands/assess", c, 403);
  }

  @Test
  void assessorCannotCloseOwnDecision() throws Exception {
    available();
    use();
    var d = fail();
    var p = d.path("impacts").get(0).path("impact");
    var a = command(p);
    a.put("assigneeId", reviewerId);
    p = ok(writer, "POST", "/impacts/" + p.path("id").asLong() + "/commands/assign", a);
    var c = command(p);
    c.put("resolution", "NO_IMPACT");
    ok(reviewer, "POST", "/impacts/" + p.path("id").asLong() + "/commands/assess", c);
    var i = submit();
    request(
        reviewer,
        "POST",
        "/incidents/" + i.path("id").asLong() + "/commands/close",
        command(i),
        403);
  }

  @Test
  void returnAllowsReassessment() throws Exception {
    available();
    use();
    assess(fail());
    var i = act(reviewer, "incidents", submit(), "return");
    assertEquals("OPEN", i.path("status").asString());
    assess(incident());
    act(reviewer, "incidents", submit(), "close");
  }

  @Test
  void zeroUsageRequiresExplicitScopeReview() throws Exception {
    available();
    var i = fail();
    assertEquals(0, i.path("impacts").size());
    var c = command(i.path("record"));
    c.put("note", "");
    request(
        writer,
        "POST",
        "/incidents/" + i.path("record").path("id").asLong() + "/commands/submit",
        c,
        400);
    act(reviewer, "incidents", submit(), "close");
  }

  @Test
  void closeAloneDoesNotRelease() throws Exception {
    available();
    fail();
    act(reviewer, "incidents", submit(), "close");
    request(writer, "POST", "/gauges/" + gauge + "/commands/release", command(gauge()), 409);
  }

  @Test
  void recoveryCalibrationCannotSkipOpenIncident() throws Exception {
    available();
    fail();
    available();
    request(writer, "POST", "/gauges/" + gauge + "/commands/release", command(gauge()), 409);
  }

  @Test
  void foreignDepartmentIsDeniedEverywhere() throws Exception {
    available();
    use();
    var d = fail();
    for (var path :
        List.of(
            "/gauges/" + gauge,
            "/incidents/" + d.path("record").path("id").asLong(),
            "/gauges/" + gauge + "/report.json")) request(outsider, "GET", path, null, 403);
    assertEquals(0, ok(outsider, "GET", "/gauges", null).path("total").asInt());
    request(outsider, "GET", "/admin/users", null, 403);
    request(outsider, "POST", "/calibrations", calibration("PASS"), 403);
  }

  @Test
  void staleVersionRejected() throws Exception {
    var g = gauge();
    ok(
        writer,
        "PUT",
        "/gauges/" + gauge,
        Map.of(
            "code",
            g.path("code").asString(),
            "name",
            "修订名称",
            "model",
            "25mm",
            "category",
            "LENGTH",
            "departmentId",
            dept,
            "version",
            g.path("version").asLong()));
    request(writer, "POST", "/gauges/" + gauge + "/commands/retire", command(g), 409);
  }

  @Test
  void historicalIdentityCannotBeChangedOrDeleted() throws Exception {
    available();
    var g = gauge();
    request(
        writer,
        "PUT",
        "/gauges/" + gauge,
        Map.of(
            "code",
            "new-" + suffix,
            "name",
            "修订",
            "model",
            "25mm",
            "category",
            "LENGTH",
            "departmentId",
            dept,
            "version",
            g.path("version").asLong()),
        409);
    request(
        writer, "DELETE", "/gauges/" + gauge + "?version=" + g.path("version").asLong(), null, 409);
  }

  @Test
  void exactRetryCreatesNoDuplicateUsage() throws Exception {
    available();
    var u = usage();
    ok(writer, "POST", "/uses", u);
    ok(writer, "POST", "/uses", u);
    assertEquals(1, ok(writer, "GET", "/uses", null).path("total").asInt());
    var v = new HashMap<>(u);
    v.put("product", "改变载荷");
    request(writer, "POST", "/uses", v, 409);
  }

  @Test
  void exactRetryDoesNotDuplicateFailIncident() throws Exception {
    available();
    clock.value = clock.value.plusSeconds(10);
    var c = calibration("FAIL");
    ok(writer, "POST", "/calibrations", c);
    ok(writer, "POST", "/calibrations", c);
    assertEquals(1, ok(writer, "GET", "/incidents", null).path("total").asInt());
  }

  @Test
  void voidedUsageStillAppearsInImpactSnapshot() throws Exception {
    available();
    var u = use();
    ok(writer, "POST", "/uses/" + u.path("id").asLong() + "/void", command(u));
    assertEquals("VOID", fail().path("impacts").get(0).path("usage").path("status").asString());
  }

  @Test
  void retiredGaugeRejectsFurtherReports() throws Exception {
    act(writer, "gauges", gauge(), "retire");
    request(writer, "POST", "/calibrations", calibration("PASS"), 409);
  }

  @Test
  void reportChronologyAndFutureRejected() throws Exception {
    available();
    var c = calibration("PASS");
    c.put("checkedAt", clock.value.plusSeconds(100).toString());
    request(writer, "POST", "/calibrations", c, 400);
    c.put("checkedAt", clock.value.minusSeconds(100).toString());
    request(writer, "POST", "/calibrations", c, 400);
  }

  @Test
  void anonymousAndCsrfProtected() throws Exception {
    request(null, "GET", "/gauges", null, 401);
    assertEquals(
        403,
        mvc.perform(
                post("/api/gauges").session(writer).contentType("application/json").content("{}"))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void disabledAccountSessionRevoked() throws Exception {
    var u = ok(admin, "GET", "/admin/users", null);
    JsonNode w = null;
    for (var a : u) if (a.path("id").asLong() == writerId) w = a;
    var v = json.convertValue(w, HashMap.class);
    v.put("enabled", false);
    ok(admin, "PUT", "/admin/users/" + writerId, v);
    request(writer, "GET", "/options", null, 401);
  }

  @Test
  void concurrentUseAndFreezeNeverLosesImpact() throws Exception {
    available();
    var u = usage();
    clock.value = clock.value.plusSeconds(20);
    var c = calibration("FAIL");
    var pool = Executors.newFixedThreadPool(2);
    try {
      var start = new CountDownLatch(1);
      var f1 =
          pool.submit(
              () -> {
                start.await();
                return mvc.perform(
                        post("/api/uses")
                            .session(writer)
                            .with(csrf())
                            .contentType("application/json")
                            .content(json.writeValueAsString(u)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      var f2 =
          pool.submit(
              () -> {
                start.await();
                return mvc.perform(
                        post("/api/calibrations")
                            .session(writer)
                            .with(csrf())
                            .contentType("application/json")
                            .content(json.writeValueAsString(c)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      start.countDown();
      int used = f1.get(), failed = f2.get();
      assertEquals(200, failed);
      assertTrue(used == 200 || used == 409);
      assertEquals(used == 200 ? 1 : 0, incident().path("impacts").size());
    } finally {
      pool.shutdownNow();
    }
  }
}
