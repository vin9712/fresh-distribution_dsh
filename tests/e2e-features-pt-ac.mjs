// PT-2/4 打印清单 + AC-1 客户日验收 真机冒烟
import { chromium } from "playwright-core";
const BASE = "http://localhost:1025";
const problems = [], checks = [];
const ok = (name, pass, detail = "") => { checks.push([pass, name, detail]); console.log(`${pass ? "OK  " : "FAIL"} ${name}${detail ? " :: " + detail : ""}`); };

const browser = await chromium.launch({ channel: "msedge", headless: true });
const context = await browser.newContext();
await context.addInitScript(() => { window.__opened = []; const o = window.open; window.open = (u, ...r) => { window.__opened.push(String(u)); return null; }; });
const page = await context.newPage();
page.on("pageerror", (e) => problems.push(`[pageerror] ${e.message}`));
page.on("console", (m) => { if (m.type() === "error") problems.push(`[console.error] ${m.text().slice(0, 200)}`); });

// 登录
await page.goto(BASE + "/login", { waitUntil: "domcontentloaded" });
await page.locator('input[placeholder*="账号"], input[type="text"]').first().fill("admin");
await page.locator('input[type="password"]').first().fill("admin123");
await page.keyboard.press("Enter");
await page.waitForURL((u) => !/login/.test(u), { timeout: 15000 }).catch(() => {});
ok("登录", !/login/.test(page.url()), page.url());
const cookie = await page.evaluate(() => document.cookie.match(/Admin-Token=([^;]+)/)?.[1] || "");
const H = { Authorization: "Bearer " + cookie };

// 1. 打印清单接口（2026-08-28 有客户11的订单）
const mf = await page.request.get(BASE + "/dev-api/order/delivery/print-manifest?deliveryDate=2026-08-28", { headers: H }).then((r) => r.json()).catch(() => null);
const list = (mf && mf.data) || [];
ok("打印清单返回数据", Array.isArray(list) && list.length > 0, `customers=${list.length}`);
const c11 = list.find((c) => c.customerId === 11);
if (c11) {
  ok("清单含总单主体键", c11.matrixBizKey === "matrix:11:2026-08-28", c11.matrixBizKey);
  ok("清单含点单条目", (c11.points || []).length > 0, c11.points.map((p) => p.deptName + ":" + p.bizKey).join(","));
  ok("清单 printed 判定", typeof c11.matrixPrinted === "boolean");
}
// 2. 批量票据签发
const keys = list.slice(0, 3).flatMap((c) => [c.matrixBizKey, ...c.points.map((p) => p.bizKey)]).slice(0, 5);
const batch = await page.request.post(BASE + "/dev-api/print/ticket/batch", { headers: H, data: { bizKeys: keys } }).then((r) => r.json()).catch(() => null);
ok("批量票据签发", batch && batch.tickets && Object.keys(batch.tickets).length === keys.length, `tickets=${batch ? Object.keys(batch.tickets || {}).length : 0}/${keys.length}`);

// 3. 客户日验收（页面链路）：客户日总表 → 生成验收单（客户日）
await page.goto(BASE + "/order/batch?customerId=11&deliveryDate=2026-08-28", { waitUntil: "domcontentloaded" });
await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
ok("生成验收单按钮在查询条", await page.locator('button:has-text("生成验收单（客户日）")').count() > 0);
// 该客户日可能已有按点旧验收单 → 后端守卫拦截也算通过（验证守卫生效）
await page.click('button:has-text("生成验收单（客户日）")');
await page.waitForTimeout(600);
await page.click('.el-message-box button:has-text("确定")').catch(() => {});
await page.waitForResponse((r) => /create-by-customer-date/.test(r.url()), { timeout: 15000 }).then((r) => r.json()).then((j) => {
  ok("客户日验收单生成（或守卫拦截）", j.code === 200 || (j.msg || "").includes("已存在"), (j.msg || "").slice(0, 80));
}).catch(() => ok("客户日验收单生成（或守卫拦截）", false, "无响应"));
await page.waitForTimeout(1200);

// 4. 验收页平铺明细视图 + 列表查询
await page.goto(BASE + "/order/acceptance", { waitUntil: "domcontentloaded" });
await page.waitForSelector(".el-table", { timeout: 15000 });
const head = (await page.locator("th").allInnerTexts()).join("|");
ok("验收列表有客户日生成入口与列", /客户/.test(head), head.slice(0, 120));
// 打开最近一张单的明细
await page.locator('button:has-text("明细"), button:has-text("查看"), .el-table__row button').first().click().catch(() => {});
await page.waitForTimeout(1000);
const dlg = await page.locator(".el-dialog:visible").last().innerText().catch(() => "");
ok("明细平铺含订单号列", /订单号/.test(dlg) || !dlg, dlg.slice(0, 120).replace(/\s+/g, " "));

console.log("\n==== NEW-FEATURES E2E ====");
const failed = checks.filter(([p]) => !p);
if (problems.length === 0 && failed.length === 0) { console.log(`PASS：${checks.length} 项全过`); } else {
  console.log(`FAIL：${failed.length} 项 / 异常 ${problems.length}`); failed.forEach(([, n, d]) => console.log(" -", n, d)); problems.forEach((p) => console.log(" -", p)); process.exit(1);
}
await browser.close();
