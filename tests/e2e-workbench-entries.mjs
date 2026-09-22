/**
 * 文员工作台入口 E2E（对齐 D-055 后流程）
 *
 * 需求：工作台入口要符合当前流程——
 *   - 去掉已退役的「待打印送货单」卡与「送货单生成异常」告警条
 *   - 日结待办链：已确认订单 → 采购录入 → 送货单据 → 独立验收（去掉「送达登记」）
 *   - 送货入口指向「送货单据」(/order/batch)，采购入口指向采购录入，验收入口指向订单页
 *
 * 步骤：登录 → /workbench → 断言区块/卡片文案 → 点节点与卡片断言跳转 URL
 *      → 接口断言 summary 已改为订单维度（pendingDelivery，无 pendingPrint/pendingMarkDelivered）
 *
 * 运行：node tests/e2e-workbench-entries.mjs
 */
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const API = BASE + "/dev-api";
const problems = [];

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

  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder*="账号"]', "admin");
  await page.fill('input[placeholder*="密码"]', "admin123");
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });
  const token = (await page.context().cookies()).find((c) => c.name === "Admin-Token")?.value || "";

  await page.goto(BASE + "/workbench", { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".chain-card", { timeout: 20000 });
  await page.waitForTimeout(1500);

  const body = await page.locator(".app-container").innerText();
  console.log("工作台文案:", body.replace(/\s+/g, " ").slice(0, 300));

  // 1. 退役项不应出现
  if (body.includes("送货单生成")) problems.push("[退役] 顶部仍出现「送货单生成异常」告警");
  if (body.includes("待打印送货单")) problems.push("[退役] 仍出现「待打印送货单」卡");
  if (body.includes("送达登记")) problems.push("[退役] 待办链仍出现「送达登记」节点");

  // 2. 链节点
  const chainTexts = await page.locator(".chain-node .chain-node-name").allInnerTexts();
  console.log("链节点:", chainTexts.join(" → "));
  for (const name of ["已确认订单", "采购录入", "送货单据", "独立验收"]) {
    if (!chainTexts.includes(name)) problems.push(`[链] 缺少节点「${name}」`);
  }

  // 3. 卡片
  const cardTitles = await page.locator(".workbench-card .card-title").allInnerTexts();
  console.log("卡片:", cardTitles.join(" / "));
  for (const name of ["待录/待确认订单", "待生成采购单", "待验收", "待处理加退换"]) {
    if (!cardTitles.includes(name)) problems.push(`[卡片] 缺少「${name}」`);
  }

  // 4. 跳转断言
  const reloadWorkbench = async () => {
    await page.goto(BASE + "/workbench", { waitUntil: "domcontentloaded" });
    await page.waitForSelector(".chain-card", { timeout: 15000 });
    await page.waitForTimeout(800);
  };
  // 链节点按「节点名精确匹配」定位（避免描述文案里含同名子串而误点）
  const clickChainNode = async (name, expectPath) => {
    await reloadWorkbench();
    await page.locator(".chain-node-name", { hasText: new RegExp(`^${name}$`) }).click();
    await page.waitForTimeout(1200);
    const url = page.url();
    console.log(`  链节点「${name}」→ ${url.replace(BASE, "")}`);
    if (!url.includes(expectPath)) problems.push(`[跳转] 链节点「${name}」期望 ${expectPath}，实际 ${url.replace(BASE, "")}`);
  };
  const clickCard = async (title, expectPath) => {
    await reloadWorkbench();
    await page.locator(".workbench-card", { hasText: title }).first().click();
    await page.waitForTimeout(1200);
    const url = page.url();
    console.log(`  卡片「${title}」→ ${url.replace(BASE, "")}`);
    if (!url.includes(expectPath)) problems.push(`[跳转] 卡片「${title}」期望 ${expectPath}，实际 ${url.replace(BASE, "")}`);
  };

  // 录入订单入口（链头主按钮）
  const addBtn = page.locator('.chain-header button:has-text("录入订单")');
  if ((await addBtn.count()) === 0) problems.push("[入口] 缺少「录入订单」按钮");
  await reloadWorkbench();
  await page.locator('.chain-header button:has-text("录入订单")').click();
  await page.waitForTimeout(1200);
  console.log(`  按钮「录入订单」→ ${page.url().replace(BASE, "")}`);
  if (!page.url().includes("/order/sale-detail/index")) {
    problems.push(`[跳转] 「录入订单」期望 /order/sale-detail/index，实际 ${page.url().replace(BASE, "")}`);
  }

  await clickChainNode("送货单据", "/order/batch");
  await clickChainNode("采购录入", "/order/purchase/day/index");
  await clickChainNode("独立验收", "/order/sale?status=1");
  await clickCard("待生成采购单", "/order/purchase/day/index");
  await clickCard("待验收", "/order/sale?status=1");
  await clickCard("待录/待确认订单", "/order/sale?status=0");

  // 5. 接口断言 summary 字段
  const summary = await page.evaluate(
    async ([api, tk]) => {
      const r = await fetch(`${api}/workbench/summary`, { headers: { Authorization: "Bearer " + tk } });
      return r.json();
    },
    [API, token]
  );
  const data = (summary && summary.data) || {};
  console.log("summary:", JSON.stringify(data));
  if (!("pendingDelivery" in data)) problems.push("[接口] summary 缺少 pendingDelivery（今日待配送订单）");
  if ("pendingPrint" in data) problems.push("[接口] summary 仍返回退役字段 pendingPrint");
  if ("pendingMarkDelivered" in data) problems.push("[接口] summary 仍返回退役字段 pendingMarkDelivered");

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
