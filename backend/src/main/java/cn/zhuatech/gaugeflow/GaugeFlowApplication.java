// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.gaugeflow;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** 计量器具校准与超差追溯服务入口。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@SpringBootApplication
public class GaugeFlowApplication {
  /** 启动计量服务。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
  public static void main(String[] args) {
    SpringApplication.run(GaugeFlowApplication.class, args);
  }

  /** 提供可替换的业务时钟。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
