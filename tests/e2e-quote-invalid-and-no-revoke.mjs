/**
 * 报价「不可撤销 / 只能复制为草稿 / 可作废」E2E
 *
 * 需求：已发布报价不可撤回为草稿（后端护栏），行内不再提供「撤销」，
 *       提供「复制为草稿」；未生效的已发布报价可「失效」（带确认弹窗）。
 *
 * 步骤：登录 → 造一张未来生效草稿（今天+10~+20，客户12）→ 列表搜索
 *      → 断言行内无「撤销」、有「复制为草稿」
 *      → 点「复制为草稿」→ 进入报价详情且为新增态（复制草稿）
 *      → 返回列表 → 发布该单 → 因未到生效期 valid=0 → 出现「失效」
 *      → 点「失效」→ 确认弹窗 → 确认 → 提示「作废成功」且状态变「失效」
 *      → 清理：删除测试报价单
 *
 * 运行：node tests/e2e-quote-invalid-and-no-revoke.mjs
 */
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const API = BASE + "/dev-api";
const CUSTOMER_ID = 12;
const problems = [];
let createdQuoteId = null;
let quoteCode = null;

const fmt = (d) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;

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

  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder*="账号"]', "admin");
  await page.fill('input[placeholder*="密码"]', "admin123");
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });
  const token = (await page.context().cookies()).find((c) => c.name === "Admin-Token")?.value || "";
  const auth = { Authorization: "Bearer " + token, "Content-Type": "application/json" };

  // ---------- 造未来生效草稿 ----------
  const today = new Date();
  const start = new Date(today.getTime() + 10 * 86400000);
  const end = new Date(today.getTime() + 20 * 86400000);
  const codeResp = await page.evaluate(
    async ([api, tk]) => {
      const r = await fetch(`${api}/product/quote/code?refresh=true`, { headers: { Authorization: "Bearer " + tk } });
      return r.json();
    },
    [API, token]
  );
  quoteCode = codeResp && codeResp.msg;
  const createResp = await page.evaluate(
    async ([api, h, body]) => {
      const r = await fetch(`${api}/product/quote/create`, { method: "POST", headers: h, body: JSON.stringify(body) });
      return r.json();
    },
    [
      API,
      auth,
      {
        customerId: CUSTOMER_ID,
        quoteCode,
        effectiveStartDate: fmt(start),
        effectiveEndDate: fmt(end),
        remark: "E2E未来生效",
        quoteDetails: [{ skuId: 1, productName: "E2E商品", productUnit: "斤", productSpec: "", price: 8.8 }],
      },
    ]
  );
  if (createResp && createResp.code === 200 && createResp.data) createdQuoteId = createResp.data.id;
  if (!createdQuoteId) problems.push(`[前置] 造草稿失败: ${JSON.stringify(createResp).slice(0, 200)}`);
  console.log("草稿报价:", createdQuoteId, quoteCode);

  // 列表默认按生效时间过滤（今天~+7），未来单需把筛选区间挪到未来
  const searchQuote = async () => {
    await page.evaluate(
      ([code, s, e]) => {
        let c = document.querySelector(".app-container")?.__vueParentComponent;
        while (c && !(c.ctx && c.ctx.queryParams)) c = c.parent;
        if (c) {
          c.ctx.queryParams.code = code;
          c.ctx.queryParams.effectiveDateRange = [s, e];
          c.ctx.handleQuery();
        }
      },
      [quoteCode, fmt(start), fmt(end)]
    );
    await page.waitForTimeout(1200);
  };

  // ---------- 列表：行内动作断言 ----------
  await page.goto(BASE + "/basicInfo/quote", { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".quick-table .vxe-table", { timeout: 20000 });
  await page.waitForTimeout(600);
  await searchQuote();

  const row = page.locator(".vxe-table--body .vxe-body--row", { hasText: quoteCode }).first();
  await row.waitFor({ state: "visible", timeout: 10000 });
  const rowText = await row.innerText();
  if (rowText.includes("撤销")) problems.push("[行内] 不应再出现「撤销」");
  if (!rowText.includes("复制为草稿")) problems.push("[行内] 缺少「复制为草稿」");
  console.log("行内动作:", rowText.replace(/\s+/g, " "));

  // ---------- 复制为草稿 ----------
  await row.locator('button:has-text("复制为草稿")').click();
  await page.waitForURL((u) => String(u).includes("mode=copy"), { timeout: 15000 });
  console.log("复制为草稿已跳转:", page.url());
  if (!page.url().includes("mode=copy")) problems.push("[复制为草稿] 未进入复制态");

  // ---------- 发布（未到生效期 → valid=0）----------
  await page.goto(BASE + "/basicInfo/quote", { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".quick-table .vxe-table", { timeout: 20000 });
  await page.waitForTimeout(600);
  await searchQuote();
  const row2 = page.locator(".vxe-table--body .vxe-body--row", { hasText: quoteCode }).first();
  await row2.waitFor({ state: "visible", timeout: 10000 });
  await row2.locator('button:has-text("发布")').click();
  const publishBox = page.locator('.el-message-box:has-text("发布报价确认")');
  await publishBox.waitFor({ state: "visible", timeout: 8000 });
  const publishText = (await publishBox.innerText()).replace(/\s+/g, " ");
  if (!publishText.includes("暂不生效")) problems.push(`[发布弹窗] 未来生效应提示暂不生效: ${publishText.slice(0, 120)}`);
  await publishBox.locator('button:has-text("确认发布")').click();
  await page.waitForSelector('.el-message--success:has-text("发布成功")', { timeout: 10000 });
  await page.waitForTimeout(1200);

  // ---------- 失效 ----------
  const row3 = page.locator(".vxe-table--body .vxe-body--row", { hasText: quoteCode }).first();
  await row3.locator('button:has-text("失效")').waitFor({ state: "visible", timeout: 8000 });
  await row3.locator('button:has-text("失效")').click();
  const invalidBox = page.locator('.el-message-box:has-text("作废报价确认")');
  await invalidBox.waitFor({ state: "visible", timeout: 8000 });
  console.log("作废弹窗:", (await invalidBox.innerText()).replace(/\s+/g, " ").slice(0, 120));
  await invalidBox.locator('button:has-text("确认作废")').click();
  await page.waitForSelector('.el-message--success:has-text("作废成功")', { timeout: 10000 });
  const invalidMsg = (await page.locator('.el-message--success:has-text("作废成功")').first().innerText()).replace(/\s+/g, " ");
  console.log("作废提示:", invalidMsg);
  if (!invalidMsg.includes("作废成功")) problems.push(`[作废] 提示异常: ${invalidMsg}`);
  await page.waitForTimeout(1200);
  const row4Text = await page.locator(".vxe-table--body .vxe-body--row", { hasText: quoteCode }).first().innerText();
  if (!row4Text.includes("失效")) problems.push(`[作废] 状态未变「失效」: ${row4Text.replace(/\s+/g, " ")}`);
  else console.log("作废后状态为「失效」（符合预期）");

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
