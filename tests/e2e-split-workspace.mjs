// SplitWorkspace 组件运行时验证（W0-5.1，2026-09）
// 前置：本地环境已启动（./dev.sh start，前端 1025 → 后端 8090）
// 范围：登录 → /demo/split-workspace 演示页
//   1. 组件渲染、默认占比
//   2. 拖拽中缝 → 占比变化 + localStorage 按用户命名空间写入
//   3. 刷新页面 → 宽度记忆保持
//   4. 恢复默认按钮 → 回默认占比 + 清除存储
//   5. 键盘 ←/→ 微调
// 运行：node tests/e2e-split-workspace.mjs
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
  console.log("LOGIN OK ->", page.url());

  // ---- 打开演示页 ----
  await page.goto(BASE + "/demo/split-workspace", { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".split-workspace", { timeout: 15000 });
  const tagText = () => page.locator(".demo-toolbar .el-tag").innerText();

  const initial = await tagText();
  console.log("初始占比:", initial);
  if (!initial.includes("70.0%")) problems.push(`[默认占比] 期望 70.0%，实际 ${initial}`);

  // ---- 拖拽中缝（向左拖 150px）----
  const handle = page.locator(".split-workspace__handle");
  const before = await handle.boundingBox();
  await page.mouse.move(before.x + before.width / 2, before.y + before.height / 2);
  await page.mouse.down();
  await page.mouse.move(before.x + before.width / 2 - 150, before.y + before.height / 2, { steps: 8 });
  await page.mouse.up();
  const afterDrag = await tagText();
  console.log("拖拽后占比:", afterDrag);
  if (afterDrag === initial) problems.push(`[拖拽] 占比未变化: ${afterDrag}`);

  // ---- localStorage 用户命名空间写入 ----
  const stored = await page.evaluate(() => {
    const keys = Object.keys(localStorage).filter((k) => k.startsWith("splitws:"));
    return keys.map((k) => ({ k, v: localStorage.getItem(k) }));
  });
  console.log("存储:", JSON.stringify(stored));
  const hit = stored.find((s) => s.k.startsWith("splitws:u") && s.k.endsWith(":demo-split-workspace"));
  if (!hit || !hit.v.includes('"v":1')) problems.push(`[持久化] 未找到用户命名空间存储: ${JSON.stringify(stored)}`);

  // ---- 刷新后记忆保持 ----
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.waitForSelector(".split-workspace", { timeout: 15000 });
  const afterReload = parseFloat((await tagText()).replace(/[^\d.]/g, ""));
  const dragVal = parseFloat(afterDrag.replace(/[^\d.]/g, ""));
  console.log("刷新后占比:", afterReload);
  if (Math.abs(afterReload - dragVal) > 0.15) problems.push(`[记忆] 刷新后 ${afterReload}% ≉ 拖拽后 ${dragVal}%`);

  // ---- 键盘微调 ----
  await handle.focus();
  await page.keyboard.press("ArrowLeft");
  await page.waitForTimeout(100);
  const afterKey = await tagText();
  console.log("键盘微调后占比:", afterKey);
  if (afterKey === afterReload) problems.push(`[键盘] 方向键未生效: ${afterKey}`);

  // ---- 恢复默认 ----
  await page.click("text=恢复默认布局");
  await page.waitForTimeout(100);
  const afterReset = await tagText();
  const storedAfterReset = await page.evaluate(() =>
    Object.keys(localStorage).filter((k) => k.startsWith("splitws:")).length
  );
  console.log("恢复默认后占比:", afterReset, "| 残留存储键:", storedAfterReset);
  if (!afterReset.includes("70.0%")) problems.push(`[恢复默认] ${afterReset}`);
  if (storedAfterReset !== 0) problems.push(`[恢复默认] 存储未清除，残留 ${storedAfterReset} 键`);

  // ---- 页面渲染完整性抽查 ----
  const paneText = await page.locator(".split-workspace__pane--right").innerText();
  if (!paneText.includes("常用商品")) problems.push("[右面板] 未渲染演示标签页");
} finally {
  await browser.close();
}

if (problems.length) {
  console.error("\n❌ 验证未通过:");
  problems.forEach((p) => console.error(" -", p));
  process.exit(1);
}
console.log("\n✅ SplitWorkspace 全部验证通过");
