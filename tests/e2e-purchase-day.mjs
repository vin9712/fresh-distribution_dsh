// 采购单视图化 E2E 验收（D-056~D-063）
// 前置：./dev.sh start（后端 8090 + 前端 1025），且 sql/s30_purchase_batch_entry.sql 已在目标库执行
// 范围：/purchase「采购录入」日应采汇总 —— 应采清单 → 分批录入（不同价）→ 加权均价/待采/超采
//       → 批量录入限订单商品 → 空单不可确认 → 确认 → 调整成本 → 入库
// 判定：关键断言逐条打印；无 pageerror / console.error / 5xx
// 依赖：仓库根 node_modules/playwright-core（系统 Chrome）
// 运行：node tests/e2e-purchase-day.mjs
//       可选环境变量：E2E_PURCHASE_DATE（默认 2026-09-15）、E2E_CHANNEL（默认 chrome）
//
// ⚠️ 本脚本会在目标库写入采购数据（草稿→确认→入库），请配合 tests/run-purchase-day-e2e.sh 运行，
//    由外层负责跑前跑后清理（入库单不可通过接口删除，必须走 SQL）。

import { chromium } from "playwright-core";

const BASE = process.env.E2E_BASE || "http://localhost:1025";
const API = process.env.E2E_API || "http://localhost:8090";
const DATE = process.env.E2E_PURCHASE_DATE || "2026-09-15";
const CHANNEL = process.env.E2E_CHANNEL || "chrome";
const USER = process.env.E2E_USER || "admin";
const PASS = process.env.E2E_PASS || "admin123";

const problems = [];
const checks = [];
const ok = (name, pass, detail = "") => {
  checks.push([pass, name, detail]);
  console.log(`${pass ? "OK  " : "FAIL"} ${name}${detail ? " :: " + detail : ""}`);
};

