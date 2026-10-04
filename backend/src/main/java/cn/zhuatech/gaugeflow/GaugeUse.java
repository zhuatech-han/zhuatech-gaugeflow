// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.gaugeflow;

import jakarta.persistence.*;
import java.time.*;

/** 已发生的当前量具使用；保留批次及任务引用，冲销不删除原始事实。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "gauge_use")
public class GaugeUse {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version public Long version;

  @Column(name = "gauge_id", nullable = false)
  public Long gaugeId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "report_id", nullable = false)
  public Long reportId;

  @Column(name = "operator_id", nullable = false)
  public Long operatorId;

  @Column(name = "batch_ref", nullable = false, length = 120)
  public String batchRef;

  @Column(name = "task_ref", nullable = false, length = 120)
  public String taskRef;

  @Column(name = "product", nullable = false, length = 120)
  public String product;

  @Column(name = "note", nullable = false, length = 2000)
  public String note;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "void_reason", length = 2000)
  public String voidReason;

  @Column(name = "used_at", nullable = false)
  public Instant usedAt;
}
