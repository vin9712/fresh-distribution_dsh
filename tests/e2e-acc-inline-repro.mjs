// OA 行内换货 真机复现：点「换」→ 编辑行是否出现/激活
import { chromium } from "playwright-core";
const BASE = "http://localhost:1025";
const browser = await chromium.launch({ channel: "msedge", headless: true });
const page = await (await browser.newContext()).newPage();
const errors = [];
page.on("pageerror", (e) => errors.push("PAGEERROR: " + e.message));
page.on("console", (m) => { if (m.type() === "error") errors.push("CONSOLE: " + m.text().slice(0, 200)); });

await page.goto(BASE + "/login", { waitUntil: "domcontentloaded" });
let cookie = "";
for (let i = 0; i < 3 && !cookie; i++) {
  await page.locator('input[type="text"]').first().fill("admin");
  await page.locator('input[type="password"]').first().fill("admin123");
  await page.keyboard.press("Enter");
  await page.waitForTimeout(3000);
  cookie = await page.evaluate(() => document.cookie.match(/Admin-Token=([^;]+)/)?.[1] || "");
}
console.log("cookie:", cookie ? "OK" : "FAIL");

// 直接进验收模式（订单 88 → 验收单 YS20260909003 草稿）
await page.goto(BASE + "/order/sale-detail/index?mode=acceptance&orderId=88", { waitUntil: "domcontentloaded" });
await page.waitForTimeout(3500);

// 表格行数与换按钮数
const rows = await page.locator(".order-table .vxe-table--body-wrapper table tbody tr").count();
const changeBtns = await page.locator(".order-table button:has-text('换')").count();
const confirmBtnsBefore = await page.locator(".order-table button:has-text('确认')").count();
console.log("rows:", rows, "换btn:", changeBtns, "确认btn(before):", confirmBtnsBefore);

// 点第一行的 换
await page.locator(".order-table button:has-text('换')").first().click();
await page.waitForTimeout(1200);

const confirmBtnsAfter = await page.locator(".order-table button:has-text('确认')").count();
const cancelBtnsAfter = await page.locator(".order-table button:has-text('取消')").count();
console.log("确认btn(after):", confirmBtnsAfter, "取消btn(after):", cancelBtnsAfter);

// 激活编辑单元格（报价面板 vxe-input）
const activeEdit = await page.evaluate(() => {
  const active = document.querySelector(".vxe-table--body-wrapper .col--active, .vxe-table--body-wrapper .vxe-cell--edit");
  const inner = document.querySelector(".vxe-table--body-wrapper .vxe-input--inner");
  return { activeCell: !!active, innerInput: !!inner, focused: document.activeElement === inner };
});
console.log("edit cell state:", JSON.stringify(activeEdit));

// 行内容快照（前2行）
const snap = await page.evaluate(() => {
  return [...document.querySelectorAll(".order-table .vxe-table--body-wrapper table tbody tr")]
    .slice(0, 3)
    .map((tr) => tr.innerText.replace(/\s+/g, " ").slice(0, 80));
});
console.log("rows snapshot:", JSON.stringify(snap, null, 1));
console.log("errors:", errors.length ? errors : "none");
await browser.close();

// --- 诊断附加：按钮 disabled 状态 + toast 捕获 ---
