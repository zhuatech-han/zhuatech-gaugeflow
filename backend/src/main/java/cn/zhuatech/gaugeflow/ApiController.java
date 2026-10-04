// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.gaugeflow;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 计量业务及系统管理接口。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final GaugeService service;
  final AdminService admin;

  public ApiController(GaugeService service, AdminService admin) {
    this.service = service;
    this.admin = admin;
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return service.options();
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/workbench")
  public Object workbench() {
    return service.workbench();
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return service.dashboard();
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return service.audit();
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping({"/gauges", "/calibrations", "/uses", "/incidents"})
  public Object list(
      jakarta.servlet.http.HttpServletRequest req,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return service.list(
        req.getRequestURI().substring(req.getContextPath().length() + 5),
        search,
        status,
        page,
        size,
        sort);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/gauges/{id}")
  public Object gaugesDetail(@PathVariable Long id) {
    return service.detail("gauges", id);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/gauges/{id}/report.json")
  public ResponseEntity<Object> gaugesReport(@PathVariable Long id) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gauges-" + id + ".json")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(service.report("gauges", id));
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/calibrations/{id}")
  public Object calibrationsDetail(@PathVariable Long id) {
    return service.detail("calibrations", id);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/calibrations/{id}/report.json")
  public ResponseEntity<Object> calibrationsReport(@PathVariable Long id) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=calibrations-" + id + ".json")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(service.report("calibrations", id));
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/uses/{id}")
  public Object usesDetail(@PathVariable Long id) {
    return service.detail("uses", id);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/uses/{id}/report.json")
  public ResponseEntity<Object> usesReport(@PathVariable Long id) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=uses-" + id + ".json")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(service.report("uses", id));
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/incidents/{id}")
  public Object incidentsDetail(@PathVariable Long id) {
    return service.detail("incidents", id);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/incidents/{id}/report.json")
  public ResponseEntity<Object> incidentsReport(@PathVariable Long id) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=incidents-" + id + ".json")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(service.report("incidents", id));
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/gauges")
  public Object createGauge(@RequestBody GaugeService.GaugeInput v) {
    return service.saveGauge(null, v);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/gauges/{id}")
  public Object saveGauge(@PathVariable Long id, @RequestBody GaugeService.GaugeInput v) {
    return service.saveGauge(id, v);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/gauges/{id}")
  public Object deleteGauge(@PathVariable Long id, @RequestParam Long version) {
    service.deleteGauge(id, version);
    return Map.of("ok", true);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/calibrations")
  public Object calibrate(@RequestBody GaugeService.CalibrationInput v) {
    return service.calibrate(v);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/uses")
  public Object use(@RequestBody GaugeService.UseInput v) {
    return service.use(v);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/gauges/{id}/commands/{action}")
  public Object gaugesCommand(
      @PathVariable Long id, @PathVariable String action, @RequestBody GaugeService.Command v) {
    return service.gaugeCommand(id, action, v);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/calibrations/{id}/commands/{action}")
  public Object calibrationsCommand(
      @PathVariable Long id, @PathVariable String action, @RequestBody GaugeService.Command v) {
    return service.calibrationCommand(id, action, v);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/incidents/{id}/commands/{action}")
  public Object incidentsCommand(
      @PathVariable Long id, @PathVariable String action, @RequestBody GaugeService.Command v) {
    return service.incidentCommand(id, action, v);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/impacts/{id}/commands/{action}")
  public Object impactsCommand(
      @PathVariable Long id, @PathVariable String action, @RequestBody GaugeService.Command v) {
    return service.impactCommand(id, action, v);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/uses/{id}/void")
  public Object voidUse(@PathVariable Long id, @RequestBody GaugeService.Command v) {
    return service.voidUse(id, v);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object listAdmin(@PathVariable String type) {
    return admin.list(type);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object createAdmin(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object saveAdmin(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object deleteAdmin(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
