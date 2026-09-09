// D-055 收尾 页面级 E2E 冒烟（2026-09-04）
// 前置：后端 8090 + 前端 1025 已启动（远端库）
// 范围：/order/batch 三口径同源、总单/点单打印开窗参数、打印分界标识、历史日期回退
// 判定：无 pageerror / console.error / 5xx；关键断言逐条打印
// 依赖：playwright-core（仓库不内置，任选一种：npm i -D playwright-core 于仓库根，或 npm i playwright-core 于 tests/）
// 浏览器：使用系统 Edge（channel: msedge；无 Edge 时改 channel: chrome）
// 运行：node tests/e2e-d055-closeout.mjs
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const problems = [];
const checks = [];
const ok = (name, pass, detail = "") => {
  checks.push([pass, name, detail]);
  console.log(`${pass ? "OK  " : "FAIL"} ${name}${detail ? " :: " + detail : ""}`);
};

const browser = await chromium.launch({ channel: "msedge", headless: true });
const context = await browser.newContext();
// 拦截打印开窗（避免真开报表页），记录 URL
await context.addInitScript(() => {
  window.__opened = [];
  const orig = window.open;
  window.open = (u, ...rest) => {
    window.__opened.push(String(u));
    return null;
  };
});
const page = await context.newPage();
page.on("pageerror", (e) => problems.push(`[pageerror] ${page.url()} :: ${e.message}`));
page.on("console", (m) => {
  if (m.type() === "error") problems.push(`[console.error] ${page.url()} :: ${m.text().slice(0, 300)}`);
});
const apiCalls = [];
page.on("response", async (r) => {
  const u = r.url();
  if (r.status() >= 500) problems.push(`[http${r.status()}] ${u}`);
  if (/\/print\/ticket|\/order\/delivery\/print-log|\/order\/delivery\/batch|\/print-state/.test(u)) {
    apiCalls.push({ url: u.replace(BASE, ""), status: r.status() });
  }
});

// ---------- 登录 ----------
await page.goto(BASE + "/login", { waitUntil: "networkidle" });
await page.fill('input[placeholder="账号"]', "admin");
await page.fill('input[placeholder="密码"]', "admin123");
await page.click(".login-button, .el-button--primary");
await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 25000 });
ok("登录", true, page.url());

// ---------- 1. 矩阵口径（客户11 / 2026-08-28：该日无历史送货单，旧口径必空） ----------
await page.goto(BASE + "/order/batch?customerId=11&deliveryDate=2026-08-28", { waitUntil: "domcontentloaded" });
await page.waitForSelector(".app-container", { timeout: 15000 });
await page.waitForResponse((r) => /\/matrix/.test(r.url()), { timeout: 15000 }).catch(() => {});
await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
let matrixRows = await page.locator(".el-table__body-wrapper tbody tr").count();
let headText = await page.locator("th").allInnerTexts();
ok("矩阵口径有数据行（订单明细同源）", matrixRows >= 3, `rows=${matrixRows}`);
ok("矩阵列头含配送点", /棠下/.test(headText.join(" ")) && /华铃/.test(headText.join(" ")), headText.filter(Boolean).join("|").slice(0, 120));
ok("矩阵无「没有已确认订单」告警", !(headText.join(" ").includes("没有有效送货单")));

// ---------- 2. 配货口径 ----------
await page.click('.el-radio-button:has-text("配货总表")');
await page.waitForResponse((r) => /batch\/view/.test(r.url()), { timeout: 15000 }).catch(() => {});
await page.waitForTimeout(800);
const pickRows = await page.locator(".el-table__body-wrapper tbody tr").count();
ok("配货口径有数据行", pickRows >= 3, `rows=${pickRows}`);

// ---------- 3. 点单口径（分 tab，无配送点选择器） + 打印分界标识 ----------
await page.click('.el-radio-button:has-text("点单")');
await page.waitForResponse((r) => /point-view/.test(r.url()), { timeout: 15000 }).catch(() => {});
await page.waitForTimeout(1500);
// 默认自动选中首个有单的配送点 tab
const pointText = await page.locator(".batch-footer").last().innerText().catch(() => "");
ok("点单口径明细渲染", /共\s*\d+\s*行/.test(pointText), pointText.replace(/\s+/g, " ").slice(0, 160));
ok("点单显示打印分界标识", /已打印|未打印/.test(pointText), pointText.match(/(已打印|未打印)[^·]*/)?.[0]?.slice(0, 60) || "");

// ---------- 4. 点单打印：后端解析模板（PT-1）+ 开窗不登记（PT-3） ----------
await page.evaluate(() => (window.__opened = []));
await page.click('button:has-text("打印点单")');
const pointTicketResp = await page
  .waitForResponse((r) => /\/print\/ticket/.test(r.url()) && r.request().method() === "POST", { timeout: 15000 })
  .then((r) => r.json())
  .catch(() => null);
await page.waitForTimeout(1200);
let opened = await page.evaluate(() => window.__opened);
const pointUrl = (opened || []).find((u) => u.includes("/jmreport/view/")) || "";
ok("点单打印开窗", !!pointUrl, pointUrl.slice(0, 150));
ok("点单打印 URL 带 ticket 与新主体参数", /ticket=ptk_/.test(pointUrl) && /customerId=/.test(pointUrl) && /customerDeptId=/.test(pointUrl) && /deliveryDate=/.test(pointUrl));
ok("开窗即登记已废除（PT-3）", !apiCalls.some((c) => c.url.includes("print-log")),
  apiCalls.filter((c) => c.url.includes("print-log")).map((c) => c.url).join(",") || "无 print-log 调用");
