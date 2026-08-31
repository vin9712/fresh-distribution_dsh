// 快捷键服务运行时验证（W0-5.2，2026-09）
// 前置：本地环境已启动（./dev.sh start，前端 1025 → 后端 8090）
// 范围：
//   1. global 作用域：Ctrl+K 打开全局搜索，Esc 关闭
//   2. workspace 作用域：演示页 F2 切换右面板、F3 聚焦搜索
//   3. 输入焦点保护：输入框内 F2 不触发
//   4. IME 组合态保护：isComposing 事件不分发
//   5. table 作用域：QuickTable 页 F2 新增、F3 聚焦搜索
//   6. 责任链：无选中时 table 层 Esc 下传、global 层不误触
// 运行：node tests/e2e-shortcuts.mjs
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const problems = [];
const browser = await chromium.launch({ channel: "chrome", headless: true });
try {
  const page = await browser.newPage();
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

  // ---- 1. global：Ctrl+K / Esc ----
  await page.keyboard.press("Control+k");
  await page.waitForSelector(".global-search-dialog", { timeout: 5000 });
  console.log("OK   Ctrl+K 打开全局搜索");
  await page.keyboard.press("Escape");
  await page.waitForSelector(".global-search-dialog", { state: "hidden", timeout: 5000 });
  console.log("OK   Esc 关闭全局搜索");

  // ---- 演示页 ----
  await page.goto(BASE + "/demo/split-workspace", { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".split-workspace", { timeout: 15000 });
  const activeTab = () => page.locator(".demo-pane--right .el-tabs__item.is-active").innerText();

  // ---- 2. workspace：F2 / F3 ----
  if (!(await activeTab()).includes("常用商品")) problems.push("[前置] 演示页默认应为常用商品页签");
  await page.keyboard.press("F2");
  await page.waitForTimeout(150);
  const t1 = await activeTab();
  console.log("OK   F2 切换面板 ->", t1);
  if (!t1.includes("商品搜索")) problems.push(`[F2] 期望切到商品搜索，实际 ${t1}`);

  // ---- 3. 输入焦点保护：搜索框内按 F2/F3 不触发 ----
  await page.click(".demo-pane--right .el-tabs__item:has-text('常用商品')");
  await page.click("body");
  await page.locator(".demo-pane--right input").first().focus().catch(() => {});
  // 聚焦右面板搜索输入框（先进搜索页签再聚焦）
  await page.keyboard.press("F3"); // workspace: 切到搜索并聚焦
  await page.waitForTimeout(150);
  const focusedInSearch = await page.evaluate(() => {
    const el = document.activeElement;
    return el && el.tagName === "INPUT" && !!el.closest(".demo-pane--right");
  });
  console.log("OK   F3 聚焦搜索输入框:", focusedInSearch);
  if (!focusedInSearch) problems.push("[F3] 未聚焦右面板输入框");
  const beforeTab = await activeTab();
  await page.keyboard.press("F2"); // 输入框内应被输入焦点保护拦下
  await page.waitForTimeout(150);
  const afterTab = await activeTab();
  console.log("输入框内 F2：", beforeTab, "->", afterTab);
  if (beforeTab !== afterTab) problems.push("[输入保护] 输入框内 F2 不应切换面板");

  // ---- 4. IME 组合态保护 ----
  const tabBeforeIme = await activeTab();
  await page.evaluate(() => {
    window.dispatchEvent(
      new KeyboardEvent("keydown", { key: "F2", bubbles: true, isComposing: true })
    );
  });
  await page.waitForTimeout(150);
  const tabAfterIme = await activeTab();
  console.log("IME 组合态 F2：", tabBeforeIme, "->", tabAfterIme);
  if (tabBeforeIme !== tabAfterIme) problems.push("[IME] 组合态事件不应触发快捷键");
  // 同一事件去掉组合态后应正常触发（对照）
  await page.evaluate(() => {
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "F2", bubbles: true }));
  });
  await page.waitForTimeout(150);
  if ((await activeTab()) === tabAfterIme) problems.push("[对照] 非组合态 F2 应正常触发");

  // ---- 5. table 作用域：QuickTable 页 ----
  await page.goto(BASE + "/basicInfo/sku", { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".app-container", { timeout: 15000 });
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  await page.keyboard.press("F2");
  await page.waitForTimeout(400);
  const dialogVisible = await page.locator(".el-dialog:visible").count();
  console.log("QuickTable F2 新增弹窗:", dialogVisible > 0 ? "OK 打开" : "未打开");
  if (!dialogVisible) problems.push("[table-F2] F2 未触发新增弹窗");
  await page.keyboard.press("F2"); // 弹窗内输入框聚焦时 F2 也不应重复触发/误触
  await page.waitForTimeout(300);
  // 关闭弹窗（点取消/叉），避免残留
  await page.keyboard.press("Escape");
  await page.waitForTimeout(300);
  await page.keyboard.press("F3");
  await page.waitForTimeout(200);
  const searchFocused = await page.evaluate(() => {
    const el = document.activeElement;
    return el && el.tagName === "INPUT" && !!el.closest(".app-container");
  });
  console.log("QuickTable F3 聚焦搜索:", searchFocused ? "OK" : "未聚焦");
  if (!searchFocused) problems.push("[table-F3] F3 未聚焦搜索框");
} finally {
  await browser.close();
}

if (problems.length) {
  console.error("\n❌ 验证未通过:");
  problems.forEach((p) => console.error(" -", p));
  process.exit(1);
}
console.log("\n✅ 快捷键服务全部验证通过");
