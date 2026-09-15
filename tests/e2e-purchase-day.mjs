// 采购录入整页 E2E 验收（D-056~D-063 + 交互重构：整页 + 全行内录入，无嵌套弹窗）
// 前置：./dev.sh start（后端 8090 + 前端 1025），且 sql/s30_purchase_batch_entry.sql 已在目标库执行
// 范围：采购管理 →「采购录入」独立整页 —— 日期切换 → 行内录入（数量/进货价/供应商，回车或「加入」）
//       → 同商品多批不同价加权均价 → 展开行改批次 → 底部多行批量提交 → 确认 → 入库 → 列表回读
// 判定：关键断言逐条打印；无 pageerror / console.error / 5xx
// 依赖：仓库根 node_modules/playwright-core（系统 Chrome）
// 运行：node tests/e2e-purchase-day.mjs
// 可选环境变量：E2E_PURCHASE_DATE、E2E_CHANNEL、E2E_BASE、E2E_API
//
// ️ 会向目标库写入采购数据（草稿→确认→入库），请用 tests/run-purchase-day-e2e.sh 运行（外层负责清理）。

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

// ---------- Node 侧 API（直连后端 8090，数据断言） ----------
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
async function waitFor(fn, desc, timeout = 10000) {
  const t0 = Date.now();
  let last;
  while (Date.now() - t0 < timeout) {
    last = await fn();
    if (last) return last;
    await new Promise((r) => setTimeout(r, 250));
  }
  throw new Error(`等待超时：${desc}（最后结果：${JSON.stringify(last)}）`);
}
const rowOf = (s, key) => (s.rows || []).find((x) => x.key === key);

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
  console.log(`\n== 采购录入整页 E2E（采购日期 ${DATE}）==\n`);

  // ---------- UI 登录 ----------
  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder="账号"]', USER);
  await page.fill('input[placeholder="密码"]', PASS);
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 25000 });
  ok("UI 登录", true, page.url());

  // ---------- 从列表进入采购录入整页 ----------
  await page.goto(BASE + "/purchase", { waitUntil: "networkidle" });
  await page.getByRole("button", { name: "采购录入" }).first().click();
  await page.waitForURL((u) => String(u).includes("/purchase/day"), { timeout: 15000 });
  await page.waitForSelector(".pd-stats", { state: "visible", timeout: 15000 });
  ok("从列表跳转采购录入整页", true, page.url());
  ok("整页无嵌套弹窗（.el-dialog 数量 0）", (await page.locator(".el-dialog").count()) === 0);

  // ---------- 切到有订单的日期 ----------
  const dateInput = page.locator(".pd-toolbar .el-date-editor input").first();
  await dateInput.click();
  await dateInput.fill(DATE);
  await dateInput.press("Enter");
  await page.keyboard.press("Escape"); // 页面上 Esc 只关闭日期面板，不会关页面
  await page.waitForTimeout(600);

  const s0 = await waitFor(async () => {
    const s = await summary();
    return s && s.rows && s.rows.length ? s : null;
  }, "当日应采清单加载");
  ok("应采清单加载（订单视图）", s0.requiredItemCount > 0,
    `品种 ${s0.requiredItemCount} / 应采总量 ${s0.requiredQty} / 未建单 purchaseId=${s0.purchaseId}`);

  const mainRows = page.locator("tr", { has: page.locator(".pd-inline-entry") });
  await mainRows.first().waitFor({ state: "visible", timeout: 8000 });
  ok("页面汇总行数 = 接口应采行数", (await mainRows.count()) === s0.rows.length,
    `UI ${await mainRows.count()} vs API ${s0.rows.length}`);

  // ---------- 选目标商品（行内录入） ----------
  const selectable = s0.rows.filter((r) => !r.orphan && Number(r.requiredQty) > 0);
  const target = selectable.find((r) => r.skuId != null) || selectable[0];
  let targetRow = mainRows.filter({ hasText: target.productName }).first();
  ok("定位目标商品行", (await targetRow.count()) > 0, `${target.productName} 应采 ${target.requiredQty}`);

  // ---------- 默认值 + 纯键盘起始焦点 ----------
  const qtyDefault = await targetRow.locator(".pd-entry-qty input").inputValue();
  ok("数量默认=待采数量（未录时即订单总数）", Math.abs(Number(qtyDefault) - Number(target.pendingQty)) < 1e-6,
    `默认 ${qtyDefault} vs 待采 ${target.pendingQty}`);
  ok("进货价默认留空（必须手填）", (await targetRow.locator(".pd-entry-price input").inputValue()) === "");
  const autoFocusPrice = await page.evaluate(
    () => !!document.activeElement && !!document.activeElement.closest(".pd-entry-price")
  );
  ok("打开即聚焦进货价（纯键盘）", autoFocusPrice);

  // ---------- 未保存离开守卫 ----------
  await targetRow.locator(".pd-entry-price input").fill("9.99");
  await page.waitForTimeout(200);
  const unloadPrevented = await page.evaluate(() => {
    const e = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(e);
    return e.defaultPrevented;
  });
  ok("有未提交内容时拦截浏览器关闭/刷新（beforeunload）", unloadPrevented);
  await page.getByRole("button", { name: "返回列表" }).click();
  const leaveBox = page.locator(".el-message-box");
  await leaveBox.waitFor({ state: "visible", timeout: 8000 });
  ok("未提交时返回列表弹出未保存确认", (await leaveBox.innerText()).includes("未提交"),
    (await leaveBox.innerText()).replace(/\n/g, " ").slice(0, 50));
  await leaveBox.getByRole("button", { name: "继续编辑" }).click();
  await leaveBox.waitFor({ state: "hidden", timeout: 8000 });
  ok("取消后仍留在采购录入页", page.url().includes("/purchase/day"));
  // 清掉测试值，避免影响后续录入流程
  await targetRow.locator(".pd-entry-price input").fill("");
  await page.waitForTimeout(200);

  const fillEntry = async (row, qty, price, supplier) => {
    const inputs = row.locator(".pd-inline-entry input");
    await inputs.nth(0).fill(String(qty));
    await inputs.nth(0).press("Tab");
    await inputs.nth(1).fill(String(price));
    await inputs.nth(1).press("Tab");
    await inputs.nth(2).fill(supplier);
  };

  // 交互后组件会重新 load()：等 day-summary 响应回来再继续，避免在旧 DOM 上操作（竞态）
  const actionWithReload = async (fn) => {
    const wait = page
      .waitForResponse((r) => r.url().includes("/purchase/day-summary") && r.status() === 200, { timeout: 12000 })
      .catch(() => null);
    await fn();
    await wait;
    await page.waitForTimeout(200);
  };

  // ---------- 行内录入：批次1 ----------
  await fillEntry(targetRow, 1, "2.00", "E2E甲商");
  await actionWithReload(() => targetRow.getByRole("button", { name: "加入" }).click());
  const s1 = await waitFor(async () => {
    const s = await summary();
    const r = rowOf(s, target.key);
    return r && r.batchCount === 1 ? s : null;
  }, "批次1 落库");
  const r1 = rowOf(s1, target.key);
  ok("行内录入批次1（1 × 2.00，无弹窗）", Number(r1.purchasedQty) === 1 && Number(r1.amount) === 2,
    `已采 ${r1.purchasedQty} 金额 ${r1.amount} 单号 ${s1.code}`);

  // ---------- 行内录入：批次2（不同价，回车提交） ----------
  targetRow = mainRows.filter({ hasText: target.productName }).first();
  await fillEntry(targetRow, 1, "4.00", "E2E乙商");
  await actionWithReload(() => targetRow.locator(".pd-entry-supplier input").press("Enter")); // 供应商回车即提交
  const s2 = await waitFor(async () => {
    const s = await summary();
    const r = rowOf(s, target.key);
    return r && r.batchCount === 2 ? s : null;
  }, "批次2 落库");
  const r2 = rowOf(s2, target.key);
  ok("同商品分批不同价 + 加权均价", Number(r2.purchasedQty) === 2 && Math.abs(Number(r2.avgPrice) - 3) < 1e-6,
    `已采 ${r2.purchasedQty} 批次数 ${r2.batchCount} 均价 ${r2.avgPrice}（期望 3）金额 ${r2.amount}`);
  ok("按批次保留各自供应商",
    r2.batches.map((b) => b.supplierName).join(",") === "E2E甲商,E2E乙商",
    r2.batches.map((b) => `${b.batchNo}:${b.supplierName}`).join(" "));
  // 回车提交后焦点自动落到下一行进货价（连打）
  const focusMoved = await page.evaluate(
    () => !!document.activeElement && !!document.activeElement.closest(".pd-entry-price")
  );
  ok("回车保存后焦点落到下一行进货价（纯键盘连打）", focusMoved);

  // ---------- 展开行内改批次 ----------
  targetRow = mainRows.filter({ hasText: target.productName }).first();
  await targetRow.locator(".el-table__expand-icon").click();
  await page.waitForSelector(".pd-batches tbody tr", { state: "visible", timeout: 8000 });
  ok("展开行显示批次明细", (await page.locator(".pd-batches tbody tr").count()) === 2,
    `批次行 ${await page.locator(".pd-batches tbody tr").count()}`);
  const firstBatchRow = page.locator(".pd-batches tbody tr").first();
  const bQty = firstBatchRow.locator(".el-input-number input").first();
  await bQty.fill("3");
  await bQty.press("Tab");
  await actionWithReload(() => firstBatchRow.getByRole("button", { name: "保存" }).click());
  const s3 = await waitFor(async () => {
    const s = await summary();
    const r = rowOf(s, target.key);
    return r && Number(r.purchasedQty) === 4 ? s : null;
  }, "批次行内改数量生效");
  ok("展开行内改批次数量（1→3，已采 4）", Number(rowOf(s3, target.key).purchasedQty) === 4,
    `已采 ${rowOf(s3, target.key).purchasedQty} 金额 ${rowOf(s3, target.key).amount}`);

  // ---------- 底部多行批量提交 ----------
  const second = selectable.find((r) => r.key !== target.key);
  let secondRow = mainRows.filter({ hasText: second.productName }).first();
  await fillEntry(secondRow, 2, "1.50", "E2E丙商");
  const submitBtn = page.getByRole("button", { name: /提交录入/ });
  ok("底部「提交录入」汇总已填进货价行数", (await submitBtn.innerText()).includes("1 行"), await submitBtn.innerText());
  // 纯键盘路径：Ctrl+Enter 一次提交所有已填进货价的行
  await actionWithReload(() => page.keyboard.press("Control+Enter"));
  const s4 = await waitFor(async () => {
    const s = await summary();
    const r = rowOf(s, second.key);
    return r && r.batchCount === 1 ? s : null;
  }, "批量提交落库");
  ok("Ctrl+Enter 多行批量提交（第二商品 2 × 1.50）",
    Number(rowOf(s4, second.key).purchasedQty) === 2 && Number(rowOf(s4, second.key).amount) === 3,
    `${second.productName} 已采 ${rowOf(s4, second.key).purchasedQty} 金额 ${rowOf(s4, second.key).amount}`);

  // ---------- 未录齐不拦截，可确认 ----------
  const sPre = await summary();
  ok("未录齐仅提示不拦截（确认按钮可用）", await page.getByRole("button", { name: "确认采购单" }).isEnabled(),
    `已录 ${sPre.purchasedItemCount}/${sPre.requiredItemCount} 品种，待采 ${sPre.pendingQty}，超采 ${sPre.overCount} 行`);

  await actionWithReload(async () => {
    await page.getByRole("button", { name: "确认采购单" }).click();
    await page.locator(".el-message-box__btns .el-button--primary").click();
  });
  const s5 = await waitFor(async () => {
    const s = await summary();
    return s && s.status === 1 ? s : null;
  }, "确认后状态=1");
  ok("确认采购单（草稿→已确认）", s5.status === 1);
  await page.waitForTimeout(500);
  ok("确认后行内录入消失（批次锁定）", (await page.locator(".pd-inline-entry").count()) === 0);

  // ---------- 入库（确认成本） ----------
  await actionWithReload(async () => {
    await page.getByRole("button", { name: "入库（确认成本）" }).click();
    await page.locator(".el-message-box__btns .el-button--primary").click();
  });
  const s6 = await waitFor(async () => {
    const s = await summary();
    return s && s.status === 2 ? s : null;
  }, "入库后状态=2");
  ok("入库（已确认成本）", s6.status === 2, `总额 ${s6.totalAmount}`);

  // ---------- 返回列表回读 ----------
  const listRespInfo = [];
  const onListResp = async (r) => {
    if (r.url().includes("/purchase/list")) {
      listRespInfo.push("status=" + r.status());
    }
  };
  page.on("response", onListResp);
  await Promise.all([
    page.waitForResponse((r) => r.url().includes("/purchase/list"), { timeout: 12000 }).catch(() => null),
    page.getByRole("button", { name: "返回列表" }).click(),
  ]);
  await page.waitForURL((u) => !String(u).includes("/purchase/day"), { timeout: 10000 });
  let appeared = false;
  let lastCount = 0;
  for (let i = 0; i < 16 && !appeared; i++) {
    await page.waitForTimeout(500);
    lastCount = await page.locator("tbody tr").count();
    appeared = (await page.locator("tbody tr", { hasText: s6.code }).count()) > 0;
  }
  page.off("response", onListResp);
  ok("返回列表并出现该单", appeared, `${s6.code} 总行数=${lastCount} listResp=${listRespInfo.join(",")}`);

  console.log("\n采购单ID（供清理）：" + s6.purchaseId + " 单号：" + s6.code);
} catch (e) {
  ok("脚本执行", false, e.message);
} finally {
  ok("无 pageerror / console.error / 5xx", problems.length === 0, problems.slice(0, 5).join(" | "));
  const failed = checks.filter(([p]) => !p).length;
  console.log(`\n===== 结果：${checks.length - failed}/${checks.length} 通过 =====`);
  await browser.close();
  process.exit(failed ? 1 : 0);
}