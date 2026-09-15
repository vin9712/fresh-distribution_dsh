// 订单录入页「未保存改动离开守卫」E2E（2026-09-15）
// 前置：./dev.sh start（后端 8090 + 前端 1025）
// 范围：/order/sale-detail/index/ 录入页 —— 有未保存改动时
//   ① 浏览器返回（回到上一页）→ 弹确认；取消后留在本页
//   ② beforeunload（关闭/刷新）→ 事件被 preventDefault
//   ③ 页面「返回」按钮 → 弹确认
// 判定：逐条打印；无 pageerror / console.error / 5xx
// 依赖：仓库根 node_modules/playwright-core（系统 Chrome）
// 运行：node tests/e2e-order-leave-guard.mjs
// 说明：只改「订单备注」触发脏标记，不保存、不提交，无数据库写入。

import { chromium } from "playwright-core";

const BASE = process.env.E2E_BASE || "http://localhost:1025";
const CHANNEL = process.env.E2E_CHANNEL || "chrome";
const USER = process.env.E2E_USER || "admin";
const PASS = process.env.E2E_PASS || "admin123";

const problems = [];
const checks = [];
const ok = (name, pass, detail = "") => {
  checks.push([pass, name, detail]);
  console.log(`${pass ? "OK  " : "FAIL"} ${name}${detail ? " :: " + detail : ""}`);
};

const browser = await chromium.launch({ channel: CHANNEL, headless: true });
const context = await browser.newContext();
const page = await context.newPage();
page.on("pageerror", (e) => problems.push(`[pageerror] ${page.url()} :: ${e.message}`));
page.on("console", (m) => {
  if (m.type() === "error") problems.push(`[console.error] ${page.url()} :: ${m.text().slice(0, 300)}`);
});
page.on("response", (r) => {
  if (r.status() >= 500) problems.push(`[http${r.status()}] ${r.url()}`);
});

try {
  console.log("\n== 订单录入页未保存离开守卫 E2E ==\n");

  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder="账号"]', USER);
  await page.fill('input[placeholder="密码"]', PASS);
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 25000 });
  ok("UI 登录", true, page.url());

  await page.goto(BASE + "/order/sale", { waitUntil: "networkidle" });
  await page.getByRole("button", { name: "新增明细" }).first().click();
  await page.waitForURL((u) => String(u).includes("/order/sale-detail"), { timeout: 15000 });
  const remark = page.locator('input[placeholder="请输入订单备注"]');
  await remark.waitFor({ state: "visible", timeout: 15000 });
  ok("进入订单录入页", true, page.url());

  // 等 initOrderDetailPage 完成（原始快照建立）后再编辑，否则快照会把编辑一并计入而看不出脏
  await page.waitForTimeout(2500);

  // 改备注 → 触发脏标记（不保存）
  await remark.fill("E2E未保存测试");
  await page.waitForTimeout(300);
  const box = page.locator(".el-message-box");

  // ① 浏览器返回（回到上一页）
  await page.evaluate(() => window.history.back());
  await box.waitFor({ state: "visible", timeout: 8000 });
  const backText = (await box.innerText()).replace(/\n/g, " ");
  ok("浏览器返回弹出未保存确认", backText.includes("未保存"), backText.slice(0, 46));
  await box.getByRole("button", { name: "继续编辑" }).click();
  await box.waitFor({ state: "hidden", timeout: 8000 });
  ok("取消后仍留在订单录入页", page.url().includes("/order/sale-detail"), page.url());

  // ② beforeunload（关闭/刷新）被拦截
  const unloadPrevented = await page.evaluate(() => {
    const e = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(e);
    return e.defaultPrevented;
  });
  ok("关闭/刷新被 beforeunload 拦截", unloadPrevented);

  // ③ 页面「返回」按钮
  await page.getByRole("button", { name: "返回" }).first().click();
  await box.waitFor({ state: "visible", timeout: 8000 });
  ok("页面「返回」按钮弹出未保存确认", (await box.innerText()).includes("未保存"));
  await box.getByRole("button", { name: "取消" }).click();
  await box.waitFor({ state: "hidden", timeout: 8000 });

  // 清掉测试值，避免留下脏状态
  await remark.fill("");
  await page.waitForTimeout(200);
} catch (e) {
  ok("脚本执行", false, e.message);
} finally {
  ok("无 pageerror / console.error / 5xx", problems.length === 0, problems.slice(0, 5).join(" | "));
  const failed = checks.filter(([p]) => !p).length;
  console.log(`\n===== 结果：${checks.length - failed}/${checks.length} 通过 =====`);
  await browser.close();
  process.exit(failed ? 1 : 0);
}
