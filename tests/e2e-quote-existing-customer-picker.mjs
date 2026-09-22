/**
 * 报价新增交互 E2E（已有客户商品场景）：按需选品 + 行内改别名
 *
 * 覆盖：
 *   1. 客户已有客户商品时，进入详情不自动弹选品（报价表直接展示其客户商品池）
 *   2. 点「添加商品」按需打开商品库，已在报价的商品标记为「已在报价」且不可勾选
 *   3. 勾选新商品 + 自定义别名 → 加入报价并同步 customers_sku
 *   4. 行内「别名」按钮：改名同步 customers_sku 且报价表展示新别名
 *   5. 清理测试数据（删除本次新增的客户商品）
 *
 * 运行：node tests/e2e-quote-existing-customer-picker.mjs
 */
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const API = BASE + "/dev-api";
const CUSTOMER_ID = 11; // 大长江：已有 10 条客户商品
const ALIAS_ADD = "E2E新别名";
const ALIAS_EDIT = "E2E改名";
const problems = [];
let newPoolId = null;

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

  const token = (await page.context().cookies()).find((c) => c.name === "Admin-Token")?.value || "";
  const poolOf = async () => {
    const r = await page.evaluate(
      async ([api, cid, tk]) => {
        const res = await fetch(`${api}/product/customer-sku/pool?customerId=${cid}`, {
          headers: { Authorization: "Bearer " + tk },
        });
        return res.json();
      },
      [API, CUSTOMER_ID, token]
    );
    return (r && r.data) || [];
  };

  const poolBefore = await poolOf();
  console.log("客户已有客户商品:", poolBefore.length);

  // ---------- 进入报价详情（新增态） ----------
  await page.goto(
    BASE + `/basicInfo/quote-detail/index/${CUSTOMER_ID}?quoteId=&mode=add&startDate=2026-09-22&endDate=2026-09-29`,
    { waitUntil: "domcontentloaded" }
  );
  await page.waitForSelector(".vxe-table--body .vxe-body--row", { timeout: 20000 });
  await page.waitForTimeout(2500);

  // 1. 不应自动弹选品
  const picker = page.locator('.el-dialog:has-text("从商品库添加商品")');
  if (await picker.isVisible().catch(() => false)) {
    problems.push("[按需弹窗] 客户已有客户商品时不应自动弹选品");
  } else {
    console.log("未自动弹选品（符合预期）");
  }
  const quoteRowsBefore = await page.locator(".vxe-table--body .vxe-body--row").count();
  console.log("报价表初始行数:", quoteRowsBefore);

  // 2. 手动打开选品
  await page.click('button:has-text("添加商品")');
  await picker.waitFor({ state: "visible", timeout: 10000 });
  await picker.locator(".el-table__body .el-table__row").first().waitFor({ state: "visible", timeout: 15000 });
  const pickerText = await picker.locator(".el-table__body").innerText();
  const inQuoteCount = await page.evaluate(() => {
    const dialogs = Array.from(document.querySelectorAll(".el-dialog"));
    const el = dialogs.find((d) => d.textContent.includes("从商品库添加商品")) || dialogs[dialogs.length - 1];
    let c = el && el.__vueParentComponent;
    while (c && !(c.ctx && c.ctx.skuPicker)) c = c.parent;
    return c ? c.ctx.skuPicker.rows.filter((r) => r._inQuote).length : -1;
  });
  if (inQuoteCount <= 0) problems.push(`[选品] 未把已在报价的商品标记出来（_inQuote=${inQuoteCount}）`);
  console.log("选品弹窗已打开，已在报价行数:", inQuoteCount, "当前页含「已在报价」:", pickerText.includes("已在报价"));

  // 3. 勾选一个「新商品」行 + 填别名
  const rows = picker.locator(".el-table__body .el-table__row");
  const total = await rows.count();
  let targetIdx = -1;
  for (let i = 0; i < total; i++) {
    const txt = await rows.nth(i).innerText();
    if (txt.includes("新商品")) {
      targetIdx = i;
      break;
    }
  }
  if (targetIdx < 0) {
    problems.push("[选品] 未找到「新商品」行");
  } else {
    await rows.nth(targetIdx).locator(".el-checkbox").first().click();
    await rows.nth(targetIdx).locator("td").nth(7).locator("input").fill(ALIAS_ADD);
    await page.waitForTimeout(300);
    await picker.locator('button:has-text("添加")').click();
    await page.waitForSelector(".el-message--success", { timeout: 10000 });
    console.log("提示:", (await page.locator(".el-message--success").first().innerText()).replace(/\s+/g, " "));
  }

  await page.waitForTimeout(1000);
  const quoteRowsAfter = await page.locator(".vxe-table--body .vxe-body--row").count();
  const tableText = await page.locator(".vxe-table--body").innerText();
  if (quoteRowsAfter !== quoteRowsBefore + 1) {
    problems.push(`[报价表] 期望新增 1 行（${quoteRowsBefore}→${quoteRowsBefore + 1}），实际 ${quoteRowsAfter}`);
  }
  if (!tableText.includes(ALIAS_ADD)) problems.push(`[报价表] 未展示别名「${ALIAS_ADD}」`);
  console.log("报价表行数:", quoteRowsAfter, "含新别名:", tableText.includes(ALIAS_ADD));

  // 落库断言
  const poolAfter = await poolOf();
  if (poolAfter.length !== poolBefore.length + 1) {
    problems.push(`[落库] 期望客户商品 +1（${poolBefore.length}→${poolBefore.length + 1}），实际 ${poolAfter.length}`);
  }
  const added = poolAfter.find((p) => p.alias === ALIAS_ADD);
  if (!added) {
    problems.push(`[落库] 未找到别名「${ALIAS_ADD}」的客户商品`);
  } else {
    newPoolId = added.id;
  }
  console.log("customers_sku 条数:", poolAfter.length, "新增条目:", newPoolId);

  // 4. 行内改别名：定位刚新增的报价行
  if (newPoolId) {
    const addedRow = page.locator(".vxe-table--body .vxe-body--row", { hasText: ALIAS_ADD }).first();
    await addedRow.locator('button:has-text("别名")').click();
    const promptBox = page.locator('.el-message-box:has-text("修改客户别名")');
    await promptBox.waitFor({ state: "visible", timeout: 8000 });
    await promptBox.locator("input").first().fill(ALIAS_EDIT);
    await promptBox.locator('button:has-text("确定")').click();
    await page.waitForTimeout(1200);

    const tableText2 = await page.locator(".vxe-table--body").innerText();
    if (!tableText2.includes(ALIAS_EDIT)) problems.push(`[别名] 报价表未更新为「${ALIAS_EDIT}」`);
    const poolRenamed = (await poolOf()).find((p) => p.id === newPoolId);
    if (!poolRenamed || poolRenamed.alias !== ALIAS_EDIT) {
      problems.push(`[别名] customers_sku 别名未同步为「${ALIAS_EDIT}」（实际 ${poolRenamed && poolRenamed.alias}）`);
    }
    console.log("行内改名后报价表/客户商品别名:", tableText2.includes(ALIAS_EDIT), poolRenamed && poolRenamed.alias);
  }

  // 5. 清理
  if (newPoolId) {
    await page.evaluate(
      async ([api, id, tk]) => {
        await fetch(`${api}/product/customer-sku/${id}`, {
          method: "DELETE",
          headers: { Authorization: "Bearer " + tk },
        });
      },
      [API, newPoolId, token]
    );
    console.log("已清理新增客户商品:", newPoolId);
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
