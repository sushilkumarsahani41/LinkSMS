import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "tests",
  testMatch: "**/*.spec.js",
  use: {
    browserName: "chromium",
    channel: "chrome",
    viewport: { width: 1440, height: 1000 },
  },
  outputDir: "work/playwright-results",
  reporter: "list",
});
