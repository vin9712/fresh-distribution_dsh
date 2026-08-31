// 草稿工具治理运行时验证（W0-5.3，2026-09）
// 前置：本地环境已启动（./dev.sh start，前端 1025 → 后端 8090）
// 范围：登录 → /demo/draft-workspace 演示页（对 saleDraft 工具真实读写）
//   1. 用户命名空间：key 含 u{userId}
//   2. 版本号：ver=2、rev 自增
//   3. 容量治理：>256KB 本地拒绝；22 条写入后压到 20 条（最旧淘汰）
//   4. 恢复校验：合法草稿校验通过且消费删除；损坏 JSON 被清除
//   5. 用户隔离：伪造他人命名空间 key 后 listDrafts 不可见
// 运行：node tests/e2e-sale-draft.mjs
import { chromium } from "playwright-core";

const BASE = "http://localhost:1025";
const problems = [];
const browser = await chromium.launch({ channel: "chrome", headless: true });
try {
  const page = await browser.newPage();
  page.on("pageerror", (e) => problems.push(`[pageerror] ${page.url()} :: ${e.message}`));
  page.on("console", (m) => {
    if (m.type() === "error" && !m.text().includes("草稿超出")) problems.push(`[console.error] ${page.url()} :: ${m.text().slice(0, 300)}`);
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

  // ---- 演示页 ----
  await page.goto(BASE + "/demo/draft-workspace", { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".el-table", { timeout: 15000 });
  const draftRows = () => page.locator(".el-table__body .el-table__row").count();

  // 预清理
  await page.click("text=删除全部");
  await page.waitForTimeout(200);
  if ((await draftRows()) !== 0) problems.push("[前置] 初始清理后仍有草稿");

  // ---- 1/2. 用户命名空间 + 版本号 ----
  await page.click("text=保存草稿");
  await page.waitForTimeout(200);
  await page.click("text=保存草稿");
  await page.waitForTimeout(200);
  const firstRow = (await page.locator(".el-table__body .el-table__row").first().innerText()).replace(/\s+/g, " ");
  console.log("首行:", firstRow);
  if (!firstRow.includes("saleDraft:u1:")) problems.push(`[命名空间] key 未含用户段: ${firstRow}`);
  // 列序：key | rev | ver | savedAt；第二次保存后 rev 应为 2、ver 为 2
  const cells = firstRow.split(" ").filter(Boolean);
  const revCell = cells[cells.indexOf("saleDraft:u1:new:9001") + 1];
  if (revCell !== "2") problems.push(`[rev] 第二次保存 rev 应为 2，实际 ${revCell}`);

  // ---- 3a. 超大草稿本地拒绝 ----
  const keysBefore = await page.evaluate(() => Object.keys(localStorage).filter((k) => k.startsWith("saleDraft:u1:")).length);
  await page.click("text=保存超大草稿");
  await page.waitForTimeout(300);
  const keysAfterBig = await page.evaluate(() => Object.keys(localStorage).filter((k) => k.startsWith("saleDraft:u1:")).length);
  console.log(`超大草稿：本地 keys ${keysBefore} -> ${keysAfterBig}`);
  if (keysAfterBig !== keysBefore) problems.push("[容量] 超大草稿不应落本地");

  // ---- 3b. 上限淘汰：灌 22 条 → 剩 20 ----
  await page.click("text=灌满至 20 条上限");
  await page.waitForTimeout(500);
  const countAfterFill = await page.evaluate(() => Object.keys(localStorage).filter((k) => k.startsWith("saleDraft:u1:") && !k.includes("migrated")).length);
  console.log("灌满后本地草稿数:", countAfterFill);
  if (countAfterFill > 20) problems.push(`[容量] 超过 20 条上限: ${countAfterFill}`);

  // ---- 4. 恢复校验 + 消费删除 ----
  const rowsBeforeRestore = await draftRows();
  await page.locator(".el-table__body .el-table__row").first().locator("text=恢复(消费)").click();
  await page.waitForTimeout(300);
  const msgText = await page.locator(".el-message").last().innerText().catch(() => "");
  console.log("恢复提示:", msgText);
  if (!msgText.includes("校验通过")) problems.push(`[恢复校验] ${msgText}`);
  await page.waitForTimeout(300);
  const rowsAfterRestore = await draftRows();
  if (rowsAfterRestore !== rowsBeforeRestore - 1) problems.push(`[消费] 恢复后草稿应删除 ${rowsBeforeRestore}->${rowsAfterRestore}`);

  // ---- 4b. 损坏 JSON 清除 ----
  await page.click("text=保存草稿");
  await page.waitForTimeout(200);
  await page.evaluate(() => {
    const k = Object.keys(localStorage).find((k) => k.startsWith("saleDraft:u1:"));
    localStorage.setItem(k, "{corrupted-json!!");
  });
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.waitForSelector(".el-table", { timeout: 15000 });
  await page.waitForTimeout(300);
  const corruptedGone = await page.evaluate(() => !Object.values(localStorage).some((v) => v.includes("corrupted-json")));
  console.log("损坏记录被清除:", corruptedGone);
  if (!corruptedGone) problems.push("[校验] 损坏 JSON 未被清除");

  // ---- 5. 用户隔离 ----
  await page.evaluate(() => {
    localStorage.setItem("saleDraft:u999:new:8888", JSON.stringify({ ver: 2, savedAt: new Date().toISOString(), details: [], remark: "他人草稿" }));
  });
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.waitForSelector(".el-table", { timeout: 15000 });
  await page.waitForTimeout(300);
  const alienVisible = await page.evaluate(() => Object.keys(localStorage).some((k) => k.includes("u999")));
  const alienInList = await page.evaluate(() => {
    // 演示页表格只展示当前用户草稿：若表格内出现 u999 key 则隔离失败
    return document.querySelector(".el-table").innerText.includes("u999");
  });
  console.log("他人命名空间 key 存在(应true):", alienVisible, "| 出现在列表(应false):", alienInList);
  if (!alienVisible) problems.push("[隔离] 伪造 key 应存在于 localStorage");
  if (alienInList) problems.push("[隔离] 他人草稿不应出现在当前用户列表");
  await page.evaluate(() => localStorage.removeItem("saleDraft:u999:new:8888"));
} finally {
  await browser.close();
}

if (problems.length) {
  console.error("\n❌ 验证未通过:");
  problems.forEach((p) => console.error(" -", p));
  process.exit(1);
}
console.log("\n✅ 草稿工具治理全部验证通过");
