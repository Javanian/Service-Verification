import { test, expect, Page } from "@playwright/test";
import path from "node:path";
const password = "local-technician-password";
async function login(page: Page, name: string, pass: string) {
  await page.goto("/");
  await page.getByLabel("Username", { exact: true }).fill(name);
  await page.getByLabel("Password", { exact: true }).fill(pass);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("button", { name: "Sign out" })).toBeVisible();
}
async function api(page: Page, url: string, body: unknown) {
  return page.evaluate(
    async ({ url, body }) => {
      const s = await (await fetch("/api/session")).json();
      const r = await fetch("/api" + url, {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": s.csrf },
        body: JSON.stringify(body),
      });
      return { status: r.status, body: await r.json() };
    },
    { url, body },
  );
}
test("owner and technician complete correction, approval, immutable revision and private print flow", async ({
  browser,
  page,
}) => {
  const suffix = Date.now().toString();
  const tech = "tech" + suffix;
  const other = "other" + suffix;
  await login(
    page,
    process.env["OWNER_USERNAME"] || "owner",
    process.env["OWNER_PASSWORD"] || "local-owner-password",
  );
  await page.getByText("Manage technicians", { exact: true }).click();
  await page.getByLabel("Technician username").fill(tech);
  await page.getByLabel("Temporary password").fill(password);
  await page
    .getByRole("button", { name: "Add technician", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText("Technician created");
  expect(
    (await api(page, "/technicians", { username: other, password })).status,
  ).toBe(200);
  await page.getByRole("button", { name: "+ New service job" }).click();
  await page
    .getByLabel("Customer", { exact: true })
    .fill("North Studio " + suffix);
  await page
    .getByLabel("Location", { exact: true })
    .fill("Bandung · Second floor");
  await page.getByLabel("External invoice reference").fill("INV-2026-008");
  await page.getByLabel("Assigned technician").selectOption(tech);
  await page.getByLabel("AC unit labels").fill("Lobby · AC-01");
  await page.getByRole("button", { name: "Create job", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Lobby · AC-01" }),
  ).toBeVisible();
  const techContext = await browser.newContext({
    viewport: { width: 390, height: 844 },
  });
  const tp = await techContext.newPage();
  await login(tp, tech, password);
  await tp.getByRole("button", { name: /North Studio/ }).click();
  await tp.getByRole("button", { name: "Submit for review" }).click();
  await expect(tp.getByRole("alert")).toContainText("Each unit needs");
  await tp.getByLabel("Coil and filter cleaned").check();
  await tp.getByLabel("Drain checked", { exact: true }).check();
  await tp.getByLabel("Cooling checked", { exact: true }).check();
  await tp
    .getByLabel("Service actions & observations")
    .fill("Cleaned coil and filter; flushed drain. Cooling restored.");
  await expect(
    tp.getByRole("button", { name: "Submit for review" }),
  ).toBeDisabled();
  await tp.route("**/photos/before", (route) => route.abort(), { times: 1 });
  await tp
    .getByLabel("Add before photo")
    .setInputFiles(path.resolve("fixtures/service.png"));
  await expect(tp.getByRole("button", { name: "Retry upload" })).toBeVisible();
  await tp.getByRole("button", { name: "Retry upload" }).click();
  await expect(tp.getByRole("button", { name: "Retry upload" })).toHaveCount(0);
  await tp
    .getByLabel("Add after photo")
    .setInputFiles(path.resolve("fixtures/service.png"));
  await expect(tp.getByRole("status")).toContainText("Photo saved");
  await expect(tp.getByLabel("Service actions & observations")).toHaveValue(
    "Cleaned coil and filter; flushed drain. Cooling restored.",
  );
  await tp.getByRole("button", { name: "Save checklist" }).click();
  await expect(tp.getByRole("status")).toContainText("Unit checklist saved");
  await expect(tp.locator("body")).toHaveJSProperty("scrollWidth", 390);
  await tp.screenshot({
    path: "../evidence/mobile-checklist.png",
    fullPage: true,
  });
  await tp.getByRole("button", { name: "Submit for review" }).click();
  await expect(tp.getByText("Submitted evidence is locked")).toBeVisible();
  await page.getByRole("button", { name: "Reload job" }).click();
  await page
    .getByLabel("Correction reason")
    .fill("Add the observed outlet temperature.");
  await page
    .getByRole("button", { name: "Request changes", exact: true })
    .click();
  await expect(
    page.getByText("CHANGES REQUESTED", { exact: true }),
  ).toBeVisible();
  await tp.getByRole("button", { name: "Reload job" }).click();
  await expect(
    tp
      .getByText("Add the observed outlet temperature.", { exact: true })
      .first(),
  ).toBeVisible();
  await tp
    .getByLabel("Service actions & observations")
    .fill("Cleaned coil and filter; flushed drain. Outlet temperature: 17 °C.");
  await tp.getByRole("button", { name: "Save checklist" }).click();
  await expect(tp.getByRole("status")).toContainText("Unit checklist saved");
  await tp.getByRole("button", { name: "Submit for review" }).click();
  await expect(tp.getByText("Submitted evidence is locked")).toBeVisible();
  await page.getByRole("button", { name: "Reload job" }).click();
  await page
    .getByRole("button", { name: "Approve report", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText("immutable report");
  await expect(
    page.getByLabel("Service actions & observations"),
  ).toBeDisabled();
  await page
    .getByRole("button", { name: "View approved report · Revision 1" })
    .click();
  await expect(
    page.getByRole("heading", { name: "Service report", exact: true }),
  ).toBeVisible();
  await page.locator(".report img").evaluateAll(async (images) => {
    await Promise.all(images.map((img) => (img as HTMLImageElement).decode()));
  });
  await page.screenshot({
    path: "../evidence/approved-report.png",
    fullPage: true,
  });
  await page.evaluate(() => {
    window.print = () => {
      document.body.dataset["printRequested"] = "true";
    };
  });
  await page.getByRole("button", { name: "Print / Save PDF" }).click();
  await expect(page.locator("body")).toHaveAttribute(
    "data-print-requested",
    "true",
  );
  await page.emulateMedia({ media: "print" });
  await expect(
    page.getByRole("button", { name: "Print / Save PDF" }),
  ).toBeHidden();
  await page.pdf({
    path: "../evidence/service-report.pdf",
    format: "A4",
    printBackground: true,
  });
  await page.emulateMedia({ media: "screen" });
  const photoUrl = await page
    .locator(".report img")
    .first()
    .getAttribute("src");
  const otherContext = await browser.newContext();
  const op = await otherContext.newPage();
  await login(op, other, password);
  await expect(op.getByRole("button", { name: /North Studio/ })).toHaveCount(0);
  expect((await op.request.get(photoUrl!)).status()).toBe(404);
  await otherContext.close();
  await page.getByRole("button", { name: "Back to job" }).click();
  await page.getByLabel("Reason for a new revision").fill("Follow-up visit");
  await page.getByRole("button", { name: "Start new revision" }).click();
  await expect(
    page.getByText("REVISION 2", { exact: false }).first(),
  ).toBeVisible();
  await page
    .getByLabel("Service actions & observations")
    .fill("Follow-up found cooling stable at 16 °C.");
  await page.getByRole("button", { name: "Save checklist" }).click();
  await expect(page.getByRole("status")).toContainText("Unit checklist saved");
  await page
    .getByRole("button", { name: "View approved report · Revision 1" })
    .click();
  await expect(page.locator(".actions-text")).toContainText("17 °C");
  await expect(page.locator(".actions-text")).not.toContainText("16 °C");
  await techContext.close();
});
test("invalid credentials and unauthenticated photo access are rejected", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByLabel("Username", { exact: true }).fill("nobody");
  await page.getByLabel("Password", { exact: true }).fill("wrong-password");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Sign in again");
  expect(
    (
      await page.request.get("/api/photos/00000000-0000-0000-0000-000000000001")
    ).status(),
  ).toBe(401);
});

test('expired session returns to sign-in without losing access to recovery', async ({ page, context }) => {
  await login(page, process.env['OWNER_USERNAME'] || 'owner', process.env['OWNER_PASSWORD'] || 'local-owner-password');
  await page.getByRole('button', { name: /North Studio/ }).first().click();
  await expect(page.getByRole('button', { name: 'Reload job' })).toBeVisible();
  await context.clearCookies();
  await page.getByRole('button', { name: 'Reload job' }).click();
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible();
  await expect(page.getByRole('alert')).toContainText('Sign in again');
});
