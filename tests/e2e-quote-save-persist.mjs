/**
 * 报价新增全链路 E2E：商品库选品 → 同步客户商品 → 提交报价 → 编辑态回显
 *
 * 背景：报价明细是按「客户商品池」重建的，若选品只加到报价表而不落 customers_sku，
 *       再次编辑时明细会凭空消失。本用例守护该持久化闭环。
 *
 * 步骤：登录 → 客户12（无客户商品）新增报价 → 自动弹选品 → 勾 2 个 + 别名
 *      → 确认 → 提交 → 断言 create 成功 → 接口校验报价明细
 *      → 重新进入编辑态 → 断言明细回显（含别名）
 *      → 清理：删除本次报价单 + customers_sku
 *
 * 运行：node tests/e2e-quote-save-persist.mjs
 */
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const API = BASE + "/dev-api";
const CUSTOMER_ID = 12;
const ALIAS = "E2E持久化别名";
const problems = [];
let createdQuoteId = null;

async function main() {
  const browser = await chromium.launch({ channel: "chrome", headless: true });
  const page = await browser.newPage({ viewport: { width: 1600, height: 900 } });

  page.on("pageerror", (e) => problems.push(`[pageerror] ${page.url()} :: ${e.message}`));
  page.on("console", (m) => {
    if (m.type() === "error") problems.push(`[console.error] ${page.url()} :: ${m.text().slice(0, 300)}`);
  });
  page.on("response", async (r) => {
    if (r.status() >= 500) problems.push(`[http${r.status()}] ${r.url()}`);
    if (/\/product\/quote\/(create|update)$/.test(r.url())) {
      try {
        const b = await r.json();
        if (b && b.code === 200 && b.data && b.data.id) createdQuoteId = b.data.id;
        if (b && b.code && b.code !== 200) problems.push(`[保存接口] code=${b.code} ${String(b.msg || "").slice(0, 200)}`);
      } catch (e) {}
    }
  });

  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder*="账号"]', "admin");
  await page.fill('input[placeholder*="密码"]', "admin123");
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });
  const token = (await page.context().cookies()).find((c) => c.name === "Admin-Token")?.value || "";

  const apiGet = (path) =>
    page.evaluate(
      async ([api, p, tk]) => {
        const r = await fetch(api + p, { headers: { Authorization: "Bearer " + tk } });
        return r.json();
      },
      [API, path, token]
    );

  // ---------- 新增报价 ----------
  await page.goto(
    BASE + `/basicInfo/quote-detail/index/${CUSTOMER_ID}?quoteId=&mode=add&startDate=2026-09-22&endDate=2026-09-29`,
    { waitUntil: "domcontentloaded" }
  );
  const picker = page.locator('.el-dialog:has-text("从商品库添加商品")');
  await picker.waitFor({ state: "visible", timeout: 15000 });
  await picker.locator(".el-table__body .el-table__row").first().waitFor({ state: "visible", timeout: 15000 });

  const rows = picker.locator(".el-table__body .el-table__row");
  await rows.nth(0).locator(".el-checkbox").first().click();
  await rows.nth(0).locator("td").nth(7).locator("input").fill(ALIAS);
  await rows.nth(1).locator(".el-checkbox").first().click();
  await page.waitForTimeout(300);
  await picker.locator('button:has-text("添加")').click();
  await page.waitForSelector(".el-message--success", { timeout: 10000 });
  await page.waitForTimeout(1000);

  const tableText = await page.locator(".vxe-table--body").innerText();
  if (!tableText.includes(ALIAS)) problems.push(`[报价表] 未展示别名「${ALIAS}」`);
  console.log("选品完成，报价表含别名:", tableText.includes(ALIAS));

  // ---------- 填价并提交（后端要求明细价格必须为正数） ----------
  await page.evaluate(() => {
    let c = document.querySelector(".app-container")?.__vueParentComponent;
    while (c && !(c.ctx && c.ctx.skuQuoteList)) c = c.parent;
    if (c) c.ctx.skuQuoteList.forEach((r) => (r.price = "1.50"));
  });
  await page.waitForTimeout(300);
  await page.click('button:has-text("提交")');
  await page.waitForURL((u) => String(u).includes("/basicInfo/quote") && !String(u).includes("quote-detail"), {
    timeout: 15000,
  });
  console.log("提交后已返回报价列表");

  if (!createdQuoteId) {
    problems.push("[提交] 未捕获到新建报价单 id");
  } else {
    console.log("新建报价单 id:", createdQuoteId);
  }

  // ---------- 接口校验明细 ----------
  if (createdQuoteId) {
    const detail = await apiGet(`/quote/quoteDetail/list?quoteId=${createdQuoteId}`);
    const list = (detail && detail.data) || [];
    if (list.length !== 2) problems.push(`[明细] 期望 2 条，实际 ${list.length}`);
    const aliased = list.find((d) => d.productName === ALIAS);
    if (!aliased) problems.push(`[明细] 未找到别名「${ALIAS}」的明细`);
    console.log("报价明细条数:", list.length, "别名命中:", !!aliased);

    // ---------- 编辑态回显 ----------
    await page.goto(
      BASE + `/basicInfo/quote-detail/index/${CUSTOMER_ID}?quoteId=${createdQuoteId}&mode=edit`,
      { waitUntil: "domcontentloaded" }
    );
    await page.waitForSelector(".vxe-table--body .vxe-body--row", { timeout: 20000 });
    await page.waitForTimeout(1500);
    const editText = await page.locator(".vxe-table--body").innerText();
    const editRows = await page.locator(".vxe-table--body .vxe-body--row").count();
    if (editRows !== 2) problems.push(`[编辑回显] 期望 2 行，实际 ${editRows}`);
    if (!editText.includes(ALIAS)) problems.push(`[编辑回显] 未回显别名「${ALIAS}」`);
    console.log("编辑态行数:", editRows, "别名回显:", editText.includes(ALIAS));
  }

  // ---------- 清理 ----------
  if (createdQuoteId) {
    await page.evaluate(
      async ([api, id, tk]) => {
        await fetch(`${api}/product/quote/${id}`, { method: "DELETE", headers: { Authorization: "Bearer " + tk } });
      },
      [API, createdQuoteId, token]
    );
    console.log("已删除测试报价单:", createdQuoteId);
  }
  const pool = await apiGet(`/product/customer-sku/pool?customerId=${CUSTOMER_ID}`);
  const poolIds = ((pool && pool.data) || []).map((p) => p.id);
  if (poolIds.length) {
    await page.evaluate(
      async ([api, ids, tk]) => {
        await fetch(`${api}/product/customer-sku/${ids.join(",")}`, { method: "DELETE", headers: { Authorization: "Bearer " + tk } });
      },
      [API, poolIds, token]
    );
    console.log("已清理测试客户商品:", poolIds.join(","));
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
