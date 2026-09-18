// OA 回归：换货候选过滤 / 换货取消 / 换货确认（含换入行单价）/ 加单取消
//
// 覆盖问题（2026-09-14 修复）：
//   1. 点「换」进入编辑态后，候选菜列表里不能出现「待换的菜」（同名不同规格的 SKU 也排除）
//   2. 编辑态只有「确认」能点、「取消」点了没反应（缺 vxe loadData 重渲染）
//   3. 换入行单价不得为 0（此前恒为 0，验收金额录成 0）
//
// 用法：node tests/e2e-acc-exchange.mjs [orderId]   默认 92（需已确认订单，验收/变更模式均可）
// 前置：后端 8090 + 前端 1025 已启动；脚本写库但结束前必定回退还原
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const API = BASE + "/dev-api";
const ORDER_ID = Number(process.argv[2] || 92);
const ROW = ".order-table .vxe-table--body-wrapper tbody tr";
const DROP = ".product-dropdown-planel .vxe-table--body-wrapper tbody tr";
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
const H = { Authorization: "Bearer " + cookie };
const details = () => page.request.get(API + `/order/saleDetail/list?orderId=${ORDER_ID}`, { headers: H })
  .then((r) => r.json()).then((j) => j.data || []);

// 前置清理：将订单上遗留的配送后变更标记整组回退，保证本脚本可反复执行（幂等）
const revertAllChangeMarks = async () => {
  for (let i = 0; i < 10; i++) {
    const list = await details();
    const marked = list.find((d) => [1, 2, 3].includes(Number(d.changeType)));
    if (!marked) return true;
    const r = await page.request.post(API + `/order/delivery-change/${ORDER_ID}/revoke/${marked.id}`, { headers: H });
    if (r.status() !== 200) return false;
  }
  return false;
};
ok("前置清理：订单无遗留变更标记", await revertAllChangeMarks());

