/**
 * 金满楼静态样板闭环验证：登录 → 签发票据 → 打开 /jmreport/view/{报表ID} →
 * 校验 show 响应中 44 行逐字段数据，保存截图供人工核验（不以响应成功代替视觉验收）。
 * 运行：node tests/_jinman-view-check.mjs （前置：前端 1025 / 后端 8090 已启动）
 */
import { chromium } from "playwright-core";
import { readFileSync } from "node:fs";

const BASE = "http://localhost:1025";
const API = BASE + "/dev-api";
const REPORT_ID = "7196889299099535232";
const problems = [];

async function main() {
  const browser = await chromium.launch({ channel: "chrome", headless: true });
  const page = await browser.newPage({ viewport: { width: 1500, height: 1600 } });
  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder*="账号"]', "admin");
  await page.fill('input[placeholder*="密码"]', "admin123");
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });

  const cookies = await page.context().cookies(BASE);
  const token = (cookies.find((c) => c.name === "Admin-Token") || {}).value;
  if (!token) problems.push("未取到登录 token");
  const ticket = (await page.request.post(API + "/print/ticket", {
    headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
    data: {},
  }).then((r) => r.json())).ticket;
  if (!ticket) problems.push("票据签发失败");

  const responsePromise = page.waitForResponse(r => r.url().includes('/jmreport/show') && r.request().method() === 'POST');
  page.on('pageerror', e => problems.push(e.message));
  await page.goto(BASE + "/jmreport/view/" + REPORT_ID + "?token=" + ticket + "&ticket=" + ticket, { waitUntil: "domcontentloaded", timeout: 30000 });
  await page.waitForTimeout(6000);
  const response = await (await responsePromise).json();
  if (response.code !== 200 || !response.success) problems.push('show 返回失败');
  const rows = response.result?.dataList?.jinmanDs?.list || [];
  const expected = JSON.parse(readFileSync(new URL('../lin-distribution/src/test/resources/print/jinman-source.json', import.meta.url))).datasets[0].jsonData;
  if (rows.length !== 44) problems.push(`明细行数错误: ${rows.length}`);
  expected.forEach((source, i) => {
    for (const [key, value] of Object.entries(source)) {
      const normalized = typeof value === 'number' ? (value === 0 ? '' : String(value)) : value;
      if (rows[i]?.[key] !== normalized) problems.push(`第${i+1}行 ${key} 不一致`);
    }
    const total = ['zhongchu','dianxin','weibu','tieban','shangza','fushi','baoyu'].reduce((sum, key) => sum + source[key], 0);
    if (rows[i]?.sum1 !== (total === 0 ? '' : String(total))) problems.push(`第${i+1}行合计不一致`);
  });
  await page.screenshot({ path: "/tmp/jinman-sample-view.png", fullPage: true });
  console.log('渲染载体:', await page.evaluate(() => ({canvas: document.querySelectorAll('canvas').length, td: document.querySelectorAll('td').length})));
  await page.mouse.move(400, 800);
  await page.mouse.wheel(0, 1200);
  await page.waitForTimeout(700);
  await page.screenshot({ path: '/tmp/jinman-sample-bottom.png' });
  console.log(problems.length ? "FAIL:\n" + problems.join("\n") : "PASS: show 返回44行逐字段与合计一致；截图待人工核对，不代表完整接入验收");
  await browser.close();
  process.exit(problems.length ? 1 : 0);
}
main().catch((e) => { console.error("E2E异常:", e.message); process.exit(1); });
