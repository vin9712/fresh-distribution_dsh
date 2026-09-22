/**
 * 报价发布确认弹窗 E2E
 *
 * 需求：发布价格（发布报价单）必须先弹确认框，说明生效影响与不可撤回，
 *       取消不改变状态，确认后才真正发布。
 *
 * 步骤：登录 → 接口造一张草稿报价（今天~+7天，客户12）→ 列表按编号搜索
 *      → 点「发布」→ 断言确认弹窗（含单号/客户/生效说明）
 *      → 点「取消」→ 状态仍为「新增」
 *      → 再点「发布」→「确认发布」→ 提示「发布成功」且状态变「发布」
 *      → 清理：删除测试报价单
 *
 * 运行：node tests/e2e-quote-publish-confirm.mjs
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

  // ---------- 造草稿报价 ----------
  const today = new Date();
  const later = new Date(today.getTime() + 7 * 86400000);
  const codeResp = await page.evaluate(
    async ([api, tk]) => {
      const r = await fetch(`${api}/product/quote/code?refresh=true`, { headers: { Authorization: "Bearer " + tk } });
      return r.json();
    },
    [API, token]
  );
  quoteCode = codeResp && codeResp.msg;
  if (!quoteCode) {
    problems.push("[前置] 未取到报价单号");
  } else {
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
          effectiveStartDate: fmt(today),
          effectiveEndDate: fmt(later),
          remark: "E2E发布确认",
          quoteDetails: [
            { skuId: 1, productName: "E2E商品", productUnit: "斤", productSpec: "", price: 9.9 },
          ],
        },
      ]
    );
    if (createResp && createResp.code === 200 && createResp.data) createdQuoteId = createResp.data.id;
    if (!createdQuoteId) problems.push(`[前置] 造草稿失败: ${JSON.stringify(createResp).slice(0, 200)}`);
    console.log("草稿报价:", createdQuoteId, quoteCode);
  }

  // ---------- 列表搜索 ----------
  await page.goto(BASE + "/basicInfo/quote", { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".quick-table .vxe-table", { timeout: 20000 });
  await page.waitForTimeout(600);
  await page.fill('input[placeholder="请输入报价编号"]', quoteCode);
  await page.click('button:has-text("搜索")');
  await page.waitForTimeout(1200);

  const row = page.locator(".vxe-table--body .vxe-body--row", { hasText: quoteCode }).first();
  await row.waitFor({ state: "visible", timeout: 10000 });
  if (!(await row.innerText()).includes("新增")) problems.push("[前置] 草稿行状态不是「新增」");

  // ---------- 点发布 → 确认弹窗 ----------
  await row.locator('button:has-text("发布")').click();
  const box = page.locator('.el-message-box:has-text("发布报价确认")');
  await box.waitFor({ state: "visible", timeout: 8000 });
  const boxText = (await box.innerText()).replace(/\s+/g, " ");
  console.log("确认弹窗:", boxText.slice(0, 160));
  if (!boxText.includes(quoteCode)) problems.push("[弹窗] 未展示报价单号");
  if (!boxText.includes("立即生效")) problems.push("[弹窗] 未说明生效影响（今天在有效期内应提示立即生效）");
  if (!boxText.includes("不可撤回")) problems.push("[弹窗] 未提示不可撤回为草稿");

  // ---------- 取消：状态不变 ----------
  await box.locator('button:has-text("取消")').click();
  await page.waitForTimeout(1000);
  const rowAfterCancel = page.locator(".vxe-table--body .vxe-body--row", { hasText: quoteCode }).first();
  const cancelText = await rowAfterCancel.innerText();
  if (!cancelText.includes("新增")) problems.push(`[取消] 状态不应变化，实际: ${cancelText.replace(/\s+/g, " ")}`);
  else console.log("取消后仍为「新增」（符合预期）");

  // ---------- 确认发布 ----------
  await rowAfterCancel.locator('button:has-text("发布")').click();
  await box.waitFor({ state: "visible", timeout: 8000 });
  await box.locator('button:has-text("确认发布")').click();
  await page.waitForSelector(".el-message--success", { timeout: 10000 });
  const successMsg = (await page.locator(".el-message--success").first().innerText()).replace(/\s+/g, " ");
  console.log("发布提示:", successMsg);
  if (!successMsg.includes("发布成功")) problems.push(`[发布] 提示异常: ${successMsg}`);

  await page.waitForTimeout(1200);
  const rowAfterPublish = page.locator(".vxe-table--body .vxe-body--row", { hasText: quoteCode }).first();
  const publishText = await rowAfterPublish.innerText();
  if (!publishText.includes("发布")) problems.push(`[发布] 状态未变「发布」，实际: ${publishText.replace(/\s+/g, " ")}`);
  else console.log("发布后状态为「发布」（符合预期）");

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
