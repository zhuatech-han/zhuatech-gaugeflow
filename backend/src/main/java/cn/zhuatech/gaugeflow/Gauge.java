// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.gaugeflow;

import jakarta.persistence.*;
import java.time.*;

/** 量具台账与最近审核通过的有效性；历史引用后不删除。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "gauge")
public class Gauge {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version public Long version;

  @Column(nullable = false)
  public long revision;

  @Column(name = "code", nullable = false, length = 60)
  public String code;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "model", nullable = false, length = 120)
  public String model;

  @Column(name = "category", nullable = false, length = 60)
  public String category;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "last_pass_at")
  public Instant lastPassAt;

  @Column(name = "valid_until")
  public LocalDate validUntil;

  @Column(name = "approved_report_id")
  public Long approvedReportId;

  @Column(name = "last_fail_at")
  public Instant lastFailAt;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
