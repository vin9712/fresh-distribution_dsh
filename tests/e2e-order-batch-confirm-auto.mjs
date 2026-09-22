/**
 * 按客户批量确认草稿订单：自动查询 E2E
 *
 * 需求：抽屉里的「查询」按钮去掉，选客户 / 改配送日期后自动加载草稿订单。
 *
 * 步骤：登录 → 录单页 → 打开「按客户批量确认」抽屉
 *      → 断言无「查询」按钮
 *      → 选客户「丽宫」→ 断言自动发出草稿查询请求（无需点按钮）
 *      → 改配送日期为有草稿的日期 → 断言自动再次查询且列表过滤为 2 条
 *      → 关闭抽屉（不提交，无数据变更）
 *
 * 运行：node tests/e2e-order-batch-confirm-auto.mjs
 */
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const problems = [];
let saleListReqs = 0;

async function main() {
  const browser = await chromium.launch({ channel: "chrome", headless: true });
  const page = await browser.newPage({ viewport: { width: 1600, height: 900 } });

  page.on("pageerror", (e) => problems.push(`[pageerror] ${page.url()} :: ${e.message}`));
  page.on("console", (m) => {
    if (m.type() === "error") problems.push(`[console.error] ${page.url()} :: ${m.text().slice(0, 300)}`);
  });
  page.on("response", (r) => {
    if (r.status() >= 500) problems.push(`[http${r.status()}] ${r.url()}`);
  });
  page.on("request", (r) => {
    if (r.url().includes("/order/sale/list")) saleListReqs++;
  });

  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder*="账号"]', "admin");
  await page.fill('input[placeholder*="密码"]', "admin123");
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });

  await page.goto(BASE + "/order/sale-detail/index/", { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".split-workspace", { timeout: 20000 });
  await page.waitForTimeout(2500);

  // ---------- 打开抽屉 ----------
  await page.click('button:has-text("按客户批量确认")');
  const drawer = page.locator('.el-drawer:has-text("按客户批量确认草稿订单")');
  await drawer.waitFor({ state: "visible", timeout: 8000 });
  await page.waitForTimeout(1200);

  // 1. 无「查询」按钮
  const queryBtnCount = await drawer.locator('button:has-text("查询")').count();
  if (queryBtnCount > 0) problems.push("[抽屉] 「查询」按钮应已移除");
  else console.log("抽屉内已无「查询」按钮（符合预期）");

  // 2. 选客户 → 自动查询（不点任何按钮）
  saleListReqs = 0;
  await drawer.locator(".el-select").first().click();
  await page.keyboard.type("丽宫");
  await page.waitForTimeout(400);
  const option = page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("丽宫")').first();
  await option.waitFor({ state: "visible", timeout: 5000 });
  await option.click();
  await page.waitForTimeout(1500);
  if (saleListReqs < 1) problems.push("[自动查询] 选客户后未自动发起草稿查询");
  console.log("选客户后自动查询请求数:", saleListReqs);

  // 3. 改配送日期 → 自动过滤（该日期应有 2 条草稿）
  saleListReqs = 0;
  const rows = drawer.locator(".el-table__body .el-table__row");
  const dateInput = drawer.locator('input[placeholder="不限"]');
  await dateInput.click();
  await dateInput.fill("2026-08-22");
  await page.keyboard.press("Enter");
  await page.waitForTimeout(1500);
  if (saleListReqs < 1) problems.push("[自动查询] 改配送日期后未自动发起草稿查询");
  const filteredCount = await rows.count();
  console.log("改日期后自动查询请求数:", saleListReqs, "列表条数:", filteredCount);
  if (filteredCount !== 2) problems.push(`[自动过滤] 期望 2 条，实际 ${filteredCount}`);

  // ---------- 关闭（不提交） ----------
  await drawer.locator(".el-drawer__close-btn").click().catch(() => {});
  await page.keyboard.press("Escape");

  await browser.close();
  console.log("\n==== RESULT ====");
  if (problems.length) {
    problems.forEach((p) => console.log("FAIL " + p));
    process.exit(1);
  }
  console.log("ALL PASS");
}

main().catch((e) => {
  console.error("FATAL", e);
  process.exit(1);
});
