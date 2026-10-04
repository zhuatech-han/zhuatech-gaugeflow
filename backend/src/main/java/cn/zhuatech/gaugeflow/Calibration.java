// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.gaugeflow;

import jakarta.persistence.*;
import java.time.*;

/** 校准报告的提交事实及独立审核结论；提交后事实不可覆盖。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "calibration")
public class Calibration {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version public Long version;

  @Column(name = "gauge_id", nullable = false)
  public Long gaugeId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "report_no", nullable = false, length = 120)
  public String reportNo;

  @Column(name = "provider", nullable = false, length = 120)
  public String provider;

  @Column(name = "checked_at", nullable = false)
  public Instant checkedAt;

  @Column(name = "valid_until")
  public LocalDate validUntil;

  @Column(name = "result", nullable = false, length = 10)
  public String result;

  @Column(name = "evidence", nullable = false, length = 4000)
  public String evidence;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "creator_id", nullable = false)
  public Long creatorId;

  @Column(name = "reviewer_id")
  public Long reviewerId;

  @Column(name = "review_note", length = 2000)
  public String reviewNote;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "reviewed_at")
  public Instant reviewedAt;
}
