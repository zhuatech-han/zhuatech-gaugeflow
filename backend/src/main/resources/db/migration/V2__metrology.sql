-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

CREATE TABLE gauge (
  revision bigint NOT NULL DEFAULT 0,
  id bigint AUTO_INCREMENT PRIMARY KEY,
  version bigint NOT NULL DEFAULT 0,
  code varchar(60) NOT NULL,
  name varchar(120) NOT NULL,
  model varchar(120) NOT NULL,
  category varchar(60) NOT NULL,
  department_id bigint NOT NULL,
  status varchar(20) NOT NULL,
  last_pass_at timestamp(6),
  valid_until date,
  approved_report_id bigint,
  last_fail_at timestamp(6),
  created_at timestamp(6) NOT NULL,
  UNIQUE(code),
  FOREIGN KEY(department_id) REFERENCES department(id)
);

CREATE TABLE calibration (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  version bigint NOT NULL DEFAULT 0,
  gauge_id bigint NOT NULL,
  department_id bigint NOT NULL,
  report_no varchar(120) NOT NULL,
  provider varchar(120) NOT NULL,
  checked_at timestamp(6) NOT NULL,
  valid_until date,
  result varchar(10) NOT NULL,
  evidence varchar(4000) NOT NULL,
  status varchar(20) NOT NULL,
  creator_id bigint NOT NULL,
  reviewer_id bigint,
  review_note varchar(2000),
  created_at timestamp(6) NOT NULL,
  reviewed_at timestamp(6),
  UNIQUE(gauge_id,report_no),
  FOREIGN KEY(gauge_id) REFERENCES gauge(id),
  FOREIGN KEY(department_id) REFERENCES department(id),
  FOREIGN KEY(creator_id) REFERENCES account(id),
  FOREIGN KEY(reviewer_id) REFERENCES account(id)
);

CREATE TABLE gauge_use (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  version bigint NOT NULL DEFAULT 0,
  gauge_id bigint NOT NULL,
  department_id bigint NOT NULL,
  report_id bigint NOT NULL,
  operator_id bigint NOT NULL,
  batch_ref varchar(120) NOT NULL,
  task_ref varchar(120) NOT NULL,
  product varchar(120) NOT NULL,
  note varchar(2000) NOT NULL,
  status varchar(20) NOT NULL,
  void_reason varchar(2000),
  used_at timestamp(6) NOT NULL,
  FOREIGN KEY(gauge_id) REFERENCES gauge(id),
  FOREIGN KEY(report_id) REFERENCES calibration(id),
  FOREIGN KEY(department_id) REFERENCES department(id),
  FOREIGN KEY(operator_id) REFERENCES account(id)
);

CREATE TABLE incident (
  revision bigint NOT NULL DEFAULT 0,
  id bigint AUTO_INCREMENT PRIMARY KEY,
  version bigint NOT NULL DEFAULT 0,
  gauge_id bigint NOT NULL,
  calibration_id bigint NOT NULL,
  department_id bigint NOT NULL,
  creator_id bigint NOT NULL,
  status varchar(20) NOT NULL,
  window_from timestamp(6) NOT NULL,
  window_to timestamp(6) NOT NULL,
  scope_note varchar(2000) NOT NULL,
  review_note varchar(2000),
  reviewer_id bigint,
  closed_at timestamp(6),
  UNIQUE(calibration_id),
  FOREIGN KEY(gauge_id) REFERENCES gauge(id),
  FOREIGN KEY(calibration_id) REFERENCES calibration(id),
  FOREIGN KEY(department_id) REFERENCES department(id),
  FOREIGN KEY(creator_id) REFERENCES account(id),
  FOREIGN KEY(reviewer_id) REFERENCES account(id)
);

CREATE TABLE impact (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  version bigint NOT NULL DEFAULT 0,
  incident_id bigint NOT NULL,
  use_id bigint NOT NULL,
  assignee_id bigint,
  assessor_id bigint,
  resolution varchar(30) NOT NULL,
  evidence varchar(4000),
  assessed_at timestamp(6),
  UNIQUE(incident_id,use_id),
  FOREIGN KEY(incident_id) REFERENCES incident(id),
  FOREIGN KEY(use_id) REFERENCES gauge_use(id),
  FOREIGN KEY(assignee_id) REFERENCES account(id),
  FOREIGN KEY(assessor_id) REFERENCES account(id)
);

CREATE TABLE flow_event (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  kind varchar(20) NOT NULL,
  object_id bigint NOT NULL,
  department_id bigint NOT NULL,
  actor_id bigint NOT NULL,
  action varchar(40) NOT NULL,
  note varchar(2000) NOT NULL,
  created_at timestamp(6) NOT NULL,
  FOREIGN KEY(department_id) REFERENCES department(id),
  FOREIGN KEY(actor_id) REFERENCES account(id)
);

CREATE TABLE command_record (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  request_key varchar(36) NOT NULL,
  fingerprint varchar(64) NOT NULL,
  UNIQUE(request_key)
);

ALTER TABLE gauge ADD CONSTRAINT fk_gauge_report FOREIGN KEY(approved_report_id) REFERENCES calibration(id);

CREATE INDEX ix_gauge_scope ON gauge(department_id,status);

CREATE INDEX ix_use_trace ON gauge_use(gauge_id,used_at);

CREATE INDEX ix_calibration_scope ON calibration(department_id,status);

CREATE INDEX ix_incident_scope ON incident(department_id,status);

CREATE INDEX ix_impact_assignee ON impact(assignee_id,resolution);

CREATE INDEX ix_flow_event ON flow_event(kind,object_id,created_at);
