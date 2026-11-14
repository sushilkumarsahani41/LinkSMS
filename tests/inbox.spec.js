import { test, expect } from "@playwright/test";
test("demo inbox filters devices, searches and copies an OTP", async ({
  page,
  context,
}) => {
  await context.grantPermissions(["clipboard-read", "clipboard-write"]);
  await page.goto("http://127.0.0.1:5173/?demo=1");
  const demo = page.getByRole("button", { name: "Explore the demo" });
  if (await demo.isVisible()) await demo.click();
  await expect(
    page.getByRole("heading", { name: "Your messages, together." }),
  ).toBeVisible();
  await expect(page.locator(".message-row")).toHaveCount(5);
  await page.getByRole("button", { name: "Copy code", exact: true }).click();
  await expect
    .poll(() => page.evaluate(() => navigator.clipboard.readText()))
    .toBe("482913");
  await page.getByRole("button", { name: "POCO Pad Sync enabled" }).click();
  await expect(page.locator(".message-row")).toHaveCount(2);
  await page.getByRole("button", { name: "All messages" }).click();
  await page
    .getByRole("button", { name: "Verification codes", exact: true })
    .click();
  await expect(page.locator(".message-row")).toHaveCount(2);
  await page.getByRole("textbox", { name: "Search messages" }).fill("709821");
  await expect(page.locator(".message-row")).toHaveCount(1);
  await expect(page.locator(".otp-card strong")).toHaveText("709821");
});
test("setup generates a valid key and inbox fits narrow screens", async ({
  page,
}) => {
  await page.goto("http://127.0.0.1:5173/?demo=1");
  const demo = page.getByRole("button", { name: "Explore the demo" });
  if (await demo.isVisible()) await demo.click();
  await page.getByRole("button", { name: "Settings & setup" }).click();
  await page.getByRole("button", { name: "Generate key", exact: true }).click();
  const key = await page
    .getByLabel("Encryption key", { exact: true })
    .inputValue();
  expect(Buffer.from(key, "base64").length).toBe(32);
  await page.getByRole("button", { name: "Close settings" }).click();
  await page.screenshot({ path: "work/web-desktop.png", fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("heading", { name: "Your messages, together." }),
  ).toBeVisible();
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth),
  ).toBeLessThanOrEqual(390);
  await page.screenshot({ path: "work/web-mobile.png", fullPage: true });
});

test("account form rejects mismatched passwords before contacting Firebase", async ({
  page,
}) => {
  await page.goto("http://127.0.0.1:5173/");
  await page.getByRole("button", { name: "New here? Create account" }).click();
  await page
    .getByLabel("Email", { exact: true })
    .fill("local-validation@example.invalid");
  await page.getByLabel("Password", { exact: true }).fill("local-only-example");
  await page
    .getByLabel("Confirm password", { exact: true })
    .fill("different-example");
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "The passwords do not match.",
  );
});
