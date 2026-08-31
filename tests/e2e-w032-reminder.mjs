/**
 * W0-3.2 E2E：待验收提醒前端标色接入（工作台卡片分级计数 + 送货单列表行标色/提醒标签）
 *
 * 前置：dev 环境已启动（前端 1025，后端 8090 为含 reminderLevel 改造的 jar）
 * 运行：node tests/e2e-w032-reminder.mjs
 *
 * 说明：只读验证——不新建/不改动业务数据，基于 dev 库既有「已送达未提交验收」送货单断言：
 *   1) /workbench/pending-acceptance 与列表页 /order/delivery/page 同口径（同纯函数实时计算）；
 *   2) 工作台「待验收」卡片红/黄分级计数与 API 一致；
 *   3) 送货单列表红级行 row-class-name 生效 + 状态列「待验收」提醒标签（悬浮原因）；
 *   4) 已提交验收单的送达行不标色（无提醒 class/tag）。
 */
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const API = BASE + "/dev-api";
const problems = [];

const ok = (label) => console.log("OK   " + label);
const bad = (label) => {
  console.log("FAIL " + label);
  problems.push(label);
};
const check = (cond, label) => (cond ? ok(label) : bad(label));

async function main() {
  const browser = await chromium.launch({ channel: "chrome", headless: true });
  const page = await browser.newPage({ viewport: { width: 1600, height: 900 } });

  page.on("pageerror", (e) => problems.push(`[pageerror] ${page.url()} :: ${e.message}`));
  page.on("console", (m) => {
    if (m.type() === "error") problems.push(`[console.error] ${page.url()} :: ${m.text().slice(0, 200)}`);
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
  ok("LOGIN -> " + page.url());

  // ---------- API 基线：待验收列表（工作台口径） ----------
  const cookies = await page.context().cookies(BASE);
  const token = (cookies.find((c) => c.name === "Admin-Token") || {}).value;
  const headers = { Authorization: "Bearer " + token };
  const resp = await page.request.get(API + "/workbench/pending-acceptance", { headers });
  const pendingList = (await resp.json()).data || [];
  const expectRed = pendingList.filter((r) => r.reminderLevel === 2).length;
  const expectYellow = pendingList.filter((r) => r.reminderLevel === 1).length;
  ok(`基线：待验收 ${pendingList.length} 张（红 ${expectRed} / 黄 ${expectYellow}）`);
  if (!pendingList.length) {
    bad("dev 库无「已送达未验收」数据，无法验证标色（请先造数）");
  }
  const redRow = pendingList.find((r) => r.reminderLevel === 2); // 过期红样例
  const judged = pendingList.some((r) => r.reminderLevel === 2 && r.reminderReason);
  check(judged, "提醒行带 reminderReason");

  // ---------- 1. 工作台卡片：分级计数 ----------
  await page.goto(BASE + "/workbench", { waitUntil: "networkidle" });
  await page.waitForSelector(".workbench-card", { timeout: 15000 });
  await page.waitForTimeout(1200);

  const card = page.locator(".workbench-card", { hasText: "待验收" }).first();
  check((await card.count()) === 1, "工作台存在「待验收」卡片");
  const levelBox = card.locator(".card-levels");
  if (expectRed + expectYellow > 0) {
    check((await levelBox.count()) === 1, "卡片显示分级计数区（有提醒时）");
    const redDot = levelBox.locator(".level-red");
    const yellowDot = levelBox.locator(".level-yellow");
    if (expectRed > 0) {
      check((await redDot.count()) === 1, `红徽标存在`);
      check((await redDot.textContent()).trim() === `红 ${expectRed}`, `红徽标计数=${expectRed}`);
    }
    if (expectYellow > 0) {
      check((await yellowDot.textContent()).trim() === `黄 ${expectYellow}`, `黄徽标计数=${expectYellow}`);
    }
  } else {
    check((await levelBox.count()) === 0, "无提醒时分级计数区不占位");
  }

  // ---------- 2. 送货单列表：行标色 + 提醒标签 ----------
  await page.goto(BASE + "/order/delivery?status=2", { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".el-table__row", { timeout: 15000 });
  await page.waitForTimeout(1500);

  if (redRow) {
    const redRowEl = page.locator(".el-table__row", { hasText: redRow.deliveryCode }).first();
    check((await redRowEl.count()) === 1, `找到红色样例行 ${redRow.deliveryCode}`);
    const rowClass = await redRowEl.getAttribute("class");
    check((rowClass || "").includes("row-reminder-red"), "红级行挂 row-reminder-red（行底色标红）");
    const tag = redRowEl.locator(".reminder-tag--red");
    check((await tag.count()) === 1, "状态列出现红色「待验收」提醒标签");
    // 悬浮提示：展示 reminderReason
    await tag.hover();
    await page.waitForTimeout(800);
    const tip = await page.locator(".el-popper:visible").last().textContent().catch(() => "");
    check((tip || "").includes("待验收提醒"), `提醒标签悬浮显示原因（${(tip || "").trim().slice(0, 30)}…）`);
  }

  // 已提交验收单的送达行不标色（取列表中无 reminderLevel 的一行：状态=已送达但无 row-reminder class）
  const allRows = page.locator(".el-table__row");
  const n = await allRows.count();
  let colored = 0;
  for (let i = 0; i < n; i++) {
    const cls = (await allRows.nth(i).getAttribute("class")) || "";
    if (cls.includes("row-reminder-red") || cls.includes("row-reminder-yellow")) colored++;
  }
  check(colored === expectRed + expectYellow, `列表标色行数(${colored}) = 基线提醒数(${expectRed + expectYellow})，已验收送达行不标`);

  // ---------- 3. keep-alive 返回工作台：activated 刷新不报错且计数仍在 ----------
  await page.goto(BASE + "/workbench", { waitUntil: "networkidle" });
  await page.waitForTimeout(1000);
  const card2 = page.locator(".workbench-card", { hasText: "待验收" }).first();
  check((await card2.locator(".card-levels .level-dot").count()) >= 1, "keep-alive 返回工作台后分级计数仍在（activated 刷新无异常）");

  await browser.close();

  if (problems.length) {
    console.error("\n[W0-3.2] FAIL:");
    for (const p of problems) console.error("  " + p);
    process.exit(1);
  }
  console.log("\n[W0-3.2] PASS: 工作台分级计数 / 送货单列表行标色 / 提醒标签 / 已验收不标 / keep-alive 刷新 全部通过");
}

main().catch((e) => {
  console.error("[W0-3.2] crashed:", e);
  process.exit(1);
});