// ---------- Node 侧 API（直连后端 8090，用于数据断言 / 清理定位） ----------
let token = "";
async function login() {
  const r = await fetch(`${API}/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: USER, password: PASS }),
  });
  const d = await r.json();
  if (!d.token) throw new Error("登录失败：" + JSON.stringify(d));
  token = d.token;
}
async function api(path, opts = {}) {
  const r = await fetch(`${API}${path}`, {
    ...opts,
    headers: { Authorization: `Bearer ${token}`, ...(opts.headers || {}) },
  });
  let body = null;
  try {
    body = await r.json();
  } catch {
    /* ignore */
  }
  return { http: r.status, ...(body || {}) };
}
const summary = async () => (await api(`/purchase/day-summary?orderDate=${DATE}`)).data;
async function waitFor(fn, desc, timeout = 8000) {
  const t0 = Date.now();
  let last;
  while (Date.now() - t0 < timeout) {
    last = await fn();
    if (last) return last;
    await new Promise((r) => setTimeout(r, 250));
  }
  throw new Error(`等待超时：${desc}（最后结果：${JSON.stringify(last)}）`);
}

// ---------- 启动 ----------
const browser = await chromium.launch({ channel: CHANNEL, headless: true });
const context = await browser.newContext();
const page = await context.newPage();
page.on("pageerror", (e) => problems.push(`[pageerror] ${page.url()} :: ${e.message}`));
page.on("console", (m) => {
  if (m.type() === "error") problems.push(`[console.error] ${page.url()} :: ${m.text().slice(0, 300)}`);
});
page.on("response", (r) => {
  if (r.status() >= 500) problems.push(`[http${r.status()}] ${r.url()}`);
});

try {
  await login();
  console.log(`\n== 采购录入 E2E（采购日期 ${DATE}）==\n`);

  // ---------- 登录（UI） ----------
  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder="账号"]', USER);
  await page.fill('input[placeholder="密码"]', PASS);
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 25000 });
  ok("UI 登录", true, page.url());

  // ---------- 打开采购录入 ----------
  await page.goto(BASE + "/purchase", { waitUntil: "networkidle" });
  await page.getByRole("button", { name: "采购录入" }).first().click();
  await page.waitForSelector(".pd-stats", { state: "visible", timeout: 15000 });
  const dlg = page.locator(".el-dialog").filter({ has: page.locator(".pd-stats") }).first();
  ok("打开「采购录入」弹窗", true);

  // ---------- 切到有订单的日期 ----------
  const dateInput = dlg.locator(".el-date-editor input").first();
  await dateInput.click();
  await dateInput.fill(DATE);
  await dateInput.press("Enter");
  // 点中性区域收起日期面板（⚠️ 不能按 Esc：会连带关闭整个录入弹窗）
  await dlg.locator(".pd-stats").click();
  await page.waitForTimeout(600);

  const s0 = await waitFor(async () => {
    const s = await summary();
    return s && s.rows && s.rows.length ? s : null;
  }, "当日应采清单加载");
  ok("应采清单加载（订单视图）", s0.requiredItemCount > 0,
    `品种 ${s0.requiredItemCount} / 应采总量 ${s0.requiredQty} / 未建单 purchaseId=${s0.purchaseId}`);

  const summaryRows = dlg.locator(".el-table").first().locator("tbody tr");
  await summaryRows.first().waitFor({ state: "visible", timeout: 8000 });
  const uiRowCount = await summaryRows.count();
  ok("页面汇总行数 = 接口应采行数", uiRowCount === s0.rows.length, `UI ${uiRowCount} vs API ${s0.rows.length}`);

  // ---------- 选一个可录入的目标商品 ----------
  const target = s0.rows.find((r) => r.skuId != null && Number(r.requiredQty) > 0) || s0.rows[0];
  const targetRow = summaryRows.filter({ hasText: target.productName }).first();
  ok("定位目标商品行", (await targetRow.count()) > 0, `${target.productName} 应采 ${target.requiredQty}`);

  // ---------- 分批录入：批次1 ----------
  const recordBatch = async (qty, price, supplier) => {
    await targetRow.getByRole("button", { name: "录入" }).click();
    const bdlg = page.locator(".el-dialog").filter({ hasText: "录入进货批次" }).last();
    await bdlg.waitFor({ state: "visible", timeout: 8000 });
    const inputs = bdlg.locator(".el-form input");
    await inputs.nth(0).fill(String(qty));
    await inputs.nth(0).press("Tab");
    await inputs.nth(1).fill(String(price));
    await inputs.nth(1).press("Tab");
    await inputs.nth(2).fill(supplier);
    await bdlg.getByRole("button", { name: "保存" }).click();
    await bdlg.waitFor({ state: "hidden", timeout: 8000 });
  };

  await recordBatch(1, "2.00", "E2E甲商");
  const s1 = await waitFor(async () => {
    const s = await summary();
    const r = s.rows.find((x) => x.key === target.key);
    return r && r.batchCount === 1 ? s : null;
  }, "批次1 落库");
  const r1 = s1.rows.find((x) => x.key === target.key);
  ok("批次1 录入（1 × 2.00）", Number(r1.purchasedQty) === 1 && Number(r1.amount) === 2,
    `已采 ${r1.purchasedQty} 金额 ${r1.amount} 均价 ${r1.avgPrice} 单号 ${s1.code}`);

  // ---------- 分批录入：批次2（不同价） ----------
  await recordBatch(1, "4.00", "E2E乙商");
  const s2 = await waitFor(async () => {
    const s = await summary();
    const r = s.rows.find((x) => x.key === target.key);
    return r && r.batchCount === 2 ? s : null;
  }, "批次2 落库");
  const r2 = s2.rows.find((x) => x.key === target.key);
  const expectAvg = (2 + 4) / 2;
  ok("同商品分批不同价 + 加权均价", Number(r2.purchasedQty) === 2 && Math.abs(Number(r2.avgPrice) - expectAvg) < 1e-6,
    `已采 ${r2.purchasedQty} 批次数 ${r2.batchCount} 均价 ${r2.avgPrice}（期望 ${expectAvg}）金额 ${r2.amount}`);
  ok("按批次保留各自供应商",
    r2.batches.map((b) => b.supplierName).join(",") === "E2E甲商,E2E乙商",
    r2.batches.map((b) => `${b.batchNo}:${b.supplierName}`).join(" "));

  // ---------- 批量录入：下拉只含当日订单商品 ----------
  await dlg.getByRole("button", { name: "批量录入" }).click();
  const drawer = page.locator(".el-drawer").filter({ hasText: "批量录入采购批次" }).last();
  await drawer.waitFor({ state: "visible", timeout: 8000 });
  await drawer.locator(".el-select").first().click();
  const dropdownItems = page.locator(".el-select-dropdown:visible .el-select-dropdown__item");
  await dropdownItems.first().waitFor({ state: "visible", timeout: 5000 });
  const optCount = await dropdownItems.count();
  const selectable = s2.rows.filter((r) => !r.orphan).length;
  ok("批量录入选品 = 当日订单商品（不含已撤回遗留行）", optCount === selectable,
    `下拉 ${optCount} 项 vs 应采 ${selectable} 项`);
  await page.keyboard.press("Escape");
  await drawer.locator(".el-drawer__close-btn").click().catch(() => {});
  await drawer.waitFor({ state: "hidden", timeout: 8000 }).catch(() => {});

  // ---------- 未录齐不拦截，可确认 ----------
  const s2b = await summary();
  ok("未录齐/超采仅提示不拦截（确认按钮可用）",
    await dlg.getByRole("button", { name: "确认采购单" }).isEnabled(),
    `已录 ${s2b.purchasedItemCount}/${s2b.requiredItemCount} 品种，待采 ${s2b.pendingQty}，超采 ${s2b.overCount} 行`);

  await dlg.getByRole("button", { name: "确认采购单" }).click();
  await page.locator(".el-message-box__btns .el-button--primary").click();
  const s3 = await waitFor(async () => {
    const s = await summary();
    return s && s.status === 1 ? s : null;
  }, "确认后状态=1");
  ok("确认采购单（草稿→已确认）", s3.status === 1);
  await page.waitForTimeout(500);
  ok("确认后隐藏录入/删除入口（批次锁定）",
    (await targetRow.getByRole("button", { name: "录入" }).count()) === 0);

  // ---------- 入库（确认成本） ----------
  await dlg.getByRole("button", { name: "入库（确认成本）" }).click();
  await page.locator(".el-message-box__btns .el-button--primary").click();
  const s4 = await waitFor(async () => {
    const s = await summary();
    return s && s.status === 2 ? s : null;
  }, "入库后状态=2");
  ok("入库（已确认成本）", s4.status === 2, `总额 ${s4.totalAmount}`);

  // ---------- 列表页回读 ----------
  await page.keyboard.press("Escape");
  await dlg.waitFor({ state: "hidden", timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(500);
  const listHas = await page.locator("tbody tr", { hasText: s4.code }).count();
  ok("采购单列表出现该单", listHas > 0, s4.code);

  console.log("\n采购单ID（供清理）：" + s4.purchaseId + " 单号：" + s4.code);
} catch (e) {
  ok("脚本执行", false, e.message);
} finally {
  ok("无 pageerror / console.error / 5xx", problems.length === 0, problems.slice(0, 5).join(" | "));
  const failed = checks.filter(([p]) => !p).length;
  console.log(`\n===== 结果：${checks.length - failed}/${checks.length} 通过 =====`);
  await browser.close();
  process.exit(failed ? 1 : 0);
}
