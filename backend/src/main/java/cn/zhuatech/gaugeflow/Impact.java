// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.gaugeflow;

import jakarta.persistence.*;
import java.time.*;

/** 单条潜在受影响使用记录的责任分配和人工评估证据。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "impact")
public class Impact {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version public Long version;

  @Column(name = "incident_id", nullable = false)
  public Long incidentId;

  @Column(name = "use_id", nullable = false)
  public Long useId;

  @Column(name = "assignee_id")
  public Long assigneeId;

  @Column(name = "assessor_id")
  public Long assessorId;

  @Column(name = "resolution", nullable = false, length = 30)
  public String resolution;

  @Column(name = "evidence", length = 4000)
  public String evidence;

  @Column(name = "assessed_at")
  public Instant assessedAt;
}
