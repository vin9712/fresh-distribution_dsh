// 全局搜索竞态修复验证（W0-5.4，2026-09）
// 前置：本地环境已启动（./dev.sh start，前端 1025 → 后端 8090）
// 范围：
//   1. 防抖：连续输入只发一次请求（250ms）
//   2. 序号 + AbortController：过期响应被丢弃、在途请求被取消且无错误弹窗
//   3. 结果正确性：慢速旧关键词响应不覆盖新关键词结果（后端加 delay 模拟）
//   4. 路由跳转统一：经 router.resolve（含 base），新标签 URL 正确
//   5. 空关键词清空结果
// 运行：node tests/e2e-global-search.mjs
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const problems = [];
const browser = await chromium.launch({ channel: "chrome", headless: true });
let page;
try {
  page = await browser.newPage();
  page.on("pageerror", (e) => problems.push(`[pageerror] ${page.url()} :: ${e.message}`));
  page.on("console", (m) => {
    if (m.type() === "error") problems.push(`[console.error] ${page.url()} :: ${m.text().slice(0, 300)}`);
  });
  page.on("response", (r) => {
    if (r.status() >= 500) problems.push(`[http${r.status()}] ${r.url()}`);
  });

  // ---- 登录 ----
  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder="账号"]', "admin");
  await page.fill('input[placeholder="密码"]', "admin123");
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });
  console.log("LOGIN OK");

  // ---- 拦截搜索接口：第一个请求延迟 1500ms，其余正常，用于制造乱序 ----
  await page.route("**/dev-api/search/global**", async (route) => {
    const url = route.request().url();
    const kw = decodeURIComponent(url.match(/keyword=([^&]*)/)?.[1] || "");
    const slow = kw.includes("番"); // 首个慢请求用「番」触发
    if (slow) await new Promise((r) => setTimeout(r, 1500));
    const resp = await route.fetch();
    route.fulfill({ response: resp });
  });

  await page.keyboard.press("Control+k");
  await page.waitForSelector(".global-search-dialog", { timeout: 5000 });
  const input = page.locator(".global-search-dialog input");

  // ---- 1. 防抖：连续输入只发 1 次请求 ----
  let reqCount = 0;
  page.on("request", (r) => {
    if (r.url().includes("/search/global")) reqCount++;
  });
  await input.pressSequentially("番茄", { delay: 30 });
  await page.waitForTimeout(600);
  console.log("连续输入 2 字符请求数:", reqCount);
  if (reqCount !== 1) problems.push(`[防抖] 期望 1 次请求，实际 ${reqCount}`);
  await page.waitForSelector(".gs-item", { timeout: 5000 });
  const firstTitle = await page.locator(".gs-item-title").first().innerText();
  console.log("首条结果:", firstTitle);

  // ---- 2/3. 乱序：慢的旧请求（番，延迟 1500ms）+ 快的新请求（土豆）→ 最终应显示新关键词结果 ----
  await input.fill("");
  await page.waitForTimeout(100);
  await input.pressSequentially("番", { delay: 30 });
  await page.waitForTimeout(80); // 让「番」的慢请求发出（延迟 1500ms）
  await input.fill("土豆"); // 新关键词快请求
  await page.waitForTimeout(2500); // 等两个请求都返回
  const finalTitles = await page.locator(".gs-item-title").allInnerTexts();
  const emptyVisible = await page.locator(".gs-empty-text").count();
  console.log("最终结果数:", finalTitles.length, "| 空态:", emptyVisible > 0, "| 首条:", finalTitles[0] || "-");
  // 若旧响应覆盖新结果，首条会是「番」的结果；正确行为是「土豆」的结果
  if (emptyVisible > 0) problems.push("[竞态] 新结果被旧响应覆盖为空态");
  if (!finalTitles.length) problems.push("[竞态] 无结果渲染");
  if (finalTitles.length && !finalTitles.some((t) => t.includes("土豆")))
    problems.push(`[竞态] 最终结果不是新关键词「土豆」的结果: ${finalTitles.join(",").slice(0, 80)}`);

  // ---- 4. 路由跳转统一（window.open 拦截校验 href） ----
  await page.evaluate(() => {
    window.__opened = [];
    window.open = (u) => window.__opened.push(String(u));
  });
  await page.locator(".gs-item").first().click();
  await page.waitForTimeout(200);
  const openedUrls = await page.evaluate(() => window.__opened || []);
  console.log("window.open URL:", openedUrls[0] || "（无）");
  if (!openedUrls[0]) problems.push("[跳转] 未发生 window.open");
  if (openedUrls[0] && openedUrls[0].includes("/dev-api")) problems.push(`[跳转] URL 不应含代理前缀: ${openedUrls[0]}`);

  // ---- 5. 空关键词清空 ----
  await page.keyboard.press("Control+k");
  await page.waitForSelector(".global-search-dialog", { timeout: 5000 });
  await page.locator(".global-search-dialog input").fill("番");
  await page.waitForTimeout(500);
  await page.locator(".global-search-dialog input").fill("");
  await page.waitForTimeout(500);
  const hintVisible = await page.locator(".gs-empty-hint").count();
  console.log("清空后回提示态:", hintVisible > 0);
  if (!hintVisible) problems.push("[空关键词] 未回到提示态");
} finally {
  await page.unrouteAll({ behavior: 'ignoreErrors' }).catch(() => {});
  await browser.close();
}

if (problems.length) {
  console.error("\n❌ 验证未通过:");
  problems.forEach((p) => console.error(" -", p));
  process.exit(1);
}
console.log("\n✅ 全局搜索竞态修复全部验证通过");
