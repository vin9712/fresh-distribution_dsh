/**
 * 报价删除级联明细 E2E（后端修复验证）
 *
 * 背景：原 deleteProductSkuQuoteByIds/ById 只删主表，明细成为孤儿数据。
 *       修复后删除报价单应同时物理删除其明细。
 *
 * 步骤：登录 → 取单号 → 建报价（1 条明细）→ 断言明细存在
 *      → 删除报价单 → 断言主单与明细都已删除
 *
 * 运行：node tests/e2e-quote-delete-cascade.mjs
 */
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const API = BASE + "/dev-api";
const problems = [];

const fmt = (d) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;

async function main() {
  const browser = await chromium.launch({ channel: "chrome", headless: true });
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  page.on("pageerror", (e) => problems.push(`[pageerror] ${e.message}`));

  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder*="账号"]', "admin");
  await page.fill('input[placeholder*="密码"]', "admin123");
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });
  const token = (await page.context().cookies()).find((c) => c.name === "Admin-Token")?.value || "";
  const auth = { Authorization: "Bearer " + token, "Content-Type": "application/json" };

  const api = (path, opts) =>
    page.evaluate(
      async ([api, p, o, tk]) => {
        const r = await fetch(api + p, {
          ...(o || {}),
          headers: { Authorization: "Bearer " + tk, "Content-Type": "application/json", ...((o && o.headers) || {}) },
        });
        return r.json();
      },
      [API, path, opts || null, token]
    );

  // 取单号
  const codeResp = await api("/product/quote/code?refresh=true");
  const code = codeResp && codeResp.msg;
  if (!code) problems.push("[前置] 未取到报价单号");

  // 建报价 + 明细
  const today = new Date();
  const later = new Date(today.getTime() + 7 * 86400000);
  const createResp = await api("/product/quote/create", {
    method: "POST",
    body: JSON.stringify({
      customerId: 12,
      quoteCode: code,
      effectiveStartDate: fmt(today),
      effectiveEndDate: fmt(later),
      remark: "E2E删除级联",
      quoteDetails: [
        { skuId: 1, productName: "E2E商品", productUnit: "斤", productSpec: "", price: 7.7 },
        { skuId: 2, productName: "E2E商品2", productUnit: "斤", productSpec: "", price: 8.8 },
      ],
    }),
  });
  const quoteId = createResp && createResp.data && createResp.data.id;
  if (!quoteId) {
    problems.push(`[前置] 建报价失败: ${JSON.stringify(createResp).slice(0, 200)}`);
    await browser.close();
    problems.forEach((p) => console.log("FAIL " + p));
    process.exit(1);
  }
  console.log("新建报价:", quoteId, code);

  const detailBefore = await api(`/quote/quoteDetail/list?quoteId=${quoteId}`);
  const beforeLen = ((detailBefore && detailBefore.data) || []).length;
  console.log("删除前明细条数:", beforeLen);
  if (beforeLen !== 2) problems.push(`[前置] 明细应为 2 条，实际 ${beforeLen}`);

  // 删除报价单
  const delResp = await api(`/product/quote/${quoteId}`, { method: "DELETE" });
  console.log("删除响应:", JSON.stringify(delResp).slice(0, 120));
  if (!delResp || delResp.code !== 200) problems.push(`[删除] 接口返回异常: ${JSON.stringify(delResp).slice(0, 160)}`);

  // 断言主单已删
  const getResp = await api(`/product/quote/${quoteId}`);
  const stillThere = getResp && getResp.code === 200 && getResp.data && getResp.data.id;
  if (stillThere) problems.push(`[主单] 删除后仍能查到 quoteId=${quoteId}`);
  else console.log("主单已删除");

  // 断言明细已级联删除
  const detailAfter = await api(`/quote/quoteDetail/list?quoteId=${quoteId}`);
  const afterLen = ((detailAfter && detailAfter.data) || []).length;
  console.log("删除后明细条数:", afterLen);
  if (afterLen !== 0) problems.push(`[明细] 删除主单后仍有 ${afterLen} 条孤儿明细`);

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