// 模拟报表页回执（真实链路由 print-annotation.js v=11 在打印动作后 POST /print/receipt）
const pointTicket = pointTicketResp && pointTicketResp.ticket;
ok("票据已签发", !!pointTicket);
if (pointTicket) {
  const receipt = await page.request
    .post(BASE + "/dev-api/print/receipt", { data: { ticket: pointTicket } })
    .then((r) => r.json())
    .catch(() => null);
  ok("打印回执登记成功（bizKey 主体）", receipt && receipt.registered === true, JSON.stringify(receipt || {}));
  // 回执后翻标识（storage 事件仅跨页触发；此处直接重查分界接口验证登记生效，带页面 Cookie 里的 JWT）
  const token = await page.evaluate(() => document.cookie.match(/Admin-Token=([^;]+)/)?.[1] || "");
  const usp = new URL(pointUrl, BASE).searchParams;
  const state = await page.request
    .get(BASE + "/dev-api/order/delivery/print-state?customerId=" + usp.get("customerId")
      + "&deliveryDate=" + usp.get("deliveryDate")
      + "&customerDeptId=" + usp.get("customerDeptId"),
      { headers: { Authorization: "Bearer " + token } })
    .then((r) => r.json())
    .catch(() => null);
  ok("回执后打印分界翻转「已打印」", state && state.printed === true, JSON.stringify(state || {}));
}

// ---------- 5. 总单（矩阵）打印 ----------
await page.click('.el-radio-button:has-text("矩阵总表")');
await page.waitForResponse((r) => /\/matrix/.test(r.url()), { timeout: 15000 }).catch(() => {});
await page.waitForTimeout(800);
await page.evaluate(() => (window.__opened = []));
await page.click('button:has-text("打印总单")');
await page.waitForResponse((r) => /\/print\/ticket/.test(r.url()) && r.request().method() === "POST", { timeout: 15000 }).catch(() => {});
await page.waitForTimeout(1500);
opened = await page.evaluate(() => window.__opened);
const matrixUrl = (opened || []).find((u) => u.includes("/jmreport/view/")) || "";
ok("总单打印开窗", !!matrixUrl, matrixUrl.slice(0, 150));
ok("总单打印按后端解析模板且带 customerId/deliveryDate（PT-1：不再硬编码宽表模板ID，长表/宽表随绑定路由）",
  /\/jmreport\/view\/\d+/.test(matrixUrl) && /customerId=11/.test(matrixUrl) && /deliveryDate=2026-08-28/.test(matrixUrl), matrixUrl.slice(0, 160));

// ---------- 6. 历史日期回退（客户11 / 2026-09-01 有 D-055 前送货单） ----------
await page.goto(BASE + "/order/batch?customerId=11&deliveryDate=2026-09-01", { waitUntil: "domcontentloaded" });
const histResp = await page
  .waitForResponse((r) => /\/matrix/.test(r.url()), { timeout: 15000 })
  .then((r) => r.json())
  .catch(() => null);
const hd = histResp && histResp.data ? histResp.data : {};
ok("历史日期仍有数据（订单明细口径覆盖）", (hd.rows || []).length > 0, `rows=${(hd.rows || []).length}`);
ok("历史日期行仍按订单明细取数（deliveryId 为空）", (hd.rows || []).every((r) => r.deliveryId == null));

// ---------- 7. 客户页：组单策略字段已下线（D-055） ----------
await page.goto(BASE + "/basicInfo/customer", { waitUntil: "domcontentloaded" });
await page.waitForSelector(".app-container table", { timeout: 15000 });
await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
const custHead = (await page.locator("th").allInnerTexts()).join("|");
ok("客户列表不再显示组单策略列", !/组单策略|相同商品合并/.test(custHead), custHead.slice(0, 140));
await page.locator(".el-table__body-wrapper tbody tr").first().click().catch(() => {});
await page.click('button:has-text("新增")');
await page.waitForTimeout(900);
const custFormText = await page.locator(".el-dialog:visible").first().innerText().catch(() => "");
ok("客户表单不再要求填组单策略", !/组单策略|相同商品合并/.test(custFormText));
await page.click('.el-dialog:visible button:has-text("取 消"), .el-dialog:visible button:has-text("取消")').catch(() => {});

console.log("\n==== E2E RESULT ====");
console.log("API 调用:", apiCalls.map((c) => `${c.status} ${c.url}`).join("\n           "));
const failed = checks.filter(([p]) => !p);
if (problems.length === 0 && failed.length === 0) {
  console.log(`PASS：${checks.length} 项断言全过，无 pageerror / console.error / 5xx`);
} else {
  console.log(`FAIL：断言未过 ${failed.length} 项 / 异常 ${problems.length} 项`);
  failed.forEach(([, n, d]) => console.log("  - 断言:", n, d));
  [...new Set(problems)].forEach((p) => console.log("  -", p));
  process.exit(1);
}
await browser.close();
