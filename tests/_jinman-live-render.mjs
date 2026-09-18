/** 参数化金满楼总单渲染验证：登录→按bizKey签票据→带参打开视图→断言show返回hm/dm数据。 */
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const API = BASE + "/dev-api";
const REPORT_ID = "6364325618227429623";
const cases = [
  { customerId: "11", deliveryDate: "2026-09-10", label: "有单客户11" },
  { customerId: "13", deliveryDate: "2026-09-17", label: "金满楼13(无单)" },
];
const problems = [];

const browser = await chromium.launch({ channel: "chrome", headless: true });
const page = await browser.newPage({ viewport: { width: 1500, height: 1200 } });
await page.goto(BASE + "/login", { waitUntil: "networkidle" });
await page.fill('input[placeholder*="账号"]', "admin");
await page.fill('input[placeholder*="密码"]', "admin123");
await page.click(".login-button, .el-button--primary");
await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });
const cookies = await page.context().cookies(BASE);
const token = (cookies.find((c) => c.name === "Admin-Token") || {}).value;
if (!token) problems.push("未取到登录 token");

for (const c of cases) {
  const bizKey = `matrix:${c.customerId}:${c.deliveryDate}`;
  const ticket = (await page.request.post(API + "/print/ticket", {
    headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
    data: { bizKey },
  }).then((r) => r.json())).ticket;
  if (!ticket) { problems.push(`${c.label} 票据签发失败`); continue; }

  const showPromise = page.waitForResponse((r) => r.url().includes("/jmreport/show"), { timeout: 30000 });
  await page.goto(BASE + "/jmreport/view/" + REPORT_ID + "?token=" + ticket + "&ticket=" + ticket
    + "&customerId=" + c.customerId + "&deliveryDate=" + c.deliveryDate, { waitUntil: "domcontentloaded" });
  let show;
  try { show = await (await showPromise).json(); } catch (e) { show = null; }
  if (!show || show.code !== 200) { problems.push(`${c.label} show失败`); continue; }
  const ds = show.result?.dataList || {};
  const hm = ds.hm?.list?.[0] || {};
  const dm = ds.dm?.list || [];
  console.log(`[${c.label}] hm.printTitle=${JSON.stringify(hm.printTitle)} deliveryDate=${hm.deliveryDate}`);
  console.log(`[${c.label}] c1Name..c7Name=`, [1, 2, 3, 4, 5, 6, 7].map((i) => hm["c" + i + "Name"]));
  console.log(`[${c.label}] dm行数=${dm.length} 首行=`, dm[0] ? { productName: dm[0].productName, unit: dm[0].productUnit, c1: dm[0].c1, c2: dm[0].c2, c7: dm[0].c7, total: dm[0].total } : null);
  console.log(`[${c.label}] totalQuantity=${hm.totalQuantity} pointCount=${hm.pointCount} colBlocks=${hm.totalColBlocks}`);
  if (c.customerId === "11") {
    if (!dm.length) problems.push("有单客户明细未渲染");
    if (!hm.printTitle || !String(hm.printTitle).includes("总单")) problems.push("printTitle未绑定");
    if (dm.length && !("c7" in dm[0])) problems.push("dm缺少c7槽位字段");
    await page.waitForTimeout(5000);
    await page.screenshot({ path: "/tmp/jinman-live-customer11.png", fullPage: false });
  }
  await page.waitForTimeout(1200);
}
await browser.close();
console.log(problems.length ? "FAIL:\n" + problems.join("\n") : "PASS: hm/dm 两数据集均出数且绑定生效");
process.exit(problems.length ? 1 : 0);
