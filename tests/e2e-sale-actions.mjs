// 销售订单列表：状态 → 操作矩阵 + 已确认单只读（2026-09-14 状态收敛）
//
// 业务规则（本次定稿）：
//   · 只有「草稿」可直接改单（列表「修改」+ 详情页可编辑）
//   · 「已确认」不再提供「修改」，必须先「撤回」回草稿；撤回确认框需讲清“改完要重新确认 / 会连带作废扣除 / 什么情况不能撤”
//   · 「删除」仅草稿可见（已确认及之后不可删）
//   · 已确认订单详情页为只读快照（无保存按钮、单元格不可进入编辑）+ 顶部提示条指引撤回
//
// 用法：node tests/e2e-sale-actions.mjs
// 前置：后端 8090 + 前端 1025 已启动；本脚本只读不写库（撤回确认框只会被取消）
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const TROW = ".el-table__body-wrapper tbody tr";
const checks = [];
const errors = [];
const ok = (name, pass, detail = "") => {
  checks.push(!!pass);
  console.log(`${pass ? "OK  " : "FAIL"} ${name}${detail ? " :: " + detail : ""}`);
};

const browser = await chromium.launch({ channel: "msedge", headless: true });
const page = await (await browser.newContext()).newPage();
page.on("pageerror", (e) => errors.push("[pageerror] " + e.message));
page.on("console", (m) => { if (m.type() === "error") errors.push("[console.error] " + m.text().slice(0, 200)); });

await page.goto(BASE + "/login", { waitUntil: "domcontentloaded" });
let cookie = "";
for (let i = 0; i < 3 && !cookie; i++) {
  await page.locator('input[type="text"]').first().fill("admin");
  await page.locator('input[type="password"]').first().fill("admin123");
  await page.keyboard.press("Enter");
  await page.waitForTimeout(2500);
  cookie = await page.evaluate(() => document.cookie.match(/Admin-Token=([^;]+)/)?.[1] || "");
}
ok("登录", !!cookie);
if (!cookie) { await browser.close(); process.exit(1); }

const gotoList = async () => {
  await page.goto(BASE + "/order/sale", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(3500);
};
const allRows = (statusText) => page.locator(TROW).filter({ hasText: statusText });
// 未被送货单占用的已确认行（提供「撤回」按钮）；被占用行只有灰字提示，不再误报“可撤回”
const recallableRow = () => allRows("已确认").filter({ has: page.locator('button:has-text("撤回")') }).first();
const blockedRow = () => allRows("已确认").filter({ has: page.locator(".op-disabled-tip") }).first();
const opsOf = async (loc) => (await loc.count())
  ? (await loc.locator("button").allInnerTexts()).map((s) => s.trim()).filter(Boolean)
  : [];

await gotoList();
const draftOps = await opsOf(allRows("草稿").first());
const confirmedOps = await opsOf(recallableRow());
ok("草稿行操作 = 修改 + 删除", draftOps.join("/") === "修改/删除", draftOps.join("/"));
ok("已确认行不含「修改」", !confirmedOps.includes("修改"), confirmedOps.join("/"));
ok("已确认行不含「删除」", !confirmedOps.includes("删除"), confirmedOps.join("/"));
ok("未占用的已确认行含「撤回」", confirmedOps.includes("撤回"), confirmedOps.join("/"));
if ((await blockedRow().count()) > 0) {
  const blockedOps = await opsOf(blockedRow());
  ok("被送货单占用的已确认行不提供「撤回」（灰字提示先作废）", !blockedOps.includes("撤回"), blockedOps.join("/"));
}

// 撤回确认文案（点开→断言→取消，不写库）
await recallableRow().locator('button:has-text("撤回")').first().click();
await page.waitForTimeout(1200);
const box = page.locator(".el-message-box");
const title = await box.locator(".el-message-box__title").innerText().catch(() => "");
const body = await box.locator(".el-message-box__message").innerText().catch(() => "");
const btns = (await box.locator(".el-message-box__btns button").allInnerTexts()).map((s) => s.trim());
ok("撤回确认框标题明确", title.includes("撤回订单"), title);
ok("文案说明“回到草稿可修改”", /草稿/.test(body) && /修改/.test(body), body.slice(0, 60));
ok("文案说明“会自动作废/扣除未入库采购单”", /作废/.test(body) && /采购单/.test(body), "");
ok("文案说明“不可撤回的情形”", /已入库/.test(body) && /未作废的送货单/.test(body), "");
ok("多行文案按行渲染（HTML 生效）", body.includes("\n"), JSON.stringify(body.slice(0, 30)));
ok("确认按钮文案为「确认撤回」", btns.includes("确认撤回"), btns.join("/"));
await box.locator('button:has-text("取消")').first().click();
await page.waitForTimeout(600);

// 已确认订单详情页：只读
const confirmedCode = await recallableRow().locator("td").nth(2).innerText();
await recallableRow().locator("td").nth(1).dblclick();
await page.waitForTimeout(4000);
const banner = await page.evaluate(() =>
  ([...document.querySelectorAll(".el-alert")].map((x) => x.innerText.replace(/\s+/g, " ").trim())
    .find((t) => t.includes("只读")) || ""));
ok(`已确认单详情页有只读提示条（${confirmedCode.trim()}）`, banner.includes("撤回"), banner.slice(0, 60));
ok("已确认单详情页无「保存」按钮", (await page.locator('button:has-text("保存")').count()) === 0);
{
  const cell = page.locator(".order-table .vxe-table--body-wrapper tbody tr").first().locator("td").nth(4);
  await cell.click();
  await page.waitForTimeout(600);
  ok("已确认单单元格不可进入编辑",
    (await page.locator(".order-table .vxe-table--body-wrapper tbody tr").first().locator("input").count()) === 0);
}

// 草稿订单详情页：可编辑
await gotoList();
const draftCode = await allRows("草稿").first().locator("td").nth(2).innerText();
await allRows("草稿").first().locator("td").nth(1).dblclick();
await page.waitForTimeout(4000);
ok(`草稿单详情页有「保存」按钮（${draftCode.trim()}）`,
  (await page.locator('button:has-text("保存")').count()) > 0);
{
  const cell = page.locator(".order-table .vxe-table--body-wrapper tbody tr").first().locator("td").nth(4);
  await cell.click();
  await page.waitForTimeout(600);
  const editable = (await page.locator(".order-table .vxe-table--body-wrapper tbody tr").first().locator("input").count()) > 0;
  ok("草稿单单元格可进入编辑", editable);
  if (editable) await page.keyboard.press("Escape");
}

ok("无前端报错", errors.length === 0, errors.slice(0, 2).join(" | "));
await browser.close();
console.log(`\n${checks.filter(Boolean).length}/${checks.length} 通过`);
process.exit(checks.every(Boolean) ? 0 : 1);