const openPage = async (mode = "acceptance") => {
  await page.goto(BASE + `/order/sale-detail/index?mode=${mode}&orderId=${ORDER_ID}`, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(4500);
};
const rowTexts = () => page.evaluate((sel) =>
  [...document.querySelectorAll(sel)].map((tr) => tr.innerText.replace(/\s+/g, " ").trim()), ROW);
const rowOf = (name) => page.locator(ROW).filter({ hasText: name }).first();
const editRow = () => page.locator(ROW).filter({ has: page.locator('button:has-text("确认")') }).first();

// ==================== 1. 换货：候选过滤 + 取消 ====================
await openPage("acceptance");
const detailBase = await details();
const firstNormal = detailBase.find((d) => (Number(d.changeType) ?? 0) === 0 && Number(d.num) > 0);
ok("页面加载明细行", detailBase.length > 0, `${detailBase.length} 行，首个正常菜：${firstNormal ? firstNormal.productName : "-"}`);
const targetName = firstNormal ? firstNormal.productName : "";
ok("存在可换货的正常行", !!firstNormal, targetName);
if (!firstNormal) { await browser.close(); process.exit(1); }

await rowOf(targetName).locator('button:has-text("换")').first().click();
await page.waitForTimeout(1500);
ok("点「换」进入编辑态（确认/取消可用）",
  (await editRow().count()) === 1
  && (await editRow().locator('button:has-text("取消")').count()) === 1);

// 打开候选菜面板
await editRow().locator("td").nth(2).click().catch(() => { });
await page.waitForTimeout(1500);
const candidates = await page.evaluate((sel) =>
  [...document.querySelectorAll(sel)].map((tr) => tr.innerText.replace(/\s+/g, " ").trim()), DROP);
ok("候选菜面板有数据", candidates.length > 0, `${candidates.length} 条`);
ok(`候选菜不含「待换的菜」（${targetName}）`,
  candidates.length > 0 && !candidates.some((t) => t.includes(targetName)),
  candidates.filter((t) => t.includes(targetName)).slice(0, 3).join(" / "));

// 取消
await editRow().locator('button:has-text("取消")').first().click();
await page.waitForTimeout(1800);
const afterCancel = await rowTexts();
ok("点「取消」生效：编辑行移除、原行还原",
  afterCancel.length === detailBase.length && !afterCancel.some((t) => t.includes("确认")),
  `${afterCancel.length} 行`);

// ==================== 2. 换货：确认（换入行单价不为 0）+ 整组回退 ====================
const detailBefore = await details();
const normalRow = detailBefore.find((d) => (Number(d.changeType) ?? 0) === 0 && Number(d.num) > 0);
ok("存在可换货的正常行", !!normalRow, normalRow ? normalRow.productName : "无");
if (normalRow) {
  await openPage("acceptance");
  await rowOf(normalRow.productName).locator('button:has-text("换")').first().click();
  await page.waitForTimeout(1500);
  await editRow().locator("td").nth(2).click().catch(() => { });
  await page.waitForTimeout(1500);
  await page.locator(DROP).first().click(); // 选第一个候选（已被过滤掉待换菜）
  await page.waitForTimeout(1200);
  await page.keyboard.type("2");            // 数量（选品后焦点自动落到数量单元格）
  await page.waitForTimeout(400);
  await Promise.all([
    page.waitForResponse((r) => r.url().includes("/delivery-change/") && r.url().endsWith("/exchange"), { timeout: 25000 })
      .then((r) => ok("换货接口 200", r.status() === 200, "status=" + r.status()))
      .catch(() => ok("换货接口 200", false, "无响应")),
    editRow().locator('button:has-text("确认")').first().click(),
  ]);
  await page.waitForTimeout(2500);

  const afterExchange = await details();
  const returned = afterExchange.find((d) => d.id === normalRow.id);
  const exchangedIn = afterExchange.find((d) => (d.changeType ?? 0) === 2);
  ok("被换行已标退货（应送归 0）",
    returned && Number(returned.changeType) === 3 && Number(returned.num) === 0,
    returned ? `changeType=${returned.changeType} num=${returned.num}` : "-");
  ok("新增换入行 change_type=2 且单价 > 0（P0 单价修复）",
    exchangedIn && Number(exchangedIn.productPrice) > 0,
    exchangedIn ? `${exchangedIn.productName} 单价=${exchangedIn.productPrice} 数量=${exchangedIn.num}` : "无换入行");
  ok("换入行与被换行同 change_group",
    exchangedIn && returned && String(exchangedIn.changeGroup) === String(returned.changeGroup),
    `${exchangedIn && exchangedIn.changeGroup} vs ${returned && returned.changeGroup}`);

  // 整组回退还原
  await openPage("acceptance");
  await Promise.all([
    page.waitForResponse((r) => r.url().includes("/delivery-change/") && r.url().includes("/revoke"), { timeout: 25000 })
      .then((r) => ok("回退接口 200", r.status() === 200, "status=" + r.status()))
      .catch(() => ok("回退接口 200", false, "无响应")),
    rowOf(exchangedIn ? exchangedIn.productName : normalRow.productName).locator('button:has-text("回退")').first().click()
      .then(() => page.waitForTimeout(700))
      .then(() => page.locator('.el-message-box button:has-text("确定")').first().click()),
  ]);
  await page.waitForTimeout(2500);
  const afterRevert = await details();
  const restored = afterRevert.find((d) => d.id === normalRow.id);
  ok("换货回退：被换行恢复原数量、换入行删除",
    restored && Number(restored.changeType) === 0
    && Number(restored.num) === Number(normalRow.num)
    && !afterRevert.some((d) => (d.changeType ?? 0) === 2 && d.id !== normalRow.id),
    restored ? `${restored.productName} num=${restored.num} changeType=${restored.changeType}` : "-");
}

// ==================== 3. 加单：取消移除编辑行 ====================
await openPage("change");
const rowsBeforeAdd = await rowTexts();
await page.locator('button:has-text("加单")').first().click();
await page.waitForTimeout(1500);
const editingAfterAdd = await editRow().count();
if (editingAfterAdd === 0) {
  ok("加单入口存在（编辑行出现）", false, "未找到编辑行，跳过取消校验");
} else {
  await editRow().locator('button:has-text("取消")').first().click();
  await page.waitForTimeout(1800);
  const rowsAfterAdd = await rowTexts();
  ok("加单取消生效：编辑行移除",
    rowsAfterAdd.length === rowsBeforeAdd.length && !rowsAfterAdd.some((t) => t.includes("确认")),
    `${rowsBeforeAdd.length} → ${rowsAfterAdd.length} 行`);
}

// ==================== 4. 退出变更（顶部「退出变更」/工具条「返回列表」）====================
const pathOf = () => new URL(page.url()).pathname;
await openPage("change");
await page.locator('button:has-text("退出变更")').first().click();
await page.waitForTimeout(2500);
ok("顶部「退出变更」返回订单列表", pathOf() === "/order/sale", pathOf());
await openPage("change");
await page.locator('button:has-text("返回列表")').first().click();
await page.waitForTimeout(2500);
ok("工具条「返回列表」返回订单列表", pathOf() === "/order/sale", pathOf());

ok("无前端报错", errors.length === 0, errors.slice(0, 2).join(" | "));
await browser.close();
console.log(`\n${checks.filter(Boolean).length}/${checks.length} 通过`);
process.exit(checks.every(Boolean) ? 0 : 1);
