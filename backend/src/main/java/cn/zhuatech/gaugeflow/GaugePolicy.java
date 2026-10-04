// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.gaugeflow;

import java.time.*;
import java.util.*;

/** 校准日期、有效性与版本的统一规则。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class GaugePolicy {
  private GaugePolicy() {}

  /** 有效截止日按上海日期含当天；冻结和报废优先于到期状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String status(Gauge g, Instant now) {
    if ("AVAILABLE".equals(g.status) && (g.validUntil == null || g.validUntil.isBefore(today(now))))
      return "EXPIRED";
    return g.status;
  }

  /** 使用及恢复必须有当前有效的独立审核报告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void usable(Gauge g, Instant now) {
    if (!"AVAILABLE".equals(status(g, now)) || g.approvedReportId == null)
      throw new Problem(409, "GAUGE_UNAVAILABLE");
  }

  /** 拒绝未来校准、乱序报告与不合理的合格截止日期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void calibration(
      Instant checked, LocalDate until, String result, Instant previous, Instant now) {
    if (checked == null || checked.isAfter(now) || previous != null && !checked.isAfter(previous))
      throw new Problem(400, "INVALID_CALIBRATION_TIME");
    if (!Set.of("PASS", "FAIL").contains(Objects.toString(result, "")))
      throw new Problem(400, "INVALID_INPUT");
    if ("PASS".equals(result) && (until == null || until.isBefore(today(checked))))
      throw new Problem(400, "INVALID_VALIDITY");
    if ("FAIL".equals(result) && until != null) throw new Problem(400, "INVALID_VALIDITY");
  }

  /** 乐观版本要求用户基于当前事实操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void version(Long actual, Long requested) {
    if (requested == null || !Objects.equals(actual, requested))
      throw new Problem(409, "STALE_VERSION");
  }

  /** 全系统使用固定上海业务日期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static LocalDate today(Instant now) {
    return now.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
  }
}
