import { defineConfig } from "@playwright/test";
const baseURL = process.env["BASE_URL"] || "http://localhost:4200";
if (!["localhost", "127.0.0.1", "[::1]"].includes(new URL(baseURL).hostname)) {
  throw new Error("Browser fixtures require a disposable loopback instance");
}
export default defineConfig({
  testDir: "e2e",
  workers: 1,
  timeout: 90000,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    launchOptions: process.env["CHROMIUM_PATH"]
      ? { executablePath: process.env["CHROMIUM_PATH"] }
      : {},
  },
});
