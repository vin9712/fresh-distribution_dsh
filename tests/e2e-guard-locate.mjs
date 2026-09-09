// 守卫 + 订单视角定位 真机冒烟（修复 delivery_date XML 漏列后）
import { chromium } from "playwright-core";
const BASE = "http://localhost:1025";
const checks = [], problems = [];
const ok = (n, p, d = "") => { checks.push([p, n, d]); console.log(`${p ? "OK  " : "FAIL"} ${n}${d ? " :: " + d : ""}`); };
const browser = await chromium.launch({ channel: "msedge", headless: true });
const page = await (await browser.newContext()).newPage();
page.on("pageerror", (e) => problems.push(e.message));
page.on("console", (m) => { if (m.type() === "error") problems.push(m.text().slice(0, 150)); });

await page.goto(BASE + "/login", { waitUntil: "domcontentloaded" });
let cookie = "";
for (let i = 0; i < 3 && !cookie; i++) {
  await page.goto(BASE + "/login", { waitUntil: "domcontentloaded" }).catch(() => {});
  await page.waitForTimeout(1200);
  await page.locator('input[type="text"]').first().fill("admin");
  await page.locator('input[type="password"]').first().fill("admin123");
  await page.keyboard.press("Enter");
  await page.waitForTimeout(3500);
  cookie = await page.evaluate(() => document.cookie.match(/Admin-Token=([^;]+)/)?.[1] || "");
}
ok("登录（拿到 JWT）", !!cookie);
const H = { Authorization: "Bearer " + cookie, "Content-Type": "application/json" };

// 1. 守卫：重复生成客户日验收单应被拦截且带单号
const dup = await page.request.post(BASE + "/dev-api/acceptance/create-by-customer-date",
  { headers: H, data: { customerId: 11, deliveryDate: "2026-08-28" } }).then((r) => r.json()).catch(() => null);
ok("一客户日一验守卫生效", dup && dup.code !== 200 && /YS\d+/.test(dup.msg || ""), (dup && dup.msg || "").slice(0, 80));

// 2. 订单视角定位：找客户11在 2026-08-28 的订单 → by-order 应命中客户日验收单 YS20260909001
const orders = await page.request.get(BASE + "/dev-api/order/sale/list?customerId=11&deliveryDate=2026-08-28", { headers: H }).then((r) => r.json()).catch(() => null);
const oid = orders && orders.data && orders.data.length ? orders.data[0].id : null;
ok("找到测试订单", !!oid, `orderId=${oid}`);
if (oid) {
  const loc = await page.request.get(BASE + "/dev-api/acceptance/by-order/" + oid, { headers: H }).then((r) => r.json()).catch(() => null);
  const v = (loc && loc.data) || {};
  ok("订单视角定位返回客户日", v.customerId === 11 && v.deliveryDate === "2026-08-28", JSON.stringify({ customerId: v.customerId, deliveryDate: v.deliveryDate }));
  ok("订单视角命中客户日验收单", v.hasAcceptance === true && v.acceptanceCode === "YS20260909001", JSON.stringify({ hasAcceptance: v.hasAcceptance, code: v.acceptanceCode }));
}

// 3. 工作台待验收（客户11/08-28 已有草稿非已提交 → 仍应显示，但客户10/09-01 有草稿也不显示已提交隔离）
const wb = await page.request.get(BASE + "/dev-api/workbench/pending-acceptance", { headers: H }).then((r) => r.json()).catch(() => null);
ok("工作台待验收接口可用", wb && Array.isArray(wb.data), `rows=${wb && wb.data ? wb.data.length : "err"}`);

console.log("\n==== GUARD/LOCATE E2E ====");
const failed = checks.filter(([p]) => !p);
if (problems.length === 0 && failed.length === 0) { console.log(`PASS：${checks.length} 项全过`); } else {
  console.log(`FAIL：${failed.length} / 异常 ${problems.length}`); failed.forEach(([, n, d]) => console.log(" -", n, d)); problems.forEach((p) => console.log(" -", p)); process.exit(1);
}
await browser.close();
