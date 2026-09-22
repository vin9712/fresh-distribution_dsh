/**
 * 报价新增交互 E2E：从商品库批量选品 + 自定义客户别名 + 同步客户商品
 *
 * 背景：客户没有客户商品时，报价详情页原先只能展示空表，无法录入；
 *       已有客户商品的客户也只能一条条去「客户商品」页新增。
 *       本次改造：新增报价走建单对话框；详情页「添加商品」从商品库批量挑选，
 *       可逐行填客户别名，确认后自动同步为 customers_sku。
 *
 * 步骤：登录 → /basicInfo/quote → 新增报价（建单对话框）→ 进入详情
 *      → 自动弹出「从商品库添加商品」（客户无客户商品）
 *      → 勾选商品 + 填别名 → 确认 → 断言报价表新增行 + 别名展示
 *      → 接口断言 customers_sku 落库 → 清理测试数据
 *
 * 运行：node tests/e2e-quote-add-from-library.mjs
 */
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const API = BASE + "/dev-api";
const EMPTY_CUSTOMER_ID = 12; // 广德：无客户商品
const EMPTY_CUSTOMER_NAME = "广德";
const ALIAS = "E2E别名";
const problems = [];
let createdCustomerSkuIds = [];

async function main() {
  const browser = await chromium.launch({ channel: "chrome", headless: true });
  const page = await browser.newPage({ viewport: { width: 1600, height: 900 } });

  page.on("pageerror", (e) => problems.push(`[pageerror] ${page.url()} :: ${e.message}`));
  page.on("console", (m) => {
    if (m.type() === "error") problems.push(`[console.error] ${page.url()} :: ${m.text().slice(0, 300)}`);
  });
  page.on("response", (r) => {
    if (r.status() >= 500) problems.push(`[http${r.status()}] ${r.url()}`);
  });

  // ---------- 登录 ----------
  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder*="账号"]', "admin");
  await page.fill('input[placeholder*="密码"]', "admin123");
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });
  console.log("LOGIN OK");

  // ---------- 报价列表：新增报价建单对话框 ----------
  await page.goto(BASE + "/basicInfo/quote", { waitUntil: "domcontentloaded" });
  await page.waitForSelector('.quick-table .vxe-table', { timeout: 20000 });
  await page.waitForTimeout(800);

  await page.click('button:has-text("新增报价")');
  const createDialog = page.locator('.el-dialog:has-text("新增报价")');
  await createDialog.waitFor({ state: "visible", timeout: 8000 });
  console.log("建单对话框已打开");

  // 选客户（el-select 非原生，需点开下拉再选项）
  await createDialog.locator(".el-select").first().click();
  await page.keyboard.type(EMPTY_CUSTOMER_NAME);
  await page.waitForTimeout(500);
  const option = page.locator(`.el-select-dropdown:visible .el-select-dropdown__item:has-text("${EMPTY_CUSTOMER_NAME}")`).first();
  await option.waitFor({ state: "visible", timeout: 5000 });
  await option.click();
  await page.waitForTimeout(300);

  // 生效时间已默认填 7 天，直接进入
  await createDialog.locator('button:has-text("进入报价")').click();
  await page.waitForURL((u) => String(u).includes("/basicInfo/quote-detail/index/" + EMPTY_CUSTOMER_ID), {
    timeout: 15000,
  });
  console.log("已跳转报价详情:", page.url());

  // ---------- 详情页：客户无客户商品 → 自动弹出商品库 ----------
  const picker = page.locator('.el-dialog:has-text("从商品库添加商品")');
  await picker.waitFor({ state: "visible", timeout: 15000 });
  // 商品库数据异步加载，等行渲染出来再计数
  await picker.locator(".el-table__body .el-table__row").first().waitFor({ state: "visible", timeout: 15000 });
  console.log("商品库选品弹窗已自动打开");

  const rowCount = await picker.locator(".el-table__body .el-table__row").count();
  if (rowCount === 0) {
    problems.push("[选品] 商品库弹窗没有任何商品行");
  }
  console.log("商品库可选行数:", rowCount);

  // 勾选前两行可勾选的行（排除已在报价的禁用行）
  const rows = picker.locator(".el-table__body .el-table__row");
  let picked = 0;
  for (let i = 0; i < rowCount && picked < 2; i++) {
    const cb = rows.nth(i).locator('.el-checkbox__original, input[type="checkbox"]').first();
    const disabled = await cb.isDisabled().catch(() => true);
    if (disabled) continue;
    await rows.nth(i).locator(".el-checkbox").first().click();
    picked++;
  }
  if (picked < 2) problems.push(`[选品] 可勾选商品不足 2 个（实际 ${picked}）`);
  console.log("已勾选商品数:", picked);

  // 给第一行填别名
  const aliasInput = picker.locator(".el-table__body .el-table__row").first().locator("td").nth(7).locator("input");
  await aliasInput.fill(ALIAS);

  const selectedText = await picker.locator(".sku-picker-count").innerText();
  if (!selectedText.includes(String(picked))) problems.push(`[选品] 已选计数不符: ${selectedText}`);

  // 捕获弹窗内选中的参考价（确认后应落入「新报价」列，且新商品「上次价」为空）
  const pickedInfo = await page.evaluate(() => {
    const dialogs = Array.from(document.querySelectorAll(".el-dialog"));
    const el = dialogs.find((d) => d.textContent.includes("从商品库添加商品")) || dialogs[dialogs.length - 1];
    let c = el && el.__vueParentComponent;
    while (c && !(c.ctx && c.ctx.skuPicker)) c = c.parent;
    return c
      ? c.ctx.skuPicker.rows
          .filter((r) => r._selected)
          .map((r) => ({ id: r.id, name: r.name, ref: r._refPrice, inPool: r._inPool }))
      : [];
  });

  await picker.locator('button:has-text("添加")').click();
  await page.waitForSelector(".el-message--success", { timeout: 10000 });
  const msg = await page.locator(".el-message--success").first().innerText();
  console.log("提示:", msg.replace(/\s+/g, " "));
  if (!msg.includes("已添加")) problems.push(`[确认] 成功提示异常: ${msg}`);

  // ---------- 报价表断言 ----------
  await page.waitForTimeout(800);
  const quoteRows = page.locator(".vxe-table--body .vxe-body--row");
  const qCount = await quoteRows.count();
  if (qCount < picked) problems.push(`[报价表] 期望至少 ${picked} 行，实际 ${qCount}`);
  const tableText = await page.locator(".vxe-table--body").innerText();
  if (!tableText.includes(ALIAS)) problems.push(`[报价表] 未展示自定义别名「${ALIAS}」`);
  console.log("报价表行数:", qCount, "含别名:", tableText.includes(ALIAS));

  // 参考价 → 新报价列；新商品上次价为空/0
  const quoteList = await page.evaluate(() => {
    let c = document.querySelector(".app-container")?.__vueParentComponent;
    while (c && !(c.ctx && c.ctx.skuQuoteList)) c = c.parent;
    return c
      ? c.ctx.skuQuoteList.map((r) => ({ skuId: r.skuId, basePrice: r.basePrice, price: r.price }))
      : [];
  });
  for (const info of pickedInfo) {
    const row = quoteList.find((r) => r.skuId === info.id);
    if (!row) {
      problems.push(`[参考价] 报价表未找到 skuId=${info.id} 的行`);
      continue;
    }
    const price = parseFloat(String(row.price).replace(/[,，\s]/g, ""));
    if (info.ref != null && Math.abs(price - info.ref) > 0.001) {
      problems.push(`[参考价] ${info.name} 新报价应为参考价 ${info.ref}，实际 ${row.price}`);
    }
    const base = row.basePrice == null || row.basePrice === "" ? 0 : parseFloat(row.basePrice);
    if (!info.inPool && base > 0) {
      problems.push(`[上次价] 新商品 ${info.name} 上次价应为空/0，实际 ${row.basePrice}`);
    }
  }
  const baseTexts = await page.locator(".vxe-table--body .vxe-body--row").allInnerTexts();
  if (!baseTexts.some((t) => t.includes("-"))) {
    console.log("[上次价] 提示：当前页未见 - 展示（可能上次价列被省略号截断）");
  }
  console.log("参考价入新报价列 & 新商品上次价为空：校验完成");

  // ---------- 接口断言：customers_sku 落库 ----------
  const token = (await page.context().cookies()).find((c) => c.name === "Admin-Token")?.value || "";
  const poolResp = await page.evaluate(
    async ([api, cid, tk]) => {
      const r = await fetch(`${api}/product/customer-sku/pool?customerId=${cid}`, {
        headers: { Authorization: "Bearer " + tk },
      });
      return r.json();
    },
    [API, EMPTY_CUSTOMER_ID, token]
  );
  const pool = (poolResp && poolResp.data) || [];
  createdCustomerSkuIds = pool.map((p) => p.id);
  if (pool.length < picked) {
    problems.push(`[落库] customers_sku 期望 >= ${picked} 条，实际 ${pool.length}`);
  }
  const aliased = pool.find((p) => p.alias === ALIAS);
  if (!aliased) problems.push(`[落库] 未找到别名「${ALIAS}」的客户商品`);
  console.log("customers_sku 落库条数:", pool.length, "别名命中:", !!aliased);

  // ---------- 清理测试数据 ----------
  if (createdCustomerSkuIds.length) {
    await page.evaluate(
      async ([api, ids, tk]) => {
        await fetch(`${api}/product/customer-sku/${ids.join(",")}`, {
          method: "DELETE",
          headers: { Authorization: "Bearer " + tk },
        });
      },
      [API, createdCustomerSkuIds, token]
    );
    console.log("已清理测试数据:", createdCustomerSkuIds.join(","));
  }

  await browser.close();

  console.log("\n==== RESULT ====");
  if (problems.length) {
    problems.forEach((p) => console.log("FAIL " + p));
    process.exit(1);
  }
  console.log("ALL PASS");
}

main().catch((e) => {
  console.error("FATAL", e);
  process.exit(1);
});
