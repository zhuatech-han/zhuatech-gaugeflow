// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.gaugeflow;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

/** 上海截止日、冻结优先级及报告日期边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class GaugePolicyTest {
  Gauge gauge() {
    var g = new Gauge();
    g.status = "AVAILABLE";
    g.validUntil = LocalDate.parse("2026-10-05");
    g.approvedReportId = 1L;
    return g;
  }

  @Test
  void validUntilEndOfShanghaiDay() {
    assertEquals("AVAILABLE", GaugePolicy.status(gauge(), Instant.parse("2026-10-05T15:59:59Z")));
  }

  @Test
  void expiresAtShanghaiMidnight() {
    assertEquals("EXPIRED", GaugePolicy.status(gauge(), Instant.parse("2026-10-05T16:00:00Z")));
  }

  @Test
  void quarantineDoesNotDisappearAtExpiry() {
    var g = gauge();
    g.status = "QUARANTINED";
    assertEquals("QUARANTINED", GaugePolicy.status(g, Instant.parse("2026-10-06T16:00:00Z")));
  }

  @Test
  void futureReportRejected() {
    assertThrows(
        Problem.class,
        () ->
            GaugePolicy.calibration(
                Instant.parse("2026-10-06T00:00:00Z"),
                LocalDate.parse("2026-11-05"),
                "PASS",
                null,
                Instant.parse("2026-10-05T00:00:00Z")));
  }

  @Test
  void orderedReportsRequired() {
    var t = Instant.parse("2026-10-05T00:00:00Z");
    assertThrows(Problem.class, () -> GaugePolicy.calibration(t, null, "FAIL", t, t));
  }

  @Test
  void failureMustNotHaveValidity() {
    var t = Instant.parse("2026-10-05T00:00:00Z");
    assertThrows(
        Problem.class,
        () -> GaugePolicy.calibration(t, LocalDate.parse("2026-11-05"), "FAIL", null, t));
  }

  @Test
  void pastExpiryCannotPrecedeCheckDay() {
    var t = Instant.parse("2026-10-05T00:00:00Z");
    assertThrows(
        Problem.class,
        () -> GaugePolicy.calibration(t, LocalDate.parse("2026-10-04"), "PASS", null, t));
  }

  @Test
  void staleVersionRejected() {
    assertThrows(Problem.class, () -> GaugePolicy.version(3L, 2L));
  }
}
