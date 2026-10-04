// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.gaugeflow;

import jakarta.persistence.*;
import java.time.*;

/** 超差追溯事件；冻结窗口采用上次合格校准至本次报告登记时间。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "incident")
public class Incident {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version public Long version;

  @Column(nullable = false)
  public long revision;

  @Column(name = "gauge_id", nullable = false)
  public Long gaugeId;

  @Column(name = "calibration_id", nullable = false)
  public Long calibrationId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "creator_id", nullable = false)
  public Long creatorId;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "window_from", nullable = false)
  public Instant windowFrom;

  @Column(name = "window_to", nullable = false)
  public Instant windowTo;

  @Column(name = "scope_note", nullable = false, length = 2000)
  public String scopeNote;

  @Column(name = "review_note", length = 2000)
  public String reviewNote;

  @Column(name = "reviewer_id")
  public Long reviewerId;

  @Column(name = "closed_at")
  public Instant closedAt;
}
