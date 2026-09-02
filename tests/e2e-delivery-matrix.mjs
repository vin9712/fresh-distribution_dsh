// 页面级 E2E：P0-A 矩阵总表页（D-044~D-053）
// 前置：后端临时实例 8091（VITE_PROXY_TARGET 指向它）、前端 dev 1026
// 范围：只读——查询客户日总表矩阵口径与配货口径，不点任何写操作按钮
// 运行：node tests/e2e-delivery-matrix.mjs
import { chromium } from "playwright-core";

const BASE = "http://localhost:1026";
const problems = [];
const browser = await chromium.launch({ channel: "chrome", headless: true });

const ok = (label) => console.log("OK   " + label);
const bad = (label) => {
  console.log("FAIL " + label);
  problems.push(label);
};
const check = (cond, label) => (cond ? ok(label) : bad(label));

try {
  const page = await browser.newPage();
  page.on("pageerror", (e) => problems.push(`[pageerror] ${page.url()} :: ${e.message}`));
  page.on("console", (m) => {
    if (m.type() === "error") problems.push(`[console.error] ${page.url()} :: ${m.text().slice(0, 300)}`);
  });
  page.on("response", (r) => {
    if (r.status() >= 400 && !r.url().includes("captcha")) problems.push(`[http${r.status()}] ${r.url()}`);
  });

  await page.goto(BASE + "/login", { waitUntil: "networkidle" });
  await page.fill('input[placeholder="账号"]', "admin");
  await page.fill('input[placeholder="密码"]', "admin123");
  await page.click(".login-button, .el-button--primary");
  await page.waitForURL((u) => !String(u).includes("/login"), { timeout: 20000 });
  ok("LOGIN -> " + page.url());

  // ---- A 类跨点总单：带参直达矩阵总表 ----
  const waitMatrix = async (url) => {
    const resp = page.waitForResponse((r) => r.url().includes("/matrix"), { timeout: 15000 });
    await page.goto(url, { waitUntil: "networkidle" });
    await resp;
    await page.waitForTimeout(800);
  };
  await waitMatrix(BASE + "/order/batch?customerId=11&deliveryDate=2026-08-28");

  const body = await page.locator(".batch-print-area").innerText();
  check(body.includes("配送矩阵总表"), "A类批次默认走矩阵口径");
  check(body.includes("大长江") && body.includes("棠下") && body.includes("华铃"), "三个配送点列全部出现");
  check(body.includes("当日无单"), "当日无订单的点保留空列并标注");
  check(body.includes("布局版本 v1"), "布局快照版本回显（非实时推导）");
  const headers = await page.locator(".el-table th").allInnerTexts();
  check(headers.filter((h) => /大长江|棠下|华铃/.test(h)).length === 3, "表头点列数=3（矩阵列来自快照）");
  const cells = await page.locator(".el-table__body td").allInnerTexts();
  check(cells.some((c) => c.trim() === "7" || c.trim() === "7.0"), "格值渲染出分配量");
  check(!body.includes("恒等式自检不通过"), "恒等式通过（无红色告警条）");
  ok("A类矩阵渲染完成");

  // ---- 口径切换到配货总表（D-027/28 不拆价） ----
  const pickResp = page.waitForResponse((r) => r.url().includes("/batch/view"), { timeout: 15000 });
  await page.click(".el-radio-button:has-text('配货总表')");
  await pickResp;
  await page.waitForTimeout(800);
  const pickBody = await page.locator(".batch-print-area").innerText();
  check(pickBody.includes("配送总表") && !pickBody.includes("配送矩阵总表"), "切换到配货总表口径");
  check(pickBody.includes("配送点小计"), "配货口径保留各点小计折叠列");
  ok("口径切换正常");

  // ---- B 类批次（每点一单）矩阵：稀疏矩阵 + 无快照实时推导提示 ----
  await waitMatrix(BASE + "/order/batch?customerId=10&deliveryDate=2026-08-30");
  const bBody = await page.locator(".batch-print-area").innerText();
  check(bBody.includes("实时推导"), "B类历史批次无快照 → 提示实时推导（D-045）");
  check(bBody.includes("丽宫") && bBody.includes("点心"), "启用点全部进列（含当日无单点）");
  ok("B类矩阵渲染完成");

  console.log(problems.length ? "\n=== 问题清单 ===" : "\n=== 无异常 ===");
  problems.forEach((p) => console.log(" - " + p));
} finally {
  await browser.close();
}
process.exit(problems.length ? 1 : 0);
