import { test } from "node:test";
import assert from "node:assert/strict";
import { complete, editable } from "../src/model.ts";
test("completeness requires every check, nonblank actions and both photos", () => {
  const unit = {
    cleaned: true,
    drain_checked: true,
    cooling_checked: true,
    actions: "Cleaned drain",
    before_id: "before",
    after_id: "after",
  };
  assert.equal(complete(unit), true);
  for (const key of [
    "cleaned",
    "drain_checked",
    "cooling_checked",
    "before_id",
    "after_id",
  ])
    assert.equal(complete({ ...unit, [key]: false }), false);
  assert.equal(complete({ ...unit, actions: "  " }), false);
});
test("submitted and approved records are not editable", () => {
  for (const status of ["SUBMITTED", "APPROVED"])
    assert.equal(editable({ status }), false);
  for (const status of ["DRAFT", "CHANGES_REQUESTED"])
    assert.equal(editable({ status }), true);
});
