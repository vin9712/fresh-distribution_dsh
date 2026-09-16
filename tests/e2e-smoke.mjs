// 页面级 E2E 冒烟（T2/T7/T8 遗留补验，2026-08-28）
// 前置：本地环境已启动（./dev.sh start，前端 1025 → 后端 8090 → 远程库）
// 范围：登录 + 单据管理五个关键页面加载（销售订单/送货单据/采购管理/验收单/送货单据(历史)）
// 判定：无 pageerror、无 5xx 接口响应；每次运行只读不写，不产生业务数据
// 运行：node tests/e2e-smoke.mjs
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const PAGES = [
  ["/order/sale", "销售订单"],
  ["/order/batch", "送货单据"],
  ["/order/purchase", "采购管理"],
  ["/order/acceptance", "验收单"],
  ["/order/delivery", "送货单据(历史)"],
];

const problems = [];
const browser = await chromium.launch({ channel: "chrome", headless: true });
try {
  const page = await browser.newPage();
  page.on("pageerror", (e) => problems.push(`[pageerror] ${page.url()} :: ${e.message}`));
  page.on("console", (m) => {
    if (m.type() === "error") problems.push(`[console.error] ${page.url()} :: ${m.text().slice(0, 300)}`);
  });
  page.on("response", (r) => {
    if (r.status() >= 500) problems.push(`[http${r.status()}] ${r.url()}`);
  });

  // ---- 登录 ----
  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder="账号"]', "admin");
  await page.fill('input[placeholder="密码"]', "admin123");
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });
  console.log("LOGIN OK ->", page.url());

  // ---- 逐页冒烟 ----
  for (const [route, name] of PAGES) {
    await page.goto(BASE + route, { waitUntil: "domcontentloaded" });
    await page.waitForSelector(".app-container", { timeout: 15000 });
    await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
    // 页面应有可见内容（表格/表单容器），且未被路由 404 兜底页接管
    const notFound = await page
      .locator("text=页面不存在")
      .or(page.locator("text=404"))
      .count();
    console.log(`${notFound ? "WARN 404?" : "OK   "} ${route} (${name}) url=${page.url()}`);
    if (notFound) problems.push(`[route-404] ${route} (${name})`);
  }

  // ---- 关键元素抽查 ----
  await page.goto(BASE + "/order/sale", { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".app-container table", { timeout: 15000 });
  const saleCols = await page.locator("th").allInnerTexts();
  console.log("sale table cols:", saleCols.filter(Boolean).join(" | ").slice(0, 200));
  await page.goto(BASE + "/order/delivery", { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".app-container table", { timeout: 15000 });
  const delivCols = await page.locator("th").allInnerTexts();
  console.log("delivery table cols:", delivCols.filter(Boolean).join(" | ").slice(0, 200));
} finally {
  await browser.close();
}

console.log("\n==== SMOKE RESULT ====");
if (problems.length === 0) {
  console.log("PASS：无 pageerror / console.error / 5xx");
} else {
  console.log(`FAIL：${problems.length} 个问题`);
  for (const p of [...new Set(problems)]) console.log("  -", p);
  process.exit(1);
}
