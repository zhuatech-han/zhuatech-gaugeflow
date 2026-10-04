// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { test } from "node:test";
import assert from "node:assert/strict";
import { instant, localInput, actions, date } from "./schema.js";
test("Shanghai input is independent of browser timezone", () =>
  assert.equal(instant("2026-10-05T10:00"), "2026-10-05T02:00:00.000Z"));
test("Nonexistent date is rejected", () =>
  assert.throws(() => instant("2026-02-30T10:00")));
test("UTC round trip preserves Shanghai input", () =>
  assert.equal(localInput("2026-10-05T02:00:00Z"), "2026-10-05T10:00"));
test("Date only validity is not shifted by timezone", () =>
  assert.equal(date("2026-10-05"), "2026-10-05"));
const me = {
  id: 2,
  departmentId: 1,
  scope: "DEPARTMENT",
  permissions: ["calibration.review", "incident.review", "gauge.manage"],
};
test("Submitter has no self review button", () =>
  assert.deepEqual(
    actions(
      "calibrations",
      { departmentId: 1, status: "PENDING", creatorId: 2 },
      me,
    ),
    [],
  ));
test("Assessor has no incident close button", () =>
  assert.deepEqual(
    actions(
      "incidents",
      { departmentId: 1, status: "REVIEW", creatorId: 3 },
      me,
      [{ impact: { assessorId: 2 } }],
    ),
    [],
  ));
test("Other department has no mutation button", () =>
  assert.deepEqual(
    actions("gauges", { departmentId: 8, status: "QUARANTINED" }, me),
    [],
  ));
test("Independent reviewer can close or return", () =>
  assert.deepEqual(
    actions(
      "incidents",
      { departmentId: 1, status: "REVIEW", creatorId: 3 },
      me,
    ),
    ["close", "return"],
  ));
