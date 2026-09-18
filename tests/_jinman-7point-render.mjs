/** 金满楼7点实单渲染验证：matrix:13:2026-09-18 → 断言c1..c7七列全有数、colBlocks=1，截图。 */
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const API = BASE + "/dev-api";
const REPORT_ID = "6364325618227429623";
const problems = [];

const browser = await chromium.launch({ channel: "chrome", headless: true });
const page = await browser.newPage({ viewport: { width: 1500, height: 1200 } });
await page.goto(BASE + "/login", { waitUntil: "networkidle" });
await page.fill('input[placeholder*="账号"]', "admin");
await page.fill('input[placeholder*="密码"]', "admin123");
await page.click(".login-button, .el-button--primary");
await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });
const token = (await page.context().cookies(BASE)).find((c) => c.name === "Admin-Token").value;

const bizKey = "matrix:13:2026-09-18";
const ticket = (await (await page.request.post(API + "/print/ticket", {
  headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
  data: { bizKey },
})).json()).ticket;
if (!ticket) problems.push("票据签发失败");

const showPromise = page.waitForResponse((r) => r.url().includes("/jmreport/show"), { timeout: 30000 });
await page.goto(BASE + "/jmreport/view/" + REPORT_ID + "?token=" + ticket + "&ticket=" + ticket
  + "&customerId=13&deliveryDate=2026-09-18", { waitUntil: "domcontentloaded" });
const show = await (await showPromise).json();
if (show.code !== 200) { console.log("show失败:", JSON.stringify(show).slice(0, 300)); process.exit(1); }
const ds = show.result.dataList;
const hm = ds.hm?.list?.[0] || {};
const dm = ds.dm?.list || [];

console.log("printTitle:", hm.printTitle, "| deliveryDate:", hm.deliveryDate);
console.log("列名 c1..c7:", [1, 2, 3, 4, 5, 6, 7].map((i) => hm["c" + i + "Name"]));
console.log("pointCount:", hm.pointCount, "| colBlocks:", hm.totalColBlocks, "| totalQuantity:", hm.totalQuantity, "| 品项数:", hm.totalKinds);
console.log("明细行数:", dm.length);
console.log("样例首行:", dm[0] ? { name: dm[0].productName, unit: dm[0].productUnit, c1: dm[0].c1, c2: dm[0].c2, c3: dm[0].c3, c4: dm[0].c4, c5: dm[0].c5, c6: dm[0].c6, c7: dm[0].c7, total: dm[0].total } : null);
// 预期：dept10→c1(西红柿2,大白菜1) dept11→c2(3,2) ... dept16→c7(9,8)
const expect = (i) => [String(i + 1), String(i)];
const deptCols = {};
for (const row of dm) {
  [1, 2, 3, 4, 5, 6, 7].forEach((i) => { if (row["c" + i] !== "" && row["c" + i] != null) deptCols[i] = true; });
}
const filled = Object.keys(deptCols).map(Number).sort((a, b) => a - b);
if (JSON.stringify(filled) !== "[1,2,3,4,5,6,7]") problems.push("c1..c7 未全部有数: " + JSON.stringify(filled));
if (hm.pointCount !== 7) problems.push("pointCount≠7");
if (Number(hm.totalColBlocks) !== 1) problems.push("7点应为单列块, 实际=" + hm.totalColBlocks);
if (!dm.length) problems.push("明细为空");
// 恒等式抽查：西红柿行 total = c1+c2+...c7
const tomato = dm.find((r) => r.productName === "西红柿");
if (tomato) {
  const sum = [1, 2, 3, 4, 5, 6, 7].reduce((s, i) => s + Number(tomato["c" + i] || 0), 0);
  if (sum !== Number(tomato.total)) problems.push(`西红柿行合计不符: c1..c7和=${sum} total=${tomato.total}`);
  console.log("西红柿行:", [1, 2, 3, 4, 5, 6, 7].map((i) => tomato["c" + i] ?? ""), "合计=", tomato.total);
} else problems.push("未找到西红柿行");

await page.waitForTimeout(5000);
await page.screenshot({ path: "/tmp/jinman-7points.png", fullPage: false });
await browser.close();
console.log(problems.length ? "FAIL:\n" + problems.join("\n") : "PASS: 7点一页(c1..c7全有数), colBlocks=1, 行合计恒等式通过");
process.exit(problems.length ? 1 : 0);
